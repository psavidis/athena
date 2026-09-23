package com.athena.semantic;

/** A changed file no Change represents, and why (ticket #260). */
public record UnrepresentedFile(ChangedFile file, UnrepresentedReason reason) {
}
