package com.athena.reviewrecorder;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * One spoken segment of a transcript (ticket #209): its text, when it was
 * spoken, and the speaker if the transcription provider could identify
 * one. This is raw transcript data — never a semantic event or an
 * interpretation — matching {@link SemanticEvent}'s own precedent of
 * relaying a reference rather than interpreting it. {@link
 * TranscriptAligner} is what associates a segment with an entity; a
 * segment on its own carries no such association.
 */
public final class TranscriptSegment {

    private final String text;
    private final Instant spokenAt;
    private final String speaker;

    private TranscriptSegment(String text, Instant spokenAt, String speaker) {
        this.text = text;
        this.spokenAt = spokenAt;
        this.speaker = speaker;
    }

    /** A segment with a known speaker. */
    public static TranscriptSegment of(String text, Instant spokenAt, String speaker) {
        Objects.requireNonNull(speaker, "speaker");
        if (speaker.isBlank()) {
            throw new IllegalArgumentException("speaker must not be blank");
        }
        return new TranscriptSegment(requireText(text), requireSpokenAt(spokenAt), speaker);
    }

    /** A segment whose speaker the transcription provider could not identify. */
    public static TranscriptSegment withoutSpeaker(String text, Instant spokenAt) {
        return new TranscriptSegment(requireText(text), requireSpokenAt(spokenAt), null);
    }

    public String text() {
        return text;
    }

    public Instant spokenAt() {
        return spokenAt;
    }

    public Optional<String> speaker() {
        return Optional.ofNullable(speaker);
    }

    private static String requireText(String text) {
        Objects.requireNonNull(text, "text");
        if (text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        return text;
    }

    private static Instant requireSpokenAt(Instant spokenAt) {
        return Objects.requireNonNull(spokenAt, "spokenAt");
    }
}
