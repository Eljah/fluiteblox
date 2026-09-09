package tatar.eljah.recorder;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.lang.reflect.Method;

public class PreloadedScoreLibraryTest {
    private static final Set<String> VALID_DURATIONS = new HashSet<String>();

    static {
        VALID_DURATIONS.add("whole");
        VALID_DURATIONS.add("half");
        VALID_DURATIONS.add("quarter");
        VALID_DURATIONS.add("eighth");
        VALID_DURATIONS.add("16th");
    }

    public static void main(String[] args) {
        new PreloadedScoreLibraryTest().firstWavePiecesShouldBePlayableAndExportable();
    }

    public void firstWavePiecesShouldBePlayableAndExportable() {
        Method buildMusicXml = privateExportMethod("buildMusicXml", List.class);
        Method buildMidi = privateExportMethod("buildMidi", List.class);
        List<ScorePiece> pieces = PreloadedScoreLibrary.buildPieces();
        if (pieces.size() != 66) {
            throw new AssertionError("Expected 66 preloaded pieces, got " + pieces.size());
        }

        Set<String> ids = new HashSet<String>();
        int minMidi = MusicNotation.midiFor("D", 4);
        int maxMidi = MusicNotation.midiFor("D", 6);
        for (ScorePiece piece : pieces) {
            if (piece.id == null || piece.id.length() == 0) {
                throw new AssertionError("Preloaded piece has empty id: " + piece.title);
            }
            if (!ids.add(piece.id)) {
                throw new AssertionError("Duplicate preloaded piece id: " + piece.id);
            }
            if (piece.title == null || piece.title.length() == 0) {
                throw new AssertionError("Preloaded piece has empty title: " + piece.id);
            }
            if (piece.notes == null || piece.notes.isEmpty()) {
                throw new AssertionError("Preloaded piece has no notes: " + piece.id);
            }
            if (piece.notes.size() > 64) {
                throw new AssertionError("Preloaded piece is too long for first-wave library: "
                        + piece.id + " has " + piece.notes.size() + " notes");
            }

            for (NoteEvent note : piece.notes) {
                if (!VALID_DURATIONS.contains(note.duration)) {
                    throw new AssertionError("Invalid duration in " + piece.id + ": " + note.duration);
                }
                int midi = MusicNotation.midiFor(note.noteName, note.octave);
                if (midi < minMidi || midi > maxMidi) {
                    throw new AssertionError("Note outside beginner recorder range in "
                            + piece.id + ": " + note.noteName + note.octave);
                }
            }

            String musicXml = (String) invoke(buildMusicXml, piece.notes);
            if (musicXml.indexOf("<score-partwise") < 0) {
                throw new AssertionError("MusicXML export is missing score-partwise root for " + piece.id);
            }
            byte[] midi = (byte[]) invoke(buildMidi, piece.notes);
            if (midi == null || midi.length == 0) {
                throw new AssertionError("MIDI export is empty for " + piece.id);
            }
        }
    }

    private static Method privateExportMethod(String name, Class<?> parameterType) {
        try {
            Method method = ScoreExportUtil.class.getDeclaredMethod(name, parameterType);
            method.setAccessible(true);
            return method;
        } catch (Exception ex) {
            throw new AssertionError("Cannot access ScoreExportUtil." + name, ex);
        }
    }

    private static Object invoke(Method method, Object argument) {
        try {
            return method.invoke(null, argument);
        } catch (Exception ex) {
            throw new AssertionError("Export helper failed: " + method.getName(), ex);
        }
    }
}
