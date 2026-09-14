package com.athena.web;

import com.athena.ai.AiFindingsBoard;
import com.athena.git.TempDirectories;
import com.athena.repository.ImportedPullRequest;
import com.athena.reviewcontext.ReviewSubmission;
import com.athena.reviewui.AnnotationBoard;
import com.athena.semantic.Change;
import com.athena.semantic.PrAnalyzer;
import com.athena.semantic.ReviewStateStore;
import com.athena.semantic.SemanticProfile;
import com.athena.web.github.GitHubConnectController;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
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
    private Diff selectedDiff;
    private AiFindingsBoard aiFindingsBoard;

    public WebSession(PrAnalyzer prAnalyzer) {
        this.prAnalyzer = prAnalyzer;
    }

    public void connect(String token) {
        this.gitHubToken = token;
    }

    public Optional<String> gitHubToken() {
        return Optional.ofNullable(gitHubToken);
    }

    /** Replaces any previously-selected PR, cleaning up its checkout directories first —
     * also clears a standalone {@link #selectDiff selected Diff}, since a session holds
     * only one current selection at a time (ticket #111/#152). */
    public void select(SelectedPullRequest newSelection) {
        if (this.selectedPullRequest != null) {
            TempDirectories.deleteRecursively(this.selectedPullRequest.workDir());
        }
        if (this.selectedDiff != null) {
            TempDirectories.deleteRecursively(this.selectedDiff.workDir());
            this.selectedDiff = null;
        }
        newSelection.diff.usePrAnalyzer(prAnalyzer);
        this.selectedPullRequest = newSelection;
        this.aiFindingsBoard = null;
    }

    public Optional<SelectedPullRequest> selectedPullRequest() {
        return Optional.ofNullable(selectedPullRequest);
    }

    /** Replaces any previously-selected standalone Diff (ticket #111/#152) — a local
     * comparison with no PR/GitHub context. Also clears a selected PR Review, for the
     * same one-selection-at-a-time reason {@link #select} clears this. */
    public void selectDiff(Diff newDiff) {
        if (this.selectedDiff != null) {
            TempDirectories.deleteRecursively(this.selectedDiff.workDir());
        }
        if (this.selectedPullRequest != null) {
            TempDirectories.deleteRecursively(this.selectedPullRequest.workDir());
            this.selectedPullRequest = null;
        }
        newDiff.usePrAnalyzer(prAnalyzer);
        this.selectedDiff = newDiff;
        this.aiFindingsBoard = null;
    }

    public Optional<Diff> selectedDiff() {
        return Optional.ofNullable(selectedDiff);
    }

    /** Set once AI analysis has been triggered for the current selection (ticket #77). */
    public void setAiFindingsBoard(AiFindingsBoard board) {
        this.aiFindingsBoard = board;
    }

    public Optional<AiFindingsBoard> aiFindingsBoard() {
        return Optional.ofNullable(aiFindingsBoard);
    }

    /**
     * A PR Review (ticket #111/#152): a {@link Diff} plus the GitHub-specific
     * context that turns it into one — the imported PR's own metadata and
     * repository. Delegates every Diff-shaped method to its {@link Diff}
     * rather than duplicating fields, so this class's own public API (every
     * controller across the web layer reads it) stays unchanged by the
     * Diff/PR-context split — "PR Review is Diff + Review + PR context,"
     * per the epic's own framing, expressed here as composition.
     */
    public static final class SelectedPullRequest {
        private final ImportedPullRequest pullRequest;
        private final String repositoryFullName;
        private final Diff diff;

        public SelectedPullRequest(ImportedPullRequest pullRequest, String repositoryFullName, Path workDir,
                                    Path baseRoot, Path headRoot, ReviewStateStore reviewStateStore,
                                    AnnotationBoard annotationBoard, ReviewSubmission reviewSubmission) {
            this(pullRequest, repositoryFullName,
                    new Diff(workDir, baseRoot, headRoot, reviewStateStore, annotationBoard, reviewSubmission));
        }

        public SelectedPullRequest(ImportedPullRequest pullRequest, String repositoryFullName, Diff diff) {
            this.pullRequest = pullRequest;
            this.repositoryFullName = repositoryFullName;
            this.diff = diff;
        }

        public ImportedPullRequest pullRequest() {
            return pullRequest;
        }

        public String repositoryFullName() {
            return repositoryFullName;
        }

        public Diff diff() {
            return diff;
        }

        public Path workDir() {
            return diff.workDir();
        }

        public Path baseRoot() {
            return diff.baseRoot();
        }

        public Path headRoot() {
            return diff.headRoot();
        }

        public ReviewStateStore reviewStateStore() {
            return diff.reviewStateStore();
        }

        public AnnotationBoard annotationBoard() {
            return diff.annotationBoard();
        }

        public ReviewSubmission reviewSubmission() {
            return diff.reviewSubmission();
        }

        /** The Changes detected between this selection's base and head — computed once, then cached. */
        public List<Change> changes() {
            return diff.changes();
        }

        /** This Change's Semantic Profile (ticket #94), from the same cached analysis as {@link #changes()}. */
        public SemanticProfile semanticProfileFor(Change change) {
            return diff.semanticProfileFor(change);
        }

        /** The cached narrative for a module, computing it via {@code generator} on first request. */
        public String moduleNarrative(String moduleName, Function<String, String> generator) {
            return diff.moduleNarrative(moduleName, generator);
        }
    }
}
