package com.athena.plugin.java;

import com.athena.semantic.DetectedTransformation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Fields and methods added to or removed from existing anonymous classes (ticket #320). */
class AnonymousClassMemberDetectionTest {

    @TempDir
    Path baseRoot;

    @TempDir
    Path headRoot;

    @Test
    void aSecondAnonymousClassOfTheSameTypeIsNumbered() throws IOException {
        write(baseRoot, "Tasks", tasks("", ""));
        write(headRoot, "Tasks", tasks("", "        int attempts;\n"));

        assertThat(detect()).extracting(t -> t.kind() + " " + t.involvedDescriptions())
                .containsExactly("ADD_FIELD [Tasks#create(anonymous Runnable #2)#attempts]");
    }

    @Test
    void anAnonymousClassInAStaticInitializerIsNamedAfterIt() throws IOException {
        write(baseRoot, "Registry", "public class Registry {\n    static {\n        register(new Handler() {\n        });\n    }\n}\n");
        write(headRoot, "Registry", "public class Registry {\n    static {\n        register(new Handler() {\n"
                + "            void close() {\n            }\n        });\n    }\n}\n");

        assertThat(detect()).extracting(t -> t.kind() + " " + t.involvedDescriptions())
                .contains("ADD_SYMBOL [Registry#<clinit>(anonymous Handler)#close]");
    }

    @Test
    void anUnchangedAnonymousClassReportsNothing() throws IOException {
        write(baseRoot, "Tasks", tasks("        int attempts;\n", ""));
        write(headRoot, "Tasks", tasks("        int attempts;\n", ""));

        assertThat(detect()).isEmpty();
    }

    private static String tasks(String firstMembers, String secondMembers) {
        return "public class Tasks {\n    java.util.List<Runnable> create() {\n"
                + "        Runnable a = new Runnable() {\n" + firstMembers + "            public void run() {\n            }\n        };\n"
                + "        Runnable b = new Runnable() {\n" + secondMembers + "            public void run() {\n            }\n        };\n"
                + "        return java.util.List.of(a, b);\n    }\n}\n";
    }

    private List<DetectedTransformation> detect() {
        return new TransformationDetector().detect(baseRoot, headRoot).stream()
                .filter(t -> t.involvedDescriptions().get(0).contains("(anonymous"))
                .toList();
    }

    private static void write(Path root, String className, String contents) throws IOException {
        Files.writeString(root.resolve(className + ".java"), contents);
    }
}
