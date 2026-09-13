package com.athena.github;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.security.SecureRandom;

/**
 * The App manifest Athena submits to GitHub's manifest-flow creation page
 * (https://docs.github.com/en/apps/sharing-github-apps/registering-a-github-app-from-a-manifest).
 * Requests only the read-only permissions the code-review MVP needs.
 */
final class GitHubAppManifest {

    private static final String SUFFIX_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private GitHubAppManifest() {
    }

    static String json(String backendBaseUrl) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode permissions = mapper.createObjectNode()
                .put("metadata", "read")
                .put("contents", "read")
                .put("pull_requests", "read");

        ObjectNode manifest = mapper.createObjectNode();
        // GitHub App names are unique across all of GitHub, not just this account, so a random
        // suffix is used rather than a timestamp — a timestamp can repeat across quick retries
        // (e.g. a double-click, or a browser resubmitting a cached redirect URL).
        manifest.put("name", "Athena Code Review " + randomSuffix());
        manifest.put("url", backendBaseUrl);
        manifest.put("redirect_url", backendBaseUrl + "/api/github/app-manifest-callback");
        // setup_url is where GitHub sends the browser after an install/update (its installation
        // callback); without it, GitHub falls back to the App's homepage instead of back to us.
        manifest.put("setup_url", backendBaseUrl + "/api/github/installation-callback");
        manifest.put("setup_on_update", true);
        manifest.put("public", false);
        manifest.set("default_permissions", permissions);
        manifest.putArray("default_events");
        // Athena doesn't use webhooks (it only makes outbound calls to GitHub on demand).
        // hook_attributes is deliberately omitted rather than set with active=false: GitHub
        // requires hook_attributes.url whenever hook_attributes is present at all, even to turn
        // webhooks off, and localhost isn't an acceptable webhook URL — omitting the field
        // entirely is what actually leaves webhooks inactive.
        return manifest.toString();
    }

    private static String randomSuffix() {
        StringBuilder suffix = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            suffix.append(SUFFIX_CHARS.charAt(RANDOM.nextInt(SUFFIX_CHARS.length())));
        }
        return suffix.toString();
    }
}
