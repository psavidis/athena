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
 * Structural detection works from the files that differ between the two revisions only
 * (ticket #271): its cost follows the size of the change, and no transformation can cite
 * a file the change didn't touch.
 */
class ChangedFileScopeTest {

    @TempDir
    Path baseRoot;

    @TempDir
    Path headRoot;

    @Test
    void theChangedFilesAreThoseAddedRemovedOrWithDifferentContent() throws IOException {
        write(baseRoot, "Same.java", "class Same { }\n");
        write(headRoot, "Same.java", "class Same { }\n");
        write(baseRoot, "Edited.java", "class Edited { }\n");
        write(headRoot, "Edited.java", "class Edited { int x; }\n");
        write(baseRoot, "Removed.java", "class Removed { }\n");
        write(headRoot, "pkg/Added.java", "class Added { }\n");

        assertThat(ChangedJavaFiles.between(baseRoot, headRoot).relativePaths())
                .containsExactlyInAnyOrder("Edited.java", "Removed.java", "pkg/Added.java");
    }

    @Test
    void aMoveBetweenTwoChangedFilesAmongManyUnchangedOnesIsStillDetected() throws IOException {
        write(baseRoot, "Greeter.java", "class Greeter {\n    String greet() { return \"hi\"; }\n}\n");
        write(headRoot, "Greeter.java", "class Greeter {\n}\n");
        write(baseRoot, "Farewell.java", "class Farewell {\n}\n");
        write(headRoot, "Farewell.java", "class Farewell {\n    String greet() { return \"hi\"; }\n}\n");
        for (int i = 0; i < 10; i++) {
            String unchanged = "class Other" + i + " {\n    String greet() { return \"hi\"; }\n}\n";
            write(baseRoot, "Other" + i + ".java", unchanged);
            write(headRoot, "Other" + i + ".java", unchanged);
        }

        List<DetectedTransformation> transformations = detect();

        assertThat(transformations).anyMatch(t -> t.kind() == TransformationKind.MOVE_SYMBOL
                && t.involvedDescriptions().equals(List.of("Greeter#greet", "Farewell#greet")));
        assertThat(transformations).allMatch(t -> t.filesTouched().stream()
                .allMatch(file -> file.equals("Greeter.java") || file.equals("Farewell.java")));
    }

    @Test
    void anUnchangedFileIsNeverCitedEvenWhenItsBodiesMatch() throws IOException {
        String legacy = "class Legacy {\n    int compute(int x) { return x * 42 + 7; }\n}\n";
        write(baseRoot, "Legacy.java", legacy);
        write(headRoot, "Legacy.java", legacy);
        write(headRoot, "Modern.java", "class Modern {\n    int compute(int x) { return x * 42 + 7; }\n}\n");

        assertThat(detect()).noneMatch(t -> t.filesTouched().contains("Legacy.java"));
    }

    @Test
    void identicalRevisionsProduceNothing() throws IOException {
        write(baseRoot, "Same.java", "class Same {\n    int a() { return 1; }\n}\n");
        write(headRoot, "Same.java", "class Same {\n    int a() { return 1; }\n}\n");

        assertThat(detect()).isEmpty();
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
