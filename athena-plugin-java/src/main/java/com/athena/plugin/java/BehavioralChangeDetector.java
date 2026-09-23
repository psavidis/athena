package com.athena.plugin.java;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.WhileStmt;

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
            for (MethodBody head : headMethods) {
                if (!base.enclosingType.equals(head.enclosingType) || !base.name.equals(head.name)) {
                    continue;
                }

                List<String> baseConditions = conditions(base.body);
                List<String> headConditions = conditions(head.body);
                if (!baseConditions.equals(headConditions)) {
                    changes.add(new DetectedBehavioralChange(base.description(), "condition changed"));
                    break;
                }

                int baseBranchCount = branchCount(base.body);
                int headBranchCount = branchCount(head.body);
                if (baseBranchCount != headBranchCount) {
                    changes.add(new DetectedBehavioralChange(base.description(),
                            headBranchCount > baseBranchCount ? "branch added" : "branch removed"));
                    break;
                }

                int baseLoopCount = loopCount(base.body);
                int headLoopCount = loopCount(head.body);
                if (baseLoopCount != headLoopCount) {
                    changes.add(new DetectedBehavioralChange(base.description(),
                            headLoopCount > baseLoopCount ? "loop added" : "loop removed"));
                    break;
                }

                // Otherwise: body may differ (e.g. a changed method call or return
                // expression), but that is not a condition/control-flow change and is
                // out of scope for this detector.
                break;
            }
        }

        return changes;
    }

    private List<String> conditions(BlockStmt body) {
        return body.findAll(IfStmt.class).stream()
                .map(ifStmt -> ifStmt.getCondition().toString())
                .toList();
    }

    private int branchCount(BlockStmt body) {
        int count = 0;
        for (IfStmt ifStmt : body.findAll(IfStmt.class)) {
            count++; // the "if" itself
            Statement current = ifStmt.getElseStmt().orElse(null);
            while (current != null) {
                count++;
                if (current instanceof IfStmt elseIf) {
                    current = elseIf.getElseStmt().orElse(null);
                } else {
                    current = null;
                }
            }
        }
        return count;
    }

    private int loopCount(BlockStmt body) {
        return body.findAll(ForStmt.class).size()
                + body.findAll(ForEachStmt.class).size()
                + body.findAll(WhileStmt.class).size()
                + body.findAll(DoStmt.class).size();
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
                for (MethodDeclaration method : type.getMethods()) {
                    method.getBody().ifPresent(body ->
                            infos.add(new MethodBody(type.getNameAsString(), method.getNameAsString(), body)));
                }
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

    private record MethodBody(String enclosingType, String name, BlockStmt body) {
        String description() {
            return enclosingType + "#" + name;
        }
    }
}
