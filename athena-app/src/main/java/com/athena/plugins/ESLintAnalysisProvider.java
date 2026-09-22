package com.athena.plugins;

import com.athena.analysis.spi.ExternalAnalysisProvider;
import com.athena.analysis.spi.ExternalFinding;
import com.athena.analysis.spi.ExternalFindingSeverity;
import com.athena.analysis.spi.ProviderConfiguration;
import com.athena.analysis.spi.ProviderRunResult;
import com.athena.analysis.spi.SourceLocation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ESLint (ticket #114/#117) as a concrete {@link ExternalAnalysisProvider}: runs the
 * analyzed project's own, already-installed ESLint CLI — never a version Athena
 * bundles or manages itself (see the ticket's "no automatic installation" scope note)
 * — against its source tree, mapping each reported message into a provider-independent
 * {@link ExternalFinding}. Unlike {@link PmdAnalysisProvider} (ticket #116), ESLint has
 * no in-process Java embedding API: it is a Node.js CLI, so this provider shells out to
 * it as a subprocess and parses its {@code --format json} output — a genuine external-
 * process boundary, not a library call, but the same provider contract either way.
 *
 * <p>Deliberately does not hardcode or bundle an ESLint version: it resolves and runs
 * whichever ESLint the analyzed project already has installed (its own {@code
 * node_modules/.bin/eslint} by default, or {@link #ESLINT_EXECUTABLE_SETTING} to point
 * elsewhere), and relies only on the {@code --format json} reporter shape that has been
 * stable across ESLint's recent major releases for the fields this class reads
 * (ruleId, severity, message, line, endLine, column) — this is how "support the current
 * major release" is satisfied without Athena tracking ESLint's release cadence itself.
 * ESLint resolves its own configuration (flat config or legacy) from the directory it
 * is run in, exactly as it would for a developer running it directly — Athena never
 * constructs or overrides that configuration.
 *
 * <p>Never throws for an ordinary failure (ESLint not installed, a config it can't
 * load, a process that can't be started): {@link #analyze} reports a failed {@link
 * ProviderRunResult} instead, per {@link ExternalAnalysisProvider#analyze}'s documented
 * contract. ESLint's own CLI exit codes distinguish "ran, and found lint problems"
 * (0 = none, 1 = some) from "did not run at all" (any other code, e.g. 2 for a fatal
 * configuration/parsing error) — only the latter is treated as a provider failure here.
 */
public final class ESLintAnalysisProvider implements ExternalAnalysisProvider {

    private static final String PROVIDER_ID = "eslint";
    private static final String LANGUAGE = "javascript";
    private static final Path DEFAULT_RELATIVE_EXECUTABLE = Path.of("node_modules", ".bin", "eslint");
    private static final int EXIT_CODE_NO_LINT_PROBLEMS = 0;
    private static final int EXIT_CODE_LINT_PROBLEMS_FOUND = 1;

    /**
     * Key into {@link ProviderConfiguration#settings()} pointing at an ESLint
     * executable other than the analyzed project's own default {@code
     * node_modules/.bin/eslint} — opaque to Athena's core, meaningful only to this
     * provider (e.g. a globally-installed ESLint, or a test fixture's own install).
     */
    public static final String ESLINT_EXECUTABLE_SETTING = "eslintExecutable";

    /**
     * ESLint's own two active message severities (1 = warn, 2 = error — 0 means "off"
     * and never appears in reported output) map onto {@link ExternalFindingSeverity}.
     * ESLint's severity model has no notion of "this must never ship" the way PMD's
     * HIGH priority documents, so "error" maps to {@code HIGH} rather than the most
     * extreme {@code BLOCKER} — {@code BLOCKER}/{@code LOW}/{@code INFO} are reserved
     * for providers whose own severity model actually distinguishes those tiers. A
     * plain lookup map rather than a switch (CODE_STYLE.md's enum-mapping guidance).
     */
    private static final Map<Integer, ExternalFindingSeverity> SEVERITY_BY_ESLINT_SEVERITY = Map.of(
            2, ExternalFindingSeverity.HIGH,
            1, ExternalFindingSeverity.MEDIUM);

    private final ObjectMapper json = new ObjectMapper();

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public String language() {
        return LANGUAGE;
    }

    @Override
    public ProviderRunResult analyze(Path sourceRoot, List<Path> changedFiles, ProviderConfiguration configuration) {
        if (!Files.isDirectory(sourceRoot)) {
            return ProviderRunResult.failure("source root does not exist or is not a directory: " + sourceRoot);
        }
        Path executable = executableOf(sourceRoot, configuration);
        if (!Files.isExecutable(executable)) {
            return ProviderRunResult.failure(
                    "ESLint is not installed in the analyzed project (no executable found at " + executable + ")");
        }
        try {
            return runEslint(sourceRoot, executable);
        } catch (IOException e) {
            return ProviderRunResult.failure(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ProviderRunResult.failure("interrupted while running ESLint");
        }
    }

    private Path executableOf(Path sourceRoot, ProviderConfiguration configuration) {
        String override = configuration.settings().get(ESLINT_EXECUTABLE_SETTING);
        return override != null ? Path.of(override) : sourceRoot.resolve(DEFAULT_RELATIVE_EXECUTABLE);
    }

    private ProviderRunResult runEslint(Path sourceRoot, Path executable) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(
                executable.toString(), "--format", "json", "--no-error-on-unmatched-pattern", ".");
        processBuilder.directory(sourceRoot.toFile());
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();

        if (exitCode != EXIT_CODE_NO_LINT_PROBLEMS && exitCode != EXIT_CODE_LINT_PROBLEMS_FOUND) {
            return ProviderRunResult.failure(diagnosticMessage(exitCode, output));
        }
        return parseFindings(output, sourceRoot);
    }

    private String diagnosticMessage(int exitCode, String output) {
        return output.isBlank() ? "ESLint exited with code " + exitCode : output.strip();
    }

    private ProviderRunResult parseFindings(String output, Path sourceRoot) {
        JsonNode fileResults;
        try {
            fileResults = json.readTree(output.isBlank() ? "[]" : output);
        } catch (JsonProcessingException e) {
            return ProviderRunResult.failure("ESLint produced output that could not be parsed as JSON: " + e.getMessage());
        }
        if (!fileResults.isArray()) {
            return ProviderRunResult.failure("ESLint's JSON output was not an array of file results");
        }

        List<ExternalFinding> findings = new ArrayList<>();
        for (JsonNode fileResult : fileResults) {
            String filePath = relativePathOf(fileResult.path("filePath").asText(""), sourceRoot);
            for (JsonNode message : fileResult.path("messages")) {
                findings.add(toExternalFinding(filePath, message));
            }
        }
        return ProviderRunResult.success(findings);
    }

    private ExternalFinding toExternalFinding(String filePath, JsonNode message) {
        String ruleId = ruleIdOf(message);
        int eslintSeverity = message.path("severity").asInt(1);
        int startLine = Math.max(1, message.path("line").asInt(1));
        int endLine = Math.max(startLine, message.path("endLine").asInt(startLine));
        String text = message.path("message").asText("");
        SourceLocation location = SourceLocation.ofRange(filePath, startLine, endLine);

        ExternalFinding.Builder builder = ExternalFinding
                .builder(PROVIDER_ID, LANGUAGE, ruleId, severityOf(eslintSeverity), text, location)
                .ruleName(ruleId);

        int column = message.path("column").asInt(0);
        if (column > 0) {
            builder.providerSpecificMetadata(Map.of("column", String.valueOf(column)));
        }
        return builder.build();
    }

    /**
     * ESLint's own fatal parse errors (e.g. a syntax error the parser can't recover
     * from) report a message with no {@code ruleId} — this is not a rule violation, so
     * it is given a stable synthetic identifier rather than leaving {@link
     * ExternalFinding}'s required, non-blank {@code ruleId} unset.
     */
    private String ruleIdOf(JsonNode message) {
        String ruleId = message.path("ruleId").asText("");
        return ruleId.isBlank() ? "parse-error" : ruleId;
    }

    /**
     * ESLint (Node.js) reports each finding's {@code filePath} resolved through any
     * symlinks in its path (e.g. macOS's {@code /var} &rarr; {@code /private/var}),
     * while {@code sourceRoot} is whatever path it was constructed with — often
     * un-resolved, as with {@link Files#createTempDirectory}. Relativizing an
     * un-resolved root against a resolved reported path are treated as unrelated
     * absolute paths by {@link Path#relativize}, producing a nonsense {@code ../..}
     * escape instead of a clean relative path (reproduced by a real ESLint run
     * against a temp dir in {@code ESLintAnalysisProviderTest}). Resolving both
     * sides to their real path first keeps them comparable regardless of symlinks.
     */
    private String relativePathOf(String reportedPath, Path sourceRoot) {
        if (reportedPath.isBlank()) {
            return reportedPath;
        }
        Path reported = Path.of(reportedPath);
        if (!reported.isAbsolute()) {
            return reportedPath;
        }
        return realPathOrSelf(sourceRoot).relativize(realPathOrSelf(reported)).toString();
    }

    private Path realPathOrSelf(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            return path.toAbsolutePath().normalize();
        }
    }

    /** Package-visible for a dedicated, exhaustive unit test — see ESLintAnalysisProviderTest. */
    static ExternalFindingSeverity severityOf(int eslintSeverity) {
        ExternalFindingSeverity severity = SEVERITY_BY_ESLINT_SEVERITY.get(eslintSeverity);
        if (severity == null) {
            throw new IllegalStateException("no documented severity mapping for ESLint severity: " + eslintSeverity);
        }
        return severity;
    }
}
