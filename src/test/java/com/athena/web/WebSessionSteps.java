package com.athena.web;

import com.athena.git.TempDirectories;
import com.athena.github.ImportedPullRequest;
import com.athena.reviewcontext.ReviewSubmission;
import com.athena.reviewui.AnnotationBoard;
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

public class WebSessionSteps {

    private final WebSession session = new WebSession();

    private Path firstWorkDir;
    private Path secondWorkDir;

    @Before
    public void createTempRoots() throws IOException {
        firstWorkDir = Files.createTempDirectory("athena-session-first-work-");
        secondWorkDir = Files.createTempDirectory("athena-session-second-work-");
    }

    @After
    public void cleanUpTempRoots() {
        // Both should already be gone by the time the scenario finishes; defensive cleanup only
        // in case an assertion failed before the behavior under test ran.
        TempDirectories.deleteRecursively(firstWorkDir);
        TempDirectories.deleteRecursively(secondWorkDir);
    }

    @Given("the reviewer's web session has a PR selected with checked-out directories")
    public void the_session_has_a_pr_selected() throws IOException {
        Path baseRoot = Files.createTempDirectory(firstWorkDir, "base-");
        Path headRoot = Files.createTempDirectory(firstWorkDir, "head-");
        ImportedPullRequest firstPr = new ImportedPullRequest(1, "First PR", "author", "base1", "head1", List.of(), List.of());
        session.select(new WebSession.SelectedPullRequest(
                firstPr, "acme/first", firstWorkDir, baseRoot, headRoot, new ReviewStateStore(), new AnnotationBoard(),
                new ReviewSubmission()));
    }

    @When("the reviewer selects a different PR")
    public void the_reviewer_selects_a_different_pr() throws IOException {
        Path baseRoot = Files.createTempDirectory(secondWorkDir, "base-");
        Path headRoot = Files.createTempDirectory(secondWorkDir, "head-");
        ImportedPullRequest secondPr = new ImportedPullRequest(2, "Second PR", "author", "base2", "head2", List.of(), List.of());
        session.select(new WebSession.SelectedPullRequest(
                secondPr, "acme/second", secondWorkDir, baseRoot, headRoot, new ReviewStateStore(), new AnnotationBoard(),
                new ReviewSubmission()));
    }

    @Then("the previous PR's checkout directories no longer exist")
    public void the_previous_checkout_directories_no_longer_exist() {
        assertThat(firstWorkDir).doesNotExist();
    }

    @Then("the session's selected PR is now the new one")
    public void the_sessions_selected_pr_is_now_the_new_one() {
        assertThat(session.selectedPullRequest()).isPresent();
        assertThat(session.selectedPullRequest().get().pullRequest().number()).isEqualTo(2);
    }
}
