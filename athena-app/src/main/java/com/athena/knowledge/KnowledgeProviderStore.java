package com.athena.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Optional;

/**
 * Persists the configured Knowledge Provider to a local file,
 * {@code ~/.athena/knowledge.json} — mirrors {@code com.athena.github.GitHubAppStore}
 * and {@code com.athena.ai.AiKeyStore}'s role for their own external
 * integrations. Only one concrete provider (Obsidian) exists yet; the
 * file shape (a top-level field per provider id) leaves room for a
 * future provider without migrating this one.
 */
public class KnowledgeProviderStore {

    private static final String OBSIDIAN_FIELD = "obsidian";

    private final Path file;
    private final ObjectMapper json = new ObjectMapper();

    public KnowledgeProviderStore() {
        this(Path.of(System.getProperty("user.home"), ".athena", "knowledge.json"));
    }

    public KnowledgeProviderStore(Path file) {
        this.file = file;
    }

    public Optional<ObsidianVaultConfig> loadObsidianConfig() {
        return readRoot().map(root -> root.get(OBSIDIAN_FIELD))
                .filter(node -> node != null && !node.isMissingNode())
                .map(node -> new ObsidianVaultConfig(node.path("vaultPath").asText(), node.path("enabled").asBoolean(true)));
    }

    public synchronized void saveObsidianConfig(ObsidianVaultConfig config) {
        ObjectNode root = readRootForWrite();
        ObjectNode node = json.createObjectNode();
        node.put("vaultPath", config.vaultPath());
        node.put("enabled", config.enabled());
        root.set(OBSIDIAN_FIELD, node);
        write(root);
    }

    public synchronized void clearObsidianConfig() {
        ObjectNode root = readRootForWrite();
        root.remove(OBSIDIAN_FIELD);
        write(root);
    }

    private Optional<JsonNode> readRoot() {
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(json.readTree(file.toFile()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private ObjectNode readRootForWrite() {
        return readRoot().filter(JsonNode::isObject).map(ObjectNode.class::cast).orElseGet(json::createObjectNode);
    }

    private void write(ObjectNode root) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, json.writerWithDefaultPrettyPrinter().writeValueAsString(root));
            restrictToOwnerOnly(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void restrictToOwnerOnly(Path path) throws IOException {
        if (path.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        }
    }
}
