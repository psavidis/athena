package com.athena.plugin.java;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaParsingFoundationSteps {

    private String source;
    private ParseResult result;

    @Given("a Java source file defining a single class with one method")
    public void a_valid_java_source_file() {
        source = "public class Greeter {\n"
                + "    public String greet() {\n"
                + "        return \"hello\";\n"
                + "    }\n"
                + "}\n";
    }

    @Given("a Java source file containing invalid Java syntax")
    public void an_invalid_java_source_file() {
        source = "public class Greeter {\n"
                + "    public String greet( {\n"
                + "        return \"hello\"\n"
                + "    }\n";
    }

    @When("the semantic engine parses the file")
    public void the_engine_parses_the_file() {
        result = new JavaSourceParser().parse(source);
    }

    @Then("the parse succeeds")
    public void the_parse_succeeds() {
        assertThat(result.isSuccessful()).isTrue();
    }

    @Then("the parse fails")
    public void the_parse_fails() {
        assertThat(result.isSuccessful()).isFalse();
    }

    @And("the parsed result reports the class's name")
    public void the_parsed_result_reports_the_class_name() {
        assertThat(result.topLevelTypeNames()).contains("Greeter");
    }

    @And("the parsed result reports the method's name")
    public void the_parsed_result_reports_the_method_name() {
        assertThat(result.methodNames()).contains("greet");
    }

    @And("the failure identifies that the source could not be parsed")
    public void the_failure_identifies_unparseable_source() {
        assertThat(result.errorMessage()).isNotBlank();
    }
}
