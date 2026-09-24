package com.athena.semantic;

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
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which module a changed file belongs to (ticket #292): the nearest directory above it that
 * holds a build descriptor ({@code pom.xml}, {@code build.gradle}, {@code build.gradle.kts},
 * {@code package.json}, or a Gradle build file named after the directory itself —
 * {@code hibernate-core/hibernate-core.gradle}, ticket #314), in head or — for a file only in
 * base — in base. The module is named
 * after that directory; a descriptor at the repository root names it after the project
 * ({@code artifactId}, {@code rootProject.name} or {@code "name"}), or {@code "(root)"}.
 * With no descriptor anywhere above a file, the file's leading path segment names its module,
 * as before build descriptors were read.
 *
 * <p>{@link #pathBased()} keeps that leading-segment rule alone, for callers without the
 * revision roots at hand.
 */
public final class ModuleLayout {

    public static final String ROOT = "(root)";

    private static final Set<String> NEVER_MODULE_DIRECTORIES = Set.of("node_modules", "target", "build", "src", "out", "bin");
    private static final int MAX_MODULE_DEPTH = 5;
    private static final List<String> BUILD_DESCRIPTORS = List.of("pom.xml", "build.gradle", "build.gradle.kts", "package.json");
    private static final Pattern MAVEN_PARENT = Pattern.compile("<parent>.*?</parent>", Pattern.DOTALL);
    private static final Pattern MAVEN_ARTIFACT_ID = Pattern.compile("<artifactId>\\s*([\\w.-]+)\\s*</artifactId>");
    private static final Pattern GRADLE_ROOT_PROJECT_NAME = Pattern.compile("rootProject\\.name\\s*=\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern PACKAGE_JSON_NAME = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");

    private final List<Path> roots;
    private final Map<String, Optional<String>> descriptorDirectoryCache = new ConcurrentHashMap<>();
    private List<String> moduleDirectories;

    private ModuleLayout(List<Path> roots) {
        this.roots = List.copyOf(roots);
    }

    /** Reads build descriptors from {@code headRoot} first, then {@code baseRoot}. */
    public static ModuleLayout of(Path baseRoot, Path headRoot) {
        return new ModuleLayout(List.of(headRoot, baseRoot));
    }

    /** The leading-path-segment rule alone, reading no files. */
    public static ModuleLayout pathBased() {
        return new ModuleLayout(List.of());
    }

    /** The directory (relative, "" for the repository root) of the module {@code filePath} belongs to. */
    public String directoryOf(String filePath) {
        String normalized = filePath.replace('\\', '/');
        int lastSlash = normalized.lastIndexOf('/');
        String directory = lastSlash < 0 ? "" : normalized.substring(0, lastSlash);
        return nearestDescriptorDirectory(directory).orElseGet(() -> leadingSegment(normalized));
    }

    /**
     * The name of the module whose directory is {@code directory}, as {@link #directoryOf} returns
     * it: the directory's own name, unique within the repository (ticket #317). When another
     * module directory has the same name, the one whose whole path is that name keeps it, and
     * each other one gets its shortest distinguishing parent path in parentheses —
     * {@code guava} and {@code guava (android)}. Parent segments are joined with {@code :}, never
     * {@code /}, since module names appear in URL paths.
     */
    public String nameOf(String directory) {
        if (directory.isEmpty()) {
            return projectName().orElse(ROOT);
        }
        List<String> segments = List.of(directory.split("/"));
        String name = segments.get(segments.size() - 1);
        List<String> namesakes = moduleDirectories().stream()
                .filter(other -> !other.equals(directory) && (other.equals(name) || other.endsWith("/" + name)))
                .toList();
        if (namesakes.isEmpty() || segments.size() == 1) {
            return name;
        }
        for (int parents = 1; parents < segments.size(); parents++) {
            String suffix = String.join("/", segments.subList(segments.size() - 1 - parents, segments.size()));
            if (namesakes.stream().noneMatch(other -> other.equals(suffix) || other.endsWith("/" + suffix))) {
                return name + " (" + String.join(":", segments.subList(segments.size() - 1 - parents, segments.size() - 1)) + ")";
            }
        }
        return name + " (" + String.join(":", segments.subList(0, segments.size() - 1)) + ")";
    }

    /**
     * Every module directory of the repository (ticket #317; discovery from #293): each
     * directory, up to {@value #MAX_MODULE_DEPTH} levels down in either revision, holding a build
     * descriptor. Source, build-output and hidden directories are never searched. Computed once.
     */
    public synchronized List<String> moduleDirectories() {
        if (moduleDirectories == null) {
            Set<String> found = new TreeSet<>();
            roots.forEach(root -> found.addAll(discoverModuleDirectories(root)));
            moduleDirectories = List.copyOf(found);
        }
        return moduleDirectories;
    }

    private static List<String> discoverModuleDirectories(Path root) {
        List<String> directories = new ArrayList<>();
        if (root == null || !Files.isDirectory(root)) {
            return directories;
        }
        try {
            Files.walkFileTree(root, Set.of(), MAX_MODULE_DEPTH, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) {
                    String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                    if (!dir.equals(root) && (name.startsWith(".") || NEVER_MODULE_DIRECTORIES.contains(name))) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    String directory = root.relativize(dir).toString().replace('\\', '/');
                    if (buildDescriptorNames(directory).stream().anyMatch(descriptor -> Files.isRegularFile(dir.resolve(descriptor)))) {
                        directories.add(directory);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Could not discover modules under " + root, e);
        }
        return directories;
    }

    private Optional<String> nearestDescriptorDirectory(String directory) {
        if (roots.isEmpty()) {
            return Optional.empty();
        }
        return descriptorDirectoryCache.computeIfAbsent(directory, this::walkUpToDescriptor);
    }

    private Optional<String> walkUpToDescriptor(String directory) {
        String current = directory;
        while (true) {
            if (hasDescriptor(current)) {
                return Optional.of(current);
            }
            if (current.isEmpty()) {
                return Optional.empty();
            }
            int lastSlash = current.lastIndexOf('/');
            current = lastSlash < 0 ? "" : current.substring(0, lastSlash);
        }
    }

    private boolean hasDescriptor(String directory) {
        return roots.stream().anyMatch(root -> buildDescriptorNames(directory).stream()
                .anyMatch(descriptor -> Files.isRegularFile(root.resolve(directory).resolve(descriptor))));
    }

    /**
     * The build descriptor file names a module directory may hold: the fixed names, plus Gradle
     * build files named after the directory ({@code <name>.gradle}, {@code <name>.gradle.kts}),
     * as builds setting {@code project.buildFileName} use (ticket #314).
     */
    public static List<String> buildDescriptorNames(String directory) {
        String name = directory.substring(directory.lastIndexOf('/') + 1);
        if (name.isEmpty()) {
            return BUILD_DESCRIPTORS;
        }
        List<String> names = new ArrayList<>(BUILD_DESCRIPTORS);
        names.addAll(gradleBuildFileNamesFor(name));
        return names;
    }

    /** The Gradle build file names {@code directory} may hold: the fixed names, then ones named after it. */
    public static List<String> gradleBuildFileNames(String directory) {
        String name = directory.substring(directory.lastIndexOf('/') + 1);
        List<String> names = new ArrayList<>(List.of("build.gradle", "build.gradle.kts"));
        if (!name.isEmpty()) {
            names.addAll(gradleBuildFileNamesFor(name));
        }
        return names;
    }

    private static List<String> gradleBuildFileNamesFor(String name) {
        return List.of(name + ".gradle", name + ".gradle.kts");
    }

    private static String leadingSegment(String filePath) {
        int separator = filePath.indexOf('/');
        return separator > 0 ? filePath.substring(0, separator) : "";
    }

    private Optional<String> projectName() {
        for (Path root : roots) {
            Optional<String> name = firstMatch(root.resolve("pom.xml"), MAVEN_ARTIFACT_ID, true)
                    .or(() -> firstMatch(root.resolve("settings.gradle"), GRADLE_ROOT_PROJECT_NAME, false))
                    .or(() -> firstMatch(root.resolve("settings.gradle.kts"), GRADLE_ROOT_PROJECT_NAME, false))
                    .or(() -> firstMatch(root.resolve("package.json"), PACKAGE_JSON_NAME, false));
            if (name.isPresent()) {
                return name;
            }
        }
        return Optional.empty();
    }

    private static Optional<String> firstMatch(Path file, Pattern pattern, boolean skipMavenParent) {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            String content = Files.readString(file);
            if (skipMavenParent) {
                content = MAVEN_PARENT.matcher(content).replaceAll("");
            }
            Matcher matcher = pattern.matcher(content);
            return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read build descriptor " + file, e);
        }
    }
}
