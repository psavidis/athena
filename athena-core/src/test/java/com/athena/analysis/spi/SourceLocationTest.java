package com.athena.analysis.spi;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceLocationTest {

    @Test
    void aWholeFileLocationHasNoLineRange() {
        SourceLocation location = SourceLocation.ofFile("Order.java");

        assertThat(location.filePath()).isEqualTo("Order.java");
        assertThat(location.startLine()).isEmpty();
        assertThat(location.endLine()).isEmpty();
    }

    @Test
    void aSingleLineLocationHasEqualStartAndEndLine() {
        SourceLocation location = SourceLocation.ofLine("Order.java", 42);

        assertThat(location.startLine()).contains(42);
        assertThat(location.endLine()).contains(42);
    }

    @Test
    void aRangeLocationCarriesBothEndpoints() {
        SourceLocation location = SourceLocation.ofRange("Order.java", 10, 15);

        assertThat(location.startLine()).contains(10);
        assertThat(location.endLine()).contains(15);
    }

    @Test
    void rejectsAnEndLineBeforeTheStartLine() {
        assertThatThrownBy(() -> SourceLocation.ofRange("Order.java", 15, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsANonPositiveLineNumber() {
        assertThatThrownBy(() -> SourceLocation.ofLine("Order.java", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsABlankFilePath() {
        assertThatThrownBy(() -> SourceLocation.ofFile("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void twoLocationsWithTheSameFileAndRangeAreEqual() {
        assertThat(SourceLocation.ofRange("Order.java", 10, 15))
                .isEqualTo(SourceLocation.ofRange("Order.java", 10, 15));
    }
}
