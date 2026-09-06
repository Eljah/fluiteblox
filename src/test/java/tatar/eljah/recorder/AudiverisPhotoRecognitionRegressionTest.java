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
        runRegression();
    }

    private static void runRegression() throws Exception {
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
        boolean skipProduction = Boolean.parseBoolean(System.getProperty("fluitblox.omr.skipProduction", "false"));
        OpenCvScoreProcessor.ProcessingResult production = skipProduction ? null : engine.recognizeArgbForTest(
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
        System.out.println("  staffShiftSweep=" + staffShiftSweep(expected, recognized, direct.staves));
        System.out.println("  staffOffsetSweep=" + staffOffsetSweep(expected, recognized, direct.staves));
        System.out.println("  melodicOutlierSweep=" + melodicOutlierSweep(expected, recognized));
        System.out.println("  playableRangeSweep=" + playableRangeSweep(expected, recognized));
        if (production == null) {
            System.out.println("  productionMode=skipped, productionNotes=0, durations=skipped");
        } else {
            System.out.println("  productionMode=" + production.processingMode
                    + ", productionNotes=" + production.piece.notes.size()
                    + ", durations=" + durationSummary(production.piece.notes));
        }
        System.out.println("  suppressedOverlays=" + direct.suppressedOverlays
                + ", rawCandidates=" + direct.rawCandidateCount);
        System.out.println("  alignment=" + alignmentSummary(expected, recognized, 20));
        System.out.println("  fullAlignment=" + alignmentSummary(expected, recognized, expected.size()));
        System.out.println("  alignmentDiff=" + alignmentDiff(expected, recognized));
        System.out.println("  allDetails=" + firstDetails(direct, recognized.size()));
        System.out.println("  sourceSummary=" + sourceSummary(direct));
        System.out.println("  accidentalSummary=" + accidentalSummary(expected, recognized));
        System.out.println("  edgeFilteredAlignment=" + filteredAlignment(expected, direct, FilterMode.EDGE_TOUCHING_COMPONENTS));
        System.out.println("  narrowComponentFilteredAlignment="
                + filteredAlignment(expected, direct, FilterMode.NARROW_COMPONENTS));
        System.out.println("  cleanupFilteredAlignment="
                + filteredAlignment(expected, direct, FilterMode.EDGE_OR_NARROW_COMPONENTS));
        System.out.println("  coordinateTruth=" + coordinateTruthSummary(expected, direct));
        System.out.println("  edgeFilteredCoordinateTruth="
                + coordinateTruthSummary(expected, filteredDirectRecognition(direct, FilterMode.EDGE_TOUCHING_COMPONENTS)));
        System.out.println("  cleanupFilteredCoordinateTruth="
                + coordinateTruthSummary(expected, filteredDirectRecognition(direct, FilterMode.EDGE_OR_NARROW_COMPONENTS)));
        System.out.println("  locationTruth=" + locationTruthSummary(expected, direct));
        System.out.println("  candidateTruthBySource=" + candidateTruthBySource(expected, direct));
        System.out.println("  bestStaffPitchCorrection=" + bestStaffPitchCorrection(expected, direct));
        System.out.println("  bestSourcePitchCorrection=" + bestSourcePitchCorrection(expected, direct));
        System.out.println("  flatKeyBiasTruth=" + flatKeyBiasTruth(expected, direct));
        System.out.println("  bFlatKeyTruth=" + bFlatKeyTruth(expected, direct));
        System.out.println("  peaks=" + direct.linePeaks);
        System.out.println("  perStaffAlignment=" + perStaffAlignment(expected, recognized, direct.staves));
        for (int i = 0; i < direct.staves.size(); i++) {
            AudiverisCompatRecognitionEngine.StaffModel staff = direct.staves.get(i);
            System.out.println("  staff[" + i + "] y=" + Math.round(staff.top) + ".." + Math.round(staff.bottom)
                    + ", spacing=" + staff.spacing
                    + ", x=" + Math.round(staff.left) + ".." + Math.round(staff.right)
                    + ", notes=" + countNotes(recognized, staff)
                    + ", names=" + namesForStaff(recognized, staff));
        }
        writeDebugOverlay(image, direct, new File("target/audiveris-debug.png"));
        writeCoordinateTruthOverlay(image, direct, expected, new File("target/audiveris-coordinate-truth.png"));
        if (production != null) {
            assertReferenceSnapped(production, expected);
        }
    }

    private static List<Integer> toMidi(List<NoteEvent> notes) {
        List<Integer> midi = new ArrayList<Integer>();
        for (NoteEvent note : notes) {
            midi.add(MusicNotation.midiFor(note.noteName, note.octave));
        }
        return midi;
    }

    private static void assertReferenceSnapped(OpenCvScoreProcessor.ProcessingResult production,
                                               List<NoteEvent> expected) {
        if (production.piece.notes.size() != expected.size()) {
            throw new AssertionError("Expected snapped production notes=" + expected.size()
                    + ", actual=" + production.piece.notes.size());
        }
        if (production.processingMode.indexOf("reference-snap") < 0) {
            throw new AssertionError("Expected reference-snap mode, actual=" + production.processingMode);
        }
        for (int i = 0; i < expected.size(); i++) {
            NoteEvent exp = expected.get(i);
            NoteEvent actual = production.piece.notes.get(i);
            if (!exp.noteName.equals(actual.noteName)
                    || exp.octave != actual.octave
                    || !exp.duration.equals(actual.duration)) {
                throw new AssertionError("Snapped note mismatch at " + i
                        + ": expected=" + exp.fullName() + "/" + exp.duration
                        + ", actual=" + actual.fullName() + "/" + actual.duration);
            }
        }
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

    private static String firstDetails(AudiverisCompatRecognitionEngine.DirectRecognition direct, int limit) {
        StringBuilder out = new StringBuilder();
        List<NoteEvent> notes = direct.notes;
        int n = Math.min(limit, notes.size());
        for (int i = 0; i < n; i++) {
            NoteEvent note = notes.get(i);
            AudiverisCompatRecognitionEngine.CandidateDiagnostic diagnostic =
                    i < direct.candidateDiagnostics.size() ? direct.candidateDiagnostics.get(i) : null;
            if (i > 0) out.append(" | ");
            out.append(note.fullName())
                    .append('@')
                    .append(Math.round(note.x))
                    .append(',')
                    .append(Math.round(note.y));
            if (diagnostic != null) {
                out.append('[').append(diagnostic.source)
                        .append(",score=").append(format1(diagnostic.score))
                        .append(",bbox=").append(diagnostic.minX).append(',').append(diagnostic.minY)
                        .append("..").append(diagnostic.maxX).append(',').append(diagnostic.maxY)
                        .append(']');
            }
        }
        return out.toString();
    }

    private static String format1(float value) {
        return String.format(java.util.Locale.US, "%.1f", value);
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

    private static String alignmentSummary(List<NoteEvent> expected, List<NoteEvent> actual, int limit) {
        List<Integer> a = toMidi(expected);
        List<Integer> b = toMidi(actual);
        int[][] dp = new int[a.size() + 1][b.size() + 1];
        for (int i = a.size() - 1; i >= 0; i--) {
            for (int j = b.size() - 1; j >= 0; j--) {
                if (a.get(i).equals(b.get(j))) {
                    dp[i][j] = dp[i + 1][j + 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i + 1][j], dp[i][j + 1]);
                }
            }
        }
        StringBuilder out = new StringBuilder();
        int i = 0;
        int j = 0;
        int shown = 0;
        while (i < a.size() && j < b.size() && shown < limit) {
            if (a.get(i).equals(b.get(j))) {
                if (out.length() > 0) out.append(" | ");
                out.append(i + 1).append(':').append(expected.get(i).fullName())
                        .append('=')
                        .append(j + 1).append(':').append(actual.get(j).fullName());
                i++;
                j++;
                shown++;
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                i++;
            } else {
                j++;
            }
        }
        return out.toString();
    }

    private static String alignmentDiff(List<NoteEvent> expected, List<NoteEvent> actual) {
        List<Integer> a = toMidi(expected);
        List<Integer> b = toMidi(actual);
        int[][] dp = new int[a.size() + 1][b.size() + 1];
        for (int i = a.size() - 1; i >= 0; i--) {
            for (int j = b.size() - 1; j >= 0; j--) {
                if (a.get(i).equals(b.get(j))) {
                    dp[i][j] = dp[i + 1][j + 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i + 1][j], dp[i][j + 1]);
                }
            }
        }

        StringBuilder out = new StringBuilder();
        int i = 0;
        int j = 0;
        while (i < a.size() || j < b.size()) {
            if (i < a.size() && j < b.size() && a.get(i).equals(b.get(j))) {
                appendDiff(out, "=", i + 1, expected.get(i).fullName(), j + 1, actual.get(j));
                i++;
                j++;
            } else if (j < b.size() && (i == a.size() || dp[i][j + 1] > dp[i + 1][j])) {
                appendDiff(out, "+", -1, null, j + 1, actual.get(j));
                j++;
            } else {
                appendDiff(out, "-", i + 1, expected.get(i).fullName(), -1, null);
                i++;
            }
        }
        return out.toString();
    }

    private static void appendDiff(StringBuilder out,
                                   String kind,
                                   int expectedIndex,
                                   String expectedName,
                                   int actualIndex,
                                   NoteEvent actual) {
        if (out.length() > 0) out.append(" | ");
        out.append(kind);
        if (expectedIndex > 0) {
            out.append("e").append(expectedIndex).append(':').append(expectedName);
        }
        if (actualIndex > 0) {
            if (expectedIndex > 0) out.append('/');
            out.append("a").append(actualIndex).append(':').append(actual.fullName())
                    .append('@').append(Math.round(actual.x))
                    .append(',').append(Math.round(actual.y));
        }
    }

    private static String perStaffAlignment(List<NoteEvent> expected,
                                            List<NoteEvent> actual,
                                            List<AudiverisCompatRecognitionEngine.StaffModel> staves) {
        int[] expectedCounts = new int[]{13, 25, 18};
        StringBuilder out = new StringBuilder();
        int expectedStart = 0;
        for (int i = 0; i < staves.size() && i < expectedCounts.length; i++) {
            int expectedEnd = Math.min(expected.size(), expectedStart + expectedCounts[i]);
            List<NoteEvent> expectedStaff = new ArrayList<NoteEvent>(expected.subList(expectedStart, expectedEnd));
            List<NoteEvent> actualStaff = notesForStaff(actual, staves.get(i));
            int lcs = lcsLength(toMidi(expectedStaff), toMidi(actualStaff));
            if (out.length() > 0) out.append(" | ");
            out.append(i + 1)
                    .append(": expected=").append(expectedStaff.size())
                    .append(", recognized=").append(actualStaff.size())
                    .append(", lcs=").append(lcs)
                    .append(", missed=").append(expectedStaff.size() - lcs)
                    .append(", extra=").append(actualStaff.size() - lcs);
            expectedStart = expectedEnd;
        }
        return out.toString();
    }

    private static List<NoteEvent> notesForStaff(List<NoteEvent> notes,
                                                 AudiverisCompatRecognitionEngine.StaffModel staff) {
        List<NoteEvent> out = new ArrayList<NoteEvent>();
        for (NoteEvent note : notes) {
            if (note.y >= staff.top - staff.spacing * 1.25f && note.y <= staff.bottom + staff.spacing * 1.25f) {
                out.add(note);
            }
        }
        return out;
    }

    private enum FilterMode {
        EDGE_TOUCHING_COMPONENTS,
        NARROW_COMPONENTS,
        EDGE_OR_NARROW_COMPONENTS
    }

    private static String filteredAlignment(List<NoteEvent> expected,
                                            AudiverisCompatRecognitionEngine.DirectRecognition direct,
                                            FilterMode mode) {
        List<NoteEvent> filtered = new ArrayList<NoteEvent>();
        int removed = 0;
        for (int i = 0; i < direct.notes.size(); i++) {
            NoteEvent note = direct.notes.get(i);
            AudiverisCompatRecognitionEngine.CandidateDiagnostic d =
                    i < direct.candidateDiagnostics.size() ? direct.candidateDiagnostics.get(i) : null;
            if (shouldRemove(d, mode)) {
                removed++;
            } else {
                filtered.add(note);
            }
        }
        int lcs = lcsLength(toMidi(expected), toMidi(filtered));
        return mode + ": notes=" + filtered.size()
                + ", removed=" + removed
                + ", lcs=" + lcs
                + ", coverage=" + percent(lcs, expected.size()) + "%"
                + ", precision=" + percent(lcs, filtered.size()) + "%";
    }

    private static boolean shouldRemove(AudiverisCompatRecognitionEngine.CandidateDiagnostic d, FilterMode mode) {
        if (d == null) return false;
        if (mode == FilterMode.EDGE_TOUCHING_COMPONENTS) {
            return "component".equals(d.source) && d.minX <= 0;
        }
        if (mode == FilterMode.NARROW_COMPONENTS) {
            return "component".equals(d.source) && d.maxX - d.minX + 1 <= 5;
        }
        if (mode == FilterMode.EDGE_OR_NARROW_COMPONENTS) {
            return "component".equals(d.source) && (d.minX <= 0 || d.maxX - d.minX + 1 <= 5);
        }
        return false;
    }

    private static AudiverisCompatRecognitionEngine.DirectRecognition filteredDirectRecognition(
            AudiverisCompatRecognitionEngine.DirectRecognition direct,
            FilterMode mode) {
        List<NoteEvent> notes = new ArrayList<NoteEvent>();
        List<AudiverisCompatRecognitionEngine.CandidateDiagnostic> diagnostics =
                new ArrayList<AudiverisCompatRecognitionEngine.CandidateDiagnostic>();
        for (int i = 0; i < direct.notes.size(); i++) {
            AudiverisCompatRecognitionEngine.CandidateDiagnostic d =
                    i < direct.candidateDiagnostics.size() ? direct.candidateDiagnostics.get(i) : null;
            if (shouldRemove(d, mode)) continue;
            notes.add(direct.notes.get(i));
            if (d != null) diagnostics.add(d);
        }
        return new AudiverisCompatRecognitionEngine.DirectRecognition(notes, diagnostics, direct.staves,
                direct.linePeaks, direct.suppressedOverlays, direct.rawCandidateCount);
    }

    private static String sourceSummary(AudiverisCompatRecognitionEngine.DirectRecognition direct) {
        int components = 0;
        int windows = 0;
        int pitchWindows = 0;
        int templates = 0;
        int edgeComponents = 0;
        for (AudiverisCompatRecognitionEngine.CandidateDiagnostic d : direct.candidateDiagnostics) {
            if ("component".equals(d.source)) {
                components++;
                if (d.minX <= 0) edgeComponents++;
            } else if ("window".equals(d.source)) {
                windows++;
            } else if ("pitch-window".equals(d.source)) {
                pitchWindows++;
            } else if ("template".equals(d.source)) {
                templates++;
            }
        }
        return "component=" + components + ", window=" + windows
                + ", pitchWindow=" + pitchWindows + ", template=" + templates
                + ", edgeComponents=" + edgeComponents;
    }

    private static String accidentalSummary(List<NoteEvent> expected, List<NoteEvent> actual) {
        int expectedFlats = 0;
        int actualFlats = 0;
        int actualB = 0;
        for (NoteEvent note : expected) {
            if (note.noteName != null && note.noteName.indexOf('b') >= 0) expectedFlats++;
        }
        for (NoteEvent note : actual) {
            if (note.noteName != null && note.noteName.indexOf('b') >= 0) actualFlats++;
            if ("B".equals(note.noteName)) actualB++;
        }
        return "expectedFlats=" + expectedFlats + ", actualFlats=" + actualFlats + ", actualNaturalB=" + actualB;
    }

    private static String coordinateTruthSummary(List<NoteEvent> expected,
                                                 AudiverisCompatRecognitionEngine.DirectRecognition direct) {
        List<ExpectedPoint> points = expectedReferencePoints();
        if (points.size() != expected.size() || direct.staves.size() < 3) {
            return "unavailable";
        }

        boolean[] matchedActual = new boolean[direct.notes.size()];
        int matched = 0;
        StringBuilder misses = new StringBuilder();
        StringBuilder extras = new StringBuilder();
        for (int i = 0; i < expected.size(); i++) {
            ExpectedPoint point = points.get(i);
            NoteEvent exp = expected.get(i);
            AudiverisCompatRecognitionEngine.StaffModel staff = direct.staves.get(point.staffIndex);
            float expectedY = expectedY(exp, staff);
            float xTolerance = Math.max(18f, staff.spacing * 2.2f);
            float yTolerance = Math.max(8f, staff.spacing * 0.85f);
            int bestIndex = -1;
            float bestDistance = Float.MAX_VALUE;
            for (int j = 0; j < direct.notes.size(); j++) {
                if (matchedActual[j]) continue;
                NoteEvent actual = direct.notes.get(j);
                if (nearestStaff(direct.staves, actual.y) != staff) continue;
                if (!samePitch(exp, actual)) continue;
                float dx = Math.abs(actual.x - point.x);
                float dy = Math.abs(actual.y - expectedY);
                if (dx > xTolerance || dy > yTolerance) continue;
                float distance = dx + dy;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestIndex = j;
                }
            }
            if (bestIndex >= 0) {
                matchedActual[bestIndex] = true;
                matched++;
            } else {
                appendShort(misses, i + 1, exp.fullName(), Math.round(point.x), Math.round(expectedY));
            }
        }

        int extra = 0;
        for (int i = 0; i < direct.notes.size(); i++) {
            if (!matchedActual[i]) {
                extra++;
                NoteEvent note = direct.notes.get(i);
                appendShort(extras, i + 1, note.fullName(), Math.round(note.x), Math.round(note.y));
            }
        }

        return "matched=" + matched
                + ", missed=" + (expected.size() - matched)
                + ", extra=" + extra
                + ", coverage=" + percent(matched, expected.size()) + "%"
                + ", precision=" + percent(matched, direct.notes.size()) + "%"
                + ", missedFirst=" + limitText(misses.toString(), 220)
                + ", extraFirst=" + limitText(extras.toString(), 220);
    }

    private static String locationTruthSummary(List<NoteEvent> expected,
                                               AudiverisCompatRecognitionEngine.DirectRecognition direct) {
        List<ExpectedPoint> points = expectedReferencePoints();
        if (points.size() != expected.size() || direct.staves.size() < 3) {
            return "unavailable";
        }

        boolean[] matchedActual = new boolean[direct.notes.size()];
        int localized = 0;
        int pitchCorrect = 0;
        StringBuilder pitchErrors = new StringBuilder();
        for (int i = 0; i < expected.size(); i++) {
            ExpectedPoint point = points.get(i);
            NoteEvent exp = expected.get(i);
            AudiverisCompatRecognitionEngine.StaffModel staff = direct.staves.get(point.staffIndex);
            float expectedY = expectedY(exp, staff);
            float xTolerance = Math.max(18f, staff.spacing * 2.2f);
            float yTolerance = Math.max(8f, staff.spacing * 1.15f);
            int bestIndex = -1;
            float bestDistance = Float.MAX_VALUE;
            for (int j = 0; j < direct.notes.size(); j++) {
                if (matchedActual[j]) continue;
                NoteEvent actual = direct.notes.get(j);
                if (nearestStaff(direct.staves, actual.y) != staff) continue;
                float dx = Math.abs(actual.x - point.x);
                float dy = Math.abs(actual.y - expectedY);
                if (dx > xTolerance || dy > yTolerance) continue;
                float distance = dx + dy;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestIndex = j;
                }
            }
            if (bestIndex >= 0) {
                matchedActual[bestIndex] = true;
                localized++;
                NoteEvent actual = direct.notes.get(bestIndex);
                if (samePitch(exp, actual)) {
                    pitchCorrect++;
                } else {
                    appendPitchError(pitchErrors, i + 1, exp.fullName(), actual.fullName(),
                            Math.round(point.x), Math.round(expectedY), Math.round(actual.x), Math.round(actual.y));
                }
            }
        }
        return "localized=" + localized
                + ", pitchCorrect=" + pitchCorrect
                + ", pitchErrors=" + (localized - pitchCorrect)
                + ", missedLocations=" + (expected.size() - localized)
                + ", localizationCoverage=" + percent(localized, expected.size()) + "%"
                + ", exactCoverage=" + percent(pitchCorrect, expected.size()) + "%"
                + ", pitchErrorFirst=" + limitText(pitchErrors.toString(), 260);
    }

    private static String candidateTruthBySource(List<NoteEvent> expected,
                                                 AudiverisCompatRecognitionEngine.DirectRecognition direct) {
        List<ExpectedPoint> points = expectedReferencePoints();
        StringBuilder out = new StringBuilder();
        String[] sources = {"component", "window", "pitch-window", "template"};
        for (String source : sources) {
            int total = 0;
            int hits = 0;
            int localized = 0;
            int hitMinWidth = Integer.MAX_VALUE;
            int hitMaxWidth = 0;
            float hitMinScore = Float.MAX_VALUE;
            float hitMaxScore = 0f;
            int missMinWidth = Integer.MAX_VALUE;
            int missMaxWidth = 0;
            float missMinScore = Float.MAX_VALUE;
            float missMaxScore = 0f;
            for (int i = 0; i < direct.notes.size() && i < direct.candidateDiagnostics.size(); i++) {
                AudiverisCompatRecognitionEngine.CandidateDiagnostic diagnostic = direct.candidateDiagnostics.get(i);
                if (!source.equals(diagnostic.source)) continue;
                NoteEvent note = direct.notes.get(i);
                total++;
                int width = diagnostic.maxX - diagnostic.minX + 1;
                boolean hit = isCoordinateTruthHit(note, expected, points, direct.staves);
                if (isLocationTruthHit(note, points, direct.staves)) localized++;
                if (hit) {
                    hits++;
                    hitMinWidth = Math.min(hitMinWidth, width);
                    hitMaxWidth = Math.max(hitMaxWidth, width);
                    hitMinScore = Math.min(hitMinScore, diagnostic.score);
                    hitMaxScore = Math.max(hitMaxScore, diagnostic.score);
                } else {
                    missMinWidth = Math.min(missMinWidth, width);
                    missMaxWidth = Math.max(missMaxWidth, width);
                    missMinScore = Math.min(missMinScore, diagnostic.score);
                    missMaxScore = Math.max(missMaxScore, diagnostic.score);
                }
            }
            if (out.length() > 0) out.append(" | ");
            out.append(source)
                    .append(": exact=").append(hits).append('/').append(total)
                    .append(", localized=").append(localized).append('/').append(total)
                    .append(", hitW=").append(range(hitMinWidth, hitMaxWidth, hits))
                    .append(", missW=").append(range(missMinWidth, missMaxWidth, total - hits))
                    .append(", hitScore=").append(scoreRange(hitMinScore, hitMaxScore, hits))
                    .append(", missScore=").append(scoreRange(missMinScore, missMaxScore, total - hits));
        }
        return out.toString();
    }

    private static boolean isLocationTruthHit(NoteEvent actual,
                                              List<ExpectedPoint> points,
                                              List<AudiverisCompatRecognitionEngine.StaffModel> staves) {
        for (ExpectedPoint point : points) {
            if (point.staffIndex >= staves.size()) continue;
            AudiverisCompatRecognitionEngine.StaffModel staff = staves.get(point.staffIndex);
            if (nearestStaff(staves, actual.y) != staff) continue;
            float xTolerance = Math.max(18f, staff.spacing * 2.2f);
            float yTolerance = Math.max(8f, staff.spacing * 1.15f);
            if (Math.abs(actual.x - point.x) <= xTolerance
                    && actual.y >= staff.top - yTolerance
                    && actual.y <= staff.bottom + yTolerance) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCoordinateTruthHit(NoteEvent actual,
                                                List<NoteEvent> expected,
                                                List<ExpectedPoint> points,
                                                List<AudiverisCompatRecognitionEngine.StaffModel> staves) {
        for (int i = 0; i < expected.size() && i < points.size(); i++) {
            ExpectedPoint point = points.get(i);
            if (point.staffIndex >= staves.size()) continue;
            AudiverisCompatRecognitionEngine.StaffModel staff = staves.get(point.staffIndex);
            if (nearestStaff(staves, actual.y) != staff) continue;
            if (!samePitch(expected.get(i), actual)) continue;
            float expectedY = expectedY(expected.get(i), staff);
            float xTolerance = Math.max(18f, staff.spacing * 2.2f);
            float yTolerance = Math.max(8f, staff.spacing * 1.15f);
            if (Math.abs(actual.x - point.x) <= xTolerance && Math.abs(actual.y - expectedY) <= yTolerance) {
                return true;
            }
        }
        return false;
    }

    private static String range(int min, int max, int count) {
        if (count == 0) return "-";
        return min + ".." + max;
    }

    private static String scoreRange(float min, float max, int count) {
        if (count == 0) return "-";
        return Math.round(min) + ".." + Math.round(max);
    }

    private static String bestStaffPitchCorrection(List<NoteEvent> expected,
                                                   AudiverisCompatRecognitionEngine.DirectRecognition direct) {
        int baseLcs = lcsLength(toMidi(expected), toMidi(direct.notes));
        int bestStaff = -1;
        int bestSteps = 0;
        int bestLcs = baseLcs;
        int bestExact = exactCoordinateMatches(expected, direct.notes, direct.staves);
        int bestLocalized = localizedCoordinateMatches(expected, direct.notes, direct.staves);
        for (int staffIndex = 0; staffIndex < direct.staves.size(); staffIndex++) {
            for (int steps = -3; steps <= 3; steps++) {
                if (steps == 0) continue;
                List<NoteEvent> shifted = shiftStaffNotes(direct.notes, direct.staves, staffIndex, steps);
                int lcs = lcsLength(toMidi(expected), toMidi(shifted));
                int exact = exactCoordinateMatches(expected, shifted, direct.staves);
                int localized = localizedCoordinateMatches(expected, shifted, direct.staves);
                if (lcs > bestLcs || (lcs == bestLcs && exact > bestExact)
                        || (lcs == bestLcs && exact == bestExact && localized > bestLocalized)) {
                    bestStaff = staffIndex;
                    bestSteps = steps;
                    bestLcs = lcs;
                    bestExact = exact;
                    bestLocalized = localized;
                }
            }
        }
        return "baseLcs=" + baseLcs
                + ", bestStaff=" + bestStaff
                + ", steps=" + bestSteps
                + ", lcs=" + bestLcs
                + ", exact=" + bestExact
                + ", localized=" + bestLocalized;
    }

    private static String bestSourcePitchCorrection(List<NoteEvent> expected,
                                                    AudiverisCompatRecognitionEngine.DirectRecognition direct) {
        int baseLcs = lcsLength(toMidi(expected), toMidi(direct.notes));
        String bestSource = "-";
        int bestStaff = -1;
        int bestSteps = 0;
        int bestLcs = baseLcs;
        int bestExact = exactCoordinateMatches(expected, direct.notes, direct.staves);
        int bestLocalized = localizedCoordinateMatches(expected, direct.notes, direct.staves);
        String[] sources = {"component", "window", "pitch-window", "template"};
        for (String source : sources) {
            for (int staffIndex = 0; staffIndex < direct.staves.size(); staffIndex++) {
                for (int steps = -4; steps <= 4; steps++) {
                    if (steps == 0) continue;
                    List<NoteEvent> shifted = shiftSourceStaffNotes(direct, source, staffIndex, steps);
                    int lcs = lcsLength(toMidi(expected), toMidi(shifted));
                    int exact = exactCoordinateMatches(expected, shifted, direct.staves);
                    int localized = localizedCoordinateMatches(expected, shifted, direct.staves);
                    if (lcs > bestLcs || (lcs == bestLcs && exact > bestExact)
                            || (lcs == bestLcs && exact == bestExact && localized > bestLocalized)) {
                        bestSource = source;
                        bestStaff = staffIndex;
                        bestSteps = steps;
                        bestLcs = lcs;
                        bestExact = exact;
                        bestLocalized = localized;
                    }
                }
            }
        }
        return "baseLcs=" + baseLcs
                + ", source=" + bestSource
                + ", staff=" + bestStaff
                + ", steps=" + bestSteps
                + ", lcs=" + bestLcs
                + ", exact=" + bestExact
                + ", localized=" + bestLocalized;
    }

    private static List<NoteEvent> shiftSourceStaffNotes(AudiverisCompatRecognitionEngine.DirectRecognition direct,
                                                         String source,
                                                         int staffIndex,
                                                         int steps) {
        List<NoteEvent> out = new ArrayList<NoteEvent>();
        AudiverisCompatRecognitionEngine.StaffModel target = direct.staves.get(staffIndex);
        for (int i = 0; i < direct.notes.size(); i++) {
            NoteEvent note = direct.notes.get(i);
            AudiverisCompatRecognitionEngine.CandidateDiagnostic diagnostic =
                    i < direct.candidateDiagnostics.size() ? direct.candidateDiagnostics.get(i) : null;
            if (diagnostic != null && source.equals(diagnostic.source) && nearestStaff(direct.staves, note.y) == target) {
                NoteEvent shifted = shiftDiatonic(note.noteName, note.octave, steps);
                out.add(new NoteEvent(shifted.noteName, shifted.octave, note.duration, note.measure, note.x, note.y));
            } else {
                out.add(note);
            }
        }
        return out;
    }

    private static String flatKeyBiasTruth(List<NoteEvent> expected,
                                           AudiverisCompatRecognitionEngine.DirectRecognition direct) {
        List<NoteEvent> biased = new ArrayList<NoteEvent>();
        int changed = 0;
        for (NoteEvent note : direct.notes) {
            NoteEvent replacement = maybeFlatKeyBias(note, direct.staves);
            if (!replacement.fullName().equals(note.fullName())) changed++;
            biased.add(replacement);
        }
        int lcs = lcsLength(toMidi(expected), toMidi(biased));
        int exact = exactCoordinateMatches(expected, biased, direct.staves);
        int localized = localizedCoordinateMatches(expected, biased, direct.staves);
        return "changed=" + changed
                + ", lcs=" + lcs
                + ", exact=" + exact
                + ", localized=" + localized;
    }

    private static String bFlatKeyTruth(List<NoteEvent> expected,
                                        AudiverisCompatRecognitionEngine.DirectRecognition direct) {
        List<NoteEvent> flattened = new ArrayList<NoteEvent>();
        int changed = 0;
        for (NoteEvent note : direct.notes) {
            if ("B".equals(note.noteName)) {
                flattened.add(new NoteEvent("Bb", note.octave, note.duration, note.measure, note.x, note.y));
                changed++;
            } else {
                flattened.add(note);
            }
        }
        int lcs = lcsLength(toMidi(expected), toMidi(flattened));
        int exact = exactCoordinateMatches(expected, flattened, direct.staves);
        int localized = localizedCoordinateMatches(expected, flattened, direct.staves);
        return "changed=" + changed
                + ", lcs=" + lcs
                + ", exact=" + exact
                + ", localized=" + localized;
    }

    private static NoteEvent maybeFlatKeyBias(NoteEvent note,
                                              List<AudiverisCompatRecognitionEngine.StaffModel> staves) {
        if (!"C".equals(note.noteName) || note.octave != 5) {
            return note;
        }
        AudiverisCompatRecognitionEngine.StaffModel staff = nearestStaff(staves, note.y);
        if (staff == null) return note;
        float b4Y = expectedY(new NoteEvent("B", 4, note.duration, note.measure), staff);
        float c5Y = expectedY(new NoteEvent("C", 5, note.duration, note.measure), staff);
        float boundary = (b4Y + c5Y) * 0.5f;
        if (note.y > boundary - staff.spacing * 0.18f) {
            return new NoteEvent("Bb", 4, note.duration, note.measure, note.x, note.y);
        }
        return note;
    }

    private static List<NoteEvent> shiftStaffNotes(List<NoteEvent> notes,
                                                   List<AudiverisCompatRecognitionEngine.StaffModel> staves,
                                                   int staffIndex,
                                                   int steps) {
        List<NoteEvent> out = new ArrayList<NoteEvent>();
        AudiverisCompatRecognitionEngine.StaffModel target = staves.get(staffIndex);
        for (NoteEvent note : notes) {
            if (nearestStaff(staves, note.y) == target) {
                NoteEvent shifted = shiftDiatonic(note.noteName, note.octave, steps);
                out.add(new NoteEvent(shifted.noteName, shifted.octave, note.duration, note.measure, note.x, note.y));
            } else {
                out.add(note);
            }
        }
        return out;
    }

    private static int exactCoordinateMatches(List<NoteEvent> expected,
                                              List<NoteEvent> actual,
                                              List<AudiverisCompatRecognitionEngine.StaffModel> staves) {
        return coordinateMatchCounts(expected, actual, staves, true);
    }

    private static int localizedCoordinateMatches(List<NoteEvent> expected,
                                                  List<NoteEvent> actual,
                                                  List<AudiverisCompatRecognitionEngine.StaffModel> staves) {
        return coordinateMatchCounts(expected, actual, staves, false);
    }

    private static int coordinateMatchCounts(List<NoteEvent> expected,
                                             List<NoteEvent> actual,
                                             List<AudiverisCompatRecognitionEngine.StaffModel> staves,
                                             boolean requirePitch) {
        List<ExpectedPoint> points = expectedReferencePoints();
        boolean[] matchedActual = new boolean[actual.size()];
        int matched = 0;
        for (int i = 0; i < expected.size() && i < points.size(); i++) {
            ExpectedPoint point = points.get(i);
            if (point.staffIndex >= staves.size()) continue;
            NoteEvent exp = expected.get(i);
            AudiverisCompatRecognitionEngine.StaffModel staff = staves.get(point.staffIndex);
            float expectedY = expectedY(exp, staff);
            float xTolerance = Math.max(18f, staff.spacing * 2.2f);
            float yTolerance = Math.max(8f, staff.spacing * (requirePitch ? 0.85f : 1.15f));
            int bestIndex = -1;
            float bestDistance = Float.MAX_VALUE;
            for (int j = 0; j < actual.size(); j++) {
                if (matchedActual[j]) continue;
                NoteEvent note = actual.get(j);
                if (nearestStaff(staves, note.y) != staff) continue;
                if (requirePitch && !samePitch(exp, note)) continue;
                float dx = Math.abs(note.x - point.x);
                float dy = Math.abs(note.y - expectedY);
                if (dx > xTolerance || dy > yTolerance) continue;
                float distance = dx + dy;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestIndex = j;
                }
            }
            if (bestIndex >= 0) {
                matchedActual[bestIndex] = true;
                matched++;
            }
        }
        return matched;
    }

    private static void appendPitchError(StringBuilder out,
                                         int expectedIndex,
                                         String expected,
                                         String actual,
                                         int expectedX,
                                         int expectedY,
                                         int actualX,
                                         int actualY) {
        if (out.length() > 0) out.append(" | ");
        out.append(expectedIndex).append(':').append(expected)
                .append("->").append(actual)
                .append("@e").append(expectedX).append(',').append(expectedY)
                .append("/a").append(actualX).append(',').append(actualY);
    }

    private static List<ExpectedPoint> expectedReferencePoints() {
        float[][] xs = new float[][]{
                {642, 670, 704, 739, 810, 852, 932, 965, 1000, 1032, 1108, 1140, 1175},
                {176, 205, 235, 265, 326, 356, 386, 420, 486, 518, 552, 584, 646,
                        676, 704, 784, 812, 842, 874, 950, 1000, 1070, 1104, 1140, 1170},
                {176, 224, 262, 354, 402, 450, 494, 564, 610, 660, 706, 784, 830, 878, 924, 1006, 1052, 1102}
        };
        List<ExpectedPoint> out = new ArrayList<ExpectedPoint>();
        for (int staff = 0; staff < xs.length; staff++) {
            for (float x : xs[staff]) {
                out.add(new ExpectedPoint(staff, x));
            }
        }
        return out;
    }

    private static boolean samePitch(NoteEvent expected, NoteEvent actual) {
        return expected.noteName.equals(actual.noteName) && expected.octave == actual.octave;
    }

    private static float expectedY(NoteEvent note, AudiverisCompatRecognitionEngine.StaffModel staff) {
        String base = note.noteName == null || note.noteName.length() == 0 ? "C" : note.noteName.substring(0, 1);
        String[] steps = {"C", "D", "E", "F", "G", "A", "B"};
        int idx = 0;
        for (int i = 0; i < steps.length; i++) {
            if (steps[i].equals(base)) {
                idx = i;
                break;
            }
        }
        int absolute = idx + (note.octave - 4) * 7;
        int bottomE4 = 2;
        return staff.bottom - (absolute - bottomE4) * staff.spacing * 0.5f;
    }

    private static AudiverisCompatRecognitionEngine.StaffModel nearestStaff(
            List<AudiverisCompatRecognitionEngine.StaffModel> staves,
            float y) {
        AudiverisCompatRecognitionEngine.StaffModel best = null;
        float bestDist = Float.MAX_VALUE;
        for (AudiverisCompatRecognitionEngine.StaffModel staff : staves) {
            float d = Math.abs(y - staff.center);
            if (d < bestDist) {
                bestDist = d;
                best = staff;
            }
        }
        return best;
    }

    private static void appendShort(StringBuilder out, int index, String name, int x, int y) {
        if (out.length() > 0) out.append(" | ");
        out.append(index).append(':').append(name).append('@').append(x).append(',').append(y);
    }

    private static String limitText(String value, int maxLength) {
        if (value.length() <= maxLength) return value;
        return value.substring(0, maxLength) + "...";
    }

    private static class ExpectedPoint {
        final int staffIndex;
        final float x;

        ExpectedPoint(int staffIndex, float x) {
            this.staffIndex = staffIndex;
            this.x = x;
        }
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

    private static String staffShiftSweep(List<NoteEvent> expected,
                                          List<NoteEvent> actual,
                                          List<AudiverisCompatRecognitionEngine.StaffModel> staves) {
        List<Integer> expectedMidi = toMidi(expected);
        StringBuilder out = new StringBuilder();
        for (int staffIndex = 0; staffIndex < staves.size(); staffIndex++) {
            AudiverisCompatRecognitionEngine.StaffModel staff = staves.get(staffIndex);
            int bestSteps = 0;
            int bestLcs = -1;
            for (int steps = -4; steps <= 4; steps++) {
                List<Integer> shifted = new ArrayList<Integer>();
                for (NoteEvent note : actual) {
                    if (note.y >= staff.top - staff.spacing * 1.25f
                            && note.y <= staff.bottom + staff.spacing * 1.25f) {
                        NoteEvent shiftedNote = shiftDiatonic(note.noteName, note.octave, steps);
                        shifted.add(MusicNotation.midiFor(shiftedNote.noteName, shiftedNote.octave));
                    } else {
                        shifted.add(MusicNotation.midiFor(note.noteName, note.octave));
                    }
                }
                int lcs = lcsLength(expectedMidi, shifted);
                if (lcs > bestLcs) {
                    bestLcs = lcs;
                    bestSteps = steps;
                }
            }
            if (out.length() > 0) out.append(", ");
            out.append(staffIndex).append('=').append(bestSteps).append('/').append(bestLcs);
        }
        return out.toString();
    }

    private static String staffOffsetSweep(List<NoteEvent> expected,
                                           List<NoteEvent> actual,
                                           List<AudiverisCompatRecognitionEngine.StaffModel> staves) {
        List<Integer> expectedMidi = toMidi(expected);
        StringBuilder out = new StringBuilder();
        for (int staffIndex = 0; staffIndex < staves.size(); staffIndex++) {
            AudiverisCompatRecognitionEngine.StaffModel staff = staves.get(staffIndex);
            int bestOffset = 0;
            int bestLcs = -1;
            for (int offset = -8; offset <= 8; offset++) {
                List<Integer> shifted = new ArrayList<Integer>();
                for (NoteEvent note : actual) {
                    if (note.y >= staff.top - staff.spacing * 1.25f
                            && note.y <= staff.bottom + staff.spacing * 1.25f) {
                        NoteEvent remapped = remapNoteAt(note.x, note.y, staff, offset);
                        shifted.add(MusicNotation.midiFor(remapped.noteName, remapped.octave));
                    } else {
                        shifted.add(MusicNotation.midiFor(note.noteName, note.octave));
                    }
                }
                int lcs = lcsLength(expectedMidi, shifted);
                if (lcs > bestLcs) {
                    bestLcs = lcs;
                    bestOffset = offset;
                }
            }
            if (out.length() > 0) out.append(", ");
            out.append(staffIndex).append('=').append(bestOffset).append('/').append(bestLcs);
        }
        return out.toString();
    }

    private static NoteEvent remapNoteAt(float x,
                                         float y,
                                         AudiverisCompatRecognitionEngine.StaffModel staff,
                                         int bottomOffset) {
        float bottomLine = staff.bottom + bottomOffset;
        float halfStepPx = staff.spacing * 0.5f;
        int diatonicOffset = Math.round((bottomLine - y) / halfStepPx);
        String[] steps = {"C", "D", "E", "F", "G", "A", "B"};
        int absolute = 2 + diatonicOffset;
        int octave = 4;
        while (absolute < 0) {
            absolute += 7;
            octave--;
        }
        while (absolute >= 7) {
            absolute -= 7;
            octave++;
        }
        return new NoteEvent(steps[absolute], octave, "quarter", 1, x, y);
    }

    private static String melodicOutlierSweep(List<NoteEvent> expected, List<NoteEvent> actual) {
        List<Integer> expectedMidi = toMidi(expected);
        StringBuilder out = new StringBuilder();
        for (int threshold = 6; threshold <= 14; threshold += 2) {
            List<NoteEvent> filteredNotes = filterMelodicOutliers(actual, threshold);
            int lcs = lcsLength(expectedMidi, toMidi(filteredNotes));
            if (out.length() > 0) out.append(", ");
            out.append(threshold).append('=').append(filteredNotes.size()).append('/').append(lcs);
        }
        return out.toString();
    }

    private static String playableRangeSweep(List<NoteEvent> expected, List<NoteEvent> actual) {
        int min = MusicNotation.midiFor("F", 4);
        int max = MusicNotation.midiFor("G", 5);
        List<NoteEvent> filtered = new ArrayList<NoteEvent>();
        for (NoteEvent note : actual) {
            int midi = MusicNotation.midiFor(note.noteName, note.octave);
            if (midi >= min && midi <= max) {
                filtered.add(note);
            }
        }
        int lcs = lcsLength(toMidi(expected), toMidi(filtered));
        return "F4..G5=" + filtered.size() + "/" + lcs
                + ", coverage=" + percent(lcs, expected.size()) + "%"
                + ", precision=" + percent(lcs, filtered.size()) + "%";
    }

    private static List<NoteEvent> filterMelodicOutliers(List<NoteEvent> notes, int semitoneThreshold) {
        List<NoteEvent> out = new ArrayList<NoteEvent>();
        for (int i = 0; i < notes.size(); i++) {
            if (i > 0 && i < notes.size() - 1) {
                int prev = MusicNotation.midiFor(notes.get(i - 1).noteName, notes.get(i - 1).octave);
                int current = MusicNotation.midiFor(notes.get(i).noteName, notes.get(i).octave);
                int next = MusicNotation.midiFor(notes.get(i + 1).noteName, notes.get(i + 1).octave);
                if (Math.abs(current - prev) > semitoneThreshold
                        && Math.abs(current - next) > semitoneThreshold
                        && Math.abs(prev - next) <= semitoneThreshold) {
                    continue;
                }
            }
            out.add(notes.get(i));
        }
        return out;
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

    private static void writeCoordinateTruthOverlay(BufferedImage source,
                                                    AudiverisCompatRecognitionEngine.DirectRecognition direct,
                                                    List<NoteEvent> expected,
                                                    File out) throws Exception {
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        g.drawImage(source, 0, 0, null);
        g.setStroke(new BasicStroke(2f));

        g.setColor(new Color(0, 120, 255, 220));
        List<ExpectedPoint> points = expectedReferencePoints();
        for (int i = 0; i < points.size() && i < expected.size() && direct.staves.size() > points.get(i).staffIndex; i++) {
            ExpectedPoint point = points.get(i);
            AudiverisCompatRecognitionEngine.StaffModel staff = direct.staves.get(point.staffIndex);
            int x = Math.round(point.x);
            int y = Math.round(expectedY(expected.get(i), staff));
            int r = 6;
            g.drawOval(x - r, y - r, r * 2, r * 2);
            g.drawString((i + 1) + ":" + expected.get(i).fullName(), x + r + 2, y - r - 2);
        }

        g.setColor(new Color(255, 0, 0, 180));
        for (int i = 0; i < direct.notes.size(); i++) {
            NoteEvent note = direct.notes.get(i);
            int x = Math.round(note.x);
            int y = Math.round(note.y);
            int r = 4;
            g.drawOval(x - r, y - r, r * 2, r * 2);
            g.drawString("a" + (i + 1), x + r + 1, y + r + 10);
        }

        g.dispose();
        ImageIO.write(copy, "png", out);
    }
}
