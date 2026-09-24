package com.athena.plugin.java;

import com.github.javaparser.ast.expr.ArrayCreationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What changed in a field's initializer (ticket #316): for an array or collection literal
 * ({@code { … }}, {@code new T[] { … }}, {@code List.of(…)}, {@code Set.of(…)},
 * {@code Arrays.asList(…)}), the elements added and removed, e.g. {@code "+HALF_DAY"}; for
 * any other value, {@code "old -> new"} when both are short.
 */
final class FieldValues {

    private static final Set<String> COLLECTION_FACTORIES = Set.of("of", "asList", "copyOf");
    private static final int MAX_ITEMS = 5;
    private static final int MAX_SCALAR_LENGTH = 40;

    private FieldValues() {
    }

    /** {@code base} → {@code head}, where either may be absent (no initializer). */
    static String describeChange(Optional<Expression> base, Optional<Expression> head) {
        Optional<List<String>> baseElements = base.flatMap(FieldValues::elements);
        Optional<List<String>> headElements = head.flatMap(FieldValues::elements);
        if (baseElements.isPresent() && headElements.isPresent()) {
            List<String> items = new ArrayList<>();
            increased(baseElements.get(), headElements.get()).forEach(element -> items.add("+" + element));
            increased(headElements.get(), baseElements.get()).forEach(element -> items.add("-" + element));
            if (items.isEmpty()) {
                return "elements reordered";
            }
            return items.size() <= MAX_ITEMS ? String.join(", ", items)
                    : String.join(", ", items.subList(0, MAX_ITEMS)) + " …and " + (items.size() - MAX_ITEMS) + " more";
        }
        String before = base.map(Expression::toString).orElse("none");
        String after = head.map(Expression::toString).orElse("none");
        return before.length() <= MAX_SCALAR_LENGTH && after.length() <= MAX_SCALAR_LENGTH
                ? before + " -> " + after : "value changed";
    }

    private static Optional<List<String>> elements(Expression expression) {
        if (expression instanceof ArrayInitializerExpr array) {
            return Optional.of(array.getValues().stream().map(FieldValues::display).toList());
        }
        if (expression instanceof ArrayCreationExpr creation && creation.getInitializer().isPresent()) {
            return elements(creation.getInitializer().get());
        }
        if (expression instanceof MethodCallExpr call && COLLECTION_FACTORIES.contains(call.getNameAsString())) {
            return Optional.of(call.getArguments().stream().map(FieldValues::display).toList());
        }
        return Optional.empty();
    }

    /** A constant reference by its simple name ({@code PeriodicityType.HALF_DAY} → {@code HALF_DAY}). */
    private static String display(Expression element) {
        return element instanceof FieldAccessExpr access ? access.getNameAsString() : element.toString();
    }

    /** The elements {@code after} has more of than {@code before}, in {@code after}'s order. */
    private static List<String> increased(List<String> before, List<String> after) {
        Map<String, Integer> remaining = new LinkedHashMap<>();
        before.forEach(element -> remaining.merge(element, 1, Integer::sum));
        List<String> added = new ArrayList<>();
        for (String element : after) {
            if (remaining.getOrDefault(element, 0) > 0) {
                remaining.merge(element, -1, Integer::sum);
            } else if (!added.contains(element)) {
                added.add(element);
            }
        }
        return added;
    }
}
