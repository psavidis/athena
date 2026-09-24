package com.athena.plugin.java;

import io.cucumber.java.en.Given;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Steps for the reorder and return-value scenarios of {@code method_body_modification_detection.feature} (tickets #339, #351). */
public class BodyOrderSteps {

    private final DetectionWorld world;

    public BodyOrderSteps(DetectionWorld world) {
        this.world = world;
    }

    @Given("a base revision where method {string} on class {string} increments {string} before checking the index")
    public void base_increments_first(String method, String className, String counter) {
        write(world.baseRoot(), className, list(className, method,
                "        " + counter + "++;\n        checkInterval(index, 0, size());\n        size++;\n"));
    }

    @Given("a head revision where {string} on {string} checks the index before incrementing {string}")
    public void head_checks_first(String method, String className, String counter) {
        write(world.headRoot(), className, list(className, method,
                "        checkInterval(index, 0, size());\n        " + counter + "++;\n        size++;\n"));
    }

    @Given("a base revision where static method {string} on class {string} returns {string}")
    public void base_returns(String method, String className, String expression) {
        write(world.baseRoot(), className, strings(className, method, expression));
    }

    @Given("a head revision where static method {string} on {string} returns {string}")
    public void head_returns(String method, String className, String expression) {
        write(world.headRoot(), className, strings(className, method, expression));
    }

    @Given("a base revision where static method {string} on class {string} defines a supplier returning {string} and returns {string}")
    public void base_defines_supplier(String method, String className, String supplied, String returned) {
        write(world.baseRoot(), className, supplier(className, method, supplied, returned));
    }

    @Given("a head revision where static method {string} on {string} defines a supplier returning {string} and returns {string}")
    public void head_defines_supplier(String method, String className, String supplied, String returned) {
        write(world.headRoot(), className, supplier(className, method, supplied, returned));
    }

    @Given("a base revision where method {string} on class {string} stores {string} before returning it")
    public void base_stores_before_returning(String method, String className, String expression) {
        write(world.baseRoot(), className, calculator(className, method, expression));
    }

    @Given("a head revision where {string} on {string} stores {string} before returning it")
    public void head_stores_before_returning(String method, String className, String expression) {
        write(world.headRoot(), className, calculator(className, method, expression));
    }

    private static String calculator(String className, String method, String expression) {
        return "public class " + className + " {\n    int " + method + "(int a, int b) {\n"
                + "        int result = " + expression + ";\n        return result;\n    }\n}\n";
    }

    private static String supplier(String className, String method, String supplied, String returned) {
        return "public class " + className + " {\n    public static String " + method + "(String str, String first, String last) {\n"
                + "        java.util.function.Supplier<String> s = () -> { return " + supplied + "; };\n"
                + "        return " + returned + ";\n    }\n}\n";
    }

    private static String list(String className, String method, String body) {
        return "public class " + className + " {\n    int modCount;\n    int size;\n"
                + "    public void " + method + "(int index, Object obj) {\n" + body + "    }\n}\n";
    }

    private static String strings(String className, String method, String expression) {
        return "public class " + className + " {\n    public static String " + method + "(String str, int index) {\n"
                + "        return " + expression + ";\n    }\n}\n";
    }

    private static void write(Path root, String className, String contents) {
        try {
            Files.writeString(root.resolve(className + ".java"), contents);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
