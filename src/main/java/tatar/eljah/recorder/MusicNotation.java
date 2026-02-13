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
        String base;
        if ("C".equals(noteName)) {
            base = "Do";
        } else if ("D".equals(noteName)) {
            base = "Re";
        } else if ("E".equals(noteName)) {
            base = "Mi";
        } else if ("F".equals(noteName)) {
            base = "Fa";
        } else if ("G".equals(noteName)) {
            base = "Sol";
        } else if ("A".equals(noteName)) {
            base = "La";
        } else if ("B".equals(noteName) || "H".equals(noteName)) {
            base = "Si";
        } else {
            base = noteName;
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
