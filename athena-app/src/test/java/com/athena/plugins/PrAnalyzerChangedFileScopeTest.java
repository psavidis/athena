package com.athena.plugins;

import com.athena.semantic.AnalysisResult;
import com.athena.semantic.AnalysisStatus;
import com.athena.semantic.PrAnalyzer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The analysis status and symbol-aware fallback entries describe the change under
 * review, not the rest of the repository (ticket #262): only files whose content differs
 * between the two revisions are parse-checked.
 */
class PrAnalyzerChangedFileScopeTest {

    private static final String VALID_CLASS = "public class Greeter {\n    public String greet() { return \"hi\"; }\n}\n";
    private static final String VALID_CLASS_CHANGED =
            "public class Greeter {\n    public String greet() { return \"hi\"; }\n    public String bye() { return \"bye\"; }\n}\n";
    private static final String UNPARSEABLE = "public class Broken {\n    public void m( { not java\n";

    private final PrAnalyzer analyzer = new PrAnalyzer(PluginRegistry.languagePlugins(), PluginRegistry.frameworkPlugins());

    @Test
    void anUnparseableUnchangedFileLeavesTheStatusReady(@TempDir Path base, @TempDir Path head) throws IOException {
        write(base, "Greeter.java", VALID_CLASS);
        write(head, "Greeter.java", VALID_CLASS_CHANGED);
        write(base, "Broken.java", UNPARSEABLE);
        write(head, "Broken.java", UNPARSEABLE);

        AnalysisResult result = analyzer.analyze(base, head);

        assertThat(result.status()).isEqualTo(AnalysisStatus.READY);
        assertThat(result.symbolAwareDiffEntries()).isEmpty();
    }

    @Test
    void anUnparseableChangedFileStillDegradesTheStatus(@TempDir Path base, @TempDir Path head) throws IOException {
        write(base, "Greeter.java", VALID_CLASS);
        write(head, "Greeter.java", VALID_CLASS_CHANGED);
        write(base, "Broken.java", VALID_CLASS.replace("Greeter", "Broken"));
        write(head, "Broken.java", UNPARSEABLE);

        AnalysisResult result = analyzer.analyze(base, head);

        assertThat(result.status()).isEqualTo(AnalysisStatus.PARTIALLY_ANALYZED);
        assertThat(result.symbolAwareDiffEntries()).extracting(entry -> entry.filePath()).containsExactly("Broken.java");
    }

    @Test
    void identicalRevisionsAreReadyWithNothingDegraded(@TempDir Path base, @TempDir Path head) throws IOException {
        write(base, "Broken.java", UNPARSEABLE);
        write(head, "Broken.java", UNPARSEABLE);

        AnalysisResult result = analyzer.analyze(base, head);

        assertThat(result.status()).isEqualTo(AnalysisStatus.READY);
        assertThat(result.symbolAwareDiffEntries()).isEmpty();
        assertThat(result.changes()).isEmpty();
    }

    @Test
    void anAddedFileThatFailsToParseDegradesTheStatus(@TempDir Path base, @TempDir Path head) throws IOException {
        write(base, "Greeter.java", VALID_CLASS);
        write(head, "Greeter.java", VALID_CLASS_CHANGED);
        write(head, "Broken.java", UNPARSEABLE);

        AnalysisResult result = analyzer.analyze(base, head);

        assertThat(result.status()).isEqualTo(AnalysisStatus.PARTIALLY_ANALYZED);
        assertThat(result.symbolAwareDiffEntries()).extracting(entry -> entry.filePath()).containsExactly("Broken.java");
    }

    private void write(Path root, String file, String content) throws IOException {
        Files.writeString(root.resolve(file), content);
    }
}
