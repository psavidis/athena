package com.athena.livesession;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Dedicated unit test for {@link LiveReviewSessionRegistry} (ticket #158). */
class LiveReviewSessionRegistryTest {

    private final LiveReviewSessionRegistry registry = new LiveReviewSessionRegistry();

    @Test
    void aCreatedSessionCanBeFoundById() {
        LiveReviewSession session = registry.create("acme/widgets", 42, "Petros");

        assertThat(registry.find(session.id())).contains(session);
    }

    @Test
    void anUnknownIdIsNotFound() {
        assertThat(registry.find("does-not-exist")).isEmpty();
    }

    @Test
    void aRemovedSessionCanNoLongerBeFound() {
        LiveReviewSession session = registry.create("acme/widgets", 42, "Petros");

        registry.remove(session.id());

        assertThat(registry.find(session.id())).isEmpty();
    }

    @Test
    void twoSessionsGetDistinctIds() {
        LiveReviewSession first = registry.create("acme/widgets", 42, "Petros");
        LiveReviewSession second = registry.create("acme/widgets", 43, "Maria");

        assertThat(first.id()).isNotEqualTo(second.id());
    }
}
