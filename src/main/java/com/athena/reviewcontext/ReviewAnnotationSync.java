package com.athena.reviewcontext;

import com.athena.github.CommentSyncer;
import com.athena.reviewui.AnnotationBoard;
import com.athena.reviewui.AnnotationScope;
import com.athena.reviewui.Comment;

/**
 * Syncs stored comments to a real GitHub Pull Request. Built only on
 * {@link AnnotationBoard#allComments()}, which structurally cannot return a
 * {@link com.athena.reviewui.PrivateNote} — so a private note can never be
 * included in a GitHub sync operation, even by accident, regardless of what
 * else is attached to the same scope (epic #6 §22).
 */
public final class ReviewAnnotationSync {

    private final CommentSyncer syncer;

    public ReviewAnnotationSync(CommentSyncer syncer) {
        this.syncer = syncer;
    }

    public void syncComments(AnnotationBoard board, String repositoryFullName, int pullRequestNumber) {
        for (Comment comment : board.allComments()) {
            AnnotationScope scope = comment.scope();
            scope.lineLocation().ifPresentOrElse(
                    location -> syncer.syncLineComment(repositoryFullName, pullRequestNumber,
                            comment.text(), location.filePath(), location.line()),
                    () -> syncer.syncGeneralComment(repositoryFullName, pullRequestNumber, comment.text()));
        }
    }
}
