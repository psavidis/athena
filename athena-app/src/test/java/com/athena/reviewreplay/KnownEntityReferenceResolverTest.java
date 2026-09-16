package com.athena.reviewreplay;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Dedicated unit test for {@link KnownEntityReferenceResolver} (ticket #210). */
class KnownEntityReferenceResolverTest {

    @Test
    void resolvesAnEntityReferenceStillAmongTheCurrentEntityNames() {
        KnownEntityReferenceResolver resolver = new KnownEntityReferenceResolver(() -> Set.of("OrderService"));

        assertThat(resolver.resolve("entity:OrderService")).contains("OrderService");
    }

    @Test
    void doesNotResolveAnEntityReferenceNoLongerAmongTheCurrentEntityNames() {
        KnownEntityReferenceResolver resolver = new KnownEntityReferenceResolver(Set::of);

        assertThat(resolver.resolve("entity:OrderService")).isEmpty();
    }

    @Test
    void doesNotResolveANonEntityReference() {
        KnownEntityReferenceResolver resolver = new KnownEntityReferenceResolver(() -> Set.of("OrderService"));

        Optional<String> resolved = resolver.resolve("component:OrderService");

        assertThat(resolved).isEmpty();
    }
}
