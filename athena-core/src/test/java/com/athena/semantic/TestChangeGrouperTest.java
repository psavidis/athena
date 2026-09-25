package com.athena.semantic;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TestChangeGrouperTest {

    private static final String TEST_FILE = "core/src/test/java/GreeterTest.java";
    private static final String PARSER_FILE = "core/src/test/java/ParserTest.java";

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

    @Test
    void foldsATestChangesFrameworkClassificationIntoItsGroupToo() {
        SemanticProfile profile = profileOf("GreeterTest#setUp", TEST_FILE);
        TaxonomyConcept junit = TaxonomyConcept.of("junit-lifecycle", SemanticDimension.FRAMEWORK,
                "JUnit: Lifecycle/Extensions", "JUnit lifecycle annotations.", Optional.empty());
        SemanticClassification lifecycle = SemanticClassification.of(junit, profile.change().matchedOccurrences());
        SemanticProfile withLifecycle = profile.with(SemanticDimension.FRAMEWORK, lifecycle);

        assertThat(grouper.group(List.of(withLifecycle))).singleElement()
                .satisfies(group -> assertThat(group.mergedClassifications()).contains(lifecycle));
    }

    // Ticket #363: a test change folded into a repeated change across test classes gets no group of its own.

    @Test
    void leavesOutTestChangesFoldedIntoARepeatedChange() {
        SemanticProfile first = profileOf("GreeterTest#afterEachTest", TEST_FILE);
        SemanticProfile second = profileOf("ParserTest#afterEachTest", "core/src/test/java/ParserTest.java");
        SemanticProfile leftover = profileOf("GreeterTest#setUp", TEST_FILE);
        List<RepeatedStructuralChange> repeated = new RepeatedStructuralChangeGrouper().group(List.of(first, second, leftover));

        assertThat(grouper.group(List.of(first, second, leftover), repeated)).singleElement().satisfies(group -> {
            assertThat(group.testClass()).isEqualTo("GreeterTest");
            assertThat(group.changeCount()).isEqualTo(1);
        });
    }

    @Test
    void keepsATestChangeWithoutAStructuralClassificationInItsGroup() {
        SemanticProfile unclassified = SemanticProfile.empty(profileOf("GreeterTest#a", TEST_FILE).change());

        assertThat(grouper.group(List.of(unclassified), List.of())).singleElement()
                .satisfies(group -> assertThat(group.changeCount()).isEqualTo(1));
    }

    // Ticket #374: only folds that take over a whole test class are kept.

    @Test
    void keepsAFoldThatTakesOverEveryChangeOfATestClass() {
        SemanticProfile first = profileOf("GreeterTest#testRejoin", TEST_FILE);
        SemanticProfile second = profileOf("ParserTest#testRejoin", PARSER_FILE);
        SemanticProfile leftover = profileOf("ParserTest#ownTest", PARSER_FILE);
        List<SemanticProfile> profiles = List.of(first, second, leftover);
        List<RepeatedStructuralChange> repeated = new RepeatedStructuralChangeGrouper().group(profiles);

        assertThat(grouper.shortening(profiles, repeated)).isEqualTo(repeated);
    }

    @Test
    void dropsAFoldThatLeavesEveryTestClassWithItsOwnCard() {
        List<SemanticProfile> profiles = List.of(
                profileOf("GreeterTest#testRejoin", TEST_FILE), profileOf("GreeterTest#ownTest", TEST_FILE),
                profileOf("ParserTest#testRejoin", PARSER_FILE), profileOf("ParserTest#otherTest", PARSER_FILE));
        List<RepeatedStructuralChange> repeated = new RepeatedStructuralChangeGrouper().group(profiles);

        assertThat(repeated).hasSize(1);
        assertThat(grouper.shortening(profiles, repeated)).isEmpty();
        assertThat(grouper.group(profiles, grouper.shortening(profiles, repeated)))
                .extracting(TestChangeGroup::changeCount).containsExactly(2, 2);
    }

    @Test
    void countsANestedClassesChangesTowardItsTopLevelTestClass() {
        List<SemanticProfile> profiles = List.of(
                profileOf("GreeterTest#testRejoin", TEST_FILE), profileOf("GreeterTest.Nested#helper", TEST_FILE),
                profileOf("ParserTest#testRejoin", PARSER_FILE), profileOf("ParserTest#otherTest", PARSER_FILE));

        assertThat(grouper.shortening(profiles, new RepeatedStructuralChangeGrouper().group(profiles))).isEmpty();
    }

    private SemanticProfile profileOf(String symbol, String file) {
        Change change = new ChangeGrouper().group(List.of(
                DetectedTransformation.of(TransformationKind.ADD_SYMBOL, List.of(symbol), List.of(file)))).get(0);
        SemanticProfile profile = SemanticProfile.empty(change);
        return structural.classify(change).map(c -> profile.with(SemanticDimension.STRUCTURAL, c)).orElse(profile);
    }
}
