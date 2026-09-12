package com.athena.semantic;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A resolved symbol model of a Java source tree: every type, method, and
 * field it declares, each with a stable {@link SymbolId}, plus any
 * resolution failures encountered along the way.
 *
 * <p>This is a single-source-tree snapshot — cross-revision diffing (base
 * vs. head) is out of scope here; see the detection tickets that consume
 * this model.
 */
public final class SymbolModel {

    private final Map<SymbolId, Symbol> symbolsById;
    private final List<ResolutionFailure> resolutionFailures;

    SymbolModel(List<Symbol> symbols, List<ResolutionFailure> resolutionFailures) {
        this.symbolsById = symbols.stream()
                .collect(Collectors.toMap(Symbol::id, s -> s, (a, b) -> a));
        this.resolutionFailures = List.copyOf(resolutionFailures);
    }

    /** Look up a symbol by its stable identifier. */
    public Optional<Symbol> findById(SymbolId id) {
        return Optional.ofNullable(symbolsById.get(id));
    }

    /** All symbols in the model, regardless of kind. */
    public List<Symbol> allSymbols() {
        return List.copyOf(symbolsById.values());
    }

    /** All symbols of one kind (type, method, or field). */
    public List<Symbol> symbolsOfKind(SymbolKind kind) {
        return symbolsById.values().stream()
                .filter(s -> s.kind() == kind)
                .toList();
    }

    /** All symbols declared in one compilation unit (source file), by its relative path. */
    public List<Symbol> symbolsInCompilationUnit(String relativePath) {
        return symbolsById.values().stream()
                .filter(s -> s.compilationUnitPath().equals(relativePath))
                .toList();
    }

    /** References the model could not resolve, reported without aborting the rest of the build. */
    public List<ResolutionFailure> resolutionFailures() {
        return resolutionFailures;
    }
}
