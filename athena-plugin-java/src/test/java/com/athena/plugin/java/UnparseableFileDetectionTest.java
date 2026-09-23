package com.athena.plugin.java;

import com.athena.semantic.DetectedTransformation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A changed file that fails to parse in one revision is left out of detection on both
 * sides: parsing only its other side would report every declaration in it as removed or
 * added, and hide that it could not be analyzed at all (ticket #260).
 */
class UnparseableFileDetectionTest {

    @TempDir
    Path baseRoot;

    @TempDir
    Path headRoot;

    @Test
    void aFileThatFailsToParseInHeadProducesNoTransformations() throws IOException {
        write(baseRoot, "Broken", "public class Broken {\n    public String name() { return \"b\"; }\n}\n");
        write(headRoot, "Broken", "public class Broken {\n    public void m( { not java\n");

        assertThat(detect()).noneMatch(t -> t.filesTouched().contains("Broken.java"));
    }

    @Test
    void aFileThatFailsToParseInBaseProducesNoTransformations() throws IOException {
        write(baseRoot, "Broken", "public class Broken {\n    public void m( { not java\n");
        write(headRoot, "Broken", "public class Broken {\n    public String name() { return \"b\"; }\n}\n");

        assertThat(detect()).noneMatch(t -> t.filesTouched().contains("Broken.java"));
    }

    @Test
    void otherChangedFilesAreStillDetected() throws IOException {
        write(baseRoot, "Broken", "public class Broken { }\n");
        write(headRoot, "Broken", "public class Broken {\n    public void m( { not java\n");
        write(baseRoot, "Greeter", "public class Greeter { }\n");
        write(headRoot, "Greeter", "public class Greeter {\n    String hi() { return \"hi\"; }\n}\n");

        assertThat(detect()).anyMatch(t -> t.involvedDescriptions().contains("Greeter#hi"));
    }

    private List<DetectedTransformation> detect() {
        return new TransformationDetector().detect(baseRoot, headRoot);
    }

    private static void write(Path root, String className, String source) throws IOException {
        Files.writeString(root.resolve(className + ".java"), source);
    }
}
