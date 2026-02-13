package tatar.eljah.recorder;

import java.util.HashMap;
import java.util.Map;

public class RecorderNoteMapper {
    private static final String[][] NOTE_RANGE = {
            {"D4", "293.66"}, {"E4", "329.63"}, {"F4", "349.23"}, {"G4", "392.00"},
            {"A4", "440.00"}, {"Bb4", "466.16"}, {"B4", "493.88"}, {"C5", "523.25"},
            {"D5", "587.33"}, {"E5", "659.25"}, {"F5", "698.46"}, {"G5", "783.99"},
            {"A5", "880.00"}, {"Bb5", "932.33"}, {"B5", "987.77"}, {"C6", "1046.50"},
            {"D6", "1174.66"}, {"E6", "1318.51"}, {"F6", "1396.91"}, {"G6", "1567.98"},
            {"A6", "1760.00"}, {"Bb6", "1864.66"}, {"B6", "1975.53"}, {"C7", "2093.00"},
            {"D7", "2349.32"}
    };

    private static final Map<String, String> FINGERINGS = new HashMap<String, String>();

    static {
        FINGERINGS.put("D4", "●●●|●●○○");
        FINGERINGS.put("E4", "●●●|●○○○");
        FINGERINGS.put("F4", "●●●|○○○○");
        FINGERINGS.put("G4", "●●○|○○○○");
        FINGERINGS.put("A4", "●○○|○○○○");
        FINGERINGS.put("Bb4", "○○○|○○○○");
        FINGERINGS.put("B4", "○○○|○○○○");
        FINGERINGS.put("C5", "●●●|●●●○");
        FINGERINGS.put("D5", "●●●|●●○○");
        FINGERINGS.put("E5", "●●●|●○○○");
        FINGERINGS.put("F5", "●●●|○○○○");
        FINGERINGS.put("G5", "●●○|○○○○");
        FINGERINGS.put("A5", "●○○|○○○○");
        FINGERINGS.put("Bb5", "○○○|○○○○");
        FINGERINGS.put("B5", "○○○|○○○○");
        FINGERINGS.put("C6", "●●●|●●●○");
        FINGERINGS.put("D6", "●●●|●●○○");
        FINGERINGS.put("E6", "●●●|●○○○");
        FINGERINGS.put("F6", "●●●|○○○○");
        FINGERINGS.put("G6", "●●○|○○○○");
        FINGERINGS.put("A6", "●○○|○○○○");
        FINGERINGS.put("Bb6", "○○○|○○○○");
        FINGERINGS.put("B6", "○○○|○○○○");
        FINGERINGS.put("C7", "●●●|●●●○");
        FINGERINGS.put("D7", "●●●|●●○○");
    }

    public String fromFrequency(float hz) {
        String closest = "";
        float best = Float.MAX_VALUE;
        for (String[] pair : NOTE_RANGE) {
            float f = Float.parseFloat(pair[1]);
            float diff = Math.abs(f - hz);
            if (diff < best) {
                best = diff;
                closest = pair[0];
            }
        }
        return closest;
    }


    public float frequencyFor(String note) {
        for (String[] pair : NOTE_RANGE) {
            if (pair[0].equals(note)) {
                return Float.parseFloat(pair[1]);
            }
        }
        return 0f;
    }

    public String fingeringFor(String note) {
        if (FINGERINGS.containsKey(note)) {
            return FINGERINGS.get(note);
        }
        return "нет данных";
    }
}
