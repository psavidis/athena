package com.athena.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Tracks, per evidence source, how much of that source's evidence a learner
 * (ticket #170/#171) has already incorporated into project memory (ticket
 * #174) — the general "high-water mark" mechanism the epic's incremental-
 * update requirement calls for, reusable by any evidence source rather than
 * hardcoded to one. Two primitives, since evidence sources don't all
 * traverse the same way: a single {@link #cursor}, for evidence with a
 * natural linear order (git commit history, walked via one "last processed"
 * point); and a {@link #processedMarkers} set, for evidence that can
 * complete out of order (Pull Requests don't necessarily close in number
 * order). A {@link #counter} accumulates a running total (e.g. a co-change
 * pair's occurrence count) across passes, so a pattern that hadn't yet
 * crossed a recurrence threshold on an earlier pass can still cross it once
 * enough new evidence arrives, without re-processing the evidence already
 * counted.
 *
 * <p>Persisted under the same {@code .athena/memory/} boundary as {@link
 * ProjectMemoryStore} (ticket #169), in a sibling file — progress bookkeeping
 * is a distinct concern from the facts themselves.
 */
public class LearningProgressStore {

    private final Path progressFile;
    private final ObjectMapper json = new ObjectMapper();

    public LearningProgressStore(Path projectRoot) {
        this.progressFile = projectRoot.resolve(".athena").resolve("memory").resolve("learning-progress.json");
    }

    /** The last-processed point for {@code source}'s linearly-ordered evidence (e.g. a commit SHA), if any. */
    public synchronized Optional<String> cursor(String source) {
        JsonNode value = section(readRoot(), "cursors").path(source);
        return value.isMissingNode() ? Optional.empty() : Optional.of(value.asText());
    }

    public synchronized void recordCursor(String source, String cursor) {
        ObjectNode root = readRoot();
        section(root, "cursors").put(source, cursor);
        write(root);
    }

    /** Markers (e.g. Pull Request numbers) already processed for {@code source}, regardless of the order they completed in. */
    public synchronized Set<String> processedMarkers(String source) {
        Set<String> markers = new LinkedHashSet<>();
        section(readRoot(), "markers").path(source).forEach(node -> markers.add(node.asText()));
        return markers;
    }

    public synchronized void recordProcessedMarker(String source, String marker) {
        ObjectNode root = readRoot();
        ObjectNode markersSection = section(root, "markers");
        Set<String> markers = new LinkedHashSet<>();
        markersSection.path(source).forEach(node -> markers.add(node.asText()));
        markers.add(marker);
        ArrayNode array = json.createArrayNode();
        markers.forEach(array::add);
        markersSection.set(source, array);
        write(root);
    }

    /** The cumulative count recorded under {@code key} for {@code source} (e.g. a co-change file pair) — 0 if none yet. */
    public synchronized int counter(String source, String key) {
        JsonNode value = section(readRoot(), "counters").path(compositeKey(source, key));
        return value.isMissingNode() ? 0 : value.asInt();
    }

    public synchronized void incrementCounter(String source, String key, int delta) {
        ObjectNode root = readRoot();
        ObjectNode counters = section(root, "counters");
        String compositeKey = compositeKey(source, key);
        int current = counters.path(compositeKey).isMissingNode() ? 0 : counters.path(compositeKey).asInt();
        counters.put(compositeKey, current + delta);
        write(root);
    }

    private static String compositeKey(String source, String key) {
        return source + "::" + key;
    }

    private ObjectNode section(ObjectNode root, String name) {
        JsonNode existing = root.get(name);
        if (existing instanceof ObjectNode objectNode) {
            return objectNode;
        }
        ObjectNode created = json.createObjectNode();
        root.set(name, created);
        return created;
    }

    private ObjectNode readRoot() {
        if (!Files.exists(progressFile)) {
            return json.createObjectNode();
        }
        try {
            JsonNode node = json.readTree(progressFile.toFile());
            return node.isObject() ? (ObjectNode) node : json.createObjectNode();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void write(ObjectNode root) {
        try {
            Files.createDirectories(progressFile.getParent());
            Files.writeString(progressFile, json.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
