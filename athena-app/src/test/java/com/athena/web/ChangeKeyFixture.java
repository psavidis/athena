package com.athena.web;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** A syntactically valid but never-matching {@link ChangeKey}, for "not found" test scenarios. */
public final class ChangeKeyFixture {

    private ChangeKeyFixture() {
    }

    public static String unmatched() {
        String raw = "REMOVE_SYMBOL\nNoSuchSymbol#neverDetected";
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
