package com.athena.web;

import com.athena.semantic.Change;
import com.athena.semantic.ChangeIdentity;
import com.athena.semantic.TransformationKind;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

/**
 * An opaque, URL-safe encoding of a {@link ChangeIdentity} (ticket #75), so
 * a Change Map entry can be referenced again in a later request (detail
 * view, comments) even though {@link Change} itself has no stable identity
 * and nothing caches the detected Change list between requests.
 */
public final class ChangeKey {

    private ChangeKey() {
    }

    public static String encode(Change change) {
        ChangeIdentity identity = ChangeIdentity.of(change);
        String raw = identity.transformationShape() + "\n" + String.join("\n", new TreeSet<>(identity.symbolSet()));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** Finds the Change among {@code candidates} whose key equals {@code encoded}, if any. */
    public static Optional<Change> resolve(String encoded, List<Change> candidates) {
        String[] lines = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8).split("\n", -1);
        TransformationKind kind = TransformationKind.valueOf(lines[0]);
        TreeSet<String> symbols = new TreeSet<>();
        for (int i = 1; i < lines.length; i++) {
            if (!lines[i].isEmpty()) {
                symbols.add(lines[i]);
            }
        }
        return candidates.stream()
                .filter(candidate -> {
                    ChangeIdentity candidateIdentity = ChangeIdentity.of(candidate);
                    return candidateIdentity.transformationShape() == kind
                            && candidateIdentity.symbolSet().equals(symbols);
                })
                .findFirst();
    }
}
