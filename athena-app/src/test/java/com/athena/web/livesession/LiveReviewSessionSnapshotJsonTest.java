package com.athena.web.livesession;

import com.athena.livesession.CanvasFocus;
import com.athena.livesession.LiveReviewSession;
import com.athena.livesession.LiveReviewSessionRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies a {@link LiveReviewSessionSnapshot} (including its embedded
 * {@link CanvasFocusResponse}) actually serializes to the JSON shape the
 * frontend expects (ticket #158) — a genuine external boundary
 * (Jackson/HTTP wire format), not one of this ticket's own classes, so
 * this is the one place a plain {@link ObjectMapper} stands in for the
 * real HTTP round-trip rather than exercising {@link LiveReviewSessionController}
 * through Spring itself (CODE_STYLE.md &sect;F.6 reserves that for
 * whole-application black-box tests).
 */
class LiveReviewSessionSnapshotJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void aFreshSnapshotSerializesWithPlainNullableFocusFields() throws Exception {
        LiveReviewSession session = new LiveReviewSessionRegistry().create("acme/widgets", 42, "Petros");

        JsonNode json = mapper.readTree(mapper.writeValueAsString(LiveReviewSessionSnapshot.of(session)));

        assertThat(json.get("sessionId").asText()).isEqualTo(session.id());
        assertThat(json.get("repositoryFullName").asText()).isEqualTo("acme/widgets");
        assertThat(json.get("pullRequestNumber").asInt()).isEqualTo(42);
        JsonNode sharedFocus = json.get("sharedFocus");
        assertThat(sharedFocus.get("zoomLevel").asText()).isEqualTo("OVERVIEW");
        assertThat(sharedFocus.get("selectedEntityId").isNull()).isTrue();
        JsonNode participants = json.get("participants");
        assertThat(participants).hasSize(1);
        assertThat(participants.get(0).get("displayName").asText()).isEqualTo("Petros");
        assertThat(participants.get(0).get("personalFocus").isNull()).isTrue();
    }

    @Test
    void aPopulatedFocusSerializesEveryField() throws Exception {
        LiveReviewSession session = new LiveReviewSessionRegistry().create("acme/widgets", 42, "Petros");
        session.presentFocus(session.creatorId(),
                CanvasFocus.of("ARCHITECTURE", "component:PaymentValidator", "abc123", "PaymentValidator"));

        JsonNode sharedFocus =
                mapper.readTree(mapper.writeValueAsString(LiveReviewSessionSnapshot.of(session))).get("sharedFocus");

        assertThat(sharedFocus.get("zoomLevel").asText()).isEqualTo("ARCHITECTURE");
        assertThat(sharedFocus.get("selectedEntityId").asText()).isEqualTo("component:PaymentValidator");
        assertThat(sharedFocus.get("selectedChangeKey").asText()).isEqualTo("abc123");
        assertThat(sharedFocus.get("navigationContext").asText()).isEqualTo("PaymentValidator");
    }
}
