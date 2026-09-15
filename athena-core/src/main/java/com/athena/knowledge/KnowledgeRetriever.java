package com.athena.knowledge;

import com.athena.knowledge.spi.KnowledgeItem;
import com.athena.knowledge.spi.KnowledgeProvider;
import com.athena.knowledge.spi.KnowledgeProviderConfiguration;
import com.athena.knowledge.spi.KnowledgeQuery;
import com.athena.knowledge.spi.KnowledgeRetrievalResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Runs every configured, enabled {@link KnowledgeProvider} for one
 * {@link KnowledgeQuery} (ticket #118), aggregating their relevant
 * {@link KnowledgeItem}s — mirrors {@code com.athena.semantic.PrAnalyzer}'s
 * external-provider loop. An empty provider map (no Knowledge Provider
 * configured) is the normal, fully-supported case: {@link #retrieve}
 * returns an empty list immediately, with no error. One provider's
 * failure — a failed {@link KnowledgeRetrievalResult}, or a misbehaving
 * implementation that throws anyway — is isolated from the rest: it never
 * prevents another provider's items, or this method's own completion,
 * from reaching the caller.
 */
public final class KnowledgeRetriever {

    private final Map<KnowledgeProvider, KnowledgeProviderConfiguration> providers;

    public KnowledgeRetriever(Map<KnowledgeProvider, KnowledgeProviderConfiguration> providers) {
        this.providers = Map.copyOf(providers);
    }

    /** No Knowledge Provider configured — {@link #retrieve} always returns an empty list. */
    public static KnowledgeRetriever none() {
        return new KnowledgeRetriever(Map.of());
    }

    public List<KnowledgeItem> retrieve(KnowledgeQuery query) {
        if (providers.isEmpty()) {
            return List.of();
        }
        List<KnowledgeItem> items = new ArrayList<>();
        for (Map.Entry<KnowledgeProvider, KnowledgeProviderConfiguration> entry : providers.entrySet()) {
            if (!entry.getValue().isEnabled()) {
                continue;
            }
            KnowledgeRetrievalResult result = runProvider(entry.getKey(), query, entry.getValue());
            if (result.isSuccessful()) {
                items.addAll(result.items());
            }
        }
        return List.copyOf(items);
    }

    /** Isolates one provider's own thrown exception into a failed result, matching the
     * failure-handling contract {@link KnowledgeProvider#retrieveRelevant} documents. */
    private KnowledgeRetrievalResult runProvider(KnowledgeProvider provider, KnowledgeQuery query,
                                                  KnowledgeProviderConfiguration configuration) {
        try {
            return provider.retrieveRelevant(query, configuration);
        } catch (RuntimeException e) {
            return KnowledgeRetrievalResult.failure(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }
}
