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

/** Steps for {@code override_annotation_is_not_a_contract_change.feature} (ticket #296). */
public class OverrideAnnotationSteps {

    private final DetectionWorld world;

    public OverrideAnnotationSteps(DetectionWorld world) {
        this.world = world;
    }

    @Given("a base revision where method {string} on class {string} has no annotations")
    public void base_method_without_annotations(String method, String className) {
        write(world.baseRoot(), className, source(className, method, ""));
    }

    @Given("a base revision where method {string} on class {string} is annotated {string}")
    public void base_method_annotated(String method, String className, String annotations) {
        write(world.baseRoot(), className, source(className, method, annotations + "\n    "));
    }

    @Given("a head revision where {string} is annotated {string}")
    public void head_method_annotated(String symbol, String annotations) {
        String[] parts = symbol.split("#");
        write(world.headRoot(), parts[0], source(parts[0], parts[1], annotations + "\n    "));
    }

    @Given("a head revision where {string} has no annotations")
    public void head_method_without_annotations(String symbol) {
        String[] parts = symbol.split("#");
        write(world.headRoot(), parts[0], source(parts[0], parts[1], ""));
    }

    @Then("the method annotation change of {string} reads {string}")
    public void the_method_annotation_change_reads(String symbol, String description) {
        assertThat(world.transformations().orElseThrow())
                .filteredOn(t -> t.kind() == TransformationKind.CHANGE_METHOD_ANNOTATIONS)
                .filteredOn(t -> t.involvedDescriptions().get(0).equals(symbol))
                .singleElement()
                .extracting(DetectedTransformation::involvedDescriptions)
                .satisfies(involved -> assertThat(involved.get(1)).isEqualTo(description));
    }

    private static String source(String className, String method, String annotations) {
        return "public class " + className + " extends Base {\n    " + annotations
                + "public Object " + method + "() {\n        return null;\n    }\n}\n";
    }

    private static void write(Path root, String className, String contents) {
        try {
            Files.writeString(root.resolve(className + ".java"), contents);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
