package com.athena.knowledge.spi;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What the current review needs relevant knowledge for (ticket #118): the
 * repository being reviewed, the files it changes, and any additional
 * terms (e.g. technologies/frameworks) worth matching against. Used by a
 * {@link KnowledgeProvider} to answer "what's relevant?" rather than
 * returning its entire knowledge base — advanced retrieval (embeddings,
 * knowledge graphs) is explicitly out of scope for this ticket, so
 * {@link #isRelevantTo(KnowledgeItem)} is a simple, explainable keyword
 * match: an item is relevant when its title or content mentions a
 * keyword derived from this query's changed files or terms.
 */
public final class KnowledgeQuery {

    // Separators stripped from both keywords and haystack text before matching, so
    // "payment-service" (prose), "paymentservice" (a path segment), and "Payment Service"
    // (a user-typed term) are all recognized as the same keyword.
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");

    private final String repositoryContext;
    private final List<String> changedFiles;
    private final List<String> terms;
    private final Set<String> keywords;

    private KnowledgeQuery(String repositoryContext, List<String> changedFiles, List<String> terms) {
        this.repositoryContext = repositoryContext;
        this.changedFiles = List.copyOf(changedFiles);
        this.terms = List.copyOf(terms);
        this.keywords = deriveKeywords(this.changedFiles, this.terms);
    }

    public static KnowledgeQuery of(String repositoryContext, List<String> changedFiles, List<String> terms) {
        Objects.requireNonNull(repositoryContext, "repositoryContext");
        Objects.requireNonNull(changedFiles, "changedFiles");
        Objects.requireNonNull(terms, "terms");
        return new KnowledgeQuery(repositoryContext, changedFiles, terms);
    }

    public String repositoryContext() {
        return repositoryContext;
    }

    public List<String> changedFiles() {
        return changedFiles;
    }

    public List<String> terms() {
        return terms;
    }

    /**
     * Whether {@code item} shares any keyword with this query's changed
     * files/terms — case-insensitive substring match against the item's
     * title and content. An item is never relevant to a query with no
     * derivable keywords (an empty query never matches everything).
     */
    public boolean isRelevantTo(KnowledgeItem item) {
        Objects.requireNonNull(item, "item");
        if (keywords.isEmpty()) {
            return false;
        }
        String haystack = normalize(item.title() + " " + item.content());
        return keywords.stream().anyMatch(haystack::contains);
    }

    private static Set<String> deriveKeywords(List<String> changedFiles, List<String> terms) {
        Set<String> keywords = new LinkedHashSet<>();
        for (String term : terms) {
            addKeyword(keywords, term);
        }
        for (String file : changedFiles) {
            // The file extension (java, ts, md, ...) is deliberately excluded: it's shared by
            // huge numbers of unrelated files and would otherwise make almost every note "relevant".
            for (String segment : stripExtension(file).split("[/\\\\._-]+")) {
                addKeyword(keywords, segment);
            }
        }
        return Set.copyOf(keywords);
    }

    private static String stripExtension(String filePath) {
        int lastSlash = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        int lastDot = filePath.lastIndexOf('.');
        return lastDot > lastSlash ? filePath.substring(0, lastDot) : filePath;
    }

    private static void addKeyword(Set<String> keywords, String candidate) {
        String normalized = normalize(candidate);
        // Short keywords (path separators, single-letter extensions) are too generic to be a
        // useful relevance signal and would otherwise match almost any note.
        if (normalized.length() > 2) {
            keywords.add(normalized);
        }
    }

    private static String normalize(String text) {
        return NON_ALPHANUMERIC.matcher(text.toLowerCase(Locale.ROOT)).replaceAll("");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof KnowledgeQuery other)) return false;
        return repositoryContext.equals(other.repositoryContext)
                && changedFiles.equals(other.changedFiles)
                && terms.equals(other.terms);
    }

    @Override
    public int hashCode() {
        return Objects.hash(repositoryContext, changedFiles, terms);
    }
}
