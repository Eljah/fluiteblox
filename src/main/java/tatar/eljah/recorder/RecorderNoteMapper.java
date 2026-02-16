package tatar.eljah.recorder;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class RecorderNoteMapper {
    private static final Map<String, Float> NOTE_FREQUENCIES;
    private static final Map<String, String> FINGERINGS;

    static {
        Map<String, Float> frequencies = new LinkedHashMap<String, Float>();
        // Alto recorder in G, equal temperament A4 = 440Hz.
        // Supported range: D4 (293.66Hz) .. A6 (1760Hz).
        frequencies.put("D4", 293.66f);
        frequencies.put("E4", 329.63f);
        frequencies.put("F4", 349.23f);
        frequencies.put("G4", 392.00f);
        frequencies.put("A4", 440.00f);
        frequencies.put("Bb4", 466.16f);
        frequencies.put("B4", 493.88f);
        frequencies.put("C5", 523.25f);
        frequencies.put("D5", 587.33f);
        frequencies.put("E5", 659.25f);
        frequencies.put("F5", 698.46f);
        frequencies.put("F#5", 739.99f);
        frequencies.put("G5", 783.99f);
        frequencies.put("A5", 880.00f);
        frequencies.put("B5", 987.77f);
        frequencies.put("C6", 1046.50f);
        frequencies.put("D6", 1174.66f);
        frequencies.put("E6", 1318.51f);
        frequencies.put("F#6", 1479.98f);
        frequencies.put("G6", 1567.98f);
        frequencies.put("A6", 1760.00f);
        NOTE_FREQUENCIES = Collections.unmodifiableMap(frequencies);

        Map<String, String> fingerings = new LinkedHashMap<String, String>();
        fingerings.put("D4", "нет данных");
        fingerings.put("E4", "нет данных");
        fingerings.put("F4", "нет данных");
        fingerings.put("G4", "●●○|○○○○");
        fingerings.put("A4", "●○○|○○○○");
        fingerings.put("Bb4", "нет данных");
        fingerings.put("B4", "○○○|○○○○");
        fingerings.put("C5", "●●●|●●●○");
        fingerings.put("D5", "●●●|●●○○");
        fingerings.put("E5", "●●●|●○○○");
        fingerings.put("F5", "нет данных");
        fingerings.put("F#5", "●●●|○○○○");
        fingerings.put("G5", "●●○|○○○○");
        fingerings.put("A5", "●○○|○○○○");
        fingerings.put("B5", "○○○|○○○○");
        fingerings.put("C6", "●●●|●●●○");
        fingerings.put("D6", "●●●|●●○○");
        fingerings.put("E6", "●●●|●○○○");
        fingerings.put("F#6", "●●●|○○○○");
        fingerings.put("G6", "●●○|○○○○");
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
        Float frequency = NOTE_FREQUENCIES.get(note);
        return frequency == null ? 0f : frequency;
    }

    public String fingeringFor(String note) {
        if (FINGERINGS.containsKey(note)) {
            return FINGERINGS.get(note);
        }
        return "нет данных";
    }
}
