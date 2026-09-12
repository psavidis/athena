package com.athena.web;

import com.athena.github.GitHubRepositoryBrowser;
import com.athena.github.Repository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Lists the repositories accessible to the connected GitHub account (ticket #73). */
@RestController
public class RepositoriesController {

    private final WebSession session;

    public RepositoriesController(WebSession session) {
        this.session = session;
    }

    @GetMapping("/api/repositories")
    public List<Repository> repositories() {
        String token = session.gitHubToken()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not connected to GitHub"));
        return new GitHubRepositoryBrowser(token).listAccessibleRepositories();
    }
}
