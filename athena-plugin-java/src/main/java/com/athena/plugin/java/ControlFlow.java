package com.athena.plugin.java;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.WhileStmt;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The control flow of one method body — its conditions, branches and loops — the narrow,
 * deterministic slice of "behavior" Athena compares between two revisions of a method
 * (epic #4 §53 item 10, ticket #18). A changed method call or return expression alone is
 * deliberately not control flow: too fuzzy to call a behavioral change.
 *
 * <p>Immutable; compared with {@link #describeChangeTo}.
 */
final class ControlFlow {

    private static final ControlFlow NONE = new ControlFlow(List.of(), List.of(), 0, 0);

    private final List<String> branchConditions;
    private final List<String> loopConditions;
    private final int branchCount;
    private final int loopCount;

    private ControlFlow(List<String> branchConditions, List<String> loopConditions, int branchCount, int loopCount) {
        this.branchConditions = List.copyOf(branchConditions);
        this.loopConditions = List.copyOf(loopConditions);
        this.branchCount = branchCount;
        this.loopCount = loopCount;
    }

    /** The control flow of {@code method}'s body; empty for a method without a body (abstract/interface). */
    static ControlFlow of(MethodDeclaration method) {
        return method.getBody().map(ControlFlow::of).orElse(NONE);
    }

    /** The control flow of an initializer block ({@code static { ... }}) or any other statement block. */
    static ControlFlow of(Node body) {
        List<IfStmt> ifs = body.findAll(IfStmt.class);
        List<String> branchConditions = ifs.stream().map(ifStmt -> ifStmt.getCondition().toString()).toList();
        List<String> loopConditions = new ArrayList<>();
        body.findAll(WhileStmt.class).forEach(loop -> loopConditions.add(loop.getCondition().toString()));
        body.findAll(DoStmt.class).forEach(loop -> loopConditions.add(loop.getCondition().toString()));
        body.findAll(ForStmt.class).forEach(loop -> loopConditions.add(loop.getCompare().map(Node::toString).orElse("")));
        int loopCount = loopConditions.size() + body.findAll(ForEachStmt.class).size();
        return new ControlFlow(branchConditions, loopConditions, branchCount(ifs), loopCount);
    }

    /**
     * What changed between this (base) control flow and {@code head}'s, e.g. {@code
     * "condition changed"}, {@code "branch added"} or {@code "branch removed, loop added"} —
     * empty when the control flow is the same. A changed branch count takes precedence over
     * comparing conditions: a newly added guard is an added branch, not merely a changed
     * condition list.
     */
    Optional<String> describeChangeTo(ControlFlow head) {
        List<String> changes = new ArrayList<>();
        if (head.branchCount != branchCount) {
            changes.add(head.branchCount > branchCount ? "branch added" : "branch removed");
        }
        boolean branchConditionsChanged = head.branchCount == branchCount && !head.branchConditions.equals(branchConditions);
        boolean loopConditionsChanged = head.loopCount == loopCount && !head.loopConditions.equals(loopConditions);
        if (branchConditionsChanged || loopConditionsChanged) {
            changes.add("condition changed");
        }
        if (head.loopCount != loopCount) {
            changes.add(head.loopCount > loopCount ? "loop added" : "loop removed");
        }
        return changes.isEmpty() ? Optional.empty() : Optional.of(String.join(", ", changes));
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
