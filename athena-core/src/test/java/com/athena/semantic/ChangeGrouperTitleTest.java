package com.athena.semantic;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Titles of class moves between packages (ticket #335). */
class ChangeGrouperTitleTest {

    @Test
    void aSameNamedClassMovedBetweenPackagesNamesTheShortestDistinguishingPackages() {
        assertThat(titleOfMove("ServiceResource", "io.vertx.core.impl", "io.vertx.core.internal"))
                .isEqualTo("Move class impl.ServiceResource -> internal.ServiceResource");
    }

    @Test
    void packagesThatDifferEarlierKeepTheSharedTailForContext() {
        assertThat(titleOfMove("Codec", "com.acme.a.io", "com.acme.b.io"))
                .isEqualTo("Move class a.io.Codec -> b.io.Codec");
    }

    @Test
    void aMoveOutOfTheDefaultPackageShowsOnlyTheTargetPackage() {
        assertThat(titleOfMove("Codec", "", "com.acme")).isEqualTo("Move class Codec -> acme.Codec");
    }

    @Test
    void aMoveWithoutPackageContextKeepsThePlainTitle() {
        Change change = new ChangeGrouper().group(List.of(DetectedTransformation.of(TransformationKind.MOVE_CLASS,
                List.of("Codec", "Codec"), List.of("a/Codec.java", "b/Codec.java")))).get(0);

        assertThat(change.title()).isEqualTo("Move class Codec -> Codec");
    }

    @Test
    void aSingleSupertypeChangeIsNamedInTheSingular() {
        assertThat(titleOf(TransformationKind.CHANGE_SUPERTYPE, "SimpleRegistry", "LinkedHashMap -> ConcurrentHashMap"))
                .isEqualTo("Change supertype of SimpleRegistry: LinkedHashMap -> ConcurrentHashMap");
    }

    @Test
    void severalSupertypeChangesAreNamedInThePlural() {
        assertThat(titleOf(TransformationKind.CHANGE_SUPERTYPE, "Registry", "LinkedHashMap -> ConcurrentHashMap, -Serializable"))
                .isEqualTo("Change supertypes of Registry: LinkedHashMap -> ConcurrentHashMap, -Serializable");
    }

    // Ticket #384: a rename counts the other files whose references it updated.

    @Test
    void aRenameNamesTheFilesWhoseReferencesItUpdated() {
        DetectedTransformation rename = DetectedTransformation.of(TransformationKind.RENAME_SYMBOL,
                        List.of("Account#getBalance", "Account#currentFunds"), List.of("Account.java"))
                .withContext(DetectedTransformation.REFERENCE_FOLLOW_ONS, "2");

        assertThat(new ChangeGrouper().group(List.of(rename)).get(0).title())
                .isEqualTo("Rename Account#getBalance -> currentFunds (references updated in 2 files)");
    }

    @Test
    void aFieldRenameWithOneOtherFileUsesTheSingular() {
        DetectedTransformation rename = DetectedTransformation.of(TransformationKind.RENAME_FIELD,
                        List.of("Account#balance", "Account#funds"), List.of("Account.java"))
                .withContext(DetectedTransformation.REFERENCE_FOLLOW_ONS, "1");

        assertThat(new ChangeGrouper().group(List.of(rename)).get(0).title())
                .isEqualTo("Rename field Account#balance -> funds (references updated in 1 file)");
    }

    // Ticket #385: a class rename whose other files changed references, not just imports.

    @Test
    void aClassRenameNamesTheFilesWhoseReferencesItUpdated() {
        DetectedTransformation rename = DetectedTransformation.of(TransformationKind.RENAME_CLASS,
                        List.of("Account", "Wallet"), List.of("Account.java", "Wallet.java"))
                .withContext(DetectedTransformation.REFERENCE_FOLLOW_ONS, "2");

        assertThat(new ChangeGrouper().group(List.of(rename)).get(0).title())
                .isEqualTo("Rename class Account -> Wallet (references updated in 2 files)");
    }

    // Ticket #387: members moved within a class.

    @Test
    void aReorderNamesTheClassAndTheMove() {
        assertThat(titleOf(TransformationKind.REORDER_MEMBERS, "Calc", "mul moved before add"))
                .isEqualTo("Reorder members of Calc: mul moved before add");
    }

    // Ticket #386: a local variable or parameter rename names its kind and methods.

    @Test
    void aLocalVariableRenameNamesItsMethod() {
        assertThat(scopedRenameTitle("local variable", "Account#deposit"))
                .isEqualTo("Rename local variable updated -> newFunds in Account#deposit");
    }

    @Test
    void aParameterRenameInManyMethodsListsThreeThenCounts() {
        assertThat(scopedRenameTitle("parameter", "A#a, A#b, A#c, A#d, A#e"))
                .isEqualTo("Rename parameter updated -> newFunds in A#a, A#b, A#c …and 2 more");
    }

    private static String scopedRenameTitle(String kind, String scope) {
        DetectedTransformation rename = DetectedTransformation.of(TransformationKind.MECHANICAL_REPLACEMENT,
                        List.of("updated -> newFunds"), List.of("Account.java"))
                .withContext(DetectedTransformation.RENAME_SCOPE_KIND, kind)
                .withContext(DetectedTransformation.RENAME_SCOPE, scope);
        return new ChangeGrouper().group(List.of(rename)).get(0).title();
    }

    private static String titleOf(TransformationKind kind, String... involved) {
        DetectedTransformation change = DetectedTransformation.of(kind, List.of(involved), List.of("Registry.java"));
        return new ChangeGrouper().group(List.of(change)).get(0).title();
    }

    private static String titleOfMove(String className, String fromPackage, String toPackage) {
        DetectedTransformation move = DetectedTransformation.of(TransformationKind.MOVE_CLASS,
                        List.of(className, className), List.of("a/" + className + ".java", "b/" + className + ".java"))
                .withContext(DetectedTransformation.FROM_PACKAGE, fromPackage)
                .withContext(DetectedTransformation.TO_PACKAGE, toPackage);
        return new ChangeGrouper().group(List.of(move)).get(0).title();
    }
}
