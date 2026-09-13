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
import java.util.Optional;
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
    private final StructuralTaxonomyClassifier structuralClassifier =
            new StructuralTaxonomyClassifier(new TaxonomyLoader().load(SemanticDimension.STRUCTURAL));

    public AnalysisResult analyze(Path baseRoot, Path headRoot) {
        Set<String> relativePaths = allRelativeJavaPaths(baseRoot, headRoot);

        Map<String, String> rawDiffsByFile = new LinkedHashMap<>();
        List<SymbolAwareDiffEntry> degradedEntries = new ArrayList<>();
        boolean anyParseable = false;

        for (String relativePath : relativePaths) {
            Optional<String> baseText = readIfExists(baseRoot.resolve(relativePath));
            Optional<String> headText = readIfExists(headRoot.resolve(relativePath));
            rawDiffsByFile.put(relativePath,
                    unifiedDiff(relativePath, baseText.orElse(""), headText.orElse("")));

            Optional<ParseResult> baseParse = baseText.map(parser::parse);
            Optional<ParseResult> headParse = headText.map(parser::parse);
            boolean baseOk = baseParse.map(ParseResult::isSuccessful).orElse(true);
            boolean headOk = headParse.map(ParseResult::isSuccessful).orElse(true);

            if (baseOk && headOk) {
                anyParseable = true;
            } else {
                degradedEntries.add(new SymbolAwareDiffEntry(relativePath,
                        degradationReason(baseParse, baseOk, headParse, headOk)));
            }
        }

        List<Change> changes = anyParseable ? detectChanges(baseRoot, headRoot) : List.of();
        List<SemanticProfile> semanticProfiles = changes.stream().map(this::classify).toList();

        AnalysisStatus status = status(relativePaths.size(), degradedEntries.size());

        return new AnalysisResult(status, changes, semanticProfiles, degradedEntries, rawDiffsByFile);
    }

    /**
     * Classifies one Change along whichever semantic dimensions have a classifier today
     * (ticket #86) — currently structural only; other dimensions (pattern, framework,
     * responsibility, feature, architecture, intent) have taxonomies but no classifier yet,
     * so a Change simply carries no classification along those dimensions rather than a
     * guessed or placeholder one.
     */
    private SemanticProfile classify(Change change) {
        SemanticProfile profile = SemanticProfile.empty(change);
        Optional<SemanticClassification> structural = structuralClassifier.classify(change);
        if (structural.isPresent()) {
            profile = profile.with(SemanticDimension.STRUCTURAL, structural.get());
        }
        return profile;
    }

    private String degradationReason(Optional<ParseResult> baseParse, boolean baseOk,
                                      Optional<ParseResult> headParse, boolean headOk) {
        List<String> reasons = new ArrayList<>();
        if (!baseOk) {
            reasons.add("base: " + baseParse.orElseThrow().errorMessage());
        }
        if (!headOk) {
            reasons.add("head: " + headParse.orElseThrow().errorMessage());
        }
        return String.join("; ", reasons);
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
        // A walk failure (permission error, symlink loop, concurrent deletion) must not
        // crash the whole analysis — it degrades to "this root contributed no files"
        // rather than violating the graceful-degradation guarantee this class exists for.
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(p -> p.toString().endsWith(".java"))
                    .filter(Files::isRegularFile)
                    .map(p -> root.relativize(p).toString())
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        } catch (IOException | UncheckedIOException e) {
            return Set.of();
        }
    }

    /** Empty when the file doesn't exist in this revision at all; present (possibly blank) otherwise. */
    private Optional<String> readIfExists(Path file) {
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(file));
        } catch (IOException e) {
            return Optional.empty();
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
