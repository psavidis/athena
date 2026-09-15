package com.athena.memory;

import com.athena.knowledge.spi.KnowledgeItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Dedicated unit tests for {@link ExternalKnowledgeLearner} (ticket #172) —
 * turning already-retrieved {@link KnowledgeItem}s into project-memory
 * facts, each retaining its source note and provider as provenance.
 */
class ExternalKnowledgeLearnerTest {

    private Path projectRoot;
    private ProjectMemoryStore store;

    @BeforeEach
    void createProject() throws IOException {
        projectRoot = Files.createTempDirectory("athena-external-knowledge-learner-test-");
        store = new ProjectMemoryStore(projectRoot);
    }

    @Test
    void recordsARetrievedKnowledgeItemAsAFactWithItsSourceAsProvenance() {
        KnowledgeItem item = knowledgeItem("obsidian", "Reporting Adapter Exception",
                "Reporting adapters intentionally bypass the repository boundary");

        ExternalKnowledgeLearner.learn(List.of(item), store);

        MemoryEntry entry = store.entries().stream()
                .filter(e -> e.fact().contains("Reporting adapters intentionally bypass the repository boundary"))
                .findFirst()
                .orElseThrow();
        assertThat(entry.evidence()).contains("Reporting Adapter Exception").contains("obsidian");
        assertThat(entry.developerConfirmed()).isFalse();
    }

    @Test
    void recordsMultipleItemsAsSeparateFacts() {
        KnowledgeItem first = knowledgeItem("obsidian", "Reporting Adapter Exception",
                "Reporting adapters intentionally bypass the repository boundary");
        KnowledgeItem second = knowledgeItem("obsidian", "Payment Retry Policy",
                "Payment retries are capped at three attempts");

        ExternalKnowledgeLearner.learn(List.of(first, second), store);

        assertThat(store.entries()).hasSize(2);
        assertThat(store.entries()).anyMatch(e -> e.fact().contains("Reporting adapters"));
        assertThat(store.entries()).anyMatch(e -> e.fact().contains("Payment retries"));
    }

    @Test
    void producesNoMemoryWhenNoItemsAreRetrieved() {
        assertThatCode(() -> ExternalKnowledgeLearner.learn(List.of(), store)).doesNotThrowAnyException();

        assertThat(store.entries()).isEmpty();
    }

    @Test
    void doesNotDuplicateAnAlreadyLearnedFactOnRerun() {
        KnowledgeItem item = knowledgeItem("obsidian", "Reporting Adapter Exception",
                "Reporting adapters intentionally bypass the repository boundary");
        ExternalKnowledgeLearner.learn(List.of(item), store);

        ExternalKnowledgeLearner.learn(List.of(item), store);

        assertThat(store.entries())
                .filteredOn(e -> e.fact().contains("Reporting adapters intentionally bypass the repository boundary"))
                .hasSize(1);
    }

    private KnowledgeItem knowledgeItem(String providerId, String title, String content) {
        return KnowledgeItem.builder(providerId, title, content, title + ".md", Instant.now()).build();
    }
}
