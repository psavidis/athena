package com.athena.knowledge.spi;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeRetrievalResultTest {

    private static final KnowledgeItem ITEM = KnowledgeItem
            .builder("obsidian", "Title", "content", "source.md", Instant.parse("2026-01-01T00:00:00Z"))
            .build();

    @Test
    void aSuccessfulRetrievalCarriesItsItems() {
        KnowledgeRetrievalResult result = KnowledgeRetrievalResult.success(List.of(ITEM));

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.items()).containsExactly(ITEM);
    }

    @Test
    void aSuccessfulRetrievalMayHaveNoItems() {
        KnowledgeRetrievalResult result = KnowledgeRetrievalResult.success(List.of());

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.items()).isEmpty();
    }

    @Test
    void aFailedRetrievalHasNoItemsButCarriesAReason() {
        KnowledgeRetrievalResult result = KnowledgeRetrievalResult.failure("vault directory not found");

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.items()).isEmpty();
        assertThat(result.failureReason()).isEqualTo("vault directory not found");
    }

    @Test
    void rejectsABlankFailureReason() {
        assertThatThrownBy(() -> KnowledgeRetrievalResult.failure("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
