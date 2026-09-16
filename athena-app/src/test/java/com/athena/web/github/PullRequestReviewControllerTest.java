package com.athena.web.github;

import com.athena.github.FakeGitHubTransport;
import com.athena.github.GitHubTransport;
import com.athena.github.ReviewDataImporter;
import com.athena.plugins.PluginRegistry;
import com.athena.semantic.PrAnalyzer;
import com.athena.web.WebSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Dedicated unit test for {@link PullRequestReviewController} (ticket #192) —
 * resolving the session's token, delegating to {@link ReviewDataImporter},
 * and mapping to the response DTOs. A fake {@link GitHubTransport} (the
 * established network boundary) stands in for GitHub.
 *
 * <p>Unlike {@link com.athena.web.contextrewind.ContextRewindController},
 * this controller takes the repository/PR to look up directly (not from the
 * session's current selection): a Pull Request referenced from Context
 * Rewind isn't necessarily the one currently selected for review.
 */
class PullRequestReviewControllerTest {

    private static final String TOKEN = "test-token";
    private static final String REPOSITORY = "acme/widgets";

    private final FakeGitHubTransport transport = new FakeGitHubTransport();
    private final WebSession session =
            new WebSession(new PrAnalyzer(PluginRegistry.languagePlugins(), PluginRegistry.frameworkPlugins()));
    private final PullRequestReviewController controller =
            new PullRequestReviewController(session, token -> new ReviewDataImporter(token, transport));

    @BeforeEach
    void acceptToken() {
        transport.acceptToken(TOKEN, "octocat");
    }

    @Test
    void rejectsWhenNotConnectedToGitHub() {
        assertThatThrownBy(() -> controller.review("acme", "widgets", 217))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(401));
    }

    @Test
    void mapsCommentsAndReviewsForTheGivenPullRequest() {
        session.connect(TOKEN);
        transport.addReviewComment(REPOSITORY, 217, "octocat", "Can this produce duplicate charges?", "PaymentProcessor.java");
        transport.addReviewComment(REPOSITORY, 217, "hubot", "Guarded by the idempotency key.", "PaymentProcessor.java");
        transport.addReview(REPOSITORY, 217, "octocat", "APPROVED");

        PullRequestReviewResponse response = controller.review("acme", "widgets", 217);

        assertThat(response.comments())
                .containsExactly(
                        new ReviewCommentResponse("octocat", "Can this produce duplicate charges?", "PaymentProcessor.java"),
                        new ReviewCommentResponse("hubot", "Guarded by the idempotency key.", "PaymentProcessor.java"));
        assertThat(response.reviews()).containsExactly(new ReviewVerdictResponse("octocat", "APPROVED"));
    }

    @Test
    void returnsEmptyListsWhenNothingIsRecorded() {
        session.connect(TOKEN);

        PullRequestReviewResponse response = controller.review("acme", "widgets", 300);

        assertThat(response.comments()).isEmpty();
        assertThat(response.reviews()).isEmpty();
    }
}
