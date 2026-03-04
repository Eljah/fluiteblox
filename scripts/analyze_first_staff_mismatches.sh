#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT_DIR="$ROOT/target/first-staff-analysis"
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR/android/graphics" "$OUT_DIR/android/content" "$OUT_DIR/android/content/res" "$OUT_DIR/tatar/eljah/recorder"

cp "$ROOT/target/stem-duration-test/android/graphics/Bitmap.java" "$OUT_DIR/android/graphics/Bitmap.java"
cp "$ROOT/target/stem-duration-test/android/graphics/Color.java" "$OUT_DIR/android/graphics/Color.java"
cp "$ROOT/target/stem-duration-test/android/content/Context.java" "$OUT_DIR/android/content/Context.java"
cp "$ROOT/target/stem-duration-test/android/content/res/AssetManager.java" "$OUT_DIR/android/content/res/AssetManager.java"
cp "$ROOT/target/stem-duration-test/tatar/eljah/recorder/AppLocaleManager.java" "$OUT_DIR/tatar/eljah/recorder/AppLocaleManager.java"
cp "$ROOT/target/stem-duration-test/tatar/eljah/recorder/ReferenceCompositionExtractor.java" "$OUT_DIR/tatar/eljah/recorder/ReferenceCompositionExtractor.java"

OPENCV_JAR="$ROOT/target/photo-screenshot-test/opencv-4.9.0-0.jar"
if [[ ! -f "$OPENCV_JAR" ]]; then
  mkdir -p "$(dirname "$OPENCV_JAR")"
  curl -fsSL -o "$OPENCV_JAR" "https://repo1.maven.org/maven2/org/openpnp/opencv/4.9.0-0/opencv-4.9.0-0.jar"
fi

javac -cp "$OPENCV_JAR" -d "$OUT_DIR" \
  "$OUT_DIR/android/graphics/Bitmap.java" \
  "$OUT_DIR/android/graphics/Color.java" \
  "$OUT_DIR/android/content/Context.java" \
  "$OUT_DIR/android/content/res/AssetManager.java" \
  "$OUT_DIR/tatar/eljah/recorder/AppLocaleManager.java" \
  "$OUT_DIR/tatar/eljah/recorder/ReferenceCompositionExtractor.java" \
  "$ROOT/src/main/java/tatar/eljah/recorder/NoteEvent.java" \
  "$ROOT/src/main/java/tatar/eljah/recorder/ScorePiece.java" \
  "$ROOT/src/main/java/tatar/eljah/recorder/MusicNotation.java" \
  "$ROOT/src/main/java/tatar/eljah/recorder/ReferenceComposition.java" \
  "$ROOT/src/main/java/tatar/eljah/recorder/OpenCvScoreProcessor.java" \
  "$ROOT/src/test/java/tatar/eljah/recorder/FirstStaffMismatchAnalysis.java"

java -cp "$OUT_DIR:$OPENCV_JAR" tatar.eljah.recorder.FirstStaffMismatchAnalysis
