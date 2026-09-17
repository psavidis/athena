package com.athena.reviewreplay;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Discovers {@link TranscriptReference}s already linked in a PR/MR
 * description (ticket #213): a markdown link whose line also mentions
 * "transcript" (case-insensitive) — a narrow, common PR-description
 * convention (e.g. "**Transcript:** [Recording](https://...)"), chosen
 * over a fixed platform whitelist since transcript-hosting platforms are
 * numerous and unbounded, and a whitelist would silently miss any not on
 * it. The platform is the link's URL host; the identifier is its path
 * (falling back to the host itself if the path is empty); format is never
 * inferred from a bare URL, since nothing here says whether it's audio,
 * video, or text.
 */
public final class TranscriptReferenceParser {

    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[[^]]*]\\((https?://[^)\\s]+)\\)");

    private TranscriptReferenceParser() {
    }

    /** Every transcript reference discoverable in {@code description}, in the order they appear. Never null. */
    public static List<TranscriptReference> discover(String description) {
        List<TranscriptReference> references = new ArrayList<>();
        if (description == null || description.isBlank()) {
            return references;
        }
        for (String line : description.split("\n")) {
            if (!line.toLowerCase().contains("transcript")) {
                continue;
            }
            Matcher matcher = MARKDOWN_LINK.matcher(line);
            while (matcher.find()) {
                references.add(toReference(matcher.group(1)));
            }
        }
        return references;
    }

    private static TranscriptReference toReference(String url) {
        URI uri = URI.create(url);
        String host = uri.getHost() != null ? uri.getHost() : url;
        String path = uri.getPath();
        String identifier = (path != null && !path.isBlank()) ? path : host;
        return TranscriptReference.of(host, identifier, url, null);
    }
}
