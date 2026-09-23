package com.athena.plugin.java;

import com.athena.semantic.DetectedTransformation;
import com.athena.semantic.TransformationKind;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.AnnotationMemberDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
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

    @Given("a base revision where class {string} has a method {string} and a field {string}")
    public void base_class_has_method_and_field(String className, String methodName, String fieldName) {
        write(baseRoot, className, "public class " + className + " {\n"
                + "    private String " + fieldName + ";\n"
                + "    public String " + methodName + "() {\n"
                + "        return \"greeting\";\n"
                + "    }\n"
                + "}\n");
    }

    @Given("a base revision where class {string} has a method {string} and a field {string} in file {string}")
    public void base_class_has_method_and_field_in_file(String className, String methodName, String fieldName, String file) {
        writeFile(baseRoot, file, "public class " + className + " {\n"
                + "    private String " + fieldName + ";\n"
                + "    public String " + methodName + "() {\n"
                + "        return \"greeting\";\n"
                + "    }\n"
                + "}\n");
    }

    @Given("a base revision with no class {string}")
    public void base_revision_with_no_class(String className) {
        write(baseRoot, "Placeholder", "public class Placeholder {\n}\n");
    }

    @Given("a base revision where class {string} has a field {string} of type {string}")
    public void base_class_has_field_of_type(String className, String fieldName, String type) {
        write(baseRoot, className, "public class " + className + " {\n"
                + "    private " + type + " " + fieldName + ";\n"
                + "}\n");
    }

    @Given("a base revision where class {string} has a field {string} of type {string} and class {string} is empty")
    public void base_class_has_field_and_other_class_empty(String classA, String fieldName, String type, String classB) {
        write(baseRoot, classA, "public class " + classA + " {\n"
                + "    private " + type + " " + fieldName + ";\n"
                + "}\n");
        write(baseRoot, classB, "public class " + classB + " {\n}\n");
    }

    @Given("a base revision where class {string} has no field {string}")
    public void base_class_has_no_field(String className, String fieldName) {
        write(baseRoot, className, "public class " + className + " {\n}\n");
    }

    // ---- head revision setups ----

    @Given("a head revision where the same file instead declares class {string} with the same method and field")
    public void head_same_file_declares_renamed_class(String newClassName) {
        // Same physical file (Greeter.java) as the base revision, but declaring a
        // differently-named class inside it — that's what "renamed, same file" means
        // for the detector's file+simpleName match key. Real IDE/git renames keep the
        // filename in sync with the class name too, but the detector matches by AST
        // content, not filename, so this fixture only needs the two to diverge here to
        // exercise the "same file, different class name" match condition directly.
        writeFile(headRoot, "Greeter.java", "public class " + newClassName + " {\n"
                + "    private String prefix;\n"
                + "    public String greet() {\n"
                + "        return \"greeting\";\n"
                + "    }\n"
                + "}\n");
    }

    @Given("a head revision where the same class has been moved to file {string} unchanged")
    public void head_class_moved_to_file(String newFile) {
        writeFile(headRoot, newFile, "public class Greeter {\n"
                + "    private String prefix;\n"
                + "    public String greet() {\n"
                + "        return \"greeting\";\n"
                + "    }\n"
                + "}\n");
    }

    @Given("a head revision where class {string} has a method {string}")
    public void head_revision_class_has_method(String className, String methodName) {
        write(headRoot, className, "public class " + className + " {\n"
                + "    public String " + methodName + "() {\n"
                + "        return \"bye\";\n"
                + "    }\n"
                + "}\n");
    }

    @Given("a head revision with no class {string}")
    public void head_revision_with_no_class(String className) {
        write(headRoot, "Placeholder", "public class Placeholder {\n}\n");
    }

    @Given("a head revision where class {string} has a field {string} of type {string} instead")
    public void head_class_has_renamed_field(String className, String fieldName, String type) {
        write(headRoot, className, "public class " + className + " {\n"
                + "    private " + type + " " + fieldName + ";\n"
                + "}\n");
    }

    @Given("a head revision where class {string} has a field {string} of type {string} and class {string} is empty")
    public void head_class_has_field_and_other_empty(String classWithField, String fieldName, String type, String emptyClass) {
        write(headRoot, classWithField, "public class " + classWithField + " {\n"
                + "    private " + type + " " + fieldName + ";\n"
                + "}\n");
        write(headRoot, emptyClass, "public class " + emptyClass + " {\n}\n");
    }

    @Given("a head revision where class {string} has an additional field {string} of type {string}")
    public void head_class_has_additional_field(String className, String fieldName, String type) {
        write(headRoot, className, "public class " + className + " {\n"
                + "    private " + type + " " + fieldName + ";\n"
                + "}\n");
    }

    @Given("a head revision where class {string} no longer has the field {string}")
    public void head_class_no_longer_has_field(String className, String fieldName) {
        write(headRoot, className, "public class " + className + " {\n}\n");
    }

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

    // ---- nested and inner types (ticket #265) ----

    @Given("a base revision where nested class {string} inside {string} has no field {string}")
    public void base_nested_class_has_no_field(String nested, String outer, String fieldName) {
        write(baseRoot, outer, nestedSource(outer + "." + nested, ""));
    }

    @Given("a head revision where {string} has a field {string}")
    public void head_nested_class_has_field(String qualifiedName, String fieldName) {
        write(headRoot, topLevelOf(qualifiedName), nestedSource(qualifiedName, "private boolean " + fieldName + ";\n"));
    }

    @Given("a base revision where nested class {string} inside {string} has no method {string}")
    public void base_nested_class_has_no_method(String nested, String outer, String methodName) {
        write(baseRoot, outer, nestedSource(outer + "." + nested, ""));
    }

    @Given("a head revision where {string} has a method {string}")
    public void head_nested_class_has_method(String qualifiedName, String methodName) {
        write(headRoot, topLevelOf(qualifiedName), nestedSource(qualifiedName, method(methodName)));
    }

    @Given("a base revision where inner class {string} inside {string} has a method {string}")
    public void base_inner_class_has_method(String inner, String outer, String methodName) {
        write(baseRoot, outer, nestedSource(outer + "." + inner, method(methodName), false));
    }

    @Given("a head revision where {string} no longer has {string}")
    public void head_type_no_longer_has_member(String qualifiedName, String memberName) {
        String topLevel = topLevelOf(qualifiedName);
        write(headRoot, topLevel, withoutMember(readBase(topLevel), qualifiedName, memberName));
    }

    @Given("a base revision where nested class {string} inside {string} gets its bean factory through a setter")
    public void base_nested_class_setter_injection(String nested, String outer) {
        write(baseRoot, outer, nestedSource(outer + "." + nested,
                "private Object beanFactory;\n"
                        + "public void setBeanFactory(Object beanFactory) {\n"
                        + "    this.beanFactory = beanFactory;\n"
                        + "}\n"));
    }

    @Given("a head revision where {string} takes the bean factory as a constructor parameter assigned to a same-named field")
    public void head_nested_class_constructor_injection(String qualifiedName) {
        String simpleName = qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
        write(headRoot, topLevelOf(qualifiedName), nestedSource(qualifiedName,
                "private final Object beanFactory;\n"
                        + simpleName + "(Object beanFactory) {\n"
                        + "    this.beanFactory = beanFactory;\n"
                        + "}\n"));
    }

    @Given("a base revision where both {string} and {string} have a nested class {string} with a method {string}")
    public void base_two_outers_with_same_nested_class(String outerA, String outerB, String nested, String methodName) {
        write(baseRoot, outerA, nestedSource(outerA + "." + nested, method(methodName)));
        write(baseRoot, outerB, nestedSource(outerB + "." + nested, method(methodName)));
    }

    @Given("a head revision where only {string} no longer has {string}")
    public void head_only_one_nested_class_loses_method(String qualifiedName, String methodName) {
        String changedOuter = topLevelOf(qualifiedName);
        String nested = qualifiedName.substring(qualifiedName.indexOf('.') + 1);
        write(headRoot, changedOuter, nestedSource(qualifiedName, ""));
        try (var files = Files.list(baseRoot)) {
            for (Path baseFile : files.toList()) {
                String outer = baseFile.getFileName().toString().replace(".java", "");
                if (!outer.equals(changedOuter)) {
                    write(headRoot, outer, nestedSource(outer + "." + nested, method(methodName)));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Given("a base revision where class {string} has no nested class {string}")
    public void base_class_has_no_nested_class(String outer, String nested) {
        write(baseRoot, outer, "public class " + outer + " {\n}\n");
    }

    @Given("a head revision where {string} has a nested class {string}")
    public void head_class_has_nested_class(String outer, String nested) {
        write(headRoot, outer, nestedSource(outer + "." + nested, method("attempts")));
    }

    @Given("a base and head revision where class {string} gains a method {string}")
    public void base_and_head_where_deeply_nested_class_gains_method(String qualifiedName, String methodName) {
        write(baseRoot, topLevelOf(qualifiedName), nestedSource(qualifiedName, ""));
        write(headRoot, topLevelOf(qualifiedName), nestedSource(qualifiedName, method(methodName)));
    }

    @Then("no transformation is detected involving {string}")
    public void no_transformation_is_detected_involving(String symbol) {
        no_structural_transformation_involving(symbol);
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

    // ---- enum constants and annotation elements (ticket #266) ----

    @Given("a base revision where enum {string} has constants {string} and {string}")
    public void base_enum_has_two_constants(String enumName, String first, String second) {
        write(baseRoot, enumName, "public enum " + enumName + " {\n    " + first + ",\n    " + second + "\n}\n");
    }

    @Given("a base revision where enum {string} has constants {string}, {string} and {string}")
    public void base_enum_has_three_constants(String enumName, String first, String second, String third) {
        write(baseRoot, enumName, "public enum " + enumName + " {\n    " + first + ",\n    " + second + ",\n    "
                + third + "\n}\n");
    }

    @Given("a head revision where {string} also has {string}")
    public void head_enum_also_has_constant(String enumName, String constant) {
        String base = readBase(enumName);
        write(headRoot, enumName, base.replace("\n}", ",\n    " + constant + "\n}"));
    }

    @Given("a base and head revision where enum {string} has the same constants in a different order")
    public void enum_constants_reordered(String enumName) {
        write(baseRoot, enumName, "public enum " + enumName + " {\n    ACTIVE,\n    DISABLED\n}\n");
        write(headRoot, enumName, "public enum " + enumName + " {\n    DISABLED,\n    ACTIVE\n}\n");
    }

    @Given("a base revision where annotation {string} has no elements")
    public void base_annotation_has_no_elements(String annotation) {
        write(baseRoot, annotation, "public @interface " + annotation + " {\n}\n");
    }

    @Given("a head revision where annotation {string} has element {string} of type {string} with default {string}")
    public void head_annotation_has_element_with_default(String annotation, String element, String type, String defaultValue) {
        write(headRoot, annotation, "public @interface " + annotation + " {\n    " + type + " " + element
                + "() default \"" + defaultValue + "\";\n}\n");
    }

    @Given("a base revision where annotation {string} has elements {string} and {string}")
    public void base_annotation_has_two_elements(String annotation, String first, String second) {
        write(baseRoot, annotation, "public @interface " + annotation + " {\n    int " + first + "();\n    int "
                + second + "();\n}\n");
    }

    @Given("a head revision where annotation {string} only has {string}")
    public void head_annotation_only_has_element(String annotation, String element) {
        write(headRoot, annotation, "public @interface " + annotation + " {\n    int " + element + "();\n}\n");
    }

    @Given("a base revision where annotation {string} has element {string} with default {int}")
    public void base_annotation_element_with_default(String annotation, String element, int defaultValue) {
        write(baseRoot, annotation, "public @interface " + annotation + " {\n    int " + element + "() default "
                + defaultValue + ";\n}\n");
    }

    @Given("a head revision where {string} on {string} has default {int}")
    public void head_annotation_element_default_changed(String element, String annotation, int defaultValue) {
        write(headRoot, annotation, "public @interface " + annotation + " {\n    int " + element + "() default "
                + defaultValue + ";\n}\n");
    }

    @Then("an enum constant addition is detected involving {string}")
    public void an_enum_constant_addition_is_detected(String constant) {
        a_transformation_is_detected_involving_one("ADD_ENUM_CONSTANT", constant);
    }

    @Then("an enum constant removal is detected involving {string}")
    public void an_enum_constant_removal_is_detected(String constant) {
        a_transformation_is_detected_involving_one("REMOVE_ENUM_CONSTANT", constant);
    }

    @Then("no enum constant addition or removal is detected involving {string}")
    public void no_enum_constant_addition_or_removal(String enumName) {
        assertThat(transformations).noneMatch(t -> (t.kind() == TransformationKind.ADD_ENUM_CONSTANT
                || t.kind() == TransformationKind.REMOVE_ENUM_CONSTANT)
                && t.involvedDescriptions().stream().anyMatch(d -> d.startsWith(enumName + "#")));
    }

    @Then("an annotation element addition is detected involving {string}")
    public void an_annotation_element_addition_is_detected(String element) {
        a_transformation_is_detected_involving_one("ADD_ANNOTATION_ELEMENT", element);
    }

    @Then("the detection shows its default value {string}")
    public void the_detection_shows_its_default_value(String defaultValue) {
        assertThat(transformations).anyMatch(t -> t.kind() == TransformationKind.ADD_ANNOTATION_ELEMENT
                && t.diffText().contains("default \"" + defaultValue + "\""));
    }

    @Then("an annotation element removal is detected involving {string}")
    public void an_annotation_element_removal_is_detected(String element) {
        a_transformation_is_detected_involving_one("REMOVE_ANNOTATION_ELEMENT", element);
    }

    @Then("an annotation element default change is detected involving {string} from {int} to {int}")
    public void an_annotation_element_default_change_is_detected(String element, int from, int to) {
        the_transformation_has_a_diff_showing("CHANGE_ANNOTATION_ELEMENT_DEFAULT", element,
                "default " + from, "default " + to);
    }

    // ---- helpers ----

    private String readBase(String topLevelClassName) {
        try {
            return Files.readString(baseRoot.resolve(topLevelClassName + ".java"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * {@code source} with every member called {@code memberName} (method, field, enum
     * constant, annotation element) removed from type {@code qualifiedTypeName}, so a
     * "no longer has" step works whatever kind of type and member the base declared.
     */
    private static String withoutMember(String source, String qualifiedTypeName, String memberName) {
        CompilationUnit unit = new JavaParser(JavaParserConfigurations.currentJava()).parse(source).getResult()
                .orElseThrow(() -> new IllegalStateException("Fixture source does not parse"));
        String[] names = qualifiedTypeName.split("\\.");
        TypeDeclaration<?> type = unit.getType(0);
        for (int i = 1; i < names.length; i++) {
            String name = names[i];
            type = type.getMembers().stream()
                    .filter(member -> member instanceof TypeDeclaration<?> nested && nested.getNameAsString().equals(name))
                    .map(member -> (TypeDeclaration<?>) member)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No nested type " + name));
        }
        type.getMembers().removeIf(member -> declaresName(member, memberName));
        if (type instanceof EnumDeclaration enumDeclaration) {
            enumDeclaration.getEntries().removeIf(entry -> entry.getNameAsString().equals(memberName));
        }
        return unit.toString();
    }

    private static boolean declaresName(BodyDeclaration<?> member, String name) {
        if (member instanceof MethodDeclaration method) {
            return method.getNameAsString().equals(name);
        }
        if (member instanceof AnnotationMemberDeclaration element) {
            return element.getNameAsString().equals(name);
        }
        if (member instanceof FieldDeclaration field) {
            return field.getVariables().stream().anyMatch(variable -> variable.getNameAsString().equals(name));
        }
        return false;
    }

    private static String topLevelOf(String qualifiedName) {
        int dot = qualifiedName.indexOf('.');
        return dot < 0 ? qualifiedName : qualifiedName.substring(0, dot);
    }

    private static String method(String methodName) {
        return "public String " + methodName + "() {\n    return \"" + methodName + "\";\n}\n";
    }

    /** Source for {@code Outer.Middle.Inner} with {@code members} inside the innermost class (static nested). */
    private static String nestedSource(String qualifiedName, String members) {
        return nestedSource(qualifiedName, members, true);
    }

    /**
     * Source for {@code Outer.Middle.Inner}, every level nested in the previous one, with
     * {@code members} inside the innermost class — {@code staticNested} false makes the
     * nested levels inner (non-static) classes instead.
     */
    private static String nestedSource(String qualifiedName, String members, boolean staticNested) {
        String[] names = qualifiedName.split("\\.");
        StringBuilder source = new StringBuilder("public class " + names[0] + " {\n");
        for (int i = 1; i < names.length; i++) {
            source.append(staticNested ? "static class " : "class ").append(names[i]).append(" {\n");
        }
        source.append(members);
        source.append("}\n".repeat(names.length));
        return source.toString();
    }

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

    private void writeFile(Path root, String relativePath, String contents) {
        try {
            Path target = root.resolve(relativePath);
            Files.createDirectories(target.getParent());
            Files.writeString(target, contents);
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
