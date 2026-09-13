package com.athena.semantic;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ModuleGroupingSteps {

    private List<Change> changes;
    private List<ModuleGroup> moduleGroups;

    @Given("Changes touching files under modules {string} and {string}")
    public void changes_touching_files_under_modules(String moduleA, String moduleB) {
        List<DetectedTransformation> transformations = new ArrayList<>();
        transformations.add(DetectedTransformation.of(TransformationKind.CHANGE_METHOD_SIGNATURE,
                List.of("DeviceConfiguration#deviceApplicationService"),
                List.of(moduleA + "/src/main/java/DeviceConfiguration.java")));
        transformations.add(DetectedTransformation.of(TransformationKind.ADD_SYMBOL,
                List.of("MeasurementApplicationService#record"),
                List.of(moduleB + "/src/main/java/MeasurementApplicationService.java")));
        changes = new ChangeGrouper().group(transformations);
    }

    @Given("a Change touching a file with no module segment")
    public void a_change_touching_a_file_with_no_module_segment() {
        List<DetectedTransformation> transformations = List.of(
                DetectedTransformation.of(TransformationKind.ADD_SYMBOL,
                        List.of("Greeter#greet"), List.of("Greeter.java")));
        changes = new ChangeGrouper().group(transformations);
    }

    @When("the semantic engine groups the Changes by module")
    public void group_changes_by_module() {
        moduleGroups = new ModuleGrouper().group(changes);
    }

    @Then("the module group for {string} contains those Changes")
    public void the_module_group_for_contains_those_changes(String moduleName) {
        ModuleGroup group = moduleGroups.stream()
                .filter(g -> g.moduleName().equals(moduleName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No module group found for " + moduleName));
        assertThat(group.changes()).isNotEmpty();
        assertThat(group.changes()).allSatisfy(change ->
                assertThat(change.matchedOccurrences().get(0).filesTouched().get(0)).startsWith(moduleName + "/"));
    }

    @Then("the module group for {string} contains that Change")
    public void the_module_group_for_contains_that_change(String moduleName) {
        assertThat(moduleGroups).anySatisfy(group -> assertThat(group.moduleName()).isEqualTo(moduleName));
        ModuleGroup group = moduleGroups.stream()
                .filter(g -> g.moduleName().equals(moduleName))
                .findFirst()
                .orElseThrow();
        assertThat(group.changes()).hasSize(1);
    }
}
