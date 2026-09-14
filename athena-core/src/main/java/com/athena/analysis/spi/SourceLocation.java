package com.athena.analysis.spi;

import java.util.Objects;
import java.util.Optional;

/**
 * A location an {@link ExternalFinding} (or one of its related locations,
 * ticket #114/#148) points at: a file, optionally narrowed to a line range.
 * Provider-independent — line numbers are 1-based regardless of what a
 * given provider's own tool reports.
 */
public final class SourceLocation {

    private final String filePath;
    private final Integer startLine;
    private final Integer endLine;

    private SourceLocation(String filePath, Integer startLine, Integer endLine) {
        this.filePath = filePath;
        this.startLine = startLine;
        this.endLine = endLine;
    }

    /** A whole-file location, with no specific line range. */
    public static SourceLocation ofFile(String filePath) {
        Objects.requireNonNull(filePath, "filePath");
        if (filePath.isBlank()) {
            throw new IllegalArgumentException("filePath must not be blank");
        }
        return new SourceLocation(filePath, null, null);
    }

    /** A single line within a file. */
    public static SourceLocation ofLine(String filePath, int line) {
        return ofRange(filePath, line, line);
    }

    /** A line range within a file (inclusive), e.g. spanning a multi-line statement. */
    public static SourceLocation ofRange(String filePath, int startLine, int endLine) {
        Objects.requireNonNull(filePath, "filePath");
        if (filePath.isBlank()) {
            throw new IllegalArgumentException("filePath must not be blank");
        }
        if (startLine <= 0 || endLine <= 0) {
            throw new IllegalArgumentException("line numbers must be positive: " + startLine + ".." + endLine);
        }
        if (endLine < startLine) {
            throw new IllegalArgumentException("endLine must not precede startLine: " + startLine + ".." + endLine);
        }
        return new SourceLocation(filePath, startLine, endLine);
    }

    public String filePath() {
        return filePath;
    }

    /** This location's first line, if it names a specific line range. Empty for a whole-file location. */
    public Optional<Integer> startLine() {
        return Optional.ofNullable(startLine);
    }

    /** This location's last line, if it names a specific line range. Empty for a whole-file location. */
    public Optional<Integer> endLine() {
        return Optional.ofNullable(endLine);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SourceLocation other)) return false;
        return filePath.equals(other.filePath)
                && Objects.equals(startLine, other.startLine)
                && Objects.equals(endLine, other.endLine);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filePath, startLine, endLine);
    }

    @Override
    public String toString() {
        if (startLine == null) {
            return filePath;
        }
        return startLine.equals(endLine) ? filePath + ":" + startLine : filePath + ":" + startLine + "-" + endLine;
    }
}
