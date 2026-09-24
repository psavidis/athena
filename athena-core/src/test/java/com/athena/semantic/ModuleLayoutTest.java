package com.athena.semantic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleLayoutTest {

    @TempDir
    Path baseRoot;

    @TempDir
    Path headRoot;

    @Test
    void aFileBelongsToTheNearestDirectoryWithABuildDescriptor() throws IOException {
        write(headRoot, "module/web-server/build.gradle", "");
        ModuleLayout layout = ModuleLayout.of(baseRoot, headRoot);

        String directory = layout.directoryOf("module/web-server/src/main/java/com/acme/Server.java");

        assertThat(directory).isEqualTo("module/web-server");
        assertThat(layout.nameOf(directory)).isEqualTo("web-server");
    }

    @Test
    void recognizesEveryKindOfBuildDescriptor() throws IOException {
        write(headRoot, "a/pom.xml", "<project/>");
        write(headRoot, "b/build.gradle.kts", "");
        write(headRoot, "c/package.json", "{}");
        ModuleLayout layout = ModuleLayout.of(baseRoot, headRoot);

        assertThat(layout.directoryOf("a/src/A.java")).isEqualTo("a");
        assertThat(layout.directoryOf("b/src/B.java")).isEqualTo("b");
        assertThat(layout.directoryOf("c/src/c.ts")).isEqualTo("c");
    }

    @Test
    void aModuleOnlyInBaseIsStillFound() throws IOException {
        write(baseRoot, "legacy/pom.xml", "<project/>");

        assertThat(ModuleLayout.of(baseRoot, headRoot).directoryOf("legacy/src/Old.java")).isEqualTo("legacy");
    }

    @Test
    void aRootMavenModuleIsNamedAfterItsArtifactIdNotItsParents() throws IOException {
        write(headRoot, "pom.xml", "<project><parent><artifactId>parent-pom</artifactId></parent>"
                + "<artifactId>commons-lang3</artifactId></project>");
        ModuleLayout layout = ModuleLayout.of(baseRoot, headRoot);

        assertThat(layout.nameOf(layout.directoryOf("src/main/java/Fraction.java"))).isEqualTo("commons-lang3");
    }

    @Test
    void aRootGradleModuleIsNamedAfterItsRootProjectName() throws IOException {
        write(headRoot, "build.gradle", "");
        write(headRoot, "settings.gradle", "rootProject.name = 'petclinic'\n");
        ModuleLayout layout = ModuleLayout.of(baseRoot, headRoot);

        assertThat(layout.nameOf(layout.directoryOf("src/main/java/Owner.java"))).isEqualTo("petclinic");
    }

    @Test
    void aRootModuleWithoutANameIsTheRoot() throws IOException {
        write(headRoot, "build.gradle", "");
        ModuleLayout layout = ModuleLayout.of(baseRoot, headRoot);

        assertThat(layout.nameOf(layout.directoryOf("src/main/java/Owner.java"))).isEqualTo(ModuleLayout.ROOT);
    }

    @Test
    void withoutAnyDescriptorTheLeadingSegmentNamesTheModule() {
        ModuleLayout layout = ModuleLayout.of(baseRoot, headRoot);

        assertThat(layout.nameOf(layout.directoryOf("core/src/main/java/Owner.java"))).isEqualTo("core");
        assertThat(layout.nameOf(layout.directoryOf("Owner.java"))).isEqualTo(ModuleLayout.ROOT);
    }

    @Test
    void thePathBasedLayoutReadsNoFiles() throws IOException {
        write(headRoot, "module/web-server/build.gradle", "");

        assertThat(ModuleLayout.pathBased().directoryOf("module/web-server/src/Server.java")).isEqualTo("module");
    }

    @Test
    void aGradleBuildFileNamedAfterItsDirectoryMakesItAModule() throws IOException {
        write(headRoot, "hibernate-core/hibernate-core.gradle", "");
        write(headRoot, "junit-jupiter-api/junit-jupiter-api.gradle.kts", "");
        ModuleLayout layout = ModuleLayout.of(baseRoot, headRoot);

        assertThat(layout.directoryOf("hibernate-core/src/main/java/A.java")).isEqualTo("hibernate-core");
        assertThat(layout.directoryOf("junit-jupiter-api/src/main/java/A.java")).isEqualTo("junit-jupiter-api");
    }

    @Test
    void aGradleFileNamedAfterAnotherDirectoryDoesNotMakeAModule() throws IOException {
        write(headRoot, "core/other.gradle", "");

        assertThat(ModuleLayout.of(baseRoot, headRoot).directoryOf("core/src/main/java/A.java")).isEqualTo("core");
        assertThat(ModuleLayout.buildDescriptorNames("core")).doesNotContain("other.gradle");
    }

    @Test
    void sameNamedModulesGetDistinguishingNames() throws IOException {
        write(headRoot, "guava/pom.xml", "<project/>");
        write(headRoot, "android/guava/pom.xml", "<project/>");
        ModuleLayout layout = ModuleLayout.of(baseRoot, headRoot);

        assertThat(layout.nameOf("guava")).isEqualTo("guava");
        assertThat(layout.nameOf("android/guava")).isEqualTo("guava (android)");
    }

    @Test
    void nestedNamesakesAreDistinguishedByTheirShortestDifferingParents() throws IOException {
        write(headRoot, "a/x/core/pom.xml", "<project/>");
        write(headRoot, "b/x/core/pom.xml", "<project/>");
        write(headRoot, "web/pom.xml", "<project/>");
        ModuleLayout layout = ModuleLayout.of(baseRoot, headRoot);

        assertThat(layout.nameOf("a/x/core")).isEqualTo("core (a:x)");
        assertThat(layout.nameOf("b/x/core")).isEqualTo("core (b:x)");
        assertThat(layout.nameOf("web")).isEqualTo("web");
    }

    @Test
    void discoversModulesButNeverInsideSourceOrHiddenDirectories() throws IOException {
        write(headRoot, "core/pom.xml", "<project/>");
        write(headRoot, "core/src/test/resources/fixture/pom.xml", "<project/>");
        write(headRoot, ".github/pom.xml", "<project/>");
        write(baseRoot, "legacy/build.gradle", "");

        assertThat(ModuleLayout.of(baseRoot, headRoot).moduleDirectories()).containsExactly("core", "legacy");
    }

    @Test
    void aDirectorysOnlyGradleFileMakesItAModuleWhateverItsName() throws IOException {
        write(headRoot, "settings.gradle", "");
        write(headRoot, "web/spring-security-web.gradle", "");
        write(headRoot, "config/spring-security-config.gradle", "");
        ModuleLayout layout = ModuleLayout.of(baseRoot, headRoot);

        assertThat(layout.directoryOf("web/src/main/java/A.java")).isEqualTo("web");
        assertThat(layout.directoryOf("config/src/main/java/B.java")).isEqualTo("config");
    }

    @Test
    void twoUnrelatedGradleFilesDoNotMakeAModule() throws IOException {
        write(headRoot, "scripts/a.gradle", "");
        write(headRoot, "scripts/b.gradle", "");

        assertThat(ModuleLayout.of(baseRoot, headRoot).moduleDirectories()).doesNotContain("scripts");
    }

    private static void write(Path root, String relativePath, String content) throws IOException {
        Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
