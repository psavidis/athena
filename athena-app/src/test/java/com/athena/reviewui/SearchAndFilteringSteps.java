package com.athena.reviewui;

import com.athena.plugins.TestChanges;
import com.athena.semantic.Change;
import com.athena.semantic.ChangeCategory;
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
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

public class SearchAndFilteringSteps {

    private final ReviewStateStore store = new ReviewStateStore();
    private final AnnotationBoard board = new AnnotationBoard();

    private Path baseRoot;
    private Path headRoot;
    private List<Change> changes;
    private Change renameChange;
    private Change mechanicalChange;
    private List<Change> searchResults;
    private List<Change> filteredResults;

    @Before
    public void createTempRoots() throws IOException {
        baseRoot = Files.createTempDirectory("athena-search-base");
        headRoot = Files.createTempDirectory("athena-search-head");
    }

    @After
    public void cleanUpTempRoots() throws IOException {
        JavaFixtureSupport.deleteRecursively(baseRoot);
        JavaFixtureSupport.deleteRecursively(headRoot);
    }

    @Given("a searchable PR with a rename Change and a mechanical replacement Change")
    public void a_pr_with_a_rename_and_mechanical_change() {
        JavaFixtureSupport.writeRenameFixture(baseRoot, headRoot);
        JavaFixtureSupport.writeMechanicalReplacementFixture(baseRoot, headRoot);
        changes = detectChanges();
        renameChange = changes.stream()
                .filter(change -> change.kind() == TransformationKind.RENAME_SYMBOL)
                .findFirst()
                .orElseThrow();
        mechanicalChange = mechanicalReplacementChange(changes);
    }

    @Given("a comment {string} attached to the rename Change")
    public void a_comment_attached_to_the_rename_change(String text) {
        board.addComment(AnnotationScope.change(renameChange), text);
    }

    @When("the same PR is re-analyzed, producing new Change instances for the same transformations")
    public void the_same_pr_is_re_analyzed() {
        // A fresh detect+group run produces new Change objects for the same underlying
        // transformations (e.g. what happens after a force-push) — the old renameChange
        // reference the comment was attached to is now stale, but AnnotationScope keys
        // on ChangeIdentity (symbol-set + transformation-shape), not object identity,
        // so the comment must still be found via the newly-detected equivalent Change.
        changes = detectChanges();
        renameChange = changes.stream()
                .filter(change -> change.kind() == TransformationKind.RENAME_SYMBOL)
                .findFirst()
                .orElseThrow();
        mechanicalChange = mechanicalReplacementChange(changes);
    }

    @Given("the reviewer has reviewed the rename Change in the search fixture")
    public void the_reviewer_has_reviewed_the_rename_change() {
        store.setState(renameChange, ReviewState.REVIEWED);
    }

    @When("the reviewer searches for {string}")
    public void the_reviewer_searches_for(String query) {
        searchResults = ChangeSearch.search(changes, board, query);
    }

    @When("the reviewer filters by category {string}")
    public void the_reviewer_filters_by_category(String category) {
        filteredResults = ChangeFilter.byCategory(changes, ChangeCategory.valueOf(category.toUpperCase(Locale.ROOT)));
    }

    @When("the reviewer filters by review state {string}")
    public void the_reviewer_filters_by_review_state(String state) {
        filteredResults = ChangeFilter.byReviewState(changes, store, ReviewState.valueOf(state.toUpperCase(Locale.ROOT)));
    }

    @When("the reviewer filters by symbol {string}")
    public void the_reviewer_filters_by_symbol(String symbolDescription) {
        filteredResults = ChangeFilter.bySymbol(changes, symbolDescription);
    }

    @Then("the search results include the rename Change")
    public void the_search_results_include_the_rename_change() {
        assertThat(searchResults).contains(renameChange);
    }

    @Then("the search results do not include the mechanical replacement Change")
    public void the_search_results_do_not_include_the_mechanical_change() {
        assertThat(searchResults).doesNotContain(mechanicalChange);
    }

    @Then("the search results are empty")
    public void the_search_results_are_empty() {
        assertThat(searchResults).isEmpty();
    }

    @Then("the filtered results include the rename Change")
    public void the_filtered_results_include_the_rename_change() {
        assertThat(filteredResults).contains(renameChange);
    }

    @Then("the filtered results do not include the mechanical replacement Change")
    public void the_filtered_results_do_not_include_the_mechanical_change() {
        assertThat(filteredResults).doesNotContain(mechanicalChange);
    }

    private List<Change> detectChanges() {
        return TestChanges.detect(baseRoot, headRoot);
    }

    /**
     * writeRenameFixture's own method rename incidentally also satisfies the mechanical-
     * replacement detector's whole-identifier-consistently-substituted heuristic (see
     * ChangeMapViewSteps' own note on this), producing a second MECHANICAL_REPLACEMENT
     * Change ("greet -> salute") alongside writeMechanicalReplacementFixture's genuine one
     * ("Foo -> Bar", spanning 3 reference files). This scenario needs the latter — an
     * unrelated Change whose description doesn't textually overlap the rename's — so pick
     * the one with more than one occurrence rather than the first MECHANICAL_REPLACEMENT
     * found, which the incidental single-occurrence duplicate would otherwise satisfy.
     */
    private Change mechanicalReplacementChange(List<Change> changes) {
        return changes.stream()
                .filter(change -> change.kind() == TransformationKind.MECHANICAL_REPLACEMENT)
                .filter(change -> change.occurrenceCount() > 1)
                .findFirst()
                .orElseThrow();
    }
}
