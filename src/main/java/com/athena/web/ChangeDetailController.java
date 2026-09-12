package com.athena.web;

import com.athena.reviewui.AnnotationBoard;
import com.athena.reviewui.AnnotationScope;
import com.athena.reviewui.ChangeDetailView;
import com.athena.reviewui.Comment;
import com.athena.reviewui.PrivateNote;
import com.athena.semantic.Change;
import com.athena.semantic.ChangeGrouper;
import com.athena.semantic.DetectedTransformation;
import com.athena.semantic.TransformationDetector;
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

    public ChangeDetailController(WebSession session) {
        this.session = session;
    }

    @GetMapping("/api/review/changes/{changeKey}")
    public ChangeDetailResponse changeDetail(@PathVariable String changeKey) {
        WebSession.SelectedPullRequest selection = requireSelection();
        Change change = requireChange(changeKey, detectChanges(selection));

        ChangeDetailView view = ChangeDetailView.of(change);
        return new ChangeDetailResponse(changeKey, view.category(), view.description(), view.symbols(), view.files(),
                view.textualDiff());
    }

    @PostMapping("/api/review/comments")
    public AnnotationsResponse addComment(@RequestBody AddAnnotationRequest request) {
        WebSession.SelectedPullRequest selection = requireSelection();
        AnnotationScope scope = request.scope().toScope(detectChanges(selection));
        addAnnotation(selection, scope, request.text(), true);
        return annotationsAt(selection, scope);
    }

    @PostMapping("/api/review/private-notes")
    public AnnotationsResponse addPrivateNote(@RequestBody AddAnnotationRequest request) {
        WebSession.SelectedPullRequest selection = requireSelection();
        AnnotationScope scope = request.scope().toScope(detectChanges(selection));
        addAnnotation(selection, scope, request.text(), false);
        return annotationsAt(selection, scope);
    }

    private void addAnnotation(WebSession.SelectedPullRequest selection, AnnotationScope scope, String text,
                                boolean isComment) {
        try {
            if (isComment) {
                selection.annotationBoard().addComment(scope, text);
            } else {
                selection.annotationBoard().addPrivateNote(scope, text);
            }
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "text must not be blank");
        }
    }

    private AnnotationsResponse annotationsAt(WebSession.SelectedPullRequest selection, AnnotationScope scope) {
        AnnotationBoard board = selection.annotationBoard();
        List<String> comments = board.commentsAt(scope).stream().map(Comment::text).toList();
        List<String> privateNotes = board.privateNotesAt(scope).stream().map(PrivateNote::text).toList();
        return new AnnotationsResponse(comments, privateNotes);
    }

    private WebSession.SelectedPullRequest requireSelection() {
        session.gitHubToken()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not connected to GitHub"));
        return session.selectedPullRequest()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No PR selected"));
    }

    private List<Change> detectChanges(WebSession.SelectedPullRequest selection) {
        List<DetectedTransformation> transformations =
                new TransformationDetector().detect(selection.baseRoot(), selection.headRoot());
        return new ChangeGrouper().group(transformations);
    }

    private Change requireChange(String changeKey, List<Change> changes) {
        return ChangeKey.resolve(changeKey, changes)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Change not found"));
    }
}
