package com.athena.knowledge;

import com.athena.git.TempDirectories;
import com.athena.knowledge.spi.KnowledgeCandidate;
import com.athena.knowledge.spi.KnowledgeCaptureResult;
import com.athena.knowledge.spi.KnowledgeItem;
import com.athena.knowledge.spi.KnowledgeProviderConfiguration;
import com.athena.knowledge.spi.KnowledgeQuery;
import com.athena.knowledge.spi.KnowledgeRetrievalResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dedicated unit tests for {@link ObsidianKnowledgeProvider} (ticket #118) — the Cucumber
 * scenarios in {@code KnowledgeRetrievalSteps}/{@code KnowledgeCandidateCaptureSteps} exercise
 * this class end to end through the review pipeline; these tests exercise its own public API
 * directly, per qa-ticket's rule that Gherkin coverage never excuses skipping a unit test for
 * the class that implements a use case.
 */
class ObsidianKnowledgeProviderTest {

    private final ObsidianKnowledgeProvider provider = new ObsidianKnowledgeProvider();
    private Path vault;

    @BeforeEach
    void createVault() throws IOException {
        vault = Files.createTempDirectory("athena-obsidian-provider-test-");
    }

    @AfterEach
    void cleanUpVault() {
        TempDirectories.deleteRecursively(vault);
    }

    @Test
    void retrievalFailsWhenNoVaultPathIsConfigured() {
        KnowledgeRetrievalResult result = provider.retrieveRelevant(query(), KnowledgeProviderConfiguration.enabled(Map.of()));

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.failureReason()).contains("not configured");
    }

    @Test
    void retrievalFailsWhenTheConfiguredVaultDirectoryDoesNotExist() {
        Path missing = vault.resolve("does-not-exist");

        KnowledgeRetrievalResult result = provider.retrieveRelevant(query(), configurationFor(missing));

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.failureReason()).contains("not found");
    }

    @Test
    void retrievalReturnsOnlyNotesRelevantToTheQuery() throws IOException {
        writeNote("Payment Service Ownership", "payment-service owns payment state.");
        writeNote("Unrelated Notes", "billing-export runs nightly.");

        KnowledgeRetrievalResult result = provider.retrieveRelevant(
                KnowledgeQuery.of("acme/checkout", List.of(), List.of("payment-service")), configurationFor(vault));

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.items()).extracting(KnowledgeItem::title).containsExactly("Payment Service Ownership");
    }

    @Test
    void aRetrievedItemCarriesItsProvenance() throws IOException {
        writeNote("Payment Service Ownership", "payment-service owns payment state.");

        KnowledgeRetrievalResult result = provider.retrieveRelevant(
                KnowledgeQuery.of("acme/checkout", List.of(), List.of("payment-service")), configurationFor(vault));

        KnowledgeItem item = result.items().get(0);
        assertThat(item.providerId()).isEqualTo("obsidian");
        assertThat(item.source()).isEqualTo("Payment Service Ownership.md");
        assertThat(item.repositoryContext()).isEqualTo("acme/checkout");
        assertThat(item.content()).contains("payment-service owns payment state.");
    }

    @Test
    void captureFailsWhenNoVaultPathIsConfigured() {
        KnowledgeCaptureResult result = provider.captureCandidate(candidate(), KnowledgeProviderConfiguration.enabled(Map.of()));

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.failureReason()).contains("not configured");
    }

    @Test
    void captureFailsWhenTheConfiguredVaultDirectoryDoesNotExist() {
        KnowledgeCaptureResult result = provider.captureCandidate(candidate(), configurationFor(vault.resolve("missing")));

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.failureReason()).contains("not found");
    }

    @Test
    void captureWritesANewNoteUnderTheAthenaKnowledgeFolderAndReturnsItsProvenance() {
        KnowledgeCaptureResult result = provider.captureCandidate(candidate(), configurationFor(vault));

        assertThat(result.isSuccessful()).isTrue();
        KnowledgeItem persisted = result.persistedItem().orElseThrow();
        assertThat(persisted.providerId()).isEqualTo("obsidian");
        assertThat(persisted.repositoryContext()).isEqualTo("acme/checkout");
        assertThat(persisted.content()).isEqualTo("Retry configuration intentionally overridden per environment");
        assertThat(persisted.source()).startsWith("Athena Knowledge/");
        assertThat(Files.isRegularFile(vault.resolve(persisted.source()))).isTrue();
    }

    @Test
    void captureNeverOverwritesOrRemovesAnExistingNote() throws IOException {
        writeNote("Existing Note", "human-authored content that must survive.");

        provider.captureCandidate(candidate(), configurationFor(vault));

        assertThat(Files.readString(vault.resolve("Existing Note.md"))).contains("human-authored content that must survive.");
    }

    @Test
    void aCapturedCandidateBecomesRetrievableInALaterQuery() {
        KnowledgeCaptureResult captureResult = provider.captureCandidate(candidate(), configurationFor(vault));
        assertThat(captureResult.isSuccessful()).isTrue();

        KnowledgeRetrievalResult retrievalResult = provider.retrieveRelevant(
                KnowledgeQuery.of("acme/checkout", List.of(), List.of("retry configuration")), configurationFor(vault));

        assertThat(retrievalResult.items())
                .extracting(KnowledgeItem::content)
                .anyMatch(content -> content.contains("Retry configuration intentionally overridden per environment"));
    }

    private void writeNote(String title, String content) throws IOException {
        Files.writeString(vault.resolve(title + ".md"), "# " + title + "\n\n" + content + "\n");
    }

    private KnowledgeProviderConfiguration configurationFor(Path vaultPath) {
        return KnowledgeProviderConfiguration.enabled(Map.of(ObsidianKnowledgeProvider.VAULT_PATH_SETTING, vaultPath.toString()));
    }

    private KnowledgeQuery query() {
        return KnowledgeQuery.of("acme/checkout", List.of(), List.of("payment"));
    }

    private KnowledgeCandidate candidate() {
        return KnowledgeCandidate.withRationale("acme/checkout", "Retry configuration intentionally overridden per environment",
                "Accepted AI finding", Instant.parse("2026-01-01T00:00:00Z"));
    }
}
