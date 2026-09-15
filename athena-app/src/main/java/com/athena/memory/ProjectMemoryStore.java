package com.athena.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Athena's persistent, project-local memory (ticket #169) — what Athena has
 * learned about a project, stored at {@code <projectRoot>/.athena/memory/},
 * distinct from Athena's per-installation config (e.g. {@code ~/.athena/})
 * and from its cache. Every learning source ticket #121 introduces (git
 * history, Pull Requests, external knowledge, ...) builds on this same
 * storage boundary. A project whose memory directory has never been written,
 * or was deleted directly from disk, behaves identically: {@link #entries()}
 * returns an empty list rather than failing.
 */
public class ProjectMemoryStore {

    private final Path memoryFile;
    private final ObjectMapper json = new ObjectMapper();

    public ProjectMemoryStore(Path projectRoot) {
        this.memoryFile = projectRoot.resolve(".athena").resolve("memory").resolve("memory.json");
    }

    public synchronized void record(MemoryEntry entry) {
        ArrayNode entries = readEntries();
        ObjectNode node = json.createObjectNode();
        node.put("fact", entry.fact());
        node.put("evidence", entry.evidence());
        node.put("confidence", entry.confidence());
        node.put("developerConfirmed", entry.developerConfirmed());
        entries.add(node);
        write(entries);
    }

    public List<MemoryEntry> entries() {
        List<MemoryEntry> result = new ArrayList<>();
        for (JsonNode node : readEntries()) {
            result.add(new MemoryEntry(
                    node.path("fact").asText(),
                    node.path("evidence").asText(),
                    node.path("confidence").asText(),
                    node.path("developerConfirmed").asBoolean()));
        }
        return result;
    }

    private ArrayNode readEntries() {
        if (!Files.exists(memoryFile)) {
            return json.createArrayNode();
        }
        try {
            JsonNode root = json.readTree(memoryFile.toFile());
            return root.isArray() ? (ArrayNode) root : json.createArrayNode();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void write(ArrayNode entries) {
        try {
            Files.createDirectories(memoryFile.getParent());
            Files.writeString(memoryFile, json.writerWithDefaultPrettyPrinter().writeValueAsString(entries));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
