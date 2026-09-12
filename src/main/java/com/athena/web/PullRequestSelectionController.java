package com.athena.web;

import com.athena.git.GitAskpass;
import com.athena.git.GitRevisionCheckout;
import com.athena.git.TempDirectories;
import com.athena.github.GitHubTransport;
import com.athena.github.HttpGitHubTransport;
import com.athena.github.ImportedPullRequest;
import com.athena.github.PullRequestImporter;
import com.athena.reviewui.AnnotationBoard;
import com.athena.semantic.ReviewStateStore;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/**
 * Selecting a PR imports its metadata and checks out its base/head
 * revisions (ticket #73), ready for a later ticket to run semantic
 * detection against. Read-only: no review state, no GitHub sync yet.
 */
@RestController
public class PullRequestSelectionController {

    private final WebSession session;

    public PullRequestSelectionController(WebSession session) {
        this.session = session;
    }

    @PostMapping("/api/repositories/{owner}/{repo}/pulls/{number}/select")
    public ImportedPullRequest select(@PathVariable String owner, @PathVariable String repo, @PathVariable int number) {
        String token = session.gitHubToken()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not connected to GitHub"));

        String repositoryFullName = owner + "/" + repo;
        GitHubTransport transport = new HttpGitHubTransport(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        ImportedPullRequest pr = new PullRequestImporter(token, transport).importPullRequest(repositoryFullName, number);

        String repositoryUrl = "https://github.com/" + repositoryFullName + ".git";
        Map<String, String> gitEnvironment = GitAskpass.environmentFor(token);

        Path workDir;
        try {
            workDir = Files.createTempDirectory("athena-web-review-");
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create a working directory", e);
        }
        try {
            Path baseRoot = GitRevisionCheckout.checkout(repositoryUrl, pr.baseRevision(), workDir, gitEnvironment);
            Path headRoot = GitRevisionCheckout.checkout(repositoryUrl, pr.headRevision(), workDir, gitEnvironment);
            session.select(new WebSession.SelectedPullRequest(
                    pr, workDir, baseRoot, headRoot, new ReviewStateStore(), new AnnotationBoard()));
            return pr;
        } catch (RuntimeException e) {
            TempDirectories.deleteRecursively(workDir);
            throw e;
        }
    }
}
