package com.athena.plugin.java;

import com.athena.semantic.ChangeCategory;
import com.athena.semantic.DetectedTransformation;
import com.athena.semantic.TransformationKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Condition and control-flow edits inside a matched method are reported by the same
 * detector every other transformation comes from, as a BEHAVIORAL kind (ticket #263).
 */
class ControlFlowChangeDetectionTest {

    @TempDir
    Path baseRoot;

    @TempDir
    Path headRoot;

    @Test
    void aChangedConditionIsAControlFlowChangeWithTheMethodAsEvidence() throws IOException {
        write(baseRoot, "Guard", "public class Guard {\n    boolean allowed(User u) {\n        if (u.isActive()) { return true; }\n        return false;\n    }\n}\n");
        write(headRoot, "Guard", "public class Guard {\n    boolean allowed(User u) {\n        if (u.isActive() && u.hasPermission()) { return true; }\n        return false;\n    }\n}\n");

        List<DetectedTransformation> transformations = detect();

        assertThat(transformations).singleElement().satisfies(change -> {
            assertThat(change.kind()).isEqualTo(TransformationKind.CHANGE_CONTROL_FLOW);
            assertThat(ChangeCategory.of(change.kind())).isEqualTo(ChangeCategory.BEHAVIORAL);
            assertThat(change.involvedDescriptions()).containsExactly("Guard#allowed", "condition changed");
            assertThat(change.diffText()).contains("-        if (u.isActive()) { return true; }")
                    .contains("+        if (u.isActive() && u.hasPermission()) { return true; }");
        });
    }

    @Test
    void aControlFlowChangeInsideANestedClassIsNamedWithItsOuterClass() throws IOException {
        write(baseRoot, "Cache", "public class Cache {\n  static class Node {\n    void remove() { size(); }\n  }\n}\n");
        write(headRoot, "Cache", "public class Cache {\n  static class Node {\n    void remove() { if (gone()) { return; } size(); }\n  }\n}\n");

        assertThat(detect()).singleElement().satisfies(change ->
                assertThat(change.involvedDescriptions()).containsExactly("Cache.Node#remove", "branch added"));
    }

    @Test
    void aChangedCallIsNotAControlFlowChange() throws IOException {
        write(baseRoot, "Greeter", "public class Greeter {\n    String greet() { return formatA(); }\n}\n");
        write(headRoot, "Greeter", "public class Greeter {\n    String greet() { return formatB(); }\n}\n");

        assertThat(detect()).noneMatch(t -> t.kind() == TransformationKind.CHANGE_CONTROL_FLOW);
    }

    @Test
    void aFormattingOnlyEditIsNotAControlFlowChange() throws IOException {
        write(baseRoot, "Guard", "public class Guard {\n    boolean ok(int a) { if (a > 0) { return true; } return false; }\n}\n");
        write(headRoot, "Guard", "public class Guard {\n    boolean ok(int a) {\n        if (a > 0) {\n            return true;\n        }\n        return false;\n    }\n}\n");

        assertThat(detect()).extracting(DetectedTransformation::kind)
                .contains(TransformationKind.FORMATTING_ONLY)
                .doesNotContain(TransformationKind.CHANGE_CONTROL_FLOW);
    }

    private List<DetectedTransformation> detect() {
        return new TransformationDetector().detect(baseRoot, headRoot);
    }

    private static void write(Path root, String className, String source) throws IOException {
        Files.writeString(root.resolve(className + ".java"), source);
    }
}
