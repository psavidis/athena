package com.athena.web.github;

import com.athena.github.GitHubRepositoryProvider;
import com.athena.repository.PullRequestSummary;
import com.athena.repository.RepositoryProvider;
import com.athena.web.WebSession;
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
        RepositoryProvider provider = new GitHubRepositoryProvider(token);
        return provider.openPullRequests(owner + "/" + repo);
    }
}
