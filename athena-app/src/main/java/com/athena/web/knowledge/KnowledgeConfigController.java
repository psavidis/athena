package com.athena.web.knowledge;

import com.athena.knowledge.KnowledgeProviderResolver;
import com.athena.knowledge.KnowledgeProviderStore;
import com.athena.knowledge.ObsidianVaultConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Configuring the Knowledge Provider (ticket #118) — the same "External
 * Systems / Integrations" area GitHub's Pull Request provider is
 * configured through ({@code com.athena.web.github.GitHubConnectController}).
 * Absence of a configured provider is a fully-supported, error-free state
 * ({@link #status()} simply reports {@code configured: false}) — never a
 * blocking gate for the rest of Athena.
 */
@RestController
public class KnowledgeConfigController {

    private final KnowledgeProviderStore store;
    private final KnowledgeProviderResolver resolver;

    @Autowired
    public KnowledgeConfigController() {
        this(new KnowledgeProviderStore());
    }

    KnowledgeConfigController(KnowledgeProviderStore store) {
        this.store = store;
        this.resolver = new KnowledgeProviderResolver(store);
    }

    @GetMapping("/api/knowledge/status")
    public KnowledgeStatusResponse status() {
        return statusResponse();
    }

    @PostMapping("/api/knowledge/obsidian")
    public KnowledgeStatusResponse connectObsidian(@RequestBody ConnectObsidianRequest request) {
        String vaultPath = request.vaultPath() == null ? "" : request.vaultPath().strip();
        if (vaultPath.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "vaultPath must not be blank");
        }
        if (!Files.isDirectory(Path.of(vaultPath))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "vaultPath does not exist: " + vaultPath);
        }
        store.saveObsidianConfig(new ObsidianVaultConfig(vaultPath, true));
        return statusResponse();
    }

    @DeleteMapping("/api/knowledge/obsidian")
    public KnowledgeStatusResponse disconnectObsidian() {
        store.clearObsidianConfig();
        return statusResponse();
    }

    private KnowledgeStatusResponse statusResponse() {
        return resolver.resolve()
                .map(configured -> new KnowledgeStatusResponse(true, configured.provider().providerId(), configured.vaultPath()))
                .orElseGet(() -> new KnowledgeStatusResponse(false, null, null));
    }

    public record ConnectObsidianRequest(String vaultPath) {
    }

    public record KnowledgeStatusResponse(boolean configured, String providerId, String vaultPath) {
    }
}
