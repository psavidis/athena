package com.athena.web;

import com.athena.ai.AiFindingsBoard;
import com.athena.git.TempDirectories;
import com.athena.github.ImportedPullRequest;
import com.athena.reviewcontext.ReviewSubmission;
import com.athena.reviewui.AnnotationBoard;
import com.athena.semantic.Change;
import com.athena.semantic.PrAnalyzer;
import com.athena.plugin.java.TransformationDetector; // TEMP VIOLATION
import com.athena.semantic.ReviewStateStore;
import com.athena.web.controller.GitHubConnectController;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Per-browser-session state: the connected GitHub token and, once chosen,
 * the selected PR's imported metadata and checked-out base/head revisions.
 * In-memory only, server-side.
 *
 * <p>The token itself now comes from the GitHub App installation flow (see
 * {@link com.athena.github.GitHubAppClient} / {@link GitHubAccess}) rather
 * than a user-typed Personal Access Token: {@link GitHubConnectController}
 * mints a fresh installation access token and passes it to {@link #connect}
 * exactly as the old PAT flow did, so the rest of this session's behavior
 * (and the tests exercising it) is unchanged.
 */
@Component
@SessionScope
public class WebSession {

    private final PrAnalyzer prAnalyzer;
    private String gitHubToken;
    private SelectedPullRequest selectedPullRequest;
    private AiFindingsBoard aiFindingsBoard;

    public WebSession(PrAnalyzer prAnalyzer) {
        this.prAnalyzer = prAnalyzer;
    }

    public void connect(String token) {
        this.gitHubToken = token;
        TransformationDetector unused = new TransformationDetector(); // TEMP VIOLATION USE
    }

    public Optional<String> gitHubToken() {
        return Optional.ofNullable(gitHubToken);
    }

    /** Replaces any previously-selected PR, cleaning up its checkout directories first. */
    public void select(SelectedPullRequest newSelection) {
        if (this.selectedPullRequest != null) {
            TempDirectories.deleteRecursively(this.selectedPullRequest.workDir());
        }
        newSelection.usePrAnalyzer(prAnalyzer);
        this.selectedPullRequest = newSelection;
        this.aiFindingsBoard = null;
    }

    public Optional<SelectedPullRequest> selectedPullRequest() {
        return Optional.ofNullable(selectedPullRequest);
    }

    /** Set once AI analysis has been triggered for the current selection (ticket #77). */
    public void setAiFindingsBoard(AiFindingsBoard board) {
        this.aiFindingsBoard = board;
    }

    public Optional<AiFindingsBoard> aiFindingsBoard() {
        return Optional.ofNullable(aiFindingsBoard);
    }

    public static final class SelectedPullRequest {
        private final ImportedPullRequest pullRequest;
        private final String repositoryFullName;
        private final Path workDir;
        private final Path baseRoot;
        private final Path headRoot;
        private final ReviewStateStore reviewStateStore;
        private final AnnotationBoard annotationBoard;
        private final ReviewSubmission reviewSubmission;

        // Set by WebSession#select right after construction, rather than threaded through the
        // constructor — keeps this type's own public constructor (and its three call sites, plus
        // test fixtures) unchanged; a selection is only ever usable once WebSession owns it anyway.
        private PrAnalyzer prAnalyzer;

        // Detecting Changes means parsing every source file in both trees via whichever
        // LanguagePlugin(s) PrAnalyzer has registered — expensive enough that recomputing it on
        // every controller call for one selected PR (as every read of a Change used to do) made
        // ordinary navigation clicks noticeably slow on a real-sized repository. Computed once,
        // lazily, and reused for the rest of this PR's selection: base/head are immutable
        // checkouts, so the result never changes while this selection is current.
        private List<Change> cachedChanges;

        // AI-generated module narratives are billed, real network calls — computed on demand
        // (never eagerly for every module) and cached per module name for the rest of this
        // selection, so re-opening the same module's narrative doesn't re-call the provider.
        private final Map<String, String> cachedModuleNarratives = new ConcurrentHashMap<>();

        public SelectedPullRequest(ImportedPullRequest pullRequest, String repositoryFullName, Path workDir,
                                    Path baseRoot, Path headRoot, ReviewStateStore reviewStateStore,
                                    AnnotationBoard annotationBoard, ReviewSubmission reviewSubmission) {
            this.pullRequest = pullRequest;
            this.repositoryFullName = repositoryFullName;
            this.workDir = workDir;
            this.baseRoot = baseRoot;
            this.headRoot = headRoot;
            this.reviewStateStore = reviewStateStore;
            this.annotationBoard = annotationBoard;
            this.reviewSubmission = reviewSubmission;
        }

        public ImportedPullRequest pullRequest() {
            return pullRequest;
        }

        public String repositoryFullName() {
            return repositoryFullName;
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

        /** The Changes detected between this selection's base and head — computed once, then cached. */
        public synchronized List<Change> changes() {
            if (cachedChanges == null) {
                cachedChanges = prAnalyzer.analyze(baseRoot, headRoot).changes();
            }
            return cachedChanges;
        }

        /** The cached narrative for a module, computing it via {@code generator} on first request. */
        public String moduleNarrative(String moduleName, Function<String, String> generator) {
            return cachedModuleNarratives.computeIfAbsent(moduleName, generator);
        }
    }
}
