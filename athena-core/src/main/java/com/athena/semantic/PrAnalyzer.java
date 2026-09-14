package com.athena.semantic;

import com.athena.analysis.spi.ExternalAnalysisProvider;
import com.athena.analysis.spi.ExternalFinding;
import com.athena.analysis.spi.ProviderConfiguration;
import com.athena.analysis.spi.ProviderRunResult;
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
    private final Map<ExternalAnalysisProvider, ProviderConfiguration> externalProviders;
    private final StructuralTaxonomyClassifier structuralClassifier;
    private final ResponsibilityTaxonomyClassifier responsibilityClassifier;
    private final ArchitectureTaxonomyClassifier architectureClassifier;
    private final PatternTaxonomyClassifier patternClassifier;
    private final FlowTaxonomyClassifier flowClassifier;
    private final IntentTaxonomyClassifier intentClassifier;
    private final Taxonomy frameworkTaxonomy;

    public PrAnalyzer(List<LanguagePlugin> languagePlugins, List<FrameworkPlugin> frameworkPlugins) {
        this(languagePlugins, frameworkPlugins, Map.of());
    }

    /**
     * @param externalProviders external analysis providers to run alongside Athena's own
     *        analysis (ticket #114/#149), each with its own {@link ProviderConfiguration}.
     *        A disabled or absent provider is simply not run — existing callers using the
     *        other constructor get the exact same behavior as before this parameter existed.
     */
    public PrAnalyzer(List<LanguagePlugin> languagePlugins, List<FrameworkPlugin> frameworkPlugins,
                       Map<ExternalAnalysisProvider, ProviderConfiguration> externalProviders) {
        this.languagePlugins = List.copyOf(languagePlugins);
        this.frameworkPlugins = List.copyOf(frameworkPlugins);
        this.externalProviders = Map.copyOf(externalProviders);
        TaxonomyLoader taxonomyLoader = new TaxonomyLoader();
        structuralClassifier = new StructuralTaxonomyClassifier(taxonomyLoader.load(SemanticDimension.STRUCTURAL));
        responsibilityClassifier = new ResponsibilityTaxonomyClassifier(taxonomyLoader.load(SemanticDimension.RESPONSIBILITY));
        architectureClassifier = new ArchitectureTaxonomyClassifier(taxonomyLoader.load(SemanticDimension.ARCHITECTURE));
        patternClassifier = new PatternTaxonomyClassifier(taxonomyLoader.load(SemanticDimension.PATTERN), structuralClassifier);
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
        Map<Change, SemanticClassification> frameworkCorrelationMatches = classifyFrameworkCorrelated(changes);
        List<SemanticProfile> semanticProfiles = changes.stream()
                .map(change -> classify(change, dependencyInjectionMatches.get(change), frameworkCorrelationMatches.get(change)))
                .toList();

        AnalysisStatus status = status(relativePaths.size(), degradedEntries.size());
        List<ExternalFinding> externalFindings = runExternalProviders(headRoot, relativePaths);

        return new AnalysisResult(status, changes, semanticProfiles, degradedEntries, rawDiffsByFile, externalFindings);
    }

    /**
     * Runs every configured, enabled {@link ExternalAnalysisProvider} against
     * {@code headRoot} (ticket #114/#149) — additive evidence, gathered
     * independently of the Change/SemanticProfile pipeline above and never
     * feeding into it. One provider failing (a thrown exception, or a
     * failed {@link ProviderRunResult}) never prevents another provider's
     * findings, or this method's own completion, per the epic's graceful-
     * degradation-for-providers requirement — Athena's own analysis above
     * has already completed by the time this runs.
     */
    private List<ExternalFinding> runExternalProviders(Path headRoot, Set<String> relativePaths) {
        if (externalProviders.isEmpty()) {
            return List.of();
        }
        List<Path> changedFiles = relativePaths.stream().map(Path::of).toList();
        List<ExternalFinding> findings = new ArrayList<>();
        for (Map.Entry<ExternalAnalysisProvider, ProviderConfiguration> entry : externalProviders.entrySet()) {
            if (!entry.getValue().isEnabled()) {
                continue;
            }
            ProviderRunResult result = runProvider(entry.getKey(), headRoot, changedFiles, entry.getValue());
            if (result.isSuccessful()) {
                findings.addAll(result.findings());
            }
        }
        return findings;
    }

    /** Isolates one provider's own thrown exception into a failed result, matching the
     * failure-handling contract {@link ExternalAnalysisProvider#analyze} documents — a
     * misbehaving implementation that throws anyway must not take the whole run down. */
    private ProviderRunResult runProvider(ExternalAnalysisProvider provider, Path headRoot, List<Path> changedFiles,
                                           ProviderConfiguration configuration) {
        try {
            return provider.analyze(headRoot, changedFiles, configuration);
        } catch (RuntimeException e) {
            return ProviderRunResult.failure(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
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
     *        {@link PatternTaxonomyClassifier#classifyDependencyInjection} — one of two
     *        classifications here that need more than this one Change to decide.
     * @param frameworkCorrelationMatch this Change's correlated Framework classification
     *        (e.g. Spring field-to-constructor injection), precomputed once across the
     *        whole Change set by {@link FrameworkPlugin#classifyCorrelated} — the other.
     */
    private SemanticProfile classify(Change change, SemanticClassification dependencyInjectionMatch,
                                      SemanticClassification frameworkCorrelationMatch) {
        SemanticProfile profile = SemanticProfile.empty(change);
        profile = withClassification(profile, SemanticDimension.STRUCTURAL, structuralClassifier.classify(change));
        profile = withClassification(profile, SemanticDimension.RESPONSIBILITY, responsibilityClassifier.classify(change));
        profile = withClassification(profile, SemanticDimension.ARCHITECTURE, architectureClassifier.classify(change));
        profile = withClassification(profile, SemanticDimension.PATTERN, patternClassifier.classify(change));
        profile = withClassification(profile, SemanticDimension.PATTERN, Optional.ofNullable(dependencyInjectionMatch));
        profile = withClassification(profile, SemanticDimension.FEATURE, flowClassifier.classify(change));
        profile = withClassification(profile, SemanticDimension.FRAMEWORK,
                Optional.ofNullable(frameworkCorrelationMatch).or(() -> classifyFramework(change)));
        for (SemanticClassification intentClassification : intentClassifier.classify(profile)) {
            profile = profile.with(SemanticDimension.INTENT, intentClassification);
        }
        return profile;
    }

    /** Every registered FrameworkPlugin's correlated (cross-Change) classifications, merged. */
    private Map<Change, SemanticClassification> classifyFrameworkCorrelated(List<Change> changes) {
        Map<Change, SemanticClassification> merged = new LinkedHashMap<>();
        for (FrameworkPlugin frameworkPlugin : frameworkPlugins) {
            merged.putAll(frameworkPlugin.classifyCorrelated(changes, frameworkTaxonomy));
        }
        return merged;
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
