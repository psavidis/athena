package com.athena.plugins;

import com.athena.reviewui.PrUnderstandingView;
import com.athena.semantic.AnalysisResult;
import com.athena.semantic.AnalysisStatus;
import com.athena.semantic.Change;
import com.athena.semantic.ChangeCategory;
import com.athena.semantic.PrAnalyzer;
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
    private PrUnderstandingView understanding;

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

    @Given("a base and head revision where the only changed file parses cleanly")
    public void the_only_changed_file_parses_cleanly() {
        write(baseRoot, "Greeter", "public class Greeter {\n"
                + "    public String greet() {\n"
                + "        return \"hello\";\n"
                + "    }\n"
                + "}\n");
        write(headRoot, "Greeter", "public class Greeter {\n"
                + "    public String greet() {\n"
                + "        return \"hello\";\n"
                + "    }\n"
                + "    public String farewell() {\n"
                + "        return \"bye\";\n"
                + "    }\n"
                + "}\n");
    }

    @Given("an unchanged file elsewhere in the repository fails to parse in both revisions")
    public void an_unchanged_file_fails_to_parse_in_both_revisions() {
        brokenFileName = "legacy/Unparseable.java";
        String unparseable = "public class Unparseable {\n"
                + "    public void m( { this is not valid java\n";
        writeRaw(baseRoot, brokenFileName, unparseable);
        writeRaw(headRoot, brokenFileName, unparseable);
    }

    @Given("a base and head revision where one changed file fails to parse and the other changed files parse cleanly")
    public void one_changed_file_fails_to_parse() {
        one_file_fails_to_parse();
    }

    @Given("a base and head revision where a changed file uses a switch with type patterns and an unnamed pattern variable")
    public void a_changed_file_uses_modern_switch_syntax() {
        write(baseRoot, "Describer", "public class Describer {\n"
                + "    public String describe(Object value) {\n"
                + "        return \"unknown\";\n"
                + "    }\n"
                + "}\n");
        write(headRoot, "Describer", "public class Describer {\n"
                + "    public String describe(Object value) {\n"
                + "        return switch (value) {\n"
                + "            case Integer number -> \"number \" + number;\n"
                + "            case String _ -> \"text\";\n"
                + "            default -> \"unknown\";\n"
                + "        };\n"
                + "    }\n"
                + "    public String label() {\n"
                + "        return \"describer\";\n"
                + "    }\n"
                + "}\n");
    }

    // ---- behavioral changes in the analysis (ticket #263) ----

    @Given("a base revision where method {string} on class {string} checks {string}")
    public void base_method_checks_condition(String method, String className, String condition) {
        write(baseRoot, className, guardSource(className, method, condition));
    }

    @Given("a head revision where {string} on {string} checks {string}")
    public void head_method_checks_condition(String method, String className, String condition) {
        write(headRoot, className, guardSource(className, method, condition));
    }

    @Given("a base revision where method {string} on class {string} always decrements the size")
    public void base_method_always_decrements(String method, String className) {
        write(baseRoot, className, "public class " + className + " {\n"
                + "    private int size;\n"
                + "    void " + method + "(Node node) {\n"
                + "        size--;\n"
                + "    }\n"
                + "}\n");
    }

    @Given("a head revision where {string} on {string} first returns early when the entry is already removed")
    public void head_method_returns_early(String method, String className) {
        write(headRoot, className, "public class " + className + " {\n"
                + "    private int size;\n"
                + "    void " + method + "(Node node) {\n"
                + "        if (node.isRemoved()) {\n"
                + "            return;\n"
                + "        }\n"
                + "        size--;\n"
                + "    }\n"
                + "}\n");
    }

    @Given("a base revision where method {string} on class {string} halves both values once under an if")
    public void base_method_halves_once(String method, String className) {
        write(baseRoot, className, "public class " + className + " {\n"
                + "    int[] " + method + "(int n, int d) {\n"
                + "        if ((n & 1) == 0 && (d & 1) == 0) {\n"
                + "            n /= 2;\n"
                + "            d /= 2;\n"
                + "        }\n"
                + "        return new int[] {n, d};\n"
                + "    }\n"
                + "}\n");
    }

    @Given("a head revision where {string} on {string} halves both values in a while loop")
    public void head_method_halves_in_loop(String method, String className) {
        write(headRoot, className, "public class " + className + " {\n"
                + "    int[] " + method + "(int n, int d) {\n"
                + "        while ((n & 1) == 0 && (d & 1) == 0) {\n"
                + "            n /= 2;\n"
                + "            d /= 2;\n"
                + "        }\n"
                + "        return new int[] {n, d};\n"
                + "    }\n"
                + "}\n");
    }

    @Given("a revision pair with one changed condition and one renamed method")
    public void a_revision_pair_with_changed_condition_and_renamed_method() {
        base_method_checks_condition("isAllowed", "Guard", "user.isActive()");
        head_method_checks_condition("isAllowed", "Guard", "user.isActive() && user.hasPermission()");
        write(baseRoot, "Greeter", "public class Greeter {\n    public String greet() {\n        return \"hi\";\n    }\n}\n");
        write(headRoot, "Greeter", "public class Greeter {\n    public String salute() {\n        return \"hi\";\n    }\n}\n");
    }

    @Given("a base revision where method {string} on class {string} calls {string}")
    public void base_method_calls(String method, String className, String call) {
        write(baseRoot, className, callerSource(className, method, call));
    }

    @Given("a head revision where {string} on {string} instead calls {string} with no condition or branch change")
    public void head_method_instead_calls(String method, String className, String call) {
        write(headRoot, className, callerSource(className, method, call));
    }

    @When("the engine analyzes the revision pair")
    public void the_engine_analyzes_the_revision_pair() {
        PrAnalyzer prAnalyzer = new PrAnalyzer(PluginRegistry.languagePlugins(), PluginRegistry.frameworkPlugins());
        result = prAnalyzer.analyze(baseRoot, headRoot);
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

    @Then("no symbol-aware diff entry is reported for the unchanged file")
    public void no_symbol_aware_diff_entry_for_the_unchanged_file() {
        assertThat(result.symbolAwareDiffEntries())
                .noneMatch(entry -> entry.filePath().equals(brokenFileName));
    }

    @Then("a symbol-aware diff entry is reported for the changed file that failed to parse")
    public void a_symbol_aware_diff_entry_for_the_changed_file() {
        the_analysis_reports_symbol_aware_diff_entry();
    }

    @Then("the analysis reports the Changes detected in that file")
    public void the_analysis_reports_changes_in_that_file() {
        assertThat(result.changes())
                .anyMatch(change -> change.title().contains("Describer#label"));
    }

    @Then("the analysis reports a Behavioral Change on {string} described as a changed condition")
    public void a_behavioral_change_described_as_changed_condition(String method) {
        assertThat(behavioralChangeOn(method).title()).contains("condition changed");
    }

    @Then("the analysis reports a Behavioral Change on {string} described as an added branch")
    public void a_behavioral_change_described_as_added_branch(String method) {
        assertThat(behavioralChangeOn(method).title()).contains("branch added");
    }

    @Then("the analysis reports a Behavioral Change on {string}")
    public void a_behavioral_change_on(String method) {
        behavioralChangeOn(method);
    }

    @Then("that Change shows the method before and after the edit")
    public void that_change_shows_before_and_after() {
        Change change = result.changes().stream()
                .filter(c -> ChangeCategory.of(c.kind()) == ChangeCategory.BEHAVIORAL)
                .findFirst().orElseThrow();
        String diff = change.matchedOccurrences().get(0).diffText();
        assertThat(diff.lines()).anyMatch(line -> line.startsWith("-") && line.contains("user.isActive()"));
        assertThat(diff.lines()).anyMatch(line -> line.startsWith("+") && line.contains("user.hasPermission()"));
    }

    @When("the reviewer views the Change Map for it")
    public void the_reviewer_views_the_change_map() {
        the_engine_analyzes_the_revision_pair();
        understanding = PrUnderstandingView.of("Behavioral PR", result.changes());
    }

    @Then("the summary shows {int} Behavioral and {int} Structural Change")
    public void the_summary_shows_behavioral_and_structural(int behavioral, int structural) {
        assertThat(understanding.countFor(ChangeCategory.BEHAVIORAL)).isEqualTo(behavioral);
        assertThat(understanding.countFor(ChangeCategory.STRUCTURAL)).isEqualTo(structural);
    }

    @Then("the analysis reports no Behavioral Change on {string}")
    public void no_behavioral_change_on(String method) {
        assertThat(result.changes()).noneMatch(c -> ChangeCategory.of(c.kind()) == ChangeCategory.BEHAVIORAL
                && c.title().contains(method));
    }

    private Change behavioralChangeOn(String method) {
        return result.changes().stream()
                .filter(c -> ChangeCategory.of(c.kind()) == ChangeCategory.BEHAVIORAL && c.title().contains(method))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No Behavioral Change on " + method + " in " + result.changes()));
    }

    private static String guardSource(String className, String method, String condition) {
        return "public class " + className + " {\n"
                + "    public boolean " + method + "(User user) {\n"
                + "        if (" + condition + ") {\n"
                + "            return true;\n"
                + "        }\n"
                + "        return false;\n"
                + "    }\n"
                + "}\n";
    }

    private static String callerSource(String className, String method, String call) {
        return "public class " + className + " {\n"
                + "    public String " + method + "() {\n"
                + "        return " + call + ";\n"
                + "    }\n"
                + "    private String formatA() {\n"
                + "        return \"a\";\n"
                + "    }\n"
                + "    private String formatB() {\n"
                + "        return \"b\";\n"
                + "    }\n"
                + "}\n";
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
            Path target = root.resolve(fileName);
            Files.createDirectories(target.getParent());
            Files.writeString(target, content);
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
