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
 * Mechanical replacements are found from what changed — candidate identifiers from
 * changed lines, occurrences in changed files — so their cost follows the size of the
 * change rather than of the repository, and each one cites only the files it occurs in
 * (ticket #270).
 */
class MechanicalReplacementScopeTest {

    @TempDir
    Path baseRoot;

    @TempDir
    Path headRoot;

    @Test
    void aConsistentReplacementCitesOnlyTheFilesItOccursIn() throws IOException {
        reference(baseRoot, "Ref0", "LegacyClient");
        reference(headRoot, "Ref0", "HttpClient");
        reference(baseRoot, "Ref1", "LegacyClient");
        reference(headRoot, "Ref1", "HttpClient");
        String unrelated = "public class Unrelated {\n    int size() { return 1; }\n}\n";
        Files.writeString(baseRoot.resolve("Unrelated.java"), unrelated);
        Files.writeString(headRoot.resolve("Unrelated.java"), unrelated);

        DetectedTransformation replacement = replacementOf("LegacyClient -> HttpClient");

        assertThat(replacement.occurrenceCount()).isEqualTo(2);
        assertThat(replacement.filesTouched()).containsExactlyInAnyOrder("Ref0.java", "Ref1.java");
    }

    @Test
    void anUnchangedFileStillReferencingTheOldIdentifierDoesNotBlockOrCount() throws IOException {
        reference(baseRoot, "Ref0", "LegacyClient");
        reference(headRoot, "Ref0", "HttpClient");
        reference(baseRoot, "Ref1", "LegacyClient");
        reference(headRoot, "Ref1", "LegacyClient");

        assertThat(replacementOf("LegacyClient -> HttpClient").occurrenceCount()).isEqualTo(1);
    }

    @Test
    void anIdentifierOnlyInUnchangedFilesIsNeverACandidate() throws IOException {
        reference(baseRoot, "Ref0", "Unrelated");
        reference(headRoot, "Ref0", "Unrelated");
        Files.writeString(baseRoot.resolve("Changed.java"), "public class Changed {\n    int size() { return 1; }\n}\n");
        Files.writeString(headRoot.resolve("Changed.java"), "public class Changed {\n    int size() { return 2; }\n}\n");

        assertThat(detect()).noneMatch(t -> t.kind() == TransformationKind.MECHANICAL_REPLACEMENT
                && t.involvedDescriptions().get(0).startsWith("Unrelated"));
    }

    @Test
    void anInconsistentReplacementIsNotReported() throws IOException {
        reference(baseRoot, "Ref0", "LegacyClient");
        reference(headRoot, "Ref0", "HttpClient");
        reference(baseRoot, "Ref1", "LegacyClient");
        reference(headRoot, "Ref1", "WebClient");

        assertThat(detect()).noneMatch(t -> t.kind() == TransformationKind.MECHANICAL_REPLACEMENT
                && t.involvedDescriptions().get(0).startsWith("LegacyClient"));
    }

    private DetectedTransformation replacementOf(String description) {
        List<DetectedTransformation> matches = detect().stream()
                .filter(t -> t.kind() == TransformationKind.MECHANICAL_REPLACEMENT
                        && t.involvedDescriptions().equals(List.of(description)))
                .toList();
        assertThat(matches).hasSize(1);
        return matches.get(0);
    }

    private List<DetectedTransformation> detect() {
        return new TransformationDetector().detect(baseRoot, headRoot);
    }

    private static void reference(Path root, String className, String identifier) throws IOException {
        Files.writeString(root.resolve(className + ".java"), "public class " + className + " {\n    public " + identifier
                + " make() {\n        return new " + identifier + "();\n    }\n}\n");
    }
}
