package com.athena.plugins;

import com.athena.semantic.PrAnalyzer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes {@link PluginRegistry}'s {@link java.util.ServiceLoader} discovery
 * as a Spring-managed {@link PrAnalyzer} bean, computed once at startup —
 * the SPI itself carries no Spring dependency (see {@code athena-core}); this
 * is the one place Spring and {@code ServiceLoader} meet.
 */
@Configuration
public class PluginConfiguration {

    @Bean
    public PrAnalyzer prAnalyzer() {
        return new PrAnalyzer(PluginRegistry.languagePlugins(), PluginRegistry.frameworkPlugins());
    }
}
