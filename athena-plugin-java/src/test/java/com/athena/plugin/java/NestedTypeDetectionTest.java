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
 * Members of nested and inner classes are detected like those of top-level classes and
 * are named with their outer classes, e.g. {@code KafkaProperties.Listener#field}
 * (ticket #265).
 */
class NestedTypeDetectionTest {

    @TempDir
    Path baseRoot;

    @TempDir
    Path headRoot;

    @Test
    void aFieldAddedToAStaticNestedClassIsDetectedWithItsQualifiedName() throws IOException {
        write(baseRoot, "KafkaProperties", "public class KafkaProperties {\n  public static class Listener {\n  }\n}\n");
        write(headRoot, "KafkaProperties",
                "public class KafkaProperties {\n  public static class Listener {\n    private boolean awaitAsyncResultsOnStop;\n  }\n}\n");

        assertThat(detect()).anyMatch(t -> t.kind() == TransformationKind.ADD_FIELD
                && t.involvedDescriptions().equals(List.of("KafkaProperties.Listener#awaitAsyncResultsOnStop")));
    }

    @Test
    void aMethodRemovedFromAnInnerClassIsDetected() throws IOException {
        write(baseRoot, "Config", "public class Config {\n  class Registrar {\n    void register() { }\n  }\n}\n");
        write(headRoot, "Config", "public class Config {\n  class Registrar {\n  }\n}\n");

        assertThat(detect()).anyMatch(t -> t.kind() == TransformationKind.REMOVE_SYMBOL
                && t.involvedDescriptions().equals(List.of("Config.Registrar#register")));
    }

    @Test
    void sameNamedNestedClassesInDifferentOuterClassesAreNotConfused() throws IOException {
        String withMethod = "public class %s {\n  static class Registrar {\n    void register() { }\n  }\n}\n";
        String withoutMethod = "public class %s {\n  static class Registrar {\n  }\n}\n";
        write(baseRoot, "Reactive", withMethod.formatted("Reactive"));
        write(baseRoot, "Servlet", withMethod.formatted("Servlet"));
        write(headRoot, "Reactive", withMethod.formatted("Reactive"));
        write(headRoot, "Servlet", withoutMethod.formatted("Servlet"));

        List<DetectedTransformation> transformations = detect();

        assertThat(transformations).anyMatch(t -> t.kind() == TransformationKind.REMOVE_SYMBOL
                && t.involvedDescriptions().equals(List.of("Servlet.Registrar#register")));
        assertThat(transformations).noneMatch(t -> t.involvedDescriptions().stream()
                .anyMatch(d -> d.startsWith("Reactive.")));
    }

    @Test
    void aWholeNestedClassAddedIsAClassAddition() throws IOException {
        write(baseRoot, "Outer", "public class Outer {\n}\n");
        write(headRoot, "Outer", "public class Outer {\n  static class Retry {\n    int attempts() { return 3; }\n  }\n}\n");

        assertThat(detect()).anyMatch(t -> t.kind() == TransformationKind.ADD_CLASS
                && t.involvedDescriptions().equals(List.of("Outer.Retry")));
    }

    @Test
    void aMemberTwoLevelsDeepIsDetected() throws IOException {
        write(baseRoot, "Outer", "public class Outer {\n  static class Middle {\n    static class Inner {\n    }\n  }\n}\n");
        write(headRoot, "Outer",
                "public class Outer {\n  static class Middle {\n    static class Inner {\n      int compute() { return 1; }\n    }\n  }\n}\n");

        assertThat(detect()).anyMatch(t -> t.kind() == TransformationKind.ADD_SYMBOL
                && t.involvedDescriptions().equals(List.of("Outer.Middle.Inner#compute")));
    }

    @Test
    void aTopLevelClassMemberKeepsItsUnqualifiedName() throws IOException {
        write(baseRoot, "Greeter", "public class Greeter {\n}\n");
        write(headRoot, "Greeter", "public class Greeter {\n  String greet() { return \"hi\"; }\n}\n");

        assertThat(detect()).anyMatch(t -> t.kind() == TransformationKind.ADD_SYMBOL
                && t.involvedDescriptions().equals(List.of("Greeter#greet")));
    }

    private List<DetectedTransformation> detect() {
        return new TransformationDetector().detect(baseRoot, headRoot);
    }

    private static void write(Path root, String className, String source) throws IOException {
        Files.writeString(root.resolve(className + ".java"), source);
    }
}
