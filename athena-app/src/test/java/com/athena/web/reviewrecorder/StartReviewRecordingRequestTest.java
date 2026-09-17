package com.athena.web.reviewrecorder;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dedicated unit test for {@link StartReviewRecordingRequest} (ticket
 * #208's {@code audioEnabled} field) — specifically that Jackson
 * deserializes real JSON through the canonical 3-arg constructor, not
 * the 2-arg Java-convenience overload, given the codebase's own
 * precedent (see {@code feedback_spring_constructor_ambiguity}) that an
 * extra constructor can silently confuse a framework's own
 * instantiation logic in ways only a real (de)serialization path catches
 * — no existing test in this package exercises this record through
 * actual JSON, only direct Java construction.
 */
class StartReviewRecordingRequestTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void deserializesAudioEnabledFromRealJson() throws Exception {
        String json = "{\"displayName\":\"Petros\",\"disclosureAcknowledged\":true,\"audioEnabled\":true}";

        StartReviewRecordingRequest request = mapper.readValue(json, StartReviewRecordingRequest.class);

        assertThat(request.displayName()).isEqualTo("Petros");
        assertThat(request.disclosureAcknowledged()).isTrue();
        assertThat(request.audioEnabled()).isTrue();
    }

    @Test
    void deserializesAMissingAudioEnabledAsFalse() throws Exception {
        String json = "{\"displayName\":\"Petros\",\"disclosureAcknowledged\":true}";

        StartReviewRecordingRequest request = mapper.readValue(json, StartReviewRecordingRequest.class);

        assertThat(request.audioEnabled()).isFalse();
    }
}
