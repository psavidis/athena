package com.athena.git;

/**
 * Raised when checking out a git revision fails — the repository/revision
 * doesn't exist or isn't accessible, the local `git` binary isn't
 * available, or the checkout otherwise couldn't be completed.
 */
public class GitCheckoutException extends RuntimeException {

    public GitCheckoutException(String message) {
        super(message);
    }

    public GitCheckoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
