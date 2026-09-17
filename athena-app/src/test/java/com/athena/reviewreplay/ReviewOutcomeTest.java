package com.athena.reviewreplay;

import com.athena.reviewrecorder.Moment;
import com.athena.reviewrecorder.MomentKind;
import com.athena.reviewrecorder.ReviewRecording;
import com.athena.reviewrecorder.ReviewRecordingRegistry;
import com.athena.reviewrecorder.SemanticEvent;
import com.athena.reviewrecorder.SemanticEventType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Dedicated unit test for {@link ReviewOutcome#derive} (ticket #215). */
class ReviewOutcomeTest {

    private static final Instant START = Instant.parse("2026-09-17T10:00:00Z");

    private final ReviewRecordingRegistry registry = new ReviewRecordingRegistry(Clock.fixed(START, ZoneOffset.UTC));

    @Test
    void derivesLearnedFromConfirmedInsightMoments() {
        ReviewRecording recording = newRecording();
        Moment insight = tagMoment(recording, "OrderService", MomentKind.INSIGHT);
        recording.confirmMoment(insight.id());

        ReviewOutcome outcome = ReviewOutcome.derive(recording.moments());

        assertThat(outcome.learned()).hasSize(1);
        assertThat(outcome.learned().get(0).reference()).contains("entity:OrderService");
        assertThat(outcome.decided()).isEmpty();
        assertThat(outcome.unresolved()).isEmpty();
        assertThat(outcome.actions()).isEmpty();
    }

    @Test
    void derivesDecidedFromConfirmedDecisionMoments() {
        ReviewRecording recording = newRecording();
        Moment decision = tagMoment(recording, "PaymentGateway", MomentKind.DECISION);
        recording.confirmMoment(decision.id());

        ReviewOutcome outcome = ReviewOutcome.derive(recording.moments());

        assertThat(outcome.decided()).hasSize(1);
        assertThat(outcome.decided().get(0).reference()).contains("entity:PaymentGateway");
    }

    @Test
    void derivesUnresolvedFromConfirmedQuestionsAndConcerns() {
        ReviewRecording recording = newRecording();
        Moment question = tagMoment(recording, "OrderService", MomentKind.QUESTION);
        recording.confirmMoment(question.id());
        Moment concern = tagMoment(recording, "PaymentGateway", MomentKind.CONCERN);
        recording.confirmMoment(concern.id());

        ReviewOutcome outcome = ReviewOutcome.derive(recording.moments());

        assertThat(outcome.unresolved()).hasSize(2);
        assertThat(outcome.unresolved()).extracting(item -> item.reference().orElseThrow())
                .containsExactlyInAnyOrder("entity:OrderService", "entity:PaymentGateway");
    }

    @Test
    void derivesActionsFromConfirmedActionMoments() {
        ReviewRecording recording = newRecording();
        Moment action = tagMoment(recording, "OrderService", MomentKind.ACTION);
        recording.confirmMoment(action.id());

        ReviewOutcome outcome = ReviewOutcome.derive(recording.moments());

        assertThat(outcome.actions()).hasSize(1);
    }

    @Test
    void excludesAPendingMomentNeverConfirmed() {
        ReviewRecording recording = newRecording();
        tagMoment(recording, "OrderService", MomentKind.INSIGHT);

        ReviewOutcome outcome = ReviewOutcome.derive(recording.moments());

        assertThat(outcome.learned()).isEmpty();
    }

    @Test
    void excludesARejectedMoment() {
        ReviewRecording recording = newRecording();
        Moment insight = tagMoment(recording, "OrderService", MomentKind.INSIGHT);
        recording.rejectMoment(insight.id());

        ReviewOutcome outcome = ReviewOutcome.derive(recording.moments());

        assertThat(outcome.learned()).isEmpty();
    }

    @Test
    void isEmptyForNoMoments() {
        ReviewOutcome outcome = ReviewOutcome.derive(List.of());

        assertThat(outcome.learned()).isEmpty();
        assertThat(outcome.decided()).isEmpty();
        assertThat(outcome.unresolved()).isEmpty();
        assertThat(outcome.actions()).isEmpty();
    }

    private ReviewRecording newRecording() {
        return registry.start("acme/widgets", 42, "abc123", "Petros");
    }

    private Moment tagMoment(ReviewRecording recording, String entityName, MomentKind kind) {
        recording.capture(SemanticEvent.of(SemanticEventType.ENTITY_INSPECTED, "entity:" + entityName, START));
        return recording.tagMoment(kind);
    }
}
