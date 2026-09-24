package com.athena.plugin.java;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a method body calls, throws and returns — compared between two revisions so a body
 * modification says what changed inside the method (ticket #288), e.g. {@code "+triggerOnEvent,
 * +throw IllegalStateException"}, instead of just "the body changed". Calls are counted by
 * simple name, so changed arguments to an existing call are not a call added or removed.
 *
 * <p>Immutable; compared with {@link #describeChangeTo}.
 */
final class BodySummary {

    private static final BodySummary NONE = new BodySummary(Map.of(), Map.of(), 0);
    private static final int MAX_ITEMS = 5;
    static final String OTHER_STATEMENTS = "other statements changed";

    private final Map<String, Integer> calls;
    private final Map<String, Integer> thrownTypes;
    private final int returnCount;

    private BodySummary(Map<String, Integer> calls, Map<String, Integer> thrownTypes, int returnCount) {
        this.calls = Collections.unmodifiableMap(new LinkedHashMap<>(calls));
        this.thrownTypes = Collections.unmodifiableMap(new LinkedHashMap<>(thrownTypes));
        this.returnCount = returnCount;
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
        return new BodySummary(calls, thrownTypes, body.findAll(ReturnStmt.class).size());
    }

    /**
     * What changed between this (base) body and {@code head}'s: calls added ({@code +name}),
     * calls removed ({@code -name}), thrown types added and removed ({@code +throw Type}) and a
     * changed number of return statements — at most {@value #MAX_ITEMS} items, then
     * "…and N more". {@value #OTHER_STATEMENTS} when none of those differ.
     */
    String describeChangeTo(BodySummary head) {
        List<String> items = new ArrayList<>();
        increased(calls, head.calls).forEach(name -> items.add("+" + name));
        increased(head.calls, calls).forEach(name -> items.add("-" + name));
        increased(thrownTypes, head.thrownTypes).forEach(type -> items.add("+throw " + type));
        increased(head.thrownTypes, thrownTypes).forEach(type -> items.add("-throw " + type));
        if (head.returnCount != returnCount) {
            items.add("return statements " + returnCount + " → " + head.returnCount);
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
