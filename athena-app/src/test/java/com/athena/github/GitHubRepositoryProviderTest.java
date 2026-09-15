package com.athena.github;

import com.athena.repository.ClosedPullRequestSummary;
import com.athena.repository.ImportedPullRequest;
import com.athena.repository.PullRequestSummary;
import com.athena.repository.Repository;
import com.athena.repository.RepositoryProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dedicated unit test for {@link GitHubRepositoryProvider} (ticket #113/
 * #144) — the adapter behind the provider-independent {@link
 * RepositoryProvider} contract. Uses {@link FakeGitHubTransport} (the
 * existing network-boundary fake) rather than mocking
 * {@link GitHubRepositoryBrowser}/{@link PullRequestImporter} themselves,
 * since those are real internal collaborators this class should exercise
 * for real, not isolate from.
 */
class GitHubRepositoryProviderTest {

    private static final String TOKEN = "test-token";

    private final FakeGitHubTransport transport = new FakeGitHubTransport();
    private final RepositoryProvider provider = new GitHubRepositoryProvider(TOKEN, transport);

    @Test
    void listsAccessibleRepositoriesThroughTheInstallationEndpoint() {
        transport.acceptToken(TOKEN, "octocat");
        transport.addAccessibleRepository(TOKEN, "acme/widgets");

        List<Repository> repositories = provider.accessibleRepositories();

        assertThat(repositories).containsExactly(new Repository("acme/widgets"));
    }

    @Test
    void listsOpenPullRequestsForARepository() {
        transport.acceptToken(TOKEN, "octocat");
        transport.addOpenPullRequest("acme/widgets", 42, "Add feature");

        List<PullRequestSummary> pullRequests = provider.openPullRequests("acme/widgets");

        assertThat(pullRequests).containsExactly(new PullRequestSummary(42, "Add feature"));
    }

    @Test
    void listsClosedPullRequestsForARepositoryWithTheirMergeStatus() {
        transport.acceptToken(TOKEN, "octocat");
        transport.addClosedPullRequest("acme/widgets", 10, "Ship it", true);
        transport.addClosedPullRequest("acme/widgets", 11, "Abandoned idea", false);

        List<ClosedPullRequestSummary> closedPullRequests = provider.closedPullRequests("acme/widgets");

        assertThat(closedPullRequests).containsExactlyInAnyOrder(
                new ClosedPullRequestSummary(10, "Ship it", true),
                new ClosedPullRequestSummary(11, "Abandoned idea", false));
    }

    @Test
    void importsAPullRequestsFullContent() {
        transport.acceptToken(TOKEN, "octocat");
        transport.addPullRequestDetail("acme/widgets", 42, "Add feature", "octocat", "base-sha", "head-sha");
        transport.addCommit("acme/widgets", 42, "abc123", "Add feature");
        transport.addChangedFile("acme/widgets", 42, "Feature.java", "added", "+public class Feature {}");

        ImportedPullRequest imported = provider.importPullRequest("acme/widgets", 42);

        assertThat(imported.title()).isEqualTo("Add feature");
        assertThat(imported.baseRevision()).isEqualTo("base-sha");
        assertThat(imported.headRevision()).isEqualTo("head-sha");
        assertThat(imported.commits()).hasSize(1);
        assertThat(imported.changedFiles()).hasSize(1);
    }
}
