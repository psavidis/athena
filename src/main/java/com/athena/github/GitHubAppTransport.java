package com.athena.github;

/**
 * The seam between {@link GitHubAppClient} and the actual GitHub API calls
 * needed to complete the App manifest flow and mint installation access
 * tokens. Mirrors {@link GitHubTransport}'s role for the PAT flow: the
 * production implementation talks to the real GitHub REST API, tests
 * substitute a fake.
 */
interface GitHubAppTransport {

    /**
     * Exchanges the temporary manifest-flow code for the newly-created
     * App's credentials.
     *
     * @throws GitHubAuthenticationException if GitHub rejects the code
     */
    GitHubAppRegistration convertManifest(String code);

    /**
     * Mints a short-lived installation access token, authenticating as the
     * App via the given signed JWT.
     *
     * @throws GitHubAuthenticationException if the JWT or installation ID
     *         is rejected
     */
    String createInstallationAccessToken(String appJwt, long installationId);

    /**
     * Looks up the account (user or org login) an installation belongs to.
     *
     * @throws GitHubAuthenticationException if the JWT or installation ID
     *         is rejected
     */
    String fetchInstallationAccountLogin(String appJwt, long installationId);
}
