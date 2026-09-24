package com.athena.plugin.java;

import io.cucumber.java.en.Given;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Steps for {@code constructor_parameter_detection.feature} (tickets #86, #294): a constructor
 * parameter stored into the same-named field, however the stored expression uses it.
 */
public class ConstructorParameterSteps {

    private final DetectionWorld world;

    public ConstructorParameterSteps(DetectionWorld world) {
        this.world = world;
    }

    @Given("a base revision where class {string} has a field {string} and no constructor")
    public void base_class_with_field_and_no_constructor(String className, String field) {
        write(world.baseRoot(), className, "public class " + className + " {\n    private Object " + field + ";\n}\n");
    }

    @Given("a head revision where {string} stores constructor parameter {string} as {string}")
    public void head_constructor_stores_parameter(String className, String parameter, String statements) {
        write(world.headRoot(), className, "public class " + className + " {\n    private Object " + parameter + ";\n"
                + "    " + className + "(Object " + parameter + ") {\n        " + statements + "\n    }\n}\n");
    }

    private static void write(Path root, String className, String contents) {
        try {
            Files.writeString(root.resolve(className + ".java"), contents);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
