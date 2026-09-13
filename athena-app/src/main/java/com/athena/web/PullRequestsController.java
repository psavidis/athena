package com.athena.web;

import com.athena.github.GitHubRepositoryBrowser;
import com.athena.github.PullRequestSummary;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Lists a repository's open Pull Requests (ticket #73). */
@RestController
public class PullRequestsController {

    private final WebSession session;

    public PullRequestsController(WebSession session) {
        this.session = session;
    }

    @GetMapping("/api/repositories/{owner}/{repo}/pulls")
    public List<PullRequestSummary> openPullRequests(@PathVariable String owner, @PathVariable String repo) {
        String token = session.gitHubToken()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not connected to GitHub"));
        return new GitHubRepositoryBrowser(token).listOpenPullRequests(owner + "/" + repo);
    }
}
