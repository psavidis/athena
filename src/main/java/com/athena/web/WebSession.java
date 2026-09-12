package com.athena.web;

import com.athena.ai.AiFindingsBoard;
import com.athena.git.TempDirectories;
import com.athena.github.ImportedPullRequest;
import com.athena.reviewcontext.ReviewSubmission;
import com.athena.reviewui.AnnotationBoard;
import com.athena.semantic.ReviewStateStore;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Per-browser-session state (ticket #73): the connected GitHub token and,
 * once chosen, the selected PR's imported metadata and checked-out
 * base/head revisions. In-memory only, server-side — the token never
 * reaches the frontend beyond the initial connect response, and is never
 * persisted to disk or logs.
 */
@Component
@SessionScope
public class WebSession {

    private String gitHubToken;
    private SelectedPullRequest selectedPullRequest;
    private AiFindingsBoard aiFindingsBoard;

    public void connect(String token) {
        this.gitHubToken = token;
    }

    public Optional<String> gitHubToken() {
        return Optional.ofNullable(gitHubToken);
    }

    /** Replaces any previously-selected PR, cleaning up its checkout directories first. */
    public void select(SelectedPullRequest newSelection) {
        if (this.selectedPullRequest != null) {
            TempDirectories.deleteRecursively(this.selectedPullRequest.workDir());
        }
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

    public record SelectedPullRequest(ImportedPullRequest pullRequest, String repositoryFullName, Path workDir,
                                       Path baseRoot, Path headRoot, ReviewStateStore reviewStateStore,
                                       AnnotationBoard annotationBoard, ReviewSubmission reviewSubmission) {
    }
}
