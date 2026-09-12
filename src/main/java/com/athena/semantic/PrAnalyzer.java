package com.athena.semantic;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Orchestrates the semantic engine's graceful-degradation fallback chain
 * (epic #4 §45) across one base/head revision pair:
 *
 * <ol>
 *   <li><b>Semantic Change Model</b> — full detection + grouping, for files
 *       that parse cleanly on both sides.</li>
 *   <li><b>Symbol-aware diff</b> — a file that fails to parse still gets an
 *       entry (rather than being silently dropped), even without a
 *       classified Change.</li>
 *   <li><b>Traditional textual diff</b> — always computed for every file,
 *       regardless of whether it reached level 1 or 2 (§44: raw diff access
 *       must never be blocked by analysis outcome).</li>
 * </ol>
 */
public final class PrAnalyzer {

    private final JavaSourceParser parser = new JavaSourceParser();

    public AnalysisResult analyze(Path baseRoot, Path headRoot) {
        Set<String> relativePaths = allRelativeJavaPaths(baseRoot, headRoot);

        Map<String, String> rawDiffsByFile = new LinkedHashMap<>();
        List<SymbolAwareDiffEntry> degradedEntries = new ArrayList<>();
        Set<String> parseableOnBothSides = new LinkedHashSet<>();

        for (String relativePath : relativePaths) {
            String baseText = readIfExists(baseRoot.resolve(relativePath));
            String headText = readIfExists(headRoot.resolve(relativePath));
            rawDiffsByFile.put(relativePath, unifiedDiff(relativePath, baseText, headText));

            ParseResult baseParse = baseText.isEmpty() ? null : parser.parse(baseText);
            ParseResult headParse = headText.isEmpty() ? null : parser.parse(headText);
            boolean baseOk = baseParse == null || baseParse.isSuccessful();
            boolean headOk = headParse == null || headParse.isSuccessful();

            if (baseOk && headOk) {
                parseableOnBothSides.add(relativePath);
            } else {
                String reason = !headOk ? headParse.errorMessage() : baseParse.errorMessage();
                degradedEntries.add(new SymbolAwareDiffEntry(relativePath, reason));
            }
        }

        List<Change> changes = parseableOnBothSides.isEmpty()
                ? List.of()
                : detectChanges(baseRoot, headRoot);

        AnalysisStatus status = status(relativePaths.size(), degradedEntries.size());

        return new AnalysisResult(status, changes, degradedEntries, rawDiffsByFile);
    }

    private AnalysisStatus status(int totalFiles, int degradedCount) {
        if (degradedCount == 0) {
            return AnalysisStatus.READY;
        }
        if (degradedCount == totalFiles) {
            return AnalysisStatus.ANALYSIS_FAILED;
        }
        return AnalysisStatus.PARTIALLY_ANALYZED;
    }

    private List<Change> detectChanges(Path baseRoot, Path headRoot) {
        List<DetectedTransformation> transformations = new TransformationDetector().detect(baseRoot, headRoot);
        return new ChangeGrouper().group(transformations);
    }

    private Set<String> allRelativeJavaPaths(Path baseRoot, Path headRoot) {
        Set<String> paths = new LinkedHashSet<>();
        paths.addAll(relativeJavaPaths(baseRoot));
        paths.addAll(relativeJavaPaths(headRoot));
        return paths;
    }

    private Set<String> relativeJavaPaths(Path root) {
        if (!Files.exists(root)) {
            return Set.of();
        }
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(p -> p.toString().endsWith(".java"))
                    .filter(Files::isRegularFile)
                    .map(p -> root.relativize(p).toString())
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String readIfExists(Path file) {
        if (!Files.exists(file)) {
            return "";
        }
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String unifiedDiff(String relativePath, String baseText, String headText) {
        if (baseText.equals(headText)) {
            return "";
        }
        return "--- a/" + relativePath + "\n"
                + "+++ b/" + relativePath + "\n"
                + "--- base ---\n" + baseText + "\n"
                + "--- head ---\n" + headText;
    }
}
