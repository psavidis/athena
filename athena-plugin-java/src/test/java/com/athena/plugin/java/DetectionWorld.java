package com.athena.plugin.java;

import com.athena.semantic.DetectedTransformation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Per-scenario state shared between the detection step classes (injected by
 * cucumber-picocontainer, one instance per scenario): the base and head source roots
 * every "Given" writes into, and the transformations the full detector found. Sharing
 * them lets a scenario mix steps from different classes — e.g. set up with a
 * behavioral-detection "Given" but run the full detector (ticket #264).
 */
public class DetectionWorld {

    private Path baseRoot;
    private Path headRoot;
    private List<DetectedTransformation> transformations;

    Path baseRoot() {
        if (baseRoot == null) {
            baseRoot = createTempDirectory("athena-base");
        }
        return baseRoot;
    }

    Path headRoot() {
        if (headRoot == null) {
            headRoot = createTempDirectory("athena-head");
        }
        return headRoot;
    }

    void recordTransformations(List<DetectedTransformation> detected) {
        this.transformations = List.copyOf(detected);
    }

    Optional<List<DetectedTransformation>> transformations() {
        return Optional.ofNullable(transformations);
    }

    /** Deletes both roots; called once per scenario from an {@code @After} hook. */
    void cleanUp() {
        deleteRecursively(baseRoot);
        deleteRecursively(headRoot);
    }

    private static Path createTempDirectory(String prefix) {
        try {
            return Files.createTempDirectory(prefix);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
