package com.athena.plugin.java;

import com.athena.semantic.DetectedTransformation;
import com.athena.semantic.TransformationKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A type whose superclass or implemented/extended interfaces change is one supertype change,
 * named by simple type names without type arguments (ticket #358).
 */
class SupertypeChangeDetectionTest {

    @TempDir
    Path baseRoot;

    @TempDir
    Path headRoot;

    @Test
    void aReplacedSuperclassNamesBothTypes() throws IOException {
        write(baseRoot, "Registry", "public class Registry extends java.util.LinkedHashMap<String, Object> {}");
        write(headRoot, "Registry", "public class Registry extends java.util.concurrent.ConcurrentHashMap<String, Object> {}");

        assertThat(supertypeChanges()).containsExactly("Registry, LinkedHashMap -> ConcurrentHashMap");
    }

    @Test
    void anAddedInterfaceIsAPlus() throws IOException {
        write(baseRoot, "Registry", "public class Registry {}");
        write(headRoot, "Registry", "public class Registry implements java.io.Closeable {}");

        assertThat(supertypeChanges()).containsExactly("Registry, +Closeable");
    }

    @Test
    void aRemovedSuperclassIsAMinus() throws IOException {
        write(baseRoot, "Registry", "public class Registry extends Base {}");
        write(headRoot, "Registry", "public class Registry {}");

        assertThat(supertypeChanges()).containsExactly("Registry, -Base");
    }

    @Test
    void aReplacedSuperclassAndARemovedInterfaceAreOneChange() throws IOException {
        write(baseRoot, "Registry", "public class Registry extends LinkedHashMap<String, Object> implements Serializable {}");
        write(headRoot, "Registry", "public class Registry extends ConcurrentHashMap<String, Object> {}");

        assertThat(supertypeChanges()).containsExactly("Registry, LinkedHashMap -> ConcurrentHashMap, -Serializable");
    }

    @Test
    void anInterfacesExtendedInterfacesAreItsSupertypes() throws IOException {
        write(baseRoot, "Repo", "public interface Repo extends Reader {}");
        write(headRoot, "Repo", "public interface Repo extends Reader, Writer {}");

        assertThat(supertypeChanges()).containsExactly("Repo, +Writer");
    }

    @Test
    void aNestedTypeIsNamedWithItsOuterType() throws IOException {
        write(baseRoot, "Outer", "public class Outer { static class Inner {} }");
        write(headRoot, "Outer", "public class Outer { static class Inner implements Runnable { public void run() {} } }");

        assertThat(supertypeChanges()).containsExactly("Outer.Inner, +Runnable");
    }

    @Test
    void changedTypeArgumentsAloneAreNotASupertypeChange() throws IOException {
        write(baseRoot, "Names", "public class Names extends java.util.ArrayList<String> {}");
        write(headRoot, "Names", "public class Names extends java.util.ArrayList<CharSequence> {}");

        assertThat(supertypeChanges()).isEmpty();
    }

    @Test
    void reorderedInterfacesAreNotASupertypeChange() throws IOException {
        write(baseRoot, "Pool", "public class Pool implements Runnable, AutoCloseable { public void run() {} public void close() {} }");
        write(headRoot, "Pool", "public class Pool implements AutoCloseable, Runnable { public void run() {} public void close() {} }");

        assertThat(supertypeChanges()).isEmpty();
    }

    private List<String> supertypeChanges() {
        return detect().stream().filter(t -> t.kind() == TransformationKind.CHANGE_SUPERTYPE)
                .map(t -> String.join(", ", t.involvedDescriptions())).toList();
    }

    private List<DetectedTransformation> detect() {
        return new TransformationDetector().detect(baseRoot, headRoot);
    }

    private static void write(Path root, String className, String source) throws IOException {
        Files.writeString(root.resolve(className + ".java"), source);
    }
}
