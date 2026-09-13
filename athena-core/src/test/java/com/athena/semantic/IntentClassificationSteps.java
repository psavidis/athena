package com.athena.semantic;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class IntentClassificationSteps {

    private final TaxonomyLoader loader = new TaxonomyLoader();
    private final IntentTaxonomyClassifier classifier = new IntentTaxonomyClassifier(loader.load(SemanticDimension.INTENT));

    private SemanticProfile profile;
    private List<SemanticClassification> classifications;

    @Given("a change already classified with the {string} concept along the Pattern dimension")
    public void a_change_already_classified_along_the_pattern_dimension(String conceptName) {
        Change change = renameChange();
        TaxonomyConcept concept = conceptNamed(SemanticDimension.PATTERN, conceptName);
        profile = SemanticProfile.empty(change)
                .with(SemanticDimension.PATTERN, SemanticClassification.of(concept, change.matchedOccurrences()));
    }

    @Given("the same change already classified with the {string} concept along the Framework dimension")
    public void the_same_change_already_classified_along_the_framework_dimension(String conceptName) {
        // FRAMEWORK-dimension content is owned by a FrameworkPlugin module, not athena-core
        // (see SemanticProfileTest) — synthesized here the same way, rather than depending on
        // any particular plugin's taxonomy data being present on athena-core's classpath.
        TaxonomyConcept concept = TaxonomyConcept.of("spring-field-to-constructor-injection",
                SemanticDimension.FRAMEWORK, conceptName, "", Optional.empty());
        profile = profile.with(SemanticDimension.FRAMEWORK,
                SemanticClassification.of(concept, profile.change().matchedOccurrences()));
    }

    @Given("a change with no classification on any dimension other than Intent")
    public void a_change_with_no_classification_on_any_other_dimension() {
        profile = SemanticProfile.empty(renameChange());
    }

    @When("the change is classified along the Intent dimension")
    public void the_change_is_classified_along_the_intent_dimension() {
        classifications = classifier.classify(profile);
    }

    @Then("its primary Intent classification is the {string} concept")
    public void its_primary_intent_classification_is_the_concept(String conceptName) {
        assertThat(classifications).isNotEmpty();
        assertThat(classifications.get(0).concept().name()).isEqualTo(conceptName);
    }

    @Then("it also carries the {string} concept as a lower-confidence alternative")
    public void it_also_carries_the_concept_as_a_lower_confidence_alternative(String conceptName) {
        assertThat(classifications.size()).isGreaterThan(1);
        assertThat(classifications.subList(1, classifications.size()))
                .extracting(c -> c.concept().name())
                .contains(conceptName);
    }

    @Then("that classification's evidence is the change's Pattern and Framework classifications, not its raw diff")
    public void that_classifications_evidence_is_the_pattern_and_framework_classifications() {
        List<DetectedTransformation> patternEvidence = profile.classifications(SemanticDimension.PATTERN).get(0).evidence();
        List<DetectedTransformation> frameworkEvidence = profile.classifications(SemanticDimension.FRAMEWORK).get(0).evidence();

        assertThat(classifications.get(0).evidence())
                .containsExactlyInAnyOrderElementsOf(concat(patternEvidence, frameworkEvidence));
    }

    @Then("it has no Intent classification")
    public void it_has_no_intent_classification() {
        assertThat(classifications).isEmpty();
    }

    private Change renameChange() {
        DetectedTransformation t = DetectedTransformation.of(TransformationKind.RENAME_SYMBOL,
                List.of("UserService#save", "UserService#persist"), List.of());
        return new ChangeGrouper().group(List.of(t)).get(0);
    }

    private TaxonomyConcept conceptNamed(SemanticDimension dimension, String conceptName) {
        return loader.load(dimension).concepts().stream()
                .filter(concept -> concept.name().equals(conceptName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No " + dimension + " concept named \"" + conceptName + "\""));
    }

    private List<DetectedTransformation> concat(List<DetectedTransformation> a, List<DetectedTransformation> b) {
        List<DetectedTransformation> merged = new java.util.ArrayList<>(a);
        merged.addAll(b);
        return merged;
    }
}
