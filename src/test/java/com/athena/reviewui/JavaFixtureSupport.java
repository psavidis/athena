package com.athena.reviewui;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Shared temp-directory/fixture-writing helpers for this package's step
 * definitions, so base/head Java source fixtures are created and torn down
 * the same way everywhere rather than each step-definition class
 * reimplementing its own copy (which previously let one copy drift from
 * another — see the mechanical-replacement fixture history on ticket #38).
 */
final class JavaFixtureSupport {

    private JavaFixtureSupport() {
    }

    static void write(Path root, String className, String content) {
        try {
            Files.writeString(root.resolve(className + ".java"), content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var walk = Files.walk(root)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }
}
