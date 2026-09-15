package com.athena.knowledge;

import com.athena.knowledge.spi.KnowledgeProviderConfiguration;

import java.util.Map;
import java.util.Optional;

/**
 * Resolves the currently-configured Knowledge Provider, if any (ticket
 * #118), from persisted configuration ({@link KnowledgeProviderStore}) —
 * the single place both the review pipeline
 * ({@code com.athena.web.ai.AiAnalysisController}) and the configuration
 * endpoint ({@code com.athena.web.knowledge.KnowledgeConfigController})
 * ask "is a Knowledge Provider configured, and which one?", so the two
 * can never disagree about it.
 *
 * <p>Only one concrete provider (Obsidian) is supported yet; a future
 * provider adds another branch here without changing either caller.
 */
public final class KnowledgeProviderResolver {

    private final KnowledgeProviderStore store;

    public KnowledgeProviderResolver() {
        this(new KnowledgeProviderStore());
    }

    public KnowledgeProviderResolver(KnowledgeProviderStore store) {
        this.store = store;
    }

    public Optional<ConfiguredKnowledgeProvider> resolve() {
        return store.loadObsidianConfig()
                .filter(ObsidianVaultConfig::enabled)
                .map(config -> new ConfiguredKnowledgeProvider(new ObsidianKnowledgeProvider(),
                        KnowledgeProviderConfiguration.enabled(Map.of(ObsidianKnowledgeProvider.VAULT_PATH_SETTING, config.vaultPath())),
                        config.vaultPath()));
    }
}
