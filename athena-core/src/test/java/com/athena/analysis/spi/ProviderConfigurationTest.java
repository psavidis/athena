package com.athena.analysis.spi;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderConfigurationTest {

    @Test
    void anEnabledConfigurationCarriesItsSettings() {
        ProviderConfiguration configuration = ProviderConfiguration.enabled(Map.of("rulesetPath", "/rules.xml"));

        assertThat(configuration.isEnabled()).isTrue();
        assertThat(configuration.settings()).containsEntry("rulesetPath", "/rules.xml");
    }

    @Test
    void aDisabledConfigurationHasNoSettings() {
        ProviderConfiguration configuration = ProviderConfiguration.disabled();

        assertThat(configuration.isEnabled()).isFalse();
        assertThat(configuration.settings()).isEmpty();
    }

    @Test
    void enablingOneProviderDoesNotAffectAnotherConfigurationInstance() {
        ProviderConfiguration sonarJava = ProviderConfiguration.enabled(Map.of());
        ProviderConfiguration eslint = ProviderConfiguration.disabled();

        assertThat(sonarJava.isEnabled()).isTrue();
        assertThat(eslint.isEnabled()).isFalse();
    }
}
