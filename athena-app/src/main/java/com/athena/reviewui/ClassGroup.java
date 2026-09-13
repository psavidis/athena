package com.athena.reviewui;

import java.util.List;

/**
 * Several {@link ChangeMapEntry} rows that share an enclosing type, shown as
 * one collapsible row on the Change Map instead of one disconnected row per
 * member (epic #5 §14 follow-up). A class touched by only one Change still
 * gets a ClassGroup of size 1 — the frontend decides whether a single-entry
 * group is worth collapsing.
 */
public final class ClassGroup {

    private final String enclosingType;
    private final List<ChangeMapEntry> entries;

    ClassGroup(String enclosingType, List<ChangeMapEntry> entries) {
        this.enclosingType = enclosingType;
        this.entries = List.copyOf(entries);
    }

    public String enclosingType() {
        return enclosingType;
    }

    public List<ChangeMapEntry> entries() {
        return entries;
    }
}
