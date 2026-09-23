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
 * A field that keeps its name but changes its declared type is one field-type change,
 * not a removed field plus an added field, and says when the field is visible outside
 * its class (ticket #267).
 */
class FieldTypeChangeDetectionTest {

    @TempDir
    Path baseRoot;

    @TempDir
    Path headRoot;

    @Test
    void aPrivateFieldTypeChangeIsOneChangeNamingBothTypes() throws IOException {
        write(baseRoot, "AnnotatedMethod", "public class AnnotatedMethod {\n    private MethodHolder _invoker;\n}\n");
        write(headRoot, "AnnotatedMethod", "public class AnnotatedMethod {\n    private MethodHandle _invoker;\n}\n");

        DetectedTransformation change = fieldTypeChange();

        assertThat(change.involvedDescriptions()).containsExactly("AnnotatedMethod#_invoker", "MethodHolder -> MethodHandle");
        assertThat(change.diffText()).contains("-private MethodHolder _invoker;").contains("+private MethodHandle _invoker;");
    }

    @Test
    void aProtectedFieldTypeChangeSaysSo() throws IOException {
        write(baseRoot, "Writer", "public class Writer {\n    protected Holder _accessor;\n}\n");
        write(headRoot, "Writer", "public class Writer {\n    protected MethodHandle _accessor;\n}\n");

        assertThat(fieldTypeChange()).satisfies(change -> assertThat(change.involvedDescriptions())
                .containsExactly("Writer#_accessor", "Holder -> MethodHandle (protected)"));
    }

    @Test
    void aPublicFieldTypeChangeSaysSo() throws IOException {
        write(baseRoot, "Config", "public class Config {\n    public int timeout;\n}\n");
        write(headRoot, "Config", "public class Config {\n    public long timeout;\n}\n");

        assertThat(fieldTypeChange()).satisfies(change -> assertThat(change.involvedDescriptions())
                .containsExactly("Config#timeout", "int -> long (public)"));
    }

    @Test
    void aPackagePrivateFieldTypeChangeSaysSo() throws IOException {
        write(baseRoot, "Config", "public class Config {\n    int timeout;\n}\n");
        write(headRoot, "Config", "public class Config {\n    long timeout;\n}\n");

        assertThat(fieldTypeChange()).satisfies(change -> assertThat(change.involvedDescriptions())
                .containsExactly("Config#timeout", "int -> long (package-private)"));
    }

    @Test
    void aFieldWhoseNameAndTypeBothChangedIsNotATypeChange() throws IOException {
        write(baseRoot, "Cache", "public class Cache {\n    private int size;\n}\n");
        write(headRoot, "Cache", "public class Cache {\n    private long count;\n}\n");

        // (Mechanical replacement may independently report int -> long; not this test's concern.)
        assertThat(detect()).extracting(DetectedTransformation::kind)
                .contains(TransformationKind.REMOVE_FIELD, TransformationKind.ADD_FIELD)
                .doesNotContain(TransformationKind.CHANGE_FIELD_TYPE);
    }

    @Test
    void aFieldWithTheSameTypeIsNotATypeChange() throws IOException {
        write(baseRoot, "Account", "public class Account {\n    @Autowired private String owner;\n}\n");
        write(headRoot, "Account", "public class Account {\n    private String owner;\n}\n");

        assertThat(detect()).extracting(DetectedTransformation::kind)
                .containsExactly(TransformationKind.CHANGE_FIELD_ANNOTATIONS);
    }

    /** The one field-type change; mechanical replacement may independently report the same type swap. */
    private DetectedTransformation fieldTypeChange() {
        List<DetectedTransformation> changes = detect().stream()
                .filter(t -> t.kind() == TransformationKind.CHANGE_FIELD_TYPE).toList();
        assertThat(changes).hasSize(1);
        assertThat(detect()).noneMatch(t -> t.kind() == TransformationKind.REMOVE_FIELD || t.kind() == TransformationKind.ADD_FIELD);
        return changes.get(0);
    }

    private List<DetectedTransformation> detect() {
        return new TransformationDetector().detect(baseRoot, headRoot);
    }

    private static void write(Path root, String className, String source) throws IOException {
        Files.writeString(root.resolve(className + ".java"), source);
    }
}
