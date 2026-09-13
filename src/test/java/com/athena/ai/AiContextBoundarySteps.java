package com.athena.ai;

import com.athena.reviewcontext.ReviewContext;
import com.athena.reviewui.AnnotationBoard;
import com.athena.reviewui.AnnotationScope;
import com.athena.reviewui.JavaFixtureSupport;
import com.athena.semantic.Change;
import com.athena.semantic.ChangeGrouper;
import com.athena.semantic.DetectedTransformation;
import com.athena.semantic.ReviewState;
import com.athena.semantic.ReviewStateStore;
import com.athena.semantic.TransformationDetector;
import com.athena.semantic.TransformationKind;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class AiContextBoundarySteps {

    private final ReviewStateStore store = new ReviewStateStore();
    private final AnnotationBoard board = new AnnotationBoard();

    private Path baseRoot;
    private Path headRoot;
    private Change renameChange;
    private Change mechanicalChange;
    private Change moveChange;
    private Change generatedChange;
    private AiContextBoundary boundary;

    @Before
    public void createTempRoots() throws IOException {
        baseRoot = Files.createTempDirectory("athena-ai-boundary-base");
        headRoot = Files.createTempDirectory("athena-ai-boundary-head");
    }

    @After
    public void cleanUpTempRoots() throws IOException {
        JavaFixtureSupport.deleteRecursively(baseRoot);
        JavaFixtureSupport.deleteRecursively(headRoot);
    }

    @Given("the reviewer has assembled an AI context boundary for a PR with a reviewed rename Change and a mechanical replacement Change marked mechanical")
    public void a_boundary_with_reviewed_rename_and_mechanical_change() {
        JavaFixtureSupport.writeRenameFixture(baseRoot, headRoot);
        JavaFixtureSupport.writeMechanicalReplacementFixture(baseRoot, headRoot);
        List<Change> changes = detect();

        renameChange = findByKind(changes, TransformationKind.RENAME_SYMBOL);
        mechanicalChange = findByKind(changes, TransformationKind.MECHANICAL_REPLACEMENT);
        store.setState(renameChange, ReviewState.REVIEWED);
        store.markMechanical(mechanicalChange);

        assembleBoundary(changes);
    }

    @Given("the reviewer has assembled an AI context boundary for a PR with a reviewed rename Change and an unreviewed move Change")
    public void a_boundary_with_reviewed_rename_and_unreviewed_move() {
        JavaFixtureSupport.writeRenameFixture(baseRoot, headRoot);
        writeMoveFixture(baseRoot, headRoot);
        List<Change> changes = detect();

        renameChange = findByKind(changes, TransformationKind.RENAME_SYMBOL);
        moveChange = findByKind(changes, TransformationKind.MOVE_SYMBOL);
        store.setState(renameChange, ReviewState.REVIEWED);
        // moveChange is left at its default Unseen state — deliberately not reviewed yet.

        assembleBoundary(changes);
    }

    @Given("the reviewer has assembled an AI context boundary for a PR with a reviewed rename Change and a reviewed Change confined to generated files")
    public void a_boundary_with_reviewed_rename_and_reviewed_generated_change() {
        JavaFixtureSupport.writeRenameFixture(baseRoot, headRoot);
        writeGeneratedFileChangeFixture(baseRoot, headRoot);
        List<Change> changes = detect();

        generatedChange = findByFileTouched(changes, "generated/GeneratedThing.java");
        renameChange = changes.stream()
                .filter(change -> change != generatedChange)
                .findFirst()
                .orElseThrow();
        store.setState(renameChange, ReviewState.REVIEWED);
        store.setState(generatedChange, ReviewState.REVIEWED);

        assembleBoundary(changes);
    }

    @Given("the reviewer has assembled an AI context boundary for a PR with a reviewed rename Change and a private note attached to it")
    public void a_boundary_with_reviewed_rename_and_a_private_note() {
        JavaFixtureSupport.writeRenameFixture(baseRoot, headRoot);
        List<Change> changes = detect();

        renameChange = findByKind(changes, TransformationKind.RENAME_SYMBOL);
        store.setState(renameChange, ReviewState.REVIEWED);
        board.addPrivateNote(AnnotationScope.change(renameChange), "Not fully sure, ask the author");

        assembleBoundary(changes);
    }

    @Then("the payload sent to the AI provider includes the rename Change")
    public void the_payload_includes_the_rename_change() {
        assertThat(boundary.payload().reviewedChanges()).contains(renameChange);
    }

    @Then("the payload sent to the AI provider includes the mechanical replacement Change")
    public void the_payload_includes_the_mechanical_change() {
        assertThat(boundary.payload().mechanicalChanges()).contains(mechanicalChange);
    }

    @Then("the boundary's excluded unreviewed Changes include the move Change")
    public void the_excluded_unreviewed_changes_include_the_move_change() {
        assertThat(boundary.excludedUnreviewedChanges()).contains(moveChange);
    }

    @Then("the payload sent to the AI provider does not include the move Change")
    public void the_payload_does_not_include_the_move_change() {
        assertPayloadDoesNotInclude(moveChange);
    }

    @Then("the boundary's excluded generated Changes include the generated-file Change")
    public void the_excluded_generated_changes_include_the_generated_file_change() {
        assertThat(boundary.excludedGeneratedChanges()).contains(generatedChange);
    }

    @Then("the payload sent to the AI provider does not include the generated-file Change")
    public void the_payload_does_not_include_the_generated_file_change() {
        assertPayloadDoesNotInclude(generatedChange);
    }

    @Then("the boundary reports private notes as excluded")
    public void the_boundary_reports_private_notes_as_excluded() {
        assertThat(boundary.privateNotesExcluded()).isTrue();
    }

    @Then("the payload sent to the AI provider has no private notes")
    public void the_payload_has_no_private_notes() {
        assertThat(boundary.payload().privateNotes()).isEmpty();
    }

    private void assertPayloadDoesNotInclude(Change change) {
        ReviewContext payload = boundary.payload();
        assertThat(payload.reviewedChanges()).doesNotContain(change);
        assertThat(payload.skippedChanges()).doesNotContain(change);
        assertThat(payload.mechanicalChanges()).doesNotContain(change);
        assertThat(payload.concernChanges()).doesNotContain(change);
    }

    private void assembleBoundary(List<Change> changes) {
        boundary = AiContextBoundary.assemble("Move authentication to Account", changes, store, board);
    }

    private List<Change> detect() {
        List<DetectedTransformation> transformations = new TransformationDetector().detect(baseRoot, headRoot);
        return new ChangeGrouper().group(transformations);
    }

    private Change findByKind(List<Change> changes, TransformationKind kind) {
        return changes.stream().filter(change -> change.kind() == kind).findFirst().orElseThrow();
    }

    private Change findByFileTouched(List<Change> changes, String filePath) {
        return changes.stream()
                .filter(change -> change.matchedOccurrences().stream()
                        .flatMap(occurrence -> occurrence.filesTouched().stream())
                        .anyMatch(filePath::equals))
                .findFirst()
                .orElseThrow();
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
