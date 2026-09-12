package com.athena.github;

/**
 * Raised when a requested GitHub resource (repository, Pull Request, etc.)
 * does not exist or is not accessible to the authenticated account.
 */
public class GitHubResourceNotFoundException extends RuntimeException {

    public GitHubResourceNotFoundException(String message) {
        super(message);
    }

    public GitHubResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
