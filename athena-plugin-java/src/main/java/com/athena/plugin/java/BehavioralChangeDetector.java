package com.athena.plugin.java;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Detects condition and control-flow changes (the narrow, MVP-scoped slice
 * of "basic behavioral modifications", epic #4 §53 item 10) within methods
 * matched by (enclosing type, name) across a base and head revision.
 *
 * <p>Only condition expressions and branch/loop add-remove are considered:
 * a changed method call or return expression is deliberately never flagged
 * here (see the epic's resolved scope — those are too fuzzy for MVP).
 */
public final class BehavioralChangeDetector {

    public List<DetectedBehavioralChange> detect(Path baseRoot, Path headRoot) {
        List<MethodBody> baseMethods = methodBodies(baseRoot);
        List<MethodBody> headMethods = methodBodies(headRoot);

        List<DetectedBehavioralChange> changes = new ArrayList<>();
        for (MethodBody base : baseMethods) {
            headMethods.stream()
                    .filter(head -> head.enclosingType.equals(base.enclosingType) && head.name.equals(base.name))
                    .findFirst()
                    .flatMap(head -> base.controlFlow.describeChangeTo(head.controlFlow))
                    .ifPresent(reason -> changes.add(new DetectedBehavioralChange(base.description(), reason)));
        }
        return changes;
    }

    private List<MethodBody> methodBodies(Path root) {
        List<MethodBody> infos = new ArrayList<>();
        for (Path file : javaFiles(root)) {
            CompilationUnit cu;
            try {
                StaticJavaParser.setConfiguration(JavaParserConfigurations.currentJava());
                cu = StaticJavaParser.parse(file);
            } catch (IOException | RuntimeException e) {
                continue;
            }
            for (TypeDeclaration<?> type : cu.getTypes()) {
                collectMethods(type, type.getNameAsString(), infos);
            }
        }
        return infos;
    }

    private List<Path> javaFiles(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(p -> p.toString().endsWith(".java")).filter(Files::isRegularFile).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Nested and inner types included, keyed by qualified name like {@link TransformationDetector} (#265). */
    private void collectMethods(TypeDeclaration<?> type, String qualifiedName, List<MethodBody> infos) {
        for (MethodDeclaration method : type.getMethods()) {
            infos.add(new MethodBody(qualifiedName, method.getNameAsString(), ControlFlow.of(method)));
        }
        for (BodyDeclaration<?> member : type.getMembers()) {
            if (member instanceof TypeDeclaration<?> nested) {
                collectMethods(nested, qualifiedName + "." + nested.getNameAsString(), infos);
            }
        }
    }

    private record MethodBody(String enclosingType, String name, ControlFlow controlFlow) {
        String description() {
            return enclosingType + "#" + name;
        }
    }
}
