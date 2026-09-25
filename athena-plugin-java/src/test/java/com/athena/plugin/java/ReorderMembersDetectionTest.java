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

/** Members of a class moved to other positions, each unchanged, are one reorder (ticket #387). */
class ReorderMembersDetectionTest {

    private static final String ADD = "  int add(int a, int b) {\n    return a + b;\n  }\n";
    private static final String SUB = "  int sub(int a, int b) {\n    return a - b;\n  }\n";
    private static final String MUL = "  int mul(int a, int b) {\n    return a * b;\n  }\n";
    private static final String FIELD = "  private int scale;\n";

    @TempDir
    Path baseRoot;

    @TempDir
    Path headRoot;

    @Test
    void oneMovedMethodIsNamedWithTheMemberItNowPrecedes() throws IOException {
        write(baseRoot, ADD + SUB + MUL);
        write(headRoot, MUL + ADD + SUB);

        assertThat(detect()).singleElement().satisfies(reorder -> {
            assertThat(reorder.kind()).isEqualTo(TransformationKind.REORDER_MEMBERS);
            assertThat(reorder.involvedDescriptions()).containsExactly("Calc", "mul moved before add");
            assertThat(reorder.filesTouched()).contains("Calc.java");
        });
    }

    @Test
    void aMemberMovedToTheEndIsNamedWithTheMemberItNowFollows() throws IOException {
        write(baseRoot, ADD + SUB + MUL);
        write(headRoot, SUB + MUL + ADD);

        assertThat(detect()).singleElement()
                .satisfies(reorder -> assertThat(reorder.involvedDescriptions()).containsExactly("Calc", "add moved after mul"));
    }

    @Test
    void severalMovedMembersAreCounted() throws IOException {
        write(baseRoot, FIELD + ADD + SUB + MUL);
        write(headRoot, MUL + ADD + SUB + FIELD);

        assertThat(detect()).singleElement()
                .satisfies(reorder -> assertThat(reorder.involvedDescriptions()).containsExactly("Calc", "2 members moved"));
    }

    @Test
    void aMovedMemberThatAlsoChangedIsNotAReorder() throws IOException {
        write(baseRoot, ADD + SUB + MUL);
        write(headRoot, MUL.replace("a * b", "b * a") + ADD + SUB);

        assertThat(detect()).noneMatch(t -> t.kind() == TransformationKind.REORDER_MEMBERS);
    }

    @Test
    void anUnchangedOrderIsNotAReorder() throws IOException {
        write(baseRoot, ADD + SUB + MUL);
        write(headRoot, ADD + SUB + MUL.replace("a * b", "b * a"));

        assertThat(detect()).noneMatch(t -> t.kind() == TransformationKind.REORDER_MEMBERS);
    }

    @Test
    void aNestedClassesMembersAreNamedWithTheNestedClass() throws IOException {
        write(baseRoot, "  static class Ops {\n" + ADD + SUB + "  }\n");
        write(headRoot, "  static class Ops {\n" + SUB + ADD + "  }\n");

        assertThat(detect()).singleElement()
                .satisfies(reorder -> assertThat(reorder.involvedDescriptions().get(0)).isEqualTo("Calc.Ops"));
    }

    private List<DetectedTransformation> detect() {
        return new TransformationDetector().detect(baseRoot, headRoot);
    }

    private static void write(Path root, String members) throws IOException {
        Files.writeString(root.resolve("Calc.java"), "public class Calc {\n" + members + "}\n");
    }
}
