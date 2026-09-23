package com.athena.plugin.java;

import com.athena.semantic.TransformationKind;
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

    private final DetectionWorld world;
    private List<DetectedBehavioralChange> changes;

    public BehavioralChangeDetectionSteps(DetectionWorld world) {
        this.world = world;
    }

    @Given("a base revision where method {string} on class {string} has condition {string}")
    public void base_method_has_condition(String methodName, String className, String condition) {
        write(world.baseRoot(), className, "public class " + className + " {\n"
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
        write(world.headRoot(), className, "public class " + className + " {\n"
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
        write(world.baseRoot(), className, "public class " + className + " {\n"
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
        write(world.headRoot(), className, "public class " + className + " {\n"
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
        write(world.baseRoot(), className, "public class " + className + " {\n"
                + "    public int " + methodName + "(java.util.List<Integer> input) {\n"
                + "        return input.size();\n"
                + "    }\n"
                + "}\n");
    }

    @Given("a head revision where {string} on {string} has an added loop over the input")
    public void head_method_has_added_loop(String methodName, String className) {
        write(world.headRoot(), className, "public class " + className + " {\n"
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
        write(world.baseRoot(), className, "public class " + className + " {\n"
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
        write(world.headRoot(), className, "public class " + className + " {\n"
                + "    public String " + methodName + "() {\n"
                + "        return " + callee + "();\n"
                + "    }\n"
                + "    private String formatA() { return \"a\"; }\n"
                + "    private String formatB() { return \"b\"; }\n"
                + "}\n");
    }

    @Given("a base revision where method {string} on class {string} returns {string}")
    public void base_method_returns(String methodName, String className, String value) {
        write(world.baseRoot(), className, "public class " + className + " {\n"
                + "    public String " + methodName + "() {\n"
                + "        return \"" + value + "\";\n"
                + "    }\n"
                + "}\n");
    }

    @Given("a head revision where {string} on {string} returns {string} with no condition or branch change")
    public void head_method_returns(String methodName, String className, String value) {
        write(world.headRoot(), className, "public class " + className + " {\n"
                + "    public String " + methodName + "() {\n"
                + "        return \"" + value + "\";\n"
                + "    }\n"
                + "}\n");
    }

    @When("the semantic engine detects behavioral changes between the revisions")
    public void detect_behavioral_changes() {
        changes = new BehavioralChangeDetector().detect(world.baseRoot(), world.headRoot());
    }

    @Then("a behavioral change is detected involving {string}")
    public void a_behavioral_change_is_detected(String symbol) {
        assertThat(behavioralSymbols()).anyMatch(description -> description.contains(symbol));
    }

    @Then("no behavioral change is detected involving {string}")
    public void no_behavioral_change_is_detected(String symbol) {
        assertThat(behavioralSymbols()).noneMatch(description -> description.contains(symbol));
    }

    /**
     * The methods found to have changed behaviorally — by this class's own "When", or, in a
     * scenario whose "When" ran the full detector instead, its control-flow transformations.
     */
    private List<String> behavioralSymbols() {
        if (changes != null) {
            return changes.stream().map(DetectedBehavioralChange::symbolDescription).toList();
        }
        return world.transformations().orElseThrow(() -> new IllegalStateException("No detection ran"))
                .stream()
                .filter(t -> t.kind() == TransformationKind.CHANGE_CONTROL_FLOW)
                .map(t -> t.involvedDescriptions().get(0))
                .toList();
    }

    private void write(Path root, String className, String contents) {
        try {
            Files.writeString(root.resolve(className + ".java"), contents);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
