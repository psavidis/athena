package com.athena.web.reviewui;

import com.athena.reviewui.AnnotationBoard;
import com.athena.reviewui.AnnotationScope;
import com.athena.reviewui.Comment;
import com.athena.web.CurrentReviewer;
import com.athena.web.WebSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Comments on Semantic Canvas items — territories and the nodes inside them
 * (ticket #134): a pin badge on any commented item, a thread view (list,
 * author, timestamp), and post/edit/delete. {@code itemId} is the canvas's
 * own id scheme (see SemanticCanvasPage.tsx's target-id construction) —
 * this controller treats it as an opaque string, scoped via
 * {@link AnnotationScope#canvasItem(String)}.
 */
@RestController
public class CanvasCommentController {

    private final WebSession session;
    private final CurrentReviewer currentReviewer;

    public CanvasCommentController(WebSession session, CurrentReviewer currentReviewer) {
        this.session = session;
        this.currentReviewer = currentReviewer;
    }

    @GetMapping("/api/review/canvas-items/{itemId}/comments")
    public List<CanvasCommentResponse> comments(@PathVariable String itemId) {
        AnnotationBoard board = requireSelection().annotationBoard();
        return board.commentsAt(AnnotationScope.canvasItem(itemId)).stream().map(CanvasCommentResponse::of).toList();
    }

    /**
     * Every canvas item with at least one comment, mapped to its comment
     * count — the whole-PR view the topbar's total count and the "show only
     * commented" filter both need, without fetching each item individually.
     */
    @GetMapping("/api/review/canvas-items/comment-counts")
    public Map<String, Integer> commentCounts() {
        AnnotationBoard board = requireSelection().annotationBoard();
        return board.allComments().stream()
                .map(Comment::scope)
                .map(AnnotationScope::canvasItemId)
                .flatMap(Optional::stream)
                .collect(Collectors.groupingBy(id -> id, Collectors.summingInt(id -> 1)));
    }

    @PostMapping("/api/review/canvas-items/{itemId}/comments")
    public List<CanvasCommentResponse> addComment(@PathVariable String itemId, @RequestBody CanvasCommentRequest request) {
        AnnotationBoard board = requireSelection().annotationBoard();
        AnnotationScope scope = AnnotationScope.canvasItem(itemId);
        try {
            board.addComment(scope, currentReviewer.login(), request.text());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "text must not be blank");
        }
        return comments(itemId);
    }

    @PutMapping("/api/review/canvas-items/{itemId}/comments/{commentId}")
    public List<CanvasCommentResponse> editComment(@PathVariable String itemId, @PathVariable String commentId,
                                                    @RequestBody CanvasCommentRequest request) {
        AnnotationBoard board = requireSelection().annotationBoard();
        AnnotationScope scope = AnnotationScope.canvasItem(itemId);
        try {
            board.editComment(scope, commentId, request.text());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "text must not be blank");
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found");
        }
        return comments(itemId);
    }

    @DeleteMapping("/api/review/canvas-items/{itemId}/comments/{commentId}")
    public List<CanvasCommentResponse> deleteComment(@PathVariable String itemId, @PathVariable String commentId) {
        AnnotationBoard board = requireSelection().annotationBoard();
        AnnotationScope scope = AnnotationScope.canvasItem(itemId);
        try {
            board.deleteComment(scope, commentId);
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found");
        }
        return comments(itemId);
    }

    private WebSession.SelectedPullRequest requireSelection() {
        session.gitHubToken()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not connected to GitHub"));
        return session.selectedPullRequest()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No PR selected"));
    }
}
