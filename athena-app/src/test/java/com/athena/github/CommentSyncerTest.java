package com.athena.github;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Dedicated unit test for {@link CommentSyncer} (ticket #183) — repeating a
 * sync call for the same logical comment must not post a duplicate to
 * GitHub. Uses {@link FakeGitHubTransport} (the existing network-boundary
 * fake) so real HTTP is never crossed.
 */
class CommentSyncerTest {

    private static final String TOKEN = "test-token";
    private static final String REPO = "acme/widgets";
    private static final int PULL_REQUEST = 42;

    private final FakeGitHubTransport transport = new FakeGitHubTransport();
    private final CommentSyncer syncer = new CommentSyncer(TOKEN, transport);

    @Test
    void repeatingAGeneralCommentSyncDoesNotPostADuplicate() {
        transport.acceptToken(TOKEN, "octocat");
        transport.enableCommentSync(REPO, PULL_REQUEST);

        syncer.syncGeneralComment(REPO, PULL_REQUEST, "Looks good overall");
        syncer.syncGeneralComment(REPO, PULL_REQUEST, "Looks good overall");

        assertThat(transport.postedGeneralComments(REPO, PULL_REQUEST)).containsExactly("Looks good overall");
    }

    @Test
    void repeatingALineCommentSyncDoesNotPostADuplicate() {
        transport.acceptToken(TOKEN, "octocat");
        transport.enableCommentSync(REPO, PULL_REQUEST);

        syncer.syncLineComment(REPO, PULL_REQUEST, "Please rename this variable", "README.md", 10);
        syncer.syncLineComment(REPO, PULL_REQUEST, "Please rename this variable", "README.md", 10);

        assertThat(transport.postedLineComments(REPO, PULL_REQUEST)).hasSize(1);
    }

    @Test
    void twoDistinctGeneralCommentsAreBothPosted() {
        transport.acceptToken(TOKEN, "octocat");
        transport.enableCommentSync(REPO, PULL_REQUEST);

        syncer.syncGeneralComment(REPO, PULL_REQUEST, "Looks good overall");
        syncer.syncGeneralComment(REPO, PULL_REQUEST, "One more note");

        assertThat(transport.postedGeneralComments(REPO, PULL_REQUEST))
                .containsExactlyInAnyOrder("Looks good overall", "One more note");
    }

    @Test
    void aFailedSyncIsNotTreatedAsAlreadySyncedAndCanBeRetried() {
        transport.acceptToken(TOKEN, "octocat");

        assertThatThrownBy(() -> syncer.syncGeneralComment(REPO, PULL_REQUEST, "Looks good overall"))
                .isInstanceOf(GitHubResourceNotFoundException.class);

        transport.enableCommentSync(REPO, PULL_REQUEST);
        syncer.syncGeneralComment(REPO, PULL_REQUEST, "Looks good overall");

        assertThat(transport.postedGeneralComments(REPO, PULL_REQUEST)).containsExactly("Looks good overall");
    }
}
