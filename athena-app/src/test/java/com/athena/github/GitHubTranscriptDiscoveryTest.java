package com.athena.github;

import com.athena.reviewreplay.TranscriptReference;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dedicated unit test for {@link GitHubTranscriptDiscovery} (ticket #213).
 * Uses {@link FakeGitHubTransport} (the existing network-boundary fake) so
 * real HTTP is never crossed.
 */
class GitHubTranscriptDiscoveryTest {

    private static final String TOKEN = "test-token";
    private static final String REPO = "acme/widgets";
    private static final int PULL_REQUEST = 42;

    private final FakeGitHubTransport transport = new FakeGitHubTransport();
    private final GitHubTranscriptDiscovery discovery = new GitHubTranscriptDiscovery(TOKEN, transport);

    @Test
    void discoversATranscriptLinkedInThePrDescription() {
        transport.acceptToken(TOKEN, "octocat");
        transport.setPullRequestBody(REPO, PULL_REQUEST, "**Transcript:** [Recording](https://otter.ai/s/abc123)");

        List<TranscriptReference> references = discovery.discover(REPO, PULL_REQUEST);

        assertThat(references).hasSize(1);
        assertThat(references.get(0).url()).isEqualTo("https://otter.ai/s/abc123");
    }

    @Test
    void findsNothingWhenThePrHasNoDescription() {
        transport.acceptToken(TOKEN, "octocat");

        assertThat(discovery.discover(REPO, PULL_REQUEST)).isEmpty();
    }
}
