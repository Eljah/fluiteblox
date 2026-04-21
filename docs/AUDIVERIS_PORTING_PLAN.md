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

## Optional dependency wiring

To test with Audiveris ecosystem dependency on environments that support it:

```bash
mvn -Daudiveris.dependency=true -DskipTests compile
```

This enables profile `audiveris-dependency` (adds `org.audiveris:proxymusic:4.0.3`).
Runtime presence is detected via `AudiverisDependencyBridge` and reflected in processing mode suffix.
