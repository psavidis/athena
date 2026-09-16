package com.athena.contextrewind;

import com.athena.git.TempDirectories;
import com.athena.github.FakeGitHubTransport;
import com.athena.github.GitHubRepositoryProvider;
import com.athena.knowledge.ObsidianKnowledgeProvider;
import com.athena.knowledge.spi.KnowledgeProvider;
import com.athena.knowledge.spi.KnowledgeProviderConfiguration;
import com.athena.memory.MemoryEntry;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Steps for the Context Rewind feature files (ticket #161):
 * context_reconstruction.feature and
 * context_rewind_catch_up_and_narrative.feature. Kept in one class since
 * both exercise the same {@link ContextRewindService} entry point and
 * share the "reconstruct the context for X" step text.
 */
public class ContextRewindSteps {

    private static final String TOKEN = "test-token";
    private static final String REPOSITORY = "acme/widgets";

    private final FakeGitHubTransport transport = new FakeGitHubTransport();
    private final RepositoryProvider repositoryProvider = new GitHubRepositoryProvider(TOKEN, transport);
    private final FakeContextNarrativeProvider narrativeProvider = new FakeContextNarrativeProvider();

    private Path projectRoot;
    private Path vaultDir;
    private ProjectMemoryStore memoryStore;
    private Map<KnowledgeProvider, KnowledgeProviderConfiguration> knowledgeProviders = Map.of();
    private boolean narrativeProviderConfigured;
    private Instant developerLastInteractedAt;
    private ReconstructedContext context;
    private RuntimeException failure;
    private int nextPullRequestNumber = 500;

    @Before
    public void createProject() throws IOException, InterruptedException {
        transport.acceptToken(TOKEN, "octocat");
        projectRoot = Files.createTempDirectory("athena-context-rewind-steps-");
        memoryStore = new ProjectMemoryStore(projectRoot);
        runGit("init", "--quiet");
        runGit("config", "user.email", "test@example.com");
        runGit("config", "user.name", "Test");
    }

    @After
    public void cleanUp() {
        TempDirectories.deleteRecursively(projectRoot);
        if (vaultDir != null) {
            TempDirectories.deleteRecursively(vaultDir);
        }
    }

    // --- context_reconstruction.feature ---

    @Given("a project whose git history and Pull Requests both mention a class {string}")
    public void a_project_whose_git_history_and_pull_requests_both_mention_a_class(String entityName) throws IOException, InterruptedException {
        commitFile(entityName + ".java", "introduce " + entityName, Optional.empty());
        registerMergedPullRequestTouching(nextPullRequestNumber++, entityName);
    }

    @Given("a project whose git history mentions a class {string} in {int} commit(s)")
    public void a_project_whose_git_history_mentions_a_class_in_commits(String entityName, int commitCount) throws IOException, InterruptedException {
        for (int i = 1; i <= commitCount; i++) {
            commitFile(entityName + ".java", "touch " + entityName + " (" + i + ")", Optional.empty());
        }
    }

    @Given("a project whose git history, Pull Requests, and project memory contain nothing about a class {string}")
    public void a_project_whose_history_contains_nothing_about_a_class(String entityName) {
        // No-op: a freshly created project/store/transport already has no history about entityName.
    }

    @Given("that project's memory contains the fact {string} about {string}")
    public void that_projects_memory_contains_the_fact_about(String fact, String entityName) {
        memoryStore.record(new MemoryEntry(fact, "test evidence", "medium", false));
    }

    @Given("an Obsidian vault configured as the Knowledge Provider contains a note about {string}")
    public void an_obsidian_vault_configured_as_the_knowledge_provider_contains_a_note_about(String entityName) throws IOException {
        vaultDir = Files.createTempDirectory("athena-context-rewind-steps-vault-");
        Files.writeString(vaultDir.resolve("Ownership.md"), "# Ownership\n\n" + entityName + " is owned by the payments team.\n");
        KnowledgeProvider provider = new ObsidianKnowledgeProvider();
        KnowledgeProviderConfiguration configuration = KnowledgeProviderConfiguration.enabled(
                Map.of(ObsidianKnowledgeProvider.VAULT_PATH_SETTING, vaultDir.toString()));
        knowledgeProviders = Map.of(provider, configuration);
    }

    // "no Knowledge Provider has been configured" is the shared step from
    // com.athena.knowledge.KnowledgeFixtureSteps — knowledgeProviders already
    // defaults to an empty map here, so nothing further is needed for it.

    @Then("the reconstructed context includes the git commits and Pull Requests that touched {string}")
    public void the_reconstructed_context_includes_the_git_commits_and_pull_requests_that_touched(String entityName) {
        assertThat(context.evolutionTimeline()).isNotEmpty();
        assertThat(context.pullRequestReferences()).isNotEmpty();
    }

    @Then("the reconstructed context includes the git commits that touched {string}")
    public void the_reconstructed_context_includes_the_git_commits_that_touched(String entityName) {
        assertThat(context.evolutionTimeline()).isNotEmpty();
    }

    @Then("the reconstructed context includes the project memory fact about {string}")
    public void the_reconstructed_context_includes_the_project_memory_fact_about(String entityName) {
        assertThat(context.facts()).anyMatch(fact -> fact.description().contains(entityName)
                && fact.description().contains("change together"));
    }

    @Then("the reconstructed context includes the Knowledge Provider note about {string}")
    public void the_reconstructed_context_includes_the_knowledge_provider_note_about(String entityName) {
        assertThat(context.facts()).anyMatch(fact -> fact.source() == ContextSource.KNOWLEDGE_BASE);
    }

    @Then("the git and Pull Request history in the reconstructed context is labeled as historical fact")
    public void the_git_and_pull_request_history_is_labeled_as_historical_fact() {
        assertThat(context.facts())
                .filteredOn(fact -> fact.description().contains("Pull Request") || fact.description().contains("introduce"))
                .extracting(ContextFact::source)
                .containsOnly(ContextSource.HISTORY);
    }

    @Then("the project memory fact in the reconstructed context is labeled as historical fact")
    public void the_project_memory_fact_is_labeled_as_historical_fact() {
        assertThat(context.facts()).anyMatch(fact -> fact.description().contains("change together")
                && fact.source() == ContextSource.HISTORY);
    }

    @Then("the Knowledge Provider note in the reconstructed context is labeled as knowledge-base information, not a project fact")
    public void the_knowledge_provider_note_is_labeled_as_knowledge_base_information() {
        assertThat(context.facts()).anyMatch(fact -> fact.source() == ContextSource.KNOWLEDGE_BASE);
        assertThat(context.facts()).noneMatch(fact -> fact.description().equals("Ownership") && fact.source() == ContextSource.HISTORY);
    }

    @Then("the reconstructed context includes no Knowledge Base information")
    public void the_reconstructed_context_includes_no_knowledge_base_information() {
        assertThat(context.facts()).noneMatch(fact -> fact.source() == ContextSource.KNOWLEDGE_BASE);
    }

    @Then("reconstructing the context does not fail")
    public void reconstructing_the_context_does_not_fail() {
        assertThat(failure).isNull();
    }

    @Then("the reconstructed context reports that insufficient historical information is available for {string}")
    public void the_reconstructed_context_reports_insufficient_historical_information(String entityName) {
        assertThat(context.hasInsufficientHistory()).isTrue();
        assertThat(context.insufficientHistoryMessage())
                .contains("Not enough historical information is available for " + entityName);
    }

    @Then("the reconstructed context includes no AI-generated interpretation")
    public void the_reconstructed_context_includes_no_ai_generated_interpretation() {
        assertThat(context.aiNarrative()).isEmpty();
    }

    // --- context_rewind_catch_up_and_narrative.feature ---

    @Given("a project whose Pull Request {int} added retry handling to a class {string}")
    public void a_project_whose_pull_request_added_retry_handling_to_a_class(int number, String entityName) {
        registerMergedPullRequestTouching(number, entityName);
    }

    @Given("a developer last interacted with a class {string} on {string}")
    public void a_developer_last_interacted_with_a_class_on(String entityName, String date) {
        developerLastInteractedAt = startOfDay(date);
    }

    @Given("a commit on {string} changed a class {string}")
    public void a_commit_on_changed_a_class(String date, String entityName) throws IOException, InterruptedException {
        commitFile(entityName + ".java", "change " + entityName, Optional.of(date));
    }

    @Given("a commit on {string} separately changed {string}")
    public void a_commit_on_separately_changed(String date, String entityName) throws IOException, InterruptedException {
        commitFile(entityName + ".java", "change " + entityName, Optional.of(date));
    }

    @Given("the AI provider will generate a narrative summary of {string}'s history")
    public void the_ai_provider_will_generate_a_narrative_summary_of_history(String entityName) {
        narrativeProviderConfigured = true;
        narrativeProvider.willReturnNarrative(entityName + " grew organically as retry handling was added over time.");
    }

    @When("Athena reconstructs the context for {string}")
    public void athena_reconstructs_the_context_for(String entityName) {
        reconstruct(entityName, Optional.empty());
    }

    @When("Athena reconstructs a {string} context for {string} for that developer")
    public void athena_reconstructs_a_catch_up_context_for_for_that_developer(String mode, String entityName) {
        reconstruct(entityName, Optional.of(developerLastInteractedAt));
    }

    @Then("the reconstructed context references Pull Request {int}")
    public void the_reconstructed_context_references_pull_request(int number) {
        assertThat(context.pullRequestReferences()).extracting(PullRequestReference::number).contains(number);
    }

    @Then("that reference identifies the Pull Request's number and repository so it can be opened directly")
    public void that_reference_identifies_the_pull_requests_number_and_repository() {
        assertThat(context.pullRequestReferences()).isNotEmpty();
        PullRequestReference reference = context.pullRequestReferences().get(0);
        assertThat(reference.repositoryFullName()).isEqualTo(REPOSITORY);
        assertThat(reference.url()).contains(REPOSITORY).contains(String.valueOf(reference.number()));
    }

    @Then("the reconstructed context reports the {string} commit as activity since the developer last interacted with {string}")
    public void the_reconstructed_context_reports_the_commit_as_activity_since(String date, String entityName) {
        assertThat(context.evolutionTimeline())
                .anyMatch(activity -> localDateOf(activity).equals(LocalDate.parse(date)));
    }

    @Then("the reconstructed context does not report the {string} commit")
    public void the_reconstructed_context_does_not_report_the_commit(String date) {
        assertThat(context.evolutionTimeline())
                .noneMatch(activity -> localDateOf(activity).equals(LocalDate.parse(date)));
    }

    @Then("the reconstructed context lists the {string} commit before the {string} commit in {string}'s evolution timeline")
    public void the_reconstructed_context_lists_the_commit_before_the_commit_in_evolution_timeline(
            String earlierDate, String laterDate, String entityName) {
        int earlierIndex = indexOfCommitOn(earlierDate);
        int laterIndex = indexOfCommitOn(laterDate);
        assertThat(earlierIndex).isLessThan(laterIndex);
    }

    @Then("the reconstructed context includes an AI-generated narrative about {string}")
    public void the_reconstructed_context_includes_an_ai_generated_narrative_about(String entityName) {
        assertThat(context.aiNarrative()).isPresent();
        assertThat(narrativeProvider.lastEntityName()).isEqualTo(entityName);
    }

    @Then("that narrative is labeled as an AI-generated interpretation, not a project fact")
    public void that_narrative_is_labeled_as_an_ai_generated_interpretation() {
        assertThat(context.facts()).noneMatch(fact -> fact.source() == ContextSource.AI_INTERPRETATION);
        assertThat(context.aiNarrative()).isPresent();
    }

    // --- shared helpers ---

    private void reconstruct(String entityName, Optional<Instant> since) {
        try {
            ContextRewindService service = new ContextRewindService(repositoryProvider, memoryStore, knowledgeProviders,
                    narrativeProviderConfigured ? Optional.of(narrativeProvider) : Optional.empty());
            ContextRewindRequest request = ContextRewindRequest.of(entityName, projectRoot, REPOSITORY);
            context = service.reconstruct(since.map(request::since).orElse(request));
        } catch (RuntimeException e) {
            failure = e;
        }
    }

    private int indexOfCommitOn(String date) {
        for (int i = 0; i < context.evolutionTimeline().size(); i++) {
            if (localDateOf(context.evolutionTimeline().get(i)).equals(LocalDate.parse(date))) {
                return i;
            }
        }
        throw new AssertionError("No commit on " + date + " found in evolution timeline: " + context.evolutionTimeline());
    }

    private static LocalDate localDateOf(HistoricalActivity activity) {
        return activity.occurredAt().atZone(ZoneOffset.UTC).toLocalDate();
    }

    private static Instant startOfDay(String date) {
        return LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private void registerMergedPullRequestTouching(int number, String entityName) {
        String title = "Touch " + entityName;
        transport.addClosedPullRequest(REPOSITORY, number, title, true);
        transport.addPullRequestDetail(REPOSITORY, number, title, "octocat", "main", "feature");
        transport.addChangedFile(REPOSITORY, number, entityName + ".java", "modified", null);
    }

    private void commitFile(String fileName, String message, Optional<String> isoDate) throws IOException, InterruptedException {
        Path file = projectRoot.resolve(fileName);
        String previousContent = Files.exists(file) ? Files.readString(file) : "";
        Files.writeString(file, previousContent + message + "\n");
        runGit("add", ".");

        ProcessBuilder builder = new ProcessBuilder("git", "commit", "--quiet", "-m", message)
                .directory(projectRoot.toFile());
        isoDate.ifPresent(date -> {
            String commitDate = date + "T10:00:00+00:00";
            builder.environment().put("GIT_AUTHOR_DATE", commitDate);
            builder.environment().put("GIT_COMMITTER_DATE", commitDate);
        });
        Process process = builder.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            String stderr = new String(process.getErrorStream().readAllBytes());
            throw new IllegalStateException("git commit failed: " + stderr);
        }
    }

    private void runGit(String... args) throws IOException, InterruptedException {
        String[] command = new String[args.length + 1];
        command[0] = "git";
        System.arraycopy(args, 0, command, 1, args.length);
        Process process = new ProcessBuilder(command).directory(projectRoot.toFile()).start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            String stderr = new String(process.getErrorStream().readAllBytes());
            throw new IllegalStateException("Command failed: " + String.join(" ", command) + "\n" + stderr);
        }
    }
}
