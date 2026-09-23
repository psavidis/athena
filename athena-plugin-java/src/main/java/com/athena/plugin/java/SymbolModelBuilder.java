package com.athena.plugin.java;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.resolution.UnsolvedSymbolException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Builds a {@link SymbolModel} from a directory of {@code .java} files,
 * using JavaParser's {@link JavaSymbolSolver} (backed by a
 * {@link CombinedTypeSolver} covering the source tree itself plus the JDK
 * via reflection) to resolve intra-project references.
 *
 * <p>Resolution is best-effort: a reference this builder can't resolve
 * (e.g. an external library type not on the configured classpath) is
 * recorded as a {@link ResolutionFailure} rather than aborting the whole
 * build, per epic #4's graceful-degradation requirement.
 */
public final class SymbolModelBuilder {

    public SymbolModel build(Path sourceRoot) {
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        typeSolver.add(new JavaParserTypeSolver(sourceRoot.toFile()));
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);

        ParserConfiguration configuration = JavaParserConfigurations.currentJava()
                .setSymbolResolver(symbolSolver);
        StaticJavaParser.setConfiguration(configuration);

        List<Symbol> symbols = new ArrayList<>();
        List<ResolutionFailure> failures = new ArrayList<>();

        List<Path> javaFiles = listJavaFiles(sourceRoot);
        for (Path file : javaFiles) {
            String relativePath = sourceRoot.relativize(file).toString();
            CompilationUnit cu;
            try {
                cu = StaticJavaParser.parse(file);
            } catch (IOException e) {
                failures.add(new ResolutionFailure(relativePath, "could not read/parse file: " + e.getMessage()));
                continue;
            } catch (RuntimeException e) {
                failures.add(new ResolutionFailure(relativePath, "could not parse file: " + e.getMessage()));
                continue;
            }

            for (TypeDeclaration<?> type : cu.getTypes()) {
                collectFromType(type, relativePath, symbols);
            }

            // Attempt symbol resolution on constructs likely to reference other
            // types, to surface resolution failures without aborting the model.
            for (ObjectCreationExpr expr : cu.findAll(ObjectCreationExpr.class)) {
                try {
                    expr.resolve();
                } catch (UnsolvedSymbolException e) {
                    failures.add(new ResolutionFailure(relativePath,
                            "unresolved reference: " + e.getMessage()));
                } catch (RuntimeException e) {
                    failures.add(new ResolutionFailure(relativePath,
                            "resolution error: " + e.getMessage()));
                }
            }
        }

        return new SymbolModel(symbols, failures);
    }

    private void collectFromType(TypeDeclaration<?> type, String relativePath, List<Symbol> out) {
        String typeName = type.getFullyQualifiedName().orElse(type.getNameAsString());
        SymbolId typeId = SymbolId.forType(typeName);
        out.add(new Symbol(typeId, SymbolKind.TYPE, type.getNameAsString(), type.getNameAsString(), relativePath));

        for (MethodDeclaration method : type.getMethods()) {
            String signature = method.getSignature().asString();
            SymbolId methodId = SymbolId.forMember(typeId, signature);
            out.add(new Symbol(methodId, SymbolKind.METHOD, method.getNameAsString(),
                    type.getNameAsString(), relativePath));
        }

        for (FieldDeclaration field : type.getFields()) {
            for (VariableDeclarator variable : field.getVariables()) {
                SymbolId fieldId = SymbolId.forMember(typeId, variable.getNameAsString());
                out.add(new Symbol(fieldId, SymbolKind.FIELD, variable.getNameAsString(),
                        type.getNameAsString(), relativePath));
            }
        }

        for (var nested : type.getMembers().stream()
                .filter(TypeDeclaration.class::isInstance)
                .map(TypeDeclaration.class::cast)
                .toList()) {
            collectFromType(nested, relativePath, out);
        }
    }

    private List<Path> listJavaFiles(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(p -> p.toString().endsWith(".java"))
                    .filter(Files::isRegularFile)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
