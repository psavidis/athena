package com.athena.semantic;

/** Why a changed file has no Change representing it (ticket #260). */
public enum UnrepresentedReason {
    /** No language plugin understands this file type (build files, templates, ...). */
    UNSUPPORTED_FILE_TYPE("unsupported file type"),
    /** A plugin understands the file type but the file failed to parse. */
    PARSE_FAILED("could not be parsed"),
    /** The file parsed, but no detector produced a Change for its edit. */
    NO_SEMANTIC_CHANGE("no semantic change detected");

    private final String label;

    UnrepresentedReason(String label) {
        this.label = label;
    }

    /** The reviewer-facing wording, e.g. "unsupported file type". */
    public String label() {
        return label;
    }
}
