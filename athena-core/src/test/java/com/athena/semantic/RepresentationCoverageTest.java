package com.athena.semantic;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which changed files no Change represents, and why (ticket #260) — so a partial analysis
 * never looks complete.
 */
class RepresentationCoverageTest {

    private static final ChangedFile GREETER = changed("Greeter.java");
    private static final ChangedFile POM = changed("pom.xml");
    private static final ChangedFile BROKEN = changed("Broken.java");
    private static final ChangedFile FRACTION = changed("Fraction.java");

    @Test
    void aFileCitedByAChangeIsRepresented() {
        RepresentationCoverage coverage = RepresentationCoverage.of(List.of(GREETER), List.of(changeIn("Greeter.java")),
                List.of(), path -> path.endsWith(".java"));

        assertThat(coverage.representedFileCount()).isEqualTo(1);
        assertThat(coverage.unrepresentedFiles()).isEmpty();
    }

    @Test
    void eachUnrepresentedFileGetsItsReason() {
        RepresentationCoverage coverage = RepresentationCoverage.of(List.of(GREETER, POM, BROKEN, FRACTION),
                List.of(changeIn("Greeter.java")),
                List.of(new SymbolAwareDiffEntry("Broken.java", "head: parse error")),
                path -> path.endsWith(".java"));

        assertThat(coverage.changedFiles()).hasSize(4);
        assertThat(coverage.representedFileCount()).isEqualTo(1);
        assertThat(coverage.unrepresentedFiles())
                .extracting(unrepresented -> unrepresented.file().path(), UnrepresentedFile::reason)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("pom.xml", UnrepresentedReason.UNSUPPORTED_FILE_TYPE),
                        org.assertj.core.groups.Tuple.tuple("Broken.java", UnrepresentedReason.PARSE_FAILED),
                        org.assertj.core.groups.Tuple.tuple("Fraction.java", UnrepresentedReason.NO_SEMANTIC_CHANGE));
    }

    @Test
    void reasonsHaveReviewerFacingLabels() {
        assertThat(UnrepresentedReason.UNSUPPORTED_FILE_TYPE.label()).isEqualTo("unsupported file type");
        assertThat(UnrepresentedReason.PARSE_FAILED.label()).isEqualTo("could not be parsed");
        assertThat(UnrepresentedReason.NO_SEMANTIC_CHANGE.label()).isEqualTo("no semantic change detected");
    }

    @Test
    void aChangedFileCanBeLookedUpByPathForItsRawDiff() {
        RepresentationCoverage coverage = RepresentationCoverage.of(List.of(FRACTION), List.of(), List.of(), path -> true);

        assertThat(coverage.changedFile("Fraction.java")).contains(FRACTION);
        assertThat(coverage.changedFile("Unchanged.java")).isEmpty();
    }

    @Test
    void emptyCoverageHasNothingChanged() {
        assertThat(RepresentationCoverage.empty().changedFiles()).isEmpty();
        assertThat(RepresentationCoverage.empty().representedFileCount()).isZero();
    }

    private static ChangedFile changed(String path) {
        return new ChangedFile(path, FileChangeStatus.MODIFIED, 2, 1, "-a\n+b");
    }

    private static Change changeIn(String file) {
        DetectedTransformation transformation = DetectedTransformation.of(TransformationKind.ADD_SYMBOL,
                List.of(file.replace(".java", "") + "#m"), List.of(file));
        return new ChangeGrouper().group(List.of(transformation)).get(0);
    }
}
