package com.athena.web.reviewbriefing;

import com.athena.plugins.PluginRegistry;
import com.athena.reviewbriefing.BriefingItem;
import com.athena.semantic.AnalysisResult;
import com.athena.semantic.Change;
import com.athena.semantic.PrAnalyzer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dedicated unit test for {@link BriefingItemResponse#of} (ticket #224):
 * a briefing item's entity reference is resolved to a Semantic Canvas
 * module via the real {@link com.athena.reviewreplay.EntityModuleResolver}
 * (ticket #212), against real {@link Change}s from {@link PrAnalyzer} —
 * same reasoning as {@code EntityModuleResolverTest}, {@code Change} has
 * no public constructor.
 */
class BriefingItemResponseTest {

    private final PrAnalyzer analyzer =
            new PrAnalyzer(PluginRegistry.languagePlugins(), PluginRegistry.frameworkPlugins());

    private Path baseRoot;
    private Path headRoot;

    @BeforeEach
    void createTempRoots() throws IOException {
        baseRoot = Files.createTempDirectory("athena-briefing-item-base-");
        headRoot = Files.createTempDirectory("athena-briefing-item-head-");
    }

    @AfterEach
    void cleanUpTempRoots() throws IOException {
        deleteRecursively(baseRoot);
        deleteRecursively(headRoot);
    }

    @Test
    void resolvesTheModuleForAnItemConcerningAKnownEntity() {
        writeClass(headRoot, "orders/OrderService.java", "OrderService");
        List<Change> changes = analyzer.analyze(baseRoot, headRoot).changes();

        BriefingItemResponse response = BriefingItemResponse.of(BriefingItem.of("Changed retry logic", "OrderService"), changes);

        assertThat(response.entityReference()).isEqualTo("OrderService");
        assertThat(response.module()).isEqualTo("orders");
    }

    @Test
    void hasNoModuleWhenNoChangeConcernsTheEntity() {
        writeClass(headRoot, "orders/OrderService.java", "OrderService");
        List<Change> changes = analyzer.analyze(baseRoot, headRoot).changes();

        BriefingItemResponse response = BriefingItemResponse.of(BriefingItem.of("Unrelated", "PaymentGateway"), changes);

        assertThat(response.module()).isNull();
    }

    @Test
    void hasNoModuleWhenTheItemHasNoEntityReferenceAtAll() {
        BriefingItemResponse response = BriefingItemResponse.of(BriefingItem.of("A repo-wide observation"), List.of());

        assertThat(response.entityReference()).isNull();
        assertThat(response.module()).isNull();
    }

    private void writeClass(Path root, String relativePath, String className) {
        Path file = root.resolve(relativePath);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, "public class " + className + " {\n}\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var walk = Files.walk(root)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }
}
