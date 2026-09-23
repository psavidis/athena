package com.athena.plugin.java;

import com.athena.semantic.DetectedTransformation;
import com.athena.semantic.TransformationKind;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.AnnotationMemberDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
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
        // One parse pass per root, not two — methodInfos and recordInfos used to each
        // independently call StaticJavaParser.parse() over every file, so every root's
        // source tree was parsed twice for no reason (parsing is the expensive part; the
        // method-vs-record extraction from an already-parsed CompilationUnit is cheap).
        ParsedRoot baseParsed = parseRoot(baseRoot);
        ParsedRoot headParsed = parseRoot(headRoot);

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
            MethodInfo head = firstUnmatched(headByKey.get(new MethodKey(base.enclosingType, base.name)), matchedHead);
            if (head == null) continue;

            matchedBase.add(base);
            matchedHead.add(head);

            if (base.parameterTypes.equals(head.parameterTypes) && base.returnType.equals(head.returnType)) {
                if (base.normalizedWholeDeclaration.equals(head.normalizedWholeDeclaration)) {
                    if (!base.rawWholeDeclaration.equals(head.rawWholeDeclaration)) {
                        results.add(DetectedTransformation.withDiff(TransformationKind.FORMATTING_ONLY,
                                List.of(base.description()), List.of(base.file, head.file),
                                base.rawWholeDeclaration, head.rawWholeDeclaration));
                    }
                    // else: truly identical, nothing to report.
                }
                // else: body differs but signature is the same and structure isn't AST-equal —
                // out of scope for this detector (behavioral changes are ticket #18; a changed
                // method call with no other structural change is deliberately left unclassified).
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
                if (base.enclosingType.equals(head.enclosingType) && !base.name.equals(head.name)) {
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
        //    still exists in head with a shorter body calling the new method.
        for (MethodInfo head : new ArrayList<>(stillUnmatchedHead)) {
            for (MethodInfo callerHead : headMethods) {
                if (callerHead.name.equals(head.name)) continue;
                if (callsMethod(callerHead.normalizedBody, head.name) && baseHasInlineEquivalent(baseByKey, callerHead, head)) {
                    MethodInfo callerBase = firstUnmatched(
                            baseByKey.get(new MethodKey(callerHead.enclosingType, callerHead.name)), Set.of());
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
        results.addAll(detectMechanicalReplacements(baseRoot, headRoot));

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
        // + same name + same type is a move. A same-name field whose type also changed isn't
        // fuzzy-matched into either shape — same no-confidence-score policy as the rest of
        // this detector — so it's currently left unclassified rather than reported as some
        // kind of "change field type" (not in this detector's covered kind set).
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

        return results;
    }

    private static <T> List<T> excluding(List<T> declarations, Function<T, String> enclosingType, Set<String> excludedTypes) {
        return declarations.stream().filter(d -> !excludedTypes.contains(enclosingType.apply(d))).toList();
    }

    private List<DetectedTransformation> detectEnumConstantChanges(List<EnumConstantInfo> baseConstants,
                                                                    List<EnumConstantInfo> headConstants) {
        Set<String> baseNames = baseConstants.stream().map(EnumConstantInfo::description).collect(Collectors.toSet());
        Set<String> headNames = headConstants.stream().map(EnumConstantInfo::description).collect(Collectors.toSet());
        List<DetectedTransformation> results = new ArrayList<>();
        for (EnumConstantInfo base : baseConstants) {
            if (!headNames.contains(base.description())) {
                results.add(DetectedTransformation.withDiff(TransformationKind.REMOVE_ENUM_CONSTANT,
                        List.of(base.description()), List.of(base.file()), base.rawDeclaration(), ""));
            }
        }
        for (EnumConstantInfo head : headConstants) {
            if (!baseNames.contains(head.description())) {
                results.add(DetectedTransformation.withDiff(TransformationKind.ADD_ENUM_CONSTANT,
                        List.of(head.description()), List.of(head.file()), "", head.rawDeclaration()));
            }
        }
        return results;
    }

    private List<DetectedTransformation> detectAnnotationElementChanges(List<AnnotationElementInfo> baseElements,
                                                                         List<AnnotationElementInfo> headElements) {
        Map<String, AnnotationElementInfo> headByDescription = new LinkedHashMap<>();
        headElements.forEach(head -> headByDescription.put(head.description(), head));
        Set<String> baseDescriptions = baseElements.stream().map(AnnotationElementInfo::description).collect(Collectors.toSet());

        List<DetectedTransformation> results = new ArrayList<>();
        for (AnnotationElementInfo base : baseElements) {
            AnnotationElementInfo head = headByDescription.get(base.description());
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
            if (!baseDescriptions.contains(head.description())) {
                results.add(DetectedTransformation.withDiff(TransformationKind.ADD_ANNOTATION_ELEMENT,
                        List.of(head.description()), List.of(head.file()), "", head.rawDeclaration()));
            }
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
            baseParameterNamesByEnclosingType.computeIfAbsent(c.enclosingType, k -> new HashSet<>())
                    .addAll(c.parameterNames);
            representativeBaseByEnclosingType.putIfAbsent(c.enclosingType, c);
        }

        for (ConstructorInfo head : headConstructors) {
            Set<String> baseParameterNames = baseParameterNamesByEnclosingType.getOrDefault(head.enclosingType, Set.of());
            ConstructorInfo representativeBase = representativeBaseByEnclosingType.get(head.enclosingType);
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
            headByKey.computeIfAbsent(new FieldKey(f.enclosingType, f.name), k -> new ArrayList<>()).add(f);
        }

        // 1. Exact match: same enclosing type + same name + same declared type — this is
        // what makes it genuinely "the same field" rather than an unrelated field that
        // happens to reuse the name (a same-name field whose type also changed falls through
        // to steps 2/3/5 below like any other unmatched pair, same policy as methods: no
        // fuzzy match across two differing dimensions at once).
        for (FieldInfo base : baseFields) {
            List<FieldInfo> candidates = headByKey.get(new FieldKey(base.enclosingType, base.name));
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
                if (base.enclosingType.equals(head.enclosingType) && !base.name.equals(head.name)) {
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
        for (FieldInfo base : stillUnmatchedBase) {
            results.add(DetectedTransformation.withDiff(TransformationKind.REMOVE_FIELD,
                    List.of(base.description()), List.of(base.file), base.rawDeclaration, ""));
        }
        for (FieldInfo head : stillUnmatchedHead) {
            results.add(DetectedTransformation.withDiff(TransformationKind.ADD_FIELD,
                    List.of(head.description()), List.of(head.file), "", head.rawDeclaration));
        }

        return results;
    }

    private record FieldKey(String enclosingType, String name) {
    }

    private Map<MethodKey, List<MethodInfo>> indexByKey(List<MethodInfo> methods) {
        Map<MethodKey, List<MethodInfo>> index = new LinkedHashMap<>();
        for (MethodInfo m : methods) {
            index.computeIfAbsent(new MethodKey(m.enclosingType, m.name), k -> new ArrayList<>()).add(m);
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

    private record MethodKey(String enclosingType, String name) {
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

        // Read every common file's text exactly once, from disk, up front — the loop below
        // previously re-read (and, worse, re-scanned in full) every file once per candidate
        // identifier, which on a real repo with hundreds of identifiers and files meant
        // effectively re-reading the whole tree hundreds of times over.
        Map<String, String> baseTextByFile = new LinkedHashMap<>();
        Map<String, String> headTextByFile = new LinkedHashMap<>();
        for (String rel : commonRelativePaths) {
            baseTextByFile.put(rel, readFile(baseRoot.resolve(rel)));
            headTextByFile.put(rel, readFile(headRoot.resolve(rel)));
        }

        // Candidate identifiers: every simple name used in the base files.
        Set<String> candidateIdentifiers = new LinkedHashSet<>();
        for (String baseText : baseTextByFile.values()) {
            candidateIdentifiers.addAll(extractIdentifiers(baseText));
        }

        for (String oldIdentifier : candidateIdentifiers) {
            int occurrences = 0;
            String newIdentifier = null;
            boolean consistent = true;

            for (String rel : commonRelativePaths) {
                String baseText = baseTextByFile.get(rel);
                String headText = headTextByFile.get(rel);
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

    private boolean baseHasInlineEquivalent(Map<MethodKey, List<MethodInfo>> baseByKey, MethodInfo callerHead,
                                             MethodInfo extractedHead) {
        // The base version of the calling method (same enclosing type + name) must exist
        // and its body, once the extracted fragment's statements are considered, should
        // contain the same normalized statements the extracted method now holds.
        List<MethodInfo> candidates = baseByKey.get(new MethodKey(callerHead.enclosingType, callerHead.name));
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
     * Parses every file under {@code root} exactly once and extracts both
     * {@link MethodInfo}s and {@link RecordInfo}s from the same
     * {@link CompilationUnit} — methodInfos and recordInfos used to each
     * independently re-parse every file, doubling the tree's parse cost
     * (parsing dominates; extracting two different views from an
     * already-parsed AST is cheap).
     */
    private ParsedRoot parseRoot(Path root) {
        DeclarationCollector collected = new DeclarationCollector();
        for (Path file : javaFiles(root)) {
            String relativePath = root.relativize(file).toString();
            CompilationUnit cu;
            List<String> sourceLines;
            try {
                StaticJavaParser.setConfiguration(JavaParserConfigurations.currentJava());
                cu = StaticJavaParser.parse(file);
                sourceLines = Files.readAllLines(file);
            } catch (IOException | RuntimeException e) {
                continue;
            }
            for (TypeDeclaration<?> type : cu.getTypes()) {
                collectType(type, type.getNameAsString(), relativePath, sourceLines, collected);
            }
        }
        return collected.toParsedRoot();
    }

    /**
     * Collects one type's own members, then recurses into its member types (ticket #265):
     * nested and inner classes are analyzed like top-level ones, keyed by their qualified
     * name ({@code Outer.Inner}) so same-named nested types in different outer classes
     * never collide. A type's member signatures cover its own members only — a nested
     * type is a separate {@link ClassInfo}, not part of its outer class's identity.
     */
    private void collectType(TypeDeclaration<?> type, String qualifiedName, String relativePath, List<String> sourceLines,
                             DeclarationCollector collected) {
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
                    normalize(bodyOnlyText), relativePath));
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
                        annotationNames, rawDeclaration, relativePath));
                memberSignatures.add("field:" + variable.getNameAsString() + ":" + typeAsString);
            }
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
                    assignedFieldNames, rawDeclaration, relativePath));
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
            collected.classes.add(new ClassInfo(qualifiedName, relativePath, memberSignatures, headerText));
        }
        for (BodyDeclaration<?> member : type.getMembers()) {
            if (member instanceof TypeDeclaration<?> nested) {
                collectType(nested, qualifiedName + "." + nested.getNameAsString(), relativePath, sourceLines, collected);
            }
        }
    }

    /**
     * True if {@code body} contains an assignment of the form
     * {@code this.<name> = <name>} (whitespace-insensitive around the {@code =}) — the
     * canonical "constructor-injected field" shape. A text check rather than a full AST
     * walk of assignment statements, consistent with this detector's existing lightweight
     * checks for similar structural questions (see {@link #callsMethod}).
     */
    private boolean assignsParameterToSameNamedField(String body, String parameterName) {
        String normalizedBody = body.replaceAll("\\s+", "");
        return normalizedBody.contains("this." + parameterName + "=" + parameterName + ";");
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
    }

    /** An annotation-type element ({@code String mockMaker() default "";}); {@code defaultValue} is its source text. */
    private record AnnotationElementInfo(String enclosingType, String name, String type, Optional<String> defaultValue,
                                          String rawDeclaration, String file) {
        String description() {
            return enclosingType + "#" + name;
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
                                    Set<String> assignedFieldNames, String rawDeclaration, String file) {
    }

    /**
     * A non-record type's identity for rename/move matching: {@code memberSignatures}
     * (every method's name+param-types+return-type, every field's name+type, order-
     * independent via TreeSet) plays the same role {@code normalizedBody} plays for a
     * method — the "did the substance stay the same" signal that lets a rename/move be
     * told apart from an unrelated add+remove pair. {@code headerText} is the type's own
     * full source text (used for the class-level diff and formatting-only detection).
     */
    private record ClassInfo(String simpleName, String file, Set<String> memberSignatures, String headerText) {
    }

    private record FieldInfo(String enclosingType, String name, String type, Set<String> annotationNames,
                              String rawDeclaration, String file) {
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
