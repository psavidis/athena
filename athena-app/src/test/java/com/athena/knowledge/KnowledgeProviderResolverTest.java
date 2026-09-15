package com.athena.knowledge;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dedicated unit tests for {@link KnowledgeProviderResolver} (ticket #118) — its
 * enabled/disabled resolution branching, independent of the controller/web-layer glue that
 * consumes it.
 */
class KnowledgeProviderResolverTest {

    private Path storeFile;
    private KnowledgeProviderStore store;
    private KnowledgeProviderResolver resolver;

    @BeforeEach
    void createStore() throws IOException {
        storeFile = Files.createTempFile("athena-knowledge-resolver-test-", ".json");
        Files.deleteIfExists(storeFile);
        store = new KnowledgeProviderStore(storeFile);
        resolver = new KnowledgeProviderResolver(store);
    }

    @AfterEach
    void cleanUpStore() throws IOException {
        Files.deleteIfExists(storeFile);
    }

    @Test
    void resolvesToEmptyWhenNothingHasBeenConfigured() {
        assertThat(resolver.resolve()).isEmpty();
    }

    @Test
    void resolvesToObsidianWhenAnEnabledVaultIsConfigured() {
        store.saveObsidianConfig(new ObsidianVaultConfig("/vault/path", true));

        ConfiguredKnowledgeProvider configured = resolver.resolve().orElseThrow();

        assertThat(configured.provider().providerId()).isEqualTo("obsidian");
        assertThat(configured.vaultPath()).isEqualTo("/vault/path");
        assertThat(configured.configuration().isEnabled()).isTrue();
        assertThat(configured.configuration().settings())
                .containsEntry(ObsidianKnowledgeProvider.VAULT_PATH_SETTING, "/vault/path");
    }

    @Test
    void resolvesToEmptyWhenTheConfiguredVaultIsDisabled() {
        store.saveObsidianConfig(new ObsidianVaultConfig("/vault/path", false));

        assertThat(resolver.resolve()).isEmpty();
    }

    @Test
    void resolvesToEmptyAgainAfterTheConfigurationIsCleared() {
        store.saveObsidianConfig(new ObsidianVaultConfig("/vault/path", true));
        store.clearObsidianConfig();

        assertThat(resolver.resolve()).isEmpty();
    }
}
