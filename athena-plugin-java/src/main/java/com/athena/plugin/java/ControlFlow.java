package com.athena.plugin.java;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.nodeTypes.SwitchNode;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.WhileStmt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The control flow of one method body — its conditions, branches and loops — the narrow,
 * deterministic slice of "behavior" Athena compares between two revisions of a method
 * (epic #4 §53 item 10, ticket #18). A changed method call or return expression alone is
 * deliberately not control flow: too fuzzy to call a behavioral change.
 *
 * <p>{@code switch} statements and expressions count like if/else (ticket #289): each case
 * group — fall-through labels together, a {@code default} included — is a branch, and its
 * labels are its condition ({@code selector == LABEL}). So an if-chain rewritten as a switch
 * with the same conditions is recognized as such rather than as removed branches.
 *
 * <p>Immutable; compared with {@link #describeChangeTo}.
 */
final class ControlFlow {

    private static final ControlFlow NONE = new ControlFlow(List.of(), List.of(), 0, 0, List.of(), 0);

    private final List<String> branchConditions;
    private final List<String> loopConditions;
    private final int branchCount;
    private final int loopCount;
    // Every branch condition split into its ||-alternatives and sorted: the same for an
    // if-chain and a switch testing the same values, whatever form or order they take.
    private final List<String> conditionAlternatives;
    private final int switchBranchCount;

    private ControlFlow(List<String> branchConditions, List<String> loopConditions, int branchCount, int loopCount,
                        List<String> conditionAlternatives, int switchBranchCount) {
        this.branchConditions = List.copyOf(branchConditions);
        this.loopConditions = List.copyOf(loopConditions);
        this.branchCount = branchCount;
        this.loopCount = loopCount;
        this.conditionAlternatives = conditionAlternatives.stream().sorted().toList();
        this.switchBranchCount = switchBranchCount;
    }

    /** The control flow of {@code method}'s body; empty for a method without a body (abstract/interface). */
    static ControlFlow of(MethodDeclaration method) {
        return method.getBody().map(ControlFlow::of).orElse(NONE);
    }

    /** The control flow of an initializer block ({@code static { ... }}) or any other statement block. */
    static ControlFlow of(Node body) {
        List<IfStmt> ifs = body.findAll(IfStmt.class);
        List<String> branchConditions = new ArrayList<>();
        List<String> conditionAlternatives = new ArrayList<>();
        for (IfStmt ifStmt : ifs) {
            branchConditions.add(ifStmt.getCondition().toString());
            conditionAlternatives.addAll(alternatives(ifStmt.getCondition()));
        }
        List<List<String>> switchBranches = switchBranches(body);
        for (List<String> labels : switchBranches) {
            branchConditions.add(String.join(" || ", labels));
            conditionAlternatives.addAll(labels);
        }
        List<String> loopConditions = new ArrayList<>();
        body.findAll(WhileStmt.class).forEach(loop -> loopConditions.add(loop.getCondition().toString()));
        body.findAll(DoStmt.class).forEach(loop -> loopConditions.add(loop.getCondition().toString()));
        body.findAll(ForStmt.class).forEach(loop -> loopConditions.add(loop.getCompare().map(Node::toString).orElse("")));
        int loopCount = loopConditions.size() + body.findAll(ForEachStmt.class).size();
        return new ControlFlow(branchConditions, loopConditions, branchCount(ifs) + switchBranches.size(), loopCount,
                conditionAlternatives, switchBranches.size());
    }

    /**
     * What changed between this (base) control flow and {@code head}'s, e.g. {@code
     * "condition changed"}, {@code "branch added"} or {@code "branch removed, loop added"} —
     * empty when the control flow is the same. A changed branch count takes precedence over
     * comparing conditions: a newly added guard is an added branch, not merely a changed
     * condition list.
     */
    Optional<String> describeChangeTo(ControlFlow head) {
        List<String> changes = changesTo(head);
        return changes.isEmpty() ? Optional.empty() : Optional.of(String.join(", ", changes));
    }

    /**
     * Whether {@code head} restructures this control flow rather than adding, removing or
     * changing a single piece of it (ticket #357): an if-chain rewritten as a switch (or back), or
     * two or more kinds of change together, such as an unrolled loop's "branch added, condition
     * changed". A lone added guard, removed branch or changed condition is a behavior change in
     * itself, so "same calls" never softens it.
     */
    boolean restructures(ControlFlow head) {
        return rewrittenAs(head) || changesTo(head).size() >= 2;
    }

    // The same conditions moved between if/else and switch. The branch count may differ by
    // one: a statement after an if-chain becomes a switch's default, and vice versa.
    private boolean rewrittenAs(ControlFlow head) {
        return head.switchBranchCount != switchBranchCount
                && Math.abs(head.branchCount - branchCount) <= 1
                && head.conditionAlternatives.equals(conditionAlternatives);
    }

    private List<String> changesTo(ControlFlow head) {
        List<String> changes = new ArrayList<>();
        boolean rewritten = rewrittenAs(head);
        if (rewritten) {
            changes.add(head.switchBranchCount > switchBranchCount ? "if-chain rewritten as switch" : "switch rewritten as if-chain");
        } else if (head.branchCount != branchCount) {
            changes.add(head.branchCount > branchCount ? "branch added" : "branch removed");
        }
        boolean branchConditionsChanged = head.branchCount == branchCount && !rewritten
                && !head.branchConditions.equals(branchConditions);
        boolean loopConditionsChanged = head.loopCount == loopCount && !head.loopConditions.equals(loopConditions);
        if (branchConditionsChanged || loopConditionsChanged) {
            changes.add("condition changed");
        }
        if (head.loopCount != loopCount) {
            changes.add(head.loopCount > loopCount ? "loop added" : "loop removed");
        }
        return changes;
    }

    /** A condition's {@code ||}-alternatives: {@code a == 1 || a == 2} is {@code [a == 1, a == 2]}. */
    private static List<String> alternatives(Expression condition) {
        Expression unwrapped = condition.isEnclosedExpr() ? condition.asEnclosedExpr().getInner() : condition;
        if (unwrapped instanceof BinaryExpr binary && binary.getOperator() == BinaryExpr.Operator.OR) {
            List<String> both = new ArrayList<>(alternatives(binary.getLeft()));
            both.addAll(alternatives(binary.getRight()));
            return both;
        }
        return List.of(unwrapped.toString());
    }

    /**
     * Every switch statement's and expression's branches, in source order, each as its
     * conditions ({@code selector == LABEL}, empty for a plain {@code default}). Colon-form
     * labels with no statements fall through, so they join the next entry's branch.
     */
    private static List<List<String>> switchBranches(Node body) {
        List<SwitchNode> switches = new ArrayList<>();
        body.walk(Node.class, node -> {
            if (node instanceof SwitchNode switchNode) {
                switches.add(switchNode);
            }
        });
        switches.sort(Comparator.comparing(switchNode -> ((Node) switchNode).getBegin().orElseThrow()));
        List<List<String>> branches = new ArrayList<>();
        for (SwitchNode switchNode : switches) {
            String selector = switchNode.getSelector().toString();
            List<String> pending = new ArrayList<>();
            for (SwitchEntry entry : switchNode.getEntries()) {
                entry.getLabels().forEach(label -> pending.add(selector + " == " + label));
                boolean fallsThrough = entry.getType() == SwitchEntry.Type.STATEMENT_GROUP && entry.getStatements().isEmpty();
                if (!fallsThrough) {
                    branches.add(List.copyOf(pending));
                    pending.clear();
                }
            }
            if (!pending.isEmpty()) {
                branches.add(List.copyOf(pending));
            }
        }
        return branches;
    }

    /** One per {@code if}, plus one per {@code else if}/{@code else} hanging off it. */
    private static int branchCount(List<IfStmt> ifs) {
        int count = 0;
        for (IfStmt ifStmt : ifs) {
            count++;
            Statement elseBranch = ifStmt.getElseStmt().orElse(null);
            // An `else if` is itself one of `ifs` and counted there; only a final plain
            // `else` adds a branch of its own.
            if (elseBranch != null && !(elseBranch instanceof IfStmt)) {
                count++;
            }
        }
        return count;
    }
}
