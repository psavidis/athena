package com.athena.semantic;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Detects deterministic structural/mechanical transformations between a
 * base and head Java source tree: rename, move, add/remove symbol, changed
 * method signature, extract method, mechanical replacement, and
 * formatting-only changes. See epic #4 (Semantic Change Engine) for the
 * full scope; behavioral (condition/control-flow) detection is a separate
 * detector (ticket #18), not this one.
 *
 * <p>Every classification here is produced by exact structural (AST)
 * comparison — no confidence score, no fuzzy matching. A pair of methods
 * that merely resemble each other without matching structurally is left
 * unclassified rather than force-fit into a category (see the "changed
 * method call" scenario: a method whose only difference is *which* method
 * it calls is not reported as any of the kinds here).
 */
public final class TransformationDetector {

    public List<DetectedTransformation> detect(Path baseRoot, Path headRoot) {
        List<MethodInfo> baseMethods = methodInfos(baseRoot);
        List<MethodInfo> headMethods = methodInfos(headRoot);

        List<DetectedTransformation> results = new ArrayList<>();
        Set<MethodInfo> matchedBase = new LinkedHashSet<>();
        Set<MethodInfo> matchedHead = new LinkedHashSet<>();

        // 1. Exact match: same enclosing type + same name -> unchanged, changed signature,
        //    formatting-only, or "no structural transformation" (e.g. only a call target changed).
        for (MethodInfo base : baseMethods) {
            for (MethodInfo head : headMethods) {
                if (matchedHead.contains(head)) continue;
                if (!base.enclosingType.equals(head.enclosingType) || !base.name.equals(head.name)) continue;

                matchedBase.add(base);
                matchedHead.add(head);

                if (base.parameterTypes.equals(head.parameterTypes) && base.returnType.equals(head.returnType)) {
                    if (base.normalizedWholeDeclaration.equals(head.normalizedWholeDeclaration)) {
                        if (!base.rawWholeDeclaration.equals(head.rawWholeDeclaration)) {
                            results.add(DetectedTransformation.of(TransformationKind.FORMATTING_ONLY,
                                    List.of(base.description()), List.of(base.file, head.file)));
                        }
                        // else: truly identical, nothing to report.
                    }
                    // else: body differs but signature is the same and structure isn't AST-equal —
                    // out of scope for this detector (behavioral changes are ticket #18; a changed
                    // method call with no other structural change is deliberately left unclassified).
                } else {
                    results.add(DetectedTransformation.of(TransformationKind.CHANGE_METHOD_SIGNATURE,
                            List.of(base.description()), List.of(base.file, head.file)));
                }
                break;
            }
        }

        List<MethodInfo> unmatchedBase = baseMethods.stream().filter(m -> !matchedBase.contains(m)).toList();
        List<MethodInfo> unmatchedHead = headMethods.stream().filter(m -> !matchedHead.contains(m)).toList();

        // 2. Rename: same enclosing type, same normalized body, different name.
        List<MethodInfo> stillUnmatchedBase = new ArrayList<>(unmatchedBase);
        List<MethodInfo> stillUnmatchedHead = new ArrayList<>(unmatchedHead);
        for (MethodInfo base : new ArrayList<>(stillUnmatchedBase)) {
            for (MethodInfo head : new ArrayList<>(stillUnmatchedHead)) {
                if (base.enclosingType.equals(head.enclosingType)
                        && !base.name.equals(head.name)
                        && base.normalizedBody.equals(head.normalizedBody)) {
                    results.add(DetectedTransformation.of(TransformationKind.RENAME_SYMBOL,
                            List.of(base.description(), head.description()), List.of(base.file, head.file)));
                    stillUnmatchedBase.remove(base);
                    stillUnmatchedHead.remove(head);
                    break;
                }
            }
        }

        // 3. Move: same name, same normalized body, different enclosing type.
        for (MethodInfo base : new ArrayList<>(stillUnmatchedBase)) {
            for (MethodInfo head : new ArrayList<>(stillUnmatchedHead)) {
                if (!base.enclosingType.equals(head.enclosingType)
                        && base.name.equals(head.name)
                        && base.normalizedBody.equals(head.normalizedBody)) {
                    results.add(DetectedTransformation.of(TransformationKind.MOVE_SYMBOL,
                            List.of(base.description(), head.description()), List.of(base.file, head.file)));
                    stillUnmatchedBase.remove(base);
                    stillUnmatchedHead.remove(head);
                    break;
                }
            }
        }

        // 4. Extract method: a head method not present in base whose body is structurally
        //    equivalent to a fragment still present (as a call) inside a base method that
        //    still exists in head with a shorter body calling the new method.
        for (MethodInfo head : new ArrayList<>(stillUnmatchedHead)) {
            for (MethodInfo callerHead : headMethods) {
                if (callerHead.name.equals(head.name)) continue;
                if (callsMethod(callerHead.normalizedBody, head.name) && baseHasInlineEquivalent(baseMethods, callerHead, head)) {
                    results.add(DetectedTransformation.of(TransformationKind.EXTRACT_METHOD,
                            List.of(callerHead.description(), head.description()),
                            List.of(callerHead.file, head.file)));
                    stillUnmatchedHead.remove(head);
                    break;
                }
            }
        }

        // 5. Remaining unmatched base methods -> removed; remaining unmatched head methods -> added.
        for (MethodInfo base : stillUnmatchedBase) {
            results.add(DetectedTransformation.of(TransformationKind.REMOVE_SYMBOL,
                    List.of(base.description()), List.of(base.file)));
        }
        for (MethodInfo head : stillUnmatchedHead) {
            results.add(DetectedTransformation.of(TransformationKind.ADD_SYMBOL,
                    List.of(head.description()), List.of(head.file)));
        }

        // 6. Mechanical replacement: a whole-identifier textual substitution applied
        //    identically across every file that referenced the old identifier.
        results.addAll(detectMechanicalReplacements(baseRoot, headRoot));

        // 7. Record signature change: a same-named record's component (canonical constructor)
        //    list differs between revisions. Handled separately from steps 1-5 above, which
        //    match by MethodDeclaration and don't see a record's component list at all — and
        //    deliberately not fed through the rename/move matching either, since a record here
        //    keeps its own name; matching by name alone is simpler and correct, unlike the
        //    method rename/move detectors' "same body, different name/enclosing type" heuristic.
        results.addAll(detectRecordSignatureChanges(baseRoot, headRoot));

        return results;
    }

    private List<DetectedTransformation> detectRecordSignatureChanges(Path baseRoot, Path headRoot) {
        Map<String, RecordInfo> baseRecords = recordInfos(baseRoot);
        Map<String, RecordInfo> headRecords = recordInfos(headRoot);

        List<DetectedTransformation> results = new ArrayList<>();
        for (Map.Entry<String, RecordInfo> entry : baseRecords.entrySet()) {
            RecordInfo base = entry.getValue();
            RecordInfo head = headRecords.get(entry.getKey());
            if (head != null && !base.componentTypes.equals(head.componentTypes)) {
                results.add(DetectedTransformation.of(TransformationKind.CHANGE_METHOD_SIGNATURE,
                        List.of(entry.getKey() + "#<init>"), List.of(base.file, head.file)));
            }
        }
        return results;
    }

    private Map<String, RecordInfo> recordInfos(Path root) {
        Map<String, RecordInfo> infos = new LinkedHashMap<>();
        for (Path file : javaFiles(root)) {
            String relativePath = root.relativize(file).toString();
            CompilationUnit cu;
            try {
                StaticJavaParser.setConfiguration(new ParserConfiguration()
                        .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17));
                cu = StaticJavaParser.parse(file);
            } catch (IOException | RuntimeException e) {
                continue;
            }
            for (TypeDeclaration<?> type : cu.getTypes()) {
                if (type instanceof RecordDeclaration record) {
                    List<String> componentTypes = record.getParameters().stream()
                            .map(Parameter::getTypeAsString).toList();
                    infos.put(type.getNameAsString(), new RecordInfo(componentTypes, relativePath));
                }
            }
        }
        return infos;
    }

    private record RecordInfo(List<String> componentTypes, String file) {
    }

    private List<DetectedTransformation> detectMechanicalReplacements(Path baseRoot, Path headRoot) {
        List<DetectedTransformation> replacements = new ArrayList<>();
        List<Path> baseFiles = javaFiles(baseRoot);

        // Only consider files present (by relative path) in both revisions.
        List<String> commonRelativePaths = baseFiles.stream()
                .map(p -> baseRoot.relativize(p).toString())
                .filter(rel -> Files.exists(headRoot.resolve(rel)))
                .toList();

        if (commonRelativePaths.isEmpty()) {
            return replacements;
        }

        // Candidate identifiers: every simple name used in the base files.
        Set<String> candidateIdentifiers = new LinkedHashSet<>();
        for (String rel : commonRelativePaths) {
            candidateIdentifiers.addAll(extractIdentifiers(readFile(baseRoot.resolve(rel))));
        }

        for (String oldIdentifier : candidateIdentifiers) {
            int occurrences = 0;
            String newIdentifier = null;
            boolean consistent = true;

            for (String rel : commonRelativePaths) {
                String baseText = readFile(baseRoot.resolve(rel));
                String headText = readFile(headRoot.resolve(rel));
                if (!containsWholeIdentifier(baseText, oldIdentifier)) {
                    continue;
                }
                String substituted = replaceWholeIdentifier(baseText, oldIdentifier,
                        newIdentifier == null ? "___PLACEHOLDER___" : newIdentifier);

                if (newIdentifier == null) {
                    // Infer the replacement from the actual head text by diffing token-for-token
                    // against the substitution attempt using a placeholder-based structural check.
                    String inferred = inferReplacement(baseText, headText, oldIdentifier);
                    if (inferred == null) {
                        consistent = false;
                        break;
                    }
                    newIdentifier = inferred;
                    substituted = replaceWholeIdentifier(baseText, oldIdentifier, newIdentifier);
                }

                if (!substituted.equals(headText)) {
                    consistent = false;
                    break;
                }
                occurrences++;
            }

            if (consistent && occurrences > 0 && newIdentifier != null && !newIdentifier.equals(oldIdentifier)) {
                replacements.add(DetectedTransformation.withOccurrences(TransformationKind.MECHANICAL_REPLACEMENT,
                        List.of(oldIdentifier + " -> " + newIdentifier), commonRelativePaths, occurrences));
            }
        }

        return replacements;
    }

    private String inferReplacement(String baseText, String headText, String oldIdentifier) {
        // Find the first whole-word occurrence of oldIdentifier in baseText and read off
        // the corresponding token in headText at the same textual offset, provided the
        // surrounding text (prefix/suffix) is otherwise identical.
        int idx = indexOfWholeIdentifier(baseText, oldIdentifier, 0);
        if (idx < 0) return null;
        String prefix = baseText.substring(0, idx);
        if (!headText.startsWith(prefix)) return null;
        String headRemainder = headText.substring(prefix.length());
        int end = 0;
        while (end < headRemainder.length() && isIdentifierChar(headRemainder.charAt(end))) {
            end++;
        }
        if (end == 0) return null;
        return headRemainder.substring(0, end);
    }

    private boolean isIdentifierChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private boolean containsWholeIdentifier(String text, String identifier) {
        return indexOfWholeIdentifier(text, identifier, 0) >= 0;
    }

    private int indexOfWholeIdentifier(String text, String identifier, int from) {
        int idx = text.indexOf(identifier, from);
        while (idx >= 0) {
            boolean leftOk = idx == 0 || !isIdentifierChar(text.charAt(idx - 1));
            int endIdx = idx + identifier.length();
            boolean rightOk = endIdx >= text.length() || !isIdentifierChar(text.charAt(endIdx));
            if (leftOk && rightOk) {
                return idx;
            }
            idx = text.indexOf(identifier, idx + 1);
        }
        return -1;
    }

    private String replaceWholeIdentifier(String text, String oldIdentifier, String newIdentifier) {
        StringBuilder sb = new StringBuilder();
        int pos = 0;
        int idx;
        while ((idx = indexOfWholeIdentifier(text, oldIdentifier, pos)) >= 0) {
            sb.append(text, pos, idx).append(newIdentifier);
            pos = idx + oldIdentifier.length();
        }
        sb.append(text.substring(pos));
        return sb.toString();
    }

    private Set<String> extractIdentifiers(String text) {
        Set<String> identifiers = new LinkedHashSet<>();
        StringBuilder current = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (isIdentifierChar(c)) {
                current.append(c);
            } else {
                if (current.length() > 0) {
                    identifiers.add(current.toString());
                    current.setLength(0);
                }
            }
        }
        if (current.length() > 0) {
            identifiers.add(current.toString());
        }
        return identifiers;
    }

    private boolean callsMethod(String body, String methodName) {
        return body.contains(methodName + "(");
    }

    private boolean baseHasInlineEquivalent(List<MethodInfo> baseMethods, MethodInfo callerHead, MethodInfo extractedHead) {
        // The base version of the calling method (same enclosing type + name) must exist
        // and its body, once the extracted fragment's statements are considered, should
        // contain the same normalized statements the extracted method now holds.
        return baseMethods.stream().anyMatch(baseMethod ->
                baseMethod.enclosingType.equals(callerHead.enclosingType)
                        && baseMethod.name.equals(callerHead.name)
                        && containsNormalizedFragment(baseMethod.normalizedBody, extractedHead.normalizedBody));
    }

    private boolean containsNormalizedFragment(String haystack, String fragment) {
        String strippedFragment = stripBraces(fragment);
        return haystack.contains(strippedFragment);
    }

    private String stripBraces(String normalizedMethodBody) {
        String s = normalizedMethodBody.trim();
        if (s.startsWith("{") && s.endsWith("}")) {
            s = s.substring(1, s.length() - 1).trim();
        }
        return s;
    }

    private List<MethodInfo> methodInfos(Path root) {
        List<MethodInfo> infos = new ArrayList<>();
        for (Path file : javaFiles(root)) {
            String relativePath = root.relativize(file).toString();
            CompilationUnit cu;
            List<String> sourceLines;
            try {
                StaticJavaParser.setConfiguration(new ParserConfiguration()
                        .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17));
                cu = StaticJavaParser.parse(file);
                sourceLines = Files.readAllLines(file);
            } catch (IOException | RuntimeException e) {
                continue;
            }
            for (TypeDeclaration<?> type : cu.getTypes()) {
                for (MethodDeclaration method : type.getMethods()) {
                    // Two different textual views, both from *original source text* (via
                    // Range), not JavaParser's re-printed toString() (which normalizes
                    // formatting and would defeat both comparisons below):
                    //  - wholeDeclarationText: signature + body, used only to detect
                    //    formatting-only changes between two methods already matched by
                    //    name + enclosing type.
                    //  - bodyOnlyText: just the body, name-independent, used to match
                    //    renames/moves/extractions where the method's name itself changes.
                    String wholeDeclarationText = method.getRange()
                            .map(range -> sourceSlice(sourceLines, range))
                            .orElse(method.toString());
                    String bodyOnlyText = method.getBody()
                            .flatMap(Node::getRange)
                            .map(range -> sourceSlice(sourceLines, range))
                            .orElse(method.getBody().map(Node::toString).orElse(""));
                    List<String> paramTypes = method.getParameters().stream()
                            .map(Parameter::getTypeAsString).toList();
                    infos.add(new MethodInfo(type.getNameAsString(), method.getNameAsString(),
                            paramTypes, method.getTypeAsString(),
                            wholeDeclarationText, normalize(wholeDeclarationText),
                            normalize(bodyOnlyText), relativePath));
                }
            }
        }
        return infos;
    }

    private String sourceSlice(List<String> lines, com.github.javaparser.Range range) {
        StringBuilder sb = new StringBuilder();
        for (int line = range.begin.line; line <= range.end.line; line++) {
            String text = lines.get(line - 1);
            int fromCol = (line == range.begin.line) ? range.begin.column - 1 : 0;
            int toCol = (line == range.end.line) ? range.end.column : text.length();
            sb.append(text, Math.max(0, fromCol), Math.min(text.length(), toCol));
            if (line != range.end.line) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private String normalize(String body) {
        // Collapse all whitespace so formatting-only differences don't affect equality,
        // while keeping the actual token content (and thus structure) significant.
        return body.replaceAll("\\s+", " ").trim();
    }

    private List<Path> javaFiles(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(p -> p.toString().endsWith(".java")).filter(Files::isRegularFile).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String readFile(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record MethodInfo(String enclosingType, String name, List<String> parameterTypes, String returnType,
                               String rawWholeDeclaration, String normalizedWholeDeclaration,
                               String normalizedBody, String file) {
        String description() {
            return enclosingType + "#" + name;
        }
    }
}
