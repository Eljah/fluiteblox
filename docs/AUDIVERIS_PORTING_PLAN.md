# Audiveris porting status (Android, no server)

This repository now contains a real on-device compatibility layer for Audiveris-style recognition:

- `AudiverisCompatRecognitionEngine` implements:
  - grayscale conversion
  - Otsu binarization
  - horizontal projection staff-line detection
  - connected-component notehead candidate extraction
  - pitch mapping from staff position (treble clef baseline)
- If compatibility pass does not produce stable staff/note output, it falls back to `OpenCvRecognitionEngine`.

## Why this approach

The upstream `Audiveris/audiveris` project is desktop-oriented and depends on Java/tooling not directly
compatible with this Android Java 8 runtime. So the porting path is algorithmic compatibility:
reimplement compatible stages on Android while preserving local/offline execution.

## Next compatibility tasks

1. Port stem/beam filtering heuristics from Audiveris morphology stages.
2. Add accidental/rest classifiers.
3. Add confidence scores and manual correction workflow based on symbol confidence.
