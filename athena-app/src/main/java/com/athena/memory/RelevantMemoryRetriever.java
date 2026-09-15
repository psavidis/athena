package com.athena.memory;

import java.util.List;

/**
 * Scopes a project's memory (ticket #169) to what's relevant to a review's
 * changed files (ticket #173), rather than handing a review the entire
 * memory store — mirrors {@code com.athena.knowledge.spi.KnowledgeQuery}'s
 * role for project knowledge (ticket #118). A fact is relevant when it
 * mentions one of the changed files by name; matching is by file name
 * (the last path segment) rather than full path, since the learners under
 * {@code com.athena.memory} (tickets #170/#171/#172) record facts using
 * bare file names, not full repository-relative paths.
 */
public final class RelevantMemoryRetriever {

    private RelevantMemoryRetriever() {
    }

    public static List<MemoryEntry> retrieve(ProjectMemoryStore store, List<String> changedFiles) {
        List<String> fileNames = changedFiles.stream().map(RelevantMemoryRetriever::fileName).toList();
        return store.entries().stream()
                .filter(entry -> fileNames.stream().anyMatch(name -> entry.fact().contains(name)))
                .toList();
    }

    private static String fileName(String path) {
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }
}
