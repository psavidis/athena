package com.athena.semantic;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FieldChangeDetectionTest {

    private Path baseRoot;
    private Path headRoot;

    @BeforeEach
    void createTempRoots() throws IOException {
        baseRoot = Files.createTempDirectory("athena-field-base");
        headRoot = Files.createTempDirectory("athena-field-head");
    }

    @AfterEach
    void cleanUpTempRoots() throws IOException {
        deleteRecursively(baseRoot);
        deleteRecursively(headRoot);
    }

    @Test
    void aSameNameSameTypeFieldWithNoAnnotationChangeIsReportedAsNothing() {
        write(baseRoot, "Config", "public class Config {\n"
                + "    private String name;\n"
                + "}\n");
        write(headRoot, "Config", "public class Config {\n"
                + "    private String name;\n"
                + "}\n");

        List<DetectedTransformation> transformations = new TransformationDetector().detect(baseRoot, headRoot);

        assertThat(transformations).isEmpty();
    }

    @Test
    void aSameNameFieldWhoseTypeChangedIsReportedAsRemoveAndAdd() {
        write(baseRoot, "Config", "public class Config {\n"
                + "    private OldThing setting;\n"
                + "}\n");
        write(headRoot, "Config", "public class Config {\n"
                + "    private BrandNewThing setting;\n"
                + "}\n");

        List<DetectedTransformation> transformations = new TransformationDetector().detect(baseRoot, headRoot);

        // Also picked up by mechanical-replacement detection (OldThing -> BrandNewThing is a
        // consistent whole-identifier substitution across the file) — an independent, correct
        // detector step, not something this assertion needs to rule out.
        assertThat(transformations).extracting(DetectedTransformation::kind)
                .contains(TransformationKind.REMOVE_FIELD, TransformationKind.ADD_FIELD);
    }

    @Test
    void aSameNameSameTypeFieldThatGainsAnAnnotationIsReportedAsChangeFieldAnnotations() {
        write(baseRoot, "Config", "public class Config {\n"
                + "    private String name;\n"
                + "}\n");
        write(headRoot, "Config", "public class Config {\n"
                + "    @Deprecated\n"
                + "    private String name;\n"
                + "}\n");

        List<DetectedTransformation> transformations = new TransformationDetector().detect(baseRoot, headRoot);

        assertThat(transformations).hasSize(1);
        assertThat(transformations.get(0).kind()).isEqualTo(TransformationKind.CHANGE_FIELD_ANNOTATIONS);
        assertThat(transformations.get(0).involvedDescriptions()).containsExactly("Config#name");
    }

    @Test
    void aSameNameSameTypeFieldThatLosesAnAutowiredAnnotationIsReportedAsChangeFieldAnnotations() {
        write(baseRoot, "UserService", "public class UserService {\n"
                + "    @Autowired\n"
                + "    private UserRepository userRepository;\n"
                + "}\n");
        write(headRoot, "UserService", "public class UserService {\n"
                + "    private UserRepository userRepository;\n"
                + "}\n");

        List<DetectedTransformation> transformations = new TransformationDetector().detect(baseRoot, headRoot);

        assertThat(transformations).hasSize(1);
        assertThat(transformations.get(0).kind()).isEqualTo(TransformationKind.CHANGE_FIELD_ANNOTATIONS);
        assertThat(transformations.get(0).diffText()).contains("-@Autowired");
    }

    private void write(Path root, String simpleName, String content) {
        try {
            Files.writeString(root.resolve(simpleName + ".java"), content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException ignored) {
                    // best-effort cleanup of a temp directory
                }
            });
        }
    }
}
