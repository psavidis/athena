package com.athena.reviewui;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ChangeMapViewSteps {

    private final ReviewStateStore store = new ReviewStateStore();

    private Path baseRoot;
    private Path headRoot;
    private List<Change> changes;
    private ChangeMapView view;

    @Before
    public void createTempRoots() throws IOException {
        baseRoot = Files.createTempDirectory("athena-changemap-base");
        headRoot = Files.createTempDirectory("athena-changemap-head");
    }

    @After
    public void cleanUpTempRoots() throws IOException {
        JavaFixtureSupport.deleteRecursively(baseRoot);
        JavaFixtureSupport.deleteRecursively(headRoot);
    }

    @Given("a PR with a rename Change and a mechanical replacement Change")
    public void a_pr_with_a_rename_and_mechanical_change() {
        // writeRenameFixture's own method rename (Greeter#greet -> Greeter#salute) already
        // produces both a RENAME_SYMBOL Change and — since "greet" is consistently replaced
        // by "salute" everywhere it appears (this one file) — a MECHANICAL_REPLACEMENT Change
        // for the same identifier pair, which is exactly the "rename Change and a mechanical
        // replacement Change" this scenario asks for. No second, unrelated fixture is needed
        // (an earlier version added an unrelated Widget -> Gadget reference for that purpose,
        // but any real field/method-typed reference to it also registers its own independent
        // structural Change — e.g. a field's declared type changing — once the field/method
        // detectors correctly report that fact, which isn't what this scenario is about).
        JavaFixtureSupport.writeRenameFixture(baseRoot, headRoot);
    }

    @Given("a PR with a newly-produced rename Change")
    public void a_pr_with_a_newly_produced_rename_change() {
        JavaFixtureSupport.writeRenameFixture(baseRoot, headRoot);
    }

    @Given("a PR with a rename Change")
    public void a_pr_with_a_rename_change() {
        JavaFixtureSupport.writeRenameFixture(baseRoot, headRoot);
    }

    @Given("the reviewer has marked that Change as {string}")
    public void the_reviewer_has_marked_that_change_as(String state) {
        // Detect once here so the state can be set on a real Change; ChangeIdentity
        // (symbol-set + transformation-shape) makes this recognizable as "the same"
        // Change when the When-step re-detects it from the same fixture.
        Change detectedNow = detectRenameChange();
        store.setState(detectedNow, ReviewState.valueOf(state.toUpperCase()));
    }

    @Given("a PR with no detected Changes")
    public void a_pr_with_no_detected_changes() {
        write(baseRoot, "Untouched", "public class Untouched {\n}\n");
        write(headRoot, "Untouched", "public class Untouched {\n}\n");
    }

    @Given("a PR where class {string} has two methods with changed signatures, and an unrelated rename elsewhere")
    public void a_pr_with_two_signature_changes_on_one_class_and_an_unrelated_rename(String className) {
        write(baseRoot, className, "public class " + className + " {\n"
                + "    public String deviceApplicationService() {\n"
                + "        return \"a\";\n"
                + "    }\n"
                + "    public String deviceUseCases() {\n"
                + "        return \"b\";\n"
                + "    }\n"
                + "}\n");
        write(headRoot, className, "public class " + className + " {\n"
                + "    public String deviceApplicationService(String zone) {\n"
                + "        return \"a\" + zone;\n"
                + "    }\n"
                + "    public String deviceUseCases(String zone) {\n"
                + "        return \"b\" + zone;\n"
                + "    }\n"
                + "}\n");
        JavaFixtureSupport.writeRenameFixture(baseRoot, headRoot);
    }

    @When("the reviewer opens the Change Map")
    public void the_reviewer_opens_the_change_map() {
        List<DetectedTransformation> transformations = new TransformationDetector().detect(baseRoot, headRoot);
        changes = new ChangeGrouper().group(transformations);
        view = ChangeMapView.of(changes, store);
    }

    @Then("the Change Map lists {int} entries")
    public void the_change_map_lists_n_entries(int expectedCount) {
        assertThat(view.entries()).hasSize(expectedCount);
    }

    @Then("an entry shows its category")
    public void an_entry_shows_its_category() {
        assertThat(view.entries()).allSatisfy(entry -> assertThat(entry.category()).isNotNull());
    }

    @Then("an entry shows its transformation description")
    public void an_entry_shows_its_transformation_description() {
        assertThat(view.entries()).allSatisfy(entry -> assertThat(entry.description()).isNotBlank());
    }

    @Then("an entry shows its current review state")
    public void an_entry_shows_its_current_review_state() {
        assertThat(view.entries()).allSatisfy(entry -> assertThat(entry.reviewState()).isNotNull());
    }

    @Then("the Change Map has a class group for {string} containing {int} entries")
    public void the_change_map_has_a_class_group_containing_n_entries(String enclosingType, int expectedCount) {
        assertThat(view.classGroups())
                .filteredOn(group -> group.enclosingType().equals(enclosingType))
                .singleElement()
                .satisfies(group -> assertThat(group.entries()).hasSize(expectedCount));
    }

    @Then("the Change Map has a class group for {string} containing {int} entry")
    public void the_change_map_has_a_class_group_containing_n_entry(String enclosingType, int expectedCount) {
        the_change_map_has_a_class_group_containing_n_entries(enclosingType, expectedCount);
    }

    @Then("the entry for that Change shows review state {string}")
    public void the_entry_for_that_change_shows_review_state(String expectedState) {
        // The rename fixture also satisfies the mechanical-replacement detector's own
        // whole-identifier-substitution rule (a single consistent method-name swap in
        // one file looks like both a rename and a mechanical replacement to the two
        // independent detectors) — both are legitimately detected; assert on the rename
        // specifically, by TransformationKind rather than the coarser ChangeCategory
        // (multiple kinds can share a category, which would make this ambiguous).
        ChangeMapEntry renameEntry = view.entries().stream()
                .filter(entry -> entry.change().kind() == TransformationKind.RENAME_SYMBOL)
                .findFirst()
                .orElseThrow();
        assertThat(renameEntry.reviewState()).isEqualTo(ReviewState.valueOf(expectedState.toUpperCase()));
    }

    private Change detectRenameChange() {
        List<DetectedTransformation> transformations = new TransformationDetector().detect(baseRoot, headRoot);
        return new ChangeGrouper().group(transformations).stream()
                .filter(change -> change.kind() == TransformationKind.RENAME_SYMBOL)
                .findFirst()
                .orElseThrow();
    }

    private void write(Path root, String className, String content) {
        JavaFixtureSupport.write(root, className, content);
    }
}
