package tatar.eljah.recorder;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PhotoRecognitionScreenshotRegressionTest {

    public static void main(String[] args) throws Exception {
        File photo = new File("photo_2026-02-13_14-27-38.jpg");
        File screenshot = new File("clear_sreenshot.png");
        File xml = new File("Free-trial-photo-2026-02-13-14-27-38.xml");

        if (!photo.exists() || !screenshot.exists() || !xml.exists()) {
            throw new AssertionError("Required regression files are missing in repository root");
        }

        List<NoteEvent> expectedFromXml = parseXmlNotes(xml);
        if (expectedFromXml.isEmpty()) {
            throw new AssertionError("XML reference did not contain pitched notes");
        }

        RecognitionSummary photoSummary = recognizeImage(photo, "reference-photo", expectedFromXml);
        RecognitionSummary screenshotSummary = recognizeImage(screenshot, "clear-screenshot", expectedFromXml);

        // We intentionally do not enforce any hardcoded count like 56 for every input.
        // Instead, we evaluate how much of the reference melody was honestly recovered
        // and also whether screenshot and photo agree with each other.
        if (screenshotSummary.recognized.isEmpty()) {
            throw new AssertionError("Screenshot recognition returned no notes");
        }

        PairwiseSimilarity screenshotVsPhoto = compareRecognitions(photoSummary.recognized, screenshotSummary.recognized);

        boolean sameAsReferencePiece = screenshotSummary.referenceCoverage >= 0.65f
                && screenshotSummary.recognitionPrecision >= 0.65f
                && screenshotVsPhoto.coverageFromSecond >= 0.65f
                && screenshotVsPhoto.precisionToFirst >= 0.65f;

        System.out.println("Reference photo notes: " + photoSummary.recognized.size()
                + ", coverage(xml)=" + formatPct(photoSummary.referenceCoverage)
                + ", precision(xml)=" + formatPct(photoSummary.recognitionPrecision));
        System.out.println("Screenshot notes: " + screenshotSummary.recognized.size()
                + ", coverage(xml)=" + formatPct(screenshotSummary.referenceCoverage)
                + ", precision(xml)=" + formatPct(screenshotSummary.recognitionPrecision));
        System.out.println("Screenshot vs photo: lcs=" + screenshotVsPhoto.lcs
                + ", coverage(screenshot->photo)=" + formatPct(screenshotVsPhoto.coverageFromSecond)
                + ", precision(photo<-screenshot)=" + formatPct(screenshotVsPhoto.precisionToFirst));
        System.out.println("Conclusion: screenshot is "
                + (sameAsReferencePiece ? "the same composition" : "not confidently the same composition")
                + " (LCS against XML = " + screenshotSummary.lcsWithReference + "/" + expectedFromXml.size() + ").");
    }

    private static RecognitionSummary recognizeImage(File imageFile,
                                                     String title,
                                                     List<NoteEvent> expectedFromXml) throws Exception {
        BufferedImage image = ImageIO.read(imageFile);
        if (image == null) {
            throw new AssertionError("Unable to decode image file: " + imageFile.getName());
        }

        int width = image.getWidth();
        int height = image.getHeight();
        int[] argb = image.getRGB(0, 0, width, height, null, 0, width);

        OpenCvScoreProcessor processor = new OpenCvScoreProcessor();
        OpenCvScoreProcessor.ProcessingResult result = processor.processArgb(width, height, argb, title);
        List<NoteEvent> recognized = result.piece.notes;

        List<Integer> expectedMidi = toMidi(expectedFromXml);
        List<Integer> recognizedMidi = toMidi(recognized);
        int lcs = longestCommonSubsequence(expectedMidi, recognizedMidi);

        RecognitionSummary summary = new RecognitionSummary();
        summary.recognized = recognized;
        summary.lcsWithReference = lcs;
        summary.referenceCoverage = expectedMidi.isEmpty() ? 0f : ((float) lcs / (float) expectedMidi.size());
        summary.recognitionPrecision = recognizedMidi.isEmpty() ? 0f : ((float) lcs / (float) recognizedMidi.size());
        return summary;
    }


    private static PairwiseSimilarity compareRecognitions(List<NoteEvent> first, List<NoteEvent> second) {
        List<Integer> firstMidi = toMidi(first);
        List<Integer> secondMidi = toMidi(second);
        int lcs = longestCommonSubsequence(firstMidi, secondMidi);

        PairwiseSimilarity out = new PairwiseSimilarity();
        out.lcs = lcs;
        out.coverageFromSecond = secondMidi.isEmpty() ? 0f : ((float) lcs / (float) secondMidi.size());
        out.precisionToFirst = firstMidi.isEmpty() ? 0f : ((float) lcs / (float) firstMidi.size());
        return out;
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

    private static String formatPct(float ratio) {
        return Math.round(ratio * 100f) + "%";
    }

    private static List<NoteEvent> parseXmlNotes(File xmlFile) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        Document doc = dbf.newDocumentBuilder().parse(xmlFile);
        NodeList noteNodes = doc.getElementsByTagName("note");
        List<NoteEvent> out = new ArrayList<NoteEvent>();

        for (int i = 0; i < noteNodes.getLength(); i++) {
            Element note = (Element) noteNodes.item(i);
            if (note.getElementsByTagName("rest").getLength() > 0) {
                continue;
            }
            NodeList pitchNodes = note.getElementsByTagName("pitch");
            if (pitchNodes.getLength() == 0) {
                continue;
            }
            Element pitch = (Element) pitchNodes.item(0);
            String step = textOfFirst(pitch, "step");
            String alter = textOfFirst(pitch, "alter");
            String octaveText = textOfFirst(pitch, "octave");
            String duration = textOfFirst(note, "type");
            if (step == null || octaveText == null) {
                continue;
            }
            String name = step;
            if ("1".equals(alter)) name += "#";
            if ("-1".equals(alter)) name += "b";
            if (duration == null || duration.length() == 0) duration = "quarter";
            out.add(new NoteEvent(name, Integer.parseInt(octaveText.trim()), duration, 1 + out.size() / 4));
        }
        return out;
    }

    private static String textOfFirst(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        if (list.getLength() == 0) return null;
        return list.item(0).getTextContent();
    }

    private static class RecognitionSummary {
        List<NoteEvent> recognized;
        int lcsWithReference;
        float referenceCoverage;
        float recognitionPrecision;
    }

    private static class PairwiseSimilarity {
        int lcs;
        float coverageFromSecond;
        float precisionToFirst;
    }
}
