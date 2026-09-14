package com.athena.repository;

/**
 * A lightweight summary of a Pull Request — enough to list and select one,
 * before importing its full content. Provider-independent.
 */
public record PullRequestSummary(int number, String title) {
}
