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

/**
 * Steps for the package-move (ticket #335) and rename-and-move (ticket #336) scenarios of
 * {@code structural_change_detection.feature}.
 */
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

    @Given("a base revision where generic class {string} with type parameter {string} lives in package {string}")
    public void base_generic_class(String className, String typeParameter, String packageName) {
        writeSource(world.baseRoot(), packageName, className, cleanable(packageName, className, typeParameter, ""));
    }

    @Given("a head revision where it is renamed {string} with type parameter {string} in package {string}")
    public void head_renamed_generic_class(String className, String typeParameter, String packageName) {
        writeSource(world.headRoot(), packageName, className, cleanable(packageName, className, typeParameter, ""));
    }

    @Given("a head revision where it is renamed {string} with type parameter {string} in package {string} and gains a method")
    public void head_renamed_generic_class_with_new_method(String className, String typeParameter, String packageName) {
        writeSource(world.headRoot(), packageName, className, cleanable(packageName, className, typeParameter,
                "  public boolean isCleaned() {\n    return action == null;\n  }\n"));
    }

    @Then("no class removal or addition is reported")
    public void no_class_removal_or_addition() {
        assertThat(world.transformations().orElseThrow()).extracting(DetectedTransformation::kind)
                .doesNotContain(TransformationKind.REMOVE_CLASS, TransformationKind.ADD_CLASS);
    }

    @Then("no class rename is reported")
    public void no_class_rename() {
        assertThat(world.transformations().orElseThrow()).extracting(DetectedTransformation::kind)
                .doesNotContain(TransformationKind.RENAME_CLASS);
    }

    private static String cleanable(String packageName, String className, String t, String extra) {
        return "package " + packageName + ";\n\npublic class " + className + "<" + t + "> {\n"
                + "  private Action<" + t + "> action;\n"
                + "  public " + className + "(Object dispose) {\n  }\n"
                + "  protected final " + t + " get() {\n    Action<" + t + "> a = this.action;\n    return null;\n  }\n"
                + "  public " + className + "<" + t + "> self() {\n    return this;\n  }\n"
                + extra
                + "  static class Action<" + t + "> implements Runnable {\n"
                + "    public void run() {\n    }\n  }\n}\n";
    }

    private static void writeSource(Path root, String packageName, String className, String source) {
        try {
            Path file = root.resolve(packageName.replace('.', '/')).resolve(className + ".java");
            Files.createDirectories(file.getParent());
            Files.writeString(file, source);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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
