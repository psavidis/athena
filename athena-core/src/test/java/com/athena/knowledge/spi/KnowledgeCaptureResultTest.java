package com.athena.knowledge.spi;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeCaptureResultTest {

    private static final KnowledgeItem ITEM = KnowledgeItem
            .builder("obsidian", "Title", "content", "Athena Knowledge/note.md", Instant.parse("2026-01-01T00:00:00Z"))
            .build();

    @Test
    void aSuccessfulCaptureCarriesThePersistedItem() {
        KnowledgeCaptureResult result = KnowledgeCaptureResult.success(ITEM);

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.persistedItem()).contains(ITEM);
    }

    @Test
    void aFailedCaptureHasNoPersistedItemButCarriesAReason() {
        KnowledgeCaptureResult result = KnowledgeCaptureResult.failure("vault directory not found");

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.persistedItem()).isEmpty();
        assertThat(result.failureReason()).isEqualTo("vault directory not found");
    }

    @Test
    void rejectsABlankFailureReason() {
        assertThatThrownBy(() -> KnowledgeCaptureResult.failure("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
