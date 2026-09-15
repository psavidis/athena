package com.athena.knowledge.spi;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeQueryTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void anItemMentioningAChangedFilesNameIsRelevant() {
        KnowledgeQuery query = KnowledgeQuery.of("acme/checkout",
                List.of("src/main/java/com/acme/paymentservice/PaymentProcessor.java"), List.of());
        KnowledgeItem item = note("Payment Service Ownership", "payment-service owns payment state.");

        assertThat(query.isRelevantTo(item)).isTrue();
    }

    @Test
    void anItemMentioningAnExplicitTermIsRelevant() {
        KnowledgeQuery query = KnowledgeQuery.of("acme/checkout", List.of(), List.of("retry-configuration"));
        KnowledgeItem item = note("Retry Behavior", "retry-configuration is intentionally overridden per environment.");

        assertThat(query.isRelevantTo(item)).isTrue();
    }

    @Test
    void anUnrelatedItemIsNotRelevant() {
        KnowledgeQuery query = KnowledgeQuery.of("acme/checkout",
                List.of("src/main/java/com/acme/paymentservice/PaymentProcessor.java"), List.of());
        KnowledgeItem item = note("Unrelated Notes", "billing-export runs nightly.");

        assertThat(query.isRelevantTo(item)).isFalse();
    }

    @Test
    void aQueryWithNoChangedFilesOrTermsMatchesNothing() {
        KnowledgeQuery query = KnowledgeQuery.of("acme/checkout", List.of(), List.of());
        KnowledgeItem item = note("Anything", "some content");

        assertThat(query.isRelevantTo(item)).isFalse();
    }

    @Test
    void matchingIsCaseInsensitive() {
        KnowledgeQuery query = KnowledgeQuery.of("acme/checkout", List.of(), List.of("PaymentService"));
        KnowledgeItem item = note("Ownership", "paymentservice owns payment state.");

        assertThat(query.isRelevantTo(item)).isTrue();
    }

    private static KnowledgeItem note(String title, String content) {
        return KnowledgeItem.builder("obsidian", title, content, title + ".md", CREATED_AT).build();
    }
}
