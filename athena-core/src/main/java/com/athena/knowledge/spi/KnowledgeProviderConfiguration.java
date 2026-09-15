package com.athena.knowledge.spi;

import java.util.Map;
import java.util.Objects;

/**
 * Whether a {@link KnowledgeProvider} is enabled, plus its own settings
 * (ticket #118) — mirrors {@code com.athena.analysis.spi.ProviderConfiguration}'s
 * role for {@code ExternalAnalysisProvider}. {@code settings} is opaque to
 * Athena's core, interpreted only by the provider it belongs to (e.g. an
 * Obsidian vault path).
 */
public final class KnowledgeProviderConfiguration {

    private final boolean enabled;
    private final Map<String, String> settings;

    private KnowledgeProviderConfiguration(boolean enabled, Map<String, String> settings) {
        this.enabled = enabled;
        this.settings = Map.copyOf(settings);
    }

    public static KnowledgeProviderConfiguration enabled(Map<String, String> settings) {
        return new KnowledgeProviderConfiguration(true, settings);
    }

    public static KnowledgeProviderConfiguration disabled() {
        return new KnowledgeProviderConfiguration(false, Map.of());
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Map<String, String> settings() {
        return settings;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof KnowledgeProviderConfiguration other)) return false;
        return enabled == other.enabled && settings.equals(other.settings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, settings);
    }
}
