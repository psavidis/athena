package com.athena.semantic;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class GracefulDegradationSteps {

    private Path baseRoot;
    private Path headRoot;
    private String brokenFileName;
    private AnalysisResult result;

    @Before
    public void createTempRoots() throws IOException {
        baseRoot = Files.createTempDirectory("athena-degradation-base");
        headRoot = Files.createTempDirectory("athena-degradation-head");
    }

    @After
    public void cleanUpTempRoots() throws IOException {
        deleteRecursively(baseRoot);
        deleteRecursively(headRoot);
    }

    @Given("a base and head revision where every file parses and resolves cleanly")
    public void every_file_parses_cleanly() {
        write(baseRoot, "Greeter", "public class Greeter {\n"
                + "    public String greet() {\n"
                + "        return \"hello\";\n"
                + "    }\n"
                + "}\n");
        write(headRoot, "Greeter", "public class Greeter {\n"
                + "    public String greet() {\n"
                + "        return \"hello there\";\n"
                + "    }\n"
                + "}\n");
        write(baseRoot, "Farewell", "public class Farewell {\n}\n");
        write(headRoot, "Farewell", "public class Farewell {\n"
                + "    public String bye() {\n"
                + "        return \"bye\";\n"
                + "    }\n"
                + "}\n");
    }

    @Given("a base and head revision where one file fails to parse and the rest parse cleanly")
    public void one_file_fails_to_parse() {
        write(baseRoot, "Greeter", "public class Greeter {\n"
                + "    public String greet() {\n"
                + "        return \"hello\";\n"
                + "    }\n"
                + "}\n");
        write(headRoot, "Greeter", "public class Greeter {\n"
                + "    public String greet() {\n"
                + "        return \"hello there\";\n"
                + "    }\n"
                + "}\n");

        brokenFileName = "Broken.java";
        write(baseRoot, "Broken", "public class Broken {\n"
                + "    public void m() { }\n"
                + "}\n");
        writeRaw(headRoot, brokenFileName, "public class Broken {\n"
                + "    public void m( { this is not valid java\n");
    }

    @Given("a base and head revision where every file fails to parse")
    public void every_file_fails_to_parse() {
        writeRaw(baseRoot, "Broken.java", "public class Broken {\n"
                + "    public void m() { }\n"
                + "}\n");
        writeRaw(headRoot, "Broken.java", "public class Broken {\n"
                + "    public void m( { still not valid java\n");
    }

    @Given("a base and head revision with two files, both of which fail to parse")
    public void two_files_both_fail_to_parse() {
        writeRaw(baseRoot, "BrokenOne.java", "public class BrokenOne {\n"
                + "    public void m() { }\n"
                + "}\n");
        writeRaw(headRoot, "BrokenOne.java", "public class BrokenOne {\n"
                + "    public void m( { not valid java\n");
        writeRaw(baseRoot, "BrokenTwo.java", "public class BrokenTwo {\n"
                + "    public void n() { }\n"
                + "}\n");
        writeRaw(headRoot, "BrokenTwo.java", "public class BrokenTwo {\n"
                + "    public void n( { also not valid java\n");
    }

    @When("the engine analyzes the revision pair")
    public void the_engine_analyzes_the_revision_pair() {
        result = new PrAnalyzer().analyze(baseRoot, headRoot);
    }

    @Then("the analysis status is {string}")
    public void the_analysis_status_is(String expectedStatus) {
        assertThat(result.status()).isEqualTo(AnalysisStatus.valueOf(
                expectedStatus.toUpperCase().replace(' ', '_')));
    }

    @Then("the analysis reports the detected Changes")
    public void the_analysis_reports_detected_changes() {
        assertThat(result.changes()).isNotEmpty();
    }

    @Then("the analysis reports no detected Changes")
    public void the_analysis_reports_no_changes() {
        assertThat(result.changes()).isEmpty();
    }

    @Then("the analysis reports the Changes detected from the files that did parse")
    public void the_analysis_reports_changes_from_files_that_parsed() {
        assertThat(result.changes()).isNotEmpty();
    }

    @Then("the analysis reports a symbol-aware diff entry for the file that failed to parse")
    public void the_analysis_reports_symbol_aware_diff_entry() {
        assertThat(result.symbolAwareDiffEntries())
                .anyMatch(entry -> entry.filePath().equals(brokenFileName));
    }

    @Then("the raw textual diff is still available")
    public void the_raw_textual_diff_is_still_available() {
        assertThat(result.rawDiff()).isNotBlank();
    }

    @Then("the raw textual diff is still available for the file that failed to parse")
    public void the_raw_textual_diff_is_still_available_for_broken_file() {
        assertThat(result.rawDiffFor(brokenFileName)).isNotBlank();
    }

    @Then("a caller can retrieve the raw base\\/head diff for any file, independent of analysis status")
    public void a_caller_can_retrieve_the_raw_diff_independent_of_status() {
        assertThat(result.rawDiffFor("Broken.java")).isNotBlank();
    }

    private void write(Path root, String className, String content) {
        writeRaw(root, className + ".java", content);
    }

    private void writeRaw(Path root, String fileName, String content) {
        try {
            Files.writeString(root.resolve(fileName), content);
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
