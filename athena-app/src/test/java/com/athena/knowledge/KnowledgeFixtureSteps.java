package com.athena.knowledge;

import com.athena.git.TempDirectories;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Shared "Knowledge Provider configured / not configured" precondition
 * steps for the Project Knowledge Base feature files (ticket #118) — every
 * {@code knowledge_*.feature} file needs this same precondition, so it
 * lives in one glue class, constructor-injected into the others, rather
 * than each re-declaring an identical {@code @Given} step, which
 * Cucumber-JVM rejects as a duplicate step definition (mirrors
 * {@code ChangeMapControllerSteps}/{@code AiAnalysisControllerSteps}'
 * own established shared-steps-class convention).
 */
public class KnowledgeFixtureSteps {

    private Path storeFile;
    private Path vaultDir;
    private KnowledgeProviderStore store;

    @Before
    public void createTempStore() throws IOException {
        storeFile = Files.createTempFile("athena-knowledge-fixture-", ".json");
        Files.deleteIfExists(storeFile);
        store = new KnowledgeProviderStore(storeFile);
    }

    @After
    public void cleanUp() throws IOException {
        Files.deleteIfExists(storeFile);
        if (vaultDir != null) {
            TempDirectories.deleteRecursively(vaultDir);
        }
    }

    @Given("no Knowledge Provider has been configured")
    public void no_knowledge_provider_has_been_configured() {
        // no-op: a fresh store has nothing saved
    }

    @Given("an Obsidian vault configured as the Knowledge Provider")
    public void an_obsidian_vault_configured_as_the_knowledge_provider() throws IOException {
        vaultDir = Files.createTempDirectory("athena-knowledge-fixture-vault-");
        store.saveObsidianConfig(new ObsidianVaultConfig(vaultDir.toString(), true));
    }

    public KnowledgeProviderStore store() {
        return store;
    }

    /** The configured vault directory, or {@code null} if no Knowledge Provider is configured. */
    public Path vaultDir() {
        return vaultDir;
    }
}
