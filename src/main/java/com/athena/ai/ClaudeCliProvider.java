package com.athena.ai;

import com.athena.git.TempDirectories;
import com.athena.reviewcontext.ReviewContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * {@link AiProvider} backed by the locally-installed {@code claude} CLI
 * instead of a direct, separately-billed Anthropic API call — reuses
 * whatever Claude Code login/subscription the person running Athena
 * already has (see {@link ClaudeAiProvider} for the API-key alternative).
 *
 * <p>Run with flags that keep it a narrow one-shot analysis call rather
 * than a full agent session: {@code --restricted --tools ""} strips all
 * tool access and ignores this repo's CLAUDE.md/settings, and running from
 * a scratch temp directory (not this project's working directory) means
 * there is no project context for it to read or act on in the first place.
 * Flags alone are not enough, though — by default the model still narrates
 * agentic intentions ("let me check the working directory...") instead of
 * just answering, even with no tools to act on that intent; an explicit
 * {@code --append-system-prompt} telling it it is a plain text-analysis
 * function, not a coding agent, is what actually gets a clean answer.
 */
public class ClaudeCliProvider implements AiProvider {

    private static final String ANALYSIS_SYSTEM_PROMPT =
            "You are a pure text-analysis function, not a coding agent. You have no tools, no file "
                    + "access, and no working directory to inspect. Base your answer only on the "
                    + "information given directly in the user message — never attempt to explore, read "
                    + "files, or check context beyond what is provided. Respond with only the exact "
                    + "output format requested, nothing else — no narration, no explanation of what you "
                    + "would do, no code fences.";

    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private final String claudeExecutable;
    private final ObjectMapper json = new ObjectMapper();

    public ClaudeCliProvider(String claudeExecutable) {
        this.claudeExecutable = claudeExecutable;
    }

    @Override
    public List<AiFinding> analyze(ReviewContext reviewContext) {
        String prompt = ClaudeAnalysisPrompt.build(reviewContext);
        JsonNode envelope = run(prompt);
        if (envelope.path("is_error").asBoolean(false)) {
            throw new AiProviderException("claude CLI reported an error: " + envelope.path("result").asText());
        }
        return ClaudeAnalysisPrompt.parseFindings(envelope.path("result").asText("[]"));
    }

    private JsonNode run(String prompt) {
        Path scratchDir;
        try {
            scratchDir = Files.createTempDirectory("athena-claude-cli-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    claudeExecutable, "-p", prompt,
                    "--restricted", "--tools", "",
                    "--append-system-prompt", ANALYSIS_SYSTEM_PROMPT,
                    "--output-format", "json")
                    .directory(scratchDir.toFile())
                    .redirectErrorStream(false);

            Process process = processBuilder.start();
            process.getOutputStream().close();
            String stdout = new String(process.getInputStream().readAllBytes());
            String stderr = new String(process.getErrorStream().readAllBytes());
            boolean finished = process.waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new AiProviderException("claude CLI timed out after " + TIMEOUT.toSeconds() + "s");
            }
            if (process.exitValue() != 0) {
                throw new AiProviderException("claude CLI exited with code " + process.exitValue() + ": " + stderr);
            }
            return json.readTree(stdout);
        } catch (IOException e) {
            throw new AiProviderException("Failed to run claude CLI: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiProviderException("Interrupted while running claude CLI", e);
        } finally {
            TempDirectories.deleteRecursively(scratchDir);
        }
    }
}
