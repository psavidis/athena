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
 * Annotations added to or removed from a method's parameters, its return type, or the
 * method itself change its contract and are reported, not silently dropped (ticket #268).
 */
class ContractAnnotationChangeDetectionTest {

    @TempDir
    Path baseRoot;

    @TempDir
    Path headRoot;

    @Test
    void anAnnotationAddedToAParameterNamesTheParameterAndAnnotation() throws IOException {
        write(baseRoot, "Mockito", "public class Mockito {\n    public static Object when(Object methodCall) { return null; }\n}\n");
        write(headRoot, "Mockito", "public class Mockito {\n    public static Object when(@Nullable Object methodCall) { return null; }\n}\n");

        assertThat(detect()).singleElement().satisfies(change -> {
            assertThat(change.kind()).isEqualTo(TransformationKind.CHANGE_PARAMETER_ANNOTATIONS);
            assertThat(change.involvedDescriptions()).containsExactly("Mockito#when", "methodCall +@Nullable");
        });
    }

    @Test
    void anAnnotationRemovedFromAParameterIsNamedAsRemoved() throws IOException {
        write(baseRoot, "Repo", "public class Repo {\n    void save(@NonNull Object entity) { }\n}\n");
        write(headRoot, "Repo", "public class Repo {\n    void save(Object entity) { }\n}\n");

        assertThat(detect()).singleElement().satisfies(change ->
                assertThat(change.involvedDescriptions()).containsExactly("Repo#save", "entity -@NonNull"));
    }

    @Test
    void severalChangedParametersAreOneChange() throws IOException {
        write(baseRoot, "Stubbing", "public interface Stubbing<T> {\n    Stubbing<T> thenReturn(T value, T... values);\n}\n");
        write(headRoot, "Stubbing",
                "public interface Stubbing<T> {\n    Stubbing<T> thenReturn(@Nullable T value, @Nullable T... values);\n}\n");

        assertThat(detect()).singleElement().satisfies(change -> assertThat(change.involvedDescriptions())
                .containsExactly("Stubbing#thenReturn", "value +@Nullable, values +@Nullable"));
    }

    @Test
    void aConstructorParameterAnnotationChangeIsReportedOnTheConstructor() throws IOException {
        write(baseRoot, "Account", "public class Account {\n    public Account(Object owner) { }\n}\n");
        write(headRoot, "Account", "public class Account {\n    public Account(@Nullable Object owner) { }\n}\n");

        assertThat(detect()).singleElement().satisfies(change -> {
            assertThat(change.kind()).isEqualTo(TransformationKind.CHANGE_PARAMETER_ANNOTATIONS);
            assertThat(change.involvedDescriptions()).containsExactly("Account#<init>", "owner +@Nullable");
        });
    }

    @Test
    void anAnnotationAddedToTheMethodIsAMethodAnnotationChange() throws IOException {
        write(baseRoot, "Repo", "public class Repo {\n    public Object find() { return null; }\n}\n");
        write(headRoot, "Repo", "public class Repo {\n    @Nullable public Object find() { return null; }\n}\n");

        assertThat(detect()).singleElement().satisfies(change -> {
            assertThat(change.kind()).isEqualTo(TransformationKind.CHANGE_METHOD_ANNOTATIONS);
            assertThat(change.involvedDescriptions()).containsExactly("Repo#find", "+@Nullable");
        });
    }

    @Test
    void anAnnotationOnTheReturnTypeIsAMethodAnnotationChange() throws IOException {
        write(baseRoot, "Repo", "public class Repo {\n    public Object find() { return null; }\n}\n");
        write(headRoot, "Repo", "public class Repo {\n    public @Nullable Object find() { return null; }\n}\n");

        assertThat(detect()).extracting(DetectedTransformation::kind)
                .containsExactly(TransformationKind.CHANGE_METHOD_ANNOTATIONS);
    }

    @Test
    void anUnchangedAnnotatedMethodIsNotReported() throws IOException {
        String source = "public class Repo {\n    @Nullable public Object find(@NonNull Object id) { return null; }\n}\n";
        write(baseRoot, "Repo", source);
        write(headRoot, "Repo", source);

        assertThat(detect()).isEmpty();
    }

    @Test
    void aParameterAnnotationChangeIsNotASignatureChange() throws IOException {
        write(baseRoot, "Mockito", "public class Mockito {\n    public static Object when(Object methodCall) { return null; }\n}\n");
        write(headRoot, "Mockito", "public class Mockito {\n    public static Object when(@Nullable Object methodCall) { return null; }\n}\n");

        assertThat(detect()).noneMatch(t -> t.kind() == TransformationKind.CHANGE_METHOD_SIGNATURE);
    }

    @Test
    void anOverrideAddedOrRemovedAloneIsNotReported() throws IOException {
        write(baseRoot, "Repo", "public class Repo extends Base {\n    Object find() { return null; }\n}\n");
        write(headRoot, "Repo", "public class Repo extends Base {\n    @Override\n    Object find() { return null; }\n}\n");

        assertThat(detect()).extracting(DetectedTransformation::kind).doesNotContain(TransformationKind.CHANGE_METHOD_ANNOTATIONS);
    }

    @Test
    void anOverrideAddedWithAContractAnnotationIsLeftOutOfTheDescription() throws IOException {
        write(baseRoot, "Repo", "public class Repo extends Base {\n    Object find() { return null; }\n}\n");
        write(headRoot, "Repo", "public class Repo extends Base {\n    @Override @Deprecated\n    Object find() { return null; }\n}\n");

        assertThat(detect()).filteredOn(t -> t.kind() == TransformationKind.CHANGE_METHOD_ANNOTATIONS).singleElement()
                .satisfies(t -> assertThat(t.involvedDescriptions()).containsExactly("Repo#find", "+@Deprecated"));
    }

    private List<DetectedTransformation> detect() {
        return new TransformationDetector().detect(baseRoot, headRoot);
    }

    private static void write(Path root, String typeName, String source) throws IOException {
        Files.writeString(root.resolve(typeName + ".java"), source);
    }
}
