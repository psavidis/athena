package com.athena.reviewui;

import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Dedicated unit test for {@link AnnotationBoard}'s edit/delete-by-id
 * behavior (ticket #134) — {@link CommentsAndPrivateNotesSteps} already
 * covers add/reject-blank end-to-end via Gherkin; this focuses on the new
 * per-comment identity (id, author, postedAt) and the in-place-replace
 * semantics {@link Comment}'s immutability requires for edit.
 */
class AnnotationBoardCommentEditingTest {

    private final AnnotationBoard board = new AnnotationBoard();
    private final AnnotationScope scope = AnnotationScope.canvasItem("territory:crowdness-live");

    @Test
    void addedCommentCarriesAuthorAndAGeneratedId() {
        board.addComment(scope, "alex", "Should this handle the null case?");

        Comment comment = board.commentsAt(scope).get(0);
        assertThat(comment.author()).isEqualTo("alex");
        assertThat(comment.text()).isEqualTo("Should this handle the null case?");
        assertThat(comment.id()).isNotBlank();
        assertThat(comment.postedAt()).isNotNull();
    }

    @Test
    void twoCommentsOnTheSameScopeGetDistinctIds() {
        board.addComment(scope, "alex", "First");
        board.addComment(scope, "alex", "Second");

        var ids = board.commentsAt(scope).stream().map(Comment::id).toList();
        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test
    void editingReplacesTheTextInPlaceWithoutAddingADuplicate() {
        board.addComment(scope, "alex", "Should this handle the null case?");
        String id = board.commentsAt(scope).get(0).id();

        board.editComment(scope, id, "Never mind, it's covered.");

        assertThat(board.commentsAt(scope)).hasSize(1);
        assertThat(board.commentsAt(scope).get(0).text()).isEqualTo("Never mind, it's covered.");
        assertThat(board.commentsAt(scope).get(0).id()).isEqualTo(id);
    }

    @Test
    void editingRejectsBlankText() {
        board.addComment(scope, "alex", "Should this handle the null case?");
        String id = board.commentsAt(scope).get(0).id();

        assertThatThrownBy(() -> board.editComment(scope, id, "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void editingAnUnknownCommentIdFails() {
        assertThatThrownBy(() -> board.editComment(scope, "does-not-exist", "text"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void deletingRemovesTheCommentById() {
        board.addComment(scope, "alex", "Only comment");
        String id = board.commentsAt(scope).get(0).id();

        board.deleteComment(scope, id);

        assertThat(board.commentsAt(scope)).isEmpty();
    }

    @Test
    void deletingOneCommentLeavesTheOthersOnTheSameScopeIntact() {
        board.addComment(scope, "alex", "Keep me");
        board.addComment(scope, "alex", "Delete me");
        String idToDelete = board.commentsAt(scope).get(1).id();

        board.deleteComment(scope, idToDelete);

        assertThat(board.commentsAt(scope)).extracting(Comment::text).containsExactly("Keep me");
    }

    @Test
    void deletingAnUnknownCommentIdFails() {
        assertThatThrownBy(() -> board.deleteComment(scope, "does-not-exist"))
                .isInstanceOf(NoSuchElementException.class);
    }
}
