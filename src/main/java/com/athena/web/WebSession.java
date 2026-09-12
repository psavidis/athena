package com.athena.web;

import com.athena.git.TempDirectories;
import com.athena.github.ImportedPullRequest;
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
    }

    public Optional<SelectedPullRequest> selectedPullRequest() {
        return Optional.ofNullable(selectedPullRequest);
    }

    public record SelectedPullRequest(ImportedPullRequest pullRequest, Path workDir, Path baseRoot, Path headRoot) {
    }
}
