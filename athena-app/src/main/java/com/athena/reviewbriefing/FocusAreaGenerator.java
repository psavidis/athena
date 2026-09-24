package com.athena.reviewbriefing;

import com.athena.semantic.Change;
import com.athena.semantic.SemanticDimension;
import com.athena.semantic.SemanticProfile;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Generates {@link ReviewBriefing#focusAreas()}: the 2-4 parts of a PR's
 * Changes that most deserve a reviewer's attention (tickets #220, #286). A
 * deliberately conservative, deterministic ranking over existing {@link
 * Change}/{@link SemanticProfile} data:
 *
 * <ol>
 *   <li>Production Changes before {@linkplain Change#isTestCode() test-code}
 *       Changes — a test only becomes a focus area when there are too few
 *       production Changes to fill the list.</li>
 *   <li>Then by kind precedence: BEHAVIORAL Changes, then relocations
 *       (move/rename), then public API changes (signatures, constructor
 *       parameters, annotation elements, enum constants, contract annotations),
 *       then everything else.</li>
 *   <li>Then by profile score: +2 per classification under {@link
 *       SemanticDimension#RESPONSIBILITY} or {@link
 *       SemanticDimension#ARCHITECTURE}, +1 per classification under any
 *       other dimension, +1 more if the Change recurs.</li>
 *   <li>Ties broken by occurrence count descending, then by detection order —
 *       never alphabetically, which put "Add …" titles first regardless of
 *       what they were.</li>
 *   <li>Take the top {@value #MAX_FOCUS_AREAS} — fewer if there are fewer
 *       Changes; never padded to a minimum.</li>
 * </ol>
 *
 * <p>Each resulting {@link BriefingItem} references its Change's {@link
 * Change#enclosingType()} as the semantic entity it concerns.
 */
public class FocusAreaGenerator {

    private static final int MAX_FOCUS_AREAS = 4;

    /** The ranked focus-area {@link BriefingItem}s for {@code changes}/{@code profiles}, most significant first. */
    public List<BriefingItem> generate(List<Change> changes, List<SemanticProfile> profiles) {
        return IntStream.range(0, changes.size())
                .mapToObj(i -> new ScoredChange(changes.get(i), score(profiles.get(i)), i))
                .sorted(Comparator.comparing((ScoredChange sc) -> sc.change().isTestCode())
                        .thenComparingInt(sc -> precedence(sc.change()))
                        .thenComparing(Comparator.comparingInt(ScoredChange::score).reversed())
                        .thenComparing(sc -> sc.change().occurrenceCount(), Comparator.reverseOrder())
                        .thenComparingInt(ScoredChange::detectionIndex))
                .limit(MAX_FOCUS_AREAS)
                .map(ScoredChange::toBriefingItem)
                .toList();
    }

    /**
     * Lower ranks first: behavioral, relocation, public API, everything else. {@code
     * CHANGE_FIELD_TYPE} stays in the last tier: a field's visibility isn't known here, and a
     * private field's type is not API.
     */
    private static int precedence(Change change) {
        return switch (change.kind()) {
            case CHANGE_CONTROL_FLOW -> 0;
            case MOVE_CLASS, MOVE_SYMBOL, MOVE_FIELD, RENAME_CLASS, RENAME_SYMBOL, RENAME_FIELD,
                 PULL_UP_FIELD, PULL_UP_SYMBOL -> 1;
            case CHANGE_METHOD_SIGNATURE, ADD_CONSTRUCTOR_PARAMETER, ADD_ANNOTATION_ELEMENT, REMOVE_ANNOTATION_ELEMENT,
                 CHANGE_ANNOTATION_ELEMENT_DEFAULT, ADD_ENUM_CONSTANT, REMOVE_ENUM_CONSTANT,
                 CHANGE_PARAMETER_ANNOTATIONS, CHANGE_METHOD_ANNOTATIONS -> 2;
            case ADD_SYMBOL, REMOVE_SYMBOL, ADD_CLASS, REMOVE_CLASS, ADD_FIELD, REMOVE_FIELD, EXTRACT_METHOD,
                 MECHANICAL_REPLACEMENT, FORMATTING_ONLY, CHANGE_FIELD_ANNOTATIONS, CHANGE_FIELD_TYPE,
                 MODIFY_METHOD_BODY, CHANGE_MODIFIERS, CHANGE_FIELD_VALUE,
                 ADD_DEPENDENCY, REMOVE_DEPENDENCY, CHANGE_DEPENDENCY -> 3;
        };
    }

    private int score(SemanticProfile profile) {
        int score = 0;
        for (SemanticDimension dimension : SemanticDimension.values()) {
            int weight = (dimension == SemanticDimension.RESPONSIBILITY || dimension == SemanticDimension.ARCHITECTURE)
                    ? 2 : 1;
            score += weight * profile.classifications(dimension).size();
        }
        if (profile.change().occurrenceCount() > 1) {
            score += 1;
        }
        return score;
    }

    private record ScoredChange(Change change, int score, int detectionIndex) {
        BriefingItem toBriefingItem() {
            return BriefingItem.of(change.title(), change.enclosingType());
        }
    }
}
