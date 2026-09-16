package com.athena.web.reviewrecorder;

import com.athena.git.TempDirectories;
import com.athena.plugins.PluginRegistry;
import com.athena.repository.ImportedPullRequest;
import com.athena.reviewcontext.ReviewSubmission;
import com.athena.reviewrecorder.ReviewRecordingRegistry;
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
import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Dedicated unit test for {@link ReviewRecordingController} (ticket
 * #203): its own job of translating
 * {@link com.athena.reviewrecorder.ReviewRecording} outcomes into HTTP
 * status codes, requiring disclosure acknowledgement, and resolving the
 * caller's selected PR — {@code ReviewRecordingTest} already covers the
 * aggregate's own behavior directly.
 */
class ReviewRecordingControllerTest {

    private final WebSession webSession =
            new WebSession(new PrAnalyzer(PluginRegistry.languagePlugins(), PluginRegistry.frameworkPlugins()));
    private final ReviewRecordingRegistry registry = new ReviewRecordingRegistry(Clock.systemUTC());
    private final ReviewRecordingController controller = new ReviewRecordingController(webSession, registry);

    private Path baseRoot;
    private Path headRoot;

    @BeforeEach
    void createTempRoots() throws IOException {
        baseRoot = Files.createTempDirectory("athena-review-recording-controller-base-");
        headRoot = Files.createTempDirectory("athena-review-recording-controller-head-");
    }

    @AfterEach
    void cleanUpTempRoots() {
        TempDirectories.deleteRecursively(baseRoot);
        TempDirectories.deleteRecursively(headRoot);
    }

    @Test
    void startingWithNoPrOrDiffSelectedIsRejected() {
        assertThatThrownBy(() -> controller.start(new StartReviewRecordingRequest("Petros", true)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(409));
    }

    @Test
    void startingWithoutAcknowledgingTheDisclosureIsRejected() {
        selectPullRequest(42, "acme/widgets");

        assertThatThrownBy(() -> controller.start(new StartReviewRecordingRequest("Petros", false)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void startingWithASelectedPrCreatesAnActiveRecording() {
        selectPullRequest(42, "acme/widgets");

        ReviewRecordingStartResponse response = controller.start(new StartReviewRecordingRequest("Petros", true));

        assertThat(response.recordingId()).isNotNull();
        assertThat(response.snapshot().active()).isTrue();
        assertThat(response.snapshot().repositoryFullName()).isEqualTo("acme/widgets");
        assertThat(response.snapshot().pullRequestNumber()).isEqualTo(42);
    }

    @Test
    void stoppingAnUnknownRecordingIsRejected() {
        assertThatThrownBy(() -> controller.stop("does-not-exist"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void stoppingAnAlreadyStoppedRecordingIsRejected() {
        selectPullRequest(42, "acme/widgets");
        ReviewRecordingStartResponse response = controller.start(new StartReviewRecordingRequest("Petros", true));
        controller.stop(response.recordingId());

        assertThatThrownBy(() -> controller.stop(response.recordingId()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(409));
    }

    @Test
    void captureDisclosureNamesWhatWillBeCaptured() {
        assertThat(controller.captureDisclosure()).isNotBlank();
    }

    private void selectPullRequest(int number, String repositoryFullName) {
        ImportedPullRequest pr = new ImportedPullRequest(number, "A PR", "author", "base-sha", "head-sha",
                List.of(), List.of());
        webSession.connect("test-token");
        webSession.select(new WebSession.SelectedPullRequest(
                pr, repositoryFullName, headRoot, baseRoot, headRoot, new ReviewStateStore(), new AnnotationBoard(),
                new ReviewSubmission()));
    }
}
