package com.athena.reviewui;

import com.athena.semantic.Change;
import com.athena.semantic.ChangeCategory;
import com.athena.semantic.ChangeEvidence;

import java.util.Set;

/**
 * The Change detail view a reviewer opens from a Change Map entry: category,
 * transformation description, and the symbols/files it touches — the next
 * level down from the Change Map in the Intent → Change → Symbol → File →
 * Diff → Line hierarchy (epic #5 §15).
 *
 * <p>Evidence is sourced from {@link ChangeEvidence}, which is available
 * regardless of the Change's category — classification never gates access
 * to the underlying diff (§10, §44).
 */
public final class ChangeDetailView {

    private final ChangeCategory category;
    private final String description;
    private final ChangeEvidence evidence;

    private ChangeDetailView(ChangeCategory category, String description, ChangeEvidence evidence) {
        this.category = category;
        this.description = description;
        this.evidence = evidence;
    }

    public static ChangeDetailView of(Change change) {
        return new ChangeDetailView(ChangeCategory.of(change.kind()), change.title(), ChangeEvidence.of(change));
    }

    public ChangeCategory category() {
        return category;
    }

    public String description() {
        return description;
    }

    /** The symbols this Change involves, for drilling down to symbol-level navigation. */
    public Set<String> symbols() {
        return evidence.symbols();
    }

    /** The files this Change touches, for drilling down to file-level navigation. */
    public Set<String> files() {
        return evidence.files();
    }

    /** The underlying textual diff, always available regardless of classification. */
    public String textualDiff() {
        return evidence.textualDiff();
    }
}
