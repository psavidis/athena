package com.athena.web.reviewui;

import com.athena.reviewui.AnnotationBoard;
import com.athena.reviewui.AnnotationScope;
import com.athena.reviewui.ChangeDetailView;
import com.athena.reviewui.Comment;
import com.athena.reviewui.PrivateNote;
import com.athena.semantic.Change;
import com.athena.web.ChangeKey;
import com.athena.web.CurrentReviewer;
import com.athena.web.Diff;
import com.athena.web.WebSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * A Change's detail view, and attaching comments/private notes to it or to a
 * line/symbol/the review overall (ticket #75). Reuses {@link ChangeDetailView},
 * {@link AnnotationBoard}, {@link AnnotationScope}, {@link Comment},
 * {@link PrivateNote} unmodified. Each add-annotation call returns the
 * scope's current annotations, so the frontend doesn't need a separate
 * read-back call after posting one.
 */
@RestController
public class ChangeDetailController {

    private final WebSession session;
    private final CurrentReviewer currentReviewer;

    public ChangeDetailController(WebSession session, CurrentReviewer currentReviewer) {
        this.session = session;
        this.currentReviewer = currentReviewer;
    }

    @GetMapping("/api/review/changes/{changeKey}")
    public ChangeDetailResponse changeDetail(@PathVariable String changeKey) {
        Diff diff = requireSelection();
        Change change = requireChange(changeKey, diff.changes());

        ChangeDetailView view = ChangeDetailView.of(change);
        return new ChangeDetailResponse(changeKey, view.category(), view.kind(), view.description(), view.symbols(),
                view.files(), view.textualDiff());
    }

    @PostMapping("/api/review/comments")
    public AnnotationsResponse addComment(@RequestBody AddAnnotationRequest request) {
        Diff diff = requireSelection();
        AnnotationScope scope = request.scope().toScope(diff.changes());
        addAnnotation(diff, scope, request.text(), true);
        return annotationsAt(diff, scope);
    }

    @PostMapping("/api/review/private-notes")
    public AnnotationsResponse addPrivateNote(@RequestBody AddAnnotationRequest request) {
        Diff diff = requireSelection();
        AnnotationScope scope = request.scope().toScope(diff.changes());
        addAnnotation(diff, scope, request.text(), false);
        return annotationsAt(diff, scope);
    }

    private void addAnnotation(Diff diff, AnnotationScope scope, String text, boolean isComment) {
        try {
            if (isComment) {
                diff.annotationBoard().addComment(scope, currentReviewer.login(), text);
            } else {
                diff.annotationBoard().addPrivateNote(scope, text);
            }
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "text must not be blank");
        }
    }

    private AnnotationsResponse annotationsAt(Diff diff, AnnotationScope scope) {
        AnnotationBoard board = diff.annotationBoard();
        List<String> comments = board.commentsAt(scope).stream().map(Comment::text).toList();
        List<String> privateNotes = board.privateNotesAt(scope).stream().map(PrivateNote::text).toList();
        return new AnnotationsResponse(comments, privateNotes);
    }

    /**
     * A standalone Diff needs no GitHub connection at all (ticket #111/#153), so this only
     * ever reports the GitHub-specific 401 when there is truly nothing usable selected AND
     * no token — the same "not connected to GitHub" reviewers saw before this ticket for the
     * PR-only flow. A session with a token but nothing selected still reports 409, unchanged.
     */
    private Diff requireSelection() {
        return session.currentDiff().orElseThrow(() -> {
            if (session.gitHubToken().isEmpty()) {
                return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not connected to GitHub");
            }
            return new ResponseStatusException(HttpStatus.CONFLICT, "No PR or Diff selected");
        });
    }

    private Change requireChange(String changeKey, List<Change> changes) {
        return ChangeKey.resolve(changeKey, changes)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Change not found"));
    }
}
