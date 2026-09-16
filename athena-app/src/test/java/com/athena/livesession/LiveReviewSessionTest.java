package com.athena.livesession;

import com.athena.reviewui.AnnotationScope;
import com.athena.reviewui.Comment;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Dedicated unit test for {@link LiveReviewSession} (ticket #158) — the
 * central new aggregate this ticket introduces. Exercises its public API
 * directly, with no Spring/HTTP involved at all (that thin layer is
 * {@code LiveReviewSessionController}'s own job, covered separately).
 */
class LiveReviewSessionTest {

    private static final CanvasFocus PAYMENT_VALIDATOR =
            CanvasFocus.of("ARCHITECTURE", "component:PaymentValidator", null, "PaymentValidator");
    private static final CanvasFocus ORDER_SERVICE =
            CanvasFocus.of("ARCHITECTURE", "component:OrderService", null, "OrderService");

    @Test
    void creatingASessionJoinsItsCreatorAsFirstParticipantAndPresenter() {
        LiveReviewSession session = LiveReviewSession.create("acme/widgets", 42, "Petros");

        assertThat(session.repositoryFullName()).isEqualTo("acme/widgets");
        assertThat(session.pullRequestNumber()).isEqualTo(42);
        assertThat(session.presenterId()).contains(session.creatorId());
        assertThat(session.snapshot().participants())
                .singleElement()
                .satisfies(p -> {
                    assertThat(p.displayName()).isEqualTo("Petros");
                    assertThat(p.mode()).isEqualTo(ParticipantMode.PRESENTING);
                    assertThat(p.connected()).isTrue();
                });
    }

    @Test
    void aSecondParticipantCanJoinAndBothAreListed() {
        LiveReviewSession session = LiveReviewSession.create("acme/widgets", 42, "Petros");

        Participant maria = session.join(Optional.empty(), "Maria");

        assertThat(session.snapshot().participants())
                .extracting(ParticipantSnapshot::displayName)
                .containsExactlyInAnyOrder("Petros", "Maria");
        assertThat(maria.mode()).isEqualTo(ParticipantMode.FOLLOWING);
    }

    @Test
    void leavingRemovesTheParticipantEntirely() {
        LiveReviewSession session = LiveReviewSession.create("acme/widgets", 42, "Petros");
        Participant maria = session.join(Optional.empty(), "Maria");

        session.leave(maria.id());

        assertThat(session.snapshot().participants())
                .extracting(ParticipantSnapshot::displayName)
                .containsExactly("Petros");
    }

    @Test
    void leavingWhilePresentingClearsThePresenter() {
        LiveReviewSession session = LiveReviewSession.create("acme/widgets", 42, "Petros");

        session.leave(session.creatorId());

        assertThat(session.presenterId()).isEmpty();
    }

    @Test
    void disconnectingMarksTheParticipantInactiveWithoutRemovingThem() {
        LiveReviewSession session = LiveReviewSession.create("acme/widgets", 42, "Petros");
        Participant maria = session.join(Optional.empty(), "Maria");

        session.disconnect(maria.id());

        List<ParticipantSnapshot> participants = session.snapshot().participants();
        assertThat(participants).extracting(ParticipantSnapshot::displayName).contains("Petros", "Maria");
        assertThat(participants).filteredOn(p -> p.displayName().equals("Maria"))
                .singleElement()
                .satisfies(p -> assertThat(p.connected()).isFalse());
    }

    @Test
    void reconnectingWithTheSameParticipantIdResumesTheSameIdentity() {
        LiveReviewSession session = LiveReviewSession.create("acme/widgets", 42, "Petros");
        Participant maria = session.join(Optional.empty(), "Maria");
        session.disconnect(maria.id());

        Participant reconnected = session.join(Optional.of(maria.id()), "Maria");

        assertThat(reconnected.id()).isEqualTo(maria.id());
        assertThat(session.snapshot().participants())
                .filteredOn(p -> p.participantId().equals(maria.id()))
                .singleElement()
                .satisfies(p -> assertThat(p.connected()).isTrue());
    }

    @Test
    void joiningWithAnUnknownParticipantIdStartsFreshInsteadOfFailing() {
        LiveReviewSession session = LiveReviewSession.create("acme/widgets", 42, "Petros");

        Participant participant = session.join(Optional.of("not-a-real-id"), "Maria");

        assertThat(participant.id()).isNotEqualTo("not-a-real-id");
        assertThat(participant.displayName()).isEqualTo("Maria");
    }

    @Test
    void onlyThePresenterCanMoveTheSharedFocus() {
        LiveReviewSession session = LiveReviewSession.create("acme/widgets", 42, "Petros");
        Participant maria = session.join(Optional.empty(), "Maria");

        assertThatThrownBy(() -> session.presentFocus(maria.id(), PAYMENT_VALIDATOR))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void thePresenterMovingTheSharedFocusUpdatesItForEveryone() {
        LiveReviewSession session = LiveReviewSession.create("acme/widgets", 42, "Petros");
        session.join(Optional.empty(), "Maria");

        session.presentFocus(session.creatorId(), PAYMENT_VALIDATOR);

        assertThat(session.snapshot().sharedFocus()).isEqualTo(PAYMENT_VALIDATOR);
    }

    @Test
    void takingControlReassignsThePresenterAndDemotesTheOldOne() {
        LiveReviewSession session = LiveReviewSession.create("acme/widgets", 42, "Petros");
        Participant maria = session.join(Optional.empty(), "Maria");

        session.takeControl(maria.id());

        assertThat(session.presenterId()).contains(maria.id());
        assertThat(session.snapshot().participants())
                .filteredOn(p -> p.participantId().equals(session.creatorId()))
                .singleElement()
                .satisfies(p -> assertThat(p.mode()).isEqualTo(ParticipantMode.FOLLOWING));
    }

    @Test
    void exploringIndependentlyDoesNotChangeTheSharedFocus() {
        LiveReviewSession session = LiveReviewSession.create("acme/widgets", 42, "Petros");
        Participant maria = session.join(Optional.empty(), "Maria");
        session.presentFocus(session.creatorId(), PAYMENT_VALIDATOR);

        session.explore(maria.id(), ORDER_SERVICE);

        assertThat(session.snapshot().sharedFocus()).isEqualTo(PAYMENT_VALIDATOR);
        assertThat(session.snapshot().participants())
                .filteredOn(p -> p.participantId().equals(maria.id()))
                .singleElement()
                .satisfies(p -> {
                    assertThat(p.mode()).isEqualTo(ParticipantMode.EXPLORING);
                    assertThat(p.personalFocus()).isEqualTo(ORDER_SERVICE);
                });
    }

    @Test
    void explorIndependentlyAsThePresenterGivesUpControl() {
        LiveReviewSession session = LiveReviewSession.create("acme/widgets", 42, "Petros");

        session.explore(session.creatorId(), ORDER_SERVICE);

        assertThat(session.presenterId()).isEmpty();
    }

    @Test
    void followingReturnsToTheSharedViewAndClearsPersonalFocus() {
        LiveReviewSession session = LiveReviewSession.create("acme/widgets", 42, "Petros");
        Participant maria = session.join(Optional.empty(), "Maria");
        session.explore(maria.id(), ORDER_SERVICE);

        session.follow(maria.id());

        assertThat(session.snapshot().participants())
                .filteredOn(p -> p.participantId().equals(maria.id()))
                .singleElement()
                .satisfies(p -> {
                    assertThat(p.mode()).isEqualTo(ParticipantMode.FOLLOWING);
                    assertThat(p.personalFocus()).isNull();
                });
    }

    @Test
    void addingACommentIsVisibleThroughCommentsAt() {
        LiveReviewSession session = LiveReviewSession.create("acme/widgets", 42, "Petros");
        AnnotationScope scope = AnnotationScope.canvasItem("component:PaymentValidator");

        session.addComment(session.creatorId(), scope, "Should this retry on timeout?");

        List<Comment> comments = session.commentsAt(scope);
        assertThat(comments).extracting(Comment::text).containsExactly("Should this retry on timeout?");
        assertThat(comments).extracting(Comment::author).containsExactly("Petros");
    }

    @Test
    void aBlankCommentIsRejected() {
        LiveReviewSession session = LiveReviewSession.create("acme/widgets", 42, "Petros");
        AnnotationScope scope = AnnotationScope.canvasItem("component:PaymentValidator");

        assertThatThrownBy(() -> session.addComment(session.creatorId(), scope, "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onlyTheCreatorCanEndTheSession() {
        LiveReviewSession session = LiveReviewSession.create("acme/widgets", 42, "Petros");
        Participant maria = session.join(Optional.empty(), "Maria");

        assertThatThrownBy(() -> session.end(maria.id())).isInstanceOf(IllegalStateException.class);

        session.end(session.creatorId());
        assertThat(session.ended()).isTrue();
    }

    @Test
    void actingAsAnUnknownParticipantFails() {
        LiveReviewSession session = LiveReviewSession.create("acme/widgets", 42, "Petros");

        assertThatThrownBy(() -> session.follow("ghost")).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void everyMutationIncreasesTheRevisionAndBroadcastsToListeners() {
        LiveReviewSession session = LiveReviewSession.create("acme/widgets", 42, "Petros");
        List<LiveReviewSessionSnapshot> received = new ArrayList<>();
        session.addListener(received::add);

        session.join(Optional.empty(), "Maria");
        session.presentFocus(session.creatorId(), PAYMENT_VALIDATOR);

        assertThat(received).hasSize(2);
        assertThat(received.get(0).revision()).isLessThan(received.get(1).revision());
    }

    @Test
    void removingAListenerStopsFurtherNotifications() {
        LiveReviewSession session = LiveReviewSession.create("acme/widgets", 42, "Petros");
        List<LiveReviewSessionSnapshot> received = new ArrayList<>();
        java.util.function.Consumer<LiveReviewSessionSnapshot> listener = received::add;
        session.addListener(listener);
        session.removeListener(listener);

        session.join(Optional.empty(), "Maria");

        assertThat(received).isEmpty();
    }
}
