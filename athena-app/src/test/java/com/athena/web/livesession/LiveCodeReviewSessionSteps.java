package com.athena.web.livesession;

import com.athena.git.TempDirectories;
import com.athena.livesession.LiveReviewSession;
import com.athena.livesession.LiveReviewSessionRegistry;
import com.athena.livesession.LiveReviewSessionSnapshot;
import com.athena.livesession.ParticipantMode;
import com.athena.livesession.ParticipantSnapshot;
import com.athena.plugins.PluginRegistry;
import com.athena.repository.ImportedPullRequest;
import com.athena.reviewcontext.ReviewSubmission;
import com.athena.reviewui.AnnotationBoard;
import com.athena.semantic.PrAnalyzer;
import com.athena.semantic.ReviewStateStore;
import com.athena.web.WebSession;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for all three Live Code Review Session feature files
 * (ticket #158) — lifecycle, shared navigation, and collaborative
 * comments. Kept as one class since every scenario across the three files
 * builds on the same "session started, a second reviewer joined" fixture;
 * splitting it up would only reproduce {@code ChangeMapControllerSteps}'s
 * own documented reason for staying single (avoiding Cucumber-JVM's
 * duplicate-step-definition error across sibling files).
 *
 * <p>One {@link LiveReviewSessionController} instance is shared by both
 * named reviewers here, exactly as production does: only {@link #create}
 * ever consults {@code WebSession}, so a single controller bound to
 * "Petros"'s session is enough — "Maria"'s actions never touch
 * {@code WebSession} at all, only the session id + her own participant id,
 * matching how any other browser's requests would arrive at the same
 * singleton controller bean in production.
 */
public class LiveCodeReviewSessionSteps {

    private final WebSession webSession =
            new WebSession(new PrAnalyzer(PluginRegistry.languagePlugins(), PluginRegistry.frameworkPlugins()));
    private final LiveReviewSessionRegistry registry = new LiveReviewSessionRegistry();
    private final LiveReviewSessionController controller = new LiveReviewSessionController(webSession, registry);

    private final Map<String, String> participantIdsByName = new HashMap<>();
    private String sessionId;
    private LiveReviewSessionSnapshot lastSnapshot;
    private ResponseStatusException failure;

    private Path baseRoot;
    private Path headRoot;

    @Before
    public void createTempRoots() throws IOException {
        baseRoot = Files.createTempDirectory("athena-live-session-base-");
        headRoot = Files.createTempDirectory("athena-live-session-head-");
    }

    @After
    public void cleanUpTempRoots() {
        TempDirectories.deleteRecursively(baseRoot);
        TempDirectories.deleteRecursively(headRoot);
    }

    @Given("a reviewer has PR {int} in {string} selected")
    public void a_reviewer_has_pr_selected(int number, String repositoryFullName) {
        selectPullRequest(number, repositoryFullName);
    }

    @Given("a reviewer has no PR or Diff selected")
    public void a_reviewer_has_no_pr_or_diff_selected() {
        // no-op: a fresh WebSession starts with nothing selected
    }

    @When("the reviewer starts a Live Code Review Session as {string}")
    public void the_reviewer_starts_a_live_code_review_session_as(String displayName) {
        attemptCreate(displayName);
    }

    @When("the reviewer attempts to start a Live Code Review Session as {string}")
    public void the_reviewer_attempts_to_start_a_live_code_review_session_as(String displayName) {
        attemptCreate(displayName);
    }

    private void attemptCreate(String displayName) {
        try {
            LiveSessionJoinResponse response = controller.create(new CreateLiveSessionRequest(displayName));
            sessionId = response.sessionId();
            participantIdsByName.put(displayName, response.participantId());
            lastSnapshot = response.snapshot();
        } catch (ResponseStatusException e) {
            failure = e;
        }
    }

    @Given("{string} has started a Live Code Review Session for PR {int} in {string}")
    public void has_started_a_live_code_review_session(String displayName, int number, String repositoryFullName) {
        selectPullRequest(number, repositoryFullName);
        attemptCreate(displayName);
    }

    @Then("the session exists")
    public void the_session_exists() {
        assertThat(sessionId).isNotNull();
        assertThat(registry.find(sessionId)).isPresent();
    }

    @Then("{string} appears as a participant in the session")
    @Then("{string} still appears as a participant in the session")
    public void appears_as_a_participant_in_the_session(String displayName) {
        assertThat(currentSnapshot().participants())
                .anySatisfy(p -> assertThat(p.displayName()).isEqualTo(displayName));
    }

    @Then("{string} appears as an active participant in the session")
    public void appears_as_an_active_participant(String displayName) {
        assertThat(currentSnapshot().participants())
                .anySatisfy(p -> {
                    assertThat(p.displayName()).isEqualTo(displayName);
                    assertThat(p.connected()).isTrue();
                });
    }

    @Then("{string} no longer appears as an active participant in the session")
    public void no_longer_appears_as_an_active_participant(String displayName) {
        List<ParticipantSnapshot> active = currentSnapshot().participants().stream()
                .filter(ParticipantSnapshot::connected)
                .toList();
        assertThat(active).noneSatisfy(p -> assertThat(p.displayName()).isEqualTo(displayName));
    }

    @Then("{string} is the session's presenter")
    public void is_the_sessions_presenter(String displayName) {
        String presenterId = currentSnapshot().presenterId();
        assertThat(presenterId).isEqualTo(participantIdsByName.get(displayName));
    }

    @Then("{string} is no longer the session's presenter")
    public void is_no_longer_the_sessions_presenter(String displayName) {
        String presenterId = currentSnapshot().presenterId();
        assertThat(presenterId).isNotEqualTo(participantIdsByName.get(displayName));
    }

    @When("{string} joins that session")
    public void joins_that_session(String displayName) {
        attemptJoin(displayName, null);
    }

    @Given("{string} has joined that session")
    public void has_joined_that_session(String displayName) {
        attemptJoin(displayName, null);
    }

    @When("a reviewer attempts to join session {string}")
    public void a_reviewer_attempts_to_join_session(String unknownSessionId) {
        try {
            controller.join(unknownSessionId, new JoinLiveSessionRequest("Someone", null));
        } catch (ResponseStatusException e) {
            failure = e;
        }
    }

    private void attemptJoin(String displayName, String existingParticipantId) {
        try {
            LiveSessionJoinResponse response =
                    controller.join(sessionId, new JoinLiveSessionRequest(displayName, existingParticipantId));
            participantIdsByName.put(displayName, response.participantId());
            lastSnapshot = response.snapshot();
        } catch (ResponseStatusException e) {
            failure = e;
        }
    }

    @Then("the state {string} receives on joining shows {string} as the presenter")
    public void the_state_receives_on_joining_shows_as_the_presenter(String joiner, String presenterName) {
        assertThat(lastSnapshot.presenterId()).isEqualTo(participantIdsByName.get(presenterName));
    }

    @Then("the state {string} receives on joining lists both {string} and {string} as participants")
    public void the_state_receives_on_joining_lists_both_as_participants(String joiner, String a, String b) {
        assertThat(lastSnapshot.participants())
                .extracting(ParticipantSnapshot::displayName)
                .contains(a, b);
    }

    @When("{string} leaves the session")
    public void leaves_the_session(String displayName) {
        try {
            lastSnapshot = controller.leave(sessionId, new LiveParticipantRequest(participantIdsByName.get(displayName)));
        } catch (ResponseStatusException e) {
            failure = e;
        }
    }

    @When("{string} disconnects from the session")
    public void disconnects_from_the_session(String displayName) {
        LiveReviewSession liveSession = registry.find(sessionId).orElseThrow();
        liveSession.disconnect(participantIdsByName.get(displayName));
    }

    @When("{string} reconnects to the session")
    public void reconnects_to_the_session(String displayName) {
        attemptJoin(displayName, participantIdsByName.get(displayName));
    }

    @Then("the live session request is rejected as invalid")
    public void the_attempt_is_rejected_as_invalid() {
        assertThat(failure).isNotNull();
        assertThat(failure.getStatusCode().is4xxClientError()).isTrue();
    }

    // --- Shared navigation ---

    @When("{string} moves the shared focus to the {string} component")
    public void moves_the_shared_focus_to_the_component(String displayName, String componentName) {
        try {
            lastSnapshot = controller.presentFocus(sessionId, componentFocus(displayName, componentName));
        } catch (ResponseStatusException e) {
            failure = e;
        }
    }

    @Given("{string} has moved the shared focus to the {string} component")
    public void has_moved_the_shared_focus_to_the_component(String displayName, String componentName) {
        moves_the_shared_focus_to_the_component(displayName, componentName);
    }

    @When("{string} attempts to move the shared focus to the {string} component")
    public void attempts_to_move_the_shared_focus_to_the_component(String displayName, String componentName) {
        moves_the_shared_focus_to_the_component(displayName, componentName);
    }

    @Then("the session's shared focus is the {string} component")
    public void the_sessions_shared_focus_is_the_component(String componentName) {
        assertThat(currentSnapshot().sharedFocus().selectedEntityId()).contains("component:" + componentName);
    }

    @Then("the session's shared focus is still the {string} component")
    public void the_sessions_shared_focus_is_still_the_component(String componentName) {
        the_sessions_shared_focus_is_the_component(componentName);
    }

    @When("{string} takes control of the session")
    public void takes_control_of_the_session(String displayName) {
        lastSnapshot = controller.takeControl(sessionId, new LiveParticipantRequest(participantIdsByName.get(displayName)));
    }

    @Given("{string} has taken control of the session")
    public void has_taken_control_of_the_session(String displayName) {
        takes_control_of_the_session(displayName);
    }

    @When("{string} explores the {string} component independently")
    public void explores_the_component_independently(String displayName, String componentName) {
        lastSnapshot = controller.explore(sessionId, componentFocus(displayName, componentName));
    }

    @Given("{string} is exploring the {string} component independently")
    public void is_exploring_the_component_independently(String displayName, String componentName) {
        explores_the_component_independently(displayName, componentName);
    }

    @When("{string} returns to the shared view")
    public void returns_to_the_shared_view(String displayName) {
        lastSnapshot = controller.follow(sessionId, new LiveParticipantRequest(participantIdsByName.get(displayName)));
    }

    @Then("{string} is shown as exploring the {string} component")
    public void is_shown_as_exploring_the_component(String displayName, String componentName) {
        assertThat(currentSnapshot().participants())
                .anySatisfy(p -> {
                    assertThat(p.displayName()).isEqualTo(displayName);
                    assertThat(p.mode()).isEqualTo(ParticipantMode.EXPLORING);
                    assertThat(p.personalFocus()).isNotNull();
                    assertThat(p.personalFocus().selectedEntityId()).contains("component:" + componentName);
                });
    }

    @Then("{string} is following the shared focus")
    public void is_following_the_shared_focus(String displayName) {
        assertThat(currentSnapshot().participants())
                .anySatisfy(p -> {
                    assertThat(p.displayName()).isEqualTo(displayName);
                    assertThat(p.mode()).isEqualTo(ParticipantMode.FOLLOWING);
                });
    }

    private LiveFocusRequest componentFocus(String displayName, String componentName) {
        return new LiveFocusRequest(participantIdsByName.get(displayName), "ARCHITECTURE",
                "component:" + componentName, null, componentName);
    }

    // --- Collaborative comments ---

    @When("{string} comments {string} on the {string} component")
    public void comments_on_the_component(String displayName, String text, String componentName) {
        try {
            controller.addComment(sessionId,
                    new LiveCommentRequest(participantIdsByName.get(displayName), "component:" + componentName, text));
        } catch (ResponseStatusException e) {
            failure = e;
        }
    }

    @When("{string} attempts to comment {string} on the {string} component")
    public void attempts_to_comment_on_the_component(String displayName, String text, String componentName) {
        comments_on_the_component(displayName, text, componentName);
    }

    @Then("{string} sees the comment {string} on the {string} component")
    public void sees_the_comment_on_the_component(String displayName, String text, String componentName) {
        List<LiveCommentResponse> comments = controller.comments(sessionId, "component:" + componentName);
        assertThat(comments).extracting(LiveCommentResponse::text).contains(text);
    }

    @Then("{string} does not see the comment {string} on the {string} component")
    public void does_not_see_the_comment_on_the_component(String displayName, String text, String componentName) {
        List<LiveCommentResponse> comments = controller.comments(sessionId, "component:" + componentName);
        assertThat(comments).extracting(LiveCommentResponse::text).doesNotContain(text);
    }

    private LiveReviewSessionSnapshot currentSnapshot() {
        return controller.snapshot(sessionId);
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
