package com.athena.memory;

/**
 * Two co-changed file paths, order-independent: {@code (A, B)} and
 * {@code (B, A)} are the same pair, so the same commit-history pattern is
 * recognized and reported consistently regardless of the order files
 * happened to appear in a given commit (ticket #170).
 */
record UnorderedFilePair(String first, String second) {

    static UnorderedFilePair of(String a, String b) {
        return a.compareTo(b) <= 0 ? new UnorderedFilePair(a, b) : new UnorderedFilePair(b, a);
    }
}
