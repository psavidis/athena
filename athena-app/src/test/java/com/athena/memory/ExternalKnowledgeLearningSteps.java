package com.athena.memory;

import com.athena.git.TempDirectories;
import com.athena.knowledge.KnowledgeFixtureSteps;
import com.athena.knowledge.KnowledgeRetriever;
import com.athena.knowledge.ObsidianKnowledgeProvider;
import com.athena.knowledge.spi.KnowledgeItem;
import com.athena.knowledge.spi.KnowledgeProvider;
import com.athena.knowledge.spi.KnowledgeProviderConfiguration;
import com.athena.knowledge.spi.KnowledgeQuery;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Steps for the External Knowledge Learning feature (ticket #172). */
public class ExternalKnowledgeLearningSteps {

    private static final String REPOSITORY_CONTEXT = "acme/checkout";

    private final KnowledgeFixtureSteps fixture;
    private final List<KnowledgeItem> retrievedItems = new ArrayList<>();

    private Path projectRoot;
    private ProjectMemoryStore store;
    private RuntimeException failure;
    private Map<String, String> vaultSnapshotBeforeLearning;

    public ExternalKnowledgeLearningSteps(KnowledgeFixtureSteps fixture) {
        this.fixture = fixture;
    }

    @Before
    public void createProject() throws IOException {
        projectRoot = Files.createTempDirectory("athena-external-knowledge-learning-steps-");
        store = new ProjectMemoryStore(projectRoot);
    }

    @After
    public void cleanUp() {
        if (projectRoot != null) {
            TempDirectories.deleteRecursively(projectRoot);
        }
    }

    @Given("relevant external knowledge for the term {string} has already been retrieved")
    public void relevant_external_knowledge_for_the_term_has_already_been_retrieved(String term) {
        KnowledgeProvider provider = new ObsidianKnowledgeProvider();
        KnowledgeProviderConfiguration configuration = KnowledgeProviderConfiguration.enabled(
                Map.of(ObsidianKnowledgeProvider.VAULT_PATH_SETTING, fixture.vaultDir().toString()));
        KnowledgeRetriever retriever = new KnowledgeRetriever(Map.of(provider, configuration));
        retrievedItems.addAll(retriever.retrieve(KnowledgeQuery.of(REPOSITORY_CONTEXT, List.of(), List.of(term))));
    }

    @Given("no relevant external knowledge has been retrieved for this project")
    public void no_relevant_external_knowledge_has_been_retrieved_for_this_project() {
        // No fixtures registered: retrievedItems stays empty.
    }

    @Given("Athena has already learned from that project's external knowledge once")
    public void athena_has_already_learned_from_that_projects_external_knowledge_once() {
        ExternalKnowledgeLearner.learn(retrievedItems, store);
    }

    @When("Athena learns from that project's external knowledge")
    public void athena_learns_from_that_projects_external_knowledge() throws IOException {
        vaultSnapshotBeforeLearning = snapshotVault();
        try {
            ExternalKnowledgeLearner.learn(retrievedItems, store);
        } catch (RuntimeException e) {
            failure = e;
        }
    }

    @When("Athena learns from that project's external knowledge again")
    public void athena_learns_from_that_projects_external_knowledge_again() throws IOException {
        athena_learns_from_that_projects_external_knowledge();
    }

    @Then("querying that project's memory returns a fact that mentions {string}")
    public void querying_that_projects_memory_returns_a_fact_that_mentions(String term) {
        assertThat(store.entries()).anyMatch(entry -> entry.fact().contains(term));
    }

    @Then("that fact's evidence names the note {string} from the {string} Knowledge Provider")
    public void that_facts_evidence_names_the_note_from_the_knowledge_provider(String title, String providerId) {
        assertThat(store.entries())
                .anyMatch(entry -> entry.evidence().contains(title) && entry.evidence().contains(providerId));
    }

    @Then("Athena's external-knowledge learning leaves that project's memory empty")
    public void athenas_external_knowledge_learning_leaves_that_projects_memory_empty() {
        assertThat(store.entries()).isEmpty();
    }

    @Then("external-knowledge learning does not fail with an exception")
    public void external_knowledge_learning_does_not_fail_with_an_exception() {
        assertThat(failure).isNull();
    }

    @Then("querying that project's memory returns exactly one fact that mentions {string}")
    public void querying_that_projects_memory_returns_exactly_one_fact_that_mentions(String term) {
        assertThat(store.entries()).filteredOn(entry -> entry.fact().contains(term)).hasSize(1);
    }

    @Then("the vault's notes are left unchanged")
    public void the_vaults_notes_are_left_unchanged() throws IOException {
        assertThat(snapshotVault()).isEqualTo(vaultSnapshotBeforeLearning);
    }

    private Map<String, String> snapshotVault() throws IOException {
        Path vaultDir = fixture.vaultDir();
        if (vaultDir == null) {
            return Map.of();
        }
        Map<String, String> snapshot = new HashMap<>();
        try (var files = Files.list(vaultDir)) {
            for (Path file : files.toList()) {
                snapshot.put(file.getFileName().toString(), Files.readString(file));
            }
        }
        return snapshot;
    }
}
