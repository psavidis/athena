package com.athena.semantic;

import java.util.List;

/**
 * Every test-code Change of one test class, folded together for the Explorer (ticket #287) so
 * production changes aren't buried under test fixtures. {@link #mergedClassifications()} are
 * the Structural classifications this group replaces, so a response builder can drop them from
 * a flat per-Change list before adding this group's one entry in their place.
 */
public final class TestChangeGroup {

    private final String testClass;
    private final int changeCount;
    private final List<DetectedTransformation> evidence;
    private final List<SemanticClassification> mergedClassifications;

    TestChangeGroup(String testClass, int changeCount, List<DetectedTransformation> evidence,
                    List<SemanticClassification> mergedClassifications) {
        this.testClass = testClass;
        this.changeCount = changeCount;
        this.evidence = List.copyOf(evidence);
        this.mergedClassifications = List.copyOf(mergedClassifications);
    }

    /** The top-level test class, e.g. {@code SpyAnnotationTest} for its nested {@code SpyAnnotationTest.Fixture}. */
    public String testClass() {
        return testClass;
    }

    /** How many Changes this group folds. */
    public int changeCount() {
        return changeCount;
    }

    public List<DetectedTransformation> evidence() {
        return evidence;
    }

    /** The distinct files the folded Changes touch, in first-seen order. */
    public List<String> filesTouched() {
        return evidence.stream().flatMap(occurrence -> occurrence.filesTouched().stream()).distinct().toList();
    }

    public List<SemanticClassification> mergedClassifications() {
        return mergedClassifications;
    }
}
