package tatar.eljah.recorder;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class FirstStaffParameterSweepTest {
    private static class Ref {
        List<String> pitch = new ArrayList<String>();
        List<String> duration = new ArrayList<String>();
    }

    private static class Candidate {
        OpenCvScoreProcessor.ProcessingOptions options;
        int pitchOk;
        int durationOk;
        int topStaffCount;
        int totalCount;
        float durationPrecision;

        String summary() {
            return String.format(
                    "minA=%.2f minFill=%.2f minCirc=%.2f analytical=%.2f | pitchOk=%d durationOk=%d top=%d total=%d durationPrecision=%.4f",
                    options.noteMinAreaFactor,
                    options.noteMinFill,
                    options.noteMinCircularity,
                    options.analyticalFilterStrength,
                    pitchOk,
                    durationOk,
                    topStaffCount,
                    totalCount,
                    durationPrecision
            );
        }
    }

    public static void main(String[] args) throws Exception {
        Ref reference = parseFirstSystemReference(new File("Free-trial-photo-2026-02-13-14-27-38.xml"));
        BufferedImage image = ImageIO.read(new File("photo_2026-02-13_14-27-38.jpg"));
        int w = image.getWidth();
        int h = image.getHeight();
        int[] argb = image.getRGB(0, 0, w, h, null, 0, w);

        float[] minArea = {0.6f, 0.8f, 1.0f};
        float[] minFill = {0.18f, 0.22f, 0.26f};
        float[] minCircularity = {0.24f, 0.32f, 0.40f};
        float[] analytical = {0.55f, 0.70f, 0.85f};

        List<Candidate> all = new ArrayList<Candidate>();
        OpenCvScoreProcessor processor = new OpenCvScoreProcessor();

        for (float aMin : minArea) {
            for (float fill : minFill) {
                for (float circularity : minCircularity) {
                    for (float anal : analytical) {
                        OpenCvScoreProcessor.ProcessingOptions options = new OpenCvScoreProcessor.ProcessingOptions(
                                7, 3, 0.5f,
                                false, false,
                                aMin, 4.0f,
                                fill, 0.9f,
                                circularity,
                                false,
                                anal,
                                null,
                                true);

                        OpenCvScoreProcessor.ProcessingResult result = processor.processArgb(w, h, argb, "first-staff-sweep", options);
                        Candidate c = evaluate(result, reference, options);
                        all.add(c);
                    }
                }
            }
        }

        Collections.sort(all, new Comparator<Candidate>() {
            @Override
            public int compare(Candidate a, Candidate b) {
                if (a.pitchOk != b.pitchOk) return b.pitchOk - a.pitchOk;
                if (a.durationOk != b.durationOk) return b.durationOk - a.durationOk;
                return Float.compare(b.durationPrecision, a.durationPrecision);
            }
        });

        System.out.println("Tried combinations=" + all.size());
        for (int i = 0; i < Math.min(10, all.size()); i++) {
            System.out.println((i + 1) + ") " + all.get(i).summary());
        }

        Candidate baseline = evaluate(processor.processArgb(
                        w,
                        h,
                        argb,
                        "first-staff-baseline",
                        new OpenCvScoreProcessor.ProcessingOptions(7, 3, 0.5f, false, false, 0.6f, 4.0f, 0.18f, 0.9f, 0.32f, false, 0.55f, null, true)),
                reference,
                new OpenCvScoreProcessor.ProcessingOptions(7, 3, 0.5f, false, false, 0.6f, 4.0f, 0.18f, 0.9f, 0.32f, false, 0.55f, null, true)
        );

        System.out.println("Baseline (current tuned): " + baseline.summary());
    }

    private static Candidate evaluate(OpenCvScoreProcessor.ProcessingResult result,
                                      Ref reference,
                                      OpenCvScoreProcessor.ProcessingOptions options) {
        List<NoteEvent> top = new ArrayList<NoteEvent>();
        for (NoteEvent n : result.piece.notes) {
            if (nearestCorridorIndex(n.y, result.staffCorridors) == 0) {
                top.add(n);
            }
        }
        Collections.sort(top, new Comparator<NoteEvent>() {
            @Override
            public int compare(NoteEvent a, NoteEvent b) {
                return Float.compare(a.x, b.x);
            }
        });

        int cmp = Math.min(reference.pitch.size(), top.size());
        int pitchOk = 0;
        int durationOk = 0;
        for (int i = 0; i < cmp; i++) {
            String rp = top.get(i).noteName + top.get(i).octave;
            String rd = normalizeDuration(top.get(i).duration);
            if (reference.pitch.get(i).equals(rp)) pitchOk++;
            if (reference.duration.get(i).equals(rd)) durationOk++;
        }

        List<String> expectedDur = new ArrayList<String>(reference.duration);
        List<String> actualDur = new ArrayList<String>();
        for (NoteEvent n : result.piece.notes) {
            String d = normalizeDuration(n.duration);
            if ("half".equals(d) || "quarter".equals(d) || "eighth".equals(d) || "sixteenth".equals(d)) {
                actualDur.add(d);
            }
        }
        int lcs = lcs(expectedDur, actualDur);

        Candidate c = new Candidate();
        c.options = options;
        c.pitchOk = pitchOk;
        c.durationOk = durationOk;
        c.topStaffCount = top.size();
        c.totalCount = result.piece.notes.size();
        c.durationPrecision = actualDur.isEmpty() ? 0f : lcs / (float) actualDur.size();
        return c;
    }

    private static int lcs(List<String> a, List<String> b) {
        int[][] dp = new int[a.size() + 1][b.size() + 1];
        for (int i = 1; i <= a.size(); i++) {
            for (int j = 1; j <= b.size(); j++) {
                if (a.get(i - 1).equals(b.get(j - 1))) dp[i][j] = dp[i - 1][j - 1] + 1;
                else dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
            }
        }
        return dp[a.size()][b.size()];
    }

    private static int nearestCorridorIndex(float y, List<OpenCvScoreProcessor.StaffCorridor> corridors) {
        if (corridors == null || corridors.isEmpty()) return -1;
        int best = 0;
        float bestDist = Float.MAX_VALUE;
        for (int i = 0; i < corridors.size(); i++) {
            OpenCvScoreProcessor.StaffCorridor c = corridors.get(i);
            float d = Math.abs(y - (c.top + c.bottom) * 0.5f);
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }

    private static Ref parseFirstSystemReference(File xmlFile) throws Exception {
        Ref out = new Ref();
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        Document doc = dbf.newDocumentBuilder().parse(xmlFile);
        NodeList measures = doc.getElementsByTagName("measure");
        for (int i = 0; i < measures.getLength(); i++) {
            Element m = (Element) measures.item(i);
            int num;
            try {
                num = Integer.parseInt(m.getAttribute("number"));
            } catch (Exception e) {
                continue;
            }
            if (num < 4 || num > 7) continue;

            NodeList notes = m.getElementsByTagName("note");
            for (int j = 0; j < notes.getLength(); j++) {
                Element note = (Element) notes.item(j);
                if (note.getElementsByTagName("rest").getLength() > 0) continue;
                NodeList pitchNodes = note.getElementsByTagName("pitch");
                if (pitchNodes.getLength() == 0) continue;

                Element pitch = (Element) pitchNodes.item(0);
                String step = textOfFirst(pitch, "step");
                String alter = textOfFirst(pitch, "alter");
                String octave = textOfFirst(pitch, "octave");
                if (step == null || octave == null) continue;

                String name = step;
                if ("1".equals(alter)) name += "#";
                if ("-1".equals(alter)) name += "b";
                out.pitch.add(name + octave);
                out.duration.add(normalizeDuration(textOfFirst(note, "type")));
            }
        }
        return out;
    }

    private static String textOfFirst(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        if (list.getLength() == 0) return null;
        return list.item(0).getTextContent();
    }

    private static String normalizeDuration(String d) {
        if (d == null || d.length() == 0) return "quarter";
        if ("16th".equals(d)) return "sixteenth";
        return d;
    }
}
