package com.athena.semantic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Clusters {@link Change}s by module, the coarse unit a reviewer actually
 * thinks in ("the new crowdness-live module", "the ingestion module") — see
 * {@link ModuleGroup}. Where a module ends is read from the project's build
 * descriptors when a {@link ModuleLayout} with the revision roots is given
 * (ticket #292), else from the leading path segment. Deterministic, like
 * {@link ChangeGrouper}: no AI, no confidence score. The AI
 * narrative that explains *why* a module was touched is a separate,
 * explicitly non-deterministic concern layered on top of this grouping
 * (see {@code com.athena.ai.ModuleNarrativeProvider}), not part of it.
 */
public final class ModuleGrouper {

    private final ModuleLayout layout;

    /** Groups by the leading path segment alone (see {@link ModuleLayout#pathBased()}). */
    public ModuleGrouper() {
        this(ModuleLayout.pathBased());
    }

    /** Groups by the module {@code layout} places each Change's first touched file in (ticket #292). */
    public ModuleGrouper(ModuleLayout layout) {
        this.layout = layout;
    }

    public List<ModuleGroup> group(List<Change> changes) {
        Map<String, List<Change>> byDirectory = new LinkedHashMap<>();
        for (Change change : changes) {
            byDirectory.computeIfAbsent(directoryOf(change), m -> new ArrayList<>()).add(change);
        }

        List<ModuleGroup> groups = new ArrayList<>();
        for (Map.Entry<String, List<Change>> entry : byDirectory.entrySet()) {
            groups.add(new ModuleGroup(layout.nameOf(entry.getKey()), entry.getKey(), entry.getValue()));
        }
        return groups;
    }

    /** The name of the module {@code filePath} belongs to under this grouper's layout. */
    public String moduleNameOf(String filePath) {
        return layout.nameOf(layout.directoryOf(filePath));
    }

    /**
     * The module directory of the first touched file (in matched-occurrence order) that isn't
     * at the repository root, or "" — the root module — when every touched file is.
     */
    private String directoryOf(Change change) {
        for (DetectedTransformation occurrence : change.matchedOccurrences()) {
            for (String file : occurrence.filesTouched()) {
                String directory = layout.directoryOf(file);
                if (!directory.isEmpty()) {
                    return directory;
                }
            }
        }
        return "";
    }

    /**
     * The leading-path-segment rule of {@link ModuleLayout#pathBased()}, applied to a single
     * file path directly — used to compare a move's before/after module without a whole
     * {@link Change} to derive it from (e.g. {@code CapabilitySplitDetector}).
     */
    public static String moduleOf(String filePath) {
        int separator = filePath.indexOf('/');
        return separator > 0 ? filePath.substring(0, separator) : "(root)";
    }

    /**
     * The path segment immediately before {@code src/} (Maven/Gradle's own multi-module
     * convention, each with its own {@code src/} root) — e.g.
     * "crowdness-domain/crowdness-domain-connect/src/main/..." -> "crowdness-domain-connect".
     * Finer-grained than {@link #moduleOf(String)}'s single leading segment, which only
     * reaches the coarse top-level territory ("crowdness-domain") and can't tell apart two
     * sub-modules nested inside it — the distinction {@link CapabilitySplitDetector} needs
     * to recognize a move between them as crossing a real module boundary. Falls back to
     * {@link #moduleOf(String)}'s rule when the path has no {@code src/} segment at all.
     */
    public static String buildModuleOf(String filePath) {
        String[] segments = filePath.split("/");
        for (int i = 0; i < segments.length; i++) {
            if (segments[i].equals("src")) {
                return i > 0 ? segments[i - 1] : "(root)";
            }
        }
        return moduleOf(filePath);
    }
}
