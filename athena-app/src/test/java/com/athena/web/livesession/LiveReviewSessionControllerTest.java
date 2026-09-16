package com.athena.web.livesession;

import com.athena.git.TempDirectories;
import com.athena.livesession.LiveReviewSessionRegistry;
import com.athena.plugins.PluginRegistry;
import com.athena.repository.ImportedPullRequest;
import com.athena.reviewcontext.ReviewSubmission;
import com.athena.reviewui.AnnotationBoard;
import com.athena.semantic.PrAnalyzer;
import com.athena.semantic.ReviewStateStore;
import com.athena.web.WebSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Dedicated unit test for {@link LiveReviewSessionController} (ticket
 * #158): its own job of translating {@link com.athena.livesession.LiveReviewSession}
 * outcomes into HTTP status codes and resolving the caller's selected PR —
 * {@code LiveReviewSessionTest} already covers the aggregate's own
 * behavior directly.
 */
class LiveReviewSessionControllerTest {

    private final WebSession webSession =
            new WebSession(new PrAnalyzer(PluginRegistry.languagePlugins(), PluginRegistry.frameworkPlugins()));
    private final LiveReviewSessionRegistry registry = new LiveReviewSessionRegistry();
    private final LiveReviewSessionController controller = new LiveReviewSessionController(webSession, registry);

    private Path baseRoot;
    private Path headRoot;

    @BeforeEach
    void createTempRoots() throws IOException {
        baseRoot = Files.createTempDirectory("athena-live-controller-base-");
        headRoot = Files.createTempDirectory("athena-live-controller-head-");
    }

    @AfterEach
    void cleanUpTempRoots() {
        TempDirectories.deleteRecursively(baseRoot);
        TempDirectories.deleteRecursively(headRoot);
    }

    @Test
    void creatingWithNoPrSelectedIsRejected() {
        assertThatThrownBy(() -> controller.create(new CreateLiveSessionRequest("Petros")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(409));
    }

    @Test
    void creatingWithABlankDisplayNameIsRejected() {
        selectPullRequest();

        assertThatThrownBy(() -> controller.create(new CreateLiveSessionRequest("  ")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void creatingReturnsASessionTheCreatorPresents() {
        selectPullRequest();

        LiveSessionJoinResponse response = controller.create(new CreateLiveSessionRequest("Petros"));

        assertThat(response.snapshot().presenterId()).isEqualTo(response.participantId());
        assertThat(response.snapshot().repositoryFullName()).isEqualTo("acme/widgets");
        assertThat(response.snapshot().pullRequestNumber()).isEqualTo(42);
    }

    @Test
    void joiningAnUnknownSessionIsRejected() {
        assertThatThrownBy(() -> controller.join("does-not-exist", new JoinLiveSessionRequest("Maria", null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void aNonPresenterCannotMoveTheSharedFocusThroughTheController() {
        selectPullRequest();
        LiveSessionJoinResponse created = controller.create(new CreateLiveSessionRequest("Petros"));
        LiveSessionJoinResponse joined =
                controller.join(created.sessionId(), new JoinLiveSessionRequest("Maria", null));

        assertThatThrownBy(() -> controller.presentFocus(created.sessionId(),
                new LiveFocusRequest(joined.participantId(), "ARCHITECTURE", "component:X", null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(409));
    }

    @Test
    void commentsAreScopedByCanvasItemAndDefaultToReviewScope() {
        selectPullRequest();
        LiveSessionJoinResponse created = controller.create(new CreateLiveSessionRequest("Petros"));

        controller.addComment(created.sessionId(),
                new LiveCommentRequest(created.participantId(), "component:PaymentValidator", "Looks good"));
        controller.addComment(created.sessionId(),
                new LiveCommentRequest(created.participantId(), null, "Overall solid"));

        assertThat(controller.comments(created.sessionId(), "component:PaymentValidator"))
                .extracting(LiveCommentResponse::text).containsExactly("Looks good");
        assertThat(controller.comments(created.sessionId(), null))
                .extracting(LiveCommentResponse::text).containsExactly("Overall solid");
    }

    @Test
    void aBlankCommentIsRejectedAsBadRequest() {
        selectPullRequest();
        LiveSessionJoinResponse created = controller.create(new CreateLiveSessionRequest("Petros"));

        assertThatThrownBy(() -> controller.addComment(created.sessionId(),
                new LiveCommentRequest(created.participantId(), "component:X", "   ")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void subscribingToEventsOfAnUnknownSessionIsRejected() {
        assertThatThrownBy(() -> controller.events("does-not-exist", "someone"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void subscribingToEventsReturnsAUsableEmitterForAKnownSession() {
        selectPullRequest();
        LiveSessionJoinResponse created = controller.create(new CreateLiveSessionRequest("Petros"));

        SseEmitter emitter = controller.events(created.sessionId(), created.participantId());

        assertThat(emitter).isNotNull();
    }

    @Test
    void endingASessionRemovesItFromTheRegistry() {
        selectPullRequest();
        LiveSessionJoinResponse created = controller.create(new CreateLiveSessionRequest("Petros"));

        controller.end(created.sessionId(), new LiveParticipantRequest(created.participantId()));

        assertThatThrownBy(() -> controller.snapshot(created.sessionId()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void onlyTheCreatorCanEndTheSessionThroughTheController() {
        selectPullRequest();
        LiveSessionJoinResponse created = controller.create(new CreateLiveSessionRequest("Petros"));
        LiveSessionJoinResponse joined =
                controller.join(created.sessionId(), new JoinLiveSessionRequest("Maria", null));

        assertThatThrownBy(() -> controller.end(created.sessionId(), new LiveParticipantRequest(joined.participantId())))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(409));
    }

    private void selectPullRequest() {
        ImportedPullRequest pr = new ImportedPullRequest(42, "A PR", "author", "base-sha", "head-sha",
                List.of(), List.of());
        webSession.connect("test-token");
        webSession.select(new WebSession.SelectedPullRequest(
                pr, "acme/widgets", headRoot, baseRoot, headRoot, new ReviewStateStore(), new AnnotationBoard(),
                new ReviewSubmission()));
    }
}
