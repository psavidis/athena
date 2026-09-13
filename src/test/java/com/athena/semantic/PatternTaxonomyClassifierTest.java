package com.athena.semantic;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PatternTaxonomyClassifierTest {

    private final PatternTaxonomyClassifier classifier =
            new PatternTaxonomyClassifier(new TaxonomyLoader().load(SemanticDimension.PATTERN));

    @Test
    void classifiesANewlyAddedBuilderAsBuilder() {
        assertThat(classifier.classify(addClass("UserBuilder")).get().concept().id()).isEqualTo("builder");
    }

    @Test
    void classifiesANewlyAddedFactoryAsFactory() {
        assertThat(classifier.classify(addClass("UserFactory")).get().concept().id()).isEqualTo("factory");
    }

    @Test
    void classifiesANewlyAddedMapperAsMapper() {
        assertThat(classifier.classify(addClass("UserMapper")).get().concept().id()).isEqualTo("mapper");
    }

    @Test
    void classifiesANewlyAddedConverterAsMapperToo() {
        assertThat(classifier.classify(addClass("UserConverter")).get().concept().id()).isEqualTo("mapper");
    }

    @Test
    void classifiesANewlyAddedRepositoryAsRepository() {
        assertThat(classifier.classify(addClass("UserRepository")).get().concept().id()).isEqualTo("repository");
    }

    @Test
    void doesNotClassifyAMemberLevelChangeInsideAnAlreadyNamedType() {
        Optional<SemanticClassification> classification =
                classifier.classify(changeOf(TransformationKind.ADD_SYMBOL, "UserBuilder#withName"));

        assertThat(classification).isEmpty();
    }

    @Test
    void leavesAnUnrecognizedNameUnclassified() {
        assertThat(classifier.classify(addClass("Helper"))).isEmpty();
    }

    private Change addClass(String simpleName) {
        return changeOf(TransformationKind.ADD_CLASS, simpleName);
    }

    private Change changeOf(TransformationKind kind, String... involved) {
        DetectedTransformation t = DetectedTransformation.of(kind, List.of(involved), List.of());
        return new ChangeGrouper().group(List.of(t)).get(0);
    }
}
