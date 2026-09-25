package com.athena.semantic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleTopologyBuilderTest {

    // ---- Gradle and nested modules (ticket #293) ----

    @Test
    void identifiesASpringBootGradleModuleByItsBuildFile(@TempDir Path base, @TempDir Path head) throws IOException {
        write(head, "module/web-server/build.gradle", "plugins { id 'org.springframework.boot' }\n");

        ModuleTopology topology = builder.build(layoutGroupsFor(base, head, "module/web-server/src/main/java/A.java"), base, head);

        assertThat(territoryFor(topology, "web-server").techStack()).isEqualTo(TechStack.SPRING_BOOT_JAVA);
    }

    @Test
    void identifiesAKotlinDslGradleModuleWithoutSpringBootAsJava(@TempDir Path base, @TempDir Path head) throws IOException {
        write(head, "module/validation/build.gradle.kts", "plugins { java }\n");

        ModuleTopology topology = builder.build(layoutGroupsFor(base, head, "module/validation/src/main/java/A.java"), base, head);

        assertThat(territoryFor(topology, "validation").techStack()).isEqualTo(TechStack.JAVA);
    }

    @Test
    void detectsAGradleProjectDependencyAndAddsItsTargetAsIdle(@TempDir Path base, @TempDir Path head) throws IOException {
        write(head, "module/core/build.gradle", "");
        write(head, "module/web-server/build.gradle", "dependencies {\n    api(project(\":module:core\"))\n}\n");

        ModuleTopology topology = builder.build(layoutGroupsFor(base, head, "module/web-server/src/main/java/A.java"), base, head);

        assertThat(topology.dependencies()).containsExactly(new ModuleDependency("web-server", "core"));
        assertThat(territoryFor(topology, "core").status()).isEqualTo(ModuleStatus.IDLE);
        assertThat(territoryFor(topology, "core").techStack()).isEqualTo(TechStack.JAVA);
    }

    @Test
    void detectsAGradleTypeSafeProjectAccessorDependency(@TempDir Path base, @TempDir Path head) throws IOException {
        write(head, "module/spring-boot-core/build.gradle", "");
        write(head, "module/web-server/build.gradle", "dependencies {\n    api(projects.module.springBootCore)\n}\n");

        ModuleTopology topology = builder.build(layoutGroupsFor(base, head, "module/web-server/src/main/java/A.java"), base, head);

        assertThat(topology.dependencies()).containsExactly(new ModuleDependency("web-server", "spring-boot-core"));
    }

    @Test
    void detectsANestedMavenModuleDependencyByItsSiblingsArtifactId(@TempDir Path base, @TempDir Path head)
            throws IOException {
        write(head, "modules/model/pom.xml", "<project><parent><artifactId>p</artifactId></parent>"
                + "<artifactId>acme-model</artifactId></project>");
        write(head, "modules/api/pom.xml", "<project><parent><artifactId>p</artifactId></parent><artifactId>acme-api</artifactId>"
                + "<dependencies><dependency><artifactId>acme-model</artifactId></dependency></dependencies></project>");

        ModuleTopology topology = builder.build(layoutGroupsFor(base, head, "modules/api/src/main/java/A.java"), base, head);

        assertThat(topology.dependencies()).containsExactly(new ModuleDependency("api", "model"));
    }

    @Test
    void readsASingleModuleProjectsTechStackFromItsRootBuildFile(@TempDir Path base, @TempDir Path head) throws IOException {
        write(head, "build.gradle", "plugins { id 'java' }\n");
        write(head, "settings.gradle", "rootProject.name = 'petclinic'\n");

        ModuleTopology topology = builder.build(layoutGroupsFor(base, head, "src/main/java/A.java"), base, head);

        assertThat(territoryFor(topology, "petclinic").techStack()).isEqualTo(TechStack.JAVA);
    }

    @Test
    void neverLooksForModulesInsideSourceOrBuildOutputDirectories(@TempDir Path base, @TempDir Path head)
            throws IOException {
        write(head, "module/web-server/build.gradle", "dependencies {\n    api(project(\":fixtures\"))\n}\n");
        write(head, "module/web-server/src/test/resources/fixtures/build.gradle", "");

        ModuleTopology topology = builder.build(layoutGroupsFor(base, head, "module/web-server/src/main/java/A.java"), base, head);

        assertThat(topology.dependencies()).isEmpty();
    }

    @Test
    void readsRailsAndTechStackFromABuildFileNamedAfterItsModule(@TempDir Path base, @TempDir Path head)
            throws IOException {
        write(head, "spring-core/spring-core.gradle", "");
        write(head, "spring-webmvc/spring-webmvc.gradle",
                "plugins { id 'org.springframework.boot' }\ndependencies {\n    api(project(\":spring-core\"))\n}\n");

        ModuleTopology topology = builder.build(layoutGroupsFor(base, head, "spring-webmvc/src/main/java/A.java"), base, head);

        assertThat(topology.dependencies()).containsExactly(new ModuleDependency("spring-webmvc", "spring-core"));
        assertThat(territoryFor(topology, "spring-webmvc").techStack()).isEqualTo(TechStack.SPRING_BOOT_JAVA);
        assertThat(territoryFor(topology, "spring-core").techStack()).isEqualTo(TechStack.JAVA);
    }

    // ---- Root project blocks (ticket #376) ----

    private static final String KAFKA_SETTINGS = "rootProject.name = 'kafka'\ninclude 'clients', 'core', 'streams', 'streams:integration-tests'\n";

    @Test
    void readsARailFromTheChangedProjectsBlockInTheRootBuildFile(@TempDir Path base, @TempDir Path head) throws IOException {
        writeKafka(head, "project(':streams') {\n  dependencies {\n    implementation project(':clients')\n"
                + "    implementation(project(\":core\")) { exclude module: 'x' }\n  }\n}\n");

        ModuleTopology topology = builder.build(layoutGroupsFor(base, head, "streams/src/main/java/A.java"), base, head);

        assertThat(topology.dependencies()).containsExactly(
                new ModuleDependency("streams", "clients"), new ModuleDependency("streams", "core"));
        assertThat(territoryFor(topology, "clients").status()).isEqualTo(ModuleStatus.IDLE);
    }

    @Test
    void readsANestedProjectsBlock(@TempDir Path base, @TempDir Path head) throws IOException {
        writeKafka(head, "project(':streams:integration-tests') {\n  dependencies {\n    testImplementation project(':streams')\n  }\n}\n");

        ModuleTopology topology = builder.build(
                layoutGroupsFor(base, head, "streams/integration-tests/src/test/java/A.java"), base, head);

        assertThat(topology.dependencies()).containsExactly(new ModuleDependency("integration-tests", "streams"));
    }

    @Test
    void ignoresOtherProjectsBlocksAndSubprojects(@TempDir Path base, @TempDir Path head) throws IOException {
        writeKafka(head, "subprojects {\n  dependencies { implementation project(':clients') }\n}\n"
                + "project(':core') {\n  dependencies { implementation project(':clients') }\n}\n");

        ModuleTopology topology = builder.build(layoutGroupsFor(base, head, "streams/src/main/java/A.java"), base, head);

        assertThat(topology.dependencies()).isEmpty();
    }

    @Test
    void theRootModuleDoesNotInheritItsProjectsBlocks(@TempDir Path base, @TempDir Path head) throws IOException {
        writeKafka(head, "dependencies { implementation project(':core') }\n"
                + "project(':streams') {\n  dependencies { implementation project(':clients') }\n}\n");

        ModuleTopology topology = builder.build(layoutGroupsFor(base, head, "src/main/java/A.java"), base, head);

        assertThat(topology.dependencies()).containsExactly(new ModuleDependency("kafka", "core"));
    }

    @Test
    void aBraceInsideAStringDoesNotEndTheBlock(@TempDir Path base, @TempDir Path head) throws IOException {
        writeKafka(head, "project(':streams') {\n  description = 'uses } braces {'\n"
                + "  dependencies { implementation project(':clients') }\n}\n");

        ModuleTopology topology = builder.build(layoutGroupsFor(base, head, "streams/src/main/java/A.java"), base, head);

        assertThat(topology.dependencies()).containsExactly(new ModuleDependency("streams", "clients"));
    }

    @Test
    void anApostropheInACommentDoesNotEndTheBlock(@TempDir Path base, @TempDir Path head) throws IOException {
        writeKafka(head, "project(':core') {\n  // don't add deps here\n}\n"
                + "project(':streams') {\n  dependencies { implementation project(':clients') }\n}\n");

        ModuleTopology topology = builder.build(layoutGroupsFor(base, head, "streams/src/main/java/A.java"), base, head);

        assertThat(topology.dependencies()).containsExactly(new ModuleDependency("streams", "clients"));
    }

    private static void writeKafka(Path head, String rootBuildFile) throws IOException {
        write(head, "settings.gradle", KAFKA_SETTINGS);
        write(head, "build.gradle", rootBuildFile);
        for (String project : List.of("clients", "core", "streams", "streams/integration-tests")) {
            write(head, project + "/src/main/java/Placeholder.java", "");
        }
    }

    private List<ModuleGroup> layoutGroupsFor(Path base, Path head, String file) {
        return new ModuleGrouper(ModuleLayout.of(base, head)).group(new ChangeGrouper().group(List.of(
                DetectedTransformation.of(TransformationKind.ADD_SYMBOL, List.of("A#m"), List.of(file)))));
    }

    private static void write(Path root, String relativePath, String content) throws IOException {
        Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private final ModuleTopologyBuilder builder = new ModuleTopologyBuilder();

    @Test
    void marksAModuleAbsentFromTheBaseRevisionAsNew(@TempDir Path base, @TempDir Path head) throws IOException {
        writeMavenModule(head, "crowdness-live", List.of());
        List<ModuleGroup> groups = groupsFor(moduleChange("crowdness-live", "Live.java"));

        ModuleTopology topology = builder.build(groups, base, head);

        ModuleTerritory territory = territoryFor(topology, "crowdness-live");
        assertThat(territory.status()).isEqualTo(ModuleStatus.NEW);
        assertThat(territory.statusSummary()).contains("new module");
    }

    @Test
    void marksAModuleThatExistedAtBaseAsTouched(@TempDir Path base, @TempDir Path head) throws IOException {
        writeMavenModule(base, "crowdness-ingestion", List.of());
        writeMavenModule(head, "crowdness-ingestion", List.of());
        List<ModuleGroup> groups = groupsFor(moduleChange("crowdness-ingestion", "Ingestion.java"));

        ModuleTopology topology = builder.build(groups, base, head);

        assertThat(territoryFor(topology, "crowdness-ingestion").status()).isEqualTo(ModuleStatus.TOUCHED);
    }

    @Test
    void reportsTheRealFileCountAndChangeSummaryAsStatusText(@TempDir Path base, @TempDir Path head)
            throws IOException {
        writeMavenModule(base, "crowdness-ingestion", List.of());
        writeMavenModule(head, "crowdness-ingestion", List.of());
        List<ModuleGroup> groups = groupsFor(moduleChange("crowdness-ingestion", "Query.java"));

        ModuleTopology topology = builder.build(groups, base, head);

        ModuleTerritory territory = territoryFor(topology, "crowdness-ingestion");
        assertThat(territory.fileCount()).isEqualTo(1);
        assertThat(territory.statusSummary()).startsWith("1 file");
    }

    @Test
    void detectsARealMavenDependencyBetweenTwoModules(@TempDir Path base, @TempDir Path head) throws IOException {
        writeMavenModule(head, "crowdness-connect", List.of());
        writeMavenModule(head, "crowdness-live", List.of("crowdness-connect"));
        writeMavenModule(base, "crowdness-live", List.of("crowdness-connect"));
        List<ModuleGroup> groups = groupsFor(moduleChange("crowdness-live", "Live.java"));

        ModuleTopology topology = builder.build(groups, base, head);

        assertThat(topology.dependencies()).contains(new ModuleDependency("crowdness-live", "crowdness-connect"));
    }

    @Test
    void addsAnUnmodifiedDependedOnModuleAsAnIdleTerritory(@TempDir Path base, @TempDir Path head) throws IOException {
        writeMavenModule(head, "crowdness-connect", List.of());
        writeMavenModule(head, "crowdness-live", List.of("crowdness-connect"));
        writeMavenModule(base, "crowdness-live", List.of("crowdness-connect"));
        List<ModuleGroup> groups = groupsFor(moduleChange("crowdness-live", "Live.java"));

        ModuleTopology topology = builder.build(groups, base, head);

        ModuleTerritory idle = territoryFor(topology, "crowdness-connect");
        assertThat(idle.status()).isEqualTo(ModuleStatus.IDLE);
        assertThat(idle.changes()).isEmpty();
    }

    @Test
    void doesNotDrawADependencyBetweenModulesWithNoRealDependency(@TempDir Path base, @TempDir Path head)
            throws IOException {
        writeMavenModule(head, "crowdness-live", List.of());
        writeMavenModule(head, "crowdness-management", List.of());
        writeMavenModule(base, "crowdness-live", List.of());
        writeMavenModule(base, "crowdness-management", List.of());
        List<ModuleGroup> groups = groupsFor(
                moduleChange("crowdness-live", "Live.java"), moduleChange("crowdness-management", "Mgmt.java"));

        ModuleTopology topology = builder.build(groups, base, head);

        assertThat(topology.dependencies()).isEmpty();
    }

    @Test
    void identifiesASpringBootModuleByItsRealPomContent(@TempDir Path base, @TempDir Path head) throws IOException {
        Path moduleDir = head.resolve("crowdness-live");
        Files.createDirectories(moduleDir);
        Files.writeString(moduleDir.resolve("pom.xml"), """
                <project>
                  <artifactId>crowdness-live</artifactId>
                  <dependencies>
                    <dependency><artifactId>spring-boot-starter-web</artifactId></dependency>
                  </dependencies>
                </project>
                """);
        List<ModuleGroup> groups = groupsFor(moduleChange("crowdness-live", "Live.java"));

        ModuleTopology topology = builder.build(groups, base, head);

        assertThat(territoryFor(topology, "crowdness-live").techStack()).isEqualTo(TechStack.SPRING_BOOT_JAVA);
    }

    @Test
    void identifiesAReactTypescriptModuleByItsRealPackageJson(@TempDir Path base, @TempDir Path head)
            throws IOException {
        Path moduleDir = head.resolve("crowdness-ui");
        Files.createDirectories(moduleDir);
        Files.writeString(moduleDir.resolve("package.json"), """
                { "name": "crowdness-ui", "dependencies": { "react": "^18.0.0" }, "devDependencies": { "typescript": "^5.0.0" } }
                """);
        List<ModuleGroup> groups = groupsFor(moduleChange("crowdness-ui", "App.tsx"));

        ModuleTopology topology = builder.build(groups, base, head);

        assertThat(territoryFor(topology, "crowdness-ui").techStack()).isEqualTo(TechStack.REACT_TYPESCRIPT);
    }

    @Test
    void doesNotTreatAnUnrelatedFieldThatMatchesAModuleNameAsARealDependency(
            @TempDir Path base, @TempDir Path head) throws IOException {
        Files.createDirectories(head.resolve("crowdness-common"));
        Files.writeString(head.resolve("crowdness-common/package.json"), "{ \"name\": \"crowdness-common\" }");
        Path moduleDir = head.resolve("crowdness-ui");
        Files.createDirectories(moduleDir);
        // "main" isn't a dependency field, but its value happens to equal another real
        // module's name — this must not be read as crowdness-ui depending on it.
        Files.writeString(moduleDir.resolve("package.json"),
                "{ \"name\": \"crowdness-ui\", \"main\": \"crowdness-common\" }");
        List<ModuleGroup> groups = groupsFor(moduleChange("crowdness-ui", "App.tsx"));

        ModuleTopology topology = builder.build(groups, base, head);

        assertThat(topology.dependencies()).isEmpty();
        assertThat(topology.territories()).extracting(ModuleTerritory::moduleName).containsExactly("crowdness-ui");
    }

    private List<ModuleGroup> groupsFor(DetectedTransformation... transformations) {
        List<Change> changes = new ChangeGrouper().group(List.of(transformations));
        return new ModuleGrouper().group(changes);
    }

    private DetectedTransformation moduleChange(String moduleName, String fileName) {
        return DetectedTransformation.of(TransformationKind.ADD_SYMBOL,
                List.of("Symbol#member"), List.of(moduleName + "/src/main/java/" + fileName));
    }

    private ModuleTerritory territoryFor(ModuleTopology topology, String moduleName) {
        return topology.territories().stream()
                .filter(t -> t.moduleName().equals(moduleName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No territory found for " + moduleName));
    }

    private void writeMavenModule(Path root, String moduleName, List<String> dependsOnArtifactIds) throws IOException {
        Path moduleDir = root.resolve(moduleName);
        Files.createDirectories(moduleDir);
        StringBuilder pom = new StringBuilder("<project>\n  <artifactId>").append(moduleName).append("</artifactId>\n");
        if (!dependsOnArtifactIds.isEmpty()) {
            pom.append("  <dependencies>\n");
            for (String dep : dependsOnArtifactIds) {
                pom.append("    <dependency><artifactId>").append(dep).append("</artifactId></dependency>\n");
            }
            pom.append("  </dependencies>\n");
        }
        pom.append("</project>\n");
        Files.writeString(moduleDir.resolve("pom.xml"), pom.toString());
    }
}
