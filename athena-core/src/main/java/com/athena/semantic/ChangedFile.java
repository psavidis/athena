package com.athena.semantic;

/**
 * One file that differs between the two revisions — any file type, not only the ones a
 * language plugin understands (ticket #260).
 *
 * @param path         path relative to the checkout root
 * @param linesChanged added plus removed lines
 * @param hunkCount    contiguous runs of changed lines
 * @param unifiedDiff  the line-based diff between the two revisions (empty for identical
 *                     text; "Binary file changed" when either side isn't text)
 */
public record ChangedFile(String path, FileChangeStatus status, int linesChanged, int hunkCount, String unifiedDiff) {
}
