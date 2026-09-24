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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Steps for {@code field_value_change_detection.feature} (ticket #316). */
public class FieldValueSteps {

    private final DetectionWorld world;

    public FieldValueSteps(DetectionWorld world) {
        this.world = world;
    }

    @Given("a base revision where class {string} has field {string} initialized to {string}")
    public void base_field_initialized_to(String className, String field, String initializer) {
        write(world.baseRoot(), className, source(className, field, initializer));
    }

    @Given("a head revision where field {string} is initialized to {string}")
    public void head_field_initialized_to(String symbol, String initializer) {
        String[] parts = symbol.split("#");
        write(world.headRoot(), parts[0], source(parts[0], parts[1], initializer));
    }

    @Then("the value change of {string} reads {string}")
    public void the_value_change_reads(String symbol, String description) {
        assertThat(valueChangesOf(symbol)).singleElement()
                .satisfies(change -> assertThat(change.involvedDescriptions().get(1)).isEqualTo(description));
    }

    @Then("the value change of {string} is categorised as Unknown")
    public void the_value_change_is_unknown(String symbol) {
        assertThat(valueChangesOf(symbol)).singleElement()
                .satisfies(change -> assertThat(ChangeCategory.of(change.kind())).isEqualTo(ChangeCategory.UNKNOWN));
    }

    private List<DetectedTransformation> valueChangesOf(String symbol) {
        return world.transformations().orElseThrow().stream()
                .filter(t -> t.kind() == TransformationKind.CHANGE_FIELD_VALUE)
                .filter(t -> t.involvedDescriptions().get(0).equals(symbol))
                .toList();
    }

    private static String source(String className, String field, String initializer) {
        String type = initializer.startsWith("{") ? "Object[]" : "Object";
        return "public class " + className + " {\n    static final " + type + " " + field + " = " + initializer + ";\n}\n";
    }

    private static void write(Path root, String className, String contents) {
        try {
            Files.writeString(root.resolve(className + ".java"), contents);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
