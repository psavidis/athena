package com.athena.semantic;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestSourcesTest {

    @Test
    void recognizesTestSources() {
        assertThat(List.of(
                "core/src/test/java/com/acme/Greeter.java",
                "src/test/java/Greeter.java",
                "core/src/it/java/com/acme/Greeter.java",
                "core/src/testFixtures/java/com/acme/Greeter.java",
                "test/com/acme/Greeter.java",
                "tests/com/acme/Greeter.java",
                "testsuite/integration/Greeter.java",
                "core/src/main/java/com/acme/GreeterTest.java",
                "core/src/main/java/com/acme/GreeterTests.java",
                "core/src/main/java/com/acme/GreeterIT.java",
                "core/src/main/java/com/acme/GreeterTestCase.java"
        )).allSatisfy(path -> assertThat(TestSources.isTestSource(path)).as(path).isTrue());
    }

    @Test
    void doesNotRecognizeProductionSources() {
        assertThat(List.of(
                "core/src/main/java/com/acme/Greeter.java",
                "core/src/main/java/com/acme/TestDataLoader.java",
                "core/src/main/java/com/acme/testing/Greeter.java",
                "core/src/main/java/com/acme/test/Greeter.java",
                "src/main/java/com/acme/Contest.java",
                "pom.xml"
        )).allSatisfy(path -> assertThat(TestSources.isTestSource(path)).as(path).isFalse());
    }
}
