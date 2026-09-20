package com.athena.reviewrecorder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Merges multiple participants' separately-transcribed {@link
 * TranscriptSegment} streams into one chronological conversation for a
 * remote/call-based Review Recording (ticket #251, per #250's decision):
 * each participant captures and transcribes only their own microphone —
 * single-speaker per stream, so no diarization is needed — and this
 * merges those streams by {@link TranscriptSegment#spokenAt()} alone.
 *
 * <p>This class does not receive real network input: wiring participants'
 * actual captured audio to real per-participant streams (upload,
 * transcription per stream, and establishing a clock basis the
 * {@code spokenAt} timestamps of different machines can be compared
 * against) is ticket #252's scope, not this class's — {@link #merge}
 * trusts that its input streams' timestamps are already comparable.
 *
 * <p>Degrades gracefully rather than failing when a stream is missing or
 * empty (ticket #209's own "unaligned rather than guessed" precedent for
 * missing data, applied here to a missing participant): an empty or
 * absent stream simply contributes nothing to the merged result.
 */
public final class RemoteTranscriptMerger {

    private RemoteTranscriptMerger() {
    }

    /**
     * Merges {@code streams} (one {@link List} of {@link TranscriptSegment} per participant, in
     * any order relative to each other) into a single list ordered by {@link
     * TranscriptSegment#spokenAt()}. Segments spoken at the exact same instant keep the relative
     * order they arrived in {@code streams} (a stable sort), since real simultaneous speech has no
     * further signal here to break the tie with.
     */
    public static List<TranscriptSegment> merge(List<List<TranscriptSegment>> streams) {
        Objects.requireNonNull(streams, "streams");
        List<TranscriptSegment> merged = new ArrayList<>();
        for (List<TranscriptSegment> stream : streams) {
            Objects.requireNonNull(stream, "stream");
            merged.addAll(stream);
        }
        merged.sort(Comparator.comparing(TranscriptSegment::spokenAt));
        return List.copyOf(merged);
    }
}
