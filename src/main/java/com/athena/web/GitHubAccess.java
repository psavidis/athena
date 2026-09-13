package com.athena.web;

import com.athena.github.GitHubAppClient;
import com.athena.github.GitHubAppInstallationRecord;
import com.athena.github.GitHubAppRegistration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Application-wide (not per-session) wrapper around {@link GitHubAppClient}:
 * the GitHub App registration and its installation are shared across every
 * browser session — Athena connects one GitHub App installation at a time,
 * not one per session — whereas {@link WebSession} is per-browser-session.
 */
@Component
public class GitHubAccess {

    private final GitHubAppClient appClient;

    public GitHubAccess(@Value("${athena.github.app.backend-base-url}") String backendBaseUrl) {
        this.appClient = new GitHubAppClient(backendBaseUrl);
    }

    public boolean isConnected() {
        return appClient.registration().isPresent() && appClient.installation().isPresent();
    }

    public String connectUrl() {
        return appClient.connectUrl();
    }

    public String manifestJson() {
        return appClient.manifestJson();
    }

    public GitHubAppRegistration completeManifest(String code) {
        return appClient.completeManifest(code);
    }

    public void recordInstallation(long installationId) {
        appClient.recordInstallation(installationId);
    }

    public String currentAccessToken() {
        return appClient.currentInstallationAccessToken();
    }

    public String connectedAccountLogin() {
        return appClient.installation().map(GitHubAppInstallationRecord::accountLogin).orElse(null);
    }
}
