package com.athena.ai;

import com.athena.plugins.TestChanges;
import com.athena.reviewcontext.ReviewContext;
import com.athena.reviewui.AnnotationBoard;
import com.athena.reviewui.AnnotationScope;
import com.athena.reviewui.JavaFixtureSupport;
import com.athena.semantic.Change;
import com.athena.semantic.ReviewState;
import com.athena.semantic.ReviewStateStore;
import com.athena.semantic.TransformationKind;
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

import static org.assertj.core.api.Assertions.assertThat;

public class AiAnalysisOrchestratorSteps {

    private static final String PR_TITLE = "Move authentication to Account";

    private final ReviewStateStore store = new ReviewStateStore();
    private final AnnotationBoard board = new AnnotationBoard();
    private final FakeAiProvider provider = new FakeAiProvider();

    private Path baseRoot;
    private Path headRoot;
    private List<Change> changes;
    private Change renameChange;
    private Change moveChange;
    private Change generatedChange;
    private AiFindingsBoard resultBoard;

    @Before
    public void createTempRoots() throws IOException {
        baseRoot = Files.createTempDirectory("athena-ai-orchestration-base");
        headRoot = Files.createTempDirectory("athena-ai-orchestration-head");
    }

    @After
    public void cleanUpTempRoots() throws IOException {
        JavaFixtureSupport.deleteRecursively(baseRoot);
        JavaFixtureSupport.deleteRecursively(headRoot);
    }

    @Given("a PR with a reviewed rename Change")
    public void a_pr_with_a_reviewed_rename_change() {
        JavaFixtureSupport.writeRenameFixture(baseRoot, headRoot);
        changes = detect();
        renameChange = findByKind(changes, TransformationKind.RENAME_SYMBOL);
        store.setState(renameChange, ReviewState.REVIEWED);
    }

    @Given("the AI provider will flag a possible missed edge case about the rename Change")
    public void the_ai_provider_will_flag_a_finding_about_the_rename_change() {
        provider.willReturnFinding("finding-1", "The renamed symbol may still be referenced in configuration files",
                renameChange.title());
    }

    @Given("a PR with a reviewed rename Change, a private note on it, an unreviewed move Change, and a reviewed Change confined to generated files")
    public void a_pr_with_reviewed_rename_private_note_unreviewed_move_and_generated_change() {
        JavaFixtureSupport.writeRenameFixture(baseRoot, headRoot);
        writeMoveFixture(baseRoot, headRoot);
        writeGeneratedFileChangeFixture(baseRoot, headRoot);
        changes = detect();

        List<Change> renames = changes.stream().filter(change -> change.kind() == TransformationKind.RENAME_SYMBOL).toList();
        renameChange = renames.stream().filter(change -> change.title().contains("Greeter")).findFirst().orElseThrow();
        generatedChange = renames.stream().filter(change -> change.title().contains("GeneratedThing")).findFirst().orElseThrow();
        moveChange = findByKind(changes, TransformationKind.MOVE_SYMBOL);

        store.setState(renameChange, ReviewState.REVIEWED);
        store.setState(generatedChange, ReviewState.REVIEWED);
        // moveChange is left at its default Unseen state — deliberately not reviewed yet.
        board.addPrivateNote(AnnotationScope.change(renameChange), "Not fully sure, ask the author");
    }

    @When("the reviewer triggers AI analysis for the PR")
    public void the_reviewer_triggers_ai_analysis_for_the_pr() {
        resultBoard = AiAnalysisOrchestrator.trigger(PR_TITLE, changes, store, board, provider);
    }

    @Then("the returned findings board has one item, attributed as {string}, with a jump target to the rename Change")
    public void the_returned_findings_board_has_one_item(String expectedSource) {
        assertThat(resultBoard.items()).hasSize(1);
        AiFindingItem item = resultBoard.items().get(0);
        assertThat(item.source()).isEqualTo(expectedSource);
        assertThat(item.jumpTarget()).isPresent();
        assertThat(item.jumpTarget().get().description()).isEqualTo(renameChange.title());
    }

    @Then("the AI provider received no private notes")
    public void the_ai_provider_received_no_private_notes() {
        assertThat(provider.lastAnalyzedReviewContext().privateNotes()).isEmpty();
    }

    @Then("the AI provider's received Changes do not include the move Change")
    public void the_ai_providers_received_changes_do_not_include_the_move_change() {
        assertThat(allReceivedChanges()).doesNotContain(moveChange);
    }

    @Then("the AI provider's received Changes do not include the generated-file Change")
    public void the_ai_providers_received_changes_do_not_include_the_generated_file_change() {
        assertThat(allReceivedChanges()).doesNotContain(generatedChange);
    }

    @Then("the AI provider's received Changes include the rename Change")
    public void the_ai_providers_received_changes_include_the_rename_change() {
        assertThat(allReceivedChanges()).contains(renameChange);
    }

    private List<Change> allReceivedChanges() {
        ReviewContext received = provider.lastAnalyzedReviewContext();
        List<Change> all = new ArrayList<>();
        all.addAll(received.reviewedChanges());
        all.addAll(received.skippedChanges());
        all.addAll(received.mechanicalChanges());
        all.addAll(received.concernChanges());
        return all;
    }

    private List<Change> detect() {
        return TestChanges.detect(baseRoot, headRoot);
    }

    private Change findByKind(List<Change> changes, TransformationKind kind) {
        return changes.stream().filter(change -> change.kind() == kind).findFirst().orElseThrow();
    }

    private void writeMoveFixture(Path baseRoot, Path headRoot) {
        JavaFixtureSupport.write(baseRoot, "Source", "public class Source {\n"
                + "    public String label() {\n"
                + "        return \"moved\";\n"
                + "    }\n"
                + "}\n");
        JavaFixtureSupport.write(baseRoot, "Destination", "public class Destination {\n}\n");
        JavaFixtureSupport.write(headRoot, "Source", "public class Source {\n}\n");
        JavaFixtureSupport.write(headRoot, "Destination", "public class Destination {\n"
                + "    public String label() {\n"
                + "        return \"moved\";\n"
                + "    }\n"
                + "}\n");
    }

    private void writeGeneratedFileChangeFixture(Path baseRoot, Path headRoot) {
        JavaFixtureSupport.write(baseRoot, "generated/GeneratedThing", "public class GeneratedThing {\n"
                + "    public String label() {\n"
                + "        return \"a\";\n"
                + "    }\n"
                + "}\n");
        JavaFixtureSupport.write(headRoot, "generated/GeneratedThing", "public class GeneratedThing {\n"
                + "    public String describe() {\n"
                + "        return \"a\";\n"
                + "    }\n"
                + "}\n");
    }
}
