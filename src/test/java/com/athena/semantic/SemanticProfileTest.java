package com.athena.semantic;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticProfileTest {

    private final TaxonomyLoader loader = new TaxonomyLoader();

    @Test
    void anEmptyProfileHasNoClassifiedDimensions() {
        Change change = renameChange();

        SemanticProfile profile = SemanticProfile.empty(change);

        assertThat(profile.classifiedDimensions()).isEmpty();
        assertThat(profile.classifications(SemanticDimension.STRUCTURAL)).isEmpty();
    }

    @Test
    void multipleDimensionsCanCoexistOnTheSameProfileWithoutOneDerivingTheOthers() {
        Change change = renameChange();
        TaxonomyConcept diPattern = loader.load(SemanticDimension.PATTERN).find("dependency-injection").orElseThrow();
        TaxonomyConcept springDi = loader.load(SemanticDimension.FRAMEWORK).find("spring-field-to-constructor-injection").orElseThrow();
        TaxonomyConcept intent = loader.load(SemanticDimension.INTENT).find("improve-testability").orElseThrow();

        SemanticProfile profile = SemanticProfile.empty(change)
                .with(SemanticDimension.PATTERN, SemanticClassification.of(diPattern, change.matchedOccurrences()))
                .with(SemanticDimension.FRAMEWORK, SemanticClassification.of(springDi, change.matchedOccurrences()))
                .with(SemanticDimension.INTENT, SemanticClassification.of(intent, List.of()));

        assertThat(profile.classifiedDimensions())
                .containsExactlyInAnyOrder(SemanticDimension.PATTERN, SemanticDimension.FRAMEWORK, SemanticDimension.INTENT);
        assertThat(profile.classifications(SemanticDimension.PATTERN)).extracting(c -> c.concept().id())
                .containsExactly("dependency-injection");
        assertThat(profile.classifications(SemanticDimension.FRAMEWORK)).extracting(c -> c.concept().id())
                .containsExactly("spring-field-to-constructor-injection");
        assertThat(profile.classifications(SemanticDimension.ARCHITECTURE)).isEmpty();
    }

    @Test
    void multipleClassificationsCanAccumulateOnTheSameDimension() {
        Change change = renameChange();
        Taxonomy pattern = loader.load(SemanticDimension.PATTERN);

        SemanticProfile profile = SemanticProfile.empty(change)
                .with(SemanticDimension.PATTERN, SemanticClassification.of(pattern.find("adapter").orElseThrow(), List.of()))
                .with(SemanticDimension.PATTERN, SemanticClassification.of(pattern.find("facade").orElseThrow(), List.of()));

        assertThat(profile.classifications(SemanticDimension.PATTERN)).extracting(c -> c.concept().id())
                .containsExactly("adapter", "facade");
    }

    @Test
    void withReturnsANewProfileRatherThanMutatingTheOriginal() {
        Change change = renameChange();
        SemanticProfile original = SemanticProfile.empty(change);
        TaxonomyConcept concept = loader.load(SemanticDimension.INTENT).find("fix-defect").orElseThrow();

        SemanticProfile updated = original.with(SemanticDimension.INTENT, SemanticClassification.of(concept, List.of()));

        assertThat(original.classifiedDimensions()).isEmpty();
        assertThat(updated.classifiedDimensions()).containsExactly(SemanticDimension.INTENT);
    }

    private Change renameChange() {
        DetectedTransformation t = DetectedTransformation.of(TransformationKind.RENAME_SYMBOL,
                List.of("UserService#save", "UserService#persist"), List.of());
        return new ChangeGrouper().group(List.of(t)).get(0);
    }
}
