package com.athena.plugin.java;

import com.athena.semantic.ChangeCategory;
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

    private final DetectionWorld world;
    private List<DetectedTransformation> transformations;

    public StructuralChangeDetectionSteps(DetectionWorld world) {
        this.world = world;
    }

    @After
    public void cleanUpRoots() {
        world.cleanUp();
    }

    // ---- base revision setups ----

    @Given("a base revision where class {string} has a method {string}")
    public void base_class_has_method(String className, String methodName) {
        write(world.baseRoot(), className, "public class " + className + " {\n"
                + "    public String " + methodName + "() {\n"
                + "        return \"greeting\";\n"
                + "    }\n"
                + "}\n");
    }

    @Given("a base revision where class {string} has a method {string} and class {string} is empty")
    public void base_class_has_method_and_other_class_empty(String classA, String methodName, String classB) {
        write(world.baseRoot(), classA, "public class " + classA + " {\n"
                + "    public String " + methodName + "() {\n"
                + "        return \"greeting\";\n"
                + "    }\n"
                + "}\n");
        write(world.baseRoot(), classB, "public class " + classB + " {\n}\n");
    }

    @Given("a base revision where class {string} has no method {string}")
    public void base_class_has_no_method(String className, String methodName) {
        write(world.baseRoot(), className, "public class " + className + " {\n}\n");
    }

    @Given("a base revision where class {string} has a method {string} taking no parameters")
    public void base_class_has_method_no_params(String className, String methodName) {
        write(world.baseRoot(), className, "public class " + className + " {\n"
                + "    public String " + methodName + "() {\n"
                + "        return \"greeting\";\n"
                + "    }\n"
                + "}\n");
    }

    @Given("a base revision where record {string} has components {string}")
    public void base_revision_record_has_components(String recordName, String components) {
        write(world.baseRoot(), recordName, "public record " + recordName + "(" + components + ") {\n}\n");
    }

    @Given("a base revision where class {string} has a method {string} with an inline fragment")
    public void base_class_has_method_with_inline_fragment(String className, String methodName) {
        write(world.baseRoot(), className, "public class " + className + " {\n"
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
            write(world.baseRoot(), className, "public class " + className + " {\n"
                    + "    public " + identifier + " make() {\n"
                    + "        return new " + identifier + "();\n"
                    + "    }\n"
                    + "}\n");
            // A minimal stand-in declaration for the referenced identifier, so
            // each file is independently parseable/compilable in spirit.
        }
        write(world.baseRoot(), identifier, "public class " + identifier + " {\n}\n");
    }

    @Given("a base revision where class {string} has a method {string} that calls {string}")
    public void base_class_has_method_calling(String className, String methodName, String call) {
        String calleeName = call.replace("()", "");
        write(world.baseRoot(), className, "public class " + className + " {\n"
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
        write(world.baseRoot(), className, "public class " + className + " {\n"
                + "    private String " + fieldName + ";\n"
                + "    public String " + methodName + "() {\n"
                + "        return \"greeting\";\n"
                + "    }\n"
                + "}\n");
    }

    @Given("a base revision where class {string} has a method {string} and a field {string} in file {string}")
    public void base_class_has_method_and_field_in_file(String className, String methodName, String fieldName, String file) {
        writeFile(world.baseRoot(), file, "public class " + className + " {\n"
                + "    private String " + fieldName + ";\n"
                + "    public String " + methodName + "() {\n"
                + "        return \"greeting\";\n"
                + "    }\n"
                + "}\n");
    }

    @Given("a base revision with no class {string}")
    public void base_revision_with_no_class(String className) {
        write(world.baseRoot(), "Placeholder", "public class Placeholder {\n}\n");
    }

    @Given("a base revision where class {string} has a field {string} of type {string}")
    public void base_class_has_field_of_type(String className, String fieldName, String type) {
        write(world.baseRoot(), className, "public class " + className + " {\n"
                + "    private " + type + " " + fieldName + ";\n"
                + "}\n");
    }

    @Given("a base revision where class {string} has a field {string} of type {string} and class {string} is empty")
    public void base_class_has_field_and_other_class_empty(String classA, String fieldName, String type, String classB) {
        write(world.baseRoot(), classA, "public class " + classA + " {\n"
                + "    private " + type + " " + fieldName + ";\n"
                + "}\n");
        write(world.baseRoot(), classB, "public class " + classB + " {\n}\n");
    }

    @Given("a base revision where class {string} has no field {string}")
    public void base_class_has_no_field(String className, String fieldName) {
        write(world.baseRoot(), className, "public class " + className + " {\n}\n");
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
        writeFile(world.headRoot(), "Greeter.java", "public class " + newClassName + " {\n"
                + "    private String prefix;\n"
                + "    public String greet() {\n"
                + "        return \"greeting\";\n"
                + "    }\n"
                + "}\n");
    }

    @Given("a head revision where the same class has been moved to file {string} unchanged")
    public void head_class_moved_to_file(String newFile) {
        writeFile(world.headRoot(), newFile, "public class Greeter {\n"
                + "    private String prefix;\n"
                + "    public String greet() {\n"
                + "        return \"greeting\";\n"
                + "    }\n"
                + "}\n");
    }

    @Given("a head revision where class {string} has a method {string}")
    public void head_revision_class_has_method(String className, String methodName) {
        write(world.headRoot(), className, "public class " + className + " {\n"
                + "    public String " + methodName + "() {\n"
                + "        return \"bye\";\n"
                + "    }\n"
                + "}\n");
    }

    @Given("a head revision with no class {string}")
    public void head_revision_with_no_class(String className) {
        write(world.headRoot(), "Placeholder", "public class Placeholder {\n}\n");
    }

    @Given("a head revision where class {string} has a field {string} of type {string} instead")
    public void head_class_has_renamed_field(String className, String fieldName, String type) {
        write(world.headRoot(), className, "public class " + className + " {\n"
                + "    private " + type + " " + fieldName + ";\n"
                + "}\n");
    }

    @Given("a head revision where class {string} has a field {string} of type {string} and class {string} is empty")
    public void head_class_has_field_and_other_empty(String classWithField, String fieldName, String type, String emptyClass) {
        write(world.headRoot(), classWithField, "public class " + classWithField + " {\n"
                + "    private " + type + " " + fieldName + ";\n"
                + "}\n");
        write(world.headRoot(), emptyClass, "public class " + emptyClass + " {\n}\n");
    }

    @Given("a head revision where class {string} has an additional field {string} of type {string}")
    public void head_class_has_additional_field(String className, String fieldName, String type) {
        write(world.headRoot(), className, "public class " + className + " {\n"
                + "    private " + type + " " + fieldName + ";\n"
                + "}\n");
    }

    @Given("a head revision where class {string} no longer has the field {string}")
    public void head_class_no_longer_has_field(String className, String fieldName) {
        write(world.headRoot(), className, "public class " + className + " {\n}\n");
    }

    @Given("a head revision where class {string} has a method {string} with the same body")
    public void head_class_has_renamed_method(String className, String methodName) {
        write(world.headRoot(), className, "public class " + className + " {\n"
                + "    public String " + methodName + "() {\n"
                + "        return \"greeting\";\n"
                + "    }\n"
                + "}\n");
    }

    @Given("a head revision where class {string} has a method {string} with the same body and class {string} is empty")
    public void head_class_has_method_and_other_empty(String classWithMethod, String methodName, String emptyClass) {
        write(world.headRoot(), classWithMethod, "public class " + classWithMethod + " {\n"
                + "    public String " + methodName + "() {\n"
                + "        return \"greeting\";\n"
                + "    }\n"
                + "}\n");
        write(world.headRoot(), emptyClass, "public class " + emptyClass + " {\n}\n");
    }

    @Given("a head revision where class {string} has an additional method {string}")
    public void head_class_has_additional_method(String className, String methodName) {
        write(world.headRoot(), className, "public class " + className + " {\n"
                + "    public String " + methodName + "() {\n"
                + "        return \"farewell\";\n"
                + "    }\n"
                + "}\n");
    }

    @Given("a head revision where class {string} no longer has the method {string}")
    public void head_class_no_longer_has_method(String className, String methodName) {
        write(world.headRoot(), className, "public class " + className + " {\n}\n");
    }

    @Given("a head revision where class {string} has a method {string} taking a {string} parameter")
    public void head_class_has_method_with_parameter(String className, String methodName, String paramType) {
        write(world.headRoot(), className, "public class " + className + " {\n"
                + "    public String " + methodName + "(" + paramType + " arg) {\n"
                + "        return \"greeting\" + arg;\n"
                + "    }\n"
                + "}\n");
    }

    @Given("a head revision where record {string} has components {string}")
    public void head_revision_record_has_components(String recordName, String components) {
        write(world.headRoot(), recordName, "public record " + recordName + "(" + components + ") {\n}\n");
    }

    @Given("a head revision where that fragment has been extracted into a new method {string} called from {string}")
    public void head_class_has_extracted_method(String extractedMethod, String callingMethod) {
        write(world.headRoot(), "Greeter", "public class Greeter {\n"
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
            write(world.headRoot(), className, "public class " + className + " {\n"
                    + "    public " + newIdentifier + " make() {\n"
                    + "        return new " + newIdentifier + "();\n"
                    + "    }\n"
                    + "}\n");
        }
        write(world.headRoot(), newIdentifier, "public class " + newIdentifier + " {\n}\n");
    }

    @Given("a head revision where the same method is reformatted with different whitespace but identical structure")
    public void head_class_reformatted() {
        write(world.headRoot(), "Greeter", "public class Greeter\n{\n"
                + "    public String greet()\n    {\n"
                + "        return \"greeting\";\n"
                + "    }\n"
                + "}\n");
    }

    @Given("a head revision where {string} instead calls {string} with no other structural change")
    public void head_class_calls_different_method(String callingMethod, String call) {
        String calleeName = call.replace("()", "");
        write(world.headRoot(), "Greeter", "public class Greeter {\n"
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
        write(world.baseRoot(), outer, nestedSource(outer + "." + nested, ""));
    }

    @Given("a head revision where {string} has a field {string}")
    public void head_nested_class_has_field(String qualifiedName, String fieldName) {
        write(world.headRoot(), topLevelOf(qualifiedName), nestedSource(qualifiedName, "private boolean " + fieldName + ";\n"));
    }

    @Given("a base revision where nested class {string} inside {string} has no method {string}")
    public void base_nested_class_has_no_method(String nested, String outer, String methodName) {
        write(world.baseRoot(), outer, nestedSource(outer + "." + nested, ""));
    }

    @Given("a head revision where {string} has a method {string}")
    public void head_nested_class_has_method(String qualifiedName, String methodName) {
        write(world.headRoot(), topLevelOf(qualifiedName), nestedSource(qualifiedName, method(methodName)));
    }

    @Given("a base revision where inner class {string} inside {string} has a method {string}")
    public void base_inner_class_has_method(String inner, String outer, String methodName) {
        write(world.baseRoot(), outer, nestedSource(outer + "." + inner, method(methodName), false));
    }

    @Given("a head revision where {string} no longer has {string}")
    public void head_type_no_longer_has_member(String qualifiedName, String memberName) {
        String topLevel = topLevelOf(qualifiedName);
        write(world.headRoot(), topLevel, withoutMember(readBase(topLevel), qualifiedName, memberName));
    }

    @Given("a base revision where nested class {string} inside {string} gets its bean factory through a setter")
    public void base_nested_class_setter_injection(String nested, String outer) {
        write(world.baseRoot(), outer, nestedSource(outer + "." + nested,
                "private Object beanFactory;\n"
                        + "public void setBeanFactory(Object beanFactory) {\n"
                        + "    this.beanFactory = beanFactory;\n"
                        + "}\n"));
    }

    @Given("a head revision where {string} takes the bean factory as a constructor parameter assigned to a same-named field")
    public void head_nested_class_constructor_injection(String qualifiedName) {
        String simpleName = qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
        write(world.headRoot(), topLevelOf(qualifiedName), nestedSource(qualifiedName,
                "private final Object beanFactory;\n"
                        + simpleName + "(Object beanFactory) {\n"
                        + "    this.beanFactory = beanFactory;\n"
                        + "}\n"));
    }

    @Given("a base revision where both {string} and {string} have a nested class {string} with a method {string}")
    public void base_two_outers_with_same_nested_class(String outerA, String outerB, String nested, String methodName) {
        write(world.baseRoot(), outerA, nestedSource(outerA + "." + nested, method(methodName)));
        write(world.baseRoot(), outerB, nestedSource(outerB + "." + nested, method(methodName)));
    }

    @Given("a head revision where only {string} no longer has {string}")
    public void head_only_one_nested_class_loses_method(String qualifiedName, String methodName) {
        String changedOuter = topLevelOf(qualifiedName);
        String nested = qualifiedName.substring(qualifiedName.indexOf('.') + 1);
        write(world.headRoot(), changedOuter, nestedSource(qualifiedName, ""));
        try (var files = Files.list(world.baseRoot())) {
            for (Path baseFile : files.toList()) {
                String outer = baseFile.getFileName().toString().replace(".java", "");
                if (!outer.equals(changedOuter)) {
                    write(world.headRoot(), outer, nestedSource(outer + "." + nested, method(methodName)));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Given("a base revision where class {string} has no nested class {string}")
    public void base_class_has_no_nested_class(String outer, String nested) {
        write(world.baseRoot(), outer, "public class " + outer + " {\n}\n");
    }

    @Given("a head revision where {string} has a nested class {string}")
    public void head_class_has_nested_class(String outer, String nested) {
        write(world.headRoot(), outer, nestedSource(outer + "." + nested, method("attempts")));
    }

    @Given("a base and head revision where class {string} gains a method {string}")
    public void base_and_head_where_deeply_nested_class_gains_method(String qualifiedName, String methodName) {
        write(world.baseRoot(), topLevelOf(qualifiedName), nestedSource(qualifiedName, ""));
        write(world.headRoot(), topLevelOf(qualifiedName), nestedSource(qualifiedName, method(methodName)));
    }

    @Then("no transformation is detected involving {string}")
    public void no_transformation_is_detected_involving(String symbol) {
        assertThat(transformations)
                .noneMatch(t -> t.involvedDescriptions().stream().anyMatch(d -> d.contains(symbol)));
    }

    // ---- action ----

    @When("the semantic engine detects transformations between the revisions")
    public void detect_transformations() {
        transformations = new TransformationDetector().detect(world.baseRoot(), world.headRoot());
        world.recordTransformations(transformations);
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
        // STRUCTURAL category only: since ticket #264 a body edit no structural detector
        // explains is still reported, as an UNKNOWN-category body modification.
        assertThat(transformations)
                .noneMatch(t -> ChangeCategory.of(t.kind()) == ChangeCategory.STRUCTURAL
                        && t.involvedDescriptions().stream().anyMatch(d -> d.contains(symbol)));
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
        write(world.baseRoot(), enumName, "public enum " + enumName + " {\n    " + first + ",\n    " + second + "\n}\n");
    }

    @Given("a base revision where enum {string} has constants {string}, {string} and {string}")
    public void base_enum_has_three_constants(String enumName, String first, String second, String third) {
        write(world.baseRoot(), enumName, "public enum " + enumName + " {\n    " + first + ",\n    " + second + ",\n    "
                + third + "\n}\n");
    }

    @Given("a head revision where {string} also has {string}")
    public void head_enum_also_has_constant(String enumName, String constant) {
        String base = readBase(enumName);
        write(world.headRoot(), enumName, base.replace("\n}", ",\n    " + constant + "\n}"));
    }

    @Given("a base and head revision where enum {string} has the same constants in a different order")
    public void enum_constants_reordered(String enumName) {
        write(world.baseRoot(), enumName, "public enum " + enumName + " {\n    ACTIVE,\n    DISABLED\n}\n");
        write(world.headRoot(), enumName, "public enum " + enumName + " {\n    DISABLED,\n    ACTIVE\n}\n");
    }

    @Given("a base revision where annotation {string} has no elements")
    public void base_annotation_has_no_elements(String annotation) {
        write(world.baseRoot(), annotation, "public @interface " + annotation + " {\n}\n");
    }

    @Given("a head revision where annotation {string} has element {string} of type {string} with default {string}")
    public void head_annotation_has_element_with_default(String annotation, String element, String type, String defaultValue) {
        write(world.headRoot(), annotation, "public @interface " + annotation + " {\n    " + type + " " + element
                + "() default \"" + defaultValue + "\";\n}\n");
    }

    @Given("a base revision where annotation {string} has elements {string} and {string}")
    public void base_annotation_has_two_elements(String annotation, String first, String second) {
        write(world.baseRoot(), annotation, "public @interface " + annotation + " {\n    int " + first + "();\n    int "
                + second + "();\n}\n");
    }

    @Given("a head revision where annotation {string} only has {string}")
    public void head_annotation_only_has_element(String annotation, String element) {
        write(world.headRoot(), annotation, "public @interface " + annotation + " {\n    int " + element + "();\n}\n");
    }

    @Given("a base revision where annotation {string} has element {string} with default {int}")
    public void base_annotation_element_with_default(String annotation, String element, int defaultValue) {
        write(world.baseRoot(), annotation, "public @interface " + annotation + " {\n    int " + element + "() default "
                + defaultValue + ";\n}\n");
    }

    @Given("a head revision where {string} on {string} has default {int}")
    public void head_annotation_element_default_changed(String element, String annotation, int defaultValue) {
        write(world.headRoot(), annotation, "public @interface " + annotation + " {\n    int " + element + "() default "
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

    // ---- field type changes (ticket #267) ----

    @Given("a base revision where class {string} has a private field {string} of type {string}")
    public void base_class_has_private_field(String className, String fieldName, String type) {
        write(world.baseRoot(), className, fieldSource(className, "private", type, fieldName, ""));
    }

    @Given("a head revision where {string} has a private field {string} of type {string}")
    public void head_class_has_private_field(String className, String fieldName, String type) {
        write(world.headRoot(), className, fieldSource(className, "private", type, fieldName, ""));
    }

    @Given("a base revision where class {string} has a protected field {string} of type {string}")
    public void base_class_has_protected_field(String className, String fieldName, String type) {
        write(world.baseRoot(), className, fieldSource(className, "protected", type, fieldName, ""));
    }

    @Given("a head revision where {string} has a protected field {string} of type {string}")
    public void head_class_has_protected_field(String className, String fieldName, String type) {
        write(world.headRoot(), className, fieldSource(className, "protected", type, fieldName, ""));
    }

    @Given("a head revision where {string} has a field {string} of type {string} and no field {string}")
    public void head_class_has_field_and_no_other(String className, String fieldName, String type, String absentField) {
        write(world.headRoot(), className, fieldSource(className, "private", type, fieldName, ""));
    }

    @Given("a base revision where class {string} has a field {string} of type {string} annotated {string}")
    public void base_class_has_annotated_field(String className, String fieldName, String type, String annotation) {
        write(world.baseRoot(), className, fieldSource(className, "private", type, fieldName, annotation + " "));
    }

    @Given("a head revision where {string} on {string} has type {string} and no annotation")
    public void head_field_has_type_and_no_annotation(String fieldName, String className, String type) {
        write(world.headRoot(), className, fieldSource(className, "private", type, fieldName, ""));
    }

    @Then("a field type change is detected involving {string} from {string} to {string}")
    public void a_field_type_change_is_detected_from_to(String field, String fromType, String toType) {
        assertThat(fieldTypeChangesInvolving(field))
                .anyMatch(t -> t.involvedDescriptions().stream().anyMatch(d -> d.contains(fromType + " -> " + toType)));
    }

    @Then("a field type change is detected involving {string}")
    public void a_field_type_change_is_detected(String field) {
        assertThat(fieldTypeChangesInvolving(field)).isNotEmpty();
    }

    @Then("no field type change is detected involving {string}")
    public void no_field_type_change_is_detected(String field) {
        assertThat(fieldTypeChangesInvolving(field)).isEmpty();
    }

    @Then("the change is described as affecting a protected field")
    public void the_change_is_described_as_affecting_a_protected_field() {
        assertThat(transformations).anyMatch(t -> t.kind() == TransformationKind.CHANGE_FIELD_TYPE
                && t.involvedDescriptions().stream().anyMatch(d -> d.contains("protected")));
    }

    @Then("no {string} or {string} transformation is detected involving {string}")
    public void no_transformation_of_either_kind(String firstKind, String secondKind, String symbol) {
        TransformationKind first = TransformationKind.valueOf(firstKind);
        TransformationKind second = TransformationKind.valueOf(secondKind);
        assertThat(transformations).noneMatch(t -> (t.kind() == first || t.kind() == second)
                && t.involvedDescriptions().stream().anyMatch(d -> d.contains(symbol)));
    }

    private List<DetectedTransformation> fieldTypeChangesInvolving(String field) {
        return transformations.stream()
                .filter(t -> t.kind() == TransformationKind.CHANGE_FIELD_TYPE)
                .filter(t -> t.involvedDescriptions().get(0).equals(field))
                .toList();
    }

    private static String fieldSource(String className, String visibility, String type, String fieldName, String annotation) {
        return "public class " + className + " {\n    " + annotation + visibility + " " + type + " " + fieldName + ";\n}\n";
    }

    // ---- parameter and return annotation changes (ticket #268) ----

    @Given("a base revision where method {string} on class {string} takes parameter {string} with no annotation")
    public void base_method_takes_unannotated_parameter(String method, String className, String parameter) {
        write(world.baseRoot(), className, methodSource("class", className, "", method, "Object " + parameter));
    }

    @Given("a head revision where parameter {string} of {string} is annotated {string}")
    public void head_parameter_is_annotated(String parameter, String qualifiedMethod, String annotation) {
        String className = qualifiedMethod.substring(0, qualifiedMethod.indexOf('#'));
        String method = qualifiedMethod.substring(qualifiedMethod.indexOf('#') + 1);
        write(world.headRoot(), className, methodSource("class", className, "", method, annotation + " Object " + parameter));
    }

    @Given("a base revision where method {string} on class {string} takes parameter {string} annotated {string}")
    public void base_method_takes_annotated_parameter(String method, String className, String parameter, String annotation) {
        write(world.baseRoot(), className, methodSource("class", className, "", method, annotation + " Object " + parameter));
    }

    @Given("a head revision where parameter {string} of {string} has no annotation")
    public void head_parameter_has_no_annotation(String parameter, String qualifiedMethod) {
        String className = qualifiedMethod.substring(0, qualifiedMethod.indexOf('#'));
        String method = qualifiedMethod.substring(qualifiedMethod.indexOf('#') + 1);
        write(world.headRoot(), className, methodSource("class", className, "", method, "Object " + parameter));
    }

    @Given("a base revision where method {string} on interface {string} takes parameters {string} and {string} with no annotations")
    public void base_interface_method_takes_two_parameters(String method, String interfaceName, String first, String second) {
        write(world.baseRoot(), interfaceName, "public interface " + interfaceName + "<T> {\n    " + interfaceName + "<T> "
                + method + "(T " + first + ", T... " + second + ");\n}\n");
    }

    @Given("a head revision where both parameters of {string} are annotated {string}")
    public void head_both_parameters_annotated(String qualifiedMethod, String annotation) {
        String interfaceName = qualifiedMethod.substring(0, qualifiedMethod.indexOf('#'));
        String method = qualifiedMethod.substring(qualifiedMethod.indexOf('#') + 1);
        write(world.headRoot(), interfaceName, "public interface " + interfaceName + "<T> {\n    " + interfaceName + "<T> "
                + method + "(" + annotation + " T value, " + annotation + " T... values);\n}\n");
    }

    @Given("a base revision where the constructor of class {string} takes parameter {string} with no annotation")
    public void base_constructor_takes_unannotated_parameter(String className, String parameter) {
        write(world.baseRoot(), className, "public class " + className + " {\n    public " + className + "(Object " + parameter
                + ") {\n    }\n}\n");
    }

    @Given("a head revision where parameter {string} of the {string} constructor is annotated {string}")
    public void head_constructor_parameter_annotated(String parameter, String className, String annotation) {
        write(world.headRoot(), className, "public class " + className + " {\n    public " + className + "(" + annotation
                + " Object " + parameter + ") {\n    }\n}\n");
    }

    @Given("a base revision where method {string} on class {string} has no annotation on its return type")
    public void base_method_without_return_annotation(String method, String className) {
        write(world.baseRoot(), className, methodSource("class", className, "", method, ""));
    }

    @Given("a head revision where {string} is annotated {string} on its return type")
    public void head_method_with_return_annotation(String qualifiedMethod, String annotation) {
        String className = qualifiedMethod.substring(0, qualifiedMethod.indexOf('#'));
        String method = qualifiedMethod.substring(qualifiedMethod.indexOf('#') + 1);
        write(world.headRoot(), className, methodSource("class", className, annotation + " ", method, ""));
    }

    @Given("a base and head revision where the only difference in method {string} on class {string} is {string} on its parameter")
    public void only_parameter_annotation_differs(String method, String className, String annotation) {
        write(world.baseRoot(), className, methodSource("class", className, "", method, "Object methodCall"));
        write(world.headRoot(), className, methodSource("class", className, "", method, annotation + " Object methodCall"));
    }

    @Then("a parameter annotation change is detected involving {string}")
    public void a_parameter_annotation_change_is_detected(String qualifiedMethod) {
        assertThat(parameterAnnotationChangesInvolving(qualifiedMethod)).isNotEmpty();
    }

    @Then("a parameter annotation change is detected involving the constructor of {string}")
    public void a_parameter_annotation_change_on_constructor(String className) {
        a_parameter_annotation_change_is_detected(className + "#<init>");
    }

    @Then("it names parameter {string} as having gained {string}")
    public void it_names_parameter_as_having_gained(String parameter, String annotation) {
        assertThat(transformations).anyMatch(t -> t.kind() == TransformationKind.CHANGE_PARAMETER_ANNOTATIONS
                && t.involvedDescriptions().stream().anyMatch(d -> d.contains(parameter + " +" + annotation)));
    }

    @Then("it names parameter {string} as having lost {string}")
    public void it_names_parameter_as_having_lost(String parameter, String annotation) {
        assertThat(transformations).anyMatch(t -> t.kind() == TransformationKind.CHANGE_PARAMETER_ANNOTATIONS
                && t.involvedDescriptions().stream().anyMatch(d -> d.contains(parameter + " -" + annotation)));
    }

    @Then("one parameter annotation change is detected involving {string} naming both parameters")
    public void one_parameter_annotation_change_naming_both(String qualifiedMethod) {
        List<DetectedTransformation> changes = parameterAnnotationChangesInvolving(qualifiedMethod);
        assertThat(changes).hasSize(1);
        assertThat(changes.get(0).involvedDescriptions().get(1)).contains("value +@").contains("values +@");
    }

    @Then("a return annotation change is detected involving {string}")
    public void a_return_annotation_change_is_detected(String qualifiedMethod) {
        assertThat(transformations).anyMatch(t -> t.kind() == TransformationKind.CHANGE_METHOD_ANNOTATIONS
                && t.involvedDescriptions().get(0).equals(qualifiedMethod));
    }

    @Then("no {string} transformation is detected involving {string}")
    public void no_transformation_of_kind_is_detected(String kind, String symbol) {
        TransformationKind unexpected = TransformationKind.valueOf(kind);
        assertThat(transformations).noneMatch(t -> t.kind() == unexpected
                && t.involvedDescriptions().stream().anyMatch(d -> d.contains(symbol)));
    }

    private List<DetectedTransformation> parameterAnnotationChangesInvolving(String qualifiedMethod) {
        return transformations.stream()
                .filter(t -> t.kind() == TransformationKind.CHANGE_PARAMETER_ANNOTATIONS)
                .filter(t -> t.involvedDescriptions().get(0).equals(qualifiedMethod))
                .toList();
    }

    private static String methodSource(String typeKeyword, String typeName, String methodAnnotation, String method,
                                       String parameters) {
        return "public " + typeKeyword + " " + typeName + " {\n    " + methodAnnotation + "public Object " + method
                + "(" + parameters + ") {\n        return null;\n    }\n}\n";
    }

    // ---- method body modifications (ticket #264) ----

    @Given("a head revision where {string} on {string} returns {string} with the same signature")
    public void head_method_returns_expression(String method, String className, String expression) {
        write(world.headRoot(), className, "public class " + className + " {\n    public String " + method
                + "() {\n        return " + expression + ";\n    }\n}\n");
    }

    @Given("a base revision where the constructor of class {string} assigns {string} directly")
    public void base_constructor_assigns_directly(String className, String field) {
        write(world.baseRoot(), className, "public class " + className + " {\n    private final long " + field + ";\n    public "
                + className + "(long " + field + ") {\n        this." + field + " = " + field + ";\n    }\n}\n");
    }

    @Given("a head revision where the constructor of {string} validates {string} before assigning it")
    public void head_constructor_validates(String className, String field) {
        write(world.headRoot(), className, "public class " + className + " {\n    private final long " + field + ";\n    public "
                + className + "(long " + field + ") {\n        this." + field + " = Math.max(0, " + field + ");\n    }\n}\n");
    }

    @Given("a base and head revision where methods {string}, {string} and {string} on class {string} each had their bodies restructured")
    public void methods_had_bodies_restructured(String first, String second, String third, String className) {
        StringBuilder base = new StringBuilder("public class " + className + " {\n");
        StringBuilder head = new StringBuilder("public class " + className + " {\n");
        for (String method : List.of(first, second, third)) {
            base.append("    String ").append(method).append("(String in) {\n        return in.strip();\n    }\n");
            head.append("    String ").append(method).append("(String in) {\n        String result = in.strip();\n        return result;\n    }\n");
        }
        write(world.baseRoot(), className, base.append("}\n").toString());
        write(world.headRoot(), className, head.append("}\n").toString());
    }

    @Given("a base revision where method {string} on class {string} checks {string}")
    public void base_method_checks(String method, String className, String condition) {
        write(world.baseRoot(), className, guardSource(className, method, condition));
    }

    @Given("a head revision where {string} on {string} checks {string}")
    public void head_method_checks(String method, String className, String condition) {
        write(world.headRoot(), className, guardSource(className, method, condition));
    }

    @Given("a base and head revision where method {string} on class {string} differs only in whitespace")
    public void method_differs_only_in_whitespace(String method, String className) {
        write(world.baseRoot(), className, "public class " + className + " {\n    public String " + method
                + "() { return \"x\"; }\n}\n");
        write(world.headRoot(), className, "public class " + className + " {\n    public String " + method
                + "() {\n        return \"x\";\n    }\n}\n");
    }

    @Given("a head revision where {string} on {string} takes a {string} parameter and uses it in its body")
    public void head_method_takes_parameter_and_uses_it(String method, String className, String type) {
        write(world.headRoot(), className, "public class " + className + " {\n    public String " + method + "(" + type
                + " name) {\n        return \"greeting \" + name;\n    }\n}\n");
    }

    @Given("a base and head revision where method {string} on class {string} is identical")
    public void method_is_identical(String method, String className) {
        String source = "public class " + className + " {\n    public String " + method + "() {\n        return \"x\";\n    }\n}\n";
        write(world.baseRoot(), className, source);
        write(world.headRoot(), className, source);
    }

    @Then("a body modification is detected involving {string} categorised as Unknown")
    public void a_body_modification_categorised_as_unknown(String symbol) {
        assertThat(bodyModificationsInvolving(symbol)).isNotEmpty()
                .allMatch(t -> ChangeCategory.of(t.kind()) == ChangeCategory.UNKNOWN);
    }

    @Then("it shows the method before and after the edit")
    public void it_shows_the_method_before_and_after() {
        DetectedTransformation modification = transformations.stream()
                .filter(t -> t.kind() == TransformationKind.MODIFY_METHOD_BODY).findFirst().orElseThrow();
        assertThat(modification.diffText().lines()).anyMatch(line -> line.startsWith("-"));
        assertThat(modification.diffText().lines()).anyMatch(line -> line.startsWith("+"));
    }

    @Then("a body modification is detected involving the constructor of {string}")
    public void a_body_modification_on_constructor(String className) {
        assertThat(bodyModificationsInvolving(className + "#<init>")).isNotEmpty();
    }

    @Then("a body modification is detected involving each of {string}, {string} and {string}")
    public void a_body_modification_on_each(String first, String second, String third) {
        assertThat(bodyModificationsInvolving(first)).hasSize(1);
        assertThat(bodyModificationsInvolving(second)).hasSize(1);
        assertThat(bodyModificationsInvolving(third)).hasSize(1);
    }

    @Then("no body modification is detected involving {string}")
    public void no_body_modification_is_detected(String symbol) {
        assertThat(bodyModificationsInvolving(symbol)).isEmpty();
    }

    private List<DetectedTransformation> bodyModificationsInvolving(String symbol) {
        return transformations.stream()
                .filter(t -> t.kind() == TransformationKind.MODIFY_METHOD_BODY)
                .filter(t -> t.involvedDescriptions().get(0).equals(symbol))
                .toList();
    }

    private static String guardSource(String className, String method, String condition) {
        return "public class " + className + " {\n    public boolean " + method + "(User user) {\n        if (" + condition
                + ") {\n            return true;\n        }\n        return false;\n    }\n}\n";
    }

    // ---- extract-method scoped to the change (ticket #269) ----

    @Given("a base and head revision where unchanged class {string} has a method that calls {string} on a stream")
    public void unchanged_class_calls_method_on_stream(String className, String call) {
        String source = "public class " + className + " {\n    void shutdown(java.io.InputStream stream) throws Exception {\n        stream."
                + call + ";\n    }\n}\n";
        write(world.baseRoot(), className, source);
        write(world.headRoot(), className, source);
    }

    @Given("the head revision adds class {string} with a new method {string}")
    public void head_adds_class_with_new_empty_method(String className, String method) {
        write(world.headRoot(), className, "public class " + className + " {\n    public void " + method + "() {\n    }\n}\n");
    }

    @Given("a base revision where class {string} does not exist")
    public void base_class_does_not_exist(String className) {
        write(world.baseRoot(), "Placeholder", "public class Placeholder {\n}\n");
    }

    @Given("a head revision where class {string} has an empty method {string} and a changed method {string} that calls {string}")
    public void head_class_with_empty_method_and_caller(String className, String emptyMethod, String caller, String call) {
        write(world.headRoot(), "Placeholder", "public class Placeholder {\n}\n");
        write(world.headRoot(), className, "public class " + className + " {\n    public void " + emptyMethod
                + "() {\n    }\n    public Object " + caller + "() {\n        " + call + ";\n        return this;\n    }\n}\n");
    }

    @Given("a base and head revision where files {string} and {string} changed and every other file is identical")
    public void only_two_files_changed(String firstFile, String secondFile) {
        String first = firstFile.replace(".java", "");
        String second = secondFile.replace(".java", "");
        write(world.baseRoot(), first, "public class " + first + " {\n    String run() {\n        return \"a\";\n    }\n}\n");
        write(world.headRoot(), first, "public class " + first + " {\n    String run() {\n        return prepare();\n    }\n"
                + "    String prepare() {\n        return \"a\";\n    }\n    void close() {\n    }\n}\n");
        write(world.baseRoot(), second, "public class " + second + " {\n}\n");
        write(world.headRoot(), second, "public class " + second + " {\n    void init() {\n    }\n}\n");
        for (String unchanged : List.of("Closer", "Starter")) {
            String source = "public class " + unchanged + " {\n    void go(" + first + " a) {\n        a.close();\n        init();\n    }\n"
                    + "    void init() {\n    }\n}\n";
            write(world.baseRoot(), unchanged, source);
            write(world.headRoot(), unchanged, source);
        }
    }

    @Then("no transformation cites a file of class {string}")
    public void no_transformation_cites_a_file_of_class(String className) {
        assertThat(transformations).noneMatch(t -> t.filesTouched().stream()
                .anyMatch(file -> file.endsWith(className + ".java")));
    }

    @Then("every detected transformation only cites {string} or {string}")
    public void every_transformation_only_cites(String firstFile, String secondFile) {
        assertThat(transformations).isNotEmpty();
        assertThat(transformations).allMatch(t -> t.filesTouched().stream()
                .allMatch(file -> file.endsWith(firstFile) || file.endsWith(secondFile)));
    }

    // ---- mechanical replacement scoped to the change (ticket #270) ----

    @Given("a base and head revision where identifier {string} is replaced by {string} consistently in three changed files")
    public void identifier_replaced_in_three_changed_files(String oldIdentifier, String newIdentifier) {
        for (int i = 0; i < 3; i++) {
            writeReference(i, oldIdentifier);
            writeReferenceHead(i, newIdentifier);
        }
    }

    @Given("a base and head revision where identifier {string} appears only in files that are identical in both revisions")
    public void identifier_only_in_unchanged_files(String identifier) {
        writeReference(0, identifier);
        writeReferenceHead(0, identifier);
        write(world.baseRoot(), "Changed", "public class Changed {\n    int size() {\n        return 1;\n    }\n}\n");
        write(world.headRoot(), "Changed", "public class Changed {\n    int size() {\n        return 2;\n    }\n}\n");
    }

    @Given("a base and head revision where {string} is replaced by {string} in one changed file and by {string} in another")
    public void identifier_replaced_inconsistently(String oldIdentifier, String firstNew, String secondNew) {
        writeReference(0, oldIdentifier);
        writeReferenceHead(0, firstNew);
        writeReference(1, oldIdentifier);
        writeReferenceHead(1, secondNew);
    }

    @Given("a base and head revision where {string} is replaced by {string} in two changed files")
    public void identifier_replaced_in_two_changed_files(String oldIdentifier, String newIdentifier) {
        for (int i = 0; i < 2; i++) {
            writeReference(i, oldIdentifier);
            writeReferenceHead(i, newIdentifier);
        }
    }

    @Given("{string} is still referenced unchanged in a third file")
    public void identifier_still_referenced_in_third_file(String identifier) {
        writeReference(2, identifier);
        writeReferenceHead(2, identifier);
    }

    @Then("a {string} transformation is detected involving {string} with {int} occurrences")
    public void a_transformation_involving_with_occurrences(String kind, String replacement, int occurrences) {
        TransformationKind expectedKind = TransformationKind.valueOf(kind);
        assertThat(transformations).anyMatch(t -> t.kind() == expectedKind
                && t.involvedDescriptions().contains(replacement)
                && t.occurrenceCount() == occurrences);
    }

    /** {@code Ref<i>} in the base revision, referencing {@code identifier}. */
    private void writeReference(int index, String identifier) {
        write(world.baseRoot(), "Ref" + index, referenceSource(index, identifier));
    }

    /** {@code Ref<i>} in the head revision, referencing {@code identifier}. */
    private void writeReferenceHead(int index, String identifier) {
        write(world.headRoot(), "Ref" + index, referenceSource(index, identifier));
    }

    private static String referenceSource(int index, String identifier) {
        return "public class Ref" + index + " {\n    public " + identifier + " make() {\n        return new " + identifier
                + "();\n    }\n}\n";
    }

    // ---- helpers ----

    private String readBase(String topLevelClassName) {
        try {
            return Files.readString(world.baseRoot().resolve(topLevelClassName + ".java"));
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
        try (var stream = Files.list(world.baseRoot())) {
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

}
