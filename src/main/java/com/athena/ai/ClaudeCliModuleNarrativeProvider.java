package com.athena.ai;

import com.athena.git.TempDirectories;
import com.athena.semantic.ModuleGroup;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * {@link ModuleNarrativeProvider} backed by the locally-installed
 * {@code claude} CLI instead of a separately-billed API call — the
 * module-narrative counterpart to {@link ClaudeCliProvider}; see there for
 * why each flag is set the way it is.
 */
public class ClaudeCliModuleNarrativeProvider implements ModuleNarrativeProvider {

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

    public ClaudeCliModuleNarrativeProvider(String claudeExecutable) {
        this.claudeExecutable = claudeExecutable;
    }

    @Override
    public String explain(ModuleGroup moduleGroup) {
        String prompt = ModuleNarrativePrompt.build(moduleGroup);
        JsonNode envelope = run(prompt);
        if (envelope.path("is_error").asBoolean(false)) {
            throw new AiProviderException("claude CLI reported an error: " + envelope.path("result").asText());
        }
        return envelope.path("result").asText("").strip();
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
