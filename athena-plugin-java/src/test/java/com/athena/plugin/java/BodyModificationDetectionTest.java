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
 * Every method or constructor whose body changed is reported, as an UNKNOWN-category
 * body modification, unless a more specific detector already explains the edit
 * (ticket #264).
 */
class BodyModificationDetectionTest {

    @TempDir
    Path baseRoot;

    @TempDir
    Path headRoot;

    @Test
    void aChangedBodyIsAnUnknownBodyModificationWithTheMethodAsEvidence() throws IOException {
        write(baseRoot, "Printer", "public class Printer {\n    String format(String name) {\n        return name;\n    }\n}\n");
        write(headRoot, "Printer", "public class Printer {\n    String format(String name) {\n        return name.trim();\n    }\n}\n");

        assertThat(detect()).singleElement().satisfies(change -> {
            assertThat(change.kind()).isEqualTo(TransformationKind.MODIFY_METHOD_BODY);
            assertThat(ChangeCategory.of(change.kind())).isEqualTo(ChangeCategory.UNKNOWN);
            assertThat(change.involvedDescriptions()).containsExactly("Printer#format", "+trim");
            assertThat(change.diffText()).contains("-        return name;").contains("+        return name.trim();");
        });
    }

    @Test
    void aChangedConstructorBodyIsABodyModificationOfTheConstructor() throws IOException {
        write(baseRoot, "Account", "public class Account {\n    long balance;\n    Account(long b) { this.balance = b; }\n}\n");
        write(headRoot, "Account", "public class Account {\n    long balance;\n    Account(long b) { this.balance = Math.max(0, b); }\n}\n");

        assertThat(detect()).extracting(t -> t.kind() + " " + t.involvedDescriptions())
                .contains("MODIFY_METHOD_BODY [Account#<init>, +max]");
    }

    @Test
    void aBehavioralEditIsNotAlsoABodyModification() throws IOException {
        write(baseRoot, "Guard", "public class Guard {\n    boolean ok(User u) {\n        if (u.active()) { return true; }\n        return false;\n    }\n}\n");
        write(headRoot, "Guard", "public class Guard {\n    boolean ok(User u) {\n        if (u.active() && u.enabled()) { return true; }\n        return false;\n    }\n}\n");

        assertThat(detect()).extracting(DetectedTransformation::kind)
                .containsExactly(TransformationKind.CHANGE_CONTROL_FLOW);
    }

    @Test
    void anEditFullyExplainedByAMechanicalReplacementIsNotAlsoABodyModification() throws IOException {
        write(baseRoot, "Ref0", "public class Ref0 {\n    Object make() {\n        return new LegacyClient();\n    }\n}\n");
        write(headRoot, "Ref0", "public class Ref0 {\n    Object make() {\n        return new HttpClient();\n    }\n}\n");

        List<DetectedTransformation> transformations = detect();

        assertThat(transformations).anyMatch(t -> t.kind() == TransformationKind.MECHANICAL_REPLACEMENT);
        assertThat(transformations).noneMatch(t -> t.kind() == TransformationKind.MODIFY_METHOD_BODY);
    }

    @Test
    void anAnnotationOnlyEditIsNotABodyModification() throws IOException {
        write(baseRoot, "Repo", "public class Repo {\n    Object find(Object id) { return id; }\n}\n");
        write(headRoot, "Repo", "public class Repo {\n    Object find(@NonNull Object id) { return id; }\n}\n");

        assertThat(detect()).extracting(DetectedTransformation::kind)
                .containsExactly(TransformationKind.CHANGE_PARAMETER_ANNOTATIONS);
    }

    @Test
    void sameNamedConstructorsInDifferentFilesAreNeverCompared() throws IOException {
        String first = "package a;\npublic class Person {\n    Person(String n) { this.n = n; }\n    String n;\n}\n";
        String second = "package b;\npublic class Person {\n    Person(String n) { this.n = n.trim(); }\n    String n;\n}\n";
        for (Path root : List.of(baseRoot, headRoot)) {
            Files.createDirectories(root.resolve("a"));
            Files.createDirectories(root.resolve("b"));
            Files.writeString(root.resolve("a/Person.java"), first);
            Files.writeString(root.resolve("b/Person.java"), second);
        }

        assertThat(detect()).isEmpty();
    }

    @Test
    void anEditInsideAStaticInitializerIsReportedOnTheInitializer() throws IOException {
        write(baseRoot, "Reader", "public class Reader {\n    static {\n        Access.INSTANCE = new Access() {\n"
                + "            public void promote(Reader r) { r.peek(); }\n        };\n    }\n}\n");
        write(headRoot, "Reader", "public class Reader {\n    static {\n        Access.INSTANCE = new Access() {\n"
                + "            public void promote(Reader r) { r.peek(); r.flush(); }\n        };\n    }\n}\n");

        assertThat(detect()).singleElement().satisfies(change -> {
            assertThat(change.kind()).isEqualTo(TransformationKind.MODIFY_METHOD_BODY);
            assertThat(change.involvedDescriptions()).containsExactly("Reader#<clinit>", "+flush");
            assertThat(change.diffText()).contains("r.flush()");
        });
    }

    @Test
    void anUnchangedMethodIsNotReported() throws IOException {
        String source = "public class Printer {\n    String format(String name) { return name; }\n}\n";
        write(baseRoot, "Printer", source);
        write(headRoot, "Printer", source);

        assertThat(detect()).isEmpty();
    }

    private List<DetectedTransformation> detect() {
        return new TransformationDetector().detect(baseRoot, headRoot);
    }

    private static void write(Path root, String className, String source) throws IOException {
        Files.writeString(root.resolve(className + ".java"), source);
    }
}
