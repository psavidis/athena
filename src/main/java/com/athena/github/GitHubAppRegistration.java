package com.athena.github;

/**
 * A GitHub App registered for this Athena installation (App ID, slug, and
 * the RSA private key GitHub minted for it). Created once via the App
 * manifest flow ({@link GitHubAppClient}) and persisted by
 * {@link GitHubAppStore} so subsequent launches reuse the same App instead
 * of registering a new one every time.
 */
public record GitHubAppRegistration(String appId, String appSlug, String privateKeyPem, String webhookSecret) {
}
