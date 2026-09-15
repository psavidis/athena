package com.athena.github;

import com.athena.repository.ClosedPullRequestSummary;
import com.athena.repository.ImportedPullRequest;
import com.athena.repository.PullRequestSummary;
import com.athena.repository.Repository;
import com.athena.repository.RepositoryProvider;

import java.time.Duration;
import java.net.http.HttpClient;
import java.util.List;

/**
 * GitHub's implementation of {@link RepositoryProvider} (ticket #113/#144).
 * Wraps {@link GitHubRepositoryBrowser} and {@link PullRequestImporter} —
 * the same GitHub API calls the web controllers made directly before this
 * ticket — behind the provider-independent contract, so a future provider
 * (GitLab, etc.) only needs its own implementation of this interface, not
 * changes to the controllers.
 *
 * <p>Uses {@link GitHubRepositoryBrowser#listInstallationRepositories()}
 * (not {@code listAccessibleRepositories()}): the web UI always holds a
 * GitHub App installation access token (see {@link
 * com.athena.web.GitHubAccess}), which requires the installation-scoped
 * endpoint.
 */
public class GitHubRepositoryProvider implements RepositoryProvider {

    private final GitHubRepositoryBrowser browser;
    private final PullRequestImporter importer;

    public GitHubRepositoryProvider(String token) {
        this(token, new HttpGitHubTransport(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build()));
    }

    public GitHubRepositoryProvider(String token, GitHubTransport transport) {
        this.browser = new GitHubRepositoryBrowser(token, transport);
        this.importer = new PullRequestImporter(token, transport);
    }

    @Override
    public List<Repository> accessibleRepositories() {
        return browser.listInstallationRepositories();
    }

    @Override
    public List<PullRequestSummary> openPullRequests(String repositoryFullName) {
        return browser.listOpenPullRequests(repositoryFullName);
    }

    @Override
    public List<ClosedPullRequestSummary> closedPullRequests(String repositoryFullName) {
        return browser.listClosedPullRequests(repositoryFullName);
    }

    @Override
    public ImportedPullRequest importPullRequest(String repositoryFullName, int number) {
        return importer.importPullRequest(repositoryFullName, number);
    }
}
