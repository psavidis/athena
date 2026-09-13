package com.athena.github;

/**
 * A completed installation of Athena's GitHub App onto a specific GitHub
 * account/org, as reported by GitHub's installation callback.
 */
public record GitHubAppInstallationRecord(long installationId, String accountLogin) {
}
