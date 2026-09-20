package com.athena.reviewrecorder;

import java.util.List;
import java.util.Optional;

/**
 * The only {@link TranscriptionProvider} that exists today (ticket
 * #208): never produces a transcript, regardless of whether audio
 * capture was enabled for a recording. No real transcription vendor is
 * being adopted (a 2026-09-20 product decision) — #209 (alignment) and
 * #214 (conversation reconstruction) ship as best-effort against this
 * provider rather than a real one: their logic is real, but {@link
 * #segments()} staying empty means they always have nothing to work
 * with in the running system.
 */
public final class NoOpTranscriptionProvider implements TranscriptionProvider {

    @Override
    public Optional<String> transcribe() {
        return Optional.empty();
    }

    @Override
    public List<TranscriptSegment> segments() {
        return List.of();
    }
}
