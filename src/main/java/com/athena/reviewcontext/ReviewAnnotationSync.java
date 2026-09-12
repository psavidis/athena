package com.athena.reviewcontext;

import com.athena.github.CommentSyncer;
import com.athena.reviewui.AnnotationBoard;
import com.athena.reviewui.AnnotationScope;
import com.athena.reviewui.Comment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * Attempts every comment independently — one comment's sync failure (e.g. a
     * transient network error, or the PR having been closed mid-sync) does not
     * prevent the remaining comments from being attempted, and the result
     * reports exactly which comments succeeded and which failed with why.
     */
    public ReviewAnnotationSyncResult syncComments(AnnotationBoard board, String repositoryFullName, int pullRequestNumber) {
        List<Comment> succeeded = new ArrayList<>();
        Map<Comment, Exception> failed = new LinkedHashMap<>();
        for (Comment comment : board.allComments()) {
            try {
                syncOne(comment, repositoryFullName, pullRequestNumber);
                succeeded.add(comment);
            } catch (Exception e) {
                failed.put(comment, e);
            }
        }
        return new ReviewAnnotationSyncResult(succeeded, failed);
    }

    private void syncOne(Comment comment, String repositoryFullName, int pullRequestNumber) {
        AnnotationScope scope = comment.scope();
        scope.lineLocation().ifPresentOrElse(
                location -> syncer.syncLineComment(repositoryFullName, pullRequestNumber,
                        comment.text(), location.filePath(), location.line()),
                () -> syncer.syncGeneralComment(repositoryFullName, pullRequestNumber, comment.text()));
    }
}
