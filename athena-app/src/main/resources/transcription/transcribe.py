#!/usr/bin/env python3
"""Transcribes and diarizes one audio file via WhisperX + pyannote.audio
(ticket #251), printing the result as JSON on stdout.

Not a Python library consumed by other Python code in this repo — this is
the entire integration surface between Athena's Java backend and the
WhisperX/pyannote pipeline (ticket #250's decision). Athena's Java
`WhisperXTranscriptionProvider` shells out to this script per recording,
matching `com.athena.plugins.ESLintAnalysisProvider`'s own precedent for
integrating an external tool: a subprocess, JSON on stdout, non-zero exit
on failure rather than a Java-side exception for an ordinary failure mode
(model not downloaded, HF auth missing, unsupported audio, etc).

Requires Python <=3.13 (whisperx's pinned ctranslate2 dependency has no
wheel for 3.14 as of this writing — verified during #250's investigation)
and a Hugging Face token with access to whichever diarization model the
installed whisperx version requests (see this script's own error output
if access is missing — it surfaces the model's own gated-access message
rather than swallowing it).

Output JSON shape (on success, exit 0):
    {
      "segments": [
        {"text": "...", "start": 0.031, "end": 3.103, "speaker": "SPEAKER_01"},
        ...
      ]
    }
A segment's "speaker" key is omitted entirely when diarization could not
attribute one — never a null/empty placeholder, so the Java side's parsing
can treat "key present" as "speaker known" directly.

Accepts an optional trailing `--model NAME` (default "large-v3", the
production-accuracy model). Tests point this at a much smaller model
(e.g. "tiny") via WhisperXTranscriptionProvider's own model-name
parameter — loading/running "large-v3" on every test invocation (a
~2.9GB model, expensive even on CPU regardless of whether it is already
downloaded) is what made the test suite slow; the model is the accuracy
knob, and unit/Gherkin tests here only need the pipeline's wiring to be
correct, not its transcription accuracy.

On failure (tool/model unavailable, bad input, inference error): prints a
one-line diagnostic to stderr and exits non-zero. Never partially writes
malformed JSON to stdout on failure.
"""
import json
import logging
import sys


def _silence_whisperx_console_logging() -> None:
    """WhisperX's own whisperx/log_utils.py (source-verified, not just observed):
    get_logger(name), called at each whisperx submodule's own import time, runs
    setup_logging() — which hardcodes a StreamHandler(sys.stdout) — whenever the shared
    "whisperx" logger currently has NO handlers. Several whisperx submodules (asr.py,
    vads/pyannote.py, diarize.py, ...) are imported lazily, at different points during a
    pipeline run rather than all at `import whisperx` time, so this guard can re-trigger
    more than once per run. The first version of this function called
    `whisperx_logger.handlers.clear()`, which left the handler list empty and made the
    NEXT lazily-imported submodule's get_logger() call see "no handlers" and re-run
    setup_logging() — re-attaching a fresh stdout handler AND resetting the level (whose
    reset undid this function's own setLevel(CRITICAL+1) from a moment earlier). The fix:
    leave a single inert NullHandler in place instead of clearing to empty, so `if not
    whisperx_logger.handlers` is never true again for the rest of this process, no matter
    how many more whisperx submodules get lazily imported afterward."""
    whisperx_logger = logging.getLogger("whisperx")
    whisperx_logger.handlers.clear()
    whisperx_logger.addHandler(logging.NullHandler())
    whisperx_logger.setLevel(logging.CRITICAL + 1)
    whisperx_logger.propagate = False


DEFAULT_MODEL = "large-v3"


def transcribe(audio_path: str, min_speakers: int | None, max_speakers: int | None,
               model_name: str = DEFAULT_MODEL) -> dict:
    # Silenced BEFORE `import whisperx`: whisperx.asr logs "No language specified" from
    # inside load_model() itself (source-verified: whisperx/asr.py line ~368, before
    # load_model returns) — too early for any fix placed after that call. Pre-configuring
    # the shared "whisperx" parent logger here works regardless of when its various
    # submodules (asr.py, vads/pyannote.py, diarize.py, ...) end up being lazily imported:
    # a child logger created later with no explicit level of its own defers to its
    # parent's effective level at the moment each log call happens, not at logger-creation
    # time — so this only needs to run once, first, not be re-applied after each stage.
    _silence_whisperx_console_logging()
    import whisperx  # imported lazily: a missing/broken install must surface as this
                      # script's own clean non-zero exit (see main()), not an import-time
                      # traceback bleeding into stdout before JSON output has even started.

    device = "cpu"
    compute_type = "int8"

    model = whisperx.load_model(model_name, device, compute_type=compute_type)
    audio = whisperx.load_audio(audio_path)
    result = model.transcribe(audio, batch_size=16)

    align_model, align_metadata = whisperx.load_align_model(language_code=result["language"], device=device)
    result = whisperx.align(result["segments"], align_model, align_metadata, audio, device)

    diarize_model = whisperx.diarize.DiarizationPipeline(device=device)
    diarize_segments = diarize_model(audio, min_speakers=min_speakers, max_speakers=max_speakers)
    result = whisperx.assign_word_speakers(diarize_segments, result)

    segments = []
    for segment in result["segments"]:
        entry = {
            "text": segment["text"].strip(),
            "start": segment["start"],
            "end": segment["end"],
        }
        if "speaker" in segment and segment["speaker"]:
            entry["speaker"] = segment["speaker"]
        segments.append(entry)
    return {"segments": segments}


def main() -> int:
    if len(sys.argv) < 2:
        print("usage: transcribe.py <audio_path> [--min-speakers N] [--max-speakers N]", file=sys.stderr)
        return 2

    audio_path = sys.argv[1]
    min_speakers = None
    max_speakers = None
    model_name = DEFAULT_MODEL
    args = sys.argv[2:]
    for i in range(0, len(args) - 1, 2):
        if args[i] == "--min-speakers":
            min_speakers = int(args[i + 1])
        elif args[i] == "--max-speakers":
            max_speakers = int(args[i + 1])
        elif args[i] == "--model":
            model_name = args[i + 1]

    try:
        result = transcribe(audio_path, min_speakers, max_speakers, model_name)
    except Exception as e:  # noqa: BLE001 - deliberately broad: any failure here must become
                             # a clean non-zero exit + stderr message, matching
                             # ESLintAnalysisProvider's "never throws for an ordinary failure"
                             # contract on the Java side that shells out to this script.
        print(f"transcription failed: {e}", file=sys.stderr)
        return 1

    json.dump(result, sys.stdout)
    return 0


if __name__ == "__main__":
    sys.exit(main())
