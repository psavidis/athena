package com.athena.semantic;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeTestCodeTest {

    @Test
    void aChangeTouchingOnlyTestSourcesIsTestCode() {
        assertThat(changeTouching("core/src/test/java/GreeterTest.java").isTestCode()).isTrue();
    }

    @Test
    void aChangeTouchingAProductionSourceIsNotTestCode() {
        assertThat(changeTouching("core/src/main/java/Greeter.java").isTestCode()).isFalse();
    }

    @Test
    void aChangeTouchingBothTestAndProductionSourcesIsNotTestCode() {
        assertThat(changeTouching("core/src/test/java/GreeterTest.java", "core/src/main/java/Greeter.java").isTestCode())
                .isFalse();
    }

    @Test
    void aChangeTouchingNoFilesIsNotTestCode() {
        assertThat(changeTouching().isTestCode()).isFalse();
    }

    private Change changeTouching(String... files) {
        DetectedTransformation t = DetectedTransformation.of(TransformationKind.ADD_SYMBOL, List.of("Greeter#m"), List.of(files));
        return new ChangeGrouper().group(List.of(t)).get(0);
    }
}
