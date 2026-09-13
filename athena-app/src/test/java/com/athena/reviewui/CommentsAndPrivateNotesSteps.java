package com.athena.reviewui;

import com.athena.plugins.TestChanges;
import com.athena.semantic.Change;
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

public class CommentsAndPrivateNotesSteps {

    private final AnnotationBoard board = new AnnotationBoard();

    private Path baseRoot;
    private Path headRoot;
    private AnnotationScope currentScope;
    private Change theChange;
    private RuntimeException rejection;

    @Before
    public void createTempRoots() throws IOException {
        baseRoot = Files.createTempDirectory("athena-annotations-base");
        headRoot = Files.createTempDirectory("athena-annotations-head");
    }

    @After
    public void cleanUpTempRoots() throws IOException {
        JavaFixtureSupport.deleteRecursively(baseRoot);
        JavaFixtureSupport.deleteRecursively(headRoot);
    }

    @Given("a reviewer viewing line {int} of file {string}")
    public void a_reviewer_viewing_line_of_file(int line, String filePath) {
        currentScope = AnnotationScope.line(filePath, line);
    }

    @Given("a reviewer viewing symbol {string}")
    public void a_reviewer_viewing_symbol(String symbolDescription) {
        currentScope = AnnotationScope.symbol(symbolDescription);
    }

    @Given("a reviewer viewing a Change")
    public void a_reviewer_viewing_a_change() {
        JavaFixtureSupport.writeRenameFixture(baseRoot, headRoot);
        theChange = TestChanges.detect(baseRoot, headRoot).stream().findFirst().orElseThrow();
        currentScope = AnnotationScope.change(theChange);
    }

    @Given("a reviewer viewing the whole review")
    public void a_reviewer_viewing_the_whole_review() {
        currentScope = AnnotationScope.review();
    }

    @When("the reviewer adds the comment {string} at that line")
    @When("the reviewer adds the comment {string} at that symbol")
    @When("the reviewer adds the comment {string} at that Change")
    public void the_reviewer_adds_the_comment_at_current_scope(String text) {
        board.addComment(currentScope, text);
    }

    @When("the reviewer adds the comment {string} at review scope")
    public void the_reviewer_adds_the_comment_at_review_scope(String text) {
        board.addComment(currentScope, text);
    }

    @When("the reviewer adds the private note {string} at that Change")
    public void the_reviewer_adds_the_private_note_at_that_change(String text) {
        board.addPrivateNote(currentScope, text);
    }

    @Then("the line's comments include {string}")
    @Then("the symbol's comments include {string}")
    @Then("the Change's comments include {string}")
    @Then("the review's comments include {string}")
    public void the_scopes_comments_include(String expectedText) {
        assertThat(board.commentsAt(currentScope)).extracting(Comment::text).contains(expectedText);
    }

    @Then("the Change's private notes include {string}")
    public void the_changes_private_notes_include(String expectedText) {
        assertThat(board.privateNotesAt(currentScope)).extracting(PrivateNote::text).contains(expectedText);
    }

    @Then("the Change's comments do not include {string}")
    public void the_changes_comments_do_not_include(String text) {
        assertThat(board.commentsAt(currentScope)).extracting(Comment::text).doesNotContain(text);
    }

    @When("the reviewer attempts to add a blank comment at that Change")
    public void the_reviewer_attempts_to_add_a_blank_comment() {
        try {
            board.addComment(currentScope, "   ");
            rejection = null;
        } catch (RuntimeException e) {
            rejection = e;
        }
    }

    @Then("the attempt is rejected as invalid")
    public void the_attempt_is_rejected_as_invalid() {
        assertThat(rejection).isInstanceOf(IllegalArgumentException.class);
    }
}
