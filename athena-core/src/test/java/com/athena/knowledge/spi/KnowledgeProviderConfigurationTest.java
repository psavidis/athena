package com.athena.knowledge.spi;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeProviderConfigurationTest {

    @Test
    void anEnabledConfigurationCarriesItsSettings() {
        KnowledgeProviderConfiguration configuration = KnowledgeProviderConfiguration.enabled(Map.of("vaultPath", "/vault"));

        assertThat(configuration.isEnabled()).isTrue();
        assertThat(configuration.settings()).containsEntry("vaultPath", "/vault");
    }

    @Test
    void aDisabledConfigurationHasNoSettings() {
        KnowledgeProviderConfiguration configuration = KnowledgeProviderConfiguration.disabled();

        assertThat(configuration.isEnabled()).isFalse();
        assertThat(configuration.settings()).isEmpty();
    }
}
