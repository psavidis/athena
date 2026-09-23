package com.athena.web.ai;

import com.athena.ai.AiKeyStore;
import com.athena.ai.ClaudeCliModuleNarrativeProvider;
import com.athena.ai.ClaudeModuleNarrativeProvider;
import com.athena.ai.ModuleNarrativeProvider;
import com.athena.semantic.ModuleGroup;
import com.athena.semantic.ModuleGrouper;
import com.athena.web.ChangeKey;
import com.athena.web.WebSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * "What changed and why" — the module-level grouping and AI narrative
 * layered above the Change Map, so a reviewer sees the business purpose
 * behind a cluster of Changes spanning many classes/files before diving
 * into individual rows. {@link #modules()} is free/deterministic (pure
 * {@link ModuleGrouper} structural grouping); {@link #narrativeFor} is the
 * billed, on-demand AI call, cached per module for the rest of the
 * selection (see {@link WebSession.SelectedPullRequest#moduleNarrative}).
 *
 * <p>Provider resolution mirrors {@link AiAnalysisController}'s own
 * CLI-first-then-API-key fallback exactly, but against
 * {@link ModuleNarrativeProvider} instead of {@link com.athena.ai.AiProvider}
 * — narrating a module and finding what a reviewer missed are different
 * questions (see {@link ModuleNarrativeProvider}'s own doc comment), so they
 * don't share a provider type, even though they share how a provider is found.
 */
@RestController
public class ModuleNarrativeController {

    private final WebSession session;
    private final AiKeyStore keyStore;
    private final ModuleNarrativeProvider fixedProvider;

    // Distinguishes "no test seam given, resolve for real" (public constructor) from
    // "test seam given, trust it exactly as passed" (package-private constructor) — a
    // fixedProvider of null from the test seam must mean "nothing configured," not fall
    // through to real CLI/API-key detection, which would make an "unconfigured" test
    // depend on whether this machine happens to have the claude CLI on PATH.
    private final boolean usesFixedProvider;

    @Autowired
    public ModuleNarrativeController(WebSession session) {
        this.session = session;
        this.keyStore = new AiKeyStore();
        this.fixedProvider = null;
        this.usesFixedProvider = false;
    }

    /** Test seam: a fake {@link ModuleNarrativeProvider} (or null, for "unconfigured") stands in for the real network boundary. */
    ModuleNarrativeController(WebSession session, ModuleNarrativeProvider fixedProvider) {
        this.session = session;
        this.keyStore = new AiKeyStore();
        this.fixedProvider = fixedProvider;
        this.usesFixedProvider = true;
    }

    @GetMapping("/api/review/modules")
    public List<ModuleNarrativeResponse> modules() {
        WebSession.SelectedPullRequest selection = requireSelection();
        List<ModuleGroup> groups = selection.diff().moduleGroups();
        return groups.stream()
                .map(group -> new ModuleNarrativeResponse(group.moduleName(), changeKeysOf(group), null))
                .toList();
    }

    @GetMapping("/api/review/modules/{moduleName}/narrative")
    public ModuleNarrativeResponse narrativeFor(@PathVariable String moduleName) {
        ModuleNarrativeProvider provider = requireProviderConfigured();
        WebSession.SelectedPullRequest selection = requireSelection();
        ModuleGroup group = selection.diff().moduleGroups().stream()
                .filter(g -> g.moduleName().equals(moduleName))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Module not found"));

        String narrative = selection.moduleNarrative(moduleName, name -> provider.explain(group));
        return new ModuleNarrativeResponse(group.moduleName(), changeKeysOf(group), narrative);
    }

    private List<String> changeKeysOf(ModuleGroup group) {
        return group.changes().stream().map(ChangeKey::encode).toList();
    }

    /** Resolved fresh on every call so a key saved via "Connect Claude" takes effect with no restart. */
    private ModuleNarrativeProvider provider() {
        if (usesFixedProvider) {
            return fixedProvider;
        }
        Optional<String> claudeExecutable = findClaudeExecutable();
        if (claudeExecutable.isPresent()) {
            return new ClaudeCliModuleNarrativeProvider(claudeExecutable.get());
        }
        return apiKey().map(ModuleNarrativeController::buildApiProvider).orElse(null);
    }

    private Optional<String> findClaudeExecutable() {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) {
            return Optional.empty();
        }
        for (String dir : pathEnv.split(File.pathSeparator)) {
            File candidate = new File(dir, "claude");
            if (candidate.isFile() && candidate.canExecute()) {
                return Optional.of(candidate.getAbsolutePath());
            }
        }
        return Optional.empty();
    }

    private Optional<String> apiKey() {
        String envKey = System.getenv("ANTHROPIC_API_KEY");
        if (envKey != null && !envKey.isBlank()) {
            return Optional.of(envKey);
        }
        return keyStore.loadApiKey();
    }

    private static ModuleNarrativeProvider buildApiProvider(String apiKey) {
        return new ClaudeModuleNarrativeProvider(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), apiKey);
    }

    private ModuleNarrativeProvider requireProviderConfigured() {
        ModuleNarrativeProvider provider = provider();
        if (provider == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AI analysis is not configured: connect Claude or set the ANTHROPIC_API_KEY environment variable");
        }
        return provider;
    }

    private WebSession.SelectedPullRequest requireSelection() {
        session.gitHubToken()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not connected to GitHub"));
        return session.selectedPullRequest()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No PR selected"));
    }
}
