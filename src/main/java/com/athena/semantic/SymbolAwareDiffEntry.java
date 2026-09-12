package com.athena.semantic;

/**
 * The second fallback level (epic #4 §45): when full Change detection isn't
 * possible for a file but the file itself is still known, associate its
 * raw diff with the enclosing file even without a classified Change.
 */
public final class SymbolAwareDiffEntry {

    private final String filePath;
    private final String reason;

    SymbolAwareDiffEntry(String filePath, String reason) {
        this.filePath = filePath;
        this.reason = reason;
    }

    public String filePath() {
        return filePath;
    }

    /** Why this file couldn't reach full semantic classification. */
    public String reason() {
        return reason;
    }

    @Override
    public String toString() {
        return filePath + ": " + reason;
    }
}
