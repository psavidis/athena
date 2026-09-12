package com.athena.ai;

import com.athena.reviewui.ChangeDetailView;

import java.util.Optional;

/**
 * One AI-surfaced finding as the reviewer sees it: its own independent
 * {@link AiFindingDisposition}, a jump target when it names a specific
 * Change (epic #5 #38's drill-down, via {@link ChangeDetailView}), and a
 * fixed AI attribution — this board only ever holds AI-surfaced findings,
 * never the reviewer's own comments or review state (epic #7 §35).
 */
public final class AiFindingItem {

    private final AiFinding finding;
    private final AiFindingDisposition disposition;
    private final Optional<ChangeDetailView> jumpTarget;

    AiFindingItem(AiFinding finding, AiFindingDisposition disposition, Optional<ChangeDetailView> jumpTarget) {
        this.finding = finding;
        this.disposition = disposition;
        this.jumpTarget = jumpTarget;
    }

    public String id() {
        return finding.id();
    }

    public String description() {
        return finding.description();
    }

    public AiFindingDisposition disposition() {
        return disposition;
    }

    /** Always "AI" — distinguishes this item from the reviewer's own comments/review state. */
    public String source() {
        return "AI";
    }

    /** The Change to jump to, when this finding names one; empty for a general, PR-wide finding. */
    public Optional<ChangeDetailView> jumpTarget() {
        return jumpTarget;
    }
}
