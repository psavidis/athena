package com.athena.web.reviewui;

import com.athena.git.TempDirectories;
import com.athena.github.ImportedPullRequest;
import com.athena.plugins.PluginRegistry;
import com.athena.reviewcontext.ReviewSubmission;
import com.athena.reviewui.AnnotationBoard;
import com.athena.semantic.PrAnalyzer;
import com.athena.semantic.ReviewStateStore;
import com.athena.web.WebSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Dedicated unit test for {@link CanvasCommentController} (ticket #134):
 * the controller's own job of resolving the session's real
 * {@link AnnotationBoard} at a canvas-item scope and mapping to/from the
 * response DTOs — {@link com.athena.reviewui.AnnotationBoardCommentEditingTest}
 * already covers the board's own add/edit/delete semantics directly.
 */
class CanvasCommentControllerTest {

    private final WebSession session =
            new WebSession(new PrAnalyzer(PluginRegistry.languagePlugins(), PluginRegistry.frameworkPlugins()));
    private final CanvasCommentController controller = new CanvasCommentController(session, () -> "alex");

    private Path baseRoot;
    private Path headRoot;

    @BeforeEach
    void createTempRoots() throws IOException {
        baseRoot = Files.createTempDirectory("athena-canvas-comments-base-");
        headRoot = Files.createTempDirectory("athena-canvas-comments-head-");
    }

    @AfterEach
    void cleanUpTempRoots() {
        TempDirectories.deleteRecursively(baseRoot);
        TempDirectories.deleteRecursively(headRoot);
    }

    @Test
    void rejectsWhenNoPullRequestIsSelected() {
        session.connect("test-token");

        assertThatThrownBy(() -> controller.comments("territory:crowdness-live"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(409));
    }

    @Test
    void aFreshItemHasNoComments() {
        selectPullRequest();

        assertThat(controller.comments("territory:crowdness-live")).isEmpty();
    }

    @Test
    void postingAddsACommentAttributedToTheCurrentReviewer() {
        selectPullRequest();

        List<CanvasCommentResponse> comments =
                controller.addComment("territory:crowdness-live", new CanvasCommentRequest("Looks good"));

        assertThat(comments).hasSize(1);
        assertThat(comments.get(0).author()).isEqualTo("alex");
        assertThat(comments.get(0).text()).isEqualTo("Looks good");
    }

    @Test
    void postingBlankTextIsRejected() {
        selectPullRequest();

        assertThatThrownBy(() -> controller.addComment("territory:crowdness-live", new CanvasCommentRequest("   ")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void editingUpdatesTheCommentTextInPlace() {
        selectPullRequest();
        String itemId = "territory:crowdness-live";
        String commentId = controller.addComment(itemId, new CanvasCommentRequest("Looks good")).get(0).id();

        List<CanvasCommentResponse> comments =
                controller.editComment(itemId, commentId, new CanvasCommentRequest("Actually, one concern"));

        assertThat(comments).hasSize(1);
        assertThat(comments.get(0).text()).isEqualTo("Actually, one concern");
        assertThat(comments.get(0).id()).isEqualTo(commentId);
    }

    @Test
    void deletingRemovesTheComment() {
        selectPullRequest();
        String itemId = "territory:crowdness-live";
        String commentId = controller.addComment(itemId, new CanvasCommentRequest("Looks good")).get(0).id();

        List<CanvasCommentResponse> comments = controller.deleteComment(itemId, commentId);

        assertThat(comments).isEmpty();
    }

    @Test
    void commentCountsCoverEveryCommentedItemAcrossThePr() {
        selectPullRequest();
        controller.addComment("territory:crowdness-live", new CanvasCommentRequest("First"));
        controller.addComment("territory:crowdness-live", new CanvasCommentRequest("Second"));
        controller.addComment("territory:crowdness-ingestion", new CanvasCommentRequest("Third"));

        Map<String, Integer> counts = controller.commentCounts();

        assertThat(counts).containsEntry("territory:crowdness-live", 2);
        assertThat(counts).containsEntry("territory:crowdness-ingestion", 1);
    }

    @Test
    void commentCountsOmitItemsWithNoComments() {
        selectPullRequest();

        assertThat(controller.commentCounts()).doesNotContainKey("territory:crowdness-live");
    }

    private void selectPullRequest() {
        session.connect("test-token");
        ImportedPullRequest pr = new ImportedPullRequest(1, "Add live module", "author", "base-sha", "head-sha",
                List.of(), List.of());
        session.select(new WebSession.SelectedPullRequest(
                pr, "acme/widgets", headRoot, baseRoot, headRoot, new ReviewStateStore(), new AnnotationBoard(),
                new ReviewSubmission()));
    }
}
