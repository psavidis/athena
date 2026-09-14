package com.athena.web.github;

import com.athena.github.GitHubAppClient;
import com.athena.web.GitHubAccess;
import com.athena.web.WebSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Drives "Connect GitHub" in the web UI via a GitHub App installation
 * (replacing the earlier Personal Access Token entry, ticket #73): the
 * frontend asks where to send the browser, GitHub redirects back to the two
 * callbacks below once the App is created and then installed, and from
 * then on this session holds a short-lived installation access token
 * instead of a long-lived user-supplied secret.
 */
@RestController
public class GitHubConnectController {

    private final WebSession session;
    private final GitHubAccess gitHubAccess;
    private final String frontendBaseUrl;

    public GitHubConnectController(WebSession session, GitHubAccess gitHubAccess,
                                    @Value("${athena.github.app.frontend-base-url}") String frontendBaseUrl) {
        this.session = session;
        this.gitHubAccess = gitHubAccess;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    /** Where the frontend should redirect the browser to start (or continue) connecting GitHub. */
    @GetMapping("/api/github/connect-url")
    public ConnectUrlResponse connectUrl() {
        return new ConnectUrlResponse(gitHubAccess.connectUrl());
    }

    /** Whether an installation is already connected, so the frontend can skip straight past "Connect GitHub". */
    @GetMapping("/api/github/status")
    public GitHubStatusResponse status() {
        boolean connected = gitHubAccess.isConnected();
        if (connected) {
            session.connect(gitHubAccess.currentAccessToken());
        }
        return new GitHubStatusResponse(
                connected,
                connected ? gitHubAccess.connectedAccountLogin() : null,
                connected ? gitHubAccess.connectedInstallationConfigureUrl() : null);
    }

    /**
     * Auto-submitting HTML page that POSTs the App manifest to GitHub's
     * manifest-flow creation endpoint. GitHub only reads the manifest from
     * a POSTed form field (not a URL query parameter), so {@link #connectUrl}
     * points here rather than linking to github.com directly when no App is
     * registered yet.
     */
    @GetMapping(value = GitHubAppClient.MANIFEST_FORM_PATH, produces = MediaType.TEXT_HTML_VALUE)
    public String manifestForm() {
        String manifestJson = gitHubAccess.manifestJson();
        String escaped = manifestJson
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
        return "<!DOCTYPE html><html><body onload=\"document.forms[0].submit()\">"
                + "<form method=\"post\" action=\"https://github.com/settings/apps/new\">"
                + "<input type=\"hidden\" name=\"manifest\" value=\"" + escaped + "\">"
                + "</form>Redirecting to GitHub…</body></html>";
    }

    /**
     * GitHub redirects here once the App manifest has been converted into a
     * real App. The temporary {@code code} is exchanged for the App's
     * credentials, which are persisted, then the browser is sent on to
     * install the newly-created App.
     */
    @GetMapping("/api/github/app-manifest-callback")
    public ResponseEntity<Void> appManifestCallback(@RequestParam String code) {
        gitHubAccess.completeManifest(code);
        return redirectTo(gitHubAccess.connectUrl());
    }

    /**
     * GitHub redirects here once the user has installed (or updated) the
     * App on an account/org. Persists the installation and sends the
     * browser back to the frontend to continue.
     */
    @GetMapping("/api/github/installation-callback")
    public ResponseEntity<Void> installationCallback(@RequestParam("installation_id") long installationId,
                                                       @RequestParam(value = "setup_action", required = false)
                                                       String setupAction) {
        if ("install".equals(setupAction) || "update".equals(setupAction)) {
            gitHubAccess.recordInstallation(installationId);
            session.connect(gitHubAccess.currentAccessToken());
        }
        return redirectTo(frontendBaseUrl);
    }

    private ResponseEntity<Void> redirectTo(String url) {
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, URI.create(url).toString()).build();
    }

    public record ConnectUrlResponse(String url) {
    }

    public record GitHubStatusResponse(boolean connected, String accountLogin, String installationConfigureUrl) {
    }
}
