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
