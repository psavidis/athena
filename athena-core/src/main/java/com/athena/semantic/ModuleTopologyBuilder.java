package com.athena.semantic;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds the {@link ModuleTopology} the Semantic Canvas territory map draws
 * (ticket #129): starts from the modules {@link ModuleGrouper} found changed,
 * then walks each one's real manifest (a Maven {@code pom.xml} or npm
 * {@code package.json}) to find both its tech stack and which sibling
 * modules it actually depends on — adding those siblings as idle territories
 * when they weren't already changed. A module absent from {@code baseRoot}
 * but present in {@code headRoot} is {@link ModuleStatus#NEW}; only
 * discoverable this way because {@link ModuleGrouper} has no "was this file
 * new" concept of its own.
 */
public final class ModuleTopologyBuilder {

    private static final Pattern MAVEN_ARTIFACT_ID =
            Pattern.compile("<artifactId>\\s*([\\w.-]+)\\s*</artifactId>");
    // Captures a "dependencies"/"devDependencies" object's own body (non-greedy up to its
    // closing brace) so package-name matching stays scoped to real dependency entries —
    // matching every "key": "value" pair in the file would false-positive on an unrelated
    // field (e.g. "main": "crowdness-common") that happens to equal another module's name.
    private static final Pattern PACKAGE_JSON_DEPENDENCIES_BLOCK =
            Pattern.compile("\"(?:dependencies|devDependencies)\"\\s*:\\s*\\{([^}]*)}");
    private static final Pattern PACKAGE_JSON_DEPENDENCY_NAME =
            Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"[^\"]*\"");
    private static final Pattern MAVEN_PARENT = Pattern.compile("<parent>.*?</parent>", Pattern.DOTALL);
    // project(":module:web-server"), project(path: ":core") — Groovy and Kotlin DSL alike.
    private static final Pattern GRADLE_PROJECT_DEPENDENCY =
            Pattern.compile("project\\(\\s*(?:path\\s*[:=]\\s*)?['\"](:[^'\"]+)['\"]");
    // projects.module.springBootCore — Gradle's type-safe project accessors.
    private static final Pattern GRADLE_PROJECT_ACCESSOR = Pattern.compile("\\bprojects((?:\\.\\w+)+)");
    private static final List<String> GRADLE_BUILD_FILES = List.of("build.gradle", "build.gradle.kts");
    private static final List<String> BUILD_DESCRIPTORS = List.of("pom.xml", "build.gradle", "build.gradle.kts", "package.json");
    private static final Set<String> NEVER_MODULE_DIRECTORIES = Set.of("node_modules", "target", "build", "src", "out", "bin");
    private static final int MAX_MODULE_DEPTH = 5;

    public ModuleTopology build(List<ModuleGroup> changedGroups, Path baseRoot, Path headRoot) {
        List<RepositoryModule> allModules = discoverModules(baseRoot, headRoot);
        Map<String, ModuleGroup> changedByName = new LinkedHashMap<>();
        for (ModuleGroup group : changedGroups) {
            changedByName.put(group.moduleName(), group);
        }

        List<ModuleDependency> dependencies = new ArrayList<>();
        Set<String> referencedModules = new LinkedHashSet<>();
        for (ModuleGroup group : changedGroups) {
            String moduleName = group.moduleName();
            for (String dependsOn : manifestDependencies(headRoot, group.directory(), moduleName, allModules)) {
                dependencies.add(new ModuleDependency(moduleName, dependsOn));
                if (!changedByName.containsKey(dependsOn)) {
                    referencedModules.add(dependsOn);
                }
            }
        }

        List<ModuleTerritory> territories = new ArrayList<>();
        for (ModuleGroup group : changedGroups) {
            territories.add(territoryFor(group, baseRoot, headRoot));
        }
        for (String idleModuleName : referencedModules) {
            String directory = allModules.stream().filter(module -> module.name().equals(idleModuleName))
                    .map(RepositoryModule::directory).findFirst().orElse(idleModuleName);
            territories.add(idleTerritory(idleModuleName, directory, headRoot));
        }

        return new ModuleTopology(territories, dependencies);
    }

    private ModuleTerritory territoryFor(ModuleGroup group, Path baseRoot, Path headRoot) {
        String moduleName = group.moduleName();
        // The module's own directory (ticket #292), which for a nested module isn't its name.
        String directory = group.directory();
        boolean isNew = !directory.isEmpty() && !Files.isDirectory(baseRoot.resolve(directory))
                && Files.isDirectory(headRoot.resolve(directory));
        Set<String> filesTouched = filesTouchedBy(group);
        ModuleStatus status = isNew ? ModuleStatus.NEW : ModuleStatus.TOUCHED;
        String summary = isNew
                ? filesTouched.size() + (filesTouched.size() == 1 ? " file" : " files") + " · new module"
                : filesTouched.size() + (filesTouched.size() == 1 ? " file" : " files")
                        + " · " + changeSummary(group);
        return new ModuleTerritory(moduleName, status, filesTouched.size(), summary,
                techStackOf(headRoot, directory), group.changes());
    }

    private ModuleTerritory idleTerritory(String moduleName, String directory, Path headRoot) {
        return new ModuleTerritory(moduleName, ModuleStatus.IDLE, 0, "unchanged",
                techStackOf(headRoot, directory), List.of());
    }

    private Set<String> filesTouchedBy(ModuleGroup group) {
        Set<String> files = new LinkedHashSet<>();
        for (Change change : group.changes()) {
            for (DetectedTransformation occurrence : change.matchedOccurrences()) {
                files.addAll(occurrence.filesTouched());
            }
        }
        return files;
    }

    private String changeSummary(ModuleGroup group) {
        if (group.changes().isEmpty()) {
            return "no classified changes";
        }
        if (group.changes().size() == 1) {
            String title = group.changes().get(0).title();
            return title == null || title.isBlank() ? "one change" : title;
        }
        return group.changes().size() + " changes";
    }

    /**
     * Every module of the repository at head (ticket #293): each directory, up to {@value
     * #MAX_MODULE_DEPTH} levels down, holding a build descriptor — named as {@link ModuleLayout}
     * names it, so a dependency target matches its territory's name. Source, build-output and
     * hidden directories are never searched.
     */
    private List<RepositoryModule> discoverModules(Path baseRoot, Path headRoot) {
        List<RepositoryModule> modules = new ArrayList<>();
        if (headRoot == null || !Files.isDirectory(headRoot)) {
            return modules;
        }
        ModuleLayout layout = ModuleLayout.of(baseRoot, headRoot);
        try {
            Files.walkFileTree(headRoot, Set.of(), MAX_MODULE_DEPTH, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) {
                    String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                    if (!dir.equals(headRoot) && (name.startsWith(".") || NEVER_MODULE_DIRECTORIES.contains(name))) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (BUILD_DESCRIPTORS.stream().anyMatch(descriptor -> Files.isRegularFile(dir.resolve(descriptor)))) {
                        String directory = headRoot.relativize(dir).toString().replace('\\', '/');
                        modules.add(new RepositoryModule(directory, layout.nameOf(directory), mavenArtifactId(dir)));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Could not discover modules under " + headRoot, e);
        }
        return modules;
    }

    private String mavenArtifactId(Path moduleDir) {
        Path pom = moduleDir.resolve("pom.xml");
        if (!Files.isRegularFile(pom)) {
            return "";
        }
        Matcher matcher = MAVEN_ARTIFACT_ID.matcher(MAVEN_PARENT.matcher(readQuietly(pom)).replaceAll(""));
        return matcher.find() ? matcher.group(1) : "";
    }

    /**
     * Real declared dependencies on other modules in this repo, read from this module's own
     * build files: Maven artifactIds, Gradle {@code project(":…")} references and type-safe
     * {@code projects.…} accessors (ticket #293), and npm package names.
     */
    private Set<String> manifestDependencies(Path headRoot, String moduleDirectory, String moduleName,
                                             List<RepositoryModule> allModules) {
        Path moduleDir = headRoot.resolve(moduleDirectory);
        Set<String> found = new LinkedHashSet<>();

        Path pomFile = moduleDir.resolve("pom.xml");
        if (Files.isRegularFile(pomFile)) {
            String content = MAVEN_PARENT.matcher(readQuietly(pomFile)).replaceAll("");
            Matcher matcher = MAVEN_ARTIFACT_ID.matcher(content);
            while (matcher.find()) {
                String artifactId = matcher.group(1);
                allModules.stream()
                        // A Maven module is matched by its own artifactId only; its directory name
                        // could equal some unrelated external artifact ("core", "ui", "tests").
                        .filter(module -> module.artifactId().isEmpty()
                                ? module.name().equals(artifactId) : module.artifactId().equals(artifactId))
                        .map(RepositoryModule::name)
                        .findFirst()
                        .ifPresent(found::add);
            }
        }

        for (String buildFile : GRADLE_BUILD_FILES) {
            Path gradleFile = moduleDir.resolve(buildFile);
            if (!Files.isRegularFile(gradleFile)) {
                continue;
            }
            String content = readQuietly(gradleFile);
            Matcher projectMatcher = GRADLE_PROJECT_DEPENDENCY.matcher(content);
            while (projectMatcher.find()) {
                String directory = projectMatcher.group(1).substring(1).replace(':', '/');
                String lastSegment = directory.substring(directory.lastIndexOf('/') + 1);
                allModules.stream()
                        .filter(module -> module.directory().equals(directory))
                        .findFirst()
                        .or(() -> allModules.stream().filter(module -> module.name().equals(lastSegment)).findFirst())
                        .map(RepositoryModule::name)
                        .ifPresent(found::add);
            }
            Matcher accessorMatcher = GRADLE_PROJECT_ACCESSOR.matcher(content);
            while (accessorMatcher.find()) {
                String path = accessorMatcher.group(1);
                String accessor = path.substring(path.lastIndexOf('.') + 1);
                allModules.stream()
                        .filter(module -> camelCase(module.name()).equals(accessor))
                        .map(RepositoryModule::name)
                        .findFirst()
                        .ifPresent(found::add);
            }
        }

        Path packageJson = moduleDir.resolve("package.json");
        if (Files.isRegularFile(packageJson)) {
            String content = readQuietly(packageJson);
            Matcher blockMatcher = PACKAGE_JSON_DEPENDENCIES_BLOCK.matcher(content);
            while (blockMatcher.find()) {
                Matcher nameMatcher = PACKAGE_JSON_DEPENDENCY_NAME.matcher(blockMatcher.group(1));
                while (nameMatcher.find()) {
                    String depName = nameMatcher.group(1);
                    if (allModules.stream().anyMatch(module -> module.name().equals(depName))) {
                        found.add(depName);
                    }
                }
            }
        }

        found.remove(moduleName);
        return found;
    }

    /** "spring-boot-core" -> "springBootCore", as Gradle derives type-safe project accessors. */
    private static String camelCase(String name) {
        StringBuilder camel = new StringBuilder();
        boolean upper = false;
        for (char c : name.toCharArray()) {
            if (c == '-' || c == '_' || c == '.') {
                upper = true;
            } else {
                camel.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            }
        }
        return camel.toString();
    }

    private TechStack techStackOf(Path headRoot, String moduleDirectory) {
        Path moduleDir = headRoot.resolve(moduleDirectory);
        boolean hasPom = Files.isRegularFile(moduleDir.resolve("pom.xml"));
        boolean hasPackageJson = Files.isRegularFile(moduleDir.resolve("package.json"));
        if (hasPom) {
            String content = readQuietly(moduleDir.resolve("pom.xml"));
            return content.contains("spring-boot") ? TechStack.SPRING_BOOT_JAVA : TechStack.JAVA;
        }
        for (String buildFile : GRADLE_BUILD_FILES) {
            if (Files.isRegularFile(moduleDir.resolve(buildFile))) {
                String content = readQuietly(moduleDir.resolve(buildFile));
                return content.contains("spring-boot") || content.contains("org.springframework.boot")
                        ? TechStack.SPRING_BOOT_JAVA : TechStack.JAVA;
            }
        }
        if (hasPackageJson) {
            String content = readQuietly(moduleDir.resolve("package.json"));
            return content.contains("\"typescript\"") || Files.isRegularFile(moduleDir.resolve("tsconfig.json"))
                    ? TechStack.REACT_TYPESCRIPT
                    : TechStack.TYPESCRIPT;
        }
        return TechStack.UNKNOWN;
    }

    private String readQuietly(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read manifest " + file, e);
        }
    }

    /** A module found in the repository: its directory ("" for the root), name and Maven artifactId ("" if none). */
    private record RepositoryModule(String directory, String name, String artifactId) {
    }
}
