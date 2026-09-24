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

/** A class renamed, possibly with a move, whose members only refer to its own names (ticket #336). */
class ClassRenameAndMoveTest {

    @TempDir
    Path baseRoot;

    @TempDir
    Path headRoot;

    @Test
    void aClassWhoseSelfReferencesFollowTheNewNameIsOneRename() throws IOException {
        write(baseRoot, "a/Old.java", "package a;\npublic class Old {\n  public Old copy() {\n    return this;\n  }\n}\n");
        write(headRoot, "b/Fresh.java", "package b;\npublic class Fresh {\n  public Fresh copy() {\n    return this;\n  }\n}\n");

        assertThat(detect()).extracting(t -> t.kind() + " " + t.involvedDescriptions())
                .containsExactly("RENAME_CLASS [Old, Fresh]");
    }

    @Test
    void twoEquallyShapedCandidatesAreNotGuessedBetween() throws IOException {
        write(baseRoot, "a/Old.java", "package a;\npublic class Old {\n  public Old copy() {\n    return this;\n  }\n}\n");
        write(headRoot, "b/First.java", "package b;\npublic class First {\n  public First copy() {\n    return this;\n  }\n}\n");
        write(headRoot, "b/Second.java", "package b;\npublic class Second {\n  public Second copy() {\n    return this;\n  }\n}\n");

        assertThat(detect()).extracting(DetectedTransformation::kind).doesNotContain(TransformationKind.RENAME_CLASS);
    }

    private List<DetectedTransformation> detect() {
        return new TransformationDetector().detect(baseRoot, headRoot).stream()
                .filter(t -> t.kind().name().endsWith("_CLASS"))
                .toList();
    }

    private static void write(Path root, String path, String contents) throws IOException {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, contents);
    }
}
