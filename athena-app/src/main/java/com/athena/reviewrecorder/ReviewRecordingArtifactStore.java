package com.athena.reviewrecorder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Persistent, project-local storage for {@link ReviewRecordingArtifact}s
 * (ticket #207), at {@code <projectRoot>/.athena/review-recordings/}, one
 * file per artifact — the same {@code .athena/}-rooted, project-local
 * storage boundary {@code com.athena.memory.ProjectMemoryStore} already
 * establishes for Project Memory, applied here since a Review Recording
 * is exactly this kind of project-local artifact. No centralized Athena
 * service is required to read or write it.
 */
public class ReviewRecordingArtifactStore {

    private final Path artifactsDir;
    private final ObjectMapper json = new ObjectMapper();

    public ReviewRecordingArtifactStore(Path projectRoot) {
        this.artifactsDir = projectRoot.resolve(".athena").resolve("review-recordings");
    }

    /** Persists {@code artifact}, overwriting any previous version of the same recording id. */
    public void persist(ReviewRecordingArtifact artifact) {
        ObjectNode root = json.createObjectNode();
        root.put("recordingId", artifact.recordingId());
        root.put("repositoryFullName", artifact.repositoryFullName());
        root.put("pullRequestNumber", artifact.pullRequestNumber());
        root.put("commitOrVersion", artifact.commitOrVersion());
        root.put("audioEnabled", artifact.audioEnabled());
        root.put("durationSeconds", artifact.summary().duration().getSeconds());
        root.set("events", eventsNode(artifact.events()));
        root.set("moments", momentsNode(artifact.moments()));
        write(artifact.recordingId(), root);
    }

    private ArrayNode eventsNode(List<SemanticEvent> events) {
        ArrayNode array = json.createArrayNode();
        for (SemanticEvent event : events) {
            ObjectNode node = json.createObjectNode();
            node.put("type", event.type().name());
            node.put("reference", event.reference());
            node.put("occurredAt", event.occurredAt().toString());
            array.add(node);
        }
        return array;
    }

    private ArrayNode momentsNode(List<Moment> moments) {
        ArrayNode array = json.createArrayNode();
        for (Moment moment : moments) {
            ObjectNode node = json.createObjectNode();
            node.put("id", moment.id());
            node.put("kind", moment.kind().name());
            node.put("reference", moment.reference());
            node.put("taggedAt", moment.taggedAt().toString());
            node.put("status", moment.status().name());
            array.add(node);
        }
        return array;
    }

    /** The persisted artifact for {@code recordingId}, if one was ever persisted. */
    public Optional<ReviewRecordingArtifact> find(String recordingId) {
        Path file = artifactFile(recordingId);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        return Optional.of(parseArtifact(readTree(file)));
    }

    /**
     * Imports a Review Recording artifact a teammate exported from their own Athena instance
     * (ticket #217) — the same JSON shape {@link #persist} writes, since that file already is
     * the portable artifact format; no separate export mechanism needed. Rejects (with {@link
     * IllegalArgumentException}) a file that isn't a valid artifact, one whose repository doesn't
     * match {@code expectedRepositoryFullName}, or one whose recording id is already stored
     * locally — the last case protects against a shared file silently overwriting a distinct
     * local recording that happens to share an id.
     */
    public ReviewRecordingArtifact importFrom(Path file, String expectedRepositoryFullName) {
        JsonNode root;
        ReviewRecordingArtifact artifact;
        try {
            root = readTree(file);
            artifact = parseArtifact(root);
        } catch (RuntimeException e) {
            // Covers UncheckedIOException (invalid JSON), DateTimeParseException (Instant.parse
            // on a corrupt occurredAt/taggedAt), and IllegalArgumentException/NullPointerException
            // (SemanticEventType/MomentKind.valueOf, requireNonBlank) — every way a
            // structurally-valid-JSON-but-not-actually-an-artifact file can fail parsing must
            // reject as "not a valid artifact", never surface as an unhandled 500.
            throw new IllegalArgumentException("Not a valid Review Recording artifact file: " + file, e);
        }
        if (!artifact.repositoryFullName().equals(expectedRepositoryFullName)) {
            throw new IllegalArgumentException("Artifact is for repository \"" + artifact.repositoryFullName()
                    + "\", not the currently-selected \"" + expectedRepositoryFullName + "\"");
        }
        if (Files.exists(artifactFile(artifact.recordingId()))) {
            throw new IllegalArgumentException(
                    "A Review Recording artifact with id \"" + artifact.recordingId() + "\" already exists locally");
        }
        persist(artifact);
        return artifact;
    }

    private ReviewRecordingArtifact parseArtifact(JsonNode root) {
        List<SemanticEvent> events = new ArrayList<>();
        for (JsonNode node : root.path("events")) {
            events.add(SemanticEvent.of(SemanticEventType.valueOf(node.path("type").asText()),
                    node.path("reference").asText(), Instant.parse(node.path("occurredAt").asText())));
        }
        List<Moment> moments = new ArrayList<>();
        for (JsonNode node : root.path("moments")) {
            moments.add(Moment.reconstruct(node.path("id").asText(), MomentKind.valueOf(node.path("kind").asText()),
                    node.path("reference").asText(null), Instant.parse(node.path("taggedAt").asText()),
                    MomentStatus.valueOf(node.path("status").asText())));
        }
        Duration duration = Duration.ofSeconds(root.path("durationSeconds").asLong());
        String recordingId = requireNonBlank(root.path("recordingId").asText(null), "recordingId");
        String repositoryFullName = requireNonBlank(root.path("repositoryFullName").asText(null), "repositoryFullName");
        return ReviewRecordingArtifact.of(recordingId, repositoryFullName, root.path("pullRequestNumber").asInt(),
                root.path("commitOrVersion").asText(), root.path("audioEnabled").asBoolean(), events, moments, duration);
    }

    private String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private JsonNode readTree(Path file) {
        try {
            return json.readTree(file.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void write(String recordingId, ObjectNode root) {
        Path file = artifactFile(recordingId);
        try {
            Files.createDirectories(artifactsDir);
            Files.writeString(file, json.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path artifactFile(String recordingId) {
        return artifactsDir.resolve(recordingId + ".json");
    }
}
