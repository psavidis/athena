package com.athena.github;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;

/**
 * Backend abstraction for the GitHub App installation flow used by
 * "Connect GitHub" in the web UI. Kept independent of any web-framework
 * type (no Spring imports) so the future CLI device-flow authentication
 * can reuse it for the same underlying GitHub API/repository-review
 * services — see CLAUDE.md's GitHub Integration notes.
 *
 * <p>Athena is ephemeral (launched per session, no persistent server), so
 * this client leans on {@link GitHubAppStore} to remember the registered
 * App and its installation across launches: the manifest flow to create
 * the App itself only needs to run once, ever, per machine.
 *
 * <p>Once an installation exists, {@link #currentInstallationAccessToken()}
 * mints a fresh short-lived installation access token on demand and hands
 * it to the existing PAT-shaped {@link GitHubTransport}-based classes
 * ({@link GitHubRepositoryBrowser}, etc.) unchanged — an installation
 * token is just a bearer token from their point of view.
 */
public class GitHubAppClient {

    private final GitHubAppStore store;
    private final GitHubAppTransport transport;
    private final String backendBaseUrl;

    public GitHubAppClient(String backendBaseUrl) {
        this(backendBaseUrl, new GitHubAppStore(), new HttpGitHubAppTransport(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build()));
    }

    GitHubAppClient(String backendBaseUrl, GitHubAppStore store, GitHubAppTransport transport) {
        this.backendBaseUrl = backendBaseUrl;
        this.store = store;
        this.transport = transport;
    }

    public Optional<GitHubAppRegistration> registration() {
        return store.loadRegistration();
    }

    public Optional<GitHubAppInstallationRecord> installation() {
        return store.loadInstallation();
    }

    /**
     * The URL the frontend should redirect the browser to in order to
     * connect GitHub: the App's installation page directly if an App is
     * already registered, otherwise a backend page
     * ({@link #MANIFEST_FORM_PATH}) that auto-submits the App manifest to
     * GitHub — GitHub's manifest flow only reads the manifest from a POSTed
     * form field, not a URL query parameter, so a bare github.com link
     * can't carry it.
     */
    public String connectUrl() {
        return registration()
                .map(this::installationUrl)
                .orElse(backendBaseUrl + MANIFEST_FORM_PATH);
    }

    /** Relative path of the auto-submitting manifest form page, served by the web layer. */
    public static final String MANIFEST_FORM_PATH = "/api/github/manifest-form";

    /** The manifest JSON the {@code MANIFEST_FORM_PATH} page should POST to GitHub. */
    public String manifestJson() {
        return GitHubAppManifest.json(backendBaseUrl);
    }

    private String installationUrl(GitHubAppRegistration registration) {
        return "https://github.com/apps/" + registration.appSlug() + "/installations/new";
    }

    /**
     * Completes the manifest flow: exchanges the temporary code GitHub
     * redirected back with for the newly-created App's credentials, and
     * persists them.
     */
    public GitHubAppRegistration completeManifest(String code) {
        GitHubAppRegistration registration = transport.convertManifest(code);
        store.saveRegistration(registration);
        return registration;
    }

    /**
     * Looks up and persists the installation GitHub's installation callback
     * reported by ID (the callback's query string carries only the ID, not
     * the account it belongs to).
     *
     * @throws IllegalStateException if no App is registered yet
     */
    public GitHubAppInstallationRecord recordInstallation(long installationId) {
        GitHubAppRegistration registration = registration()
                .orElseThrow(() -> new IllegalStateException("No GitHub App registered yet"));
        String appJwt = GitHubAppJwtSigner.sign(registration.appId(), registration.privateKeyPem());
        String accountLogin = transport.fetchInstallationAccountLogin(appJwt, installationId);

        GitHubAppInstallationRecord installationRecord = new GitHubAppInstallationRecord(installationId, accountLogin);
        store.saveInstallation(installationRecord);
        return installationRecord;
    }

    /**
     * Mints a fresh installation access token for the currently-connected
     * installation.
     *
     * @throws IllegalStateException if no App/installation is connected yet
     */
    public String currentInstallationAccessToken() {
        GitHubAppRegistration registration = registration()
                .orElseThrow(() -> new IllegalStateException("No GitHub App registered yet"));
        GitHubAppInstallationRecord installationRecord = installation()
                .orElseThrow(() -> new IllegalStateException("GitHub App is not installed yet"));

        String appJwt = GitHubAppJwtSigner.sign(registration.appId(), registration.privateKeyPem());
        return transport.createInstallationAccessToken(appJwt, installationRecord.installationId());
    }
}
