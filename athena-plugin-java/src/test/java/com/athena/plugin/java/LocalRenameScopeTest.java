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
 * A mechanical replacement of a local variable's or parameter's name records that kind and the
 * methods it's declared in (ticket #386), so the rename can say where it happened.
 */
class LocalRenameScopeTest {

    @TempDir
    Path baseRoot;

    @TempDir
    Path headRoot;

    @Test
    void aLocalVariableRenameRecordsItsMethod() throws IOException {
        write(baseRoot, "long deposit(long a) {\n        long updated = a + 1;\n        return updated;\n    }");
        write(headRoot, "long deposit(long a) {\n        long newFunds = a + 1;\n        return newFunds;\n    }");

        DetectedTransformation rename = replacement();

        assertThat(rename.context())
                .containsEntry(DetectedTransformation.RENAME_SCOPE_KIND, "local variable")
                .containsEntry(DetectedTransformation.RENAME_SCOPE, "Account#deposit");
    }

    @Test
    void aParameterRenameRecordsEveryMethodItIsAParameterOf() throws IOException {
        write(baseRoot, "long deposit(long amount) {\n        return amount;\n    }\n"
                + "    boolean canWithdraw(long amount) {\n        return amount > 0;\n    }");
        write(headRoot, "long deposit(long value) {\n        return value;\n    }\n"
                + "    boolean canWithdraw(long value) {\n        return value > 0;\n    }");

        assertThat(replacement().context())
                .containsEntry(DetectedTransformation.RENAME_SCOPE_KIND, "parameter")
                .containsEntry(DetectedTransformation.RENAME_SCOPE, "Account#deposit, Account#canWithdraw");
    }

    @Test
    void aLambdaParameterCountsAsALocalVariable() throws IOException {
        write(baseRoot, "java.util.function.LongUnaryOperator twice() {\n        return x -> x * 2;\n    }");
        write(headRoot, "java.util.function.LongUnaryOperator twice() {\n        return n -> n * 2;\n    }");

        assertThat(replacement().context()).containsEntry(DetectedTransformation.RENAME_SCOPE_KIND, "local variable");
    }

    @Test
    void aNameThatIsAParameterInOneMethodAndALocalInAnotherHasNoKind() throws IOException {
        write(baseRoot, "long a(long amount) {\n        return amount;\n    }\n"
                + "    long b() {\n        long amount = 1;\n        return amount;\n    }");
        write(headRoot, "long a(long value) {\n        return value;\n    }\n"
                + "    long b() {\n        long value = 1;\n        return value;\n    }");

        assertThat(replacement().context()).doesNotContainKey(DetectedTransformation.RENAME_SCOPE_KIND);
    }

    @Test
    void aNameUsedOutsideTheDeclaringMethodsHasNoKind() throws IOException {
        write(baseRoot, "long amount;\n    long deposit(long amount) {\n        return amount;\n    }");
        write(headRoot, "long value;\n    long deposit(long value) {\n        return value;\n    }");

        assertThat(detect()).noneMatch(t -> t.context().containsKey(DetectedTransformation.RENAME_SCOPE_KIND));
    }

    private DetectedTransformation replacement() {
        List<DetectedTransformation> replacements = detect().stream()
                .filter(t -> t.kind() == TransformationKind.MECHANICAL_REPLACEMENT)
                .toList();
        assertThat(replacements).hasSize(1);
        return replacements.get(0);
    }

    private List<DetectedTransformation> detect() {
        return new TransformationDetector().detect(baseRoot, headRoot);
    }

    private static void write(Path root, String members) throws IOException {
        Files.writeString(root.resolve("Account.java"), "public class Account {\n    " + members + "\n}\n");
    }
}
