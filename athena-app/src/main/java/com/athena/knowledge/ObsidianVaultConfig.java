package com.athena.knowledge;

/**
 * The persisted Obsidian Knowledge Provider configuration (ticket #118):
 * the vault's directory path, and whether it's currently enabled.
 * Disconnecting clears this entirely (see {@link KnowledgeProviderStore})
 * rather than merely flipping {@code enabled} to false, so a stale vault
 * path never lingers once the user disconnects it.
 */
public record ObsidianVaultConfig(String vaultPath, boolean enabled) {
}
