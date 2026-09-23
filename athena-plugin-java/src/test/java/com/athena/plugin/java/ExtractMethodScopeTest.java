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
 * An extracted method is only reported when its caller is part of the change and the
 * extracted body has substance — so a new empty {@code close()} is never "extracted from"
 * some untouched file that happens to call {@code close()} (ticket #269, the keycloak
 * false positive from the #258 evaluation).
 */
class ExtractMethodScopeTest {

    @TempDir
    Path baseRoot;

    @TempDir
    Path headRoot;

    @Test
    void aGenuineExtractionIsStillDetected() throws IOException {
        write(baseRoot, "Greeter", "public class Greeter {\n    String greet() {\n        String g = \"Hello, \" + \"world\";\n        return g;\n    }\n}\n");
        write(headRoot, "Greeter", "public class Greeter {\n    String greet() {\n        return build();\n    }\n"
                + "    String build() {\n        String g = \"Hello, \" + \"world\";\n        return g;\n    }\n}\n");

        assertThat(detect()).anyMatch(t -> t.kind() == TransformationKind.EXTRACT_METHOD
                && t.involvedDescriptions().equals(List.of("Greeter#greet", "Greeter#build")));
    }

    @Test
    void aNewEmptyMethodIsNotExtractedFromAnUnchangedCaller() throws IOException {
        String unchanged = "public class Distribution {\n    void stop(java.io.Closeable c) throws Exception {\n        c.close();\n    }\n}\n";
        write(baseRoot, "Distribution", unchanged);
        write(headRoot, "Distribution", unchanged);
        write(headRoot, "ExecutorFactory", "public class ExecutorFactory {\n    public void close() {\n    }\n}\n");

        List<DetectedTransformation> transformations = detect();

        assertThat(transformations).noneMatch(t -> t.kind() == TransformationKind.EXTRACT_METHOD);
        assertThat(transformations).noneMatch(t -> t.filesTouched().contains("Distribution.java"));
        assertThat(transformations).anyMatch(t -> t.kind() == TransformationKind.ADD_SYMBOL
                && t.involvedDescriptions().equals(List.of("ExecutorFactory#close")));
    }

    @Test
    void aNewEmptyMethodCalledFromAChangedCallerIsAnAdditionNotAnExtraction() throws IOException {
        write(baseRoot, "Factory", "public class Factory {\n    Object create() {\n        return this;\n    }\n}\n");
        write(headRoot, "Factory", "public class Factory {\n    void init() {\n    }\n    Object create() {\n        init();\n        return this;\n    }\n}\n");

        List<DetectedTransformation> transformations = detect();

        assertThat(transformations).noneMatch(t -> t.kind() == TransformationKind.EXTRACT_METHOD);
        assertThat(transformations).anyMatch(t -> t.kind() == TransformationKind.ADD_SYMBOL
                && t.involvedDescriptions().equals(List.of("Factory#init")));
    }

    private List<DetectedTransformation> detect() {
        return new TransformationDetector().detect(baseRoot, headRoot);
    }

    private static void write(Path root, String className, String source) throws IOException {
        Files.writeString(root.resolve(className + ".java"), source);
    }
}
