package com.athena.semantic;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class FlowFeatureClassificationSteps {

    private final FlowTaxonomyClassifier classifier =
            new FlowTaxonomyClassifier(new TaxonomyLoader().load(SemanticDimension.FEATURE));

    private Change theChange;
    private Optional<SemanticClassification> classification;

    @Given("a change that adds a class named {string} to the codebase")
    public void a_change_that_adds_a_class_named(String simpleName) {
        DetectedTransformation t = DetectedTransformation.of(TransformationKind.ADD_CLASS, List.of(simpleName), List.of());
        theChange = new ChangeGrouper().group(List.of(t)).get(0);
    }

    @Given("a change with no matched code at all")
    public void a_change_with_no_matched_code_at_all() {
        theChange = new Change("Untitled", TransformationKind.ADD_CLASS, List.of(), List.of());
    }

    @When("the change is classified along the Feature dimension")
    public void the_change_is_classified_along_the_feature_dimension() {
        classification = classifier.classify(theChange);
    }

    @Then("it is classified with the {string} flow concept")
    public void it_is_classified_with_the_flow_concept(String conceptName) {
        assertThat(classification).isPresent();
        assertThat(classification.get().concept().name()).isEqualTo(conceptName);
    }

    @Then("it has no Feature classification")
    public void it_has_no_feature_classification() {
        assertThat(classification).isEmpty();
    }
}
