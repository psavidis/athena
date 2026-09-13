package com.athena.semantic;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The outcome of analyzing one base/head revision pair through the
 * graceful-degradation fallback chain (epic #4 §45): the {@link
 * AnalysisStatus} reached, whatever {@link Change}s and {@link
 * SymbolAwareDiffEntry} entries were produced along the way, and the raw
 * textual diff — always available regardless of status (§44).
 *
 * <p>Each {@link Change} also carries a {@link SemanticProfile} (ticket
 * #86) — one per Change, in the same order as {@link #changes()} —
 * classifying it along whichever semantic dimensions a classifier exists
 * for today. A Change with no classifier support yet simply carries an
 * {@link SemanticProfile#empty(Change) empty} profile, never a missing one.
 */
public final class AnalysisResult {

    private final AnalysisStatus status;
    private final List<Change> changes;
    private final List<SemanticProfile> semanticProfiles;
    private final List<SymbolAwareDiffEntry> symbolAwareDiffEntries;
    private final Map<String, String> rawDiffsByFile;

    AnalysisResult(AnalysisStatus status, List<Change> changes, List<SemanticProfile> semanticProfiles,
                   List<SymbolAwareDiffEntry> symbolAwareDiffEntries, Map<String, String> rawDiffsByFile) {
        this.status = status;
        this.changes = List.copyOf(changes);
        this.semanticProfiles = List.copyOf(semanticProfiles);
        this.symbolAwareDiffEntries = List.copyOf(symbolAwareDiffEntries);
        this.rawDiffsByFile = Collections.unmodifiableMap(new LinkedHashMap<>(rawDiffsByFile));
    }

    public AnalysisStatus status() {
        return status;
    }

    /** Changes detected via the full Semantic Change Model, for the files that reached it. */
    public List<Change> changes() {
        return changes;
    }

    /** Every Change's {@link SemanticProfile}, in the same order as {@link #changes()} — one per Change, never omitted. */
    public List<SemanticProfile> semanticProfiles() {
        return semanticProfiles;
    }

    /** This Change's semantic profile, or an empty one if {@code change} isn't among {@link #changes()}. */
    public SemanticProfile semanticProfileFor(Change change) {
        int index = changes.indexOf(change);
        return index < 0 ? SemanticProfile.empty(change) : semanticProfiles.get(index);
    }

    /** Files that fell back to a symbol-aware diff instead of a fully classified Change. */
    public List<SymbolAwareDiffEntry> symbolAwareDiffEntries() {
        return symbolAwareDiffEntries;
    }

    /** The full raw base/head diff across every file, concatenated. */
    public String rawDiff() {
        return String.join("\n", rawDiffsByFile.values());
    }

    /** The raw base/head diff for one file, regardless of analysis status. Empty if the file is unchanged/unknown. */
    public String rawDiffFor(String filePath) {
        return rawDiffsByFile.getOrDefault(filePath, "");
    }
}
