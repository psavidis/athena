package com.athena.plugin.java;

import com.athena.semantic.ChangeCategory;
import com.athena.semantic.DetectedTransformation;
import com.athena.semantic.TransformationKind;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Steps for {@code control_flow_same_calls.feature} (ticket #319). */
public class SameCallsSteps {

    private final DetectionWorld world;
    private String lastSymbol;

    public SameCallsSteps(DetectionWorld world) {
        this.world = world;
    }

    @Given("a base revision where method {string} on class {string} writes each property in a simple loop")
    public void base_simple_loop(String method, String className) {
        write(world.baseRoot(), className, serializer(className, method,
                "        for (int i = 0; i < props.length; ++i) {\n"
                        + "            if (props[i] != null) {\n                out.write(props[i]);\n            }\n"
                        + "        }\n"));
    }

    @Given("a head revision where {string} on {string} writes the properties in a loop unrolled by two")
    public void head_unrolled_loop(String method, String className) {
        write(world.headRoot(), className, serializer(className, method,
                "        int i = 0;\n"
                        + "        for (; i + 1 < props.length; i += 2) {\n"
                        + "            if (props[i] != null) {\n                out.write(props[i]);\n            }\n"
                        + "            if (props[i + 1] != null) {\n                out.write(props[i + 1]);\n            }\n"
                        + "        }\n"
                        + "        if (i < props.length && props[i] != null) {\n            out.write(props[i]);\n        }\n"));
    }

    @Given("a head revision where {string} on {string} also logs each skipped property in the loop")
    public void head_loop_with_logging(String method, String className) {
        write(world.headRoot(), className, serializer(className, method,
                "        for (int i = 0; i < props.length; ++i) {\n"
                        + "            if (props[i] != null) {\n                out.write(props[i]);\n"
                        + "            } else {\n                out.log(i);\n            }\n"
                        + "        }\n"));
    }

    @Given("a head revision where {string} on {string} first rejects an empty property list by throwing")
    public void head_guard_throwing(String method, String className) {
        write(world.headRoot(), className, serializer(className, method,
                "        if (props.length == 0) {\n            throw new IllegalArgumentException();\n        }\n"
                        + "        for (int i = 0; i < props.length; ++i) {\n"
                        + "            if (props[i] != null) {\n                out.write(props[i]);\n            }\n"
                        + "        }\n"));
    }

    @Then("the control-flow change of {string} ends with {string}")
    public void the_control_flow_change_ends_with(String symbol, String suffix) {
        lastSymbol = symbol;
        assertThat(controlFlowChange(symbol).involvedDescriptions().get(1)).endsWith(suffix);
    }

    @Then("it is still categorised as Behavioral")
    public void it_is_still_behavioral() {
        assertThat(ChangeCategory.of(controlFlowChange(lastSymbol).kind())).isEqualTo(ChangeCategory.BEHAVIORAL);
    }

    @Then("the control-flow change of {string} does not mention {string}")
    public void the_control_flow_change_does_not_mention(String symbol, String text) {
        assertThat(controlFlowChange(symbol).involvedDescriptions().get(1)).doesNotContain(text);
    }

    private DetectedTransformation controlFlowChange(String symbol) {
        return world.transformations().orElseThrow().stream()
                .filter(t -> t.kind() == TransformationKind.CHANGE_CONTROL_FLOW)
                .filter(t -> t.involvedDescriptions().get(0).equals(symbol))
                .findFirst().orElseThrow();
    }

    private static String serializer(String className, String method, String body) {
        return "public class " + className + " {\n    void " + method + "(Object[] props, Out out) {\n" + body + "    }\n}\n";
    }

    private static void write(Path root, String className, String contents) {
        try {
            Files.writeString(root.resolve(className + ".java"), contents);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
