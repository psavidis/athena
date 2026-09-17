package com.athena.reviewreplay;

import com.athena.memory.MemoryEntry;
import com.athena.reviewrecorder.Moment;
import com.athena.reviewrecorder.MomentKind;
import com.athena.reviewrecorder.ReviewRecording;
import com.athena.reviewrecorder.ReviewRecordingArtifact;
import com.athena.reviewrecorder.ReviewRecordingRegistry;
import com.athena.reviewrecorder.SemanticEvent;
import com.athena.reviewrecorder.SemanticEventType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Dedicated unit test for {@link OutcomePromotion} (ticket #216). */
class OutcomePromotionTest {

    private static final Instant START = Instant.parse("2026-09-17T10:00:00Z");

    @Test
    void buildsAConfirmedHighConfidenceEntryWithOriginAsEvidence() {
        ReviewReplay replay = replayWithOneConfirmedInsightAbout("OrderService");

        Optional<MemoryEntry> entry = OutcomePromotion.entryFor(replay, OutcomeSection.LEARNED, "entity:OrderService",
                "Retries are capped at 3 attempts");

        assertThat(entry).isPresent();
        assertThat(entry.get().fact()).isEqualTo("Retries are capped at 3 attempts");
        assertThat(entry.get().developerConfirmed()).isTrue();
        assertThat(entry.get().confidence()).isEqualTo("high");
        assertThat(entry.get().evidence()).contains("acme/widgets").contains("PR 42").contains("entity:OrderService");
    }

    @Test
    void isEmptyWhenNoItemMatchesTheSectionAndReference() {
        ReviewReplay replay = replayWithOneConfirmedInsightAbout("OrderService");

        Optional<MemoryEntry> entry = OutcomePromotion.entryFor(replay, OutcomeSection.DECIDED, "entity:OrderService", "Some fact");

        assertThat(entry).isEmpty();
    }

    @Test
    void isEmptyWhenTheReferenceDoesNotMatchAnyItemInThatSection() {
        ReviewReplay replay = replayWithOneConfirmedInsightAbout("OrderService");

        Optional<MemoryEntry> entry = OutcomePromotion.entryFor(replay, OutcomeSection.LEARNED, "entity:PaymentGateway", "Some fact");

        assertThat(entry).isEmpty();
    }

    private static ReviewReplay replayWithOneConfirmedInsightAbout(String entityName) {
        ReviewRecordingRegistry registry = new ReviewRecordingRegistry(Clock.fixed(START, ZoneOffset.UTC));
        ReviewRecording recording = registry.start("acme/widgets", 42, "abc123", "Petros");
        recording.capture(SemanticEvent.of(SemanticEventType.ENTITY_INSPECTED, "entity:" + entityName, START));
        Moment insight = recording.tagMoment(MomentKind.INSIGHT);
        recording.confirmMoment(insight.id());
        recording.stop();
        ReviewRecordingArtifact artifact = ReviewRecordingArtifact.of(recording);
        return ReviewReplay.open(artifact, new KnownEntityReferenceResolver(() -> Set.of(entityName)));
    }
}
