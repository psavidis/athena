package com.athena.reviewcontext;

import com.athena.knowledge.spi.KnowledgeItem;
import com.athena.memory.MemoryEntry;
import com.athena.reviewui.AnnotationBoard;
import com.athena.reviewui.Comment;
import com.athena.reviewui.PrivateNote;
import com.athena.semantic.Change;
import com.athena.semantic.ReviewCoverageReport;
import com.athena.semantic.ReviewState;
import com.athena.semantic.ReviewStateStore;

import java.util.List;

/**
 * The structured artifact assembled from a human review (epic #6 §33):
 * the PR's intent, its conceptual Changes broken down by reviewed/skipped/
 * mechanical, comments, coverage, and — only when the reviewer explicitly
 * opts in — private notes. This is the object epic #7 (AI Integration)
 * will later consume as input, so its field list stays close to §33
 * rather than inventing new shapes.
 *
 * <p>Concerns, assumptions, and unresolved questions (also listed in §33)
 * are represented via existing data for MVP rather than new dedicated
 * annotation types: a "concern" is a Change left in {@link ReviewState#CONCERN},
 * and assumptions/unresolved questions are free-text the reviewer attaches
 * as ordinary comments — no separate storage/UI ticket for those, per this
 * ticket's own "no UI polish beyond a functional summary view" scope.
 */
public final class ReviewContext {

    private final String prTitle;
    private final List<Change> reviewedChanges;
    private final List<Change> skippedChanges;
    private final List<Change> mechanicalChanges;
    private final List<Change> concernChanges;
    private final List<Comment> comments;
    private final List<PrivateNote> privateNotes;
    private final String coverageSummary;
    private final List<KnowledgeItem> knowledgeItems;
    private final List<MemoryEntry> memoryEntries;

    private ReviewContext(String prTitle, List<Change> reviewedChanges, List<Change> skippedChanges,
                           List<Change> mechanicalChanges, List<Change> concernChanges, List<Comment> comments,
                           List<PrivateNote> privateNotes, String coverageSummary, List<KnowledgeItem> knowledgeItems,
                           List<MemoryEntry> memoryEntries) {
        this.prTitle = prTitle;
        this.reviewedChanges = List.copyOf(reviewedChanges);
        this.skippedChanges = List.copyOf(skippedChanges);
        this.mechanicalChanges = List.copyOf(mechanicalChanges);
        this.concernChanges = List.copyOf(concernChanges);
        this.comments = List.copyOf(comments);
        this.privateNotes = List.copyOf(privateNotes);
        this.coverageSummary = coverageSummary;
        this.knowledgeItems = List.copyOf(knowledgeItems);
        this.memoryEntries = List.copyOf(memoryEntries);
    }

    /** Assembles the artifact with private notes excluded and no project knowledge — the safe default. */
    public static ReviewContext assemble(String prTitle, List<Change> changes, ReviewStateStore store,
                                          AnnotationBoard board) {
        return assemble(prTitle, changes, store, board, List.of());
    }

    /**
     * Assembles the artifact with private notes excluded, and the given project knowledge
     * (ticket #118) included as additional contextual evidence — empty when no Knowledge
     * Provider is configured, which is the normal, fully-supported case.
     */
    public static ReviewContext assemble(String prTitle, List<Change> changes, ReviewStateStore store,
                                          AnnotationBoard board, List<KnowledgeItem> knowledgeItems) {
        return assemble(prTitle, changes, store, board, knowledgeItems, List.of());
    }

    /**
     * Assembles the artifact with private notes excluded, and the given project knowledge
     * (ticket #118) and relevant project memory (ticket #173) both included as additional,
     * non-authoritative contextual evidence — empty when there is none, the normal case for a
     * project whose memory hasn't learned anything yet.
     */
    public static ReviewContext assemble(String prTitle, List<Change> changes, ReviewStateStore store,
                                          AnnotationBoard board, List<KnowledgeItem> knowledgeItems,
                                          List<MemoryEntry> memoryEntries) {
        return assemble(prTitle, changes, store, board, List.of(), knowledgeItems, memoryEntries);
    }

    /**
     * Assembles the artifact with private notes explicitly included — named distinctly from
     * {@link #assemble(String, List, ReviewStateStore, AnnotationBoard)} (rather than a bare
     * boolean flag) so a caller can't silently opt into the privacy-sensitive branch by
     * copy-pasting a call site without noticing which one they picked.
     */
    public static ReviewContext assembleIncludingPrivateNotes(String prTitle, List<Change> changes,
                                                                ReviewStateStore store, AnnotationBoard board) {
        return assemble(prTitle, changes, store, board, board.allPrivateNotes(), List.of(), List.of());
    }

    private static ReviewContext assemble(String prTitle, List<Change> changes, ReviewStateStore store,
                                           AnnotationBoard board, List<PrivateNote> privateNotes,
                                           List<KnowledgeItem> knowledgeItems, List<MemoryEntry> memoryEntries) {
        // Mechanical classification is reported as its own bucket, mutually exclusive with the
        // review-state buckets below — a Change already marked mechanical is not also listed as
        // reviewed/skipped/concern, matching §32's own example (Reviewed and Classified mechanical
        // are presented as separate, non-overlapping lists).
        List<Change> mechanical = changes.stream().filter(store::isMechanical).toList();
        List<Change> nonMechanical = changes.stream().filter(c -> !store.isMechanical(c)).toList();
        List<Change> reviewed = nonMechanical.stream().filter(c -> store.stateOf(c) == ReviewState.REVIEWED).toList();
        List<Change> skipped = nonMechanical.stream().filter(c -> store.stateOf(c) == ReviewState.SKIPPED).toList();
        List<Change> concerns = nonMechanical.stream().filter(c -> store.stateOf(c) == ReviewState.CONCERN).toList();
        String coverageSummary = ReviewCoverageReport.summary(changes, store);

        return new ReviewContext(prTitle, reviewed, skipped, mechanical, concerns,
                board.allComments(), privateNotes, coverageSummary, knowledgeItems, memoryEntries);
    }

    public String prTitle() {
        return prTitle;
    }

    public List<Change> reviewedChanges() {
        return reviewedChanges;
    }

    public List<Change> skippedChanges() {
        return skippedChanges;
    }

    public List<Change> mechanicalChanges() {
        return mechanicalChanges;
    }

    public List<Change> concernChanges() {
        return concernChanges;
    }

    public List<Comment> comments() {
        return comments;
    }

    /** Empty unless the reviewer explicitly opted private notes into this artifact. */
    public List<PrivateNote> privateNotes() {
        return privateNotes;
    }

    public String coverageSummary() {
        return coverageSummary;
    }

    /**
     * Project knowledge retrieved as relevant to this review (ticket #118) — contextual
     * evidence only, never authoritative. Empty when no Knowledge Provider is configured, or
     * when none of its knowledge was relevant to this review's changed files.
     */
    public List<KnowledgeItem> knowledgeItems() {
        return knowledgeItems;
    }

    /**
     * Project memory (ticket #169) relevant to this review's changed files (ticket #173) —
     * additive, non-authoritative evidence, exactly like {@link #knowledgeItems()}. Empty when
     * the project's memory hasn't learned anything relevant yet, or hasn't learned anything at
     * all — the normal, fully-supported case.
     */
    public List<MemoryEntry> memoryEntries() {
        return memoryEntries;
    }
}
