package com.athena.analysis.spi;

import java.util.Map;
import java.util.Objects;

/**
 * Whether an {@link ExternalAnalysisProvider} is enabled, plus its own
 * settings (ticket #114/#148) — each provider is configured independently,
 * so enabling/disabling or reconfiguring one never affects another.
 * {@code settings} is opaque to Athena's core, interpreted only by the
 * provider it belongs to (e.g. a rule-set path, a severity threshold).
 */
public final class ProviderConfiguration {

    private final boolean enabled;
    private final Map<String, String> settings;

    private ProviderConfiguration(boolean enabled, Map<String, String> settings) {
        this.enabled = enabled;
        this.settings = Map.copyOf(settings);
    }

    public static ProviderConfiguration enabled(Map<String, String> settings) {
        return new ProviderConfiguration(true, settings);
    }

    public static ProviderConfiguration disabled() {
        return new ProviderConfiguration(false, Map.of());
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
        if (!(o instanceof ProviderConfiguration other)) return false;
        return enabled == other.enabled && settings.equals(other.settings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, settings);
    }
}
