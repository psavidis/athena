package com.athena.repository;

/**
 * A single commit on a Pull Request. Provider-independent.
 */
public record Commit(String sha, String message) {
}
