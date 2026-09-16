package com.athena.reviewrecorder;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Dedicated unit test for {@link Moment} (tickets #205, #206). */
class MomentTest {

    private static final Instant NOW = Instant.parse("2026-09-17T10:00:00Z");

    @Test
    void createsAPendingMomentWithItsKindReferenceAndTimestamp() {
        Moment moment = Moment.tagged(MomentKind.QUESTION, "entity:OrderService", NOW);

        assertThat(moment.id()).isNotBlank();
        assertThat(moment.kind()).isEqualTo(MomentKind.QUESTION);
        assertThat(moment.reference()).isEqualTo("entity:OrderService");
        assertThat(moment.taggedAt()).isEqualTo(NOW);
        assertThat(moment.status()).isEqualTo(MomentStatus.PENDING);
    }

    @Test
    void aNullKindIsRejected() {
        assertThatThrownBy(() -> Moment.tagged(null, "entity:OrderService", NOW))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void aNullTimestampIsRejected() {
        assertThatThrownBy(() -> Moment.tagged(MomentKind.QUESTION, "entity:OrderService", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void aMomentWithNoActiveReferenceIsAllowed() {
        Moment moment = Moment.tagged(MomentKind.INSIGHT, null, NOW);

        assertThat(moment.reference()).isNull();
    }

    @Test
    void confirmingAPendingMomentMakesItConfirmed() {
        Moment moment = Moment.tagged(MomentKind.QUESTION, "entity:OrderService", NOW);

        Moment confirmed = moment.confirmed();

        assertThat(confirmed.status()).isEqualTo(MomentStatus.CONFIRMED);
        assertThat(confirmed.id()).isEqualTo(moment.id());
        assertThat(confirmed.kind()).isEqualTo(MomentKind.QUESTION);
    }

    @Test
    void confirmingANonPendingMomentIsRejected() {
        Moment confirmed = Moment.tagged(MomentKind.QUESTION, "entity:OrderService", NOW).confirmed();

        assertThatThrownBy(confirmed::confirmed).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectingAPendingMomentMakesItRejected() {
        Moment moment = Moment.tagged(MomentKind.QUESTION, "entity:OrderService", NOW);

        Moment rejected = moment.rejected();

        assertThat(rejected.status()).isEqualTo(MomentStatus.REJECTED);
    }

    @Test
    void rejectingANonPendingMomentIsRejected() {
        Moment rejected = Moment.tagged(MomentKind.QUESTION, "entity:OrderService", NOW).rejected();

        assertThatThrownBy(rejected::rejected).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void editingAPendingMomentChangesItsKindAndKeepsItPending() {
        Moment moment = Moment.tagged(MomentKind.QUESTION, "entity:OrderService", NOW);

        Moment edited = moment.withKind(MomentKind.CONCERN);

        assertThat(edited.kind()).isEqualTo(MomentKind.CONCERN);
        assertThat(edited.status()).isEqualTo(MomentStatus.PENDING);
        assertThat(edited.id()).isEqualTo(moment.id());
    }

    @Test
    void editingANonPendingMomentIsRejected() {
        Moment confirmed = Moment.tagged(MomentKind.QUESTION, "entity:OrderService", NOW).confirmed();

        assertThatThrownBy(() -> confirmed.withKind(MomentKind.CONCERN)).isInstanceOf(IllegalStateException.class);
    }
}
