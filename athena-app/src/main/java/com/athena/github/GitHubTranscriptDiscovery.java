package com.athena.github;

import com.athena.reviewreplay.TranscriptReference;
import com.athena.reviewreplay.TranscriptReferenceParser;

import java.util.List;

/**
 * Discovers {@link TranscriptReference}s already linked in a GitHub PR's
 * description (ticket #213). Kept GitHub-specific rather than added to
 * {@link com.athena.repository.RepositoryProvider} — that interface is
 * provider-independent and GitLab has no implementation yet (see the
 * ticket's own flagged limitation), so this would be aspirational there.
 */
public class GitHubTranscriptDiscovery {

    private final String token;
    private final GitHubTransport transport;

    public GitHubTranscriptDiscovery(String token, GitHubTransport transport) {
        this.token = token;
        this.transport = transport;
    }

    /** Every transcript reference discoverable in the given PR's description, in the order they appear. */
    public List<TranscriptReference> discover(String repositoryFullName, int number) {
        String body = transport.fetchPullRequestBody(token, repositoryFullName, number);
        return TranscriptReferenceParser.discover(body);
    }
}
