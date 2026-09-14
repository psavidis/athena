package com.athena.repository;

import java.util.List;

/**
 * The full imported content of a Pull Request: metadata, base/head
 * revisions, commits, and changed files (each with status and, where
 * available, its textual diff). Provider-independent.
 */
public record ImportedPullRequest(
        int number,
        String title,
        String author,
        String baseRevision,
        String headRevision,
        List<Commit> commits,
        List<ChangedFile> changedFiles) {
}
