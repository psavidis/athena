package com.athena.plugin.java;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a method body calls, throws and returns — compared between two revisions so a body
 * modification says what changed inside the method (ticket #288), e.g. {@code "+triggerOnEvent,
 * +throw IllegalStateException"}, instead of just "the body changed". Calls are counted by
 * simple name, so changed arguments to an existing call are not a call added or removed.
 *
 * <p>Immutable; compared with {@link #describeChangeTo}.
 */
final class BodySummary {

    private static final BodySummary NONE = new BodySummary(Map.of(), Map.of(), List.of(), List.of());
    private static final int MAX_ITEMS = 5;
    private static final int MAX_SNIPPET_LENGTH = 40;
    private static final String RETURN_VALUE_CHANGED = "return value changed";
    private static final Pattern TOKEN = Pattern.compile("\\w+|\"(?:[^\"\\\\]|\\\\.)*\"|\\S");
    static final String OTHER_STATEMENTS = "other statements changed";

    private final Map<String, Integer> calls;
    private final Map<String, Integer> thrownTypes;
    private final int returnCount;
    // Ticket #339: the returned expressions in source order, and the body's top-level statements.
    private final List<String> returnExpressions;
    private final List<String> statements;

    private BodySummary(Map<String, Integer> calls, Map<String, Integer> thrownTypes, List<String> returnExpressions,
                        List<String> statements) {
        this.calls = Collections.unmodifiableMap(new LinkedHashMap<>(calls));
        this.thrownTypes = Collections.unmodifiableMap(new LinkedHashMap<>(thrownTypes));
        this.returnCount = returnExpressions.size();
        this.returnExpressions = List.copyOf(returnExpressions);
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
        callsInSourceOrder.forEach(call -> calls.merge(call.getNameAsString(), 1, Integer::sum));
        Map<String, Integer> thrownTypes = new LinkedHashMap<>();
        body.findAll(ThrowStmt.class).forEach(throwStmt -> thrownTypes.merge(thrownName(throwStmt), 1, Integer::sum));
        List<String> returns = body.findAll(ReturnStmt.class).stream()
                .filter(returnStmt -> returnsFrom(returnStmt, body))
                .sorted(Comparator.comparing(returnStmt -> returnStmt.getBegin().orElseThrow()))
                .map(returnStmt -> returnStmt.getExpression().map(Node::toString).orElse(""))
                .toList();
        List<String> statements = body instanceof BlockStmt block
                ? block.getStatements().stream().map(statement -> statement.toString().replaceAll("\\s+", " ").trim()).toList()
                : List.of();
        return new BodySummary(calls, thrownTypes, returns, statements);
    }

    /**
     * What changed between this (base) body and {@code head}'s: calls added ({@code +name}),
     * calls removed ({@code -name}), thrown types added and removed ({@code +throw Type}), a
     * moved statement, and a changed number of return statements or changed returned values
     * (ticket #339) — at most {@value #MAX_ITEMS} items, then
     * "…and N more". {@value #OTHER_STATEMENTS} when none of those differ.
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
                if (!returnExpressions.get(i).equals(head.returnExpressions.get(i))) {
                    items.add(returnValueChange(returnExpressions.get(i), head.returnExpressions.get(i)));
                }
            }
        }
        if (items.isEmpty()) {
            return OTHER_STATEMENTS;
        }
        if (items.size() <= MAX_ITEMS) {
            return String.join(", ", items);
        }
        return String.join(", ", items.subList(0, MAX_ITEMS)) + " …and " + (items.size() - MAX_ITEMS) + " more";
    }

    /**
     * Whether {@code head} makes the same calls: the same distinct methods, the same thrown types
     * the same number of times (#334), and as many return statements (ticket #319). How often a call appears doesn't matter, so
     * an unrolled loop still makes the same calls. It says nothing about conditions.
     */
    boolean makesSameCallsAs(BodySummary head) {
        // Calls by distinct name (an unrolled loop repeats them); throws by count (ticket #334): a
        // new throw of a type the method already throws is a real change, not a restructuring.
        return calls.keySet().equals(head.calls.keySet()) && thrownTypes.equals(head.thrownTypes)
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
     * A changed return value, trimmed to the part that differs (ticket #339): "return str -> EMPTY"
     * for {@code c ? str : s} becoming {@code c ? EMPTY : s}; "return value changed" when even
     * the differing parts are long.
     */
    private static String returnValueChange(String before, String after) {
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
            return RETURN_VALUE_CHANGED;
        }
        return "return " + removedText + " -> " + addedText;
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

    /** {@code throw new X(...)} is named by its type; anything else ({@code throw e}) by its expression. */
    private static String thrownName(ThrowStmt throwStmt) {
        return throwStmt.getExpression().toObjectCreationExpr()
                .map(ObjectCreationExpr::getType)
                .map(ClassOrInterfaceType::getNameAsString)
                .orElse(throwStmt.getExpression().toString());
    }
}
