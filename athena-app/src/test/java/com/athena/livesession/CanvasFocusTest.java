package com.athena.livesession;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Dedicated unit test for {@link CanvasFocus} (ticket #158). */
class CanvasFocusTest {

    @Test
    void ofRequiresANonBlankZoomLevel() {
        assertThatThrownBy(() -> CanvasFocus.of("", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CanvasFocus.of(null, null, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void blankOptionalFieldsAreTreatedAsAbsent() {
        CanvasFocus focus = CanvasFocus.of("ARCHITECTURE", "  ", "", null);

        assertThat(focus.selectedEntityId()).isEmpty();
        assertThat(focus.selectedChangeKey()).isEmpty();
        assertThat(focus.navigationContext()).isEmpty();
    }

    @Test
    void carriesEveryFieldThatWasProvided() {
        CanvasFocus focus = CanvasFocus.of("ARCHITECTURE", "component:PaymentValidator", "abc123", "PaymentValidator");

        assertThat(focus.zoomLevel()).isEqualTo("ARCHITECTURE");
        assertThat(focus.selectedEntityId()).contains("component:PaymentValidator");
        assertThat(focus.selectedChangeKey()).contains("abc123");
        assertThat(focus.navigationContext()).contains("PaymentValidator");
    }

    @Test
    void initialFocusHasNoSelection() {
        CanvasFocus focus = CanvasFocus.initial();

        assertThat(focus.zoomLevel()).isEqualTo("OVERVIEW");
        assertThat(focus.selectedEntityId()).isEmpty();
    }

    @Test
    void equalFocusesAreEqual() {
        CanvasFocus a = CanvasFocus.of("ARCHITECTURE", "component:X", null, null);
        CanvasFocus b = CanvasFocus.of("ARCHITECTURE", "component:X", null, null);

        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
    }
}
