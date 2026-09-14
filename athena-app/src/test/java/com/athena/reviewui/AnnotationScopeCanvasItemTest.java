package com.athena.reviewui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Dedicated unit test for {@link AnnotationScope#canvasItem(String)} (ticket
 * #134) — the new scope kind a Semantic Canvas comment attaches to.
 */
class AnnotationScopeCanvasItemTest {

    @Test
    void exposesTheCanvasItemId() {
        AnnotationScope scope = AnnotationScope.canvasItem("territory:crowdness-live");

        assertThat(scope.canvasItemId()).contains("territory:crowdness-live");
    }

    @Test
    void twoScopesWithTheSameCanvasItemIdAreEqual() {
        assertThat(AnnotationScope.canvasItem("territory:crowdness-live"))
                .isEqualTo(AnnotationScope.canvasItem("territory:crowdness-live"));
    }

    @Test
    void twoScopesWithDifferentCanvasItemIdsAreNotEqual() {
        assertThat(AnnotationScope.canvasItem("territory:crowdness-live"))
                .isNotEqualTo(AnnotationScope.canvasItem("territory:crowdness-ingestion"));
    }

    @Test
    void rejectsABlankCanvasItemId() {
        assertThatThrownBy(() -> AnnotationScope.canvasItem("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nonCanvasItemScopesHaveNoCanvasItemId() {
        assertThat(AnnotationScope.review().canvasItemId()).isEmpty();
    }
}
