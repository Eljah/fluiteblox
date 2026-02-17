package tatar.eljah.recorder;

import android.util.Log;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class RecorderNoteMapper {
    private static final String TAG = "RecorderNoteMapper";
    private static final Map<String, Float> NOTE_FREQUENCIES;
    private static final Map<String, String> FINGERINGS;

    static {
        Map<String, Float> frequencies = new LinkedHashMap<String, Float>();
        // Alto recorder in G, equal temperament A4 = 440Hz.
        // Supported range: C4 (261.63Hz) .. A6 (1760Hz), chromatic scale with recorder-oriented fallbacks.
        frequencies.put("C4", 261.63f);
        frequencies.put("C#4", 277.18f);
        frequencies.put("Db4", 277.18f);
        frequencies.put("D4", 293.66f);
        frequencies.put("D#4", 311.13f);
        frequencies.put("Eb4", 311.13f);
        frequencies.put("E4", 329.63f);
        frequencies.put("F4", 349.23f);
        frequencies.put("F#4", 369.99f);
        frequencies.put("Gb4", 369.99f);
        frequencies.put("G4", 392.00f);
        frequencies.put("G#4", 415.30f);
        frequencies.put("Ab4", 415.30f);
        frequencies.put("A4", 440.00f);
        frequencies.put("A#4", 466.16f);
        frequencies.put("Bb4", 466.16f);
        frequencies.put("B4", 493.88f);
        frequencies.put("C5", 523.25f);
        frequencies.put("C#5", 554.37f);
        frequencies.put("Db5", 554.37f);
        frequencies.put("D5", 587.33f);
        frequencies.put("D#5", 622.25f);
        frequencies.put("Eb5", 622.25f);
        frequencies.put("E5", 659.25f);
        frequencies.put("F5", 698.46f);
        frequencies.put("F#5", 739.99f);
        frequencies.put("Gb5", 739.99f);
        frequencies.put("G5", 783.99f);
        frequencies.put("G#5", 830.61f);
        frequencies.put("Ab5", 830.61f);
        frequencies.put("A5", 880.00f);
        frequencies.put("A#5", 932.33f);
        frequencies.put("Bb5", 932.33f);
        frequencies.put("B5", 987.77f);
        frequencies.put("C6", 1046.50f);
        frequencies.put("C#6", 1108.73f);
        frequencies.put("Db6", 1108.73f);
        frequencies.put("D6", 1174.66f);
        frequencies.put("D#6", 1244.51f);
        frequencies.put("Eb6", 1244.51f);
        frequencies.put("E6", 1318.51f);
        frequencies.put("F6", 1396.91f);
        frequencies.put("F#6", 1479.98f);
        frequencies.put("Gb6", 1479.98f);
        frequencies.put("G6", 1567.98f);
        frequencies.put("G#6", 1661.22f);
        frequencies.put("Ab6", 1661.22f);
        frequencies.put("A6", 1760.00f);
        NOTE_FREQUENCIES = Collections.unmodifiableMap(frequencies);

        Map<String, String> fingerings = new LinkedHashMap<String, String>();
        fingerings.put("C4", "●●●|●●●●");
        fingerings.put("C#4", "●●●|●●●◐");
        fingerings.put("Db4", "●●●|●●●◐");
        fingerings.put("D4", "●●●|●●●●");
        fingerings.put("D#4", "●●●|●●●◐");
        fingerings.put("Eb4", "●●●|●●●◐");
        fingerings.put("E4", "●●●|●●●○");
        fingerings.put("F4", "●●●|●●○○");
        fingerings.put("F#4", "●●●|●◐○○");
        fingerings.put("Gb4", "●●●|●◐○○");
        fingerings.put("G4", "●●○|○○○○");
        fingerings.put("G#4", "●◐○|○○○○");
        fingerings.put("Ab4", "●◐○|○○○○");
        fingerings.put("A4", "●○○|○○○○");
        fingerings.put("A#4", "◐○○|○○○○");
        fingerings.put("Bb4", "◐○○|○○○○");
        fingerings.put("B4", "○○○|○○○○");
        fingerings.put("C5", "●●●|●●●○");
        fingerings.put("C#5", "●●●|●●◐○");
        fingerings.put("Db5", "●●●|●●◐○");
        fingerings.put("D5", "●●●|●●○○");
        fingerings.put("D#5", "●●●|●◐○○");
        fingerings.put("Eb5", "●●●|●◐○○");
        fingerings.put("E5", "●●●|●○○○");
        fingerings.put("F5", "●●●|◐○○○");
        fingerings.put("F#5", "●●●|○○○○");
        fingerings.put("Gb5", "●●●|○○○○");
        fingerings.put("G5", "●●○|○○○○");
        fingerings.put("G#5", "●◐○|○○○○");
        fingerings.put("Ab5", "●◐○|○○○○");
        fingerings.put("A5", "●○○|○○○○");
        fingerings.put("A#5", "◐○○|○○○○");
        fingerings.put("Bb5", "◐○○|○○○○");
        fingerings.put("B5", "○○○|○○○○");
        fingerings.put("C6", "●●●|●●●○");
        fingerings.put("C#6", "●●●|●●◐○");
        fingerings.put("Db6", "●●●|●●◐○");
        fingerings.put("D6", "●●●|●●○○");
        fingerings.put("D#6", "●●●|●◐○○");
        fingerings.put("Eb6", "●●●|●◐○○");
        fingerings.put("E6", "●●●|●○○○");
        fingerings.put("F6", "●●●|◐○○○");
        fingerings.put("F#6", "●●●|○○○○");
        fingerings.put("Gb6", "●●●|○○○○");
        fingerings.put("G6", "●●○|○○○○");
        fingerings.put("G#6", "●◐○|○○○○");
        fingerings.put("Ab6", "●◐○|○○○○");
        fingerings.put("A6", "●○○|○○○○");
        FINGERINGS = Collections.unmodifiableMap(fingerings);
    }

    public String fromFrequency(float hz) {
        String closest = "";
        float best = Float.MAX_VALUE;
        for (Map.Entry<String, Float> pair : NOTE_FREQUENCIES.entrySet()) {
            float f = pair.getValue();
            float diff = Math.abs(f - hz);
            if (diff < best) {
                best = diff;
                closest = pair.getKey();
            }
        }
        return closest;
    }

    public float frequencyFor(String note) {
        String normalized = MusicNotation.normalizeNoteKey(note);
        if (normalized == null) {
            Log.w(TAG, "frequencyFor: unparseable note key='" + note + "'");
            return 0f;
        }
        Float frequency = NOTE_FREQUENCIES.get(normalized);
        return frequency == null ? 0f : frequency;
    }

    public String fingeringFor(String note) {
        String normalized = MusicNotation.normalizeNoteKey(note);
        if (normalized == null) {
            Log.w(TAG, "fingeringFor: unparseable note key='" + note + "'");
            return "нет данных";
        }
        if (FINGERINGS.containsKey(normalized)) {
            return FINGERINGS.get(normalized);
        }
        String fallback = nearestFingering(normalized);
        if (fallback == null) {
            Log.w(TAG, "fingeringFor: no exact/fallback fingering for note='" + note + "' normalized='" + normalized + "'");
            return "нет данных";
        }
        Log.w(TAG, "fingeringFor: using nearest fingering for note='" + note + "' normalized='" + normalized + "'");
        return fallback;
    }

    private String nearestFingering(String note) {
        MusicNotation.ParsedNote parsed = MusicNotation.parseNormalizedNoteKey(note);
        if (parsed == null) {
            return null;
        }

        int targetMidi = MusicNotation.midiFor(parsed.noteName, parsed.octave);
        String closestKey = null;
        int minDistance = Integer.MAX_VALUE;
        for (String key : FINGERINGS.keySet()) {
            if (key.length() < 2) continue;
            try {
                MusicNotation.ParsedNote keyParsed = MusicNotation.parseNormalizedNoteKey(key);
                if (keyParsed == null) continue;
                int keyMidi = MusicNotation.midiFor(keyParsed.noteName, keyParsed.octave);
                int distance = Math.abs(keyMidi - targetMidi);
                if (distance < minDistance) {
                    minDistance = distance;
                    closestKey = key;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return closestKey == null ? null : FINGERINGS.get(closestKey);
    }
}
