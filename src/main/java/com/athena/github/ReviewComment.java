package com.athena.github;

/**
 * An existing review comment left on a Pull Request.
 */
public record ReviewComment(String author, String body, String path) {
}
