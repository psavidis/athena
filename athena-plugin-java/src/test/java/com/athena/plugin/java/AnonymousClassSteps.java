package com.athena.plugin.java;

import io.cucumber.java.en.Given;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Steps for {@code anonymous_class_member_detection.feature} (ticket #320). Detection and
 * assertions run through the shared detection steps via {@link DetectionWorld}.
 */
public class AnonymousClassSteps {

    private final DetectionWorld world;

    public AnonymousClassSteps(DetectionWorld world) {
        this.world = world;
    }

    @Given("a base revision where method {string} on class {string} returns an anonymous {string} with no fields")
    public void base_anonymous_class_without_fields(String method, String className, String type) {
        write(world.baseRoot(), className, factory(className, method, type, ""));
    }

    @Given("a base revision where the anonymous {string} in method {string} on class {string} has field {string}")
    public void base_anonymous_class_with_field(String type, String method, String className, String field) {
        write(world.baseRoot(), className, factory(className, method, type, "            private boolean " + field + ";\n"));
    }

    @Given("a base revision where method {string} on class {string} returns null")
    public void base_method_returns_null(String method, String className) {
        write(world.baseRoot(), className, "public class " + className + " {\n    Object " + method + "() {\n"
                + "        return null;\n    }\n}\n");
    }

    @Given("a head revision where that anonymous {string} in {string} gains field {string}")
    public void head_anonymous_class_gains_field(String type, String symbol, String field) {
        String[] parts = symbol.split("#");
        write(world.headRoot(), parts[0], factory(parts[0], parts[1], type, "            private boolean " + field + ";\n"));
    }

    @Given("a head revision where that anonymous {string} in {string} gains method {string}")
    public void head_anonymous_class_gains_method(String type, String symbol, String method) {
        String[] parts = symbol.split("#");
        write(world.headRoot(), parts[0], factory(parts[0], parts[1], type,
                "            void " + method + "() {\n            }\n"));
    }

    @Given("a head revision where that anonymous {string} in {string} has no fields")
    public void head_anonymous_class_without_fields(String type, String symbol) {
        String[] parts = symbol.split("#");
        write(world.headRoot(), parts[0], factory(parts[0], parts[1], type, ""));
    }

    @Given("a head revision where {string} on {string} returns an anonymous {string} with field {string}")
    public void head_method_returns_new_anonymous_class(String method, String className, String type, String field) {
        write(world.headRoot(), className, factory(className, method, type, "            private boolean " + field + ";\n"));
    }

    private static String factory(String className, String method, String type, String members) {
        return "public class " + className + " {\n    Object " + method + "() {\n"
                + "        return new " + type + "() {\n" + members
                + "            public String name() {\n                return \"n\";\n            }\n"
                + "        };\n    }\n}\n";
    }

    private static void write(Path root, String className, String contents) {
        try {
            Files.writeString(root.resolve(className + ".java"), contents);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
