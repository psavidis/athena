package com.athena.plugin.java;

import com.athena.semantic.DetectedTransformation;
import com.athena.semantic.TransformationKind;
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

class AddedConstructorParameterDetectionTest {

    private Path baseRoot;
    private Path headRoot;

    @BeforeEach
    void createTempRoots() throws IOException {
        baseRoot = Files.createTempDirectory("athena-ctor-base");
        headRoot = Files.createTempDirectory("athena-ctor-head");
    }

    @AfterEach
    void cleanUpTempRoots() throws IOException {
        deleteRecursively(baseRoot);
        deleteRecursively(headRoot);
    }

    @Test
    void detectsAConstructorParameterAssignedToASameNamedField() {
        write(baseRoot, "UserService", "public class UserService {\n"
                + "    public UserService() {\n"
                + "    }\n"
                + "}\n");
        write(headRoot, "UserService", "public class UserService {\n"
                + "    private final UserRepository userRepository;\n"
                + "    public UserService(UserRepository userRepository) {\n"
                + "        this.userRepository = userRepository;\n"
                + "    }\n"
                + "}\n");

        List<DetectedTransformation> transformations = new TransformationDetector().detect(baseRoot, headRoot);

        assertThat(transformations).anySatisfy(t -> {
            assertThat(t.kind()).isEqualTo(TransformationKind.ADD_CONSTRUCTOR_PARAMETER);
            assertThat(t.involvedDescriptions()).containsExactly("UserService#userRepository");
        });
    }

    @Test
    void doesNotDetectAConstructorParameterThatIsNotAssignedToAField() {
        write(baseRoot, "Calculator", "public class Calculator {\n"
                + "    public Calculator() {\n"
                + "    }\n"
                + "}\n");
        write(headRoot, "Calculator", "public class Calculator {\n"
                + "    public Calculator(int seed) {\n"
                + "        System.out.println(seed);\n"
                + "    }\n"
                + "}\n");

        List<DetectedTransformation> transformations = new TransformationDetector().detect(baseRoot, headRoot);

        assertThat(transformations).noneMatch(t -> t.kind() == TransformationKind.ADD_CONSTRUCTOR_PARAMETER);
    }

    @Test
    void doesNotDetectAParameterThatWasAlreadyPresentInTheBaseConstructor() {
        write(baseRoot, "UserService", "public class UserService {\n"
                + "    private final UserRepository userRepository;\n"
                + "    public UserService(UserRepository userRepository) {\n"
                + "        this.userRepository = userRepository;\n"
                + "    }\n"
                + "}\n");
        write(headRoot, "UserService", "public class UserService {\n"
                + "    private final UserRepository userRepository;\n"
                + "    public UserService(UserRepository userRepository) {\n"
                + "        this.userRepository = userRepository;\n"
                + "        System.out.println(\"constructed\");\n"
                + "    }\n"
                + "}\n");

        List<DetectedTransformation> transformations = new TransformationDetector().detect(baseRoot, headRoot);

        assertThat(transformations).noneMatch(t -> t.kind() == TransformationKind.ADD_CONSTRUCTOR_PARAMETER);
    }

    @Test
    void doesNotDetectAParameterThatWasAlreadyPresentInADifferentBaseOverload() {
        // Base declares two constructors; the parameter in question ("repository") already
        // exists on the OTHER overload, not the one head's new overload most resembles.
        write(baseRoot, "UserService", "public class UserService {\n"
                + "    private final UserRepository repository;\n"
                + "    public UserService() {\n"
                + "        this.repository = null;\n"
                + "    }\n"
                + "    public UserService(UserRepository repository) {\n"
                + "        this.repository = repository;\n"
                + "    }\n"
                + "}\n");
        write(headRoot, "UserService", "public class UserService {\n"
                + "    private final UserRepository repository;\n"
                + "    private final Logger log;\n"
                + "    public UserService() {\n"
                + "        this.repository = null;\n"
                + "        this.log = null;\n"
                + "    }\n"
                + "    public UserService(UserRepository repository) {\n"
                + "        this.repository = repository;\n"
                + "        this.log = null;\n"
                + "    }\n"
                + "    public UserService(UserRepository repository, Logger log) {\n"
                + "        this.repository = repository;\n"
                + "        this.log = log;\n"
                + "    }\n"
                + "}\n");

        List<DetectedTransformation> transformations = new TransformationDetector().detect(baseRoot, headRoot);

        assertThat(transformations)
                .filteredOn(t -> t.kind() == TransformationKind.ADD_CONSTRUCTOR_PARAMETER)
                .extracting(DetectedTransformation::involvedDescriptions)
                .containsExactly(List.of("UserService#log"));
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
