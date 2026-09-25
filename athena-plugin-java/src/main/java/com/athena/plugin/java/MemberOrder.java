package com.athena.plugin.java;

import com.athena.semantic.DetectedTransformation;
import com.athena.semantic.TransformationKind;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.AnnotationMemberDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Members of a type moved to other positions in it, each unchanged (ticket #387). Only members
 * whose declaration is identical in both revisions (after the printer normalizes formatting) take
 * part: those outside the longest common subsequence of their two orders are the moved ones.
 */
final class MemberOrder {

    private MemberOrder() {
    }

    /** The reorders between {@code base} and {@code head} of the same file, one per type whose members moved. */
    static List<DetectedTransformation> reorders(String file, CompilationUnit base, CompilationUnit head) {
        Map<String, TypeDeclaration<?>> headTypes = typesByName(head);
        List<DetectedTransformation> reorders = new ArrayList<>();
        for (Map.Entry<String, TypeDeclaration<?>> baseType : typesByName(base).entrySet()) {
            TypeDeclaration<?> headType = headTypes.get(baseType.getKey());
            if (headType == null) {
                continue;
            }
            List<Member> baseMembers = members(baseType.getValue());
            List<Member> headMembers = members(headType);
            moveBetween(baseMembers, headMembers).ifPresent(move -> reorders.add(DetectedTransformation.withDiff(
                    TransformationKind.REORDER_MEMBERS, List.of(baseType.getKey(), move), List.of(file, file),
                    String.join("\n", displayNames(baseMembers)), String.join("\n", displayNames(headMembers)))));
        }
        return reorders;
    }

    /** "mul moved before add", "add moved after mul" or "N members moved"; empty when the identical members kept their order. */
    private static Optional<String> moveBetween(List<Member> base, List<Member> head) {
        Map<String, String> baseContent = contentByKey(base);
        Map<String, String> headContent = contentByKey(head);
        if (baseContent == null || headContent == null) {
            return Optional.empty();
        }
        List<Member> baseOrder = base.stream().filter(m -> m.content().equals(headContent.get(m.key()))).toList();
        List<Member> headOrder = head.stream().filter(m -> m.content().equals(baseContent.get(m.key()))).toList();
        List<String> baseKeys = baseOrder.stream().map(Member::key).toList();
        List<String> headKeys = headOrder.stream().map(Member::key).toList();
        if (baseKeys.equals(headKeys)) {
            return Optional.empty();
        }
        Set<String> kept = new HashSet<>(longestCommonSubsequence(baseKeys, headKeys));
        List<Integer> moved = new ArrayList<>();
        for (int i = 0; i < headOrder.size(); i++) {
            if (!kept.contains(headOrder.get(i).key())) {
                moved.add(i);
            }
        }
        if (moved.size() != 1) {
            return Optional.of(moved.size() + " members moved");
        }
        int index = moved.get(0);
        String name = headOrder.get(index).displayName();
        return Optional.of(index + 1 < headOrder.size()
                ? name + " moved before " + headOrder.get(index + 1).displayName()
                : name + " moved after " + headOrder.get(index - 1).displayName());
    }

    private static List<String> longestCommonSubsequence(List<String> a, List<String> b) {
        int[][] lengths = new int[a.size() + 1][b.size() + 1];
        for (int i = a.size() - 1; i >= 0; i--) {
            for (int j = b.size() - 1; j >= 0; j--) {
                lengths[i][j] = a.get(i).equals(b.get(j))
                        ? lengths[i + 1][j + 1] + 1
                        : Math.max(lengths[i + 1][j], lengths[i][j + 1]);
            }
        }
        List<String> common = new ArrayList<>();
        for (int i = 0, j = 0; i < a.size() && j < b.size(); ) {
            if (a.get(i).equals(b.get(j))) {
                common.add(a.get(i));
                i++;
                j++;
            } else if (lengths[i + 1][j] >= lengths[i][j + 1]) {
                i++;
            } else {
                j++;
            }
        }
        return common;
    }

    /** Content by key, or null when two members share a key (overloads with equal types), which can't be matched safely. */
    private static Map<String, String> contentByKey(List<Member> members) {
        Map<String, String> byKey = new LinkedHashMap<>();
        for (Member member : members) {
            if (byKey.put(member.key(), member.content()) != null) {
                return null;
            }
        }
        return byKey;
    }

    private static Map<String, TypeDeclaration<?>> typesByName(CompilationUnit unit) {
        Map<String, TypeDeclaration<?>> types = new LinkedHashMap<>();
        for (TypeDeclaration<?> type : unit.findAll(TypeDeclaration.class)) {
            types.putIfAbsent(qualifiedName(type), type);
        }
        return types;
    }

    /** "Outer.Inner" for a nested type. */
    private static String qualifiedName(TypeDeclaration<?> type) {
        List<String> names = new ArrayList<>(List.of(type.getNameAsString()));
        Optional<Node> parent = type.getParentNode();
        while (parent.isPresent()) {
            if (parent.get() instanceof TypeDeclaration<?> outer) {
                names.add(0, outer.getNameAsString());
            }
            parent = parent.get().getParentNode();
        }
        return String.join(".", names);
    }

    private static List<Member> members(TypeDeclaration<?> type) {
        List<Member> members = new ArrayList<>();
        for (BodyDeclaration<?> member : type.getMembers()) {
            memberOf(member).ifPresent(members::add);
        }
        return members;
    }

    private static Optional<Member> memberOf(BodyDeclaration<?> declaration) {
        String content = declaration.toString();
        if (declaration instanceof CallableDeclaration<?> callable) {
            String types = callable.getParameters().stream().map(p -> p.getType().asString()).collect(Collectors.joining(","));
            return Optional.of(new Member(callable.getNameAsString() + "(" + types + ")", callable.getNameAsString(), content));
        }
        if (declaration instanceof FieldDeclaration field) {
            String names = field.getVariables().stream().map(VariableDeclarator::getNameAsString).collect(Collectors.joining(", "));
            return Optional.of(new Member("field " + names, names, content));
        }
        if (declaration instanceof TypeDeclaration<?> nested) {
            return Optional.of(new Member("type " + nested.getNameAsString(), nested.getNameAsString(), content));
        }
        if (declaration instanceof AnnotationMemberDeclaration element) {
            return Optional.of(new Member(element.getNameAsString() + "()", element.getNameAsString(), content));
        }
        return Optional.empty();
    }

    private static List<String> displayNames(List<Member> members) {
        return members.stream().map(Member::displayName).toList();
    }

    /** A member: its matching key (name plus parameter types), the name a reviewer reads, and its normalized declaration. */
    private record Member(String key, String displayName, String content) {
    }
}
