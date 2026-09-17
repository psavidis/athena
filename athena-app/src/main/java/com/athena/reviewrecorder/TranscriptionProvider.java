package com.athena.reviewrecorder;

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
 */
public interface TranscriptionProvider {

    /** A transcript for the current recording, if this provider produced one. */
    Optional<String> transcribe();
}
