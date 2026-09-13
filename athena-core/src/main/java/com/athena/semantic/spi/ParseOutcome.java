package com.athena.semantic.spi;

/**
 * Whether a {@link LanguagePlugin} could parse one source file, without exposing
 * that language's own AST/parser types to core — {@link PrAnalyzer}'s graceful-
 * degradation fallback chain (epic #4 §45) only ever needs to know success/failure
 * and a human-readable reason, never the parsed tree itself.
 */
public final class ParseOutcome {

    private final boolean successful;
    private final String errorMessage;

    private ParseOutcome(boolean successful, String errorMessage) {
        this.successful = successful;
        this.errorMessage = errorMessage;
    }

    public static ParseOutcome success() {
        return new ParseOutcome(true, null);
    }

    public static ParseOutcome failure(String errorMessage) {
        return new ParseOutcome(false, errorMessage);
    }

    public boolean isSuccessful() {
        return successful;
    }

    /** The parse failure reason. Only meaningful when {@link #isSuccessful()} is false. */
    public String errorMessage() {
        return errorMessage;
    }
}
