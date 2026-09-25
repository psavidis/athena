package com.athena.plugin.java;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a method body calls, throws and returns — compared between two revisions so a body
 * modification says what changed inside the method (ticket #288), e.g. {@code "+triggerOnEvent,
 * +throw IllegalStateException"}, instead of just "the body changed". A call is named with its
 * receiver when that is a plain name or a field of this class ({@code consumedOffsets.clear},
 * ticket #360), otherwise by its simple name, so changed arguments to an existing call are not
 * a call added or removed.
 *
 * <p>Immutable; compared with {@link #describeChangeTo}.
 */
final class BodySummary {

    private static final BodySummary NONE = new BodySummary(Map.of(), Set.of(), List.of(), Map.of(), List.of(), List.of(), List.of());
    private static final int MAX_ITEMS = 5;
    private static final int MAX_SNIPPET_LENGTH = 40;
    private static final String RETURN_VALUE_CHANGED = "return value changed";
    private static final String SWAPPED = "swapped ?: branches in ";
    private static final Pattern TOKEN = Pattern.compile("\\w+|\"(?:[^\"\\\\]|\\\\.)*\"|\\S");
    static final String OTHER_STATEMENTS = "other statements changed";

    private final Map<String, Integer> calls;
    // Ticket #360: the called methods by simple name, for makesSameCallsAs.
    private final Set<String> callNames;
    private final Map<String, Integer> thrownTypes;
    private final int returnCount;
    // Ticket #361: every call in source order, to name one whose arguments alone changed.
    private final List<CallSite> callSites;
    // Ticket #339: the returned expressions in source order, and the body's top-level statements.
    private final List<String> returnExpressions;
    // Ticket #361: each returned expression with its ?: branches swapped (itself when it isn't a conditional).
    private final List<String> swappedReturnExpressions;
    private final List<String> statements;

    private BodySummary(Map<String, Integer> calls, Set<String> callNames, List<CallSite> callSites,
                        Map<String, Integer> thrownTypes, List<String> returnExpressions,
                        List<String> swappedReturnExpressions, List<String> statements) {
        this.calls = Collections.unmodifiableMap(new LinkedHashMap<>(calls));
        this.callNames = Set.copyOf(callNames);
        this.callSites = List.copyOf(callSites);
        this.thrownTypes = Collections.unmodifiableMap(new LinkedHashMap<>(thrownTypes));
        this.returnCount = returnExpressions.size();
        this.returnExpressions = List.copyOf(returnExpressions);
        this.swappedReturnExpressions = List.copyOf(swappedReturnExpressions);
        this.statements = List.copyOf(statements);
    }

    /** The summary of {@code method}'s body; empty for a method without a body (abstract/interface). */
    static BodySummary of(MethodDeclaration method) {
        return method.getBody().map(BodySummary::of).orElse(NONE);
    }

    /** The summary of a constructor's or initializer's body, or any other statement block. */
    static BodySummary of(Node body) {
        // findAll visits an outer call before the calls in its scope (a().b() yields b first);
        // sorting by the name's position keeps the summary in source order.
        List<MethodCallExpr> callsInSourceOrder = body.findAll(MethodCallExpr.class).stream()
                .sorted(Comparator.comparing(call -> call.getName().getBegin().orElseThrow()))
                .toList();
        Map<String, Integer> calls = new LinkedHashMap<>();
        callsInSourceOrder.forEach(call -> calls.merge(displayName(call), 1, Integer::sum));
        Set<String> callNames = new LinkedHashSet<>();
        callsInSourceOrder.forEach(call -> callNames.add(call.getNameAsString()));
        Map<String, Integer> thrownTypes = new LinkedHashMap<>();
        body.findAll(ThrowStmt.class).forEach(throwStmt -> thrownTypes.merge(thrownName(throwStmt), 1, Integer::sum));
        List<ReturnStmt> returnStatements = body.findAll(ReturnStmt.class).stream()
                .filter(returnStmt -> returnsFrom(returnStmt, body))
                .sorted(Comparator.comparing(returnStmt -> returnStmt.getBegin().orElseThrow()))
                .toList();
        List<String> returns = returnStatements.stream()
                .map(returnStmt -> returnStmt.getExpression().map(Node::toString).orElse(""))
                .toList();
        List<String> swappedReturns = returnStatements.stream()
                .map(returnStmt -> returnStmt.getExpression().map(BodySummary::swapped).orElse(""))
                .toList();
        List<String> statements = body instanceof BlockStmt block
                ? block.getStatements().stream().map(statement -> statement.toString().replaceAll("\\s+", " ").trim()).toList()
                : List.of();
        List<CallSite> callSites = callsInSourceOrder.stream().map(CallSite::of).toList();
        return new BodySummary(calls, callNames, callSites, thrownTypes, returns, swappedReturns, statements);
    }

    /**
     * What changed between this (base) body and {@code head}'s: calls added ({@code +name}),
     * calls removed ({@code -name}), thrown types added and removed ({@code +throw Type}), a
     * moved statement, and a changed number of return statements or changed returned values
     * (ticket #339), each repeated item listed once as "item ×N" (ticket #352) — at most
     * {@value #MAX_ITEMS} items, then "…and N more". {@value #OTHER_STATEMENTS} when none of those differ.
     */
    String describeChangeTo(BodySummary head) {
        List<String> items = new ArrayList<>();
        increased(calls, head.calls).forEach(name -> items.add("+" + name));
        increased(head.calls, calls).forEach(name -> items.add("-" + name));
        increased(thrownTypes, head.thrownTypes).forEach(type -> items.add("+throw " + type));
        increased(head.thrownTypes, thrownTypes).forEach(type -> items.add("-throw " + type));
        movedStatement(head).ifPresent(items::add);
        if (head.returnCount != returnCount) {
            items.add("return statements " + returnCount + " → " + head.returnCount);
        } else {
            for (int i = 0; i < returnCount; i++) {
                if (returnExpressions.get(i).equals(head.returnExpressions.get(i))) {
                    continue;
                }
                items.add(head.returnExpressions.get(i).equals(swappedReturnExpressions.get(i))
                        ? SWAPPED + "return"
                        : returnValueChange(returnExpressions.get(i), head.returnExpressions.get(i)));
            }
        }
        items.addAll(argumentChanges(head));
        if (items.isEmpty()) {
            return OTHER_STATEMENTS;
        }
        List<String> distinct = counted(items);
        if (distinct.size() <= MAX_ITEMS) {
            return String.join(", ", distinct);
        }
        return String.join(", ", distinct.subList(0, MAX_ITEMS)) + " …and " + (distinct.size() - MAX_ITEMS) + " more";
    }

    /**
     * Whether {@code head} makes the same calls: the same distinct methods, the same thrown types
     * the same number of times (#334), and as many return statements (ticket #319). How often a call appears doesn't matter, so
     * an unrolled loop still makes the same calls. It says nothing about conditions.
     */
    boolean makesSameCallsAs(BodySummary head) {
        // Calls by distinct name (an unrolled loop repeats them); throws by count (ticket #334): a
        // new throw of a type the method already throws is a real change, not a restructuring.
        return callNames.equals(head.callNames) && thrownTypes.equals(head.thrownTypes)
                && returnCount == head.returnCount;
    }

    /**
     * When the head body holds the same top-level statements in another order and one moved
     * (ticket #339): "moved modCount++ after checkInterval(index, 0, size())".
     */
    private Optional<String> movedStatement(BodySummary head) {
        if (statements.equals(head.statements) || statements.size() != head.statements.size()
                || !statements.stream().sorted().toList().equals(head.statements.stream().sorted().toList())) {
            return Optional.empty();
        }
        for (String statement : new LinkedHashSet<>(statements)) {
            List<String> baseRest = new ArrayList<>(statements);
            List<String> headRest = new ArrayList<>(head.statements);
            baseRest.remove(statement);
            headRest.remove(statement);
            if (baseRest.equals(headRest)) {
                int position = head.statements.indexOf(statement);
                return Optional.of(position > 0
                        ? "moved " + snippet(statement) + " after " + snippet(head.statements.get(position - 1))
                        : "moved " + snippet(statement) + " before " + snippet(head.statements.get(1)));
            }
        }
        return Optional.of("statements reordered");
    }

    /**
     * The calls whose arguments alone changed (ticket #361), when both bodies call the same
     * methods in the same order: a call's arguments differ, and putting the head call in place of
     * the base one makes the enclosing statement the head's. Only the innermost such call is
     * named, so {@code send(wrap(1))} becoming {@code send(wrap(2))} names {@code wrap}. A call in
     * a return statement is left to the return value's own summary.
     */
    private List<String> argumentChanges(BodySummary head) {
        if (callSites.size() != head.callSites.size()) {
            return List.of();
        }
        List<Integer> changed = new ArrayList<>();
        for (int i = 0; i < callSites.size(); i++) {
            CallSite before = callSites.get(i);
            CallSite after = head.callSites.get(i);
            if (!before.simpleName().equals(after.simpleName())) {
                return List.of();
            }
            if (!before.inReturn() && !before.arguments().equals(after.arguments())
                    && before.statement().replace(before.text(), after.text()).equals(after.statement())) {
                changed.add(i);
            }
        }
        List<String> items = new ArrayList<>();
        for (int i : changed) {
            CallSite before = callSites.get(i);
            boolean enclosesAnother = changed.stream().anyMatch(other -> other != i
                    && before.statement().equals(callSites.get(other).statement())
                    && !before.text().equals(callSites.get(other).text())
                    && before.text().contains(callSites.get(other).text()));
            if (!enclosesAnother) {
                items.add(argumentChange(before, head.callSites.get(i)));
            }
        }
        return items;
    }

    /**
     * How one call's arguments changed (ticket #361): "swapped ?: branches in name(…)", "name(…):
     * a -> b" trimmed like a return value, or "arguments of name changed" when more than one
     * argument, their number, or a long part changed.
     */
    private static String argumentChange(CallSite before, CallSite after) {
        String fallback = "arguments of " + after.name() + " changed";
        if (before.arguments().size() != after.arguments().size()) {
            return fallback;
        }
        List<Integer> differing = new ArrayList<>();
        for (int i = 0; i < before.arguments().size(); i++) {
            if (!before.arguments().get(i).equals(after.arguments().get(i))) {
                differing.add(i);
            }
        }
        if (differing.size() != 1) {
            return fallback;
        }
        int index = differing.get(0);
        if (after.arguments().get(index).equals(before.swappedArguments().get(index))) {
            return SWAPPED + after.name() + "(…)";
        }
        return valueChange(before.arguments().get(index), after.arguments().get(index))
                .map(change -> after.name() + "(…): " + change)
                .orElse(fallback);
    }

    /**
     * A changed return value, trimmed to the part that differs (ticket #339): "return str -> EMPTY"
     * for {@code c ? str : s} becoming {@code c ? EMPTY : s}; "return value changed" when even
     * the differing parts are long.
     */
    private static String returnValueChange(String before, String after) {
        return valueChange(before, after).map(change -> "return " + change).orElse(RETURN_VALUE_CHANGED);
    }

    /**
     * A changed expression trimmed to the part that differs, "str -> EMPTY" (tickets #339, #351);
     * empty when the differing parts are long or not whole expressions.
     */
    private static Optional<String> valueChange(String before, String after) {
        List<int[]> beforeTokens = tokens(before);
        List<int[]> afterTokens = tokens(after);
        int prefix = 0;
        while (prefix < Math.min(beforeTokens.size(), afterTokens.size())
                && token(before, beforeTokens.get(prefix)).equals(token(after, afterTokens.get(prefix)))) {
            prefix++;
        }
        int suffix = 0;
        while (suffix < Math.min(beforeTokens.size(), afterTokens.size()) - prefix
                && token(before, beforeTokens.get(beforeTokens.size() - 1 - suffix))
                        .equals(token(after, afterTokens.get(afterTokens.size() - 1 - suffix)))) {
            suffix++;
        }
        // A pure insertion or deletion reads better anchored on the token before it: name -> name.trim().
        if ((prefix == beforeTokens.size() - suffix || prefix == afterTokens.size() - suffix) && prefix > 0) {
            prefix--;
        }
        List<String> removed = texts(before, beforeTokens.subList(prefix, beforeTokens.size() - suffix));
        List<String> added = texts(after, afterTokens.subList(prefix, afterTokens.size() - suffix));
        String removedText = span(before, beforeTokens, prefix, beforeTokens.size() - suffix);
        String addedText = span(after, afterTokens, prefix, afterTokens.size() - suffix);
        if (removedText.length() > MAX_SNIPPET_LENGTH || addedText.length() > MAX_SNIPPET_LENGTH
                || !outsideBlocks(texts(before, beforeTokens.subList(0, prefix)))
                || !wholeExpression(removed) || !wholeExpression(added)) {
            return Optional.empty();
        }
        return Optional.of(removedText + " -> " + addedText);
    }

    /** {@code expression} printed with its ?: branches swapped when it is a conditional, else as is (ticket #361). */
    private static String swapped(Expression expression) {
        if (!(expression instanceof ConditionalExpr conditional)) {
            return expression.toString();
        }
        ConditionalExpr swapped = conditional.clone();
        swapped.setThenExpr(conditional.getElseExpr().clone());
        swapped.setElseExpr(conditional.getThenExpr().clone());
        return swapped.toString();
    }

    /**
     * One call as written (ticket #361): its displayed and simple names, its own text, the text of
     * its enclosing statement and whether that is a return, and its arguments as written and with
     * ?: branches swapped. Immutable.
     */
    private record CallSite(String name, String simpleName, String text, String statement, boolean inReturn,
                            List<String> arguments, List<String> swappedArguments) {

        private CallSite {
            arguments = List.copyOf(arguments);
            swappedArguments = List.copyOf(swappedArguments);
        }

        static CallSite of(MethodCallExpr call) {
            Optional<Statement> statement = call.findAncestor(Statement.class);
            return new CallSite(displayName(call), call.getNameAsString(), call.toString(),
                    statement.map(Node::toString).orElse(call.toString()),
                    statement.filter(Statement::isReturnStmt).isPresent(),
                    call.getArguments().stream().map(Node::toString).toList(),
                    call.getArguments().stream().map(BodySummary::swapped).toList());
        }
    }

    /** {@code items} with each repeated item listed once, at its first position, as "item ×N" (ticket #352). */
    private static List<String> counted(List<String> items) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        items.forEach(item -> counts.merge(item, 1, Integer::sum));
        return counts.entrySet().stream()
                .map(entry -> entry.getValue() == 1 ? entry.getKey() : entry.getKey() + " ×" + entry.getValue())
                .toList();
    }

    /**
     * Whether {@code returnStmt} returns from {@code body} itself (ticket #351), rather than from a
     * lambda, an anonymous or local class's method, or an initializer nested inside it.
     */
    private static boolean returnsFrom(ReturnStmt returnStmt, Node body) {
        Optional<Node> node = returnStmt.getParentNode();
        while (node.isPresent() && node.get() != body) {
            if (node.get() instanceof LambdaExpr || node.get() instanceof CallableDeclaration<?>
                    || node.get() instanceof InitializerDeclaration) {
                return false;
            }
            node = node.get().getParentNode();
        }
        return true;
    }

    /** Whether a span starting after {@code prefix} lies outside any {@code {…}} body (ticket #351). */
    private static boolean outsideBlocks(List<String> prefix) {
        return prefix.stream().filter("{"::equals).count() == prefix.stream().filter("}"::equals).count();
    }

    /**
     * Whether {@code tokens} read as one complete expression (ticket #351): non-empty, starting and
     * ending on an operand, balanced brackets and conditionals, and no lambda arrow, block or
     * top-level comma that would make "before -> after" ambiguous.
     */
    private static boolean wholeExpression(List<String> tokens) {
        if (tokens.isEmpty() || !startsOperand(tokens.get(0)) || !endsOperand(tokens.get(tokens.size() - 1))) {
            return false;
        }
        int depth = 0;
        int conditionals = 0;
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            boolean methodReference = token.equals(":") && ((i + 1 < tokens.size() && tokens.get(i + 1).equals(":"))
                    || (i > 0 && tokens.get(i - 1).equals(":")));
            if (token.equals("{") || token.equals("}")
                    || (token.equals("-") && i + 1 < tokens.size() && tokens.get(i + 1).equals(">"))) {
                return false;
            }
            if (token.equals("(") || token.equals("[")) {
                depth++;
            } else if (token.equals(")") || token.equals("]")) {
                depth--;
            } else if (token.equals(",") && depth == 0) {
                return false;
            } else if (token.equals("?")) {
                conditionals++;
            } else if (token.equals(":") && !methodReference) {
                conditionals--;
            }
            if (depth < 0 || conditionals < 0) {
                return false;
            }
        }
        return depth == 0 && conditionals == 0;
    }

    private static boolean startsOperand(String token) {
        return isWord(token) || token.startsWith("\"") || token.equals("(") || token.equals("!") || token.equals("-");
    }

    private static boolean endsOperand(String token) {
        return isWord(token) || token.startsWith("\"") || token.equals(")") || token.equals("]");
    }

    private static boolean isWord(String token) {
        return Character.isLetterOrDigit(token.charAt(0)) || token.charAt(0) == '_' || token.charAt(0) == '$';
    }

    private static List<String> texts(String text, List<int[]> tokens) {
        return tokens.stream().map(bounds -> token(text, bounds)).toList();
    }

    private static List<int[]> tokens(String text) {
        List<int[]> tokens = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(text);
        while (matcher.find()) {
            tokens.add(new int[] {matcher.start(), matcher.end()});
        }
        return tokens;
    }

    private static String token(String text, int[] bounds) {
        return text.substring(bounds[0], bounds[1]);
    }

    private static String span(String text, List<int[]> tokens, int from, int to) {
        return from >= to ? "" : text.substring(tokens.get(from)[0], tokens.get(to - 1)[1]);
    }

    private static String snippet(String statement) {
        String trimmed = statement.endsWith(";") ? statement.substring(0, statement.length() - 1) : statement;
        return trimmed.length() <= MAX_SNIPPET_LENGTH ? trimmed : trimmed.substring(0, MAX_SNIPPET_LENGTH - 1) + "…";
    }

    /** The names {@code after} has more of than {@code before}, in {@code after}'s order. */
    private static List<String> increased(Map<String, Integer> before, Map<String, Integer> after) {
        return after.entrySet().stream()
                .filter(entry -> entry.getValue() > before.getOrDefault(entry.getKey(), 0))
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * {@code call} as a body summary names it (ticket #360): {@code receiver.name} when the
     * receiver is a plain name or {@code this.field}, the simple name for anything else — a chain,
     * a method result, {@code super}, or a capitalized name, which is taken to be a type.
     */
    private static String displayName(MethodCallExpr call) {
        return call.getScope().flatMap(BodySummary::receiverName)
                .map(receiver -> receiver + "." + call.getNameAsString())
                .orElse(call.getNameAsString());
    }

    private static Optional<String> receiverName(Expression scope) {
        String name;
        if (scope instanceof NameExpr nameExpr) {
            name = nameExpr.getNameAsString();
        } else if (scope instanceof FieldAccessExpr field && field.getScope().isThisExpr()) {
            name = field.getNameAsString();
        } else {
            return Optional.empty();
        }
        return Character.isUpperCase(name.charAt(0)) ? Optional.empty() : Optional.of(name);
    }

    /** {@code throw new X(...)} is named by its type; anything else ({@code throw e}) by its expression. */
    private static String thrownName(ThrowStmt throwStmt) {
        return throwStmt.getExpression().toObjectCreationExpr()
                .map(ObjectCreationExpr::getType)
                .map(ClassOrInterfaceType::getNameAsString)
                .orElse(throwStmt.getExpression().toString());
    }
}
