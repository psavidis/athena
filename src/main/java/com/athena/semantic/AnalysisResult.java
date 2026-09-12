package com.athena.semantic;

import java.util.List;
import java.util.Map;

/**
 * The outcome of analyzing one base/head revision pair through the
 * graceful-degradation fallback chain (epic #4 §45): the {@link
 * AnalysisStatus} reached, whatever {@link Change}s and {@link
 * SymbolAwareDiffEntry} entries were produced along the way, and the raw
 * textual diff — always available regardless of status (§44).
 */
public final class AnalysisResult {

    private final AnalysisStatus status;
    private final List<Change> changes;
    private final List<SymbolAwareDiffEntry> symbolAwareDiffEntries;
    private final Map<String, String> rawDiffsByFile;

    AnalysisResult(AnalysisStatus status, List<Change> changes,
                   List<SymbolAwareDiffEntry> symbolAwareDiffEntries, Map<String, String> rawDiffsByFile) {
        this.status = status;
        this.changes = List.copyOf(changes);
        this.symbolAwareDiffEntries = List.copyOf(symbolAwareDiffEntries);
        this.rawDiffsByFile = Map.copyOf(rawDiffsByFile);
    }

    public AnalysisStatus status() {
        return status;
    }

    /** Changes detected via the full Semantic Change Model, for the files that reached it. */
    public List<Change> changes() {
        return changes;
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
