package com.athena.knowledge.spi;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the {@link KnowledgeProvider} contract itself via a minimal
 * in-memory fake — proving the interface is actually implementable and
 * usable the way a real provider (Obsidian, and future tickets) would be.
 */
class KnowledgeProviderTest {

    private static final KnowledgeQuery QUERY = KnowledgeQuery.of("acme/checkout", List.of(), List.of("payment"));
    private static final KnowledgeProviderConfiguration CONFIGURATION = KnowledgeProviderConfiguration.enabled(Map.of());

    @Test
    void aProviderReportsItsOwnIdentity() {
        KnowledgeProvider provider = new FakeProvider("obsidian", KnowledgeRetrievalResult.success(List.of()), null);

        assertThat(provider.providerId()).isEqualTo("obsidian");
    }

    @Test
    void aSuccessfulRetrievalReturnsItsItems() {
        KnowledgeItem item = KnowledgeItem.builder("obsidian", "Title", "content", "source.md",
                Instant.parse("2026-01-01T00:00:00Z")).build();
        KnowledgeProvider provider = new FakeProvider("obsidian", KnowledgeRetrievalResult.success(List.of(item)), null);

        KnowledgeRetrievalResult result = provider.retrieveRelevant(QUERY, CONFIGURATION);

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.items()).containsExactly(item);
    }

    @Test
    void aFailedRetrievalNeverThrows() {
        KnowledgeProvider provider = new FakeProvider("obsidian", KnowledgeRetrievalResult.failure("vault unreachable"), null);

        KnowledgeRetrievalResult result = provider.retrieveRelevant(QUERY, CONFIGURATION);

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.failureReason()).isEqualTo("vault unreachable");
    }

    @Test
    void aSuccessfulCaptureReturnsThePersistedItem() {
        KnowledgeItem persisted = KnowledgeItem.builder("obsidian", "Title", "content", "Athena Knowledge/note.md",
                Instant.parse("2026-01-01T00:00:00Z")).build();
        KnowledgeProvider provider = new FakeProvider("obsidian", null, KnowledgeCaptureResult.success(persisted));
        KnowledgeCandidate candidate = KnowledgeCandidate.of("acme/checkout", "content", Instant.parse("2026-01-01T00:00:00Z"));

        KnowledgeCaptureResult result = provider.captureCandidate(candidate, CONFIGURATION);

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.persistedItem()).contains(persisted);
    }

    /** A minimal fake standing in for a real knowledge source (ticket #118's Validation requirement). */
    private static final class FakeProvider implements KnowledgeProvider {
        private final String providerId;
        private final KnowledgeRetrievalResult retrievalResult;
        private final KnowledgeCaptureResult captureResult;

        FakeProvider(String providerId, KnowledgeRetrievalResult retrievalResult, KnowledgeCaptureResult captureResult) {
            this.providerId = providerId;
            this.retrievalResult = retrievalResult;
            this.captureResult = captureResult;
        }

        @Override
        public String providerId() {
            return providerId;
        }

        @Override
        public KnowledgeRetrievalResult retrieveRelevant(KnowledgeQuery query, KnowledgeProviderConfiguration configuration) {
            return retrievalResult;
        }

        @Override
        public KnowledgeCaptureResult captureCandidate(KnowledgeCandidate candidate, KnowledgeProviderConfiguration configuration) {
            return captureResult;
        }
    }
}
