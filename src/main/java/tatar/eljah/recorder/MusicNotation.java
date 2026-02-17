package tatar.eljah.recorder;

import android.content.Context;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MusicNotation {
    private static final Map<String, Integer> SEMITONES = new HashMap<String, Integer>();
    private static final Pattern NOTE_KEY_PATTERN = Pattern.compile("^\\s*([A-Ga-gh])\\s*([#♯b♭]?)\\s*(-?\\d{1,2})\\s*$");

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



    public static String normalizeNoteKey(String raw) {
        if (raw == null) {
            return null;
        }
        Matcher m = NOTE_KEY_PATTERN.matcher(raw);
        if (!m.matches()) {
            return null;
        }
        String base = m.group(1).toUpperCase(Locale.US);
        String accidental = m.group(2);
        String octavePart = m.group(3);

        if ("H".equals(base)) {
            base = "B";
        }
        if ("♯".equals(accidental)) {
            accidental = "#";
        } else if ("♭".equals(accidental)) {
            accidental = "b";
        }

        int octave;
        try {
            octave = Integer.parseInt(octavePart);
        } catch (NumberFormatException ex) {
            return null;
        }
        return base + accidental + octave;
    }

    public static ParsedNote parseNormalizedNoteKey(String normalizedNoteKey) {
        if (normalizedNoteKey == null || normalizedNoteKey.length() < 2) {
            return null;
        }
        int split = normalizedNoteKey.length() - 1;
        while (split > 0 && Character.isDigit(normalizedNoteKey.charAt(split - 1))) {
            split--;
        }
        if (split <= 0 || split >= normalizedNoteKey.length()) {
            return null;
        }

        String noteName = normalizedNoteKey.substring(0, split);
        String octavePart = normalizedNoteKey.substring(split);
        int octave;
        try {
            octave = Integer.parseInt(octavePart);
        } catch (NumberFormatException ex) {
            return null;
        }
        return new ParsedNote(noteName, octave);
    }

    public static String toEuropeanLabel(String noteName, int octave) {
        if (noteName == null || noteName.length() == 0) {
            return "?";
        }
        String lang = Locale.getDefault().getLanguage();
        return formatNoteLabel(lang, noteName, octave);
    }

    public static String toLocalizedLabel(Context context, String noteName, int octave) {
        if (noteName == null || noteName.length() == 0) {
            return "?";
        }

        String lang = AppLocaleManager.savedLanguage(context);
        if (lang == null || lang.length() == 0) {
            lang = Locale.getDefault().getLanguage();
        }

        return formatNoteLabel(lang, noteName, octave);
    }


    private static String formatNoteLabel(String lang, String noteName, int octave) {
        String baseName = noteName.substring(0, 1).toUpperCase(Locale.US);
        String accidental = noteName.length() > 1 ? noteName.substring(1) : "";
        String base = baseLabelForLanguage(lang, baseName, accidental);

        if ("#".equals(accidental)) {
            return base + "♯" + octave;
        }
        if ("b".equals(accidental)) {
            return base + "♭" + octave;
        }
        return base + octave;
    }

    private static String baseLabelForLanguage(String lang, String baseName, String accidental) {
        if ("en".equals(lang)) {
            return englishBase(baseName);
        }
        if ("de".equals(lang)) {
            return germanBase(baseName, accidental);
        }
        if ("ja".equals(lang)) {
            return japaneseBase(baseName);
        }
        if ("zh".equals(lang)) {
            return chineseBase(baseName);
        }
        if ("ar".equals(lang)) {
            return arabicBase(baseName);
        }
        if ("ru".equals(lang) || "tt".equals(lang)) {
            return cyrillicSolfegeBase(baseName);
        }
        return latinSolfegeBase(baseName);
    }

    private static String englishBase(String baseName) {
        if ("H".equals(baseName)) {
            return "B";
        }
        return baseName;
    }

    private static String germanBase(String baseName, String accidental) {
        if ("B".equals(baseName) && "b".equals(accidental)) {
            return "B";
        }
        if ("B".equals(baseName) || "H".equals(baseName)) {
            return "H";
        }
        return baseName;
    }

    private static String latinSolfegeBase(String baseName) {
        if ("C".equals(baseName)) return "Do";
        if ("D".equals(baseName)) return "Re";
        if ("E".equals(baseName)) return "Mi";
        if ("F".equals(baseName)) return "Fa";
        if ("G".equals(baseName)) return "Sol";
        if ("A".equals(baseName)) return "La";
        return "Si";
    }

    private static String cyrillicSolfegeBase(String baseName) {
        if ("C".equals(baseName)) return "До";
        if ("D".equals(baseName)) return "Ре";
        if ("E".equals(baseName)) return "Ми";
        if ("F".equals(baseName)) return "Фа";
        if ("G".equals(baseName)) return "Соль";
        if ("A".equals(baseName)) return "Ля";
        return "Си";
    }

    private static String japaneseBase(String baseName) {
        if ("C".equals(baseName)) return "ド";
        if ("D".equals(baseName)) return "レ";
        if ("E".equals(baseName)) return "ミ";
        if ("F".equals(baseName)) return "ファ";
        if ("G".equals(baseName)) return "ソ";
        if ("A".equals(baseName)) return "ラ";
        return "シ";
    }

    private static String chineseBase(String baseName) {
        if ("C".equals(baseName)) return "多";
        if ("D".equals(baseName)) return "来";
        if ("E".equals(baseName)) return "米";
        if ("F".equals(baseName)) return "发";
        if ("G".equals(baseName)) return "索";
        if ("A".equals(baseName)) return "拉";
        return "西";
    }

    private static String arabicBase(String baseName) {
        if ("C".equals(baseName)) return "دو";
        if ("D".equals(baseName)) return "ري";
        if ("E".equals(baseName)) return "مي";
        if ("F".equals(baseName)) return "فا";
        if ("G".equals(baseName)) return "صول";
        if ("A".equals(baseName)) return "لا";
        return "سي";
    }

    static final class ParsedNote {
        final String noteName;
        final int octave;

        ParsedNote(String noteName, int octave) {
            this.noteName = noteName;
            this.octave = octave;
        }
    }

    public static int midiFor(String noteName, int octave) {
        Integer semitone = SEMITONES.get(noteName);
        if (semitone == null) {
            semitone = 0;
        }
        return (octave + 1) * 12 + semitone;
    }
}
