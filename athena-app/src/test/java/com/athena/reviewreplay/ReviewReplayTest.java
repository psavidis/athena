package com.athena.reviewreplay;

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

/** Dedicated unit test for {@link ReviewReplay} (ticket #210). */
class ReviewReplayTest {

    private static final Instant START = Instant.parse("2026-09-17T10:00:00Z");

    @Test
    void carriesTheArtifactsIdentityUnchanged() {
        ReviewRecordingArtifact artifact = artifactWithOneQuestionAbout("OrderService");

        ReviewReplay replay = ReviewReplay.open(artifact, new KnownEntityReferenceResolver(Set::of));

        assertThat(replay.recordingId()).isEqualTo(artifact.recordingId());
        assertThat(replay.repositoryFullName()).isEqualTo("acme/widgets");
        assertThat(replay.pullRequestNumber()).isEqualTo(42);
        assertThat(replay.commitOrVersion()).isEqualTo("abc123");
    }

    @Test
    void resolvesAMomentReferenceStillPresentInTheCurrentCodebase() {
        ReviewRecordingArtifact artifact = artifactWithOneQuestionAbout("OrderService");

        ReviewReplay replay = ReviewReplay.open(artifact, new KnownEntityReferenceResolver(() -> Set.of("OrderService")));

        Optional<ResolvedReference> resolved = replay.resolvedReference("entity:OrderService");
        assertThat(resolved).isPresent();
        assertThat(resolved.get().resolved()).isTrue();
        assertThat(resolved.get().resolvedLabel()).contains("OrderService");
    }

    @Test
    void showsAReferenceNoLongerInTheCurrentCodebaseAsUnresolved() {
        ReviewRecordingArtifact artifact = artifactWithOneQuestionAbout("OrderService");

        ReviewReplay replay = ReviewReplay.open(artifact, new KnownEntityReferenceResolver(Set::of));

        Optional<ResolvedReference> resolved = replay.resolvedReference("entity:OrderService");
        assertThat(resolved).isPresent();
        assertThat(resolved.get().resolved()).isFalse();
        assertThat(resolved.get().resolvedLabel()).isEmpty();
    }

    @Test
    void identityIsStillReportedEvenWhenAReferenceIsUnresolved() {
        ReviewRecordingArtifact artifact = artifactWithOneQuestionAbout("OrderService");

        ReviewReplay replay = ReviewReplay.open(artifact, new KnownEntityReferenceResolver(Set::of));

        assertThat(replay.repositoryFullName()).isEqualTo("acme/widgets");
        assertThat(replay.pullRequestNumber()).isEqualTo(42);
    }

    private static ReviewRecordingArtifact artifactWithOneQuestionAbout(String entityName) {
        ReviewRecordingRegistry registry = new ReviewRecordingRegistry(Clock.fixed(START, ZoneOffset.UTC));
        ReviewRecording recording = registry.start("acme/widgets", 42, "abc123", "Petros");
        recording.capture(SemanticEvent.of(SemanticEventType.ENTITY_INSPECTED, "entity:" + entityName, START));
        Moment question = recording.tagMoment(MomentKind.QUESTION);
        recording.confirmMoment(question.id());
        recording.stop();
        return ReviewRecordingArtifact.of(recording);
    }
}
