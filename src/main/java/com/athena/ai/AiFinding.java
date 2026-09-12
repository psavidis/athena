package com.athena.ai;

/**
 * A single candidate finding returned by an {@link AiProvider}: something
 * the human reviewer's own review may have missed (epic #7 §35). Each
 * finding carries its own {@code id} so it can be evaluated independently
 * of the others — accepting or dismissing one has no effect on the rest
 * (ticket #48's concern; this is just the identity findings need for that).
 */
public record AiFinding(String id, String description) {
}
