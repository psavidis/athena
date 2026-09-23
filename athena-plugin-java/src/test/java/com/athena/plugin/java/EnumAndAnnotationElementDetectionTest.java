package com.athena.plugin.java;

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
 * Added/removed enum constants and annotation-type elements, and changed element
 * defaults, are reported as Changes (ticket #266).
 */
class EnumAndAnnotationElementDetectionTest {

    @TempDir
    Path baseRoot;

    @TempDir
    Path headRoot;

    @Test
    void anAddedEnumConstantIsDetected() throws IOException {
        write(baseRoot, "Event", "public enum Event { REQUEST, RESPONSE }\n");
        write(headRoot, "Event", "public enum Event { REQUEST, RESPONSE, EXCHANGE_RESPONSE }\n");

        assertThat(detect()).anyMatch(t -> t.kind() == TransformationKind.ADD_ENUM_CONSTANT
                && t.involvedDescriptions().equals(List.of("Event#EXCHANGE_RESPONSE")));
    }

    @Test
    void aRemovedEnumConstantIsDetected() throws IOException {
        write(baseRoot, "Status", "public enum Status { ACTIVE, LEGACY }\n");
        write(headRoot, "Status", "public enum Status { ACTIVE }\n");

        assertThat(detect()).anyMatch(t -> t.kind() == TransformationKind.REMOVE_ENUM_CONSTANT
                && t.involvedDescriptions().equals(List.of("Status#LEGACY")));
    }

    @Test
    void reorderingConstantsIsNotAnAdditionOrRemoval() throws IOException {
        write(baseRoot, "Status", "public enum Status { ACTIVE, DISABLED }\n");
        write(headRoot, "Status", "public enum Status { DISABLED, ACTIVE }\n");

        assertThat(detect()).noneMatch(t -> t.kind() == TransformationKind.ADD_ENUM_CONSTANT
                || t.kind() == TransformationKind.REMOVE_ENUM_CONSTANT);
    }

    @Test
    void aConstantAddedToANestedEnumIsNamedWithItsOuterClass() throws IOException {
        write(baseRoot, "Policy", "public class Policy {\n  enum Event { REQUEST }\n}\n");
        write(headRoot, "Policy", "public class Policy {\n  enum Event { REQUEST, RESPONSE }\n}\n");

        assertThat(detect()).anyMatch(t -> t.kind() == TransformationKind.ADD_ENUM_CONSTANT
                && t.involvedDescriptions().equals(List.of("Policy.Event#RESPONSE")));
    }

    @Test
    void anAddedAnnotationElementIsDetectedWithItsDefaultAsEvidence() throws IOException {
        write(baseRoot, "Spy", "public @interface Spy {\n}\n");
        write(headRoot, "Spy", "public @interface Spy {\n    String mockMaker() default \"\";\n}\n");

        DetectedTransformation added = single(TransformationKind.ADD_ANNOTATION_ELEMENT);

        assertThat(added.involvedDescriptions()).containsExactly("Spy#mockMaker");
        assertThat(added.diffText()).contains("+String mockMaker() default \"\";");
    }

    @Test
    void aRemovedAnnotationElementIsDetected() throws IOException {
        write(baseRoot, "Retry", "public @interface Retry {\n    int attempts();\n    int delay();\n}\n");
        write(headRoot, "Retry", "public @interface Retry {\n    int attempts();\n}\n");

        assertThat(detect()).anyMatch(t -> t.kind() == TransformationKind.REMOVE_ANNOTATION_ELEMENT
                && t.involvedDescriptions().equals(List.of("Retry#delay")));
    }

    @Test
    void aChangedAnnotationElementDefaultIsDetected() throws IOException {
        write(baseRoot, "Retry", "public @interface Retry {\n    int attempts() default 3;\n}\n");
        write(headRoot, "Retry", "public @interface Retry {\n    int attempts() default 5;\n}\n");

        DetectedTransformation changed = single(TransformationKind.CHANGE_ANNOTATION_ELEMENT_DEFAULT);

        assertThat(changed.involvedDescriptions()).containsExactly("Retry#attempts");
        assertThat(changed.diffText()).contains("-int attempts() default 3;").contains("+int attempts() default 5;");
    }

    @Test
    void anUnchangedAnnotationElementIsNotReported() throws IOException {
        String annotation = "public @interface Retry {\n    int attempts() default 3;\n}\n";
        write(baseRoot, "Retry", annotation);
        write(headRoot, "Retry", annotation);

        assertThat(detect()).isEmpty();
    }

    private DetectedTransformation single(TransformationKind kind) {
        List<DetectedTransformation> ofKind = detect().stream().filter(t -> t.kind() == kind).toList();
        assertThat(ofKind).hasSize(1);
        return ofKind.get(0);
    }

    private List<DetectedTransformation> detect() {
        return new TransformationDetector().detect(baseRoot, headRoot);
    }

    private static void write(Path root, String typeName, String source) throws IOException {
        Files.writeString(root.resolve(typeName + ".java"), source);
    }
}
