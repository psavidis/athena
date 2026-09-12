package com.athena.reviewui;

import com.athena.semantic.Change;
import com.athena.semantic.ChangeGrouper;
import com.athena.semantic.DetectedTransformation;
import com.athena.semantic.TransformationDetector;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class DrillDownNavigationSteps {

    private Path baseRoot;
    private Path headRoot;
    private Change theChange;
    private List<Change> allChanges;
    private ChangeDetailView detailView;
    private Optional<Change> owningChange;

    @Before
    public void createTempRoots() throws IOException {
        baseRoot = Files.createTempDirectory("athena-drilldown-base");
        headRoot = Files.createTempDirectory("athena-drilldown-head");
    }

    @After
    public void cleanUpTempRoots() throws IOException {
        deleteRecursively(baseRoot);
        deleteRecursively(headRoot);
    }

    @Given("a Change representing a rename between two symbols")
    public void a_change_representing_a_rename_between_two_symbols() {
        writeRenameFixture("Greeter", "greet", "salute");
        theChange = detectFirstChange();
        allChanges = List.of(theChange);
    }

    @Given("a Change representing a rename that touches file {string}")
    public void a_change_representing_a_rename_that_touches_file(String fileName) {
        String className = fileName.replace(".java", "");
        writeRenameFixture(className, "greet", "salute");
        theChange = detectFirstChange();
        allChanges = List.of(theChange);
    }

    @Given("a Change representing a mechanical replacement")
    public void a_change_representing_a_mechanical_replacement() {
        writeRenameFixture("Greeter", "greet", "salute");
        theChange = detectFirstChange();
        allChanges = List.of(theChange);
    }

    @When("the reviewer opens the Change's detail view")
    public void the_reviewer_opens_the_changes_detail_view() {
        detailView = ChangeDetailView.of(theChange);
    }

    @When("the reviewer looks up which Change owns file {string}")
    public void the_reviewer_looks_up_which_change_owns_file(String fileName) {
        owningChange = ChangeOwnership.findOwningChange(allChanges, fileName);
    }

    @Then("the detail view shows the Change's category")
    public void the_detail_view_shows_the_changes_category() {
        assertThat(detailView.category()).isNotNull();
    }

    @Then("the detail view shows the Change's description")
    public void the_detail_view_shows_the_changes_description() {
        assertThat(detailView.description()).isNotBlank();
    }

    @Then("the detail view lists the involved symbols")
    public void the_detail_view_lists_the_involved_symbols() {
        assertThat(detailView.symbols()).isNotEmpty();
    }

    @Then("the detail view lists the touched files")
    public void the_detail_view_lists_the_touched_files() {
        assertThat(detailView.files()).isNotEmpty();
    }

    @Then("the owning Change is the rename Change")
    public void the_owning_change_is_the_rename_change() {
        assertThat(owningChange).isPresent();
        assertThat(owningChange.get()).isEqualTo(theChange);
    }

    @Then("there is no owning Change")
    public void there_is_no_owning_change() {
        assertThat(owningChange).isEmpty();
    }

    @Then("the detail view still exposes the Change's underlying evidence")
    public void the_detail_view_still_exposes_the_changes_underlying_evidence() {
        assertThat(detailView.files()).isNotEmpty();
        assertThat(detailView.symbols()).isNotEmpty();
    }

    private void writeRenameFixture(String className, String oldMethodName, String newMethodName) {
        write(baseRoot, className, "public class " + className + " {\n"
                + "    public String " + oldMethodName + "() {\n"
                + "        return \"hi\";\n"
                + "    }\n"
                + "}\n");
        write(headRoot, className, "public class " + className + " {\n"
                + "    public String " + newMethodName + "() {\n"
                + "        return \"hi\";\n"
                + "    }\n"
                + "}\n");
    }

    private Change detectFirstChange() {
        List<DetectedTransformation> transformations = new TransformationDetector().detect(baseRoot, headRoot);
        return new ChangeGrouper().group(transformations).get(0);
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
