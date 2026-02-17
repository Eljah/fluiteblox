package tatar.eljah.recorder;

import java.util.HashMap;
import java.util.Map;

public final class MusicNotation {
    private static final Map<String, Integer> SEMITONES = new HashMap<String, Integer>();

    static {
        SEMITONES.put("C", 0);
        SEMITONES.put("C#", 1);
        SEMITONES.put("Db", 1);
        SEMITONES.put("D", 2);
        SEMITONES.put("D#", 3);
        SEMITONES.put("Eb", 3);
        SEMITONES.put("E", 4);
        SEMITONES.put("F", 5);
        SEMITONES.put("F#", 6);
        SEMITONES.put("Gb", 6);
        SEMITONES.put("G", 7);
        SEMITONES.put("G#", 8);
        SEMITONES.put("Ab", 8);
        SEMITONES.put("A", 9);
        SEMITONES.put("A#", 10);
        SEMITONES.put("Bb", 10);
        SEMITONES.put("B", 11);
        SEMITONES.put("H", 11);
    }

    private MusicNotation() {
    }

    public static String toEuropeanLabel(String noteName, int octave) {
        if (noteName == null || noteName.length() == 0) {
            return "?";
        }

        String baseName = noteName.substring(0, 1);
        String accidental = noteName.length() > 1 ? noteName.substring(1) : "";

        String base;
        if ("C".equals(baseName)) {
            base = "До";
        } else if ("D".equals(baseName)) {
            base = "Ре";
        } else if ("E".equals(baseName)) {
            base = "Ми";
        } else if ("F".equals(baseName)) {
            base = "Фа";
        } else if ("G".equals(baseName)) {
            base = "Соль";
        } else if ("A".equals(baseName)) {
            base = "Ля";
        } else if ("B".equals(baseName) || "H".equals(baseName)) {
            base = "Си";
        } else {
            base = noteName;
        }

        if ("#".equals(accidental)) {
            return base + "♯" + octave;
        }
        if ("b".equals(accidental)) {
            return base + "♭" + octave;
        }
        return base + octave;
    }

    public static int midiFor(String noteName, int octave) {
        Integer semitone = SEMITONES.get(noteName);
        if (semitone == null) {
            semitone = 0;
        }
        return (octave + 1) * 12 + semitone;
    }
}
