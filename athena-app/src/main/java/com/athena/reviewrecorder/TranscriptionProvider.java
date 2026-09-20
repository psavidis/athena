package com.athena.reviewrecorder;

import java.util.List;
import java.util.Optional;

/**
 * The seam a real speech-to-text integration will fill in once one is
 * chosen (ticket #208 flags this as a product/vendor decision this
 * ticket doesn't make — see the ticket's own "Limitations of Scope").
 * Until then, only {@link NoOpTranscriptionProvider} exists: audio
 * capture can be enabled as an intent, but no actual transcription ever
 * happens. {@link #transcribe} takes no audio parameter yet — that shape
 * is left to whichever ticket wires up a real provider, since it depends
 * on the provider chosen (streaming vs. batch, format, etc.).
 *
 * <p>{@link #segments()} is the structured counterpart {@link
 * TranscriptAligner} (ticket #209) aligns against — timestamped, and
 * speaker-attributed where the provider supports it. This session's
 * product decision was to ship #209/#214 as best-effort against
 * whichever provider exists today rather than block on a vendor choice:
 * since {@link NoOpTranscriptionProvider} is that provider and it never
 * produces a transcript, {@link #segments()} always returns empty in the
 * running system, but the alignment logic downstream of it is real and
 * independently tested against this shape.
 */
public interface TranscriptionProvider {

    /** A transcript for the current recording, if this provider produced one. */
    Optional<String> transcribe();

    /**
     * This provider's transcript as individually timestamped, speaker-attributed segments — the
     * shape {@link TranscriptAligner} needs. Empty whenever {@link #transcribe()} is, since there
     * is nothing to segment.
     */
    List<TranscriptSegment> segments();
}
