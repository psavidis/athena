package com.athena.semantic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Groups individually-detected transformations that represent the same
 * conceptual operation into a single {@link Change}, using deterministic
 * structural matching only (no confidence score) — see epic #4 §11/§12.
 *
 * <p>Two transformations join the same Change's matched occurrences when
 * they share the same {@link TransformationKind} and the same involved-
 * symbol description pattern (e.g. the same "OldName#member" ->
 * "NewName#member" rename shape). A transformation that shares the same
 * symbol names but a different kind (i.e. it superficially resembles the
 * group but doesn't match its transformation shape) is attached to that
 * Change as an exception rather than silently folded in, and rather than
 * becoming its own disconnected Change.
 */
public final class ChangeGrouper {

    public List<Change> group(List<DetectedTransformation> transformations) {
        Map<GroupKey, List<DetectedTransformation>> groups = new LinkedHashMap<>();
        for (DetectedTransformation t : transformations) {
            GroupKey key = new GroupKey(t.kind(), t.involvedDescriptions());
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(t);
        }

        // Decide up front, for every pair of distinct groups that share the same
        // involved-symbol names, which group is the "real" pattern (the larger one —
        // more occurrences means more evidence it's the dominant transformation) and
        // which is the exception folded into it. This must be resolved before any
        // Change is created, since map iteration order doesn't guarantee the dominant
        // group is visited first.
        List<GroupKey> consumedAsExceptions = new ArrayList<>();
        for (GroupKey key : groups.keySet()) {
            if (consumedAsExceptions.contains(key)) continue;
            for (GroupKey other : groups.keySet()) {
                if (other.equals(key) || consumedAsExceptions.contains(other)) continue;
                if (other.involvedDescriptions.equals(key.involvedDescriptions)
                        && groups.get(other).size() < groups.get(key).size()) {
                    consumedAsExceptions.add(other);
                }
            }
        }

        List<Change> changes = new ArrayList<>();
        for (Map.Entry<GroupKey, List<DetectedTransformation>> entry : groups.entrySet()) {
            GroupKey key = entry.getKey();
            if (consumedAsExceptions.contains(key)) {
                continue;
            }

            List<DetectedTransformation> matched = entry.getValue();
            List<DetectedTransformation> exceptions = new ArrayList<>();
            for (GroupKey exceptionKey : consumedAsExceptions) {
                if (exceptionKey.involvedDescriptions.equals(key.involvedDescriptions)) {
                    exceptions.addAll(groups.get(exceptionKey));
                }
            }

            String title = title(key, matched.get(0));
            changes.add(new Change(title, key.kind, matched, exceptions));
        }

        return changes;
    }

    private String title(GroupKey key, DetectedTransformation representative) {
        return switch (key.kind) {
            case RENAME_SYMBOL -> "Rename " + arrowJoin(key.involvedDescriptions);
            case MECHANICAL_REPLACEMENT -> "Rename " + String.join(" -> ", key.involvedDescriptions);
            case MOVE_SYMBOL -> "Move " + arrowJoin(key.involvedDescriptions);
            case EXTRACT_METHOD -> "Extract " + key.involvedDescriptions.get(key.involvedDescriptions.size() - 1);
            case ADD_SYMBOL -> "Add " + key.involvedDescriptions.get(0);
            case REMOVE_SYMBOL -> "Remove " + key.involvedDescriptions.get(0);
            case CHANGE_METHOD_SIGNATURE -> "Change signature of " + key.involvedDescriptions.get(0);
            case FORMATTING_ONLY -> "Formatting: " + key.involvedDescriptions.get(0);
        };
    }

    /**
     * Joins "before -> after" descriptions shaped "EnclosingType#member" for display, dropping
     * the repeated enclosing-type prefix on the right-hand side when it's the same on both sides
     * (e.g. "TestClass#a -> TestClass#b" reads as "TestClass#a -> #b") — the type name doesn't
     * carry information twice just because two descriptions happen to share it.
     */
    private String arrowJoin(List<String> descriptions) {
        if (descriptions.size() != 2) {
            return String.join(" -> ", descriptions);
        }
        String before = descriptions.get(0);
        String after = descriptions.get(1);
        int separator = before.indexOf('#');
        if (separator < 0) {
            return before + " -> " + after;
        }
        String enclosingType = before.substring(0, separator + 1);
        if (after.startsWith(enclosingType)) {
            return before + " -> " + after.substring(enclosingType.length());
        }
        return before + " -> " + after;
    }

    private record GroupKey(TransformationKind kind, List<String> involvedDescriptions) {
    }
}
