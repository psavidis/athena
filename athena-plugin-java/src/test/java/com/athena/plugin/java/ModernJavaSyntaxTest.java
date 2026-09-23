package com.athena.plugin.java;

import com.athena.semantic.DetectedTransformation;
import com.athena.semantic.TransformationKind;
import com.github.javaparser.JavaParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Java syntax newer than 17 — pattern-matching switches, record patterns, unnamed
 * variables — must parse everywhere Athena parses Java (ticket #262): real projects
 * use it, and a file that silently fails to parse drops out of the review.
 */
class ModernJavaSyntaxTest {

    private static final String SWITCH_WITH_TYPE_PATTERNS = """
            public class Describer {
                public String describe(Object value) {
                    return switch (value) {
                        case Integer number -> "number " + number;
                        default -> "unknown";
                    };
                }
            }
            """;

    private static final String RECORD_PATTERN = """
            public class Geometry {
                record Point(int x, int y) { }
                public int sum(Object value) {
                    if (value instanceof Point(int x, int y)) {
                        return x + y;
                    }
                    return 0;
                }
            }
            """;

    private static final String UNNAMED_PATTERN_VARIABLE = """
            public class Describer {
                public String describe(Object value) {
                    return switch (value) {
                        case String _ -> "text";
                        default -> "unknown";
                    };
                }
            }
            """;

    @Test
    void sharedConfigurationParsesSwitchWithTypePatterns() {
        assertThat(new JavaParser(JavaParserConfigurations.currentJava()).parse(SWITCH_WITH_TYPE_PATTERNS).isSuccessful())
                .isTrue();
    }

    @Test
    void sharedConfigurationParsesRecordPatterns() {
        assertThat(new JavaParser(JavaParserConfigurations.currentJava()).parse(RECORD_PATTERN).isSuccessful())
                .isTrue();
    }

    @Test
    void sharedConfigurationParsesUnnamedPatternVariables() {
        assertThat(new JavaParser(JavaParserConfigurations.currentJava()).parse(UNNAMED_PATTERN_VARIABLE).isSuccessful())
                .isTrue();
    }

    @Test
    void sourceParserAcceptsUnnamedPatternVariables() {
        assertThat(new JavaSourceParser().parse(UNNAMED_PATTERN_VARIABLE).isSuccessful()).isTrue();
    }

    @Test
    void detectorReportsChangesInAFileUsingModernSyntax(@TempDir Path baseRoot, @TempDir Path headRoot) throws IOException {
        Files.writeString(baseRoot.resolve("Describer.java"), UNNAMED_PATTERN_VARIABLE);
        Files.writeString(headRoot.resolve("Describer.java"), UNNAMED_PATTERN_VARIABLE.replace(
                "    public String describe", "    public String label() {\n        return \"d\";\n    }\n    public String describe"));

        List<DetectedTransformation> transformations = new TransformationDetector().detect(baseRoot, headRoot);

        assertThat(transformations).anyMatch(t -> t.kind() == TransformationKind.ADD_SYMBOL
                && t.involvedDescriptions().contains("Describer#label"));
    }
}
