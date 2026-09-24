package com.athena.plugin.java;

import com.athena.semantic.DetectedTransformation;
import com.athena.semantic.TransformationKind;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.AnnotationMemberDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
        // One parse pass per root, not two — methodInfos and recordInfos used to each
        // independently call StaticJavaParser.parse() over every file, so every root's
        // source tree was parsed twice for no reason (parsing is the expensive part; the
        // method-vs-record extraction from an already-parsed CompilationUnit is cheap).
        // Only files that differ between the revisions are parsed and compared (ticket #271):
        // an identical file can't be the source or target of any change reported here.
        ChangedJavaFiles changedFiles = ChangedJavaFiles.between(baseRoot, headRoot);
        // A file that fails to parse in either revision is left out of detection on both sides
        // (#260): parsing only its other side would report every declaration in it as removed or
        // added. It stays visible through the symbol-aware fallback and raw diff instead.
        Set<String> unparseable = new LinkedHashSet<>();
        Map<String, ParsedFile> baseFiles = parseFiles(baseRoot, changedFiles.relativePaths(), unparseable);
        Map<String, ParsedFile> headFiles = parseFiles(headRoot, changedFiles.relativePaths(), unparseable);
        ParsedRoot baseParsed = collectDeclarations(baseFiles, unparseable);
        ParsedRoot headParsed = collectDeclarations(headFiles, unparseable);

        List<DetectedTransformation> results = new ArrayList<>();

        // 0. Class rename/move/add/remove, detected before any member-level matching. A
        // renamed/moved class is matched by an EXACT member-signature-set match (no fuzzy
        // matching, same policy as everywhere else in this detector) — so if the class's
        // members changed in the same revision, it won't be recognized as the "same" class
        // and instead reads as one class removed + one added, same as any other exact-match
        // detector here when its match condition isn't met.
        ClassMatchResult classMatch = detectClassChanges(baseParsed.classes(), headParsed.classes());
        results.addAll(classMatch.transformations());

        // Once a class is known to be the "same" class under a rename/move (its member set
        // matched exactly), its members are excluded from the flat method/field matching
        // below — they didn't independently change, so they'd otherwise show up as a
        // confusing pile of REMOVE+ADD (or MOVE_SYMBOL) rows on top of the one RENAME_CLASS/
        // MOVE_CLASS Change that already explains what happened to them.
        List<MethodInfo> baseMethods = baseParsed.methods().stream()
                .filter(m -> !classMatch.excludedBaseTypes().contains(m.enclosingType)).toList();
        List<MethodInfo> headMethods = headParsed.methods().stream()
                .filter(m -> !classMatch.excludedHeadTypes().contains(m.enclosingType)).toList();

        Set<MethodInfo> matchedBase = new LinkedHashSet<>();
        Set<MethodInfo> matchedHead = new LinkedHashSet<>();
        // Same-signature methods whose body changed in a way no more specific check in step 1
        // explained; reported in step 6b unless a mechanical replacement explains them (#264).
        List<BodyEdit> unexplainedBodyEdits = new ArrayList<>();

        // Indexed by (enclosingType, name) so step 1 and step 4's caller lookups are O(1)
        // per method instead of scanning every head/base method — the dominant cost on a
        // real multi-module repo's method count (see detect()'s own class doc: no fuzzy
        // matching, so every match key here is exact equality, which is exactly what a
        // Map supports without giving up any matching precision).
        Map<MethodKey, List<MethodInfo>> headByKey = indexByKey(headMethods);
        Map<MethodKey, List<MethodInfo>> baseByKey = indexByKey(baseMethods);

        // 1. Exact match: same enclosing type + same name -> unchanged, changed signature,
        //    formatting-only, or "no structural transformation" (e.g. only a call target changed).
        for (MethodInfo base : baseMethods) {
            MethodInfo head = firstUnmatched(headByKey.get(new MethodKey(base.file, base.enclosingType, base.name)), matchedHead);
            if (head == null) continue;

            matchedBase.add(base);
            matchedHead.add(head);

            if (base.parameterTypes.equals(head.parameterTypes) && base.returnType.equals(head.returnType)) {
                // Annotations on parameters, on the return type, or on the method itself change
                // the method's contract without changing its signature (ticket #268).
                base.parameterAnnotations.describeChangesTo(head.parameterAnnotations).ifPresent(changes ->
                        results.add(DetectedTransformation.withDiff(TransformationKind.CHANGE_PARAMETER_ANNOTATIONS,
                                List.of(base.description(), changes), List.of(base.file, head.file),
                                base.rawWholeDeclaration, head.rawWholeDeclaration)));
                if (!base.methodAnnotations.equals(head.methodAnnotations)) {
                    results.add(DetectedTransformation.withDiff(TransformationKind.CHANGE_METHOD_ANNOTATIONS,
                            List.of(base.description(), ParameterAnnotations.describe(base.methodAnnotations, head.methodAnnotations)),
                            List.of(base.file, head.file), base.rawWholeDeclaration, head.rawWholeDeclaration));
                }
                if (base.normalizedWholeDeclaration.equals(head.normalizedWholeDeclaration)) {
                    if (!base.rawWholeDeclaration.equals(head.rawWholeDeclaration)) {
                        results.add(DetectedTransformation.withDiff(TransformationKind.FORMATTING_ONLY,
                                List.of(base.description()), List.of(base.file, head.file),
                                base.rawWholeDeclaration, head.rawWholeDeclaration));
                    }
                    // else: truly identical, nothing to report.
                } else {
                    // Body differs with the same signature. A changed condition, branch or loop
                    // is a behavioral change (tickets #18, #263); any other body edit (e.g. only a
                    // call target changed) is deliberately left unclassified here.
                    Optional<String> controlFlowChange = base.controlFlow.describeChangeTo(head.controlFlow);
                    if (controlFlowChange.isPresent()) {
                        results.add(DetectedTransformation.withDiff(TransformationKind.CHANGE_CONTROL_FLOW,
                                List.of(base.description(), controlFlowChange.get()), List.of(base.file, head.file),
                                base.rawWholeDeclaration, head.rawWholeDeclaration));
                    } else if (!base.normalizedBody.equals(head.normalizedBody)) {
                        unexplainedBodyEdits.add(new BodyEdit(base.description(), base.file, head.file,
                                base.normalizedBody, head.normalizedBody, base.rawWholeDeclaration, head.rawWholeDeclaration,
                                base.bodySummary.describeChangeTo(head.bodySummary)));
                    }
                }
            } else {
                results.add(DetectedTransformation.withDiff(TransformationKind.CHANGE_METHOD_SIGNATURE,
                        List.of(base.description()), List.of(base.file, head.file),
                        base.rawWholeDeclaration, head.rawWholeDeclaration));
            }
        }

        List<MethodInfo> unmatchedBase = baseMethods.stream().filter(m -> !matchedBase.contains(m)).toList();
        List<MethodInfo> unmatchedHead = headMethods.stream().filter(m -> !matchedHead.contains(m)).toList();

        // 2 & 3. Rename/move: matched by normalizedBody equality, so index the still-unmatched
        // head methods by that body once, then look each still-unmatched base method up in O(1)
        // instead of scanning linearly. Same-enclosing-type+different-name is a rename; different-
        // enclosing-type+same-name is a move — both share this index, checked in that priority
        // order per base method (renames are more common, and a body can't match both shapes at
        // once for a given base/head pair since a method has exactly one name and one enclosing type).
        Set<MethodInfo> stillUnmatchedBase = new LinkedHashSet<>(unmatchedBase);
        Set<MethodInfo> stillUnmatchedHead = new LinkedHashSet<>(unmatchedHead);
        Map<String, List<MethodInfo>> unmatchedHeadByBody = indexByBody(stillUnmatchedHead);

        for (MethodInfo base : unmatchedBase) {
            if (!stillUnmatchedBase.contains(base)) continue;
            List<MethodInfo> candidates = unmatchedHeadByBody.get(base.normalizedBody);
            if (candidates == null) continue;

            MethodInfo renameMatch = null;
            MethodInfo moveMatch = null;
            for (MethodInfo head : candidates) {
                if (!stillUnmatchedHead.contains(head)) continue;
                if (base.file.equals(head.file) && base.enclosingType.equals(head.enclosingType) && !base.name.equals(head.name)) {
                    renameMatch = head;
                    break;
                }
                if (moveMatch == null && !base.enclosingType.equals(head.enclosingType) && base.name.equals(head.name)) {
                    moveMatch = head;
                }
            }

            if (renameMatch != null) {
                results.add(DetectedTransformation.withDiff(TransformationKind.RENAME_SYMBOL,
                        List.of(base.description(), renameMatch.description()), List.of(base.file, renameMatch.file),
                        base.rawWholeDeclaration, renameMatch.rawWholeDeclaration));
                stillUnmatchedBase.remove(base);
                stillUnmatchedHead.remove(renameMatch);
            } else if (moveMatch != null) {
                results.add(DetectedTransformation.withDiff(TransformationKind.MOVE_SYMBOL,
                        List.of(base.description(), moveMatch.description()), List.of(base.file, moveMatch.file),
                        base.rawWholeDeclaration, moveMatch.rawWholeDeclaration));
                stillUnmatchedBase.remove(base);
                stillUnmatchedHead.remove(moveMatch);
            }
        }

        // 4. Extract method: a head method not present in base whose body is structurally
        //    equivalent to a fragment still present (as a call) inside a base method that
        //    still exists in head with a shorter body calling the new method. Only a caller
        //    whose own body changed can have had something extracted out of it, and an empty
        //    body is "contained" in any method, so it's never an extraction (ticket #269: the
        //    keycloak false positive credited new empty init()/close() methods to untouched
        //    files that merely call a same-named method).
        for (MethodInfo head : new ArrayList<>(stillUnmatchedHead)) {
            if (stripBraces(head.normalizedBody).isEmpty()) continue;
            for (MethodInfo callerHead : headMethods) {
                if (callerHead.name.equals(head.name)) continue;
                if (!bodyChanged(baseByKey, callerHead)) continue;
                if (callsMethod(callerHead.normalizedBody, head.name) && baseHasInlineEquivalent(baseByKey, callerHead, head)) {
                    MethodInfo callerBase = firstUnmatched(
                            baseByKey.get(new MethodKey(callerHead.file, callerHead.enclosingType, callerHead.name)), Set.of());
                    // Two methods' before/after concatenated with a blank-line separator, rather
                    // than combining two already-computed diffs: UnifiedDiff.of is a line-based
                    // LCS, so this still produces a correct combined diff, and keeps the actual
                    // diffing itself lazy (computed on first request, not here).
                    String callerBeforeText = callerBase == null ? "" : callerBase.rawWholeDeclaration;
                    String combinedBefore = callerBeforeText.isBlank() ? ""
                            : callerBeforeText + "\n\n";
                    String combinedAfter = callerHead.rawWholeDeclaration + "\n\n" + head.rawWholeDeclaration;
                    results.add(DetectedTransformation.withDiff(TransformationKind.EXTRACT_METHOD,
                            List.of(callerHead.description(), head.description()),
                            List.of(callerHead.file, head.file), combinedBefore, combinedAfter));
                    stillUnmatchedHead.remove(head);
                    break;
                }
            }
        }

        // 5. Remaining unmatched base methods -> removed; remaining unmatched head methods -> added.
        for (MethodInfo base : stillUnmatchedBase) {
            results.add(DetectedTransformation.withDiff(TransformationKind.REMOVE_SYMBOL,
                    List.of(base.description()), List.of(base.file),
                    base.rawWholeDeclaration, ""));
        }
        for (MethodInfo head : stillUnmatchedHead) {
            results.add(DetectedTransformation.withDiff(TransformationKind.ADD_SYMBOL,
                    List.of(head.description()), List.of(head.file),
                    "", head.rawWholeDeclaration));
        }

        // 6. Mechanical replacement: a whole-identifier textual substitution applied
        //    identically across every file that referenced the old identifier.
        List<DetectedTransformation> mechanicalReplacements = detectMechanicalReplacements(baseRoot, headRoot,
                changedFiles.presentInBoth());
        results.addAll(mechanicalReplacements);

        // 6b. Body modifications (ticket #264): every same-signature method or constructor whose
        //     body changed and that nothing above explains is still reported, so an edit inside a
        //     method is never invisible. One fully explained by the mechanical replacements (e.g. a
        //     renamed type used in its body) is left to those, not repeated per method.
        unexplainedBodyEdits.addAll(constructorBodyEdits(baseParsed.constructors(), headParsed.constructors()));
        results.addAll(bodyModifications(unexplainedBodyEdits, mechanicalReplacements));

        // 7. Record signature change: a same-named record's component (canonical constructor)
        //    list differs between revisions. Handled separately from steps 1-5 above, which
        //    match by MethodDeclaration and don't see a record's component list at all — and
        //    deliberately not fed through the rename/move matching either, since a record here
        //    keeps its own name; matching by name alone is simpler and correct, unlike the
        //    method rename/move detectors' "same body, different name/enclosing type" heuristic.
        results.addAll(detectRecordSignatureChanges(baseParsed.records(), headParsed.records()));

        // 8. Field rename/move/add/remove — same shape as the method detection above (steps
        // 1-3, 5), minus signature-change/extract/formatting concepts a field doesn't have.
        // A field's "body" equivalent for rename/move matching is its declared type: same
        // enclosing type + different name + same type is a rename; different enclosing type
        // + same name + same type is a move. A same-name field in the same enclosing type whose
        // type changed is a CHANGE_FIELD_TYPE (ticket #267); a field whose name AND type both
        // changed is never fuzzy-matched — same no-confidence-score policy as the rest of this
        // detector — and stays a remove + add.
        List<FieldInfo> baseFields = baseParsed.fields().stream()
                .filter(f -> !classMatch.excludedBaseTypes().contains(f.enclosingType)).toList();
        List<FieldInfo> headFields = headParsed.fields().stream()
                .filter(f -> !classMatch.excludedHeadTypes().contains(f.enclosingType)).toList();
        results.addAll(detectFieldChanges(baseFields, headFields));

        // 9. Constructor parameter added and assigned to a same-named field — the structural
        // half of "field injection -> constructor injection" (ticket #86's dependency-injection
        // pattern example). Matched by enclosing type + parameter name + parameter type against
        // the head constructor only (no rename/move matching needed here, unlike methods/fields:
        // a constructor has no name of its own to rename, so "added" is the only shape that
        // matters). Whether this actually correlates with a REMOVE_FIELD Change for the same
        // name/type is decided later by PatternTaxonomyClassifier, which sees the full Change
        // set this detector's single pass through one class's constructors can't.
        results.addAll(detectAddedConstructorParameters(baseParsed.constructors(), headParsed.constructors()));
        results.addAll(detectConstructorParameterAnnotationChanges(baseParsed.constructors(), headParsed.constructors()));

        // 10. Enum constants and annotation-type elements (ticket #266): matched by enclosing
        // type + name, so reordering is never reported. An element present on both sides whose
        // default value changed is reported as such; a changed element type is left to the
        // remove/add fallback, the same no-fuzzy-matching policy as fields.
        results.addAll(detectEnumConstantChanges(
                excluding(baseParsed.enumConstants(), EnumConstantInfo::enclosingType, classMatch.excludedBaseTypes()),
                excluding(headParsed.enumConstants(), EnumConstantInfo::enclosingType, classMatch.excludedHeadTypes())));
        results.addAll(detectAnnotationElementChanges(
                excluding(baseParsed.annotationElements(), AnnotationElementInfo::enclosingType, classMatch.excludedBaseTypes()),
                excluding(headParsed.annotationElements(), AnnotationElementInfo::enclosingType, classMatch.excludedHeadTypes())));

        // 11. Pull-ups (ticket #290): a member several existing classes lost to a new common
        // base class that gained it. Runs last, since it replaces the move/remove/add rows the
        // steps above already reported for those same members.
        return withPullUps(baseParsed, headParsed, results);
    }

    /**
     * {@code results} with every pull-up added and the move/remove/add transformations it
     * explains taken out. A pull-up needs a base class that is new in head, two or more classes
     * that existed in base and extend it (directly or through other classes) in head, and a
     * member — same name and type/signature — each of those classes had in base but not in head,
     * which the new base class declares. Sources are listed alphabetically, so the result never
     * depends on file order.
     */
    private List<DetectedTransformation> withPullUps(ParsedRoot baseParsed, ParsedRoot headParsed,
                                                     List<DetectedTransformation> results) {
        Map<String, ClassInfo> baseClasses = new LinkedHashMap<>();
        baseParsed.classes().forEach(c -> baseClasses.putIfAbsent(c.simpleName(), c));
        List<DetectedTransformation> pullUps = new ArrayList<>();
        Set<String> covered = new LinkedHashSet<>();
        for (ClassInfo newBase : headParsed.classes()) {
            if (baseClasses.containsKey(newBase.simpleName())) {
                continue;
            }
            List<ClassInfo> subclasses = headParsed.classes().stream()
                    .filter(c -> baseClasses.containsKey(c.simpleName()))
                    .filter(c -> extendsTransitively(c, newBase, headParsed.classes()))
                    .sorted(Comparator.comparing(ClassInfo::simpleName))
                    .toList();
            for (String signature : newBase.memberSignatures()) {
                boolean field = signature.startsWith("field:");
                if (!field && !signature.startsWith("method:")) {
                    continue;
                }
                List<ClassInfo> sources = subclasses.stream()
                        .filter(sub -> baseClasses.get(sub.simpleName()).memberSignatures().contains(signature))
                        .filter(sub -> !sub.memberSignatures().contains(signature))
                        .toList();
                if (sources.size() < 2) {
                    continue;
                }
                String member = memberName(signature);
                List<String> involved = new ArrayList<>(List.of(newBase.simpleName() + "#" + member));
                sources.forEach(source -> involved.add(source.simpleName() + "#" + member));
                covered.addAll(involved);
                List<String> files = new ArrayList<>(List.of(newBase.file()));
                sources.stream().map(ClassInfo::file).filter(file -> !files.contains(file)).forEach(files::add);
                String before = sources.stream()
                        .map(source -> declarationOf(baseParsed, source.simpleName(), member, field))
                        .collect(Collectors.joining("\n"));
                pullUps.add(DetectedTransformation.withDiff(
                        field ? TransformationKind.PULL_UP_FIELD : TransformationKind.PULL_UP_SYMBOL,
                        involved, files, before, declarationOf(headParsed, newBase.simpleName(), member, field)));
            }
        }
        if (pullUps.isEmpty()) {
            return results;
        }
        List<DetectedTransformation> remaining = results.stream()
                .filter(t -> !PULLED_UP_KINDS.contains(t.kind()) || !covered.containsAll(t.involvedDescriptions()))
                .collect(Collectors.toCollection(ArrayList::new));
        remaining.addAll(pullUps);
        return remaining;
    }

    private static final Set<TransformationKind> PULLED_UP_KINDS = EnumSet.of(
            TransformationKind.MOVE_FIELD, TransformationKind.REMOVE_FIELD, TransformationKind.ADD_FIELD,
            TransformationKind.MOVE_SYMBOL, TransformationKind.REMOVE_SYMBOL, TransformationKind.ADD_SYMBOL);

    /** Whether {@code type} extends {@code ancestor} in head, directly or through other head classes. */
    private static boolean extendsTransitively(ClassInfo type, ClassInfo ancestor, List<ClassInfo> headClasses) {
        Set<String> visited = new LinkedHashSet<>();
        ClassInfo current = type;
        while (current != null && !current.superclass().isEmpty() && visited.add(current.simpleName())) {
            String superclass = current.superclass();
            if (simpleNameOf(ancestor.simpleName()).equals(superclass)) {
                return true;
            }
            current = headClasses.stream().filter(c -> simpleNameOf(c.simpleName()).equals(superclass)).findFirst().orElse(null);
        }
        return false;
    }

    private static String simpleNameOf(String qualifiedName) {
        return qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
    }

    /** The member name from a signature: "field:name:Type" or "method:name(Params):Return". */
    private static String memberName(String signature) {
        String rest = signature.substring(signature.indexOf(':') + 1);
        return rest.substring(0, Math.min(indexOrEnd(rest, '('), indexOrEnd(rest, ':')));
    }

    private static int indexOrEnd(String text, char c) {
        int index = text.indexOf(c);
        return index < 0 ? text.length() : index;
    }

    private static String declarationOf(ParsedRoot parsed, String enclosingType, String member, boolean field) {
        if (field) {
            return parsed.fields().stream()
                    .filter(f -> f.enclosingType().equals(enclosingType) && f.name().equals(member))
                    .map(FieldInfo::rawDeclaration).findFirst().orElse("");
        }
        return parsed.methods().stream()
                .filter(m -> m.enclosingType().equals(enclosingType) && m.name().equals(member))
                .map(MethodInfo::rawWholeDeclaration).findFirst().orElse("");
    }

    private List<BodyEdit> constructorBodyEdits(List<ConstructorInfo> baseConstructors, List<ConstructorInfo> headConstructors) {
        List<BodyEdit> edits = new ArrayList<>();
        for (ConstructorInfo base : baseConstructors) {
            headConstructors.stream()
                    .filter(head -> head.file.equals(base.file) && head.enclosingType.equals(base.enclosingType)
                            && head.parameterTypes.equals(base.parameterTypes))
                    .filter(head -> !head.normalizedBody.equals(base.normalizedBody))
                    .findFirst()
                    .ifPresent(head -> edits.add(new BodyEdit(base.enclosingType + "#<init>", base.file, head.file,
                            base.normalizedBody, head.normalizedBody, base.rawDeclaration, head.rawDeclaration,
                            base.bodySummary.describeChangeTo(head.bodySummary))));
        }
        return edits;
    }

    private List<DetectedTransformation> bodyModifications(List<BodyEdit> edits, List<DetectedTransformation> mechanicalReplacements) {
        List<String[]> replacements = mechanicalReplacements.stream()
                .map(r -> r.involvedDescriptions().get(0).split(" -> "))
                .toList();
        List<DetectedTransformation> results = new ArrayList<>();
        for (BodyEdit edit : edits) {
            String replaced = edit.baseBody();
            for (String[] replacement : replacements) {
                replaced = replaceWholeIdentifier(replaced, replacement[0], replacement[1]);
            }
            if (!replaced.equals(edit.headBody())) {
                results.add(DetectedTransformation.withDiff(TransformationKind.MODIFY_METHOD_BODY,
                        List.of(edit.description(), edit.summary()), List.of(edit.baseFile(), edit.headFile()),
                        edit.baseDeclaration(), edit.headDeclaration()));
            }
        }
        return results;
    }

    /**
     * A matched method's or constructor's body change, pending the mechanical-replacement check.
     * {@code summary} says what changed inside it (ticket #288, see {@link BodySummary}).
     */
    private record BodyEdit(String description, String baseFile, String headFile, String baseBody, String headBody,
                            String baseDeclaration, String headDeclaration, String summary) {
    }

    private static <T> List<T> excluding(List<T> declarations, Function<T, String> enclosingType, Set<String> excludedTypes) {
        return declarations.stream().filter(d -> !excludedTypes.contains(enclosingType.apply(d))).toList();
    }

    private List<DetectedTransformation> detectEnumConstantChanges(List<EnumConstantInfo> baseConstants,
                                                                    List<EnumConstantInfo> headConstants) {
        Set<String> baseNames = baseConstants.stream().map(EnumConstantInfo::key).collect(Collectors.toSet());
        Set<String> headNames = headConstants.stream().map(EnumConstantInfo::key).collect(Collectors.toSet());
        List<DetectedTransformation> results = new ArrayList<>();
        for (EnumConstantInfo base : baseConstants) {
            if (!headNames.contains(base.key())) {
                results.add(DetectedTransformation.withDiff(TransformationKind.REMOVE_ENUM_CONSTANT,
                        List.of(base.description()), List.of(base.file()), base.rawDeclaration(), ""));
            }
        }
        for (EnumConstantInfo head : headConstants) {
            if (!baseNames.contains(head.key())) {
                results.add(DetectedTransformation.withDiff(TransformationKind.ADD_ENUM_CONSTANT,
                        List.of(head.description()), List.of(head.file()), "", head.rawDeclaration()));
            }
        }
        return results;
    }

    private List<DetectedTransformation> detectAnnotationElementChanges(List<AnnotationElementInfo> baseElements,
                                                                         List<AnnotationElementInfo> headElements) {
        Map<String, AnnotationElementInfo> headByDescription = new LinkedHashMap<>();
        headElements.forEach(head -> headByDescription.put(head.key(), head));
        Set<String> baseDescriptions = baseElements.stream().map(AnnotationElementInfo::key).collect(Collectors.toSet());

        List<DetectedTransformation> results = new ArrayList<>();
        for (AnnotationElementInfo base : baseElements) {
            AnnotationElementInfo head = headByDescription.get(base.key());
            if (head == null) {
                results.add(DetectedTransformation.withDiff(TransformationKind.REMOVE_ANNOTATION_ELEMENT,
                        List.of(base.description()), List.of(base.file()), base.rawDeclaration(), ""));
            } else if (base.type().equals(head.type()) && !base.defaultValue().equals(head.defaultValue())) {
                results.add(DetectedTransformation.withDiff(TransformationKind.CHANGE_ANNOTATION_ELEMENT_DEFAULT,
                        List.of(base.description()), List.of(base.file(), head.file()),
                        base.rawDeclaration(), head.rawDeclaration()));
            }
        }
        for (AnnotationElementInfo head : headElements) {
            if (!baseDescriptions.contains(head.key())) {
                results.add(DetectedTransformation.withDiff(TransformationKind.ADD_ANNOTATION_ELEMENT,
                        List.of(head.description()), List.of(head.file()), "", head.rawDeclaration()));
            }
        }
        return results;
    }

    /** A constructor matched by enclosing type + parameter types whose parameter annotations changed (ticket #268). */
    private List<DetectedTransformation> detectConstructorParameterAnnotationChanges(List<ConstructorInfo> baseConstructors,
                                                                                    List<ConstructorInfo> headConstructors) {
        List<DetectedTransformation> results = new ArrayList<>();
        for (ConstructorInfo base : baseConstructors) {
            headConstructors.stream()
                    .filter(head -> head.file.equals(base.file) && head.enclosingType.equals(base.enclosingType)
                            && head.parameterTypes.equals(base.parameterTypes))
                    .findFirst()
                    .flatMap(head -> base.parameterAnnotations.describeChangesTo(head.parameterAnnotations)
                            .map(changes -> DetectedTransformation.withDiff(TransformationKind.CHANGE_PARAMETER_ANNOTATIONS,
                                    List.of(base.enclosingType + "#<init>", changes), List.of(base.file, head.file),
                                    base.rawDeclaration, head.rawDeclaration)))
                    .ifPresent(results::add);
        }
        return results;
    }

    private List<DetectedTransformation> detectAddedConstructorParameters(List<ConstructorInfo> baseConstructors,
                                                                            List<ConstructorInfo> headConstructors) {
        List<DetectedTransformation> results = new ArrayList<>();
        // A class can declare more than one constructor (overloads); a parameter counts as
        // "already present in base" if ANY of the class's base constructors already had it —
        // keying by enclosing type alone (one ConstructorInfo per class) would silently drop
        // every base overload but one and produce false positives for a parameter that was
        // genuinely already present in a different base overload.
        Map<String, Set<String>> baseParameterNamesByEnclosingType = new LinkedHashMap<>();
        Map<String, ConstructorInfo> representativeBaseByEnclosingType = new LinkedHashMap<>();
        for (ConstructorInfo c : baseConstructors) {
            baseParameterNamesByEnclosingType.computeIfAbsent(c.file + "|" + c.enclosingType, k -> new HashSet<>())
                    .addAll(c.parameterNames);
            representativeBaseByEnclosingType.putIfAbsent(c.file + "|" + c.enclosingType, c);
        }

        for (ConstructorInfo head : headConstructors) {
            Set<String> baseParameterNames = baseParameterNamesByEnclosingType.getOrDefault(head.file + "|" + head.enclosingType, Set.of());
            ConstructorInfo representativeBase = representativeBaseByEnclosingType.get(head.file + "|" + head.enclosingType);
            for (String parameterName : head.parameterNames) {
                if (!baseParameterNames.contains(parameterName) && head.assignedFieldNames.contains(parameterName)) {
                    // Description format deliberately matches FieldInfo#description()'s
                    // "EnclosingType#name" exactly (parameterType kept out of it) so
                    // PatternTaxonomyClassifier can correlate this against a REMOVE_FIELD
                    // transformation for the same name by simple string equality.
                    results.add(DetectedTransformation.withDiff(TransformationKind.ADD_CONSTRUCTOR_PARAMETER,
                            List.of(head.enclosingType + "#" + parameterName), List.of(head.file),
                            representativeBase == null ? "" : representativeBase.rawDeclaration, head.rawDeclaration));
                }
            }
        }
        return results;
    }

    private List<DetectedTransformation> detectFieldChanges(List<FieldInfo> baseFields, List<FieldInfo> headFields) {
        List<DetectedTransformation> results = new ArrayList<>();
        Set<FieldInfo> matchedBase = new LinkedHashSet<>();
        Set<FieldInfo> matchedHead = new LinkedHashSet<>();

        Map<FieldKey, List<FieldInfo>> headByKey = new LinkedHashMap<>();
        for (FieldInfo f : headFields) {
            headByKey.computeIfAbsent(new FieldKey(f.file, f.enclosingType, f.name), k -> new ArrayList<>()).add(f);
        }

        // 1. Exact match: same enclosing type + same name + same declared type — this is
        // what makes it genuinely "the same field" rather than an unrelated field that
        // happens to reuse the name (a same-name field whose type changed is handled by step
        // 1b below as a field type change).
        for (FieldInfo base : baseFields) {
            List<FieldInfo> candidates = headByKey.get(new FieldKey(base.file, base.enclosingType, base.name));
            FieldInfo head = candidates == null ? null : candidates.stream()
                    .filter(h -> !matchedHead.contains(h) && h.type.equals(base.type)).findFirst().orElse(null);
            if (head == null) continue;
            matchedBase.add(base);
            matchedHead.add(head);
            // The field itself (name + declared type) is unchanged, but its annotations may
            // have changed — e.g. @Autowired removed as part of moving to constructor
            // injection (ticket #86's dependency-injection example). That's the one
            // declaration-level fact this detector does report for an otherwise-matched
            // field; anything else about the declaration text (initializer, modifiers) stays
            // out of scope as behavioral, not structural.
            if (!base.annotationNames.equals(head.annotationNames)) {
                results.add(DetectedTransformation.withDiff(TransformationKind.CHANGE_FIELD_ANNOTATIONS,
                        List.of(base.description()), List.of(base.file, head.file),
                        base.rawDeclaration, head.rawDeclaration));
            }
        }

        // 1b. Same enclosing type + same name, different declared type (ticket #267): one field
        // whose type changed, not an unrelated remove + add. Its visibility is part of the
        // description when it isn't private — a non-private field's type change can break
        // subclasses and callers, which is exactly what a reviewer needs to see here.
        for (FieldInfo base : baseFields) {
            if (matchedBase.contains(base)) continue;
            List<FieldInfo> candidates = headByKey.get(new FieldKey(base.file, base.enclosingType, base.name));
            FieldInfo head = candidates == null ? null : candidates.stream()
                    .filter(h -> !matchedHead.contains(h)).findFirst().orElse(null);
            if (head == null) continue;
            matchedBase.add(base);
            matchedHead.add(head);
            String visibilityNote = head.visibility.equals("private") ? "" : " (" + head.visibility + ")";
            results.add(DetectedTransformation.withDiff(TransformationKind.CHANGE_FIELD_TYPE,
                    List.of(base.description(), base.type + " -> " + head.type + visibilityNote),
                    List.of(base.file, head.file), base.rawDeclaration, head.rawDeclaration));
            // A retyped field can also have changed annotations (e.g. @Autowired dropped while
            // moving to constructor injection) — a separate fact the framework correlators need.
            if (!base.annotationNames.equals(head.annotationNames)) {
                results.add(DetectedTransformation.withDiff(TransformationKind.CHANGE_FIELD_ANNOTATIONS,
                        List.of(base.description()), List.of(base.file, head.file),
                        base.rawDeclaration, head.rawDeclaration));
            }
        }

        List<FieldInfo> unmatchedBase = baseFields.stream().filter(f -> !matchedBase.contains(f)).toList();
        List<FieldInfo> unmatchedHead = headFields.stream().filter(f -> !matchedHead.contains(f)).toList();

        // 2 & 3. Rename/move: matched by declared type equality, mirroring the method
        // rename/move detector's "same body" match key.
        Set<FieldInfo> stillUnmatchedBase = new LinkedHashSet<>(unmatchedBase);
        Set<FieldInfo> stillUnmatchedHead = new LinkedHashSet<>(unmatchedHead);
        Map<String, List<FieldInfo>> unmatchedHeadByType = new LinkedHashMap<>();
        for (FieldInfo f : stillUnmatchedHead) {
            unmatchedHeadByType.computeIfAbsent(f.type, k -> new ArrayList<>()).add(f);
        }

        for (FieldInfo base : unmatchedBase) {
            if (!stillUnmatchedBase.contains(base)) continue;
            List<FieldInfo> candidates = unmatchedHeadByType.get(base.type);
            if (candidates == null) continue;

            FieldInfo renameMatch = null;
            FieldInfo moveMatch = null;
            for (FieldInfo head : candidates) {
                if (!stillUnmatchedHead.contains(head)) continue;
                if (base.file.equals(head.file) && base.enclosingType.equals(head.enclosingType) && !base.name.equals(head.name)) {
                    renameMatch = head;
                    break;
                }
                if (moveMatch == null && !base.enclosingType.equals(head.enclosingType) && base.name.equals(head.name)) {
                    moveMatch = head;
                }
            }

            if (renameMatch != null) {
                results.add(DetectedTransformation.withDiff(TransformationKind.RENAME_FIELD,
                        List.of(base.description(), renameMatch.description()), List.of(base.file, renameMatch.file),
                        base.rawDeclaration, renameMatch.rawDeclaration));
                stillUnmatchedBase.remove(base);
                stillUnmatchedHead.remove(renameMatch);
            } else if (moveMatch != null) {
                results.add(DetectedTransformation.withDiff(TransformationKind.MOVE_FIELD,
                        List.of(base.description(), moveMatch.description()), List.of(base.file, moveMatch.file),
                        base.rawDeclaration, moveMatch.rawDeclaration));
                stillUnmatchedBase.remove(base);
                stillUnmatchedHead.remove(moveMatch);
            }
        }

        // 5 (no step 4 - no field equivalent of extract-method). Remaining fields -> removed/added.
        // The enclosing types' annotations travel with an added/removed field (ticket #295): a
        // framework plugin sees only Changes, yet e.g. a @ConfigurationProperties prefix decides
        // which configuration property the field is.
        for (FieldInfo base : stillUnmatchedBase) {
            results.add(DetectedTransformation.withDiff(TransformationKind.REMOVE_FIELD,
                    List.of(base.description()), List.of(base.file), base.rawDeclaration, "")
                    .withContext(DetectedTransformation.ENCLOSING_TYPE_ANNOTATIONS, base.enclosingTypeAnnotations()));
        }
        for (FieldInfo head : stillUnmatchedHead) {
            results.add(DetectedTransformation.withDiff(TransformationKind.ADD_FIELD,
                    List.of(head.description()), List.of(head.file), "", head.rawDeclaration)
                    .withContext(DetectedTransformation.ENCLOSING_TYPE_ANNOTATIONS, head.enclosingTypeAnnotations()));
        }

        return results;
    }

    /** Same file, enclosing type and name — see {@link MethodKey} for why the file is part of it. */
    private record FieldKey(String file, String enclosingType, String name) {
    }

    private Map<MethodKey, List<MethodInfo>> indexByKey(List<MethodInfo> methods) {
        Map<MethodKey, List<MethodInfo>> index = new LinkedHashMap<>();
        for (MethodInfo m : methods) {
            index.computeIfAbsent(new MethodKey(m.file, m.enclosingType, m.name), k -> new ArrayList<>()).add(m);
        }
        return index;
    }

    private Map<String, List<MethodInfo>> indexByBody(Set<MethodInfo> methods) {
        Map<String, List<MethodInfo>> index = new LinkedHashMap<>();
        for (MethodInfo m : methods) {
            index.computeIfAbsent(m.normalizedBody, k -> new ArrayList<>()).add(m);
        }
        return index;
    }

    /** The first candidate (in original order) not already in {@code excluded}, or null. */
    private MethodInfo firstUnmatched(List<MethodInfo> candidates, Set<MethodInfo> excluded) {
        if (candidates == null) return null;
        for (MethodInfo candidate : candidates) {
            if (!excluded.contains(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * "The same method in both revisions": same file, enclosing type and name. The file is
     * part of the key because unrelated types in different packages can share a simple name
     * (spring-framework has several {@code Person}s) — without it they were paired across
     * files and reported as changes in files the PR never touched.
     */
    private record MethodKey(String file, String enclosingType, String name) {
    }

    private record FileAndName(String file, String simpleName) {
    }

    /**
     * Class-level rename/move/add/remove, matched the same way method rename/move is:
     * an exact-equality "body" key — here, the class's member-signature-set — decides
     * whether a same-file-different-name pair is a rename, a same-name-different-file
     * pair is a move, or neither (in which case both sides fall through as an ordinary
     * add+remove, exactly like an unmatched method would).
     */
    private ClassMatchResult detectClassChanges(List<ClassInfo> baseClasses, List<ClassInfo> headClasses) {
        List<DetectedTransformation> results = new ArrayList<>();
        Set<String> excludedBaseTypes = new LinkedHashSet<>();
        Set<String> excludedHeadTypes = new LinkedHashSet<>();
        Set<ClassInfo> matchedBase = new LinkedHashSet<>();
        Set<ClassInfo> matchedHead = new LinkedHashSet<>();

        // 1. Exact match: same file + same simple name -> unchanged or formatting-only.
        // (A class matched here is NOT excluded from member-level matching below — its
        // members may still have individually changed, which is exactly what the flat
        // method/field detection is for.)
        Map<FileAndName, ClassInfo> headByFileAndName = new LinkedHashMap<>();
        for (ClassInfo c : headClasses) {
            headByFileAndName.put(new FileAndName(c.file, c.simpleName), c);
        }
        for (ClassInfo base : baseClasses) {
            ClassInfo head = headByFileAndName.get(new FileAndName(base.file, base.simpleName));
            if (head == null) continue;
            matchedBase.add(base);
            matchedHead.add(head);
            if (!base.headerText.equals(head.headerText) && base.memberSignatures.equals(head.memberSignatures)
                    && normalize(base.headerText).equals(normalize(head.headerText))) {
                results.add(DetectedTransformation.withDiff(TransformationKind.FORMATTING_ONLY,
                        List.of(base.simpleName), List.of(base.file, head.file), base.headerText, head.headerText));
            }
        }

        List<ClassInfo> unmatchedBase = baseClasses.stream().filter(c -> !matchedBase.contains(c)).toList();
        List<ClassInfo> unmatchedHead = headClasses.stream().filter(c -> !matchedHead.contains(c)).toList();

        // 2 & 3. Rename/move: matched by memberSignatures equality (this class's "body").
        Set<ClassInfo> stillUnmatchedBase = new LinkedHashSet<>(unmatchedBase);
        Set<ClassInfo> stillUnmatchedHead = new LinkedHashSet<>(unmatchedHead);
        Map<Set<String>, List<ClassInfo>> unmatchedHeadByMembers = new LinkedHashMap<>();
        for (ClassInfo c : stillUnmatchedHead) {
            unmatchedHeadByMembers.computeIfAbsent(c.memberSignatures, k -> new ArrayList<>()).add(c);
        }

        for (ClassInfo base : unmatchedBase) {
            if (!stillUnmatchedBase.contains(base)) continue;
            List<ClassInfo> candidates = unmatchedHeadByMembers.get(base.memberSignatures);
            if (candidates == null || base.memberSignatures.isEmpty()) continue;

            ClassInfo renameMatch = null;
            ClassInfo moveMatch = null;
            for (ClassInfo head : candidates) {
                if (!stillUnmatchedHead.contains(head)) continue;
                if (base.file.equals(head.file) && !base.simpleName.equals(head.simpleName)) {
                    renameMatch = head;
                    break;
                }
                if (moveMatch == null && !base.file.equals(head.file) && base.simpleName.equals(head.simpleName)) {
                    moveMatch = head;
                }
            }

            if (renameMatch != null) {
                results.add(DetectedTransformation.withDiff(TransformationKind.RENAME_CLASS,
                        List.of(base.simpleName, renameMatch.simpleName), List.of(base.file, renameMatch.file),
                        base.headerText, renameMatch.headerText));
                stillUnmatchedBase.remove(base);
                stillUnmatchedHead.remove(renameMatch);
                excludedBaseTypes.add(base.simpleName);
                excludedHeadTypes.add(renameMatch.simpleName);
            } else if (moveMatch != null) {
                results.add(DetectedTransformation.withDiff(TransformationKind.MOVE_CLASS,
                        List.of(base.simpleName, moveMatch.simpleName), List.of(base.file, moveMatch.file),
                        base.headerText, moveMatch.headerText));
                stillUnmatchedBase.remove(base);
                stillUnmatchedHead.remove(moveMatch);
                excludedBaseTypes.add(base.simpleName);
                excludedHeadTypes.add(moveMatch.simpleName);
            }
        }

        // 5 (no step 4 - no class equivalent of extract-method here). Remaining -> removed/added.
        for (ClassInfo base : stillUnmatchedBase) {
            results.add(DetectedTransformation.withDiff(TransformationKind.REMOVE_CLASS,
                    List.of(base.simpleName), List.of(base.file), base.headerText, ""));
        }
        for (ClassInfo head : stillUnmatchedHead) {
            results.add(DetectedTransformation.withDiff(TransformationKind.ADD_CLASS,
                    List.of(head.simpleName), List.of(head.file), "", head.headerText));
        }

        return new ClassMatchResult(results, excludedBaseTypes, excludedHeadTypes);
    }

    private record ClassMatchResult(List<DetectedTransformation> transformations, Set<String> excludedBaseTypes,
                                     Set<String> excludedHeadTypes) {
    }

    private List<DetectedTransformation> detectRecordSignatureChanges(Map<String, RecordInfo> baseRecords,
                                                                        Map<String, RecordInfo> headRecords) {
        List<DetectedTransformation> results = new ArrayList<>();
        for (Map.Entry<String, RecordInfo> entry : baseRecords.entrySet()) {
            RecordInfo base = entry.getValue();
            RecordInfo head = headRecords.get(entry.getKey());
            if (head != null && !base.componentTypes.equals(head.componentTypes)) {
                results.add(DetectedTransformation.withDiff(TransformationKind.CHANGE_METHOD_SIGNATURE,
                        List.of(entry.getKey() + "#<init>"), List.of(base.file, head.file),
                        base.headerText, head.headerText));
            }
        }
        return results;
    }

    private record RecordInfo(List<String> componentTypes, String file, String headerText) {
    }

    /**
     * A whole-identifier textual substitution applied identically in every changed file that
     * referenced the old identifier. Scoped to the change (ticket #270): candidate identifiers
     * come only from base lines that no longer appear in head, and only files whose content
     * differs are checked — an identical file can't contribute an occurrence, it only used to
     * cost a full scan per candidate (every identifier of the repository × every file). A
     * replacement cites only the files it occurs in.
     */
    private List<DetectedTransformation> detectMechanicalReplacements(Path baseRoot, Path headRoot,
                                                                       List<String> editedFiles) {
        Map<String, String> baseTextByFile = new LinkedHashMap<>();
        Map<String, String> headTextByFile = new LinkedHashMap<>();
        for (String rel : editedFiles) {
            baseTextByFile.put(rel, readFile(baseRoot.resolve(rel)));
            headTextByFile.put(rel, readFile(headRoot.resolve(rel)));
        }

        Set<String> candidateIdentifiers = new LinkedHashSet<>();
        for (Map.Entry<String, String> changed : baseTextByFile.entrySet()) {
            Set<String> headLines = new HashSet<>(headTextByFile.get(changed.getKey()).lines().toList());
            changed.getValue().lines()
                    .filter(line -> !headLines.contains(line))
                    .forEach(removedLine -> candidateIdentifiers.addAll(extractIdentifiers(removedLine)));
        }

        List<DetectedTransformation> replacements = new ArrayList<>();
        for (String oldIdentifier : candidateIdentifiers) {
            replacementOf(oldIdentifier, baseTextByFile, headTextByFile).ifPresent(replacements::add);
        }
        return replacements;
    }

    /** {@code oldIdentifier}'s replacement, if every changed file referencing it replaced it with the same identifier. */
    private Optional<DetectedTransformation> replacementOf(String oldIdentifier, Map<String, String> baseTextByFile,
                                                           Map<String, String> headTextByFile) {
        String newIdentifier = null;
        List<String> occurrenceFiles = new ArrayList<>();
        for (Map.Entry<String, String> changed : baseTextByFile.entrySet()) {
            String baseText = changed.getValue();
            String headText = headTextByFile.get(changed.getKey());
            if (!containsWholeIdentifier(baseText, oldIdentifier)) continue;
            if (newIdentifier == null) {
                // Infer the replacement from the actual head text at the first occurrence.
                newIdentifier = inferReplacement(baseText, headText, oldIdentifier);
                if (newIdentifier == null) return Optional.empty();
            }
            if (!replaceWholeIdentifier(baseText, oldIdentifier, newIdentifier).equals(headText)) {
                return Optional.empty();
            }
            occurrenceFiles.add(changed.getKey());
        }
        if (occurrenceFiles.isEmpty() || newIdentifier.equals(oldIdentifier)) {
            return Optional.empty();
        }
        return Optional.of(DetectedTransformation.withOccurrences(TransformationKind.MECHANICAL_REPLACEMENT,
                List.of(oldIdentifier + " -> " + newIdentifier), occurrenceFiles, occurrenceFiles.size()));
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

    /** True if {@code callerHead} existed in base (same enclosing type + name) with a different body. */
    private boolean bodyChanged(Map<MethodKey, List<MethodInfo>> baseByKey, MethodInfo callerHead) {
        List<MethodInfo> candidates = baseByKey.get(new MethodKey(callerHead.file, callerHead.enclosingType, callerHead.name));
        return candidates != null && candidates.stream().noneMatch(base -> base.normalizedBody.equals(callerHead.normalizedBody));
    }

    private boolean baseHasInlineEquivalent(Map<MethodKey, List<MethodInfo>> baseByKey, MethodInfo callerHead,
                                             MethodInfo extractedHead) {
        // The base version of the calling method (same enclosing type + name) must exist
        // and its body, once the extracted fragment's statements are considered, should
        // contain the same normalized statements the extracted method now holds.
        List<MethodInfo> candidates = baseByKey.get(new MethodKey(callerHead.file, callerHead.enclosingType, callerHead.name));
        if (candidates == null) return false;
        return candidates.stream()
                .anyMatch(baseMethod -> containsNormalizedFragment(baseMethod.normalizedBody, extractedHead.normalizedBody));
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

    /**
     * Parses each of {@code relativePaths} (the changed files, ticket #271) that exists under
     * {@code root}, exactly once, recording the ones that fail in {@code unparseable}. Every
     * declaration view is later extracted from the same {@link CompilationUnit} — parsing
     * dominates the cost; extracting several views from one AST is cheap.
     */
    private Map<String, ParsedFile> parseFiles(Path root, List<String> relativePaths, Set<String> unparseable) {
        Map<String, ParsedFile> parsed = new LinkedHashMap<>();
        for (String relativePath : relativePaths) {
            Path file = root.resolve(relativePath);
            if (!Files.isRegularFile(file)) continue;
            try {
                StaticJavaParser.setConfiguration(JavaParserConfigurations.currentJava());
                parsed.put(relativePath, new ParsedFile(StaticJavaParser.parse(file), Files.readAllLines(file)));
            } catch (IOException | RuntimeException e) {
                unparseable.add(relativePath);
            }
        }
        return parsed;
    }

    private ParsedRoot collectDeclarations(Map<String, ParsedFile> files, Set<String> excluded) {
        DeclarationCollector collected = new DeclarationCollector();
        for (Map.Entry<String, ParsedFile> file : files.entrySet()) {
            if (excluded.contains(file.getKey())) continue;
            for (TypeDeclaration<?> type : file.getValue().unit().getTypes()) {
                collectType(type, type.getNameAsString(), "", file.getKey(), file.getValue().sourceLines(), collected);
            }
        }
        return collected.toParsedRoot();
    }

    private record ParsedFile(CompilationUnit unit, List<String> sourceLines) {
    }

    /**
     * Collects one type's own members, then recurses into its member types (ticket #265):
     * nested and inner classes are analyzed like top-level ones, keyed by their qualified
     * name ({@code Outer.Inner}) so same-named nested types in different outer classes
     * never collide. A type's member signatures cover its own members only — a nested
     * type is a separate {@link ClassInfo}, not part of its outer class's identity.
     */
    private void collectType(TypeDeclaration<?> type, String qualifiedName, String outerTypeAnnotations,
                             String relativePath, List<String> sourceLines, DeclarationCollector collected) {
        String typeAnnotations = (outerTypeAnnotations.isEmpty() ? "" : outerTypeAnnotations + "\n")
                + type.getNameAsString() + "\t"
                + type.getAnnotations().stream().map(Node::toString).collect(Collectors.joining(" "));
        Set<String> memberSignatures = new TreeSet<>();
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
            collected.methods.add(new MethodInfo(qualifiedName, method.getNameAsString(),
                    paramTypes, method.getTypeAsString(),
                    wholeDeclarationText, normalize(wholeDeclarationText),
                    normalize(bodyOnlyText), relativePath, parameterAnnotationsOf(method.getParameters()),
                    methodAnnotationsOf(method), ControlFlow.of(method), BodySummary.of(method)));
            memberSignatures.add("method:" + method.getNameAsString() + "(" + String.join(",", paramTypes) + "):"
                    + method.getTypeAsString());
        }
        for (FieldDeclaration field : type.getMembers().stream()
                .filter(FieldDeclaration.class::isInstance).map(FieldDeclaration.class::cast).toList()) {
            String rawDeclaration = field.getRange()
                    .map(range -> sourceSlice(sourceLines, range))
                    .orElse(field.toString());
            Set<String> annotationNames = field.getAnnotations().stream()
                    .map(a -> a.getName().getIdentifier())
                    .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
            for (VariableDeclarator variable : field.getVariables()) {
                String typeAsString = variable.getType().asString();
                collected.fields.add(new FieldInfo(qualifiedName, variable.getNameAsString(), typeAsString,
                        annotationNames, visibilityOf(field), rawDeclaration, relativePath, typeAnnotations));
                memberSignatures.add("field:" + variable.getNameAsString() + ":" + typeAsString);
            }
        }
        // Initializer blocks are pseudo-members (#264): an edit inside `static { ... }` — e.g.
        // to an anonymous class registered there — would otherwise be invisible. They take the
        // JVM's names so each is matched to its counterpart like a method of the same name.
        for (InitializerDeclaration initializer : type.getMembers().stream()
                .filter(InitializerDeclaration.class::isInstance).map(InitializerDeclaration.class::cast).toList()) {
            String name = initializer.isStatic() ? "<clinit>" : "<instance-init>";
            String rawDeclaration = initializer.getRange()
                    .map(range -> sourceSlice(sourceLines, range))
                    .orElse(initializer.toString());
            String bodyText = initializer.getBody().getRange()
                    .map(range -> sourceSlice(sourceLines, range))
                    .orElse(initializer.getBody().toString());
            collected.methods.add(new MethodInfo(qualifiedName, name, List.of(), "void",
                    rawDeclaration, normalize(rawDeclaration), normalize(bodyText), relativePath,
                    new ParameterAnnotations(List.of(), List.of()), Set.of(), ControlFlow.of(initializer.getBody()),
                    BodySummary.of(initializer.getBody())));
        }
        for (ConstructorDeclaration constructor : type.getConstructors()) {
            String rawDeclaration = constructor.getRange()
                    .map(range -> sourceSlice(sourceLines, range))
                    .orElse(constructor.toString());
            String bodyText = constructor.getBody().getRange()
                    .map(range -> sourceSlice(sourceLines, range))
                    .orElse(constructor.getBody().toString());
            List<String> paramTypes = constructor.getParameters().stream()
                    .map(Parameter::getTypeAsString).toList();
            List<String> paramNames = constructor.getParameters().stream()
                    .map(Parameter::getNameAsString).toList();
            Set<String> assignedFieldNames = paramNames.stream()
                    .filter(name -> assignsParameterToSameNamedField(bodyText, name))
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            collected.constructors.add(new ConstructorInfo(qualifiedName, paramTypes, paramNames,
                    assignedFieldNames, rawDeclaration, relativePath, parameterAnnotationsOf(constructor.getParameters()),
                    normalize(bodyText), BodySummary.of(constructor.getBody())));
        }
        if (type instanceof EnumDeclaration enumDeclaration) {
            for (EnumConstantDeclaration constant : enumDeclaration.getEntries()) {
                String rawDeclaration = constant.getRange()
                        .map(range -> sourceSlice(sourceLines, range))
                        .orElse(constant.toString());
                collected.enumConstants.add(new EnumConstantInfo(qualifiedName, constant.getNameAsString(),
                        rawDeclaration, relativePath));
                memberSignatures.add("constant:" + constant.getNameAsString());
            }
        }
        for (AnnotationMemberDeclaration element : type.getMembers().stream()
                .filter(AnnotationMemberDeclaration.class::isInstance).map(AnnotationMemberDeclaration.class::cast).toList()) {
            String rawDeclaration = element.getRange()
                    .map(range -> sourceSlice(sourceLines, range))
                    .orElse(element.toString());
            collected.annotationElements.add(new AnnotationElementInfo(qualifiedName, element.getNameAsString(),
                    element.getTypeAsString(), element.getDefaultValue().map(Node::toString), rawDeclaration, relativePath));
            memberSignatures.add("element:" + element.getNameAsString() + ":" + element.getTypeAsString());
        }
        if (type instanceof RecordDeclaration record) {
            List<String> componentTypes = record.getParameters().stream()
                    .map(Parameter::getTypeAsString).toList();
            String headerText = "record " + record.getNameAsString() + "("
                    + String.join(", ", record.getParameters().stream()
                            .map(p -> p.getTypeAsString() + " " + p.getNameAsString()).toList())
                    + ")";
            collected.records.put(qualifiedName, new RecordInfo(componentTypes, relativePath, headerText));
        } else {
            String headerText = type.getRange().map(range -> sourceSlice(sourceLines, range))
                    .orElse(type.toString());
            String superclass = type instanceof ClassOrInterfaceDeclaration declaration && !declaration.isInterface()
                    ? declaration.getExtendedTypes().getFirst().map(ClassOrInterfaceType::getNameAsString).orElse("")
                    : "";
            collected.classes.add(new ClassInfo(qualifiedName, relativePath, memberSignatures, headerText, superclass));
        }
        for (BodyDeclaration<?> member : type.getMembers()) {
            if (member instanceof TypeDeclaration<?> nested) {
                collectType(nested, qualifiedName + "." + nested.getNameAsString(), typeAnnotations, relativePath,
                        sourceLines, collected);
            }
        }
    }

    private static ParameterAnnotations parameterAnnotationsOf(List<Parameter> parameters) {
        List<String> names = parameters.stream().map(Parameter::getNameAsString).toList();
        List<Set<String>> annotations = parameters.stream().map(parameter -> {
            Set<String> annotationNames = new TreeSet<>();
            parameter.getAnnotations().forEach(a -> annotationNames.add("@" + a.getName().getIdentifier()));
            parameter.getVarArgsAnnotations().forEach(a -> annotationNames.add("@" + a.getName().getIdentifier()));
            parameter.getType().getAnnotations().forEach(a -> annotationNames.add("@" + a.getName().getIdentifier()));
            return (Set<String>) annotationNames;
        }).toList();
        return new ParameterAnnotations(names, annotations);
    }

    /**
     * The method's contract annotations: its own and its return type's. {@code @Override} is left
     * out (ticket #296) — a compiler check, not part of the contract, so adding or removing it
     * alone is never an annotation change.
     */
    private static Set<String> methodAnnotationsOf(MethodDeclaration method) {
        Set<String> names = new TreeSet<>();
        method.getAnnotations().forEach(a -> names.add("@" + a.getName().getIdentifier()));
        method.getType().getAnnotations().forEach(a -> names.add("@" + a.getName().getIdentifier()));
        names.remove("@Override");
        return names;
    }

    private static String visibilityOf(FieldDeclaration field) {
        return switch (field.getAccessSpecifier()) {
            case PUBLIC -> "public";
            case PROTECTED -> "protected";
            case PRIVATE -> "private";
            case NONE -> "package-private";
        };
    }

    /**
     * True if {@code body} assigns the same-named field an expression that uses the parameter:
     * {@code this.<name> = <name>}, or a cast or conditional of it ({@code this.<name> =
     * (Type) <name>}, ticket #294) — the "constructor-injected field" shape. A text check
     * rather than a full AST walk of assignment statements, consistent with this detector's
     * existing lightweight checks for similar structural questions (see {@link #callsMethod}).
     */
    private boolean assignsParameterToSameNamedField(String body, String parameterName) {
        String name = Pattern.quote(parameterName);
        Matcher assignment = Pattern.compile("this\\s*\\.\\s*" + name + "\\s*=([^;]*);").matcher(body);
        Pattern usesParameter = Pattern.compile("\\b" + name + "\\b");
        while (assignment.find()) {
            if (usesParameter.matcher(assignment.group(1)).find()) {
                return true;
            }
        }
        return false;
    }

    private record ParsedRoot(List<MethodInfo> methods, List<FieldInfo> fields, List<ClassInfo> classes,
                               List<ConstructorInfo> constructors, Map<String, RecordInfo> records,
                               List<EnumConstantInfo> enumConstants, List<AnnotationElementInfo> annotationElements) {
    }

    /** Accumulates one root's declarations while {@link #collectType} walks its types. */
    private static final class DeclarationCollector {
        private final List<MethodInfo> methods = new ArrayList<>();
        private final List<FieldInfo> fields = new ArrayList<>();
        private final List<ClassInfo> classes = new ArrayList<>();
        private final List<ConstructorInfo> constructors = new ArrayList<>();
        private final Map<String, RecordInfo> records = new LinkedHashMap<>();
        private final List<EnumConstantInfo> enumConstants = new ArrayList<>();
        private final List<AnnotationElementInfo> annotationElements = new ArrayList<>();

        ParsedRoot toParsedRoot() {
            return new ParsedRoot(List.copyOf(methods), List.copyOf(fields), List.copyOf(classes),
                    List.copyOf(constructors), Map.copyOf(records), List.copyOf(enumConstants),
                    List.copyOf(annotationElements));
        }
    }

    private record EnumConstantInfo(String enclosingType, String name, String rawDeclaration, String file) {
        String description() {
            return enclosingType + "#" + name;
        }

        /** Identity across revisions: file + description (see {@link MethodKey}). */
        String key() {
            return file + "|" + description();
        }
    }

    /** An annotation-type element ({@code String mockMaker() default "";}); {@code defaultValue} is its source text. */
    private record AnnotationElementInfo(String enclosingType, String name, String type, Optional<String> defaultValue,
                                          String rawDeclaration, String file) {
        String description() {
            return enclosingType + "#" + name;
        }

        /** Identity across revisions: file + description (see {@link MethodKey}). */
        String key() {
            return file + "|" + description();
        }
    }

    /**
     * A constructor's shape for detecting "field injection -> constructor injection"
     * (ticket #86): which parameters it declares, and which of those it assigns straight
     * to a same-named field in its body ({@code assignedFieldNames} is the subset of
     * {@code parameterNames} that do — the structural signal a constructor actually stores
     * that parameter as field state, not just uses it transiently).
     */
    private record ConstructorInfo(String enclosingType, List<String> parameterTypes, List<String> parameterNames,
                                    Set<String> assignedFieldNames, String rawDeclaration, String file,
                                    ParameterAnnotations parameterAnnotations, String normalizedBody,
                                    BodySummary bodySummary) {
    }

    /**
     * A non-record type's identity for rename/move matching: {@code memberSignatures}
     * (every method's name+param-types+return-type, every field's name+type, order-
     * independent via TreeSet) plays the same role {@code normalizedBody} plays for a
     * method — the "did the substance stay the same" signal that lets a rename/move be
     * told apart from an unrelated add+remove pair. {@code headerText} is the type's own
     * full source text (used for the class-level diff and formatting-only detection).
     * {@code superclass} is the simple name of the class it extends, empty for none (ticket #290).
     */
    private record ClassInfo(String simpleName, String file, Set<String> memberSignatures, String headerText,
                             String superclass) {
    }

    /**
     * {@code visibility} is the declared access level: public, protected, package-private or
     * private. {@code enclosingTypeAnnotations} is the {@link
     * DetectedTransformation#ENCLOSING_TYPE_ANNOTATIONS} context of the field (ticket #295).
     */
    private record FieldInfo(String enclosingType, String name, String type, Set<String> annotationNames,
                              String visibility, String rawDeclaration, String file, String enclosingTypeAnnotations) {
        String description() {
            return enclosingType + "#" + name;
        }
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


    private String readFile(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Each parameter's annotation names, aligned with the parameter names — compared between
     * two revisions of the same method/constructor to report which parameters gained or lost
     * annotations (ticket #268).
     */
    private record ParameterAnnotations(List<String> parameterNames, List<Set<String>> annotations) {

        /** e.g. {@code "value +@Nullable, values +@Nullable"}, or empty when nothing changed. */
        Optional<String> describeChangesTo(ParameterAnnotations head) {
            List<String> changed = new ArrayList<>();
            for (int i = 0; i < Math.min(annotations.size(), head.annotations.size()); i++) {
                if (!annotations.get(i).equals(head.annotations.get(i))) {
                    changed.add(head.parameterNames.get(i) + " " + describe(annotations.get(i), head.annotations.get(i)));
                }
            }
            return changed.isEmpty() ? Optional.empty() : Optional.of(String.join(", ", changed));
        }

        /** Added annotations as {@code +@A}, removed as {@code -@B}, space-separated. */
        static String describe(Set<String> before, Set<String> after) {
            List<String> parts = new ArrayList<>();
            after.stream().filter(a -> !before.contains(a)).forEach(a -> parts.add("+" + a));
            before.stream().filter(a -> !after.contains(a)).forEach(a -> parts.add("-" + a));
            return String.join(" ", parts);
        }
    }

    /**
     * {@code parameterAnnotations} holds each parameter's annotation names ({@code @Nullable}),
     * aligned with {@code parameterNames}; {@code methodAnnotations} holds the method's own
     * annotations plus those on its return type (ticket #268: both are part of its contract).
     */
    private record MethodInfo(String enclosingType, String name, List<String> parameterTypes, String returnType,
                               String rawWholeDeclaration, String normalizedWholeDeclaration,
                               String normalizedBody, String file, ParameterAnnotations parameterAnnotations,
                               Set<String> methodAnnotations, ControlFlow controlFlow, BodySummary bodySummary) {
        String description() {
            return enclosingType + "#" + name;
        }
    }
}
