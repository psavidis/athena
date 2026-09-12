package com.athena.ai;

import java.util.Optional;

/**
 * A single candidate finding returned by an {@link AiProvider}: something
 * the human reviewer's own review may have missed (epic #7 §35). Each
 * finding carries its own {@code id} so it can be evaluated independently
 * of the others — accepting or dismissing one has no effect on the rest
 * (ticket #48's concern; this is just the identity findings need for that).
 *
 * <p>{@code relatedChangeTitle}, when present, names the exact
 * {@link com.athena.semantic.Change#title()} this finding is about —
 * ticket #48 uses it to offer a jump-to-location target. Absent for a
 * general finding not tied to one specific Change.
 */
public record AiFinding(String id, String description, Optional<String> relatedChangeTitle) {
}
