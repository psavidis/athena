package com.athena.semantic;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntentTaxonomyClassifierTest {

    private final TaxonomyLoader loader = new TaxonomyLoader();
    private final IntentTaxonomyClassifier classifier = new IntentTaxonomyClassifier(loader.load(SemanticDimension.INTENT));

    @Test
    void classifiesDependencyInjectionEvidenceAsReduceCouplingPrimaryWithImproveMaintainabilityAsAnAlternative() {
        Change change = anyChange();
        SemanticProfile profile = SemanticProfile.empty(change)
                .with(SemanticDimension.PATTERN, SemanticClassification.of(dependencyInjectionConcept(), change.matchedOccurrences()))
                .with(SemanticDimension.FRAMEWORK, SemanticClassification.of(fieldToConstructorInjectionConcept(), change.matchedOccurrences()));

        List<SemanticClassification> classifications = classifier.classify(profile);

        assertThat(classifications).extracting(c -> c.concept().id())
                .containsExactly("reduce-coupling", "improve-maintainability");
    }

    @Test
    void deduplicatesEvidenceWhenTheCorrelatingClassificationsShareTheSameUnderlyingTransformations() {
        // The realistic case: every existing classifier passes a Change's own
        // matchedOccurrences() verbatim as evidence, so a Pattern and a Framework
        // classification derived from the same Change carry the exact same evidence list.
        Change change = anyChange();
        SemanticProfile profile = SemanticProfile.empty(change)
                .with(SemanticDimension.PATTERN, SemanticClassification.of(dependencyInjectionConcept(), change.matchedOccurrences()))
                .with(SemanticDimension.FRAMEWORK, SemanticClassification.of(fieldToConstructorInjectionConcept(), change.matchedOccurrences()));

        List<SemanticClassification> classifications = classifier.classify(profile);

        assertThat(classifications.get(0).evidence()).containsExactlyElementsOf(change.matchedOccurrences());
    }

    @Test
    void theInferredClassificationsEvidenceIsTheUnionOfTheCorrelatingClassificationsEvidence() {
        DetectedTransformation patternEvidence = DetectedTransformation.of(
                TransformationKind.ADD_CONSTRUCTOR_PARAMETER, List.of("UserService#<init>"), List.of());
        DetectedTransformation frameworkEvidence = DetectedTransformation.of(
                TransformationKind.CHANGE_FIELD_ANNOTATIONS, List.of("UserService#repository"), List.of());
        SemanticProfile profile = SemanticProfile.empty(anyChange())
                .with(SemanticDimension.PATTERN, SemanticClassification.of(dependencyInjectionConcept(), List.of(patternEvidence)))
                .with(SemanticDimension.FRAMEWORK, SemanticClassification.of(fieldToConstructorInjectionConcept(), List.of(frameworkEvidence)));

        List<SemanticClassification> classifications = classifier.classify(profile);

        assertThat(classifications.get(0).evidence()).containsExactlyInAnyOrder(patternEvidence, frameworkEvidence);
    }

    @Test
    void leavesAChangeWithNoClassificationOnAnyOtherDimensionUnclassifiedRatherThanGuessing() {
        SemanticProfile profile = SemanticProfile.empty(anyChange());

        assertThat(classifier.classify(profile)).isEmpty();
    }

    @Test
    void requiresBothThePatternAndFrameworkEvidenceTogetherRatherThanEitherAlone() {
        Change change = anyChange();
        SemanticProfile patternOnly = SemanticProfile.empty(change)
                .with(SemanticDimension.PATTERN, SemanticClassification.of(dependencyInjectionConcept(), change.matchedOccurrences()));
        SemanticProfile frameworkOnly = SemanticProfile.empty(change)
                .with(SemanticDimension.FRAMEWORK, SemanticClassification.of(fieldToConstructorInjectionConcept(), change.matchedOccurrences()));

        assertThat(classifier.classify(patternOnly)).isEmpty();
        assertThat(classifier.classify(frameworkOnly)).isEmpty();
    }

    @Test
    void rejectsATaxonomyForADifferentDimension() {
        Taxonomy patternTaxonomy = loader.load(SemanticDimension.PATTERN);

        assertThatThrownBy(() -> new IntentTaxonomyClassifier(patternTaxonomy))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private TaxonomyConcept dependencyInjectionConcept() {
        return loader.load(SemanticDimension.PATTERN).find("dependency-injection").orElseThrow();
    }

    private TaxonomyConcept fieldToConstructorInjectionConcept() {
        // FRAMEWORK-dimension content is owned by a FrameworkPlugin module, not athena-core
        // (see SemanticProfileTest) — synthesized here the same way, rather than depending on
        // any particular plugin's taxonomy data being present on athena-core's classpath.
        return TaxonomyConcept.of("spring-field-to-constructor-injection", SemanticDimension.FRAMEWORK,
                "Spring: Field to Constructor Injection", "", Optional.empty());
    }

    private Change anyChange() {
        DetectedTransformation t = DetectedTransformation.of(TransformationKind.RENAME_SYMBOL,
                List.of("UserService#save", "UserService#persist"), List.of());
        return new ChangeGrouper().group(List.of(t)).get(0);
    }
}
