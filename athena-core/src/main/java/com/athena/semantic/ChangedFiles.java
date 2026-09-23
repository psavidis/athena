package com.athena.semantic;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Aggregate operations for {@link ChangedFile}: every file that differs between two
 * checkouts (ticket #260). A checkout's own {@code .git} directory is never part of it.
 */
public final class ChangedFiles {

    private static final String BINARY = "Binary file changed";

    private ChangedFiles() {
    }

    /** The files added, removed or modified between {@code baseRoot} and {@code headRoot}, sorted by path. */
    public static List<ChangedFile> between(Path baseRoot, Path headRoot) {
        Set<String> paths = new TreeSet<>(relativeFiles(baseRoot));
        paths.addAll(relativeFiles(headRoot));
        List<ChangedFile> changed = new ArrayList<>();
        for (String path : paths) {
            Optional<byte[]> base = read(baseRoot.resolve(path));
            Optional<byte[]> head = read(headRoot.resolve(path));
            if (base.isPresent() && head.isPresent() && Arrays.equals(base.get(), head.get())) {
                continue;
            }
            FileChangeStatus status = base.isEmpty() ? FileChangeStatus.ADDED
                    : head.isEmpty() ? FileChangeStatus.REMOVED : FileChangeStatus.MODIFIED;
            changed.add(changedFile(path, status, base.orElse(new byte[0]), head.orElse(new byte[0])));
        }
        return changed;
    }

    private static ChangedFile changedFile(String path, FileChangeStatus status, byte[] base, byte[] head) {
        Optional<String> baseText = text(base);
        Optional<String> headText = text(head);
        if (baseText.isEmpty() || headText.isEmpty()) {
            return new ChangedFile(path, status, 0, 0, BINARY);
        }
        String diff = UnifiedDiff.of(baseText.get(), headText.get());
        int linesChanged = 0;
        int hunks = 0;
        boolean inHunk = false;
        for (String line : diff.lines().toList()) {
            boolean changedLine = line.startsWith("+") || line.startsWith("-");
            if (changedLine) {
                linesChanged++;
                if (!inHunk) {
                    hunks++;
                }
            }
            inHunk = changedLine;
        }
        return new ChangedFile(path, status, linesChanged, hunks, diff);
    }

    private static Optional<String> text(byte[] bytes) {
        try {
            return Optional.of(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString());
        } catch (CharacterCodingException e) {
            return Optional.empty();
        }
    }

    private static Optional<byte[]> read(Path file) {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Set<String> relativeFiles(Path root) {
        if (!Files.isDirectory(root)) {
            return Set.of();
        }
        Set<String> files = new TreeSet<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .map(root::relativize)
                    .filter(relative -> !relative.startsWith(".git"))
                    .forEach(relative -> files.add(relative.toString()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return files;
    }
}
