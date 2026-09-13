package com.athena.plugins;

import com.athena.plugin.java.TransformationDetector;
import com.athena.semantic.Change;
import com.athena.semantic.ChangeGrouper;
import com.athena.semantic.DetectedTransformation;

import java.nio.file.Path;
import java.util.List;

/**
 * Shared test-fixture helper: detects and groups Changes between two real
 * Java source trees, the same two steps {@code PrAnalyzer} performs via a
 * {@code LanguagePlugin} in production. Used by step-definition classes
 * across several packages that build a {@link Change} fixture directly
 * from real source text rather than exercising the web/CLI entry points —
 * kept in one place so those tests depend on the Java plugin's detection
 * behavior through a single seam instead of each repeating {@code new
 * TransformationDetector().detect(...)} + {@code new ChangeGrouper().group(...)}.
 */
public final class TestChanges {

    private TestChanges() {
    }

    public static List<Change> detect(Path baseRoot, Path headRoot) {
        List<DetectedTransformation> transformations = new TransformationDetector().detect(baseRoot, headRoot);
        return new ChangeGrouper().group(transformations);
    }
}
