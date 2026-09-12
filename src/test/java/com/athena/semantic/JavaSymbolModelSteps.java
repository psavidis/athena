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

import static org.assertj.core.api.Assertions.assertThat;

public class JavaSymbolModelSteps {

    private Path sourceRoot;
    private SymbolModel model;
    private SymbolModel modelA;
    private SymbolModel modelB;
    private Symbol lookedUpSymbol;

    @Before
    public void createTempSourceRoot() throws IOException {
        sourceRoot = Files.createTempDirectory("athena-symbol-model-test");
    }

    @After
    public void cleanUpTempSourceRoot() throws IOException {
        if (sourceRoot != null) {
            try (var walk = Files.walk(sourceRoot)) {
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

    @Given("a Java source tree containing a class {string} with a field {string} and a method {string}")
    public void a_source_tree_with_class_field_and_method(String className, String fieldName, String methodName) {
        writeJavaFile(className, "public class " + className + " {\n"
                + "    private String " + fieldName + ";\n"
                + "    public String " + methodName + "() {\n"
                + "        return " + fieldName + ";\n"
                + "    }\n"
                + "}\n");
    }

    @Given("a Java source tree containing a class {string} with a method {string}")
    public void a_source_tree_with_class_and_method(String className, String methodName) {
        writeJavaFile(className, "public class " + className + " {\n"
                + "    public String " + methodName + "() {\n"
                + "        return \"hello\";\n"
                + "    }\n"
                + "}\n");
    }

    @Given("a Java source tree containing a class {string} whose method body references an external library type not on the classpath")
    public void a_source_tree_referencing_unresolvable_type(String className) {
        writeJavaFile(className, "import com.somelibrary.not.OnTheClasspath.Widget;\n\n"
                + "public class " + className + " {\n"
                + "    public Widget makeWidget() {\n"
                + "        return new Widget();\n"
                + "    }\n"
                + "}\n");
    }

    @Given("the semantic engine has built a symbol model of the source tree")
    public void the_engine_has_built_a_model() {
        model = new SymbolModelBuilder().build(sourceRoot);
    }

    @When("the semantic engine builds a symbol model of the source tree")
    public void the_engine_builds_a_model() {
        model = new SymbolModelBuilder().build(sourceRoot);
    }

    @When("the semantic engine builds a symbol model of the source tree twice")
    public void the_engine_builds_a_model_twice() {
        modelA = new SymbolModelBuilder().build(sourceRoot);
        modelB = new SymbolModelBuilder().build(sourceRoot);
    }

    @When("the engine looks up the {string} method symbol on {string} by its identifier")
    public void the_engine_looks_up_a_method_symbol(String methodName, String className) {
        Symbol found = model.symbolsOfKind(SymbolKind.METHOD).stream()
                .filter(s -> s.simpleName().equals(methodName) && s.enclosingTypeSimpleName().equals(className))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No method symbol found for " + className + "#" + methodName));
        lookedUpSymbol = model.findById(found.id()).orElseThrow(
                () -> new AssertionError("Symbol id " + found.id() + " did not resolve via findById"));
    }

    @Then("the symbol model lists a type symbol for {string}")
    @Then("the symbol model still lists a type symbol for {string}")
    public void the_model_lists_a_type_symbol(String typeName) {
        assertThat(model.symbolsOfKind(SymbolKind.TYPE))
                .anyMatch(s -> s.simpleName().equals(typeName));
    }

    @Then("the symbol model lists a field symbol for {string} on {string}")
    public void the_model_lists_a_field_symbol(String fieldName, String className) {
        assertThat(model.symbolsOfKind(SymbolKind.FIELD))
                .anyMatch(s -> s.simpleName().equals(fieldName) && s.enclosingTypeSimpleName().equals(className));
    }

    @Then("the symbol model lists a method symbol for {string} on {string}")
    public void the_model_lists_a_method_symbol(String methodName, String className) {
        assertThat(model.symbolsOfKind(SymbolKind.METHOD))
                .anyMatch(s -> s.simpleName().equals(methodName) && s.enclosingTypeSimpleName().equals(className));
    }

    @Then("the returned symbol identifies {string} as a method of {string}")
    public void the_returned_symbol_identifies_method(String methodName, String className) {
        assertThat(lookedUpSymbol.simpleName()).isEqualTo(methodName);
        assertThat(lookedUpSymbol.enclosingTypeSimpleName()).isEqualTo(className);
        assertThat(lookedUpSymbol.kind()).isEqualTo(SymbolKind.METHOD);
    }

    @Then("the {string} method symbol has the same identifier in both models")
    public void the_method_symbol_has_the_same_identifier(String methodName) {
        SymbolId idA = modelA.symbolsOfKind(SymbolKind.METHOD).stream()
                .filter(s -> s.simpleName().equals(methodName))
                .findFirst().orElseThrow().id();
        SymbolId idB = modelB.symbolsOfKind(SymbolKind.METHOD).stream()
                .filter(s -> s.simpleName().equals(methodName))
                .findFirst().orElseThrow().id();
        assertThat(idA).isEqualTo(idB);
    }

    @And("the symbol model reports a resolution failure for the unresolvable reference")
    public void the_model_reports_a_resolution_failure() {
        assertThat(model.resolutionFailures()).isNotEmpty();
    }

    private void writeJavaFile(String className, String contents) {
        try {
            Files.writeString(sourceRoot.resolve(className + ".java"), contents);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
