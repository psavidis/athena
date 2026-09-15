package com.athena.knowledge;

import com.athena.knowledge.spi.KnowledgeProvider;
import com.athena.knowledge.spi.KnowledgeProviderConfiguration;

import java.util.Map;

/**
 * The one currently-configured {@link KnowledgeProvider} (ticket #118),
 * resolved by {@link KnowledgeProviderResolver} — bundles the provider
 * instance with its resolved configuration, plus the vault path for
 * display, so a caller doesn't need to re-derive settings a second time.
 */
public record ConfiguredKnowledgeProvider(KnowledgeProvider provider, KnowledgeProviderConfiguration configuration,
                                           String vaultPath) {

    /** This provider as the single-entry map {@link com.athena.knowledge.KnowledgeRetriever} expects. */
    public Map<KnowledgeProvider, KnowledgeProviderConfiguration> asProviderMap() {
        return Map.of(provider, configuration);
    }
}
