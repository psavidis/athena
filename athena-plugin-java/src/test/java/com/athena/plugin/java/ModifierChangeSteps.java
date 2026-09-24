package com.athena.plugin.java;

import com.athena.semantic.DetectedTransformation;
import com.athena.semantic.TransformationKind;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Steps for {@code modifier_change_detection.feature} (ticket #313). */
public class ModifierChangeSteps {

    private final DetectionWorld world;

    public ModifierChangeSteps(DetectionWorld world) {
        this.world = world;
    }

    @Given("a base revision where class {string} has method {string} with modifiers {string}")
    public void base_method_with_modifiers(String className, String method, String modifiers) {
        write(world.baseRoot(), className, "public class " + className + " {\n    " + prefix(modifiers)
                + "void " + method + "() {\n        System.out.println(1);\n    }\n}\n");
    }

    @Given("a head revision where {string} has modifiers {string}")
    public void head_method_with_modifiers(String symbol, String modifiers) {
        String[] parts = symbol.split("#");
        write(world.headRoot(), parts[0], "public class " + parts[0] + " {\n    " + prefix(modifiers)
                + "void " + parts[1] + "() {\n        System.out.println(1);\n    }\n}\n");
    }

    @Given("a head revision where {string} has modifiers {string} and a changed body")
    public void head_method_with_modifiers_and_changed_body(String symbol, String modifiers) {
        String[] parts = symbol.split("#");
        write(world.headRoot(), parts[0], "public class " + parts[0] + " {\n    " + prefix(modifiers)
                + "void " + parts[1] + "() {\n        System.out.println(2);\n    }\n}\n");
    }

    @Given("a base revision where class {string} has field {string} with modifiers {string}")
    public void base_field_with_modifiers(String className, String field, String modifiers) {
        write(world.baseRoot(), className, "public class " + className + " {\n    " + prefix(modifiers)
                + "Object " + field + " = null;\n}\n");
    }

    @Given("a head revision where field {string} has modifiers {string}")
    public void head_field_with_modifiers(String symbol, String modifiers) {
        String[] parts = symbol.split("#");
        write(world.headRoot(), parts[0], "public class " + parts[0] + " {\n    " + prefix(modifiers)
                + "Object " + parts[1] + " = null;\n}\n");
    }

    @Given("a base revision where class {string} has modifiers {string}")
    public void base_class_with_modifiers(String className, String modifiers) {
        write(world.baseRoot(), className, prefix(modifiers) + "class " + className + " {\n    void run() {\n    }\n}\n");
    }

    @Given("a head revision where class {string} has modifiers {string}")
    public void head_class_with_modifiers(String className, String modifiers) {
        write(world.headRoot(), className, prefix(modifiers) + "class " + className + " {\n    void run() {\n    }\n}\n");
    }

    @Given("a base revision where class {string} has a constructor with modifiers {string}")
    public void base_constructor_with_modifiers(String className, String modifiers) {
        write(world.baseRoot(), className, "public class " + className + " {\n    " + prefix(modifiers)
                + className + "() {\n    }\n}\n");
    }

    @Given("a head revision where the constructor of {string} has modifiers {string}")
    public void head_constructor_with_modifiers(String className, String modifiers) {
        write(world.headRoot(), className, "public class " + className + " {\n    " + prefix(modifiers)
                + className + "() {\n    }\n}\n");
    }

    @Then("the modifier change of {string} reads {string}")
    public void the_modifier_change_reads(String symbol, String description) {
        assertThat(world.transformations().orElseThrow())
                .filteredOn(t -> t.kind() == TransformationKind.CHANGE_MODIFIERS)
                .filteredOn(t -> t.involvedDescriptions().get(0).equals(symbol))
                .singleElement()
                .extracting(DetectedTransformation::involvedDescriptions)
                .satisfies(involved -> assertThat(involved.get(1)).isEqualTo(description));
    }

    private static String prefix(String modifiers) {
        return modifiers.isEmpty() ? "" : modifiers + " ";
    }

    private static void write(Path root, String className, String contents) {
        try {
            Files.writeString(root.resolve(className + ".java"), contents);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
