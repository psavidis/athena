package com.athena.knowledge;

import com.athena.knowledge.spi.KnowledgeItem;
import com.athena.knowledge.spi.KnowledgeProvider;
import com.athena.knowledge.spi.KnowledgeProviderConfiguration;
import com.athena.knowledge.spi.KnowledgeQuery;
import com.athena.knowledge.spi.KnowledgeRetrievalResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeRetrieverTest {

    private static final KnowledgeQuery QUERY = KnowledgeQuery.of("acme/checkout", List.of(), List.of("payment"));

    @Test
    void anEmptyProviderMapRetrievesNothingWithNoError() {
        KnowledgeRetriever retriever = KnowledgeRetriever.none();

        assertThat(retriever.retrieve(QUERY)).isEmpty();
    }

    @Test
    void aggregatesItemsFromEveryEnabledProvider() {
        KnowledgeItem fromFirst = item("first");
        KnowledgeItem fromSecond = item("second");
        KnowledgeProvider first = new FakeProvider(KnowledgeRetrievalResult.success(List.of(fromFirst)));
        KnowledgeProvider second = new FakeProvider(KnowledgeRetrievalResult.success(List.of(fromSecond)));
        KnowledgeRetriever retriever = new KnowledgeRetriever(Map.of(
                first, KnowledgeProviderConfiguration.enabled(Map.of()),
                second, KnowledgeProviderConfiguration.enabled(Map.of())));

        assertThat(retriever.retrieve(QUERY)).containsExactlyInAnyOrder(fromFirst, fromSecond);
    }

    @Test
    void skipsADisabledProviderWithoutCallingIt() {
        KnowledgeProvider disabled = new FakeProvider(KnowledgeRetrievalResult.failure("must not be called"));
        KnowledgeRetriever retriever = new KnowledgeRetriever(Map.of(disabled, KnowledgeProviderConfiguration.disabled()));

        assertThat(retriever.retrieve(QUERY)).isEmpty();
    }

    @Test
    void aFailedProviderYieldsNoItemsButDoesNotPreventOthers() {
        KnowledgeItem fromWorking = item("working");
        KnowledgeProvider failing = new FakeProvider(KnowledgeRetrievalResult.failure("vault unreachable"));
        KnowledgeProvider working = new FakeProvider(KnowledgeRetrievalResult.success(List.of(fromWorking)));
        KnowledgeRetriever retriever = new KnowledgeRetriever(Map.of(
                failing, KnowledgeProviderConfiguration.enabled(Map.of()),
                working, KnowledgeProviderConfiguration.enabled(Map.of())));

        assertThat(retriever.retrieve(QUERY)).containsExactly(fromWorking);
    }

    @Test
    void aProviderThatThrowsIsIsolatedFromOthers() {
        KnowledgeItem fromWorking = item("working");
        KnowledgeProvider throwing = new ThrowingProvider();
        KnowledgeProvider working = new FakeProvider(KnowledgeRetrievalResult.success(List.of(fromWorking)));
        KnowledgeRetriever retriever = new KnowledgeRetriever(Map.of(
                throwing, KnowledgeProviderConfiguration.enabled(Map.of()),
                working, KnowledgeProviderConfiguration.enabled(Map.of())));

        assertThat(retriever.retrieve(QUERY)).containsExactly(fromWorking);
    }

    private static KnowledgeItem item(String title) {
        return KnowledgeItem.builder("fake", title, "content", title + ".md", Instant.parse("2026-01-01T00:00:00Z")).build();
    }

    private static final class FakeProvider implements KnowledgeProvider {
        private final KnowledgeRetrievalResult result;

        FakeProvider(KnowledgeRetrievalResult result) {
            this.result = result;
        }

        @Override
        public String providerId() {
            return "fake";
        }

        @Override
        public KnowledgeRetrievalResult retrieveRelevant(KnowledgeQuery query, KnowledgeProviderConfiguration configuration) {
            return result;
        }

        @Override
        public com.athena.knowledge.spi.KnowledgeCaptureResult captureCandidate(
                com.athena.knowledge.spi.KnowledgeCandidate candidate, KnowledgeProviderConfiguration configuration) {
            throw new UnsupportedOperationException();
        }
    }

    /** A misbehaving provider that throws instead of returning a failed result — {@link KnowledgeRetriever} must isolate this too. */
    private static final class ThrowingProvider implements KnowledgeProvider {
        @Override
        public String providerId() {
            return "throwing";
        }

        @Override
        public KnowledgeRetrievalResult retrieveRelevant(KnowledgeQuery query, KnowledgeProviderConfiguration configuration) {
            throw new IllegalStateException("boom");
        }

        @Override
        public com.athena.knowledge.spi.KnowledgeCaptureResult captureCandidate(
                com.athena.knowledge.spi.KnowledgeCandidate candidate, KnowledgeProviderConfiguration configuration) {
            throw new UnsupportedOperationException();
        }
    }
}
