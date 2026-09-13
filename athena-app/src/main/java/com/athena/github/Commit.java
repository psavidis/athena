package com.athena.github;

/**
 * A single commit on a Pull Request.
 */
public record Commit(String sha, String message) {
}
