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

    private static String titleOfMove(String className, String fromPackage, String toPackage) {
        DetectedTransformation move = DetectedTransformation.of(TransformationKind.MOVE_CLASS,
                        List.of(className, className), List.of("a/" + className + ".java", "b/" + className + ".java"))
                .withContext(DetectedTransformation.FROM_PACKAGE, fromPackage)
                .withContext(DetectedTransformation.TO_PACKAGE, toPackage);
        return new ChangeGrouper().group(List.of(move)).get(0).title();
    }
}
