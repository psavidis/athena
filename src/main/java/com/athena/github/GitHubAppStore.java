package com.athena.github;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Optional;
import java.util.Set;

/**
 * Persists the GitHub App registration (App ID + private key, minted once
 * via the manifest flow) and the resulting installation (installation ID +
 * account) to a local file, {@code ~/.athena/github-app.json}. Athena
 * itself is ephemeral — launched and torn down per session — but
 * re-registering a brand-new GitHub App and re-installing it on every
 * launch would defeat the point of "connect once"; this file is what lets
 * a later launch skip straight back to an already-connected state.
 */
public class GitHubAppStore {

    private static final String REGISTRATION_FIELD = "registration";
    private static final String INSTALLATION_FIELD = "installation";

    private final Path file;
    private final ObjectMapper json = new ObjectMapper();

    public GitHubAppStore() {
        this(Path.of(System.getProperty("user.home"), ".athena", "github-app.json"));
    }

    public GitHubAppStore(Path file) {
        this.file = file;
    }

    public Optional<GitHubAppRegistration> loadRegistration() {
        return readRoot().map(root -> root.get(REGISTRATION_FIELD)).filter(node -> !node.isMissingNode())
                .map(node -> new GitHubAppRegistration(
                        node.path("appId").asText(),
                        node.path("appSlug").asText(),
                        node.path("privateKeyPem").asText(),
                        node.path("webhookSecret").asText()));
    }

    public Optional<GitHubAppInstallationRecord> loadInstallation() {
        return readRoot().map(root -> root.get(INSTALLATION_FIELD)).filter(node -> !node.isMissingNode())
                .map(node -> new GitHubAppInstallationRecord(
                        node.path("installationId").asLong(),
                        node.path("accountLogin").asText()));
    }

    public synchronized void saveRegistration(GitHubAppRegistration registration) {
        ObjectNode root = readRootForWrite();
        ObjectNode node = json.createObjectNode();
        node.put("appId", registration.appId());
        node.put("appSlug", registration.appSlug());
        node.put("privateKeyPem", registration.privateKeyPem());
        node.put("webhookSecret", registration.webhookSecret());
        root.set(REGISTRATION_FIELD, node);
        write(root);
    }

    public synchronized void saveInstallation(GitHubAppInstallationRecord installation) {
        ObjectNode root = readRootForWrite();
        ObjectNode node = json.createObjectNode();
        node.put("installationId", installation.installationId());
        node.put("accountLogin", installation.accountLogin());
        root.set(INSTALLATION_FIELD, node);
        write(root);
    }

    private Optional<com.fasterxml.jackson.databind.JsonNode> readRoot() {
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
        return readRoot().filter(com.fasterxml.jackson.databind.JsonNode::isObject)
                .map(ObjectNode.class::cast)
                .orElseGet(json::createObjectNode);
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
            Set<PosixFilePermission> ownerOnly = PosixFilePermissions.fromString("rw-------");
            Files.setPosixFilePermissions(path, ownerOnly);
        }
    }
}
