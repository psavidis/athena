package com.athena.ai;

import com.athena.git.TempDirectories;
import com.athena.knowledge.KnowledgeFixtureSteps;
import com.athena.knowledge.KnowledgeRetriever;
import com.athena.knowledge.ObsidianKnowledgeProvider;
import com.athena.knowledge.spi.KnowledgeItem;
import com.athena.knowledge.spi.KnowledgeProvider;
import com.athena.knowledge.spi.KnowledgeProviderConfiguration;
import com.athena.knowledge.spi.KnowledgeQuery;
import com.athena.plugins.TestChanges;
import com.athena.reviewui.AnnotationBoard;
import com.athena.reviewui.JavaFixtureSupport;
import com.athena.semantic.Change;
import com.athena.semantic.ReviewStateStore;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Steps for the knowledge-retrieval-during-review feature (ticket #118):
 * exercises the real production pipeline — {@link ObsidianKnowledgeProvider}
 * reading actual files from a temp vault, {@link KnowledgeRetriever}
 * aggregating/isolating providers, {@link AiAnalysisOrchestrator} folding
 * the result into the {@link com.athena.reviewcontext.ReviewContext} sent
 * to the AI provider — with only the network call to the real AI itself
 * faked ({@link FakeAiProvider}, the established external-boundary double).
 * The "Knowledge Provider configured/not configured" precondition itself is
 * {@link KnowledgeFixtureSteps}, shared across every {@code knowledge_*.feature}.
 */
public class KnowledgeRetrievalSteps {

    private static final String REPOSITORY_CONTEXT = "acme/checkout";
    private static final String PR_TITLE = "Move retry handling";

    private final KnowledgeFixtureSteps fixture;
    private final ReviewStateStore store = new ReviewStateStore();
    private final AnnotationBoard board = new AnnotationBoard();
    private final FakeAiProvider aiProvider = new FakeAiProvider();

    private Path baseRoot;
    private Path headRoot;
    private Path deletedVaultDir;
    private List<Change> changes;

    public KnowledgeRetrievalSteps(KnowledgeFixtureSteps fixture) {
        this.fixture = fixture;
    }

    @Before
    public void createTempRoots() throws IOException {
        baseRoot = Files.createTempDirectory("athena-knowledge-retrieval-base-");
        headRoot = Files.createTempDirectory("athena-knowledge-retrieval-head-");
    }

    @After
    public void cleanUpTempRoots() {
        TempDirectories.deleteRecursively(baseRoot);
        TempDirectories.deleteRecursively(headRoot);
        if (deletedVaultDir != null) {
            TempDirectories.deleteRecursively(deletedVaultDir);
        }
    }

    @Given("an Obsidian vault configured as the Knowledge Provider, but the vault directory has since been deleted")
    public void an_obsidian_vault_configured_but_since_deleted() throws IOException {
        deletedVaultDir = Files.createTempDirectory("athena-knowledge-retrieval-deleted-vault-");
        TempDirectories.deleteRecursively(deletedVaultDir);
    }

    @Given("the vault contains a note titled {string} mentioning {string}")
    public void the_vault_contains_a_note_titled_mentioning(String title, String term) throws IOException {
        Files.writeString(fixture.vaultDir().resolve(title + ".md"), "# " + title + "\n\nThis note is about " + term + ".\n");
    }

    @Given("the reviewer has assembled a Review Context for a PR that changes a file under {string}")
    public void the_reviewer_has_assembled_a_review_context_for_a_pr_touching(String folder) {
        JavaFixtureSupport.write(baseRoot, folder + "/PaymentProcessor", "public class PaymentProcessor {\n"
                + "    public void process() {\n"
                + "    }\n"
                + "}\n");
        JavaFixtureSupport.write(headRoot, folder + "/PaymentProcessor", "public class PaymentProcessor {\n"
                + "    public void handle() {\n"
                + "    }\n"
                + "}\n");
        changes = TestChanges.detect(baseRoot, headRoot);
        assertThat(changes).isNotEmpty();
    }

    @When("the reviewer triggers AI analysis for the knowledge-aware review")
    public void the_reviewer_triggers_ai_analysis_for_the_knowledge_aware_review() {
        KnowledgeRetriever retriever = new KnowledgeRetriever(configuredProviders());
        List<String> changedFiles = changes.stream()
                .flatMap(change -> change.matchedOccurrences().stream())
                .flatMap(occurrence -> occurrence.filesTouched().stream())
                .distinct()
                .toList();
        List<KnowledgeItem> knowledgeItems = retriever.retrieve(KnowledgeQuery.of(REPOSITORY_CONTEXT, changedFiles, List.of()));

        AiAnalysisOrchestrator.trigger(PR_TITLE, changes, store, board, aiProvider, knowledgeItems);
    }

    @Then("the request sent to the AI provider includes the note {string}")
    public void the_request_includes_the_note(String title) {
        assertThat(aiProvider.lastAnalyzedReviewContext().knowledgeItems())
                .extracting(KnowledgeItem::title)
                .contains(title);
    }

    @Then("the request sent to the AI provider does not include the note {string}")
    public void the_request_does_not_include_the_note(String title) {
        assertThat(aiProvider.lastAnalyzedReviewContext().knowledgeItems())
                .extracting(KnowledgeItem::title)
                .doesNotContain(title);
    }

    @Then("the request sent to the AI provider includes no project knowledge")
    public void the_request_includes_no_project_knowledge() {
        assertThat(aiProvider.lastAnalyzedReviewContext().knowledgeItems()).isEmpty();
    }

    @Then("the AI analysis completes normally")
    public void the_ai_analysis_completes_normally() {
        assertThat(aiProvider.lastAnalyzedReviewContext()).isNotNull();
    }

    @Then("the request sent to the AI provider states that project knowledge is contextual evidence, not authoritative")
    public void the_request_states_project_knowledge_is_contextual_evidence() {
        String prompt = ClaudeAnalysisPrompt.build(aiProvider.lastAnalyzedReviewContext());
        assertThat(prompt).contains("never as authoritative");
    }

    /** The vault this scenario configured, if any — either via the shared "configured" fixture, or
     * this class's own "configured, but since deleted" precondition (deliberately not shared, since
     * only this feature needs a vault that existed and was then removed). */
    private Map<KnowledgeProvider, KnowledgeProviderConfiguration> configuredProviders() {
        Path vaultDir = deletedVaultDir != null ? deletedVaultDir : fixture.vaultDir();
        if (vaultDir == null) {
            return Map.of();
        }
        KnowledgeProvider provider = new ObsidianKnowledgeProvider();
        KnowledgeProviderConfiguration configuration = KnowledgeProviderConfiguration.enabled(
                Map.of(ObsidianKnowledgeProvider.VAULT_PATH_SETTING, vaultDir.toString()));
        return Map.of(provider, configuration);
    }
}
