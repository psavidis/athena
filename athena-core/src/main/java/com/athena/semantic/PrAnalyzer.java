package com.athena.semantic;

import com.athena.semantic.spi.FrameworkPlugin;
import com.athena.semantic.spi.LanguagePlugin;
import com.athena.semantic.spi.ParseOutcome;

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
 * (epic #4 §45) across one base/head revision pair, routed through whichever
 * {@link LanguagePlugin}/{@link FrameworkPlugin} instances the caller supplies
 * (see {@code athena-plugin-java}/{@code athena-plugin-spring} for the first
 * implementations, discovered via {@link java.util.ServiceLoader} by the
 * application):
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

    private final List<LanguagePlugin> languagePlugins;
    private final List<FrameworkPlugin> frameworkPlugins;
    private final StructuralTaxonomyClassifier structuralClassifier;
    private final ResponsibilityTaxonomyClassifier responsibilityClassifier;
    private final ArchitectureTaxonomyClassifier architectureClassifier;
    private final PatternTaxonomyClassifier patternClassifier;
    private final FlowTaxonomyClassifier flowClassifier;
    private final IntentTaxonomyClassifier intentClassifier;
    private final Taxonomy frameworkTaxonomy;

    public PrAnalyzer(List<LanguagePlugin> languagePlugins, List<FrameworkPlugin> frameworkPlugins) {
        this.languagePlugins = List.copyOf(languagePlugins);
        this.frameworkPlugins = List.copyOf(frameworkPlugins);
        TaxonomyLoader taxonomyLoader = new TaxonomyLoader();
        structuralClassifier = new StructuralTaxonomyClassifier(taxonomyLoader.load(SemanticDimension.STRUCTURAL));
        responsibilityClassifier = new ResponsibilityTaxonomyClassifier(taxonomyLoader.load(SemanticDimension.RESPONSIBILITY));
        architectureClassifier = new ArchitectureTaxonomyClassifier(taxonomyLoader.load(SemanticDimension.ARCHITECTURE));
        patternClassifier = new PatternTaxonomyClassifier(taxonomyLoader.load(SemanticDimension.PATTERN));
        flowClassifier = new FlowTaxonomyClassifier(taxonomyLoader.load(SemanticDimension.FEATURE));
        intentClassifier = new IntentTaxonomyClassifier(taxonomyLoader.load(SemanticDimension.INTENT));
        frameworkTaxonomy = taxonomyLoader.load(SemanticDimension.FRAMEWORK);
    }

    public AnalysisResult analyze(Path baseRoot, Path headRoot) {
        Set<String> relativePaths = allRelativeSourcePaths(baseRoot, headRoot);

        Map<String, String> rawDiffsByFile = new LinkedHashMap<>();
        List<SymbolAwareDiffEntry> degradedEntries = new ArrayList<>();
        boolean anyParseable = false;

        for (String relativePath : relativePaths) {
            Optional<String> baseText = readIfExists(baseRoot.resolve(relativePath));
            Optional<String> headText = readIfExists(headRoot.resolve(relativePath));
            rawDiffsByFile.put(relativePath,
                    unifiedDiff(relativePath, baseText.orElse(""), headText.orElse("")));

            Optional<LanguagePlugin> plugin = pluginFor(baseRoot.resolve(relativePath), headRoot.resolve(relativePath));
            Optional<ParseOutcome> baseParse = baseText.flatMap(text -> plugin.map(p -> p.checkParses(text)));
            Optional<ParseOutcome> headParse = headText.flatMap(text -> plugin.map(p -> p.checkParses(text)));
            boolean baseOk = baseParse.map(ParseOutcome::isSuccessful).orElse(true);
            boolean headOk = headParse.map(ParseOutcome::isSuccessful).orElse(true);

            if (baseOk && headOk) {
                anyParseable = true;
            } else {
                degradedEntries.add(new SymbolAwareDiffEntry(relativePath,
                        degradationReason(baseParse, baseOk, headParse, headOk)));
            }
        }

        List<Change> changes = anyParseable ? detectChanges(baseRoot, headRoot) : List.of();
        Map<Change, SemanticClassification> dependencyInjectionMatches = patternClassifier.classifyDependencyInjection(changes);
        List<SemanticProfile> semanticProfiles = changes.stream()
                .map(change -> classify(change, dependencyInjectionMatches.get(change)))
                .toList();

        AnalysisStatus status = status(relativePaths.size(), degradedEntries.size());

        return new AnalysisResult(status, changes, semanticProfiles, degradedEntries, rawDiffsByFile);
    }

    /**
     * Classifies one Change along whichever semantic dimensions have a classifier today
     * (ticket #86): structural, responsibility, feature/flow (ticket #92), and — for a
     * newly-added/renamed class only — architecture, pattern, and framework (all three read
     * a class's own name/annotations, which only a class actually taking on that identity
     * speaks to). Intent (ticket #93) is classified last, since it correlates against this
     * same Change's other already-assembled dimension classifications rather than the diff.
     *
     * @param dependencyInjectionMatch this Change's dependency-injection Pattern
     *        classification, precomputed once across the whole Change set by
     *        {@link PatternTaxonomyClassifier#classifyDependencyInjection} — the only
     *        classification here that needs more than this one Change to decide.
     */
    private SemanticProfile classify(Change change, SemanticClassification dependencyInjectionMatch) {
        SemanticProfile profile = SemanticProfile.empty(change);
        profile = withClassification(profile, SemanticDimension.STRUCTURAL, structuralClassifier.classify(change));
        profile = withClassification(profile, SemanticDimension.RESPONSIBILITY, responsibilityClassifier.classify(change));
        profile = withClassification(profile, SemanticDimension.ARCHITECTURE, architectureClassifier.classify(change));
        profile = withClassification(profile, SemanticDimension.PATTERN, patternClassifier.classify(change));
        profile = withClassification(profile, SemanticDimension.PATTERN, Optional.ofNullable(dependencyInjectionMatch));
        profile = withClassification(profile, SemanticDimension.FEATURE, flowClassifier.classify(change));
        profile = withClassification(profile, SemanticDimension.FRAMEWORK, classifyFramework(change));
        for (SemanticClassification intentClassification : intentClassifier.classify(profile)) {
            profile = profile.with(SemanticDimension.INTENT, intentClassification);
        }
        return profile;
    }

    /** The first registered FrameworkPlugin that recognizes this Change, if any. */
    private Optional<SemanticClassification> classifyFramework(Change change) {
        for (FrameworkPlugin frameworkPlugin : frameworkPlugins) {
            Optional<SemanticClassification> classification = frameworkPlugin.classify(change, frameworkTaxonomy);
            if (classification.isPresent()) {
                return classification;
            }
        }
        return Optional.empty();
    }

    private SemanticProfile withClassification(SemanticProfile profile, SemanticDimension dimension,
                                                Optional<SemanticClassification> classification) {
        return classification.isPresent() ? profile.with(dimension, classification.get()) : profile;
    }

    private String degradationReason(Optional<ParseOutcome> baseParse, boolean baseOk,
                                      Optional<ParseOutcome> headParse, boolean headOk) {
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
        List<DetectedTransformation> transformations = new ArrayList<>();
        for (LanguagePlugin plugin : languagePlugins) {
            transformations.addAll(plugin.detect(baseRoot, headRoot));
        }
        return new ChangeGrouper().group(transformations);
    }

    /** The first registered LanguagePlugin that recognizes either revision of this file, if any. */
    private Optional<LanguagePlugin> pluginFor(Path baseFile, Path headFile) {
        for (LanguagePlugin plugin : languagePlugins) {
            if (plugin.supports(baseFile) || plugin.supports(headFile)) {
                return Optional.of(plugin);
            }
        }
        return Optional.empty();
    }

    private Set<String> allRelativeSourcePaths(Path baseRoot, Path headRoot) {
        Set<String> paths = new LinkedHashSet<>();
        paths.addAll(relativeSourcePaths(baseRoot));
        paths.addAll(relativeSourcePaths(headRoot));
        return paths;
    }

    private Set<String> relativeSourcePaths(Path root) {
        if (!Files.exists(root)) {
            return Set.of();
        }
        // A walk failure (permission error, symlink loop, concurrent deletion) must not
        // crash the whole analysis — it degrades to "this root contributed no files"
        // rather than violating the graceful-degradation guarantee this class exists for.
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> languagePlugins.stream().anyMatch(plugin -> plugin.supports(p)))
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
