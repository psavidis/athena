package com.athena.semantic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Clusters {@link Change}s by top-level module, the coarse unit a reviewer
 * actually thinks in ("the new crowdness-live module", "the ingestion
 * module") — see {@link ModuleGroup}. Purely structural (a path-segment
 * split), like {@link ChangeGrouper}: no AI, no confidence score. The AI
 * narrative that explains *why* a module was touched is a separate,
 * explicitly non-deterministic concern layered on top of this grouping
 * (see {@code com.athena.ai.ModuleNarrativeProvider}), not part of it.
 */
public final class ModuleGrouper {

    public List<ModuleGroup> group(List<Change> changes) {
        Map<String, List<Change>> byModule = new LinkedHashMap<>();
        for (Change change : changes) {
            byModule.computeIfAbsent(moduleOf(change), m -> new ArrayList<>()).add(change);
        }

        List<ModuleGroup> groups = new ArrayList<>();
        for (Map.Entry<String, List<Change>> entry : byModule.entrySet()) {
            groups.add(new ModuleGroup(entry.getKey(), entry.getValue()));
        }
        return groups;
    }

    /**
     * The first matched occurrence's first touched file's leading path
     * segment (e.g. "crowdness-live/src/main/..." -> "crowdness-live"), or
     * "(root)" for a file with no module segment (a single-module repo, or
     * a file living directly under the checkout root) — kept distinct from
     * an empty string so it still reads as a real, if unstructured, group.
     */
    private String moduleOf(Change change) {
        for (DetectedTransformation occurrence : change.matchedOccurrences()) {
            for (String file : occurrence.filesTouched()) {
                int separator = file.indexOf('/');
                if (separator > 0) {
                    return file.substring(0, separator);
                }
            }
        }
        return "(root)";
    }

    /**
     * The same leading-path-segment rule as {@link #moduleOf(Change)}, applied to a single
     * file path directly — used to compare a move's before/after module without a whole
     * {@link Change} to derive it from (e.g. {@code CapabilitySplitDetector}).
     */
    public static String moduleOf(String filePath) {
        int separator = filePath.indexOf('/');
        return separator > 0 ? filePath.substring(0, separator) : "(root)";
    }
}
