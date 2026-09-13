package com.athena.semantic;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class StructuralChangeDetectionSteps {

    private Path baseRoot;
    private Path headRoot;
    private List<DetectedTransformation> transformations;

    @Before
    public void createTempRoots() throws IOException {
        baseRoot = Files.createTempDirectory("athena-base");
        headRoot = Files.createTempDirectory("athena-head");
    }

    @After
    public void cleanUpTempRoots() throws IOException {
        deleteRecursively(baseRoot);
        deleteRecursively(headRoot);
    }

    // ---- base revision setups ----

    @Given("a base revision where class {string} has a method {string}")
    public void base_class_has_method(String className, String methodName) {
        write(baseRoot, className, "public class " + className + " {\n"
                + "    public String " + methodName + "() {\n"
                + "        return \"greeting\";\n"
                + "    }\n"
                + "}\n");
    }

    @Given("a base revision where class {string} has a method {string} and class {string} is empty")
    public void base_class_has_method_and_other_class_empty(String classA, String methodName, String classB) {
        write(baseRoot, classA, "public class " + classA + " {\n"
                + "    public String " + methodName + "() {\n"
                + "        return \"greeting\";\n"
                + "    }\n"
                + "}\n");
        write(baseRoot, classB, "public class " + classB + " {\n}\n");
    }

    @Given("a base revision where class {string} has no method {string}")
    public void base_class_has_no_method(String className, String methodName) {
        write(baseRoot, className, "public class " + className + " {\n}\n");
    }

    @Given("a base revision where class {string} has a method {string} taking no parameters")
    public void base_class_has_method_no_params(String className, String methodName) {
        write(baseRoot, className, "public class " + className + " {\n"
                + "    public String " + methodName + "() {\n"
                + "        return \"greeting\";\n"
                + "    }\n"
                + "}\n");
    }

    @Given("a base revision where record {string} has components {string}")
    public void base_revision_record_has_components(String recordName, String components) {
        write(baseRoot, recordName, "public record " + recordName + "(" + components + ") {\n}\n");
    }

    @Given("a base revision where class {string} has a method {string} with an inline fragment")
    public void base_class_has_method_with_inline_fragment(String className, String methodName) {
        write(baseRoot, className, "public class " + className + " {\n"
                + "    public String " + methodName + "() {\n"
                + "        String greeting = \"Hello, \" + \"world\" + \"!\";\n"
                + "        return greeting;\n"
                + "    }\n"
                + "}\n");
    }

    @Given("a base revision where {int} files each reference the identifier {string}")
    public void base_revision_with_files_referencing_identifier(int fileCount, String identifier) {
        for (int i = 0; i < fileCount; i++) {
            String className = "Ref" + i;
            write(baseRoot, className, "public class " + className + " {\n"
                    + "    public " + identifier + " make() {\n"
                    + "        return new " + identifier + "();\n"
                    + "    }\n"
                    + "}\n");
            // A minimal stand-in declaration for the referenced identifier, so
            // each file is independently parseable/compilable in spirit.
        }
        write(baseRoot, identifier, "public class " + identifier + " {\n}\n");
    }

    @Given("a base revision where class {string} has a method {string} that calls {string}")
    public void base_class_has_method_calling(String className, String methodName, String call) {
        String calleeName = call.replace("()", "");
        write(baseRoot, className, "public class " + className + " {\n"
                + "    public String " + methodName + "() {\n"
                + "        return " + calleeName + "();\n"
                + "    }\n"
                + "    private String " + calleeName + "() {\n"
                + "        return \"a\";\n"
                + "    }\n"
                + "    private String formatB() {\n"
                + "        return \"b\";\n"
                + "    }\n"
                + "}\n");
    }

    // ---- head revision setups ----

    @Given("a head revision where class {string} has a method {string} with the same body")
    public void head_class_has_renamed_method(String className, String methodName) {
        write(headRoot, className, "public class " + className + " {\n"
                + "    public String " + methodName + "() {\n"
                + "        return \"greeting\";\n"
                + "    }\n"
                + "}\n");
    }

    @Given("a head revision where class {string} has a method {string} with the same body and class {string} is empty")
    public void head_class_has_method_and_other_empty(String classWithMethod, String methodName, String emptyClass) {
        write(headRoot, classWithMethod, "public class " + classWithMethod + " {\n"
                + "    public String " + methodName + "() {\n"
                + "        return \"greeting\";\n"
                + "    }\n"
                + "}\n");
        write(headRoot, emptyClass, "public class " + emptyClass + " {\n}\n");
    }

    @Given("a head revision where class {string} has an additional method {string}")
    public void head_class_has_additional_method(String className, String methodName) {
        write(headRoot, className, "public class " + className + " {\n"
                + "    public String " + methodName + "() {\n"
                + "        return \"farewell\";\n"
                + "    }\n"
                + "}\n");
    }

    @Given("a head revision where class {string} no longer has the method {string}")
    public void head_class_no_longer_has_method(String className, String methodName) {
        write(headRoot, className, "public class " + className + " {\n}\n");
    }

    @Given("a head revision where class {string} has a method {string} taking a {string} parameter")
    public void head_class_has_method_with_parameter(String className, String methodName, String paramType) {
        write(headRoot, className, "public class " + className + " {\n"
                + "    public String " + methodName + "(" + paramType + " arg) {\n"
                + "        return \"greeting\" + arg;\n"
                + "    }\n"
                + "}\n");
    }

    @Given("a head revision where record {string} has components {string}")
    public void head_revision_record_has_components(String recordName, String components) {
        write(headRoot, recordName, "public record " + recordName + "(" + components + ") {\n}\n");
    }

    @Given("a head revision where that fragment has been extracted into a new method {string} called from {string}")
    public void head_class_has_extracted_method(String extractedMethod, String callingMethod) {
        write(headRoot, "Greeter", "public class Greeter {\n"
                + "    public String " + callingMethod + "() {\n"
                + "        return " + extractedMethod + "();\n"
                + "    }\n"
                + "    public String " + extractedMethod + "() {\n"
                + "        String greeting = \"Hello, \" + \"world\" + \"!\";\n"
                + "        return greeting;\n"
                + "    }\n"
                + "}\n");
    }

    @Given("a head revision where every occurrence of {string} has been replaced with {string}")
    public void head_revision_with_identifier_replaced(String oldIdentifier, String newIdentifier) {
        // Mirror however many Ref files were created in base, with the identifier swapped.
        long refCount = countBaseRefFiles();
        for (int i = 0; i < refCount; i++) {
            String className = "Ref" + i;
            write(headRoot, className, "public class " + className + " {\n"
                    + "    public " + newIdentifier + " make() {\n"
                    + "        return new " + newIdentifier + "();\n"
                    + "    }\n"
                    + "}\n");
        }
        write(headRoot, newIdentifier, "public class " + newIdentifier + " {\n}\n");
    }

    @Given("a head revision where the same method is reformatted with different whitespace but identical structure")
    public void head_class_reformatted() {
        write(headRoot, "Greeter", "public class Greeter\n{\n"
                + "    public String greet()\n    {\n"
                + "        return \"greeting\";\n"
                + "    }\n"
                + "}\n");
    }

    @Given("a head revision where {string} instead calls {string} with no other structural change")
    public void head_class_calls_different_method(String callingMethod, String call) {
        String calleeName = call.replace("()", "");
        write(headRoot, "Greeter", "public class Greeter {\n"
                + "    public String " + callingMethod + "() {\n"
                + "        return " + calleeName + "();\n"
                + "    }\n"
                + "    private String formatA() {\n"
                + "        return \"a\";\n"
                + "    }\n"
                + "    private String " + calleeName + "() {\n"
                + "        return \"b\";\n"
                + "    }\n"
                + "}\n");
    }

    // ---- action ----

    @When("the semantic engine detects transformations between the revisions")
    public void detect_transformations() {
        transformations = new TransformationDetector().detect(baseRoot, headRoot);
    }

    // ---- assertions ----

    @Then("a {string} transformation is detected involving {string} and {string}")
    public void a_transformation_is_detected_involving_two(String kind, String left, String right) {
        TransformationKind expectedKind = TransformationKind.valueOf(kind);
        assertThat(transformations)
                .anyMatch(t -> t.kind() == expectedKind
                        && t.involvedDescriptions().stream().anyMatch(d -> d.contains(left))
                        && t.involvedDescriptions().stream().anyMatch(d -> d.contains(right)));
    }

    @Then("an {string} transformation is detected involving {string} and {string}")
    public void an_transformation_is_detected_involving_two(String kind, String left, String right) {
        a_transformation_is_detected_involving_two(kind, left, right);
    }

    @Then("a {string} transformation is detected involving {string}")
    public void a_transformation_is_detected_involving_one(String kind, String symbol) {
        TransformationKind expectedKind = TransformationKind.valueOf(kind);
        assertThat(transformations)
                .anyMatch(t -> t.kind() == expectedKind
                        && t.involvedDescriptions().stream().anyMatch(d -> d.contains(symbol)));
    }

    @Then("an {string} transformation is detected involving {string}")
    public void an_transformation_is_detected_involving_one(String kind, String symbol) {
        a_transformation_is_detected_involving_one(kind, symbol);
    }

    @Then("a {string} transformation is detected with {int} occurrences")
    public void a_transformation_is_detected_with_occurrences(String kind, int occurrenceCount) {
        TransformationKind expectedKind = TransformationKind.valueOf(kind);
        assertThat(transformations)
                .anyMatch(t -> t.kind() == expectedKind && t.occurrenceCount() == occurrenceCount);
    }

    @Then("no structural transformation is detected involving {string}")
    public void no_structural_transformation_involving(String symbol) {
        assertThat(transformations)
                .noneMatch(t -> t.involvedDescriptions().stream().anyMatch(d -> d.contains(symbol)));
    }

    @Then("the {string} transformation involving {string} has a diff showing removed text {string} and added text {string}")
    public void the_transformation_has_a_diff_showing(String kind, String symbol, String removedText, String addedText) {
        TransformationKind expectedKind = TransformationKind.valueOf(kind);
        DetectedTransformation transformation = transformations.stream()
                .filter(t -> t.kind() == expectedKind && t.involvedDescriptions().stream().anyMatch(d -> d.contains(symbol)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No " + kind + " transformation found involving " + symbol));
        assertThat(transformation.diffText().lines().anyMatch(l -> l.startsWith("-") && l.contains(removedText))).isTrue();
        assertThat(transformation.diffText().lines().anyMatch(l -> l.startsWith("+") && l.contains(addedText))).isTrue();
    }

    // ---- helpers ----

    private long countBaseRefFiles() {
        try (var stream = Files.list(baseRoot)) {
            return stream.filter(p -> p.getFileName().toString().startsWith("Ref")).count();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void write(Path root, String className, String contents) {
        try {
            Files.writeString(root.resolve(className + ".java"), contents);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void deleteRecursively(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }
}
