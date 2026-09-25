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
 * Members pulled up into a newly added base class from two or more of its (transitive)
 * subclasses are one pull-up each, replacing the move/remove/add rows they'd otherwise read
 * as (ticket #290).
 */
class PullUpDetectionTest {

    @TempDir
    Path baseRoot;

    @TempDir
    Path headRoot;

    @Test
    void aFieldLostByTwoSubclassesToANewBaseIsOnePullUpListingTheSourcesAlphabetically() throws IOException {
        write(baseRoot, "Zeta", "public class Zeta {\n    protected Builder builder;\n}\n");
        write(baseRoot, "Alpha", "public class Alpha {\n    protected Builder builder;\n}\n");
        write(headRoot, "Base", "public abstract class Base {\n    protected Builder builder;\n}\n");
        write(headRoot, "Zeta", "public class Zeta extends Base {\n}\n");
        write(headRoot, "Alpha", "public class Alpha extends Base {\n}\n");

        List<DetectedTransformation> detected = detect();

        assertThat(detected).filteredOn(t -> t.kind() == TransformationKind.PULL_UP_FIELD).singleElement()
                .satisfies(pullUp -> {
                    assertThat(pullUp.involvedDescriptions()).containsExactly("Base#builder", "Alpha#builder", "Zeta#builder");
                    assertThat(pullUp.filesTouched()).containsExactlyInAnyOrder("Base.java", "Alpha.java", "Zeta.java");
                    assertThat(pullUp.diffText()).contains("protected Builder builder;");
                });
        assertThat(detected).extracting(DetectedTransformation::kind)
                .doesNotContain(TransformationKind.MOVE_FIELD, TransformationKind.REMOVE_FIELD, TransformationKind.ADD_FIELD);
    }

    @Test
    void aMethodLostByTwoSubclassesIsAPullUpOfTheMethod() throws IOException {
        String getter = "    public Builder getBuilder() {\n        return null;\n    }\n";
        write(baseRoot, "Alpha", "public class Alpha {\n" + getter + "}\n");
        write(baseRoot, "Beta", "public class Beta {\n" + getter + "}\n");
        write(headRoot, "Base", "public abstract class Base {\n" + getter + "}\n");
        write(headRoot, "Alpha", "public class Alpha extends Base {\n}\n");
        write(headRoot, "Beta", "public class Beta extends Base {\n}\n");

        // Both subclasses now extend Base: that is a supertype change of its own (ticket #358).
        assertThat(detect()).extracting(t -> t.kind() + " " + t.involvedDescriptions())
                .containsExactly("ADD_CLASS [Base]", "CHANGE_SUPERTYPE [Alpha, +Base]", "CHANGE_SUPERTYPE [Beta, +Base]",
                        "PULL_UP_SYMBOL [Base#getBuilder, Alpha#getBuilder, Beta#getBuilder]");
    }

    @Test
    void aMemberOfADifferentTypeIsNotPulledUp() throws IOException {
        write(baseRoot, "Alpha", "public class Alpha {\n    protected Builder builder;\n}\n");
        write(baseRoot, "Beta", "public class Beta {\n    protected Builder builder;\n}\n");
        write(headRoot, "Base", "public abstract class Base {\n    protected OtherBuilder builder;\n}\n");
        write(headRoot, "Alpha", "public class Alpha extends Base {\n}\n");
        write(headRoot, "Beta", "public class Beta extends Base {\n}\n");

        assertThat(detect()).extracting(DetectedTransformation::kind).doesNotContain(TransformationKind.PULL_UP_FIELD);
    }

    @Test
    void aMemberOfAnExistingBaseClassIsNotAPullUp() throws IOException {
        write(baseRoot, "Base", "public abstract class Base {\n}\n");
        write(baseRoot, "Alpha", "public class Alpha extends Base {\n    protected Builder builder;\n}\n");
        write(baseRoot, "Beta", "public class Beta extends Base {\n    protected Builder builder;\n}\n");
        write(headRoot, "Base", "public abstract class Base {\n    protected Builder builder;\n}\n");
        write(headRoot, "Alpha", "public class Alpha extends Base {\n}\n");
        write(headRoot, "Beta", "public class Beta extends Base {\n}\n");

        assertThat(detect()).extracting(DetectedTransformation::kind).doesNotContain(TransformationKind.PULL_UP_FIELD);
    }

    private List<DetectedTransformation> detect() {
        return new TransformationDetector().detect(baseRoot, headRoot);
    }

    private static void write(Path root, String className, String contents) throws IOException {
        Files.writeString(root.resolve(className + ".java"), contents);
    }
}
