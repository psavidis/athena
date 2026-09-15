package com.athena.knowledge.spi;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeItemTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void carriesItsProvenanceFields() {
        KnowledgeItem item = KnowledgeItem.builder("obsidian", "Payment Ownership", "payment-service owns payment state.",
                        "notes/payment-ownership.md", CREATED_AT)
                .repositoryContext("acme/checkout")
                .metadata(Map.of("author", "jane"))
                .build();

        assertThat(item.providerId()).isEqualTo("obsidian");
        assertThat(item.title()).isEqualTo("Payment Ownership");
        assertThat(item.content()).isEqualTo("payment-service owns payment state.");
        assertThat(item.source()).isEqualTo("notes/payment-ownership.md");
        assertThat(item.createdAt()).isEqualTo(CREATED_AT);
        assertThat(item.repositoryContext()).isEqualTo("acme/checkout");
        assertThat(item.metadata()).containsEntry("author", "jane");
    }

    @Test
    void defaultsRepositoryContextAndMetadataWhenNotGiven() {
        KnowledgeItem item = KnowledgeItem.builder("obsidian", "Title", "content", "source.md", CREATED_AT).build();

        assertThat(item.repositoryContext()).isEmpty();
        assertThat(item.metadata()).isEmpty();
    }

    @Test
    void rejectsABlankTitle() {
        assertThatThrownBy(() -> KnowledgeItem.builder("obsidian", "  ", "content", "source.md", CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void twoItemsWithTheSameFieldsAreEqual() {
        KnowledgeItem first = KnowledgeItem.builder("obsidian", "Title", "content", "source.md", CREATED_AT).build();
        KnowledgeItem second = KnowledgeItem.builder("obsidian", "Title", "content", "source.md", CREATED_AT).build();

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
    }
}
