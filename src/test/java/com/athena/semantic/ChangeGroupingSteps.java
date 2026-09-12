package com.athena.semantic;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ChangeGroupingSteps {

    private final List<DetectedTransformation> transformations = new ArrayList<>();
    private List<Change> changes;
    private int occurrenceIndex = 0;

    @Given("{int} detected renames all following the pattern {string} to {string}")
    public void detected_renames_following_pattern(int count, String oldName, String newName) {
        for (int i = 0; i < count; i++) {
            transformations.add(renameTransformation(oldName, newName, occurrenceIndex++));
        }
    }

    @Given("{int} detected renames following the pattern {string} to {string}")
    public void detected_renames_following_pattern_singular_wording(int count, String oldName, String newName) {
        detected_renames_following_pattern(count, oldName, newName);
    }

    @Given("{int} detected rename with the same names but a different transformation shape")
    public void detected_rename_with_different_shape(int count) {
        for (int i = 0; i < count; i++) {
            // Same symbol names as the "Foo" -> "Bar" pattern, but a MOVE_SYMBOL kind
            // instead of RENAME_SYMBOL: same names, structurally a different shape, so
            // it must not silently join the rename group.
            transformations.add(DetectedTransformation.of(TransformationKind.MOVE_SYMBOL,
                    List.of("Foo#member", "Bar#member"), List.of("Exception" + occurrenceIndex + ".java")));
            occurrenceIndex++;
        }
    }

    @Given("{int} detected extract-method transformation for {string}")
    public void detected_extract_method_transformation(int count, String description) {
        for (int i = 0; i < count; i++) {
            transformations.add(DetectedTransformation.of(TransformationKind.EXTRACT_METHOD,
                    List.of(description), List.of("Greeter.java")));
        }
    }

    @When("the semantic engine groups the detected transformations into Changes")
    public void group_transformations_into_changes() {
        changes = new ChangeGrouper().group(transformations);
    }

    @Then("one Change is produced for the {string} to {string} rename")
    public void one_change_is_produced_for_rename(String oldName, String newName) {
        assertThat(changes).anyMatch(c -> c.title().contains(oldName) && c.title().contains(newName));
    }

    @Then("one Change is produced for {string}")
    public void one_change_is_produced_for(String description) {
        assertThat(changes).anyMatch(c -> c.title().contains(description));
    }

    @Then("that Change reports {int} occurrences")
    public void that_change_reports_occurrences(int expectedOccurrences) {
        assertThat(latestMatchedChange().occurrenceCount()).isEqualTo(expectedOccurrences);
    }

    @Then("that Change reports {int} occurrence")
    public void that_change_reports_occurrence(int expectedOccurrences) {
        that_change_reports_occurrences(expectedOccurrences);
    }

    @Then("that Change reports {int} exceptions")
    public void that_change_reports_exceptions(int expectedExceptions) {
        assertThat(latestMatchedChange().exceptionCount()).isEqualTo(expectedExceptions);
    }

    @Then("that Change reports {int} exception")
    public void that_change_reports_exception(int expectedExceptions) {
        that_change_reports_exceptions(expectedExceptions);
    }

    @And("the Change's exceptions can be listed on their own")
    public void the_changes_exceptions_can_be_listed() {
        Change change = latestMatchedChange();
        assertThat(change.exceptions()).hasSize(change.exceptionCount());
        assertThat(change.exceptions()).allMatch(ex -> !change.matchedOccurrences().contains(ex));
    }

    private Change latestMatchedChange() {
        // The most recently asserted-about Change is whichever one the prior "one Change
        // is produced for..." step matched; since these scenarios each produce exactly one
        // Change of interest, returning the single grouped Change is unambiguous here.
        assertThat(changes).hasSize(1);
        return changes.get(0);
    }

    private DetectedTransformation renameTransformation(String oldName, String newName, int index) {
        return DetectedTransformation.of(TransformationKind.RENAME_SYMBOL,
                List.of(oldName + "#member", newName + "#member"),
                List.of("File" + index + ".java"));
    }
}
