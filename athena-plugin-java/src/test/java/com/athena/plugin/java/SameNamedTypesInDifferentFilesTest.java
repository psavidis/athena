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
 * Two unrelated types that share a simple name in different packages (spring-framework has
 * several {@code Person}s and {@code Scope}s) are never compared with each other: "the same
 * method/field/constructor/constant in both revisions" means the same file too. Found by the
 * #263 corpus check on spring-framework-37268, where unrelated same-named test types were
 * paired across files and reported as annotation changes in files the PR never touched.
 */
class SameNamedTypesInDifferentFilesTest {

    @TempDir
    Path baseRoot;

    @TempDir
    Path headRoot;

    @Test
    void unchangedSameNamedTypesInDifferentFilesProduceNoTransformations() throws IOException {
        String first = "package a;\npublic class Person {\n    Person(@Nullable String name) { }\n    String name() { return \"a\"; }\n"
                + "    private int age;\n}\n";
        String second = "package b;\npublic class Person {\n    Person(String name) { this.name = name; }\n    String name() { return \"b\"; }\n"
                + "    private long age;\n}\n";
        String annotationA = "package a;\npublic @interface Scope {\n    String value() default \"x\";\n}\n";
        String annotationB = "package b;\npublic @interface Scope {\n    String value() default \"y\";\n}\n";
        for (Path root : List.of(baseRoot, headRoot)) {
            write(root, "a/Person.java", first);
            write(root, "b/Person.java", second);
            write(root, "a/Scope.java", annotationA);
            write(root, "b/Scope.java", annotationB);
        }

        assertThat(detect()).isEmpty();
    }

    @Test
    void aChangeInOneOfTwoSameNamedTypesIsAttributedToItsOwnFile() throws IOException {
        write(baseRoot, "a/Person.java", "package a;\npublic class Person {\n    String name() { return \"a\"; }\n}\n");
        write(headRoot, "a/Person.java", "package a;\npublic class Person {\n    String name() { return \"a\"; }\n}\n");
        write(baseRoot, "b/Person.java", "package b;\npublic class Person {\n    String name() { return \"b\"; }\n}\n");
        write(headRoot, "b/Person.java", "package b;\npublic class Person {\n    String name(String prefix) { return prefix + \"b\"; }\n}\n");

        List<DetectedTransformation> transformations = detect();

        assertThat(transformations).extracting(DetectedTransformation::kind)
                .containsExactly(TransformationKind.CHANGE_METHOD_SIGNATURE);
        assertThat(transformations.get(0).filesTouched()).containsOnly("b/Person.java");
    }

    private List<DetectedTransformation> detect() {
        return new TransformationDetector().detect(baseRoot, headRoot);
    }

    private static void write(Path root, String relativePath, String source) throws IOException {
        Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
    }
}
