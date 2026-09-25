package com.athena.semantic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Folds the Structural and Framework classifications of {@linkplain Change#isTestCode()
 * test-code} Changes into one {@link TestChangeGroup} per test class (tickets #287, #315): a
 * test's JUnit lifecycle card belongs with the rest of that test's changes. Nested classes count toward
 * their top-level test class. Groups come out in the order their test classes were first seen.
 */
public final class TestChangeGrouper {

    public List<TestChangeGroup> group(List<SemanticProfile> profiles) {
        List<TestChangeGroup> groups = new ArrayList<>();
        byTestClass(profiles).forEach((testClass, members) -> groups.add(toGroup(testClass, members)));
        return groups;
    }

    /**
     * As {@link #group(List)}, leaving out each test Change whose every Structural classification
     * one of {@code repeated} already folds (ticket #363): that Change is shown by the repeated
     * change's entry, not again in its test class's.
     */
    public List<TestChangeGroup> group(List<SemanticProfile> profiles, List<RepeatedStructuralChange> repeated) {
        Set<SemanticClassification> folded = repeated.stream()
                .flatMap(change -> change.mergedClassifications().stream())
                .collect(Collectors.toSet());
        return group(profiles.stream().filter(profile -> !isFolded(profile, folded)).toList());
    }

    /**
     * The repeated test changes worth showing (ticket #374): those that take over every test
     * Change of at least one test class, so that class's own group goes away. Any other fold would
     * only add a card next to the groups it draws from, making the Explorer longer.
     */
    public List<RepeatedStructuralChange> shortening(List<SemanticProfile> profiles,
                                                     List<RepeatedStructuralChange> repeated) {
        Map<String, List<SemanticProfile>> byTestClass = byTestClass(profiles);
        return repeated.stream()
                .filter(change -> takesOverATestClass(change, byTestClass.values()))
                .toList();
    }

    /** The test-code profiles by top-level test class, in first-seen order. */
    private static Map<String, List<SemanticProfile>> byTestClass(List<SemanticProfile> profiles) {
        Map<String, List<SemanticProfile>> byTestClass = new LinkedHashMap<>();
        for (SemanticProfile profile : profiles) {
            if (profile.change().isTestCode()) {
                byTestClass.computeIfAbsent(topLevelType(profile.change().enclosingType()), type -> new ArrayList<>())
                        .add(profile);
            }
        }
        return byTestClass;
    }

    private static boolean takesOverATestClass(RepeatedStructuralChange change,
                                               Iterable<List<SemanticProfile>> testClasses) {
        Set<SemanticClassification> folded = Set.copyOf(change.mergedClassifications());
        for (List<SemanticProfile> members : testClasses) {
            if (members.stream().allMatch(profile -> isFolded(profile, folded))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFolded(SemanticProfile profile, Set<SemanticClassification> folded) {
        List<SemanticClassification> structural = profile.classifications(SemanticDimension.STRUCTURAL);
        return !structural.isEmpty() && folded.containsAll(structural);
    }

    private static TestChangeGroup toGroup(String testClass, List<SemanticProfile> members) {
        List<DetectedTransformation> evidence = new ArrayList<>();
        List<SemanticClassification> merged = new ArrayList<>();
        for (SemanticProfile member : members) {
            evidence.addAll(member.change().matchedOccurrences());
            merged.addAll(member.classifications(SemanticDimension.STRUCTURAL));
            merged.addAll(member.classifications(SemanticDimension.FRAMEWORK));
        }
        return new TestChangeGroup(testClass, members.size(), evidence, merged);
    }

    private static String topLevelType(String enclosingType) {
        int nested = enclosingType.indexOf('.');
        return nested < 0 ? enclosingType : enclosingType.substring(0, nested);
    }
}
