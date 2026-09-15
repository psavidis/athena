package com.athena.memory;

import java.util.List;
import java.util.regex.Pattern;

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
                .filter(entry -> fileNames.stream().anyMatch(name -> mentions(entry.fact(), name)))
                .toList();
    }

    /**
     * Whether {@code fact} mentions {@code fileName} as a whole file name, not merely as a
     * substring — a plain {@code contains} would wrongly treat "Handler.java" as mentioned by a
     * fact about "RequestHandler.java", since the latter ends with the former's exact text.
     */
    private static boolean mentions(String fact, String fileName) {
        return Pattern.compile("\\b" + Pattern.quote(fileName) + "\\b").matcher(fact).find();
    }

    private static String fileName(String path) {
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }
}
