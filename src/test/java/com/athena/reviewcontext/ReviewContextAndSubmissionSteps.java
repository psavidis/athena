package com.athena.reviewcontext;

import com.athena.reviewui.AnnotationBoard;
import com.athena.reviewui.AnnotationScope;
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
import io.cucumber.java.en.When;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ReviewContextAndSubmissionSteps {

    private final ReviewStateStore store = new ReviewStateStore();
    private final AnnotationBoard board = new AnnotationBoard();

    private Path baseRoot;
    private Path headRoot;
    private String prTitle;
    private List<Change> changes;
    private Change renameChange;
    private Change mechanicalChange;
    private ReviewContext reviewContext;
    private PreSubmissionSummary summary;
    private ReviewSubmission submission;
    private boolean submissionSent;

    @Before
    public void createTempRoots() throws IOException {
        baseRoot = Files.createTempDirectory("athena-reviewcontext-base");
        headRoot = Files.createTempDirectory("athena-reviewcontext-head");
    }

    @After
    public void cleanUpTempRoots() throws IOException {
        deleteRecursively(baseRoot);
        deleteRecursively(headRoot);
    }

    @Given("a PR titled {string} with a rename Change and a mechanical replacement Change")
    public void a_pr_titled_with_a_rename_and_mechanical_change(String title) {
        prTitle = title;
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

        List<DetectedTransformation> transformations = new TransformationDetector().detect(baseRoot, headRoot);
        changes = new ChangeGrouper().group(transformations);
        renameChange = changes.stream()
                .filter(change -> change.kind() == TransformationKind.RENAME_SYMBOL)
                .findFirst()
                .orElseThrow();
        mechanicalChange = changes.stream()
                .filter(change -> change.kind() == TransformationKind.MECHANICAL_REPLACEMENT)
                .findFirst()
                .orElseThrow();
    }

    @Given("the reviewer has reviewed the rename Change for the review context")
    public void the_reviewer_has_reviewed_the_rename_change() {
        store.setState(renameChange, ReviewState.REVIEWED);
    }

    @Given("the reviewer has marked the mechanical replacement Change as mechanical")
    public void the_reviewer_has_marked_the_mechanical_change_as_mechanical() {
        store.markMechanical(mechanicalChange);
    }

    @Given("a review-context comment {string} attached to the rename Change")
    public void a_review_context_comment_attached_to_the_rename_change(String text) {
        board.addComment(AnnotationScope.change(renameChange), text);
    }

    @Given("a review-context private note {string} attached to the rename Change")
    public void a_review_context_private_note_attached_to_the_rename_change(String text) {
        board.addPrivateNote(AnnotationScope.change(renameChange), text);
    }

    @When("the reviewer assembles the Review Context")
    public void the_reviewer_assembles_the_review_context() {
        reviewContext = ReviewContext.assemble(prTitle, changes, store, board, false);
    }

    @When("the reviewer assembles the Review Context including private notes")
    public void the_reviewer_assembles_the_review_context_including_private_notes() {
        reviewContext = ReviewContext.assemble(prTitle, changes, store, board, true);
    }

    @When("the reviewer opens the pre-submission summary")
    public void the_reviewer_opens_the_pre_submission_summary() {
        reviewContext = ReviewContext.assemble(prTitle, changes, store, board, false);
        summary = PreSubmissionSummary.of(reviewContext);
    }

    @When("the reviewer attempts to submit without confirming")
    public void the_reviewer_attempts_to_submit_without_confirming() {
        submission = new ReviewSubmission();
        submissionSent = submission.isSubmitted();
    }

    @When("the reviewer confirms and submits the review")
    public void the_reviewer_confirms_and_submits_the_review() {
        submission = new ReviewSubmission();
        submission.confirmAndSubmit();
        submissionSent = submission.isSubmitted();
    }

    @Then("the Review Context's reviewed Changes include the rename Change")
    public void the_review_contexts_reviewed_changes_include_the_rename_change() {
        assertThat(reviewContext.reviewedChanges()).contains(renameChange);
    }

    @Then("the Review Context's mechanical Changes include the mechanical replacement Change")
    public void the_review_contexts_mechanical_changes_include_the_mechanical_change() {
        assertThat(reviewContext.mechanicalChanges()).contains(mechanicalChange);
    }

    @Then("the Review Context's comments include {string}")
    public void the_review_contexts_comments_include(String text) {
        assertThat(reviewContext.comments()).extracting(comment -> comment.text()).contains(text);
    }

    @Then("the Review Context's private notes are empty")
    public void the_review_contexts_private_notes_are_empty() {
        assertThat(reviewContext.privateNotes()).isEmpty();
    }

    @Then("the Review Context's private notes include {string}")
    public void the_review_contexts_private_notes_include(String text) {
        assertThat(reviewContext.privateNotes()).extracting(note -> note.text()).contains(text);
    }

    @Then("the Review Context's coverage summary reports exactly {int} Change reviewed out of the total detected")
    public void the_review_contexts_coverage_summary_reports_exactly_n_reviewed(int reviewedCount) {
        assertThat(reviewContext.coverageSummary()).isEqualTo(reviewedCount + " of " + changes.size() + " meaningful Changes reviewed");
    }

    @Then("the summary lists the rename Change as reviewed")
    public void the_summary_lists_the_rename_change_as_reviewed() {
        assertThat(summary.reviewedChangeTitles()).contains(renameChange.title());
    }

    @Then("the summary lists the mechanical replacement Change as classified mechanical")
    public void the_summary_lists_the_mechanical_change_as_classified_mechanical() {
        assertThat(summary.mechanicalChangeTitles()).contains(mechanicalChange.title());
    }

    @Then("the summary reports {int} comment")
    public void the_summary_reports_n_comment(int expectedCount) {
        assertThat(summary.commentCount()).isEqualTo(expectedCount);
    }

    @Then("the submission is not sent")
    public void the_submission_is_not_sent() {
        assertThat(submissionSent).isFalse();
    }

    @Then("the submission is sent")
    public void the_submission_is_sent() {
        assertThat(submissionSent).isTrue();
    }

    private void write(Path root, String className, String content) {
        try {
            Files.writeString(root.resolve(className + ".java"), content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var walk = Files.walk(root)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }
}
