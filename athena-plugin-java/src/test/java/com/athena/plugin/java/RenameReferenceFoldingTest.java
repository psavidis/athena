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
 * A rename and the reference updates it caused are one transformation (ticket #384): the
 * mechanical replacement of the same identifier pair folds into the rename, which counts the
 * other files whose references it updated.
 */
class RenameReferenceFoldingTest {

    @TempDir
    Path baseRoot;

    @TempDir
    Path headRoot;

    @Test
    void aFieldRenameInOneFileAbsorbsItsReplacement() throws IOException {
        write(baseRoot, "Account", "public class Account {\n    private long balance;\n"
                + "    long get() {\n        return balance;\n    }\n}\n");
        write(headRoot, "Account", "public class Account {\n    private long funds;\n"
                + "    long get() {\n        return funds;\n    }\n}\n");

        List<DetectedTransformation> detected = detect();

        assertThat(detected).extracting(DetectedTransformation::kind).containsExactly(TransformationKind.RENAME_FIELD);
        assertThat(detected.get(0).context()).doesNotContainKey(DetectedTransformation.REFERENCE_FOLLOW_ONS);
    }

    @Test
    void aMethodRenameCountsTheOtherFilesWhoseReferencesItUpdated() throws IOException {
        write(baseRoot, "Account", "public class Account {\n    long getBalance() {\n        return 1;\n    }\n}\n");
        write(headRoot, "Account", "public class Account {\n    long currentFunds() {\n        return 1;\n    }\n}\n");
        for (String caller : List.of("Bank", "Audit")) {
            write(baseRoot, caller, caller(caller, "getBalance"));
            write(headRoot, caller, caller(caller, "currentFunds"));
        }

        List<DetectedTransformation> detected = detect();

        assertThat(detected).singleElement().satisfies(rename -> {
            assertThat(rename.kind()).isEqualTo(TransformationKind.RENAME_SYMBOL);
            assertThat(rename.context()).containsEntry(DetectedTransformation.REFERENCE_FOLLOW_ONS, "2");
            assertThat(rename.filesTouched()).containsExactlyInAnyOrder("Account.java", "Bank.java", "Audit.java");
        });
    }

    @Test
    void aReplacementWithoutAMatchingRenameIsKept() throws IOException {
        for (String caller : List.of("Bank", "Audit")) {
            write(baseRoot, caller, caller(caller, "getBalance"));
            write(headRoot, caller, caller(caller, "currentFunds"));
        }

        assertThat(detect()).extracting(DetectedTransformation::kind)
                .containsExactly(TransformationKind.MECHANICAL_REPLACEMENT);
    }

    @Test
    void aClassRenameAbsorbsTheReplacementOfItsName() throws IOException {
        write(baseRoot, "Account", "public class Account {\n    long get() {\n        return 1;\n    }\n}\n");
        write(headRoot, "Wallet", "public class Wallet {\n    long get() {\n        return 1;\n    }\n}\n");
        write(baseRoot, "Bank", "public class Bank {\n    long get() {\n        return new Account().get();\n    }\n}\n");
        write(headRoot, "Bank", "public class Bank {\n    long get() {\n        return new Wallet().get();\n    }\n}\n");

        assertThat(detect()).noneMatch(t -> t.kind() == TransformationKind.MECHANICAL_REPLACEMENT)
                .anyMatch(t -> t.kind() == TransformationKind.RENAME_CLASS);
    }

    private List<DetectedTransformation> detect() {
        return new TransformationDetector().detect(baseRoot, headRoot);
    }

    private static String caller(String className, String method) {
        return "public class " + className + " {\n    long total(Account a) {\n        return a." + method + "();\n    }\n}\n";
    }

    private static void write(Path root, String className, String content) throws IOException {
        Files.writeString(root.resolve(className + ".java"), content);
    }
}
