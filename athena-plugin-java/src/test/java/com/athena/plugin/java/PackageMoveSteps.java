package com.athena.plugin.java;

import io.cucumber.java.en.Given;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Steps for the package-move scenario of {@code structural_change_detection.feature} (ticket #335). */
public class PackageMoveSteps {

    private final DetectionWorld world;

    public PackageMoveSteps(DetectionWorld world) {
        this.world = world;
    }

    @Given("a base revision where class {string} lives in package {string}")
    public void base_class_in_package(String className, String packageName) {
        write(world.baseRoot(), packageName, className);
    }

    @Given("a head revision where {string} lives unchanged in package {string}")
    public void head_class_in_package(String className, String packageName) {
        write(world.headRoot(), packageName, className);
    }

    private static void write(Path root, String packageName, String className) {
        try {
            Path file = root.resolve(packageName.replace('.', '/')).resolve(className + ".java");
            Files.createDirectories(file.getParent());
            Files.writeString(file, "package " + packageName + ";\n\npublic class " + className + " {\n"
                    + "    void start() {\n    }\n}\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
