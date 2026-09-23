package com.athena.semantic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * How much of a change Athena's analysis represents: every changed file, which of them no
 * Change cites, and why — so a partial analysis never looks complete (ticket #260).
 */
public final class RepresentationCoverage {

    private static final RepresentationCoverage EMPTY = new RepresentationCoverage(List.of(), List.of());

    private final List<ChangedFile> changedFiles;
    private final List<UnrepresentedFile> unrepresentedFiles;

    private RepresentationCoverage(List<ChangedFile> changedFiles, List<UnrepresentedFile> unrepresentedFiles) {
        this.changedFiles = List.copyOf(changedFiles);
        this.unrepresentedFiles = List.copyOf(unrepresentedFiles);
    }

    /**
     * @param isSupported whether a language plugin understands a file, by relative path
     */
    public static RepresentationCoverage of(List<ChangedFile> changedFiles, List<Change> changes,
                                            List<SymbolAwareDiffEntry> degradedEntries, Predicate<String> isSupported) {
        Set<String> represented = new HashSet<>();
        for (Change change : changes) {
            change.matchedOccurrences().forEach(occurrence -> represented.addAll(occurrence.filesTouched()));
            change.exceptions().forEach(occurrence -> represented.addAll(occurrence.filesTouched()));
        }
        Set<String> unparseable = new HashSet<>();
        degradedEntries.forEach(entry -> unparseable.add(entry.filePath()));

        List<UnrepresentedFile> unrepresented = new ArrayList<>();
        for (ChangedFile file : changedFiles) {
            if (represented.contains(file.path())) {
                continue;
            }
            UnrepresentedReason reason = !isSupported.test(file.path()) ? UnrepresentedReason.UNSUPPORTED_FILE_TYPE
                    : unparseable.contains(file.path()) ? UnrepresentedReason.PARSE_FAILED
                    : UnrepresentedReason.NO_SEMANTIC_CHANGE;
            unrepresented.add(new UnrepresentedFile(file, reason));
        }
        return new RepresentationCoverage(changedFiles, unrepresented);
    }

    public static RepresentationCoverage empty() {
        return EMPTY;
    }

    public List<ChangedFile> changedFiles() {
        return changedFiles;
    }

    public int representedFileCount() {
        return changedFiles.size() - unrepresentedFiles.size();
    }

    public List<UnrepresentedFile> unrepresentedFiles() {
        return unrepresentedFiles;
    }

    /** The changed file at {@code path}, if the change touched it. */
    public Optional<ChangedFile> changedFile(String path) {
        return changedFiles.stream().filter(file -> file.path().equals(path)).findFirst();
    }
}
