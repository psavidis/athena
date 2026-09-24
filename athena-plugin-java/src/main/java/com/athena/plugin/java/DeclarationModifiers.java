package com.athena.plugin.java;

import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.nodeTypes.NodeWithModifiers;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * A declaration's declared visibility and modifier keywords (ticket #313), compared between two
 * revisions so a class made {@code final} or a method made less visible is reported. Only what
 * is written counts: an interface method's implicit {@code public} is package-private here,
 * the same on both sides, so it never shows as a change. Order is irrelevant.
 *
 * <p>Immutable; compared with {@link #describeChangeTo}.
 */
final class DeclarationModifiers {

    static final DeclarationModifiers NONE = new DeclarationModifiers("package-private", Set.of());

    private static final Set<Modifier.Keyword> TRACKED_KEYWORDS = Set.of(Modifier.Keyword.FINAL,
            Modifier.Keyword.STATIC, Modifier.Keyword.ABSTRACT, Modifier.Keyword.SYNCHRONIZED,
            Modifier.Keyword.VOLATILE, Modifier.Keyword.TRANSIENT);

    private final String visibility;
    private final Set<String> keywords;

    private DeclarationModifiers(String visibility, Set<String> keywords) {
        this.visibility = visibility;
        this.keywords = Set.copyOf(keywords);
    }

    static DeclarationModifiers of(NodeWithModifiers<?> declaration) {
        String visibility = "package-private";
        Set<String> keywords = new TreeSet<>();
        for (Modifier modifier : declaration.getModifiers()) {
            Modifier.Keyword keyword = modifier.getKeyword();
            switch (keyword) {
                case PUBLIC, PROTECTED, PRIVATE -> visibility = keyword.asString();
                default -> {
                    if (TRACKED_KEYWORDS.contains(keyword)) {
                        keywords.add(keyword.asString());
                    }
                }
            }
        }
        return new DeclarationModifiers(visibility, keywords);
    }

    /**
     * What changed from this (base) to {@code head}: a visibility change first ({@code
     * "protected -> package-private"}), then keywords added and removed, in alphabetical order
     * ({@code "+final"}, {@code "-static"}). Empty when nothing changed.
     */
    Optional<String> describeChangeTo(DeclarationModifiers head) {
        List<String> items = new ArrayList<>();
        if (!visibility.equals(head.visibility)) {
            items.add(visibility + " -> " + head.visibility);
        }
        new TreeSet<>(head.keywords).stream().filter(k -> !keywords.contains(k)).forEach(k -> items.add("+" + k));
        new TreeSet<>(keywords).stream().filter(k -> !head.keywords.contains(k)).forEach(k -> items.add("-" + k));
        return items.isEmpty() ? Optional.empty() : Optional.of(String.join(", ", items));
    }
}
