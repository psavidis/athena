package com.athena.github;

/**
 * The outcome of attempting to connect to GitHub with a given token.
 */
public final class GitHubConnectionResult {

    private final boolean success;
    private final String authenticatedUsername;
    private final String errorMessage;

    private GitHubConnectionResult(boolean success, String authenticatedUsername, String errorMessage) {
        this.success = success;
        this.authenticatedUsername = authenticatedUsername;
        this.errorMessage = errorMessage;
    }

    public static GitHubConnectionResult success(String authenticatedUsername) {
        return new GitHubConnectionResult(true, authenticatedUsername, null);
    }

    public static GitHubConnectionResult failure(String errorMessage) {
        return new GitHubConnectionResult(false, null, errorMessage);
    }

    public boolean isSuccess() {
        return success;
    }

    public String authenticatedUsername() {
        return authenticatedUsername;
    }

    public String errorMessage() {
        return errorMessage;
    }
}
