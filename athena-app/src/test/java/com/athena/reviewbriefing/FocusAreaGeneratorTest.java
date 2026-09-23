package com.athena.reviewbriefing;

import com.athena.semantic.Change;
import com.athena.semantic.ChangeGrouper;
import com.athena.semantic.DetectedTransformation;
import com.athena.semantic.SemanticProfile;
import com.athena.semantic.TransformationKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dedicated unit test for {@link FocusAreaGenerator} (tickets #220, #286): production before
 * test code, then BEHAVIORAL > relocations > API changes > everything else, ties broken by
 * occurrences and then detection order. Changes are built through the public
 * {@link ChangeGrouper}; profiles are left empty so only the ranking rules themselves decide.
 */
class FocusAreaGeneratorTest {

    private static final String MAIN = "core/src/main/java/";
    private static final String TEST = "core/src/test/java/";

    private final FocusAreaGenerator generator = new FocusAreaGenerator();

    @Test
    void producesNoFocusAreasWhenThereAreNoChanges() {
        List<BriefingItem> focusAreas = generator.generate(List.of(), List.of());

        assertThat(focusAreas).isNotNull().isEmpty();
    }

    @Test
    void prefersProductionChangesOverTestChanges() {
        List<BriefingItem> focusAreas = generate(
                transformation(TransformationKind.ADD_SYMBOL, "FooTest#a", TEST + "FooTest.java"),
                transformation(TransformationKind.ADD_SYMBOL, "FooTest#b", TEST + "FooTest.java"),
                transformation(TransformationKind.ADD_SYMBOL, "Foo#a", MAIN + "Foo.java"),
                transformation(TransformationKind.ADD_SYMBOL, "Foo#b", MAIN + "Foo.java"),
                transformation(TransformationKind.ADD_SYMBOL, "Foo#c", MAIN + "Foo.java"),
                transformation(TransformationKind.ADD_SYMBOL, "Foo#d", MAIN + "Foo.java"));

        assertThat(focusAreas).extracting(BriefingItem::description).noneMatch(title -> title.contains("FooTest"));
    }

    @Test
    void fillsWithTestChangesAfterTheProductionChangesWhenThereAreFewerThanFour() {
        List<BriefingItem> focusAreas = generate(
                DetectedTransformation.of(TransformationKind.CHANGE_CONTROL_FLOW, List.of("FooTest#a", "branch added"),
                        List.of(TEST + "FooTest.java")),
                transformation(TransformationKind.ADD_SYMBOL, "Foo#a", MAIN + "Foo.java"));

        assertThat(focusAreas).extracting(BriefingItem::description)
                .satisfiesExactly(first -> assertThat(first).contains("Foo#a"),
                        second -> assertThat(second).contains("FooTest#a"));
    }

    @Test
    void ranksBehavioralThenRelocationThenApiThenEverythingElse() {
        List<BriefingItem> focusAreas = generate(
                transformation(TransformationKind.ADD_CLASS, "Added", MAIN + "Added.java"),
                transformation(TransformationKind.CHANGE_METHOD_SIGNATURE, "Api#call", MAIN + "Api.java"),
                DetectedTransformation.of(TransformationKind.MOVE_SYMBOL, List.of("From#m", "To#m"),
                        List.of(MAIN + "From.java", MAIN + "To.java")),
                DetectedTransformation.of(TransformationKind.CHANGE_CONTROL_FLOW, List.of("Flow#run", "branch added"),
                        List.of(MAIN + "Flow.java")));

        assertThat(focusAreas).extracting(BriefingItem::entityReference)
                .containsExactly(Optional.of("Flow"), Optional.of("From"),
                        Optional.of("Api"), Optional.of("Added"));
    }

    @Test
    void breaksTiesByDetectionOrderRatherThanAlphabetically() {
        List<BriefingItem> focusAreas = generate(
                transformation(TransformationKind.ADD_CLASS, "Zulu", MAIN + "Zulu.java"),
                transformation(TransformationKind.ADD_CLASS, "Mike", MAIN + "Mike.java"),
                transformation(TransformationKind.ADD_CLASS, "Alpha", MAIN + "Alpha.java"));

        assertThat(focusAreas).extracting(item -> item.entityReference().orElseThrow())
                .containsExactly("Zulu", "Mike", "Alpha");
    }

    @Test
    void breaksTiesByOccurrenceCountBeforeDetectionOrder() {
        List<BriefingItem> focusAreas = generate(
                transformation(TransformationKind.ADD_CLASS, "Once", MAIN + "Once.java"),
                DetectedTransformation.withOccurrences(TransformationKind.ADD_CLASS, List.of("Often"),
                        List.of(MAIN + "Often.java"), 3));

        assertThat(focusAreas).extracting(item -> item.entityReference().orElseThrow())
                .containsExactly("Often", "Once");
    }

    @Test
    void keepsAtMostFourFocusAreas() {
        List<DetectedTransformation> transformations = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            transformations.add(transformation(TransformationKind.ADD_CLASS, "C" + i, MAIN + "C" + i + ".java"));
        }

        assertThat(generate(transformations.toArray(DetectedTransformation[]::new))).hasSize(4);
    }

    private List<BriefingItem> generate(DetectedTransformation... transformations) {
        List<Change> changes = new ArrayList<>();
        for (DetectedTransformation transformation : transformations) {
            changes.addAll(new ChangeGrouper().group(List.of(transformation)));
        }
        return generator.generate(changes, changes.stream().map(SemanticProfile::empty).toList());
    }

    private static DetectedTransformation transformation(TransformationKind kind, String symbol, String file) {
        return DetectedTransformation.of(kind, List.of(symbol), List.of(file));
    }
}
