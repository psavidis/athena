package com.athena.analysis.spi;

import java.nio.file.Path;
import java.util.List;

/**
 * A pluggable external static-analysis tool (ticket #114/#148): the seam
 * between Athena and a linter/analyzer like SonarJava or ESLint. A
 * provider answers "what did this external analyzer find?" — Athena's own
 * semantic analysis remains solely responsible for "what does this finding
 * mean in the context of this change?" (see the epic's own framing).
 *
 * <p>Multiple providers can be configured and run independently: one
 * provider's {@link #analyze} failing must never prevent another
 * provider's run, or Athena's own analysis, from completing — callers get
 * a failed {@link ProviderRunResult} for that provider, never a thrown
 * exception escaping this method. Providers are analyzer-agnostic and
 * language-agnostic from Athena's core's point of view: adding or removing
 * one is implementing/removing this interface, not a change to Athena's
 * core analysis engine.
 *
 * <p>Provider-specific concerns (tool invocation, process/environment
 * handling, output parsing, converting the tool's own result shape into
 * {@link ExternalFinding}) belong entirely inside the implementation —
 * Athena's core consumes only this interface and its provider-independent
 * return types.
 */
public interface ExternalAnalysisProvider {

    /** A short, stable identifier for this provider, e.g. {@code "sonarjava"}. */
    String providerId();

    /** The language this provider analyzes, e.g. {@code "java"}. */
    String language();

    /**
     * Runs this provider against the given source tree with the given
     * configuration. Never throws for an ordinary analysis failure (the
     * tool isn't installed, times out, produces no output, etc.) — such
     * failures are reported via a failed {@link ProviderRunResult}, so a
     * caller running several providers can isolate one from another
     * without a try/catch per provider.
     */
    ProviderRunResult analyze(Path sourceRoot, List<Path> changedFiles, ProviderConfiguration configuration);
}
