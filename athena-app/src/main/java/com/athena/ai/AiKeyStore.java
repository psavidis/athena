package com.athena.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Optional;
import java.util.Set;

/**
 * Persists an Anthropic API key pasted in via "Connect Claude" to
 * {@code ~/.athena/claude.json}, so it only ever has to be entered once —
 * mirrors {@code com.athena.github.GitHubAppStore}'s role for the GitHub
 * App flow. The {@code ANTHROPIC_API_KEY} environment variable still takes
 * precedence when set (see {@code AiAnalysisController}), so this is purely
 * a fallback for whoever doesn't already export it themselves.
 */
public class AiKeyStore {

    private final Path file;
    private final ObjectMapper json = new ObjectMapper();

    public AiKeyStore() {
        this(Path.of(System.getProperty("user.home"), ".athena", "claude.json"));
    }

    public AiKeyStore(Path file) {
        this.file = file;
    }

    public Optional<String> loadApiKey() {
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        JsonNode root;
        try {
            root = json.readTree(file.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String apiKey = root.path("apiKey").asText(null);
        return apiKey == null || apiKey.isBlank() ? Optional.empty() : Optional.of(apiKey);
    }

    public synchronized void saveApiKey(String apiKey) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, json.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(json.createObjectNode().put("apiKey", apiKey)));
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
