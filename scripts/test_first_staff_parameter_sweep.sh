#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT_DIR="$ROOT/target/first-staff-sweep"
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR/android/graphics" "$OUT_DIR/android/content" "$OUT_DIR/android/content/res" "$OUT_DIR/tatar/eljah/recorder"

cat > "$OUT_DIR/android/graphics/Bitmap.java" <<'JAVA'
package android.graphics;
public class Bitmap {
    public enum Config { ARGB_8888 }
    private final int width;
    private final int height;
    private final int[] pixels;
    private Bitmap(int width, int height) { this.width = width; this.height = height; this.pixels = new int[width * height]; }
    public static Bitmap createBitmap(int width, int height, Config ignored) { return new Bitmap(width, height); }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getPixel(int x, int y) { return pixels[y * width + x]; }
    public void setPixel(int x, int y, int color) { pixels[y * width + x] = color; }
    public void setPixels(int[] src, int offset, int stride, int x, int y, int w, int h) {
        for (int row = 0; row < h; row++) {
            System.arraycopy(src, offset + row * stride, pixels, (y + row) * width + x, w);
        }
    }
}
JAVA

cat > "$OUT_DIR/android/graphics/Color.java" <<'JAVA'
package android.graphics;
public final class Color {
    private Color() {}
    public static int red(int color) { return (color >> 16) & 0xff; }
    public static int green(int color) { return (color >> 8) & 0xff; }
    public static int blue(int color) { return color & 0xff; }
}
JAVA

cat > "$OUT_DIR/android/content/Context.java" <<'JAVA'
package android.content;
public class Context {}
JAVA

cat > "$OUT_DIR/android/content/res/AssetManager.java" <<'JAVA'
package android.content.res;
import java.io.InputStream;
public class AssetManager { public InputStream open(String name) { return null; } }
JAVA

cat > "$OUT_DIR/tatar/eljah/recorder/AppLocaleManager.java" <<'JAVA'
package tatar.eljah.recorder;
import android.content.Context;
public final class AppLocaleManager {
    private AppLocaleManager() {}
    public static String savedLanguage(Context context) { return null; }
}
JAVA

cat > "$OUT_DIR/tatar/eljah/recorder/ReferenceCompositionExtractor.java" <<'JAVA'
package tatar.eljah.recorder;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
public final class ReferenceCompositionExtractor {
    private ReferenceCompositionExtractor() {}
    public static byte[] decodeBase64Midi(InputStream in) { return new byte[0]; }
    public static List<NoteEvent> extractFromXmlAndMidi(InputStream xmlIn, byte[] midiBytes, int limit) { return new ArrayList<NoteEvent>(); }
}
JAVA

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
  "$ROOT/src/test/java/tatar/eljah/recorder/FirstStaffParameterSweepTest.java"

java -cp "$OUT_DIR:$OPENCV_JAR" tatar.eljah.recorder.FirstStaffParameterSweepTest
