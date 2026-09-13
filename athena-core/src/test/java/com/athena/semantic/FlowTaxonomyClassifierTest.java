package com.athena.semantic;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlowTaxonomyClassifierTest {

    private final FlowTaxonomyClassifier classifier =
            new FlowTaxonomyClassifier(new TaxonomyLoader().load(SemanticDimension.FEATURE));

    @Test
    void classifiesAChangeCorrelatingWithASingleWordFlowConceptName() {
        assertThat(classifier.classify(addClass("CheckoutService")).get().concept().id())
                .isEqualTo("order-management-checkout");
    }

    @Test
    void classifiesAChangeCorrelatingWithAMultiWordFlowConceptName() {
        assertThat(classifier.classify(addClass("AddUserController")).get().concept().id())
                .isEqualTo("user-management-add-user");
    }

    @Test
    void correlationIsCaseInsensitive() {
        assertThat(classifier.classify(addClass("checkoutservice")).get().concept().id())
                .isEqualTo("order-management-checkout");
    }

    @Test
    void classifiesRegardlessOfTransformationKindUnlikeAKindOnlyClassifier() {
        Change renamed = changeOf(TransformationKind.RENAME_CLASS, "OldName", "CheckoutService");

        assertThat(classifier.classify(renamed).get().concept().id())
                .isEqualTo("order-management-checkout");
    }

    @Test
    void leavesANonCorrelatingChangeUnclassifiedRatherThanGuessing() {
        Optional<SemanticClassification> classification = classifier.classify(addClass("MathUtils"));

        assertThat(classification).isEmpty();
    }

    @Test
    void doesNotClassifyOnAMemberNameCoincidenceInsideAnUnrelatedType() {
        Change renamedField = changeOf(TransformationKind.RENAME_FIELD, "UserSettings#oldFlag", "UserSettings#loginReminder");

        assertThat(classifier.classify(renamedField)).isEmpty();
    }

    @Test
    void leavesAChangeWithNoMatchedOccurrencesUnclassified() {
        Change withNoMatchedOccurrences = new Change("Untitled", TransformationKind.ADD_CLASS, List.of(), List.of());

        assertThat(classifier.classify(withNoMatchedOccurrences)).isEmpty();
    }

    @Test
    void rejectsATaxonomyForADifferentDimension() {
        Taxonomy architectureTaxonomy = new TaxonomyLoader().load(SemanticDimension.ARCHITECTURE);

        assertThatThrownBy(() -> new FlowTaxonomyClassifier(architectureTaxonomy))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Change addClass(String simpleName) {
        return changeOf(TransformationKind.ADD_CLASS, simpleName);
    }

    private Change changeOf(TransformationKind kind, String... involved) {
        DetectedTransformation t = DetectedTransformation.of(kind, List.of(involved), List.of());
        return new ChangeGrouper().group(List.of(t)).get(0);
    }
}
