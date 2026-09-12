package com.athena.semantic;

/**
 * One class, interface, enum, method, or field discovered by the symbol
 * model, identified structurally (see {@link SymbolId}) rather than by file
 * location.
 */
public final class Symbol {

    private final SymbolId id;
    private final SymbolKind kind;
    private final String simpleName;
    private final String enclosingTypeSimpleName;
    private final String compilationUnitPath;

    Symbol(SymbolId id, SymbolKind kind, String simpleName, String enclosingTypeSimpleName,
           String compilationUnitPath) {
        this.id = id;
        this.kind = kind;
        this.simpleName = simpleName;
        this.enclosingTypeSimpleName = enclosingTypeSimpleName;
        this.compilationUnitPath = compilationUnitPath;
    }

    public SymbolId id() {
        return id;
    }

    public SymbolKind kind() {
        return kind;
    }

    /** The symbol's own short name, e.g. "greet" or "Greeter". */
    public String simpleName() {
        return simpleName;
    }

    /**
     * The simple name of the type this symbol belongs to. For a
     * {@link SymbolKind#TYPE} symbol, this is the type's own simple name
     * (a type is its own enclosing type for this purpose).
     */
    public String enclosingTypeSimpleName() {
        return enclosingTypeSimpleName;
    }

    /** Path (relative to the source root) of the file this symbol was declared in. */
    public String compilationUnitPath() {
        return compilationUnitPath;
    }

    @Override
    public String toString() {
        return kind + " " + id;
    }
}
