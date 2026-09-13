package com.athena.github;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.assertj.core.api.Assertions.assertThat;

public class ImportReviewDataAndPermissionsSteps {

    private final GitHubTestContext context;
    private ImportedReviewData reviewData;
    private String permissionLevel;
    private Exception thrown;

    public ImportReviewDataAndPermissionsSteps(GitHubTestContext context) {
        this.context = context;
    }

    @Given("pull request {int} in repository {string} has a review comment by {string} saying {string} on file {string}")
    public void pull_request_has_review_comment(int number, String repo, String author, String body, String path) {
        context.transport.addReviewComment(repo, number, author, body, path);
    }

    @Given("pull request {int} in repository {string} has a review by {string} with state {string}")
    public void pull_request_has_review(int number, String repo, String reviewer, String state) {
        context.transport.addReview(repo, number, reviewer, state);
    }

    @Given("the authenticated user has permission {string} on repository {string}")
    public void authenticated_user_has_permission(String level, String repo) {
        context.transport.setPermission(repo, level);
    }

    @When("Athena imports review data for pull request {int} in repository {string}")
    public void athena_imports_review_data(int number, String repo) {
        ReviewDataImporter importer = new ReviewDataImporter(context.token, context.transport);
        try {
            reviewData = importer.importReviewData(repo, number);
        } catch (Exception e) {
            thrown = e;
        }
    }

    @When("Athena imports permissions for repository {string}")
    public void athena_imports_permissions(String repo) {
        ReviewDataImporter importer = new ReviewDataImporter(context.token, context.transport);
        try {
            permissionLevel = importer.importPermission(repo);
        } catch (Exception e) {
            thrown = e;
        }
    }

    @Then("the imported review data includes a comment by {string} saying {string} on file {string}")
    public void imported_review_data_includes_comment(String author, String body, String path) {
        assertThat(reviewData.comments())
                .anySatisfy(c -> {
                    assertThat(c.author()).isEqualTo(author);
                    assertThat(c.body()).isEqualTo(body);
                    assertThat(c.path()).isEqualTo(path);
                });
    }

    @Then("the imported review data shows {string} with review state {string}")
    public void imported_review_data_shows_reviewer_state(String reviewer, String state) {
        assertThat(reviewData.reviews())
                .anySatisfy(r -> {
                    assertThat(r.reviewer()).isEqualTo(reviewer);
                    assertThat(r.state()).isEqualTo(state);
                });
    }

    @Then("the imported permission level is {string}")
    public void imported_permission_level_is(String level) {
        assertThat(permissionLevel).isEqualTo(level);
    }

    @Then("the import fails clearly")
    public void import_fails_clearly() {
        assertThat(thrown).isNotNull();
        assertThat(thrown.getMessage()).isNotBlank();
    }
}
