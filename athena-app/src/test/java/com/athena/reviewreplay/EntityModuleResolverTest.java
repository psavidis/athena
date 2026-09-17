package com.athena.reviewreplay;

import com.athena.plugins.PluginRegistry;
import com.athena.semantic.AnalysisResult;
import com.athena.semantic.Change;
import com.athena.semantic.PrAnalyzer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dedicated unit test for {@link EntityModuleResolver} (ticket #212). Uses
 * real {@link Change}s produced by {@link PrAnalyzer} against real git
 * checkouts — {@code Change} has no public constructor, and its {@code
 * enclosingType()}/touched-files derivation is exactly the behavior this
 * resolver depends on being consistent, so a hand-built fixture would not
 * actually exercise that guarantee.
 */
class EntityModuleResolverTest {

    private final PrAnalyzer analyzer =
            new PrAnalyzer(PluginRegistry.languagePlugins(), PluginRegistry.frameworkPlugins());

    private Path baseRoot;
    private Path headRoot;

    @BeforeEach
    void createTempRoots() throws IOException {
        baseRoot = Files.createTempDirectory("athena-entity-module-base-");
        headRoot = Files.createTempDirectory("athena-entity-module-head-");
    }

    @AfterEach
    void cleanUpTempRoots() throws IOException {
        deleteRecursively(baseRoot);
        deleteRecursively(headRoot);
    }

    @Test
    void resolvesAnEntityToTheModuleItsFirstTouchedFileLivesIn() {
        writeClass(headRoot, "orders/OrderService.java", "OrderService");

        List<Change> changes = analyzer.analyze(baseRoot, headRoot).changes();

        Optional<String> module = EntityModuleResolver.resolve("OrderService", changes);

        assertThat(module).contains("orders");
    }

    @Test
    void resolvesADifferentEntityToItsOwnModule() {
        writeClass(headRoot, "orders/OrderService.java", "OrderService");
        writeClass(headRoot, "payments/PaymentGateway.java", "PaymentGateway");

        List<Change> changes = analyzer.analyze(baseRoot, headRoot).changes();

        assertThat(EntityModuleResolver.resolve("PaymentGateway", changes)).contains("payments");
        assertThat(EntityModuleResolver.resolve("OrderService", changes)).contains("orders");
    }

    @Test
    void isEmptyWhenNoChangeConcernsTheEntity() {
        writeClass(headRoot, "orders/OrderService.java", "OrderService");

        List<Change> changes = analyzer.analyze(baseRoot, headRoot).changes();

        assertThat(EntityModuleResolver.resolve("PaymentGateway", changes)).isEmpty();
    }

    private void writeClass(Path root, String relativePath, String className) {
        Path file = root.resolve(relativePath);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, "public class " + className + " {\n}\n");
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var walk = Files.walk(root)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
        }
    }
}
