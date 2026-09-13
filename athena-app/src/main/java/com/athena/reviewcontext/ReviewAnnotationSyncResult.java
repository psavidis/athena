package com.athena.reviewcontext;

import com.athena.reviewui.Comment;

import java.util.List;
import java.util.Map;

/**
 * The outcome of one {@link ReviewAnnotationSync#syncComments} call: which
 * comments synced successfully and which failed (with why), so a caller can
 * tell a partially-synced review apart from a fully-synced one instead of
 * either losing that information or having one failure silently abort every
 * comment after it.
 */
public final class ReviewAnnotationSyncResult {

    private final List<Comment> succeeded;
    private final Map<Comment, Exception> failed;

    ReviewAnnotationSyncResult(List<Comment> succeeded, Map<Comment, Exception> failed) {
        this.succeeded = List.copyOf(succeeded);
        this.failed = Map.copyOf(failed);
    }

    public List<Comment> succeeded() {
        return succeeded;
    }

    public Map<Comment, Exception> failed() {
        return failed;
    }

    public boolean isFullySuccessful() {
        return failed.isEmpty();
    }
}
