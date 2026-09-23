package com.athena.plugin.java;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The Java files that differ between a base and a head source tree — added, removed, or
 * with different content — as paths relative to either root. Detection only ever needs
 * these (ticket #271): an identical file can't be the source or target of a change, and
 * parsing every file of a large repository twice dominated the analysis time.
 */
final class ChangedJavaFiles {

    private final List<String> relativePaths;
    private final List<String> presentInBoth;

    private ChangedJavaFiles(List<String> relativePaths, List<String> presentInBoth) {
        this.relativePaths = List.copyOf(relativePaths);
        this.presentInBoth = List.copyOf(presentInBoth);
    }

    static ChangedJavaFiles between(Path baseRoot, Path headRoot) {
        Set<String> all = new TreeSet<>(javaFiles(baseRoot));
        all.addAll(javaFiles(headRoot));
        List<String> changed = new ArrayList<>();
        List<String> changedInBoth = new ArrayList<>();
        for (String relativePath : all) {
            Path baseFile = baseRoot.resolve(relativePath);
            Path headFile = headRoot.resolve(relativePath);
            boolean inBase = Files.isRegularFile(baseFile);
            boolean inHead = Files.isRegularFile(headFile);
            if (inBase && inHead) {
                if (!sameContent(baseFile, headFile)) {
                    changed.add(relativePath);
                    changedInBoth.add(relativePath);
                }
            } else {
                changed.add(relativePath);
            }
        }
        return new ChangedJavaFiles(changed, changedInBoth);
    }

    /** Every changed file: added, removed, or edited. */
    List<String> relativePaths() {
        return relativePaths;
    }

    /** The changed files that exist in both revisions (edited, not added or removed). */
    List<String> presentInBoth() {
        return presentInBoth;
    }

    private static Set<String> javaFiles(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(p -> p.toString().endsWith(".java")).filter(Files::isRegularFile)
                    .map(p -> root.relativize(p).toString())
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean sameContent(Path first, Path second) {
        try {
            return Files.size(first) == Files.size(second)
                    && Arrays.equals(Files.readAllBytes(first), Files.readAllBytes(second));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
