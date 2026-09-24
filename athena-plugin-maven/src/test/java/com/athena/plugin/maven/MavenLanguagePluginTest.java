package com.athena.plugin.maven;

import com.athena.semantic.DetectedTransformation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Dependency changes between two revisions of a module's pom.xml (ticket #340). */
class MavenLanguagePluginTest {

    @TempDir
    Path baseRoot;

    @TempDir
    Path headRoot;

    private final MavenLanguagePlugin plugin = new MavenLanguagePlugin();

    @Test
    void reportsAddedRemovedAndChangedDependenciesOfTheModule() throws IOException {
        write(baseRoot, "core/pom.xml", pom("core", dep("a", "x", null, "test") + dep("a", "gone", null, null)));
        write(headRoot, "core/pom.xml", pom("core", dep("a", "x", null, null) + dep("a", "fresh", null, null)));

        assertThat(detect()).containsExactlyInAnyOrder(
                "CHANGE_DEPENDENCY [core#a:x, scope test -> compile]",
                "REMOVE_DEPENDENCY [core#a:gone]",
                "ADD_DEPENDENCY [core#a:fresh]");
    }

    @Test
    void distinguishesManagedDependenciesAndTheirVersions() throws IOException {
        write(baseRoot, "pom.xml", managed("bom", dep("io.netty", "codec", "4.1", null)));
        write(headRoot, "pom.xml", managed("bom", dep("io.netty", "codec", "4.2", null)));

        assertThat(detect()).containsExactly("CHANGE_DEPENDENCY [bom#io.netty:codec [managed], version 4.1 -> 4.2]");
    }

    @Test
    void reportsExclusionsDroppedFromOrAddedToADependency() throws IOException {
        String exclusions = "<exclusions><exclusion><groupId>junit</groupId><artifactId>junit</artifactId></exclusion></exclusions>";
        write(baseRoot, "pom.xml", pom("app", dep("a", "x", null, null).replace("</dependency>", exclusions + "</dependency>")
                + dep("a", "y", null, null)));
        write(headRoot, "pom.xml", pom("app", dep("a", "x", null, null)
                + dep("a", "y", null, null).replace("</dependency>", exclusions + "</dependency>")));

        assertThat(detect()).containsExactlyInAnyOrder(
                "CHANGE_DEPENDENCY [app#a:x, exclusion junit:junit removed]",
                "CHANGE_DEPENDENCY [app#a:y, exclusion junit:junit added]");
    }

    @Test
    void readsAPomInTheEncodingItsXmlDeclarationNames() throws IOException {
        String latin1 = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><project><artifactId>legacy</artifactId>"
                + "<name>Caf\u00e9</name><dependencies>%s</dependencies></project>";
        Files.createDirectories(baseRoot.resolve("legacy"));
        Files.createDirectories(headRoot.resolve("legacy"));
        Files.write(baseRoot.resolve("legacy/pom.xml"), latin1.formatted("").getBytes(StandardCharsets.ISO_8859_1));
        Files.write(headRoot.resolve("legacy/pom.xml"),
                latin1.formatted(dep("g", "a", null, null)).getBytes(StandardCharsets.ISO_8859_1));

        assertThat(detect()).containsExactly("ADD_DEPENDENCY [legacy#g:a]");
    }

    @Test
    void ignoresAPomWhoseDependenciesDidNotChange() throws IOException {
        write(baseRoot, "pom.xml", pom("core", dep("a", "x", null, null)).replace("<project>", "<project><name>old</name>"));
        write(headRoot, "pom.xml", pom("core", dep("a", "x", null, null)).replace("<project>", "<project><name>new</name>"));

        assertThat(detect()).isEmpty();
    }

    @Test
    void readsNamespacedPomsAndIgnoresTheParentsArtifactId() throws IOException {
        String base = "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"><parent><artifactId>parent</artifactId></parent>"
                + "<artifactId>child</artifactId><dependencies></dependencies></project>";
        write(baseRoot, "child/pom.xml", base);
        write(headRoot, "child/pom.xml", base.replace("<dependencies></dependencies>", "<dependencies>" + dep("g", "a", null, null) + "</dependencies>"));

        assertThat(detect()).containsExactly("ADD_DEPENDENCY [child#g:a]");
    }

    @Test
    void recognizesAMalformedPomAsNotParsing() {
        assertThat(plugin.checkParses("<project><dependencies>").isSuccessful()).isFalse();
        assertThat(plugin.checkParses("<project></project>").isSuccessful()).isTrue();
    }

    private List<String> detect() {
        return plugin.detect(baseRoot, headRoot).stream().map(DetectedTransformation::toString)
                .map(s -> s.replace(" (x1)", "")).toList();
    }

    private static String pom(String artifactId, String dependencies) {
        return "<project><parent><artifactId>parent</artifactId></parent><artifactId>" + artifactId + "</artifactId>"
                + "<dependencies>" + dependencies + "</dependencies></project>";
    }

    private static String managed(String artifactId, String dependencies) {
        return "<project><artifactId>" + artifactId + "</artifactId><dependencyManagement><dependencies>" + dependencies
                + "</dependencies></dependencyManagement></project>";
    }

    private static String dep(String groupId, String artifactId, String version, String scope) {
        return "<dependency><groupId>" + groupId + "</groupId><artifactId>" + artifactId + "</artifactId>"
                + (version == null ? "" : "<version>" + version + "</version>")
                + (scope == null ? "" : "<scope>" + scope + "</scope>") + "</dependency>";
    }

    private static void write(Path root, String path, String content) throws IOException {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
