package com.athena.semantic;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;

import java.util.List;

/**
 * The outcome of parsing one Java source file: either a successfully parsed
 * {@link CompilationUnit} (the JavaParser AST root), or a failure with a
 * human-readable reason. Callers should not need to know about JavaParser's
 * own result types directly — this is the semantic engine's own boundary.
 */
public final class ParseResult {

    private final CompilationUnit compilationUnit;
    private final String errorMessage;

    private ParseResult(CompilationUnit compilationUnit, String errorMessage) {
        this.compilationUnit = compilationUnit;
        this.errorMessage = errorMessage;
    }

    static ParseResult success(CompilationUnit compilationUnit) {
        return new ParseResult(compilationUnit, null);
    }

    static ParseResult failure(String errorMessage) {
        return new ParseResult(null, errorMessage);
    }

    public boolean isSuccessful() {
        return compilationUnit != null;
    }

    public String errorMessage() {
        return errorMessage;
    }

    /** The underlying JavaParser AST root, for callers that need full AST access. */
    public CompilationUnit compilationUnit() {
        requireSuccess();
        return compilationUnit;
    }

    /** Names of every top-level and nested type declared in the file. */
    public List<String> topLevelTypeNames() {
        requireSuccess();
        return compilationUnit.getTypes().stream()
                .map(TypeDeclaration::getNameAsString)
                .toList();
    }

    /** Names of every method declared anywhere in the file. */
    public List<String> methodNames() {
        requireSuccess();
        return compilationUnit.findAll(MethodDeclaration.class).stream()
                .map(MethodDeclaration::getNameAsString)
                .toList();
    }

    private void requireSuccess() {
        if (!isSuccessful()) {
            throw new IllegalStateException("Cannot inspect a failed parse result: " + errorMessage);
        }
    }
}
