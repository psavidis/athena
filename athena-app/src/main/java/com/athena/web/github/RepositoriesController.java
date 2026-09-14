package com.athena.web.github;

import com.athena.github.GitHubRepositoryProvider;
import com.athena.repository.Repository;
import com.athena.repository.RepositoryProvider;
import com.athena.web.WebSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Lists the repositories accessible to the connected repository provider
 * (ticket #113/#144 — GitHub is the current provider). The web UI connects
 * via a GitHub App installation (see {@link com.athena.web.GitHubAccess}), so
 * the session always holds an installation access token here.
 */
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
        RepositoryProvider provider = new GitHubRepositoryProvider(token);
        return provider.accessibleRepositories();
    }
}
