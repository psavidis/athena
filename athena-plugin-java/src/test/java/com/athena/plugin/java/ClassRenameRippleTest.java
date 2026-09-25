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
 * A class rename's ripple into other classes (ticket #385): a signature or body that differs only
 * by the renamed class's name is a reference update counted in the rename, not a Change of its own.
 */
class ClassRenameRippleTest {

    @TempDir
    Path baseRoot;

    @TempDir
    Path headRoot;

    @Test
    void aCallerWhoseSignatureOnlyNamesTheRenamedClassIsAReferenceUpdate() throws IOException {
        renameAccountToWallet();
        write(baseRoot, "Bank.java", "public class Bank {\n  long total(Account a) {\n    return a.get();\n  }\n}\n");
        write(headRoot, "Bank.java", "public class Bank {\n  long total(Wallet a) {\n    return a.get();\n  }\n}\n");

        assertThat(detect()).singleElement().satisfies(rename -> {
            assertThat(rename.kind()).isEqualTo(TransformationKind.RENAME_CLASS);
            assertThat(rename.context()).containsEntry(DetectedTransformation.REFERENCE_FOLLOW_ONS, "1")
                    .doesNotContainKey(DetectedTransformation.IMPORT_FOLLOW_ONS);
        });
    }

    @Test
    void aCallerThatAlsoChangesItsSignatureOtherwiseIsStillReported() throws IOException {
        renameAccountToWallet();
        write(baseRoot, "Bank.java", "public class Bank {\n  long total(Account a) {\n    return a.get();\n  }\n}\n");
        write(headRoot, "Bank.java", "public class Bank {\n  long total(Wallet a, long fee) {\n    return a.get() - fee;\n  }\n}\n");

        assertThat(detect()).extracting(DetectedTransformation::kind)
                .contains(TransformationKind.RENAME_CLASS, TransformationKind.CHANGE_METHOD_SIGNATURE);
    }

    @Test
    void aFileThatOnlyChangedItsImportsStillSaysImports() throws IOException {
        write(baseRoot, "a/Account.java", "package a;\npublic class Account {\n  public long get() {\n    return 1;\n  }\n}\n");
        write(headRoot, "b/Account.java", "package b;\npublic class Account {\n  public long get() {\n    return 1;\n  }\n}\n");
        write(baseRoot, "c/Bank.java", "package c;\nimport a.Account;\npublic class Bank {\n  long total(Account a) {\n    return a.get();\n  }\n}\n");
        write(headRoot, "c/Bank.java", "package c;\nimport b.Account;\npublic class Bank {\n  long total(Account a) {\n    return a.get();\n  }\n}\n");

        assertThat(detect()).singleElement()
                .satisfies(move -> assertThat(move.context()).containsEntry(DetectedTransformation.IMPORT_FOLLOW_ONS, "1"));
    }

    private void renameAccountToWallet() throws IOException {
        write(baseRoot, "Account.java", "public class Account {\n  public long get() {\n    return 1;\n  }\n}\n");
        write(headRoot, "Wallet.java", "public class Wallet {\n  public long get() {\n    return 1;\n  }\n}\n");
    }

    private List<DetectedTransformation> detect() {
        return new TransformationDetector().detect(baseRoot, headRoot);
    }

    private static void write(Path root, String path, String contents) throws IOException {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, contents);
    }
}
