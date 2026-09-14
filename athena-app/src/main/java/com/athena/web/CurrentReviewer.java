package com.athena.web;

/**
 * Who is posting a comment (ticket #134). {@link GitHubAccess} is the real
 * implementation, resolving the connected GitHub App installation's account
 * login — extracted as its own narrow interface so a test can supply a
 * fixed reviewer identity without constructing a real, network-capable
 * {@link GitHubAccess} (a genuine external boundary per this codebase's
 * Detroit-school testing rule).
 */
public interface CurrentReviewer {

    /** The name to attribute a newly-posted comment to. Never null or blank. */
    String login();
}
