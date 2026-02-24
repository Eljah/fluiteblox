package tatar.eljah.recorder;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class PhotoRecognitionParameterSweepTest {

    public static void main(String[] args) throws Exception {
        File screenshot = new File("clear_sreenshot.png");
        File xml = new File("Free-trial-photo-2026-02-13-14-27-38.xml");
        if (!screenshot.exists() || !xml.exists()) {
            throw new AssertionError("Required files are missing in repository root");
        }

        XmlReference reference = parseXmlReference(xml);
        if (reference.allNotes.isEmpty() || reference.perSystemMidi.isEmpty()) {
            throw new AssertionError("XML reference did not contain pitched notes grouped by systems");
        }

        BufferedImage image = ImageIO.read(screenshot);
        if (image == null) {
            throw new AssertionError("Unable to decode screenshot");
        }

        int width = image.getWidth();
        int height = image.getHeight();
        int[] argb = image.getRGB(0, 0, width, height, null, 0, width);

        OpenCvScoreProcessor processor = new OpenCvScoreProcessor();

        OpenCvScoreProcessor.ProcessingOptions baselineOptions = new OpenCvScoreProcessor.ProcessingOptions(
                7, 3, 0.5f, true, true,
                0.6f, 4.0f, 0.18f, 0.9f, 0.32f, true, 0.75f);
        OpenCvScoreProcessor.ProcessingResult baselineResult = processor.processArgb(width, height, argb, "sweep-baseline", baselineOptions);
        if (!baselineResult.openCvUsed) {
            throw new AssertionError("OpenCV mode required for baseline, got: " + baselineResult.processingMode);
        }
        SweepResult baseline = evaluate(baselineResult, reference, baselineOptions);

        float[] areaMinFactors = {0.35f, 0.6f, 1.0f};
        float[] areaMaxFactors = {2.6f, 4.0f, 5.5f};
        float[] minFillValues = {0.08f, 0.14f, 0.18f};
        float[] minCircularityValues = {0.14f, 0.24f, 0.32f};
        float[] analyticalStrengthValues = {0.60f, 0.85f};

        SweepResult best = null;
        int tried = 0;
        for (float areaMin : areaMinFactors) {
            for (float areaMax : areaMaxFactors) {
                if (areaMax <= areaMin * 3f) {
                    continue;
                }
                for (float minFill : minFillValues) {
                    for (float minCircularity : minCircularityValues) {
                        for (float analyticalStrength : analyticalStrengthValues) {
                            OpenCvScoreProcessor.ProcessingOptions options = new OpenCvScoreProcessor.ProcessingOptions(
                                    7,
                                    3,
                                    0.5f,
                                    true,
                                    true,
                                    areaMin,
                                    areaMax,
                                    minFill,
                                    0.95f,
                                    minCircularity,
                                    true,
                                    analyticalStrength);

                            OpenCvScoreProcessor.ProcessingResult result = processor.processArgb(width, height, argb, "sweep", options);
                            if (!result.openCvUsed) {
                                throw new AssertionError("OpenCV mode required for sweep, got: " + result.processingMode);
                            }

                            SweepResult candidate = evaluate(result, reference, options);
                            tried++;
                            if (best == null || candidate.error < best.error) {
                                best = candidate;
                            }
                        }
                    }
                }
            }
        }

        if (best == null) {
            throw new AssertionError("Sweep produced no results");
        }

        System.out.println("Swept combinations=" + tried + " (adaptive binarization OFF, noise suppression OFF, recall-first ON)");
        printResult("Baseline", baseline);
        printResult("Best", best);

        int confidentSystems = 0;
        for (int i = 0; i < best.systemMetrics.size(); i++) {
            SystemMetrics m = best.systemMetrics.get(i);
            if (m.coverage >= 0.50f && m.precision >= 0.50f) {
                confidentSystems++;
            }
        }

        System.out.println("Per-system confident matches=" + confidentSystems + "/" + best.systemMetrics.size());
        System.out.println("Note: recall-first mode intentionally allows extra notes first, then analytical filter trims non-note-like objects.");

        if (best.error > baseline.error + 0.001f) {
            throw new AssertionError("Sweep did not improve error enough: baseline=" + format2(baseline.error)
                    + ", best=" + format2(best.error));
        }
    }

    private static void printResult(String label, SweepResult result) {
        System.out.println(label + " options: minArea=" + result.options.noteMinAreaFactor
                + ", maxArea=" + result.options.noteMaxAreaFactor
                + ", minFill=" + result.options.noteMinFill
                + ", minCircularity=" + result.options.noteMinCircularity
                + ", recallFirst=" + result.options.recallFirstMode
                + ", analyticalStrength=" + result.options.analyticalFilterStrength);
        System.out.println(label + " global: recognized=" + result.recognizedCount
                + ", expected=" + result.expectedCount
                + ", lcs=" + result.lcs
                + ", coverage=" + format2(result.globalCoverage)
                + ", precision=" + format2(result.globalPrecision)
                + ", meanPitchDistance=" + format2(result.meanPitchDistance)
                + ", countDelta=" + format2(result.normalizedCountDelta)
                + ", systemPenalty=" + format2(result.systemPenalty)
                + ", error=" + format2(result.error));
        if (result.diagnosticsSummary != null) {
            System.out.println("  diagnostics: " + result.diagnosticsSummary);
            if (result.primaryLossReason != null) {
                System.out.println("  primary loss reason: " + result.primaryLossReason);
            }
        }
        for (int i = 0; i < result.systemMetrics.size(); i++) {
            SystemMetrics m = result.systemMetrics.get(i);
            System.out.println("  system " + (i + 1)
                    + ": exp=" + m.expectedCount
                    + ", rec=" + m.recognizedCount
                    + ", lcs=" + m.lcs
                    + ", coverage=" + format2(m.coverage)
                    + ", precision=" + format2(m.precision)
                    + ", pitchDist=" + format2(m.meanPitchDistance));
        }
    }

    private static SweepResult evaluate(OpenCvScoreProcessor.ProcessingResult result,
                                        XmlReference reference,
                                        OpenCvScoreProcessor.ProcessingOptions options) {
        List<NoteEvent> recognized = result.piece.notes;
        List<Integer> expectedMidi = reference.allMidi;
        List<Integer> recognizedMidi = toMidi(recognized);

        int lcs = longestCommonSubsequence(expectedMidi, recognizedMidi);
        float globalCoverage = expectedMidi.isEmpty() ? 1f : (float) lcs / (float) expectedMidi.size();
        float globalPrecision = recognizedMidi.isEmpty() ? 0f : (float) lcs / (float) recognizedMidi.size();
        float normalizedCountDelta = expectedMidi.isEmpty()
                ? recognizedMidi.size()
                : Math.abs(recognizedMidi.size() - expectedMidi.size()) / (float) expectedMidi.size();
        float meanPitchDistance = meanGreedyPitchDistance(expectedMidi, recognizedMidi);

        List<List<Integer>> recognizedBySystem = splitRecognizedBySystem(recognized, result.staffCorridors, reference.perSystemMidi.size());
        List<SystemMetrics> systemMetrics = new ArrayList<SystemMetrics>();
        float systemPenalty = 0f;
        for (int i = 0; i < reference.perSystemMidi.size(); i++) {
            List<Integer> expectedSystem = reference.perSystemMidi.get(i);
            List<Integer> recognizedSystem = i < recognizedBySystem.size() ? recognizedBySystem.get(i) : Collections.<Integer>emptyList();
            int systemLcs = longestCommonSubsequence(expectedSystem, recognizedSystem);
            float coverage = expectedSystem.isEmpty() ? 1f : (float) systemLcs / (float) expectedSystem.size();
            float precision = recognizedSystem.isEmpty() ? 0f : (float) systemLcs / (float) recognizedSystem.size();
            float pitchDist = meanGreedyPitchDistance(expectedSystem, recognizedSystem);

            SystemMetrics metric = new SystemMetrics();
            metric.expectedCount = expectedSystem.size();
            metric.recognizedCount = recognizedSystem.size();
            metric.lcs = systemLcs;
            metric.coverage = coverage;
            metric.precision = precision;
            metric.meanPitchDistance = pitchDist;
            systemMetrics.add(metric);

            float thisPenalty = (1f - coverage) * 0.55f
                    + (1f - precision) * 0.25f
                    + Math.min(1f, pitchDist / 12f) * 0.20f;
            systemPenalty += thisPenalty;
        }
        systemPenalty = systemMetrics.isEmpty() ? 1f : (systemPenalty / systemMetrics.size());

        // Итоговая ошибка: глобальная последовательность + количество + средняя дистанция по высоте
        // и штраф за несовпадение по каждому нотоносцу (system-by-system).
        float error = (1f - globalCoverage) * 0.30f
                + (1f - globalPrecision) * 0.20f
                + normalizedCountDelta * 0.15f
                + Math.min(1f, meanPitchDistance / 12f) * 0.10f
                + systemPenalty * 0.25f;

        SweepResult out = new SweepResult();
        out.options = options;
        out.error = error;
        out.lcs = lcs;
        out.expectedCount = expectedMidi.size();
        out.recognizedCount = recognizedMidi.size();
        out.normalizedCountDelta = normalizedCountDelta;
        out.meanPitchDistance = meanPitchDistance;
        out.globalCoverage = globalCoverage;
        out.globalPrecision = globalPrecision;
        out.systemPenalty = systemPenalty;
        out.systemMetrics = systemMetrics;
        if (result.noteDiagnostics != null) {
            out.diagnosticsSummary = result.noteDiagnostics.summary();
            out.primaryLossReason = primaryLossReason(result.noteDiagnostics);
        }
        return out;
    }

    private static List<List<Integer>> splitRecognizedBySystem(List<NoteEvent> recognized,
                                                               List<OpenCvScoreProcessor.StaffCorridor> corridors,
                                                               int expectedSystems) {
        List<List<Integer>> out = new ArrayList<List<Integer>>();
        for (int i = 0; i < expectedSystems; i++) {
            out.add(new ArrayList<Integer>());
        }
        if (recognized.isEmpty()) {
            return out;
        }

        if (corridors == null || corridors.isEmpty()) {
            List<NoteEvent> sorted = new ArrayList<NoteEvent>(recognized);
            Collections.sort(sorted, new Comparator<NoteEvent>() {
                @Override
                public int compare(NoteEvent a, NoteEvent b) {
                    return Float.compare(a.y, b.y);
                }
            });
            for (int i = 0; i < sorted.size(); i++) {
                int bucket = Math.min(expectedSystems - 1, (int) (((long) i * expectedSystems) / Math.max(1, sorted.size())));
                out.get(bucket).add(MusicNotation.midiFor(sorted.get(i).noteName, sorted.get(i).octave));
            }
            return out;
        }

        for (NoteEvent n : recognized) {
            int midi = MusicNotation.midiFor(n.noteName, n.octave);
            int bestIdx = nearestCorridorIndex(n.y, corridors);
            if (bestIdx < 0) {
                bestIdx = Math.min(expectedSystems - 1, (int) (n.y * expectedSystems));
            }
            bestIdx = Math.max(0, Math.min(out.size() - 1, bestIdx));
            out.get(bestIdx).add(midi);
        }
        return out;
    }

    private static int nearestCorridorIndex(float y, List<OpenCvScoreProcessor.StaffCorridor> corridors) {
        int best = -1;
        float bestDist = Float.MAX_VALUE;
        for (int i = 0; i < corridors.size(); i++) {
            OpenCvScoreProcessor.StaffCorridor c = corridors.get(i);
            float cy = (c.top + c.bottom) * 0.5f;
            float dist = Math.abs(y - cy);
            if (dist < bestDist) {
                bestDist = dist;
                best = i;
            }
        }
        return best;
    }

    private static float meanGreedyPitchDistance(List<Integer> expected, List<Integer> actual) {
        if (expected.isEmpty() || actual.isEmpty()) {
            return 12f;
        }
        int i = 0;
        int j = 0;
        int sum = 0;
        int matches = 0;
        while (i < expected.size() && j < actual.size()) {
            int d0 = Math.abs(expected.get(i) - actual.get(j));
            int d1 = (j + 1 < actual.size()) ? Math.abs(expected.get(i) - actual.get(j + 1)) : Integer.MAX_VALUE;
            int d2 = (i + 1 < expected.size()) ? Math.abs(expected.get(i + 1) - actual.get(j)) : Integer.MAX_VALUE;
            if (d0 <= d1 && d0 <= d2) {
                sum += d0;
                matches++;
                i++;
                j++;
            } else if (d1 < d2) {
                j++;
            } else {
                i++;
            }
        }
        if (matches == 0) {
            return 12f;
        }
        return sum / (float) matches;
    }

    private static List<Integer> toMidi(List<NoteEvent> notes) {
        List<Integer> midi = new ArrayList<Integer>();
        for (NoteEvent n : notes) {
            midi.add(MusicNotation.midiFor(n.noteName, n.octave));
        }
        return midi;
    }

    private static int longestCommonSubsequence(List<Integer> a, List<Integer> b) {
        int[][] dp = new int[a.size() + 1][b.size() + 1];
        for (int i = 1; i <= a.size(); i++) {
            for (int j = 1; j <= b.size(); j++) {
                if (a.get(i - 1).equals(b.get(j - 1))) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }
        return dp[a.size()][b.size()];
    }

    private static String primaryLossReason(OpenCvScoreProcessor.NoteDetectionDiagnostics d) {
        int[] values = new int[]{
                d.rejectedByArea,
                d.rejectedByBounds,
                d.rejectedBySize,
                d.rejectedByAspect,
                d.rejectedByFill,
                d.rejectedByPerimeter,
                d.rejectedByCircularity,
                d.rejectedByStaffPosition,
                d.removedByCenterDistanceDedupe,
                d.removedBySlotDedupe
        };
        String[] names = new String[]{
                "area-threshold filtering",
                "image-bounds filtering",
                "size filtering",
                "aspect-ratio filtering",
                "fill-ratio filtering",
                "perimeter filtering",
                "circularity filtering",
                "staff-corridor position filtering",
                "center-distance dedupe",
                "slot dedupe"
        };
        int bestIdx = 0;
        for (int i = 1; i < values.length; i++) {
            if (values[i] > values[bestIdx]) bestIdx = i;
        }
        if (values[bestIdx] <= 0) {
            return "none";
        }
        return names[bestIdx] + " (" + values[bestIdx] + ")";
    }

    private static String format2(float value) {
        return String.format(java.util.Locale.US, "%.2f", value);
    }

    private static XmlReference parseXmlReference(File xmlFile) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        Document doc = dbf.newDocumentBuilder().parse(xmlFile);
        NodeList measureNodes = doc.getElementsByTagName("measure");

        List<NoteEvent> allNotes = new ArrayList<NoteEvent>();
        List<List<Integer>> perSystem = new ArrayList<List<Integer>>();
        perSystem.add(new ArrayList<Integer>());
        int systemIndex = 0;

        for (int mi = 0; mi < measureNodes.getLength(); mi++) {
            Element measure = (Element) measureNodes.item(mi);
            if (hasNewSystemPrint(measure) && !perSystem.get(systemIndex).isEmpty()) {
                systemIndex++;
                perSystem.add(new ArrayList<Integer>());
            }

            NodeList children = measure.getChildNodes();
            for (int ci = 0; ci < children.getLength(); ci++) {
                Node child = children.item(ci);
                if (!(child instanceof Element)) {
                    continue;
                }
                Element elem = (Element) child;
                if (!"note".equals(elem.getTagName())) {
                    continue;
                }
                if (elem.getElementsByTagName("rest").getLength() > 0) {
                    continue;
                }
                NodeList pitchNodes = elem.getElementsByTagName("pitch");
                if (pitchNodes.getLength() == 0) {
                    continue;
                }
                Element pitch = (Element) pitchNodes.item(0);
                String step = textOfFirst(pitch, "step");
                String alter = textOfFirst(pitch, "alter");
                String octaveText = textOfFirst(pitch, "octave");
                if (step == null || octaveText == null) {
                    continue;
                }
                String name = step;
                if ("1".equals(alter)) name += "#";
                if ("-1".equals(alter)) name += "b";
                int octave = Integer.parseInt(octaveText.trim());
                int midi = MusicNotation.midiFor(name, octave);

                allNotes.add(new NoteEvent(name, octave, "quarter", 1 + allNotes.size() / 4));
                perSystem.get(systemIndex).add(midi);
            }
        }

        XmlReference out = new XmlReference();
        out.allNotes = allNotes;
        out.allMidi = toMidi(allNotes);
        out.perSystemMidi = perSystem;
        return out;
    }

    private static boolean hasNewSystemPrint(Element measure) {
        NodeList printNodes = measure.getElementsByTagName("print");
        for (int i = 0; i < printNodes.getLength(); i++) {
            Node n = printNodes.item(i);
            if (!(n instanceof Element)) {
                continue;
            }
            Element p = (Element) n;
            if ("yes".equalsIgnoreCase(p.getAttribute("new-system"))) {
                return true;
            }
        }
        return false;
    }

    private static String textOfFirst(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        if (list.getLength() == 0) return null;
        return list.item(0).getTextContent();
    }

    private static class XmlReference {
        List<NoteEvent> allNotes;
        List<Integer> allMidi;
        List<List<Integer>> perSystemMidi;
    }

    private static class SweepResult {
        OpenCvScoreProcessor.ProcessingOptions options;
        int lcs;
        int expectedCount;
        int recognizedCount;
        float normalizedCountDelta;
        float meanPitchDistance;
        float globalCoverage;
        float globalPrecision;
        float systemPenalty;
        float error;
        List<SystemMetrics> systemMetrics;
        String diagnosticsSummary;
        String primaryLossReason;
    }

    private static class SystemMetrics {
        int expectedCount;
        int recognizedCount;
        int lcs;
        float coverage;
        float precision;
        float meanPitchDistance;
    }
}
