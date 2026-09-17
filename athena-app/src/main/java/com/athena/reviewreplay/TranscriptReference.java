package com.athena.reviewreplay;

import java.util.Objects;
import java.util.Optional;

/**
 * Metadata about a transcript already linked in a PR/MR's description
 * (ticket #213) — platform, a caller-visible identifier, its URL, and its
 * format when it can be determined (a bare URL alone rarely says). Never
 * the transcript's own content: Replay treats a transcript as an external
 * resource it references, not something it copies wholesale into Athena's
 * model.
 */
public final class TranscriptReference {

    private final String platform;
    private final String identifier;
    private final String url;
    private final String format;

    private TranscriptReference(String platform, String identifier, String url, String format) {
        this.platform = platform;
        this.identifier = identifier;
        this.url = url;
        this.format = format;
    }

    public static TranscriptReference of(String platform, String identifier, String url, String format) {
        Objects.requireNonNull(platform, "platform");
        Objects.requireNonNull(identifier, "identifier");
        Objects.requireNonNull(url, "url");
        return new TranscriptReference(platform, identifier, url, format);
    }

    public String platform() {
        return platform;
    }

    public String identifier() {
        return identifier;
    }

    public String url() {
        return url;
    }

    public Optional<String> format() {
        return Optional.ofNullable(format);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TranscriptReference other)) return false;
        return platform.equals(other.platform) && identifier.equals(other.identifier) && url.equals(other.url)
                && Objects.equals(format, other.format);
    }

    @Override
    public int hashCode() {
        return Objects.hash(platform, identifier, url, format);
    }

    @Override
    public String toString() {
        return "TranscriptReference[" + platform + ", " + identifier + "]";
    }
}
