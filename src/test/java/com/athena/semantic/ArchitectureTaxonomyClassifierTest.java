package com.athena.semantic;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectureTaxonomyClassifierTest {

    private final ArchitectureTaxonomyClassifier classifier =
            new ArchitectureTaxonomyClassifier(new TaxonomyLoader().load(SemanticDimension.ARCHITECTURE));

    @Test
    void classifiesANewlyAddedControllerAsDrivingAdapter() {
        assertThat(classifier.classify(addClass("UserController")).get().concept().id())
                .isEqualTo("driving-adapter");
    }

    @Test
    void classifiesANewlyAddedRepositoryAsOutboundPort() {
        assertThat(classifier.classify(addClass("UserRepository")).get().concept().id())
                .isEqualTo("outbound-port");
    }

    @Test
    void classifiesARepositoryImplementationAsDrivenAdapter() {
        assertThat(classifier.classify(addClass("UserRepositoryImpl")).get().concept().id())
                .isEqualTo("driven-adapter");
    }

    @Test
    void classifiesARenameIntoTheConventionAsWellAsAnAdd() {
        assertThat(classifier.classify(renameClass("UserDao", "UserController")).get().concept().id())
                .isEqualTo("driving-adapter");
    }

    @Test
    void doesNotClassifyAMemberLevelChangeInsideAnAlreadyNamedType() {
        Optional<SemanticClassification> classification =
                classifier.classify(changeOf(TransformationKind.ADD_SYMBOL, "UserController#create"));

        assertThat(classification).isEmpty();
    }

    @Test
    void leavesAnUnrecognizedNameUnclassified() {
        assertThat(classifier.classify(addClass("Helper"))).isEmpty();
    }

    private Change addClass(String simpleName) {
        return changeOf(TransformationKind.ADD_CLASS, simpleName);
    }

    private Change renameClass(String from, String to) {
        return changeOf(TransformationKind.RENAME_CLASS, from, to);
    }

    private Change changeOf(TransformationKind kind, String... involved) {
        DetectedTransformation t = DetectedTransformation.of(kind, List.of(involved), List.of());
        return new ChangeGrouper().group(List.of(t)).get(0);
    }
}
