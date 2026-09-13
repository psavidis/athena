package com.athena.plugin.java;

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

import static org.assertj.core.api.Assertions.assertThat;

public class BehavioralChangeDetectionSteps {

    private Path baseRoot;
    private Path headRoot;
    private List<DetectedBehavioralChange> changes;

    @Before
    public void createTempRoots() throws IOException {
        baseRoot = Files.createTempDirectory("athena-behavioral-base");
        headRoot = Files.createTempDirectory("athena-behavioral-head");
    }

    @After
    public void cleanUpTempRoots() throws IOException {
        deleteRecursively(baseRoot);
        deleteRecursively(headRoot);
    }

    @Given("a base revision where method {string} on class {string} has condition {string}")
    public void base_method_has_condition(String methodName, String className, String condition) {
        write(baseRoot, className, "public class " + className + " {\n"
                + "    public boolean " + methodName + "(User user) {\n"
                + "        if (" + condition + ") {\n"
                + "            return true;\n"
                + "        }\n"
                + "        return false;\n"
                + "    }\n"
                + "}\n");
    }

    @Given("a head revision where {string} on {string} has condition {string}")
    public void head_method_has_condition(String methodName, String className, String condition) {
        write(headRoot, className, "public class " + className + " {\n"
                + "    public boolean " + methodName + "(User user) {\n"
                + "        if (" + condition + ") {\n"
                + "            return true;\n"
                + "        }\n"
                + "        return false;\n"
                + "    }\n"
                + "}\n");
    }

    @Given("a base revision where method {string} on class {string} has only an if-branch")
    public void base_method_has_only_if_branch(String methodName, String className) {
        write(baseRoot, className, "public class " + className + " {\n"
                + "    public String " + methodName + "(int score) {\n"
                + "        if (score > 50) {\n"
                + "            return \"pass\";\n"
                + "        }\n"
                + "        return \"unknown\";\n"
                + "    }\n"
                + "}\n");
    }

    @Given("a head revision where {string} on {string} has an additional else-branch")
    public void head_method_has_additional_else_branch(String methodName, String className) {
        write(headRoot, className, "public class " + className + " {\n"
                + "    public String " + methodName + "(int score) {\n"
                + "        if (score > 50) {\n"
                + "            return \"pass\";\n"
                + "        } else {\n"
                + "            return \"fail\";\n"
                + "        }\n"
                + "    }\n"
                + "}\n");
    }

    @Given("a base revision where method {string} on class {string} has no loop")
    public void base_method_has_no_loop(String methodName, String className) {
        write(baseRoot, className, "public class " + className + " {\n"
                + "    public int " + methodName + "(java.util.List<Integer> input) {\n"
                + "        return input.size();\n"
                + "    }\n"
                + "}\n");
    }

    @Given("a head revision where {string} on {string} has an added loop over the input")
    public void head_method_has_added_loop(String methodName, String className) {
        write(headRoot, className, "public class " + className + " {\n"
                + "    public int " + methodName + "(java.util.List<Integer> input) {\n"
                + "        int total = 0;\n"
                + "        for (int value : input) {\n"
                + "            total += value;\n"
                + "        }\n"
                + "        return total;\n"
                + "    }\n"
                + "}\n");
    }

    @Given("a base revision where method {string} on class {string} calls {string}")
    public void base_method_calls(String methodName, String className, String call) {
        String callee = call.replace("()", "");
        write(baseRoot, className, "public class " + className + " {\n"
                + "    public String " + methodName + "() {\n"
                + "        return " + callee + "();\n"
                + "    }\n"
                + "    private String formatA() { return \"a\"; }\n"
                + "    private String formatB() { return \"b\"; }\n"
                + "}\n");
    }

    @Given("a head revision where {string} on {string} instead calls {string} with no condition or branch change")
    public void head_method_calls_different(String methodName, String className, String call) {
        String callee = call.replace("()", "");
        write(headRoot, className, "public class " + className + " {\n"
                + "    public String " + methodName + "() {\n"
                + "        return " + callee + "();\n"
                + "    }\n"
                + "    private String formatA() { return \"a\"; }\n"
                + "    private String formatB() { return \"b\"; }\n"
                + "}\n");
    }

    @Given("a base revision where method {string} on class {string} returns {string}")
    public void base_method_returns(String methodName, String className, String value) {
        write(baseRoot, className, "public class " + className + " {\n"
                + "    public String " + methodName + "() {\n"
                + "        return \"" + value + "\";\n"
                + "    }\n"
                + "}\n");
    }

    @Given("a head revision where {string} on {string} returns {string} with no condition or branch change")
    public void head_method_returns(String methodName, String className, String value) {
        write(headRoot, className, "public class " + className + " {\n"
                + "    public String " + methodName + "() {\n"
                + "        return \"" + value + "\";\n"
                + "    }\n"
                + "}\n");
    }

    @When("the semantic engine detects behavioral changes between the revisions")
    public void detect_behavioral_changes() {
        changes = new BehavioralChangeDetector().detect(baseRoot, headRoot);
    }

    @Then("a behavioral change is detected involving {string}")
    public void a_behavioral_change_is_detected(String symbol) {
        assertThat(changes).anyMatch(c -> c.symbolDescription().contains(symbol));
    }

    @Then("no behavioral change is detected involving {string}")
    public void no_behavioral_change_is_detected(String symbol) {
        assertThat(changes).noneMatch(c -> c.symbolDescription().contains(symbol));
    }

    private void write(Path root, String className, String contents) {
        try {
            Files.writeString(root.resolve(className + ".java"), contents);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void deleteRecursively(Path root) throws IOException {
        if (root == null || !Files.exists(root)) return;
        try (var walk = Files.walk(root)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }
}
