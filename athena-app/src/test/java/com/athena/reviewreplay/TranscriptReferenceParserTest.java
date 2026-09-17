package com.athena.reviewreplay;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Dedicated unit test for {@link TranscriptReferenceParser} (ticket #213). */
class TranscriptReferenceParserTest {

    @Test
    void findsATranscriptLinkedNearTheWordTranscript() {
        List<TranscriptReference> references =
                TranscriptReferenceParser.discover("**Transcript:** [Recording](https://otter.ai/s/abc123)");

        assertThat(references).hasSize(1);
        assertThat(references.get(0).platform()).isEqualTo("otter.ai");
        assertThat(references.get(0).identifier()).isEqualTo("/s/abc123");
        assertThat(references.get(0).url()).isEqualTo("https://otter.ai/s/abc123");
        assertThat(references.get(0).format()).isEmpty();
    }

    @Test
    void findsNothingWhenTheDescriptionHasNoTranscriptMention() {
        assertThat(TranscriptReferenceParser.discover("Fixes the login bug.")).isEmpty();
    }

    @Test
    void ignoresALinkNotNearTheWordTranscript() {
        assertThat(TranscriptReferenceParser.discover("See [the ticket](https://example.com/TICKET-1)")).isEmpty();
    }

    @Test
    void findsEveryTranscriptLinkAcrossMultipleLines() {
        String description = """
                **Transcript (part 1):** [Recording](https://otter.ai/s/abc123)
                **Transcript (part 2):** [Recording](https://otter.ai/s/def456)
                """;

        List<TranscriptReference> references = TranscriptReferenceParser.discover(description);

        assertThat(references).hasSize(2);
        assertThat(references).extracting(TranscriptReference::url)
                .containsExactly("https://otter.ai/s/abc123", "https://otter.ai/s/def456");
    }

    @Test
    void findsNothingForABlankDescription() {
        assertThat(TranscriptReferenceParser.discover("")).isEmpty();
    }

    @Test
    void findsNothingForANullDescription() {
        assertThat(TranscriptReferenceParser.discover(null)).isEmpty();
    }

    @Test
    void fallsBackToTheHostAsIdentifierWhenTheUrlHasNoPath() {
        List<TranscriptReference> references =
                TranscriptReferenceParser.discover("Transcript: [Recording](https://otter.ai)");

        assertThat(references).hasSize(1);
        assertThat(references.get(0).identifier()).isEqualTo("otter.ai");
    }
}
