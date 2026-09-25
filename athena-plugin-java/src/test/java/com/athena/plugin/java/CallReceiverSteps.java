package com.athena.plugin.java;

import io.cucumber.java.en.Given;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Steps for {@code call_receiver_in_body_summary.feature} (ticket #360): a method with a given body. */
public class CallReceiverSteps {

    private final DetectionWorld world;

    public CallReceiverSteps(DetectionWorld world) {
        this.world = world;
    }

    @Given("a base revision where method {string} on class {string} has the body {string}")
    public void base_has_body(String method, String className, String body) {
        write(world.baseRoot(), className, withBody(className, method, body));
    }

    @Given("a head revision where {string} on {string} has the body {string}")
    public void head_has_body(String method, String className, String body) {
        write(world.headRoot(), className, withBody(className, method, body));
    }

    private static String withBody(String className, String method, String body) {
        return "public class " + className + " {\n    int size;\n"
                + "    public void " + method + "() {\n        " + body + "\n    }\n}\n";
    }

    private static void write(Path root, String className, String contents) {
        try {
            Files.writeString(root.resolve(className + ".java"), contents);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
