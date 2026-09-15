package com.athena.web.ai;

import com.athena.ai.FakeAiProvider;
import com.athena.git.TempDirectories;
import com.athena.knowledge.KnowledgeFixtureSteps;
import com.athena.knowledge.KnowledgeProviderResolver;
import com.athena.plugins.PluginRegistry;
import com.athena.repository.ImportedPullRequest;
import com.athena.reviewcontext.ReviewSubmission;
import com.athena.reviewui.AnnotationBoard;
import com.athena.semantic.PrAnalyzer;
import com.athena.semantic.ReviewStateStore;
import com.athena.web.WebSession;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Steps for the Knowledge candidate capture feature (ticket #118): saving an
 * accepted AI finding as a new knowledge item. */
public class KnowledgeCandidateCaptureSteps {

    private static final String REPOSITORY_FULL_NAME = "acme/checkout";

    private final KnowledgeFixtureSteps fixture;
    private final WebSession session =
            new WebSession(new PrAnalyzer(PluginRegistry.languagePlugins(), PluginRegistry.frameworkPlugins()));
    private final FakeAiProvider aiProvider = new FakeAiProvider();

    private Path baseRoot;
    private Path headRoot;
    private Path workDir;
    private AiAnalysisController controller;
    private String findingDescription;
    private AiAnalysisController.KnowledgeCaptureResponse captureResponse;
    private ResponseStatusException failure;

    public KnowledgeCandidateCaptureSteps(KnowledgeFixtureSteps fixture) {
        this.fixture = fixture;
    }

    @Before
    public void setUp() throws IOException {
        baseRoot = Files.createTempDirectory("athena-knowledge-capture-base-");
        headRoot = Files.createTempDirectory("athena-knowledge-capture-head-");
        workDir = Files.createTempDirectory("athena-knowledge-capture-work-");

        ImportedPullRequest pr = new ImportedPullRequest(1, "Fix retry configuration", "author", "base", "head",
                List.of(), List.of());
        session.connect("test-token");
        session.select(new WebSession.SelectedPullRequest(pr, REPOSITORY_FULL_NAME, workDir, baseRoot, headRoot,
                new ReviewStateStore(), new AnnotationBoard(), new ReviewSubmission()));
    }

    /**
     * Built lazily, on first actual use from a step — never eagerly in an {@code @Before} hook,
     * since {@link KnowledgeFixtureSteps}' own {@code @Before} (which initializes its store) is
     * not guaranteed to have run yet at that point, only by the time a step itself executes.
     */
    private AiAnalysisController controller() {
        if (controller == null) {
            controller = new AiAnalysisController(session, aiProvider, new KnowledgeProviderResolver(fixture.store()));
        }
        return controller;
    }

    @After
    public void cleanUp() {
        TempDirectories.deleteRecursively(baseRoot);
        TempDirectories.deleteRecursively(headRoot);
        TempDirectories.deleteRecursively(workDir);
    }

    @Given("the reviewer has accepted an AI finding {string}")
    public void the_reviewer_has_accepted_an_ai_finding(String description) {
        findingDescription = description;
        aiProvider.willReturnFinding(description, description);
        controller().triggerAnalysis();
        controller().accept(description);
    }

    @When("the user saves that finding to the Knowledge Base")
    public void the_user_saves_that_finding_to_the_knowledge_base() {
        attemptSave();
    }

    @When("the user attempts to save that finding to the Knowledge Base")
    public void the_user_attempts_to_save_that_finding_to_the_knowledge_base() {
        attemptSave();
    }

    @Then("the Knowledge Provider stores a new knowledge item with that content")
    public void the_knowledge_provider_stores_a_new_knowledge_item_with_that_content() throws IOException {
        assertThat(captureResponse.success()).isTrue();
        Path capturedNotesDir = fixture.vaultDir().resolve("Athena Knowledge");
        assertThat(Files.isDirectory(capturedNotesDir)).isTrue();
        try (var files = Files.list(capturedNotesDir)) {
            List<Path> notes = files.toList();
            assertThat(notes).hasSize(1);
            assertThat(Files.readString(notes.get(0))).contains(findingDescription);
        }
    }

    @Then("the stored knowledge item's provenance names {string} as its source")
    public void the_stored_knowledge_items_provenance_names_as_its_source(String providerId) {
        assertThat(captureResponse.providerId()).isEqualTo(providerId);
    }

    @Then("the save attempt is rejected because no Knowledge Provider is configured")
    public void the_save_attempt_is_rejected_because_no_knowledge_provider_is_configured() {
        assertThat(failure).isNotNull();
        assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    private void attemptSave() {
        try {
            captureResponse = controller().saveToKnowledgeBase(findingDescription);
        } catch (ResponseStatusException e) {
            failure = e;
        }
    }
}
