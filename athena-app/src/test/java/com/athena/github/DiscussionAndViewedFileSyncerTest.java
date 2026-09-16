package com.athena.github;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dedicated unit test for {@link DiscussionAndViewedFileSyncer} (ticket
 * #183) — repeating a sync call for the same logical thread/file must not
 * send the GraphQL mutation to GitHub a second time.
 */
class DiscussionAndViewedFileSyncerTest {

    private static final String TOKEN = "test-token";
    private static final String REPO = "acme/widgets";
    private static final int PULL_REQUEST = 42;

    private final FakeGitHubGraphQLTransport transport = new FakeGitHubGraphQLTransport();
    private final DiscussionAndViewedFileSyncer syncer = new DiscussionAndViewedFileSyncer(TOKEN, transport);

    @Test
    void repeatingAResolveThreadSyncSendsOnlyOneMutation() {
        transport.allowResolvingThread("thread-1");

        syncer.resolveDiscussionThread("thread-1");
        syncer.resolveDiscussionThread("thread-1");

        assertThat(transport.resolveMutationCallCount("thread-1")).isEqualTo(1);
        assertThat(transport.resolvedThreads()).contains("thread-1");
    }

    @Test
    void repeatingAMarkFileAsViewedSyncSendsOnlyOneMutation() {
        transport.allowMarkingFileViewed(REPO, PULL_REQUEST, "README.md");

        syncer.markFileAsViewed(REPO, PULL_REQUEST, "README.md");
        syncer.markFileAsViewed(REPO, PULL_REQUEST, "README.md");

        assertThat(transport.markViewedMutationCallCount(REPO, PULL_REQUEST, "README.md")).isEqualTo(1);
        assertThat(transport.viewedFiles(REPO, PULL_REQUEST)).contains("README.md");
    }

    @Test
    void twoDistinctThreadsAreBothResolved() {
        transport.allowResolvingThread("thread-1");
        transport.allowResolvingThread("thread-2");

        syncer.resolveDiscussionThread("thread-1");
        syncer.resolveDiscussionThread("thread-2");

        assertThat(transport.resolveMutationCallCount("thread-1")).isEqualTo(1);
        assertThat(transport.resolveMutationCallCount("thread-2")).isEqualTo(1);
    }
}
