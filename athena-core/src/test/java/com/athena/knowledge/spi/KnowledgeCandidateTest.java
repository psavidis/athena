package com.athena.knowledge.spi;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeCandidateTest {

    private static final Instant PROPOSED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void carriesNoRationaleWhenNoneIsGiven() {
        KnowledgeCandidate candidate = KnowledgeCandidate.of("acme/checkout", "content", PROPOSED_AT);

        assertThat(candidate.repositoryContext()).isEqualTo("acme/checkout");
        assertThat(candidate.content()).isEqualTo("content");
        assertThat(candidate.rationale()).isEmpty();
        assertThat(candidate.proposedAt()).isEqualTo(PROPOSED_AT);
    }

    @Test
    void carriesARationaleWhenGiven() {
        KnowledgeCandidate candidate = KnowledgeCandidate.withRationale("acme/checkout", "content",
                "accepted AI finding", PROPOSED_AT);

        assertThat(candidate.rationale()).contains("accepted AI finding");
    }

    @Test
    void rejectsBlankContent() {
        assertThatThrownBy(() -> KnowledgeCandidate.of("acme/checkout", "  ", PROPOSED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
