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

    @Given("a class {string} whose only change is importing {string} from its new package")
    public void a_class_following_the_move(String className, String movedClass) {
        writeSource(world.baseRoot(), "io.vertx.core.datagram", className, consumer(className,
                "import io.vertx.core.impl." + movedClass + ";\n", movedClass));
        writeSource(world.headRoot(), "io.vertx.core.datagram", className, consumer(className,
                "import io.vertx.core.internal." + movedClass + ";\n", movedClass));
    }

    @Given("a class {string} whose only change is an added import of {string}")
    public void a_class_with_an_unrelated_import(String className, String imported) {
        writeSource(world.baseRoot(), "io.vertx.core.other", className, consumer(className, "", "Object"));
        writeSource(world.headRoot(), "io.vertx.core.other", className, consumer(className,
                "import " + imported + ";\n", "Object"));
    }

    @Given("a class {string} whose only change is extending {string} instead of {string}")
    public void a_class_following_the_rename(String className, String newName, String oldName) {
        writeSource(world.baseRoot(), "io.vertx.core.impl", className, "package io.vertx.core.impl;\n\n"
                + "class " + className + " extends " + oldName + "<Object> {\n  " + className + "() {\n    super(null);\n  }\n}\n");
        writeSource(world.headRoot(), "io.vertx.core.impl", className, "package io.vertx.core.impl;\n\n"
                + "import io.vertx.core.internal." + newName + ";\n\n"
                + "class " + className + " extends " + newName + "<Object> {\n  " + className + "() {\n    super(null);\n  }\n}\n");
    }

    @Then("that move touches the file of {string}")
    public void that_move_touches_the_file_of(String className) {
        assertThat(world.transformations().orElseThrow())
                .filteredOn(t -> t.kind() == TransformationKind.MOVE_CLASS)
                .singleElement()
                .satisfies(move -> assertThat(move.filesTouched()).anyMatch(file -> file.endsWith("/" + className + ".java")));
    }

    private static String consumer(String className, String imports, String usedType) {
        return "package io.vertx.core.x;\n\n" + imports + "\npublic class " + className + " {\n"
                + "  private " + usedType + " resource;\n}\n";
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
