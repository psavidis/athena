package com.athena.plugin.maven;

import com.athena.semantic.DetectedTransformation;
import com.athena.semantic.TransformationKind;
import com.athena.semantic.spi.LanguagePlugin;
import com.athena.semantic.spi.ParseOutcome;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * The Maven {@link LanguagePlugin} (ticket #340): for every {@code pom.xml} that differs between
 * the revisions, reports dependencies added ({@link TransformationKind#ADD_DEPENDENCY}), removed
 * ({@link TransformationKind#REMOVE_DEPENDENCY}) and changed in scope, version, optionality or exclusions
 * ({@link TransformationKind#CHANGE_DEPENDENCY}). A dependency is described as
 * {@code "module#groupId:artifactId"}, where the module is the pom's own artifactId.
 * Discovered by the application via {@link java.util.ServiceLoader}.
 */
public final class MavenLanguagePlugin implements LanguagePlugin {

    private static final Set<String> NEVER_SEARCHED = Set.of("node_modules", "target", "build", "out");

    @Override
    public String languageId() {
        return "maven";
    }

    @Override
    public boolean supports(Path sourceFile) {
        Path name = sourceFile.getFileName();
        return name != null && name.toString().equals("pom.xml");
    }

    @Override
    public ParseOutcome checkParses(String sourceText) {
        return PomDependencies.parse(sourceText).isPresent()
                ? ParseOutcome.success() : ParseOutcome.failure("not a well-formed Maven pom.xml");
    }

    @Override
    public List<DetectedTransformation> detect(Path baseRoot, Path headRoot) {
        Set<String> poms = new TreeSet<>(pomsUnder(baseRoot));
        poms.addAll(pomsUnder(headRoot));
        List<DetectedTransformation> results = new ArrayList<>();
        for (String pom : poms) {
            Optional<String> baseText = read(baseRoot.resolve(pom));
            Optional<String> headText = read(headRoot.resolve(pom));
            if (baseText.equals(headText)) continue;
            Optional<PomDependencies> base = baseText.flatMap(PomDependencies::parse);
            Optional<PomDependencies> head = headText.flatMap(PomDependencies::parse);
            if ((baseText.isPresent() && base.isEmpty()) || (headText.isPresent() && head.isEmpty())) continue;
            results.addAll(dependencyChanges(pom, base, head));
        }
        return results;
    }

    private static List<DetectedTransformation> dependencyChanges(String pom, Optional<PomDependencies> base,
                                                                  Optional<PomDependencies> head) {
        String module = head.or(() -> base).map(PomDependencies::artifactId).filter(id -> !id.isEmpty()).orElse(pom);
        Map<String, PomDependencies.Dependency> before = base.map(PomDependencies::dependencies).orElse(Map.of());
        Map<String, PomDependencies.Dependency> after = head.map(PomDependencies::dependencies).orElse(Map.of());
        List<DetectedTransformation> changes = new ArrayList<>();
        for (Map.Entry<String, PomDependencies.Dependency> dependency : before.entrySet()) {
            String description = module + "#" + dependency.getKey();
            PomDependencies.Dependency now = after.get(dependency.getKey());
            if (now == null) {
                changes.add(DetectedTransformation.of(TransformationKind.REMOVE_DEPENDENCY, List.of(description), List.of(pom)));
            } else if (!now.equals(dependency.getValue())) {
                changes.add(DetectedTransformation.of(TransformationKind.CHANGE_DEPENDENCY,
                        List.of(description, delta(dependency.getValue(), now)), List.of(pom)));
            }
        }
        for (String coordinate : after.keySet()) {
            if (!before.containsKey(coordinate)) {
                changes.add(DetectedTransformation.of(TransformationKind.ADD_DEPENDENCY,
                        List.of(module + "#" + coordinate), List.of(pom)));
            }
        }
        return changes;
    }

    /** "scope test -> compile, version 1.0 -> 1.1", listing only what changed. */
    static String delta(PomDependencies.Dependency before, PomDependencies.Dependency after) {
        List<String> items = new ArrayList<>();
        if (!before.scope().equals(after.scope())) {
            items.add("scope " + orNone(before.scope()) + " -> " + orNone(after.scope()));
        }
        if (!before.version().equals(after.version())) {
            items.add("version " + orNone(before.version()) + " -> " + orNone(after.version()));
        }
        if (before.optional() != after.optional()) {
            items.add(after.optional() ? "now optional" : "no longer optional");
        }
        exclusionDelta(before.exclusions(), after.exclusions(), "removed").ifPresent(items::add);
        exclusionDelta(after.exclusions(), before.exclusions(), "added").ifPresent(items::add);
        return String.join(", ", items);
    }

    /** "exclusions a:b, c:d removed" for the entries of {@code from} missing from {@code to}. */
    private static Optional<String> exclusionDelta(Set<String> from, Set<String> to, String verb) {
        List<String> missing = from.stream().filter(exclusion -> !to.contains(exclusion)).toList();
        if (missing.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of((missing.size() == 1 ? "exclusion " : "exclusions ") + String.join(", ", missing) + " " + verb);
    }

    private static String orNone(String value) {
        return value.isEmpty() ? "none" : value;
    }

    private static List<String> pomsUnder(Path root) {
        List<String> poms = new ArrayList<>();
        if (root == null || !Files.isDirectory(root)) {
            return poms;
        }
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) {
                    String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                    return !dir.equals(root) && (name.startsWith(".") || NEVER_SEARCHED.contains(name))
                            ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    if (file.getFileName().toString().equals("pom.xml")) {
                        poms.add(root.relativize(file).toString().replace('\\', '/'));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Could not search " + root + " for pom.xml files", e);
        }
        return poms;
    }

    private static Optional<String> read(Path file) {
        try {
            return Files.isRegularFile(file) ? Optional.of(Files.readString(file)) : Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        }
    }
}
