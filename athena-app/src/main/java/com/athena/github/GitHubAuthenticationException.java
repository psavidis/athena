package com.athena.github;

/**
 * Raised when GitHub rejects the credentials used to authenticate a request
 * (e.g. an invalid, expired, or revoked Personal Access Token).
 */
public class GitHubAuthenticationException extends RuntimeException {

    public GitHubAuthenticationException(String message) {
        super(message);
    }

    public GitHubAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
