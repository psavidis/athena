package com.athena.plugin.java;

import com.athena.semantic.DetectedTransformation;
import com.athena.semantic.TransformationKind;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Steps for {@code switch_control_flow_detection.feature} (ticket #289): switch entries are
 * branches and their labels conditions. Detection itself runs through the shared
 * "the semantic engine detects transformations" step, via {@link DetectionWorld}.
 */
public class SwitchControlFlowSteps {

    private final DetectionWorld world;

    public SwitchControlFlowSteps(DetectionWorld world) {
        this.world = world;
    }

    @Given("a base revision where method {string} on class {string} picks a label with an if-chain over {string}, {string} and {string} with a final else")
    public void base_if_chain(String method, String className, String a, String b, String c) {
        write(world.baseRoot(), className, scope(className, method,
                "        if (state == " + a + ") {\n            return \"a\";\n"
                        + "        } else if (state == " + b + ") {\n            return \"b\";\n"
                        + "        } else if (state == " + c + ") {\n            return \"c\";\n"
                        + "        } else {\n            return \"?\";\n        }\n"));
    }

    @Given("a head revision where {string} on {string} picks the same labels with a switch over {string}, {string} and {string} with a default")
    public void head_same_switch(String method, String className, String a, String b, String c) {
        write(world.headRoot(), className, scope(className, method, switchStatement(List.of(a, b, c), "a", "b", "c")));
    }

    @Given("a base revision where method {string} on class {string} picks a label with an if-chain over {string} and {string} and falls back after it")
    public void base_if_chain_with_fallback(String method, String className, String a, String b) {
        write(world.baseRoot(), className, scope(className, method,
                "        if (state == " + a + ") {\n            return \"a\";\n"
                        + "        } else if (state == " + b + ") {\n            return \"b\";\n"
                        + "        }\n        return \"?\";\n"));
    }

    @Given("a base revision where method {string} on class {string} picks a label with an if-chain where one branch covers both {string} and {string}")
    public void base_if_chain_with_combined_condition(String method, String className, String a, String b) {
        write(world.baseRoot(), className, scope(className, method,
                "        if (state == EMPTY) {\n            return \"e\";\n"
                        + "        } else if (state == " + a + " || state == " + b + ") {\n            return \"x\";\n"
                        + "        } else {\n            return \"?\";\n        }\n"));
    }

    @Given("a head revision where {string} on {string} picks the same labels with a switch where {string} falls through to {string}")
    public void head_switch_with_fall_through(String method, String className, String a, String b) {
        write(world.headRoot(), className, scope(className, method,
                "        switch (state) {\n"
                        + "            case EMPTY:\n                return \"e\";\n"
                        + "            case " + a + ":\n"
                        + "            case " + b + ":\n                return \"x\";\n"
                        + "            default:\n                return \"?\";\n"
                        + "        }\n"));
    }

    @Given("a base revision where method {string} on class {string} picks a label with a switch over {string} and {string} with a default")
    public void base_switch_over_two(String method, String className, String a, String b) {
        write(world.baseRoot(), className, scope(className, method, switchStatement(List.of(a, b), "a", "b")));
    }

    @Given("a base revision where method {string} on class {string} picks a label with a switch over {string}, {string} and {string} with a default")
    public void base_switch_over_three(String method, String className, String a, String b, String c) {
        write(world.baseRoot(), className, scope(className, method, switchStatement(List.of(a, b, c), "a", "b", "c")));
    }

    @Given("a head revision where {string} on {string} picks a label with a switch over {string} and {string} with a default")
    public void head_switch_over_two(String method, String className, String a, String b) {
        write(world.headRoot(), className, scope(className, method, switchStatement(List.of(a, b), "a", "b")));
    }

    @Given("a head revision where {string} on {string} picks a label with a switch over {string}, {string} and {string} with a default")
    public void head_switch_over_three(String method, String className, String a, String b, String c) {
        write(world.headRoot(), className, scope(className, method, switchStatement(List.of(a, b, c), "a", "b", "c")));
    }

    @Given("a head revision where {string} on {string} picks different labels with a switch over {string} and {string} with a default")
    public void head_switch_with_different_bodies(String method, String className, String a, String b) {
        write(world.headRoot(), className, scope(className, method, switchStatement(List.of(a, b), "first", "second")));
    }

    @Given("a base revision where method {string} on class {string} returns a switch expression over {string} and {string} with a default")
    public void base_switch_expression_over_two(String method, String className, String a, String b) {
        write(world.baseRoot(), className, scope(className, method, switchExpression(List.of(a, b))));
    }

    @Given("a head revision where {string} on {string} returns a switch expression over {string}, {string} and {string} with a default")
    public void head_switch_expression_over_three(String method, String className, String a, String b, String c) {
        write(world.headRoot(), className, scope(className, method, switchExpression(List.of(a, b, c))));
    }

    @Then("the control-flow change of {string} is described as {string}")
    public void the_control_flow_change_is_described_as(String symbol, String description) {
        assertThat(controlFlowChangesInvolving(symbol)).singleElement()
                .satisfies(change -> assertThat(change.involvedDescriptions().get(1)).isEqualTo(description));
    }

    @Then("no control-flow change is detected involving {string}")
    public void no_control_flow_change_is_detected(String symbol) {
        assertThat(controlFlowChangesInvolving(symbol)).isEmpty();
    }

    private List<DetectedTransformation> controlFlowChangesInvolving(String symbol) {
        return world.transformations().orElseThrow().stream()
                .filter(t -> t.kind() == TransformationKind.CHANGE_CONTROL_FLOW)
                .filter(t -> t.involvedDescriptions().get(0).equals(symbol))
                .toList();
    }

    private static String switchStatement(List<String> labels, String... results) {
        StringBuilder body = new StringBuilder("        switch (state) {\n");
        for (int i = 0; i < labels.size(); i++) {
            body.append("            case ").append(labels.get(i)).append(":\n                return \"")
                    .append(results[i]).append("\";\n");
        }
        return body.append("            default:\n                return \"?\";\n        }\n").toString();
    }

    private static String switchExpression(List<String> labels) {
        StringBuilder body = new StringBuilder("        return switch (state) {\n");
        for (String label : labels) {
            body.append("            case ").append(label).append(" -> \"").append(label.toLowerCase()).append("\";\n");
        }
        return body.append("            default -> \"?\";\n        };\n").toString();
    }

    private static String scope(String className, String method, String body) {
        return "public class " + className + " {\n    static final int EMPTY = 0, OPEN = 1, CLOSED = 2;\n"
                + "    String " + method + "(int state) {\n" + body + "    }\n}\n";
    }

    private static void write(Path root, String className, String contents) {
        try {
            Files.writeString(root.resolve(className + ".java"), contents);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
