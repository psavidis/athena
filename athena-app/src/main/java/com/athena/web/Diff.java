package com.athena.web;

import com.athena.reviewcontext.ReviewSubmission;
import com.athena.reviewui.AnnotationBoard;
import com.athena.semantic.AnalysisResult;
import com.athena.semantic.Change;
import com.athena.semantic.ModuleGroup;
import com.athena.semantic.ModuleGrouper;
import com.athena.semantic.ModuleLayout;
import com.athena.semantic.PrAnalyzer;
import com.athena.semantic.RepresentationCoverage;
import com.athena.semantic.ReviewStateStore;
import com.athena.semantic.SemanticProfile;


import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Two checked-out revisions, plus everything Athena's semantic analysis
 * and a Review need on top of them — the "Diff" half of ticket #111's
 * "PR Review is Diff + Review + PR context" model. Requires no GitHub/PR
 * concept at all: a base/head revision pair from local Git (ticket #152)
 * carries exactly the same shape as a PR's imported base/head, so this
 * class is reused unmodified by both {@link WebSession.SelectedPullRequest}
 * (composing a Diff with PR-specific context) and a future standalone
 * local-Diff session.
 *
 * <p>Extracted from {@code SelectedPullRequest}, which held these same
 * fields already-mixed-in with PR-specific ones (the class's own
 * `pullRequest`/`repositoryFullName`) — this is a behavior-preserving
 * extraction, not new logic.
 */
public final class Diff {

    private final Path workDir;
    private final Path baseRoot;
    private final Path headRoot;
    private final ReviewStateStore reviewStateStore;
    private final AnnotationBoard annotationBoard;
    private final ReviewSubmission reviewSubmission;

    // Set by WebSession#select right after construction, rather than threaded through the
    // constructor — keeps this type's own public constructor (and its call sites, plus test
    // fixtures) unchanged; a Diff is only ever usable once a WebSession owns it anyway.
    private PrAnalyzer prAnalyzer;

    // Detecting Changes (and their SemanticProfiles, ticket #94) means parsing every source
    // file in both trees via whichever LanguagePlugin(s) PrAnalyzer has registered —
    // expensive enough that recomputing it on every controller call for one selected Diff (as
    // every read of a Change used to do) made ordinary navigation clicks noticeably slow on
    // a real-sized repository. Computed once, lazily, and reused for the rest of this Diff's
    // selection: base/head are immutable checkouts, so the result never changes while this
    // selection is current.
    private AnalysisResult cachedAnalysis;
    private ModuleLayout moduleLayout;

    // AI-generated module narratives are billed, real network calls — computed on demand
    // (never eagerly for every module) and cached per module name for the rest of this
    // selection, so re-opening the same module's narrative doesn't re-call the provider.
    private final Map<String, String> cachedModuleNarratives = new ConcurrentHashMap<>();

    public Diff(Path workDir, Path baseRoot, Path headRoot, ReviewStateStore reviewStateStore,
                AnnotationBoard annotationBoard, ReviewSubmission reviewSubmission) {
        this.workDir = workDir;
        this.baseRoot = baseRoot;
        this.headRoot = headRoot;
        this.reviewStateStore = reviewStateStore;
        this.annotationBoard = annotationBoard;
        this.reviewSubmission = reviewSubmission;
    }

    public Path workDir() {
        return workDir;
    }

    public Path baseRoot() {
        return baseRoot;
    }

    public Path headRoot() {
        return headRoot;
    }

    public ReviewStateStore reviewStateStore() {
        return reviewStateStore;
    }

    public AnnotationBoard annotationBoard() {
        return annotationBoard;
    }

    public ReviewSubmission reviewSubmission() {
        return reviewSubmission;
    }

    void usePrAnalyzer(PrAnalyzer prAnalyzer) {
        this.prAnalyzer = prAnalyzer;
    }

    /** The Changes detected between this Diff's base and head — computed once, then cached. */
    public List<Change> changes() {
        return analysisResult().changes();
    }

    /** This Change's Semantic Profile (ticket #94), from the same cached analysis as {@link #changes()}. */
    public SemanticProfile semanticProfileFor(Change change) {
        return analysisResult().semanticProfileFor(change);
    }

    /** Every changed file of this Diff and which of them no Change represents (ticket #260), from the cached analysis. */
    public RepresentationCoverage representationCoverage() {
        return analysisResult().representationCoverage();
    }

    private synchronized AnalysisResult analysisResult() {
        if (cachedAnalysis == null) {
            cachedAnalysis = prAnalyzer.analyze(baseRoot, headRoot);
        }
        return cachedAnalysis;
    }

    /**
     * Where this Diff's modules begin and end, read from the build descriptors of both
     * revisions (ticket #292) — the one module identity every territory, module group and
     * module-level profile shares. Created once; it caches what it reads.
     */
    public synchronized ModuleLayout moduleLayout() {
        if (moduleLayout == null) {
            moduleLayout = ModuleLayout.of(baseRoot, headRoot);
        }
        return moduleLayout;
    }

    /** This Diff's Changes grouped by module under {@link #moduleLayout()}. */
    public List<ModuleGroup> moduleGroups() {
        return new ModuleGrouper(moduleLayout()).group(changes());
    }

    /** The cached narrative for a module, computing it via {@code generator} on first request. */
    public String moduleNarrative(String moduleName, Function<String, String> generator) {
        return cachedModuleNarratives.computeIfAbsent(moduleName, generator);
    }
}
