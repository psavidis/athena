package com.athena.semantic;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestChangeGrouperTest {

    private static final String TEST_FILE = "core/src/test/java/GreeterTest.java";

    private final StructuralTaxonomyClassifier structural =
            new StructuralTaxonomyClassifier(new TaxonomyLoader().load(SemanticDimension.STRUCTURAL));
    private final TestChangeGrouper grouper = new TestChangeGrouper();

    @Test
    void foldsEveryTestChangeOfOneTestClassIntoOneGroup() {
        List<TestChangeGroup> groups = grouper.group(List.of(
                profileOf("GreeterTest#a", TEST_FILE),
                profileOf("GreeterTest#b", TEST_FILE),
                profileOf("GreeterTest#c", TEST_FILE)));

        assertThat(groups).singleElement().satisfies(group -> {
            assertThat(group.testClass()).isEqualTo("GreeterTest");
            assertThat(group.changeCount()).isEqualTo(3);
            assertThat(group.mergedClassifications()).hasSize(3);
            assertThat(group.evidence()).hasSize(3);
            assertThat(group.filesTouched()).containsExactly(TEST_FILE);
        });
    }

    @Test
    void countsANestedClassTowardItsTopLevelTestClass() {
        List<TestChangeGroup> groups = grouper.group(List.of(
                profileOf("GreeterTest#a", TEST_FILE),
                profileOf("GreeterTest.Fixtures#make", TEST_FILE)));

        assertThat(groups).singleElement().satisfies(group -> {
            assertThat(group.testClass()).isEqualTo("GreeterTest");
            assertThat(group.changeCount()).isEqualTo(2);
        });
    }

    @Test
    void givesEachTestClassItsOwnGroupInFirstSeenOrder() {
        List<TestChangeGroup> groups = grouper.group(List.of(
                profileOf("PricingTest#a", "core/src/test/java/PricingTest.java"),
                profileOf("GreeterTest#a", TEST_FILE)));

        assertThat(groups).extracting(TestChangeGroup::testClass).containsExactly("PricingTest", "GreeterTest");
    }

    @Test
    void leavesProductionChangesOut() {
        assertThat(grouper.group(List.of(profileOf("Greeter#a", "core/src/main/java/Greeter.java")))).isEmpty();
    }

    private SemanticProfile profileOf(String symbol, String file) {
        Change change = new ChangeGrouper().group(List.of(
                DetectedTransformation.of(TransformationKind.ADD_SYMBOL, List.of(symbol), List.of(file)))).get(0);
        SemanticProfile profile = SemanticProfile.empty(change);
        return structural.classify(change).map(c -> profile.with(SemanticDimension.STRUCTURAL, c)).orElse(profile);
    }
}
