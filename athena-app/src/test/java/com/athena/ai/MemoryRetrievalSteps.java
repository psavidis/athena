package com.athena.ai;

import com.athena.git.TempDirectories;
import com.athena.memory.MemoryEntry;
import com.athena.memory.ProjectMemoryStore;
import com.athena.memory.RelevantMemoryRetriever;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Steps for the Project Memory in Review Context feature (ticket #173):
 * exercises the real production pipeline — {@link com.athena.memory.RelevantMemoryRetriever}
 * filtering a real {@link ProjectMemoryStore}, {@link AiAnalysisOrchestrator} folding the
 * result into the {@link com.athena.reviewcontext.ReviewContext} sent to the AI provider —
 * with only the network call to the real AI itself faked ({@link FakeAiProvider}, the
 * established external-boundary double). Mirrors {@link KnowledgeRetrievalSteps}' own
 * shape for ticket #118.
 */
public class MemoryRetrievalSteps {

    private static final String PR_TITLE = "Move retry handling";

    private final ReviewStateStore reviewStateStore = new ReviewStateStore();
    private final AnnotationBoard board = new AnnotationBoard();
    private final FakeAiProvider aiProvider = new FakeAiProvider();

    private Path baseRoot;
    private Path headRoot;
    private Path projectRoot;
    private ProjectMemoryStore memoryStore;
    private List<Change> changes;

    @Before
    public void createTempRoots() throws IOException {
        baseRoot = Files.createTempDirectory("athena-memory-retrieval-base-");
        headRoot = Files.createTempDirectory("athena-memory-retrieval-head-");
        projectRoot = Files.createTempDirectory("athena-memory-retrieval-project-");
        memoryStore = new ProjectMemoryStore(projectRoot);
    }

    @After
    public void cleanUpTempRoots() {
        TempDirectories.deleteRecursively(baseRoot);
        TempDirectories.deleteRecursively(headRoot);
        TempDirectories.deleteRecursively(projectRoot);
    }

    @Given("a project whose memory contains the fact {string}")
    public void a_project_whose_memory_contains_the_fact(String fact) {
        memoryStore.record(new MemoryEntry(fact, "unspecified", "unspecified", false));
    }

    @Given("a project whose memory is empty")
    public void a_project_whose_memory_is_empty() {
        // No fixtures registered: a fresh store already has no recorded memory.
    }

    @Given("the reviewer has assembled a Review Context for a PR that changes a file named {string}")
    public void the_reviewer_has_assembled_a_review_context_for_a_pr_that_changes_a_file_named(String fileName) {
        String className = fileName.endsWith(".java") ? fileName.substring(0, fileName.length() - 5) : fileName;
        JavaFixtureSupport.write(baseRoot, className, "public class " + className + " {\n"
                + "    public void process() {\n"
                + "    }\n"
                + "}\n");
        JavaFixtureSupport.write(headRoot, className, "public class " + className + " {\n"
                + "    public void handle() {\n"
                + "    }\n"
                + "}\n");
        changes = TestChanges.detect(baseRoot, headRoot);
        assertThat(changes).isNotEmpty();
    }

    @When("the reviewer triggers AI analysis for the memory-aware review")
    public void the_reviewer_triggers_ai_analysis_for_the_memory_aware_review() {
        List<String> changedFiles = changes.stream()
                .flatMap(change -> change.matchedOccurrences().stream())
                .flatMap(occurrence -> occurrence.filesTouched().stream())
                .distinct()
                .toList();
        List<MemoryEntry> relevantMemory = RelevantMemoryRetriever.retrieve(memoryStore, changedFiles);

        AiAnalysisOrchestrator.trigger(PR_TITLE, changes, reviewStateStore, board, aiProvider, List.of(), relevantMemory);
    }

    @Then("the request sent to the AI provider includes the fact {string}")
    public void the_request_includes_the_fact(String fact) {
        assertThat(aiProvider.lastAnalyzedReviewContext().memoryEntries())
                .extracting(MemoryEntry::fact)
                .contains(fact);
    }

    @Then("the request sent to the AI provider does not include the fact {string}")
    public void the_request_does_not_include_the_fact(String fact) {
        assertThat(aiProvider.lastAnalyzedReviewContext().memoryEntries())
                .extracting(MemoryEntry::fact)
                .doesNotContain(fact);
    }

    @Then("the request sent to the AI provider includes no project memory")
    public void the_request_includes_no_project_memory() {
        assertThat(aiProvider.lastAnalyzedReviewContext().memoryEntries()).isEmpty();
    }

    @Then("the memory-aware AI analysis completes normally")
    public void the_memory_aware_ai_analysis_completes_normally() {
        assertThat(aiProvider.lastAnalyzedReviewContext()).isNotNull();
    }

    @Then("the request sent to the AI provider states that project memory is a historical pattern, not a hard rule")
    public void the_request_states_project_memory_is_a_historical_pattern() {
        String prompt = ClaudeAnalysisPrompt.build(aiProvider.lastAnalyzedReviewContext());
        assertThat(prompt).contains("historical pattern, not a hard rule");
    }
}
