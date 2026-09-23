package com.athena.semantic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every file whose content differs between two checkouts — any language, not only the
 * ones a plugin understands — with its status and size (ticket #260).
 */
class ChangedFilesTest {

    @TempDir
    Path baseRoot;

    @TempDir
    Path headRoot;

    @Test
    void reportsAddedModifiedAndRemovedFilesButNotIdenticalOnes() throws IOException {
        write(baseRoot, "Same.java", "class Same { }\n");
        write(headRoot, "Same.java", "class Same { }\n");
        write(baseRoot, "pom.xml", "<version>1</version>\n");
        write(headRoot, "pom.xml", "<version>2</version>\n");
        write(baseRoot, "Removed.java", "class Removed { }\n");
        write(headRoot, "docs/Added.md", "# Added\n");

        assertThat(ChangedFiles.between(baseRoot, headRoot))
                .extracting(ChangedFile::path, ChangedFile::status)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Removed.java", FileChangeStatus.REMOVED),
                        org.assertj.core.groups.Tuple.tuple("docs/Added.md", FileChangeStatus.ADDED),
                        org.assertj.core.groups.Tuple.tuple("pom.xml", FileChangeStatus.MODIFIED));
    }

    @Test
    void countsChangedLinesAndHunks() throws IOException {
        write(baseRoot, "A.java", "line1\nline2\nline3\nline4\nline5\n");
        write(headRoot, "A.java", "line1\nchanged2\nline3\nline4\nchanged5\n");

        ChangedFile changed = ChangedFiles.between(baseRoot, headRoot).get(0);

        assertThat(changed.linesChanged()).isEqualTo(4);
        assertThat(changed.hunkCount()).isEqualTo(2);
        assertThat(changed.unifiedDiff().lines()).contains("-line2", "+changed2", " line3");
    }

    @Test
    void ignoresTheGitDirectoryOfACheckout() throws IOException {
        write(baseRoot, ".git/HEAD", "ref: a\n");
        write(headRoot, ".git/HEAD", "ref: b\n");

        assertThat(ChangedFiles.between(baseRoot, headRoot)).isEmpty();
    }

    @Test
    void aBinaryFileIsReportedWithoutALineDiff() throws IOException {
        Files.write(baseRoot.resolve("logo.png"), new byte[] {(byte) 0x89, 0x50, (byte) 0xC3, 0x28});
        Files.write(headRoot.resolve("logo.png"), new byte[] {(byte) 0x89, 0x50, (byte) 0xC3, 0x29});

        List<ChangedFile> changed = ChangedFiles.between(baseRoot, headRoot);

        assertThat(changed).singleElement().satisfies(file -> {
            assertThat(file.status()).isEqualTo(FileChangeStatus.MODIFIED);
            assertThat(file.linesChanged()).isZero();
            assertThat(file.unifiedDiff()).isEqualTo("Binary file changed");
        });
    }

    private static void write(Path root, String relativePath, String content) throws IOException {
        Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
