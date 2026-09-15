package com.athena.knowledge;

import com.athena.knowledge.spi.KnowledgeCandidate;
import com.athena.knowledge.spi.KnowledgeCaptureResult;
import com.athena.knowledge.spi.KnowledgeItem;
import com.athena.knowledge.spi.KnowledgeProvider;
import com.athena.knowledge.spi.KnowledgeProviderConfiguration;
import com.athena.knowledge.spi.KnowledgeQuery;
import com.athena.knowledge.spi.KnowledgeRetrievalResult;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The initial concrete {@link KnowledgeProvider} (ticket #118): an
 * Obsidian vault — a plain directory of Markdown notes on disk. Athena
 * does not replace Obsidian or provide a note editor; it only reads
 * notes relevant to the current review, and can append a user-approved
 * {@link KnowledgeCandidate} as a new note under an "Athena Knowledge"
 * subfolder, leaving every existing human-authored note untouched.
 *
 * <p>Never throws for an ordinary failure (no vault configured, the
 * vault directory doesn't exist or became unreadable) — such failures
 * are reported via a failed {@link KnowledgeRetrievalResult}/
 * {@link KnowledgeCaptureResult}, matching {@link KnowledgeProvider}'s
 * own contract, so a missing/broken vault never blocks a review.
 */
public final class ObsidianKnowledgeProvider implements KnowledgeProvider {

    public static final String PROVIDER_ID = "obsidian";
    public static final String VAULT_PATH_SETTING = "vaultPath";
    private static final String CAPTURED_NOTES_FOLDER = "Athena Knowledge";

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public KnowledgeRetrievalResult retrieveRelevant(KnowledgeQuery query, KnowledgeProviderConfiguration configuration) {
        Optional<Path> vault = configuredVault(configuration);
        if (vault.isEmpty()) {
            return KnowledgeRetrievalResult.failure("Obsidian vault path not configured");
        }
        if (!Files.isDirectory(vault.get())) {
            return KnowledgeRetrievalResult.failure("Obsidian vault directory not found: " + vault.get());
        }
        try (Stream<Path> notes = Files.walk(vault.get())) {
            List<KnowledgeItem> items = notes
                    .filter(Files::isRegularFile)
                    .filter(ObsidianKnowledgeProvider::isMarkdownNote)
                    .map(notePath -> readNote(notePath, vault.get(), query.repositoryContext()))
                    .filter(query::isRelevantTo)
                    .toList();
            return KnowledgeRetrievalResult.success(items);
        } catch (IOException | UncheckedIOException e) {
            return KnowledgeRetrievalResult.failure("Could not read Obsidian vault: " + e.getMessage());
        }
    }

    @Override
    public KnowledgeCaptureResult captureCandidate(KnowledgeCandidate candidate, KnowledgeProviderConfiguration configuration) {
        Optional<Path> vault = configuredVault(configuration);
        if (vault.isEmpty()) {
            return KnowledgeCaptureResult.failure("Obsidian vault path not configured");
        }
        if (!Files.isDirectory(vault.get())) {
            return KnowledgeCaptureResult.failure("Obsidian vault directory not found: " + vault.get());
        }
        try {
            return KnowledgeCaptureResult.success(writeNote(candidate, vault.get()));
        } catch (IOException e) {
            return KnowledgeCaptureResult.failure("Could not write to Obsidian vault: " + e.getMessage());
        }
    }

    private KnowledgeItem writeNote(KnowledgeCandidate candidate, Path vault) throws IOException {
        Path capturedNotesDir = vault.resolve(CAPTURED_NOTES_FOLDER);
        Files.createDirectories(capturedNotesDir);
        String title = noteTitle(candidate);
        String fileName = "athena-knowledge-" + candidate.proposedAt().toEpochMilli() + ".md";
        Files.writeString(capturedNotesDir.resolve(fileName), renderNote(title, candidate));
        String source = CAPTURED_NOTES_FOLDER + "/" + fileName;
        return KnowledgeItem.builder(PROVIDER_ID, title, candidate.content(), source, candidate.proposedAt())
                .repositoryContext(candidate.repositoryContext())
                .build();
    }

    private KnowledgeItem readNote(Path notePath, Path vault, String repositoryContext) {
        try {
            String content = Files.readString(notePath);
            Instant createdAt = Files.getLastModifiedTime(notePath).toInstant();
            String title = stripExtension(notePath.getFileName().toString());
            String source = vault.relativize(notePath).toString();
            return KnowledgeItem.builder(PROVIDER_ID, title, content, source, createdAt)
                    .repositoryContext(repositoryContext)
                    .build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Optional<Path> configuredVault(KnowledgeProviderConfiguration configuration) {
        String vaultPath = configuration.settings().get(VAULT_PATH_SETTING);
        return (vaultPath == null || vaultPath.isBlank()) ? Optional.empty() : Optional.of(Path.of(vaultPath));
    }

    private static boolean isMarkdownNote(Path path) {
        return path.toString().toLowerCase(Locale.ROOT).endsWith(".md");
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static String noteTitle(KnowledgeCandidate candidate) {
        String firstLine = candidate.content().lines().findFirst().orElse("Athena Knowledge");
        return firstLine.length() > 80 ? firstLine.substring(0, 80) : firstLine;
    }

    private static String renderNote(String title, KnowledgeCandidate candidate) {
        StringBuilder body = new StringBuilder();
        body.append("# ").append(title).append("\n\n").append(candidate.content()).append("\n\n---\n");
        candidate.rationale().ifPresent(rationale -> body.append("Rationale: ").append(rationale).append('\n'));
        body.append("Proposed at: ").append(candidate.proposedAt()).append('\n');
        if (!candidate.repositoryContext().isBlank()) {
            body.append("Repository: ").append(candidate.repositoryContext()).append('\n');
        }
        return body.toString();
    }
}
