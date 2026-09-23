package com.athena.plugin.java;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ast.CompilationUnit;

/**
 * Parses Java source text into an AST ({@link CompilationUnit}), using
 * JavaParser rather than a hand-rolled parser (see epic #4's scope decision:
 * a mature existing Java AST/symbol-resolution library is the right
 * foundation for the Semantic Change Engine, not a from-scratch parser).
 *
 * <p>This class is intentionally minimal: it only proves the parsing
 * foundation is wired end to end. Symbol resolution across a whole source
 * tree is the next ticket's concern (the Java symbol model).
 */
public final class JavaSourceParser {

    private final JavaParser parser;

    public JavaSourceParser() {
        this.parser = new JavaParser(JavaParserConfigurations.currentJava());
    }

    /**
     * Parses a single Java source file's text. Never throws for invalid
     * input — parse failures are reported via {@link ParseResult#isSuccessful()}
     * so a caller can degrade gracefully (see epic #4's graceful-degradation
     * requirement) instead of crashing on unparseable source.
     */
    public ParseResult parse(String javaSourceText) {
        try {
            var result = parser.parse(javaSourceText);
            if (result.isSuccessful() && result.getResult().isPresent()) {
                return ParseResult.success(result.getResult().get());
            }
            String problems = result.getProblems().isEmpty()
                    ? "unknown parse error"
                    : result.getProblems().toString();
            return ParseResult.failure(problems);
        } catch (ParseProblemException e) {
            return ParseResult.failure(e.getMessage());
        }
    }
}
