package com.athena.semantic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Folds the Structural and Framework classifications of {@linkplain Change#isTestCode()
 * test-code} Changes into one {@link TestChangeGroup} per test class (tickets #287, #315): a
 * test's JUnit lifecycle card belongs with the rest of that test's changes. Nested classes count toward
 * their top-level test class. Groups come out in the order their test classes were first seen.
 */
public final class TestChangeGrouper {

    public List<TestChangeGroup> group(List<SemanticProfile> profiles) {
        Map<String, List<SemanticProfile>> byTestClass = new LinkedHashMap<>();
        for (SemanticProfile profile : profiles) {
            if (profile.change().isTestCode()) {
                byTestClass.computeIfAbsent(topLevelType(profile.change().enclosingType()), type -> new ArrayList<>())
                        .add(profile);
            }
        }
        List<TestChangeGroup> groups = new ArrayList<>();
        byTestClass.forEach((testClass, members) -> groups.add(toGroup(testClass, members)));
        return groups;
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
