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
public final class JavaFixtureSupport {

    private JavaFixtureSupport() {
    }

    public static void write(Path root, String className, String content) {
        try {
            Files.writeString(root.resolve(className + ".java"), content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Writes a Greeter#greet -> Greeter#salute method rename fixture into both roots. */
    public static void writeRenameFixture(Path baseRoot, Path headRoot) {
        write(baseRoot, "Greeter", "public class Greeter {\n"
                + "    public String greet() {\n"
                + "        return \"hi\";\n"
                + "    }\n"
                + "}\n");
        write(headRoot, "Greeter", "public class Greeter {\n"
                + "    public String salute() {\n"
                + "        return \"hi\";\n"
                + "    }\n"
                + "}\n");
    }

    /**
     * Writes a mechanical-replacement fixture (identifier Foo -> Bar referenced consistently
     * across 3 files) into both roots, matching StructuralChangeDetectionSteps' own
     * mechanical-replacement scenario shape.
     */
    public static void writeMechanicalReplacementFixture(Path baseRoot, Path headRoot) {
        for (int i = 0; i < 3; i++) {
            String refClass = "Ref" + i;
            write(baseRoot, refClass, "public class " + refClass + " {\n"
                    + "    public Foo make() {\n"
                    + "        return new Foo();\n"
                    + "    }\n"
                    + "}\n");
            write(headRoot, refClass, "public class " + refClass + " {\n"
                    + "    public Bar make() {\n"
                    + "        return new Bar();\n"
                    + "    }\n"
                    + "}\n");
        }
        write(baseRoot, "Foo", "public class Foo {\n}\n");
        write(headRoot, "Bar", "public class Bar {\n}\n");
    }

    public static void deleteRecursively(Path root) throws IOException {
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
