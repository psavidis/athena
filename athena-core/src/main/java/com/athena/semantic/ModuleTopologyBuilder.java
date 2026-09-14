package com.athena.semantic;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    public ModuleTopology build(List<ModuleGroup> changedGroups, Path baseRoot, Path headRoot) {
        Set<String> allModuleNames = discoverModuleDirectories(headRoot);
        Map<String, ModuleGroup> changedByName = new LinkedHashMap<>();
        for (ModuleGroup group : changedGroups) {
            changedByName.put(group.moduleName(), group);
        }

        List<ModuleDependency> dependencies = new ArrayList<>();
        Set<String> referencedModules = new LinkedHashSet<>();
        for (String moduleName : changedByName.keySet()) {
            for (String dependsOn : manifestDependencies(headRoot, moduleName, allModuleNames)) {
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
            territories.add(idleTerritory(idleModuleName, headRoot));
        }

        return new ModuleTopology(territories, dependencies);
    }

    private ModuleTerritory territoryFor(ModuleGroup group, Path baseRoot, Path headRoot) {
        String moduleName = group.moduleName();
        boolean isNew = !Files.isDirectory(baseRoot.resolve(moduleName))
                && Files.isDirectory(headRoot.resolve(moduleName));
        Set<String> filesTouched = filesTouchedBy(group);
        ModuleStatus status = isNew ? ModuleStatus.NEW : ModuleStatus.TOUCHED;
        String summary = isNew
                ? filesTouched.size() + (filesTouched.size() == 1 ? " file" : " files") + " · new module"
                : filesTouched.size() + (filesTouched.size() == 1 ? " file" : " files")
                        + " · " + changeSummary(group);
        return new ModuleTerritory(moduleName, status, filesTouched.size(), summary,
                techStackOf(headRoot, moduleName), group.changes());
    }

    private ModuleTerritory idleTerritory(String moduleName, Path headRoot) {
        return new ModuleTerritory(moduleName, ModuleStatus.IDLE, 0, "unchanged",
                techStackOf(headRoot, moduleName), List.of());
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

    private Set<String> discoverModuleDirectories(Path root) {
        Set<String> names = new LinkedHashSet<>();
        if (root == null || !Files.isDirectory(root)) {
            return names;
        }
        try (var stream = Files.list(root)) {
            stream.filter(Files::isDirectory)
                    .filter(dir -> Files.isRegularFile(dir.resolve("pom.xml"))
                            || Files.isRegularFile(dir.resolve("package.json")))
                    .forEach(dir -> names.add(dir.getFileName().toString()));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not list module directories under " + root, e);
        }
        return names;
    }

    /** Real declared dependencies on other modules in this repo, read from this module's own manifest. */
    private Set<String> manifestDependencies(Path headRoot, String moduleName, Set<String> allModuleNames) {
        Path moduleDir = headRoot.resolve(moduleName);
        Set<String> found = new LinkedHashSet<>();

        Path pomFile = moduleDir.resolve("pom.xml");
        if (Files.isRegularFile(pomFile)) {
            String content = readQuietly(pomFile);
            Matcher matcher = MAVEN_ARTIFACT_ID.matcher(content);
            while (matcher.find()) {
                String artifactId = matcher.group(1);
                if (!artifactId.equals(moduleName) && allModuleNames.contains(artifactId)) {
                    found.add(artifactId);
                }
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
                    if (allModuleNames.contains(depName) && !depName.equals(moduleName)) {
                        found.add(depName);
                    }
                }
            }
        }

        return found;
    }

    private TechStack techStackOf(Path headRoot, String moduleName) {
        Path moduleDir = headRoot.resolve(moduleName);
        boolean hasPom = Files.isRegularFile(moduleDir.resolve("pom.xml"));
        boolean hasPackageJson = Files.isRegularFile(moduleDir.resolve("package.json"));
        if (hasPom) {
            String content = readQuietly(moduleDir.resolve("pom.xml"));
            return content.contains("spring-boot") ? TechStack.SPRING_BOOT_JAVA : TechStack.JAVA;
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
}
