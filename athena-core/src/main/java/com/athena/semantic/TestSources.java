package com.athena.semantic;

import java.util.List;
import java.util.Set;

/**
 * Recognizes test sources by path alone (ticket #285), so a Change in test code can be told
 * apart from a production change — a new test method is not a new business capability.
 * A file is a test source when it sits under a test source root ({@code src/test/},
 * {@code src/it/}, {@code src/testFixtures/}), under a top-level {@code test/}, {@code tests/}
 * or {@code testsuite/} directory, or when its class name follows a test naming convention.
 */
public final class TestSources {

    private static final List<String> TEST_SOURCE_ROOTS = List.of("src/test/", "src/it/", "src/testFixtures/");
    private static final Set<String> TOP_LEVEL_TEST_DIRECTORIES = Set.of("test", "tests", "testsuite");
    private static final List<String> TEST_CLASS_SUFFIXES = List.of("Test", "Tests", "IT", "TestCase");

    private TestSources() {
    }

    public static boolean isTestSource(String path) {
        String normalized = path.replace('\\', '/');
        return underTestSourceRoot(normalized) || underTopLevelTestDirectory(normalized) || hasTestClassName(normalized);
    }

    private static boolean underTestSourceRoot(String path) {
        return TEST_SOURCE_ROOTS.stream().anyMatch(root -> path.startsWith(root) || path.contains("/" + root));
    }

    private static boolean underTopLevelTestDirectory(String path) {
        int separator = path.indexOf('/');
        return separator > 0 && TOP_LEVEL_TEST_DIRECTORIES.contains(path.substring(0, separator));
    }

    private static boolean hasTestClassName(String path) {
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        int extension = fileName.lastIndexOf('.');
        String className = extension < 0 ? fileName : fileName.substring(0, extension);
        return TEST_CLASS_SUFFIXES.stream().anyMatch(className::endsWith);
    }
}
