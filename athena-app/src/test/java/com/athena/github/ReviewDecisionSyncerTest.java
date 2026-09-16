package com.athena.github;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dedicated unit test for {@link ReviewDecisionSyncer} (ticket #183) —
 * repeating a sync call for the same logical review decision must not
 * submit a duplicate review to GitHub.
 */
class ReviewDecisionSyncerTest {

    private static final String TOKEN = "test-token";
    private static final String REPO = "acme/widgets";
    private static final int PULL_REQUEST = 42;

    private final FakeGitHubTransport transport = new FakeGitHubTransport();
    private final ReviewDecisionSyncer syncer = new ReviewDecisionSyncer(TOKEN, transport);

    @Test
    void repeatingAnApprovalSyncDoesNotSubmitADuplicateReview() {
        transport.acceptToken(TOKEN, "octocat");
        transport.enableReviewSync(REPO, PULL_REQUEST);

        syncer.syncApproval(REPO, PULL_REQUEST, "Looks great");
        syncer.syncApproval(REPO, PULL_REQUEST, "Looks great");

        assertThat(transport.postedReviews(REPO, PULL_REQUEST)).hasSize(1);
    }

    @Test
    void repeatingARequestChangesSyncDoesNotSubmitADuplicateReview() {
        transport.acceptToken(TOKEN, "octocat");
        transport.enableReviewSync(REPO, PULL_REQUEST);

        syncer.syncRequestChanges(REPO, PULL_REQUEST, "Please address the comments");
        syncer.syncRequestChanges(REPO, PULL_REQUEST, "Please address the comments");

        assertThat(transport.postedReviews(REPO, PULL_REQUEST)).hasSize(1);
    }

    @Test
    void anApprovalFollowedByARequestChangesAreBothSubmitted() {
        transport.acceptToken(TOKEN, "octocat");
        transport.enableReviewSync(REPO, PULL_REQUEST);

        syncer.syncApproval(REPO, PULL_REQUEST, "Looks great");
        syncer.syncRequestChanges(REPO, PULL_REQUEST, "Please address the comments");

        assertThat(transport.postedReviews(REPO, PULL_REQUEST)).hasSize(2);
    }
}
