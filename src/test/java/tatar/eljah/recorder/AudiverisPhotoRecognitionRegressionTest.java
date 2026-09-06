package tatar.eljah.recorder;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class AudiverisPhotoRecognitionRegressionTest {
    public static void main(String[] args) throws Exception {
        File photo = new File("photo_2026-02-13_14-27-38.jpg");
        if (!photo.exists()) {
            throw new AssertionError("Missing " + photo.getAbsolutePath());
        }

        BufferedImage image = ImageIO.read(photo);
        if (image == null) {
            throw new AssertionError("Unable to decode " + photo.getAbsolutePath());
        }

        int width = image.getWidth();
        int height = image.getHeight();
        int[] argb = image.getRGB(0, 0, width, height, null, 0, width);

        AudiverisCompatRecognitionEngine engine = new AudiverisCompatRecognitionEngine(null);
        AudiverisCompatRecognitionEngine.DirectRecognition direct = engine.recognizeDirectForTest(width, height, argb);
        List<NoteEvent> recognized = direct.notes;
        List<NoteEvent> expected = ReferenceComposition.expectedReferenceNotes();
        OpenCvScoreProcessor.ProcessingResult production = engine.recognizeArgbForTest(
                width, height, argb, "Audiveris regression", OpenCvScoreProcessor.ProcessingOptions.defaults());

        List<Integer> expectedMidi = toMidi(expected);
        List<Integer> actualMidi = toMidi(recognized);
        int lcs = lcsLength(expectedMidi, actualMidi);

        System.out.println("AudiverisCompat regression: expected=" + expected.size()
                + ", recognized=" + recognized.size()
                + ", lcs=" + lcs
                + ", coverage=" + percent(lcs, expected.size()) + "%"
                + ", precision=" + percent(lcs, recognized.size()) + "%"
                + ", expected20=" + firstNames(expected, 20)
                + ", first20=" + firstNames(recognized, 20));
        System.out.println("  shiftedLcs=" + shiftedLcs(expected, recognized));
        System.out.println("  diatonicShiftedLcs=" + diatonicShiftedLcs(expected, recognized));
        System.out.println("  leftCutSweep=" + leftCutSweep(expected, recognized));
        System.out.println("  firstStaffCutSweep=" + firstStaffCutSweep(expected, recognized, direct.staves));
        System.out.println("  productionMode=" + production.processingMode
                + ", productionNotes=" + production.piece.notes.size()
                + ", durations=" + durationSummary(production.piece.notes));
        System.out.println("  suppressedOverlays=" + direct.suppressedOverlays);
        System.out.println("  firstDetails=" + firstDetails(recognized, 20));
        System.out.println("  peaks=" + direct.linePeaks);
        for (int i = 0; i < direct.staves.size(); i++) {
            AudiverisCompatRecognitionEngine.StaffModel staff = direct.staves.get(i);
            System.out.println("  staff[" + i + "] y=" + Math.round(staff.top) + ".." + Math.round(staff.bottom)
                    + ", spacing=" + staff.spacing
                    + ", x=" + Math.round(staff.left) + ".." + Math.round(staff.right)
                    + ", notes=" + countNotes(recognized, staff)
                    + ", names=" + namesForStaff(recognized, staff));
        }
        writeDebugOverlay(image, direct, new File("target/audiveris-debug.png"));
    }

    private static List<Integer> toMidi(List<NoteEvent> notes) {
        List<Integer> midi = new ArrayList<Integer>();
        for (NoteEvent note : notes) {
            midi.add(MusicNotation.midiFor(note.noteName, note.octave));
        }
        return midi;
    }

    private static String firstNames(List<NoteEvent> notes, int limit) {
        StringBuilder out = new StringBuilder();
        int n = Math.min(limit, notes.size());
        for (int i = 0; i < n; i++) {
            if (i > 0) out.append(' ');
            out.append(notes.get(i).fullName());
        }
        return out.toString();
    }

    private static String firstDetails(List<NoteEvent> notes, int limit) {
        StringBuilder out = new StringBuilder();
        int n = Math.min(limit, notes.size());
        for (int i = 0; i < n; i++) {
            NoteEvent note = notes.get(i);
            if (i > 0) out.append(" | ");
            out.append(note.fullName())
                    .append('@')
                    .append(Math.round(note.x))
                    .append(',')
                    .append(Math.round(note.y));
        }
        return out.toString();
    }

    private static int percent(int num, int den) {
        if (den <= 0) return 0;
        return Math.round((100f * num) / (float) den);
    }

    private static int lcsLength(List<Integer> a, List<Integer> b) {
        int[] dp = new int[b.size() + 1];
        for (Integer x : a) {
            int prev = 0;
            for (int j = 1; j <= b.size(); j++) {
                int old = dp[j];
                if (x.equals(b.get(j - 1))) {
                    dp[j] = prev + 1;
                } else if (dp[j - 1] > dp[j]) {
                    dp[j] = dp[j - 1];
                }
                prev = old;
            }
        }
        return dp[b.size()];
    }

    private static String shiftedLcs(List<NoteEvent> expected, List<NoteEvent> actual) {
        List<Integer> expectedMidi = toMidi(expected);
        StringBuilder out = new StringBuilder();
        for (int semitones = -12; semitones <= 12; semitones++) {
            List<Integer> shifted = new ArrayList<Integer>();
            for (NoteEvent note : actual) {
                shifted.add(MusicNotation.midiFor(note.noteName, note.octave) + semitones);
            }
            int lcs = lcsLength(expectedMidi, shifted);
            if (lcs >= 22) {
                if (out.length() > 0) out.append(", ");
                out.append(semitones).append(':').append(lcs);
            }
        }
        return out.toString();
    }

    private static String diatonicShiftedLcs(List<NoteEvent> expected, List<NoteEvent> actual) {
        List<Integer> expectedMidi = toMidi(expected);
        StringBuilder out = new StringBuilder();
        for (int steps = -4; steps <= 4; steps++) {
            List<Integer> shifted = new ArrayList<Integer>();
            for (NoteEvent note : actual) {
                NoteEvent shiftedNote = shiftDiatonic(note.noteName, note.octave, steps);
                shifted.add(MusicNotation.midiFor(shiftedNote.noteName, shiftedNote.octave));
            }
            int lcs = lcsLength(expectedMidi, shifted);
            if (lcs >= 22) {
                if (out.length() > 0) out.append(", ");
                out.append(steps).append(':').append(lcs);
            }
        }
        return out.toString();
    }

    private static String leftCutSweep(List<NoteEvent> expected, List<NoteEvent> actual) {
        List<Integer> expectedMidi = toMidi(expected);
        StringBuilder out = new StringBuilder();
        for (int minX = 0; minX <= 180; minX += 20) {
            List<Integer> filtered = new ArrayList<Integer>();
            int kept = 0;
            for (NoteEvent note : actual) {
                if (note.x >= minX) {
                    filtered.add(MusicNotation.midiFor(note.noteName, note.octave));
                    kept++;
                }
            }
            int lcs = lcsLength(expectedMidi, filtered);
            if (out.length() > 0) out.append(", ");
            out.append(minX).append('=').append(kept).append('/').append(lcs);
        }
        return out.toString();
    }

    private static String firstStaffCutSweep(List<NoteEvent> expected,
                                             List<NoteEvent> actual,
                                             List<AudiverisCompatRecognitionEngine.StaffModel> staves) {
        if (staves.isEmpty()) {
            return "";
        }
        AudiverisCompatRecognitionEngine.StaffModel first = staves.get(0);
        List<Integer> expectedMidi = toMidi(expected);
        StringBuilder out = new StringBuilder();
        for (int minX = 0; minX <= 700; minX += 50) {
            List<Integer> filtered = new ArrayList<Integer>();
            int kept = 0;
            for (NoteEvent note : actual) {
                if (note.y >= first.top - first.spacing * 1.25f
                        && note.y <= first.bottom + first.spacing * 1.25f
                        && note.x < minX) {
                    continue;
                }
                filtered.add(MusicNotation.midiFor(note.noteName, note.octave));
                kept++;
            }
            int lcs = lcsLength(expectedMidi, filtered);
            if (out.length() > 0) out.append(", ");
            out.append(minX).append('=').append(kept).append('/').append(lcs);
        }
        return out.toString();
    }

    private static String durationSummary(List<NoteEvent> notes) {
        int eighth = 0;
        int quarter = 0;
        int half = 0;
        int other = 0;
        for (NoteEvent note : notes) {
            if ("eighth".equals(note.duration)) {
                eighth++;
            } else if ("quarter".equals(note.duration)) {
                quarter++;
            } else if ("half".equals(note.duration)) {
                half++;
            } else {
                other++;
            }
        }
        return "eighth=" + eighth + ",quarter=" + quarter + ",half=" + half + ",other=" + other;
    }

    private static NoteEvent shiftDiatonic(String noteName, int octave, int steps) {
        String[] names = {"C", "D", "E", "F", "G", "A", "B"};
        String base = noteName == null || noteName.length() == 0 ? "C" : noteName.substring(0, 1);
        int idx = 0;
        for (int i = 0; i < names.length; i++) {
            if (names[i].equals(base)) {
                idx = i;
                break;
            }
        }
        int absolute = idx + steps;
        while (absolute < 0) {
            absolute += 7;
            octave--;
        }
        while (absolute >= 7) {
            absolute -= 7;
            octave++;
        }
        return new NoteEvent(names[absolute], octave, "quarter", 1);
    }

    private static int countNotes(List<NoteEvent> notes, AudiverisCompatRecognitionEngine.StaffModel staff) {
        int count = 0;
        for (NoteEvent note : notes) {
            if (note.y >= staff.top - staff.spacing * 1.25f && note.y <= staff.bottom + staff.spacing * 1.25f) {
                count++;
            }
        }
        return count;
    }

    private static String namesForStaff(List<NoteEvent> notes, AudiverisCompatRecognitionEngine.StaffModel staff) {
        StringBuilder out = new StringBuilder();
        for (NoteEvent note : notes) {
            if (note.y >= staff.top - staff.spacing * 1.25f && note.y <= staff.bottom + staff.spacing * 1.25f) {
                if (out.length() > 0) out.append(' ');
                out.append(note.fullName());
            }
        }
        return out.toString();
    }

    private static void writeDebugOverlay(BufferedImage source,
                                          AudiverisCompatRecognitionEngine.DirectRecognition direct,
                                          File out) throws Exception {
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        g.drawImage(source, 0, 0, null);
        g.setStroke(new BasicStroke(2f));
        g.setColor(new Color(0, 220, 0, 180));
        for (AudiverisCompatRecognitionEngine.StaffModel staff : direct.staves) {
            for (int i = 0; i < 5; i++) {
                int y = Math.round(staff.top + i * staff.spacing);
                g.drawLine(Math.round(staff.left), y, Math.round(staff.right), y);
            }
        }
        g.setColor(new Color(255, 0, 0, 220));
        int index = 1;
        for (NoteEvent note : direct.notes) {
            int r = 7;
            int x = Math.round(note.x);
            int y = Math.round(note.y);
            g.drawOval(x - r, y - r, r * 2, r * 2);
            g.drawString(index + ":" + note.fullName(), x + r + 2, y - r - 2);
            index++;
        }
        g.dispose();
        ImageIO.write(copy, "png", out);
    }
}
