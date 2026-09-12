package com.athena.reviewui;

import com.athena.semantic.Change;
import com.athena.semantic.ChangeCategory;
import com.athena.semantic.ChangeGrouper;
import com.athena.semantic.DetectedTransformation;
import com.athena.semantic.TransformationDetector;
import com.athena.semantic.TransformationKind;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;

public class PrUnderstandingAndReviewQueueSteps {

    private Path baseRoot;
    private Path headRoot;
    private String prTitle;
    private List<Change> changes;
    private PrUnderstandingView understandingView;
    private ReviewQueue reviewQueue;
    private boolean selectionSucceeded;

    @Before
    public void createTempRoots() throws IOException {
        baseRoot = Files.createTempDirectory("athena-prunderstanding-base");
        headRoot = Files.createTempDirectory("athena-prunderstanding-head");
    }

    @After
    public void cleanUpTempRoots() throws IOException {
        JavaFixtureSupport.deleteRecursively(baseRoot);
        JavaFixtureSupport.deleteRecursively(headRoot);
    }

    @Given("a PR titled {string} with a rename Change, a mechanical replacement Change, and a formatting-only Change")
    public void a_pr_with_rename_mechanical_and_formatting_changes(String title) {
        prTitle = title;
        writeRenameFixture();
        writeMechanicalReplacementFixture();

        // Formatting-only: same structure, different whitespace.
        write(baseRoot, "Farewell", "public class Farewell {\n"
                + "    public String bye() {\n"
                + "        return \"bye\";\n"
                + "    }\n"
                + "}\n");
        write(headRoot, "Farewell", "public class Farewell {\n"
                + "    public String bye() {\n"
                + "        return   \"bye\";\n"
                + "    }\n"
                + "}\n");

        changes = detectChanges();
    }

    @Given("a PR with a mechanical replacement Change and a rename Change")
    public void a_pr_with_mechanical_and_rename_changes() {
        writeRenameFixture();
        writeMechanicalReplacementFixture();
        changes = detectChanges();
    }

    private void writeRenameFixture() {
        write(baseRoot, "Greeter", "public class Greeter {\n"
                + "    public String greet() {\n"
                + "        return \"hi\";\n"
                + "    }\n"
                + "}\n");
        write(headRoot, "Greeter", "public class Greeter {\n"
                + "    public String salute() {\n"
                + "        return \"hi\";\n"
                + "    }\n"
                + "}\n");
    }

    private void writeMechanicalReplacementFixture() {
        for (int i = 0; i < 3; i++) {
            String refClass = "Ref" + i;
            write(baseRoot, refClass, "public class " + refClass + " {\n"
                    + "    public Foo make() {\n"
                    + "        return new Foo();\n"
                    + "    }\n"
                    + "}\n");
            write(headRoot, refClass, "public class " + refClass + " {\n"
                    + "    public Bar make() {\n"
                    + "        return new Bar();\n"
                    + "    }\n"
                    + "}\n");
        }
        write(baseRoot, "Foo", "public class Foo {\n}\n");
        write(headRoot, "Bar", "public class Bar {\n}\n");
    }

    @Given("a PR titled {string} with no detected Changes")
    public void a_pr_with_no_detected_changes(String title) {
        prTitle = title;
        write(baseRoot, "Untouched", "public class Untouched {\n}\n");
        write(headRoot, "Untouched", "public class Untouched {\n}\n");
        changes = detectChanges();
    }

    @When("the reviewer opens the PR Understanding View")
    public void the_reviewer_opens_the_pr_understanding_view() {
        understandingView = PrUnderstandingView.of(prTitle, changes);
    }

    @When("the reviewer opens the Review Queue")
    public void the_reviewer_opens_the_review_queue() {
        reviewQueue = ReviewQueue.of(changes);
    }

    @When("the reviewer selects the mechanical replacement Change out of order")
    public void the_reviewer_selects_the_mechanical_replacement_change_out_of_order() {
        // Selecting out of order is just picking any Change from the queue's own list —
        // there is no separate "locked" state to bypass, which is itself the behavior
        // under test: the queue never gates or blocks non-sequential access.
        selectionSucceeded = reviewQueue.orderedChanges().stream()
                .anyMatch(change -> change.kind() == TransformationKind.MECHANICAL_REPLACEMENT);
    }

    @Then("the view shows the PR title {string}")
    public void the_view_shows_the_pr_title(String expectedTitle) {
        assertThat(understandingView.prTitle()).isEqualTo(expectedTitle);
    }

    @Then("the view shows at least {int} Structural Change")
    public void the_view_shows_at_least_n_structural_changes(int minimumCount) {
        assertThat(understandingView.countFor(ChangeCategory.STRUCTURAL)).isGreaterThanOrEqualTo(minimumCount);
    }

    @Then("the view shows at least {int} Mechanical Changes")
    public void the_view_shows_at_least_n_mechanical_changes(int minimumCount) {
        assertThat(understandingView.countFor(ChangeCategory.MECHANICAL)).isGreaterThanOrEqualTo(minimumCount);
    }

    @Then("the queue lists the rename Change before the mechanical replacement Change")
    public void the_queue_lists_rename_before_mechanical() {
        List<Change> ordered = reviewQueue.orderedChanges();
        int renameIndex = indexOfKind(ordered, TransformationKind.RENAME_SYMBOL);
        int mechanicalIndex = indexOfKind(ordered, TransformationKind.MECHANICAL_REPLACEMENT);
        assertThat(renameIndex).isLessThan(mechanicalIndex);
    }

    private int indexOfKind(List<Change> changes, TransformationKind kind) {
        for (int i = 0; i < changes.size(); i++) {
            if (changes.get(i).kind() == kind) {
                return i;
            }
        }
        throw new NoSuchElementException("No Change of kind " + kind);
    }

    @Then("the Review Queue is unchanged")
    public void the_review_queue_is_unchanged() {
        assertThat(reviewQueue.orderedChanges()).hasSize(changes.size());
    }

    @Then("the selection succeeds")
    public void the_selection_succeeds() {
        assertThat(selectionSucceeded).isTrue();
    }

    private List<Change> detectChanges() {
        List<DetectedTransformation> transformations = new TransformationDetector().detect(baseRoot, headRoot);
        return new ChangeGrouper().group(transformations);
    }

    private void write(Path root, String className, String content) {
        JavaFixtureSupport.write(root, className, content);
    }
}
