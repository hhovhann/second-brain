#!/usr/bin/env python3
"""Speech to text, locally. Prints a JSON list [{"start": seconds, "text": "..."}] on stdout.

Usage: transcribe.py <audio-file>
Picks the first installed backend: mlx_whisper (Apple Silicon), faster_whisper, openai-whisper.
Model: BRAIN_WHISPER_MODEL (default: whisper-small for the chosen backend).
Progress and errors go to stderr; nothing leaves the machine except the one-time model download.
"""
import json
import os
import sys


def segments_mlx(path, model):
    import mlx_whisper
    result = mlx_whisper.transcribe(path, path_or_hf_repo=model or "mlx-community/whisper-small-mlx")
    return result["segments"]


def segments_faster(path, model):
    from faster_whisper import WhisperModel
    segments, _ = WhisperModel(model or "small").transcribe(path)
    return [{"start": s.start, "text": s.text} for s in segments]


def segments_openai(path, model):
    import whisper
    return whisper.load_model(model or "small").transcribe(path)["segments"]


BACKENDS = [("mlx_whisper", segments_mlx), ("faster_whisper", segments_faster), ("whisper", segments_openai)]


def main():
    if len(sys.argv) != 2:
        sys.exit("usage: transcribe.py <audio-file>")
    model = os.environ.get("BRAIN_WHISPER_MODEL") or None
    for module, run in BACKENDS:
        try:
            __import__(module)
        except ImportError:
            continue
        segments = run(sys.argv[1], model)
        print(json.dumps([{"start": float(s["start"]), "text": s["text"].strip()} for s in segments if s["text"].strip()]))
        return
    sys.exit("no speech-to-text backend found. Install one: pip install mlx-whisper (Apple Silicon) "
             "or pip install faster-whisper")


main()
