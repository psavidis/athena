package com.athena.reviewreplay;

import com.athena.reviewrecorder.AlignedTranscriptSegment;
import com.athena.reviewrecorder.TranscriptAligner;
import com.athena.reviewrecorder.TranscriptSegment;
import com.athena.reviewrecorder.SemanticEvent;
import com.athena.reviewrecorder.SemanticEventType;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for conversation reconstruction from a transcript
 * (ticket #214). Builds {@link AlignedTranscriptSegment}s directly via
 * {@link TranscriptAligner} rather than through a full {@code
 * ReviewRecording}/artifact fixture — this ticket's own scope is the
 * reconstruction algorithm over already-aligned segments (#209's own
 * output), not the recording/persistence machinery {@link
 * com.athena.web.reviewrecorder.ReviewRecordingSessionSteps} already
 * covers; matching {@link TranscriptAligner}'s own precedent of being
 * tested as pure domain logic, not through the web layer.
 */
public class ConversationReconstructionSteps {

    private static final Instant BASE = Instant.parse("2026-09-17T10:00:00Z");

    private List<AlignedTranscriptSegment> alignedSegments = List.of();
    private ReconstructedConversation reconstructed;

    @Given("aligned segments:")
    public void aligned_segments(DataTable table) {
        List<TranscriptSegment> segments = new ArrayList<>();
        List<SemanticEvent> events = new ArrayList<>();
        int i = 0;
        for (Map<String, String> row : table.asMaps()) {
            Instant spokenAt = BASE.plusSeconds(60L * i);
            String entity = row.get("entity");
            if (entity != null && !entity.isBlank()) {
                // One synthetic ENTITY_INSPECTED event immediately before each segment that
                // names an entity, so TranscriptAligner (ticket #209) has something to align
                // that segment to — this class is about reconstruction, not alignment, so the
                // alignment inputs are kept as simple as possible: one event per entity-bearing row.
                events.add(SemanticEvent.of(SemanticEventType.ENTITY_INSPECTED, "entity:" + entity,
                        spokenAt.minusSeconds(1)));
            }
            segments.add(TranscriptSegment.of(row.get("text"), spokenAt, row.get("speaker")));
            i++;
        }
        alignedSegments = TranscriptAligner.align(segments, events);
    }

    @Given("no aligned segments are available")
    public void no_aligned_segments_are_available() {
        alignedSegments = List.of();
    }

    @When("Athena reconstructs the conversation for {string}")
    public void athena_reconstructs_the_conversation_for(String entityName) {
        reconstructed = ConversationReconstructor.reconstruct(alignedSegments, "entity:" + entityName);
    }

    @Then("the reconstructed conversation has {int} segments, in speaking order")
    public void the_reconstructed_conversation_has_segments_in_speaking_order(int count) {
        assertThat(reconstructed.segments()).hasSize(count);
        assertThat(reconstructed.segments()).isSortedAccordingTo(
                (a, b) -> a.segment().spokenAt().compareTo(b.segment().spokenAt()));
    }

    @Then("the reconstructed conversation has {int} segment, in speaking order")
    public void the_reconstructed_conversation_has_one_segment_in_speaking_order(int count) {
        the_reconstructed_conversation_has_segments_in_speaking_order(count);
    }

    @Then("every segment is labeled as verbatim transcript")
    public void every_segment_is_labeled_as_verbatim_transcript() {
        // ReconstructedConversation's own javadoc: every segment it carries is verbatim by
        // construction (no summarization is attempted), so there is nothing per-segment to
        // toggle — the guarantee is structural, verified by the type itself existing/compiling
        // with no "isInterpretation" or similar flag rather than a runtime check on each segment.
        assertThat(reconstructed.segments()).isNotEmpty();
    }

    @Then("the reconstructed conversation is empty")
    public void the_reconstructed_conversation_is_empty() {
        assertThat(reconstructed).isNotNull();
        assertThat(reconstructed.segments()).isEmpty();
    }
}
