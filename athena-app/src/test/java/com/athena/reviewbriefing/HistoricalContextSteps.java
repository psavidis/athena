package com.athena.reviewbriefing;

import com.athena.contextrewind.ContextRewindService;
import com.athena.git.TempDirectories;
import com.athena.github.FakeGitHubTransport;
import com.athena.github.GitHubRepositoryProvider;
import com.athena.knowledge.ObsidianKnowledgeProvider;
import com.athena.knowledge.spi.KnowledgeProvider;
import com.athena.knowledge.spi.KnowledgeProviderConfiguration;
import com.athena.memory.ProjectMemoryStore;
import com.athena.repository.RepositoryProvider;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for Review Briefing historical and Knowledge Base
 * context (ticket #222). Detroit-school, matching {@code
 * ContextRewindServiceTest}'s own established fixture pattern: a real
 * {@link ContextRewindService} over a real {@link GitHubRepositoryProvider}
 * (against the {@link FakeGitHubTransport} network-boundary fake), a real
 * {@link ProjectMemoryStore}, and a real {@link ObsidianKnowledgeProvider}
 * reading an actual temp vault when a scenario needs one.
 */
public class HistoricalContextSteps {

    private static final String TOKEN = "test-token";
    private static final String REPOSITORY = "acme/widgets";

    private final FakeGitHubTransport transport = new FakeGitHubTransport();
    private final RepositoryProvider repositoryProvider = new GitHubRepositoryProvider(TOKEN, transport);

    private Path projectRoot;
    private Path vault;
    private ProjectMemoryStore memoryStore;
    private final List<String> focusAreaEntityNames = new ArrayList<>();
    private HistoricalContext generatedResult;

    @Before
    public void createProject() throws IOException {
        transport.acceptToken(TOKEN, "octocat");
        projectRoot = Files.createTempDirectory("athena-historical-context-steps-");
        memoryStore = new ProjectMemoryStore(projectRoot);
    }

    @After
    public void cleanUp() {
        TempDirectories.deleteRecursively(projectRoot);
        if (vault != null) {
            TempDirectories.deleteRecursively(vault);
        }
    }

    @Given("a focus-area entity {string} touched by a prior Pull Request")
    public void a_focus_area_entity_touched_by_a_prior_pull_request(String entityName) {
        transport.addClosedPullRequest(REPOSITORY, 217, "Add retry handling", true);
        transport.addPullRequestDetail(REPOSITORY, 217, "Add retry handling", "octocat", "main", "feature");
        transport.addChangedFile(REPOSITORY, 217, entityName + ".java", "modified", null);
        focusAreaEntityNames.add(entityName);
    }

    @Given("a focus-area entity {string} with a Knowledge Base entry")
    public void a_focus_area_entity_with_a_knowledge_base_entry(String entityName) throws IOException {
        vault = Files.createTempDirectory("athena-historical-context-steps-vault-");
        Files.writeString(vault.resolve("Ownership.md"), "# Ownership\n\n" + entityName + " is owned by the payments team.\n");
        focusAreaEntityNames.add(entityName);
    }

    @Given("a focus-area entity {string} with no history or knowledge")
    public void a_focus_area_entity_with_no_history_or_knowledge(String entityName) {
        focusAreaEntityNames.add(entityName);
    }

    @Given("no focus-area entities")
    public void no_focus_area_entities() {
        // no-op: focusAreaEntityNames starts empty
    }

    @When("Athena generates the Review Briefing's historical and Knowledge Base context")
    public void athena_generates_the_review_briefings_historical_and_knowledge_base_context() {
        Map<KnowledgeProvider, KnowledgeProviderConfiguration> knowledgeProviders = vault == null
                ? Map.of()
                : Map.of(new ObsidianKnowledgeProvider(), KnowledgeProviderConfiguration.enabled(
                        Map.of(ObsidianKnowledgeProvider.VAULT_PATH_SETTING, vault.toString())));
        ContextRewindService contextRewindService =
                new ContextRewindService(repositoryProvider, memoryStore, knowledgeProviders, Optional.empty());
        HistoricalContextGenerator generator = new HistoricalContextGenerator(contextRewindService);
        generatedResult = generator.generate(focusAreaEntityNames, projectRoot, REPOSITORY);
    }

    @Then("the generated historical context is present for {string}")
    public void the_generated_historical_context_is_present_for(String entityName) {
        assertThat(generatedResult.historicalContext()).isNotEmpty();
        assertThat(generatedResult.historicalContext())
                .anySatisfy(item -> assertThat(item.entityReference()).contains(entityName));
    }

    @Then("the generated relevant knowledge is present for {string}")
    public void the_generated_relevant_knowledge_is_present_for(String entityName) {
        assertThat(generatedResult.relevantKnowledge()).isNotEmpty();
        assertThat(generatedResult.relevantKnowledge())
                .anySatisfy(item -> assertThat(item.entityReference()).contains(entityName));
    }

    @Then("the generated historical context is empty")
    public void the_generated_historical_context_is_empty() {
        assertThat(generatedResult.historicalContext()).isEmpty();
    }

    @Then("the generated relevant knowledge is empty")
    public void the_generated_relevant_knowledge_is_empty() {
        assertThat(generatedResult.relevantKnowledge()).isEmpty();
    }
}
