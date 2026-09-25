package com.athena.plugin.java;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.SimpleName;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Where a renamed identifier lived when it was a local variable or a parameter (ticket #386):
 * its kind and the methods declaring it, e.g. "parameter" in {@code Account#deposit,
 * Account#canWithdraw}. Found only when every use of the name in the changed files is inside
 * a method declaring it, and the kind is the same in all of them. Immutable.
 */
record LocalRenameScope(String kind, List<String> methods) {

    static final String LOCAL_VARIABLE = "local variable";
    static final String PARAMETER = "parameter";

    LocalRenameScope {
        methods = List.copyOf(methods);
    }

    /** The scope of {@code name} in {@code units} (the base revision of the changed files), if it's a local or parameter there. */
    static Optional<LocalRenameScope> of(String name, List<CompilationUnit> units) {
        Set<String> kinds = new LinkedHashSet<>();
        List<String> methods = new ArrayList<>();
        for (CompilationUnit unit : units) {
            long uses = occurrences(unit, name);
            long usesInDeclaringMethods = 0;
            for (CallableDeclaration<?> callable : unit.findAll(CallableDeclaration.class)) {
                Optional<String> kind = kindIn(callable, name);
                if (kind.isPresent()) {
                    kinds.add(kind.get());
                    methods.add(description(callable));
                    usesInDeclaringMethods += occurrences(callable, name);
                }
            }
            if (uses != usesInDeclaringMethods) {
                return Optional.empty();
            }
        }
        return kinds.size() == 1 ? Optional.of(new LocalRenameScope(kinds.iterator().next(), methods)) : Optional.empty();
    }

    /** "parameter" if {@code callable} takes {@code name}, "local variable" if its body declares it, empty otherwise. */
    private static Optional<String> kindIn(CallableDeclaration<?> callable, String name) {
        if (callable.getParameters().stream().anyMatch(parameter -> parameter.getNameAsString().equals(name))) {
            return Optional.of(PARAMETER);
        }
        boolean declaredInside = callable.findAll(VariableDeclarator.class).stream().anyMatch(v -> v.getNameAsString().equals(name))
                // A lambda's or catch clause's parameter is local to the method too.
                || callable.findAll(Parameter.class).stream()
                        .anyMatch(p -> p.getNameAsString().equals(name) && !callable.getParameters().contains(p));
        return declaredInside ? Optional.of(LOCAL_VARIABLE) : Optional.empty();
    }

    private static long occurrences(Node node, String name) {
        return node.findAll(SimpleName.class).stream().filter(simpleName -> simpleName.getIdentifier().equals(name)).count();
    }

    /** "Outer.Inner#method", or "#<init>" for a constructor. */
    private static String description(CallableDeclaration<?> callable) {
        List<String> types = new ArrayList<>();
        Optional<Node> parent = callable.getParentNode();
        while (parent.isPresent()) {
            if (parent.get() instanceof TypeDeclaration<?> type) {
                types.add(0, type.getNameAsString());
            }
            parent = parent.get().getParentNode();
        }
        String member = callable instanceof ConstructorDeclaration ? "<init>" : callable.getNameAsString();
        return String.join(".", types) + "#" + member;
    }
}
