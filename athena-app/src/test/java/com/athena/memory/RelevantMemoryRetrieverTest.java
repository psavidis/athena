package com.athena.memory;

import com.athena.git.TempDirectories;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dedicated unit tests for {@link RelevantMemoryRetriever} (ticket #173) —
 * scoping project memory to a review's changed files rather than returning
 * the entire store.
 */
class RelevantMemoryRetrieverTest {

    private Path projectRoot;
    private ProjectMemoryStore store;

    @BeforeEach
    void createProject() throws IOException {
        projectRoot = Files.createTempDirectory("athena-relevant-memory-retriever-test-");
        store = new ProjectMemoryStore(projectRoot);
    }

    @AfterEach
    void cleanUpProject() {
        TempDirectories.deleteRecursively(projectRoot);
    }

    @Test
    void returnsAFactMentioningAChangedFile() {
        store.record(new MemoryEntry("OrderService.java and OrderProjection.java change together",
                "3 commits", "medium", false));

        List<MemoryEntry> relevant = RelevantMemoryRetriever.retrieve(store, List.of("OrderService.java"));

        assertThat(relevant).extracting(MemoryEntry::fact)
                .containsExactly("OrderService.java and OrderProjection.java change together");
    }

    @Test
    void excludesAFactNotMentioningAnyChangedFile() {
        store.record(new MemoryEntry("Invoice.java and InvoiceView.java change together",
                "3 commits", "medium", false));

        List<MemoryEntry> relevant = RelevantMemoryRetriever.retrieve(store, List.of("OrderService.java"));

        assertThat(relevant).isEmpty();
    }

    @Test
    void matchesByFileNameEvenWhenTheChangedFileIsGivenAsAFullPath() {
        store.record(new MemoryEntry("OrderService.java and OrderProjection.java change together",
                "3 commits", "medium", false));

        List<MemoryEntry> relevant = RelevantMemoryRetriever.retrieve(
                store, List.of("src/main/java/com/acme/OrderService.java"));

        assertThat(relevant).extracting(MemoryEntry::fact)
                .containsExactly("OrderService.java and OrderProjection.java change together");
    }

    @Test
    void doesNotMatchAChangedFileNameThatIsOnlyASuffixOfAMentionedFileName() {
        store.record(new MemoryEntry("RequestHandler.java and ResponseWriter.java change together",
                "3 commits", "medium", false));

        List<MemoryEntry> relevant = RelevantMemoryRetriever.retrieve(store, List.of("Handler.java"));

        assertThat(relevant).isEmpty();
    }

    @Test
    void returnsEmptyWhenTheStoreHasNoMemory() {
        List<MemoryEntry> relevant = RelevantMemoryRetriever.retrieve(store, List.of("OrderService.java"));

        assertThat(relevant).isEmpty();
    }
}
