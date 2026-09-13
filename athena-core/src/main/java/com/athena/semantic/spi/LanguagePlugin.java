package com.athena.semantic.spi;

import com.athena.semantic.DetectedTransformation;

import java.nio.file.Path;
import java.util.List;

/**
 * A pluggable source-language backend for the Semantic Change Engine: parsing
 * plus native transformation detection (rename/move/extract/add/remove at the
 * AST level) for one language. Java is the first implementation
 * ({@code athena-plugin-java}); a future language (e.g. JavaScript) is a new
 * module implementing this interface, discovered via {@link java.util.ServiceLoader}
 * — no change to {@code athena-core} or the application is required.
 *
 * <p>Implementations must translate their own parser's exceptions into
 * {@link ParseOutcome}/plain {@link RuntimeException} rather than letting
 * language-specific exception types cross this boundary (CODE_STYLE.md's
 * exception-translation rule).
 */
public interface LanguagePlugin {

    /** A short, stable identifier for this language, e.g. {@code "java"}. */
    String languageId();

    /** Whether this plugin recognizes {@code sourceFile} as one of its own source files. */
    boolean supports(Path sourceFile);

    /**
     * Whether {@code sourceText} parses successfully under this language, without
     * exposing this language's own AST to the caller — used for the graceful-
     * degradation fallback chain (epic #4 §45), which only needs success/failure.
     */
    ParseOutcome checkParses(String sourceText);

    /**
     * Detects every native transformation (rename, move, extract, add, remove,
     * signature/annotation change, mechanical replacement, formatting-only, ...)
     * between two source trees. {@code baseRoot}/{@code headRoot} may contain
     * source files for languages other than this one; implementations must only
     * consider files this plugin {@link #supports(Path)}.
     */
    List<DetectedTransformation> detect(Path baseRoot, Path headRoot);
}
