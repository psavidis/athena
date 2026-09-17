package com.athena.reviewrecorder;

import java.util.Optional;

/**
 * The only {@link TranscriptionProvider} that exists today (ticket
 * #208): never produces a transcript, regardless of whether audio
 * capture was enabled for a recording. Placeholder until a real
 * transcription vendor is chosen (#209/#214 stay blocked on that
 * decision) — never transcribes real audio, since none is ever captured
 * while this is the only provider wired up.
 */
public final class NoOpTranscriptionProvider implements TranscriptionProvider {

    @Override
    public Optional<String> transcribe() {
        return Optional.empty();
    }
}
