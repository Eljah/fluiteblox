package tatar.eljah.recorder;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class FirstStaffMismatchAnalysis {
    private static class NoteLite {
        final String pitch;
        final String duration;

        NoteLite(String pitch, String duration) {
            this.pitch = pitch;
            this.duration = normalize(duration);
        }
    }

    public static void main(String[] args) throws Exception {
        File photo = new File("photo_2026-02-13_14-27-38.jpg");
        File xml = new File("Free-trial-photo-2026-02-13-14-27-38.xml");
        List<NoteLite> expected = parseFirstSystemXmlNotes(xml);

        BufferedImage image = ImageIO.read(photo);
        int width = image.getWidth();
        int height = image.getHeight();
        int[] argb = image.getRGB(0, 0, width, height, null, 0, width);

        OpenCvScoreProcessor.ProcessingResult result = new OpenCvScoreProcessor().processArgb(width, height, argb, "first-staff-analysis");
        List<NoteEvent> corridor0 = new ArrayList<NoteEvent>();
        for (NoteEvent n : result.piece.notes) {
            if (nearestCorridorIndex(n.y, result.staffCorridors) == 0) {
                corridor0.add(n);
            }
        }
        corridor0.sort(new Comparator<NoteEvent>() {
            @Override
            public int compare(NoteEvent a, NoteEvent b) {
                return Float.compare(a.x, b.x);
            }
        });

        int n = Math.min(expected.size(), corridor0.size());
        int pitchOk = 0;
        int durationOk = 0;
        System.out.println("First staff mismatch analysis (XML measures 4-7 vs recognized top corridor first notes):");
        for (int i = 0; i < n; i++) {
            NoteLite e = expected.get(i);
            NoteEvent a = corridor0.get(i);
            String ap = a.noteName + a.octave;
            String ad = normalize(a.duration);
            boolean pOk = e.pitch.equals(ap);
            boolean dOk = e.duration.equals(ad);
            if (pOk) pitchOk++;
            if (dOk) durationOk++;
            System.out.println(String.format("%02d exp %-3s %-8s | rec %-3s %-8s | pitch:%s dur:%s",
                    i + 1,
                    e.pitch,
                    e.duration,
                    ap,
                    ad,
                    pOk ? "OK" : "BAD",
                    dOk ? "OK" : "BAD"));
        }

        System.out.println("SUMMARY expected=" + expected.size()
                + " corridor0=" + corridor0.size()
                + " compared=" + n
                + " pitch_ok=" + pitchOk
                + " duration_ok=" + durationOk);
    }

    private static String normalize(String d) {
        if (d == null || d.length() == 0) return "quarter";
        if ("16th".equals(d)) return "sixteenth";
        return d;
    }

    private static int nearestCorridorIndex(float y, List<OpenCvScoreProcessor.StaffCorridor> corridors) {
        if (corridors == null || corridors.isEmpty()) return -1;
        int best = 0;
        float bestDist = Float.MAX_VALUE;
        for (int i = 0; i < corridors.size(); i++) {
            OpenCvScoreProcessor.StaffCorridor c = corridors.get(i);
            float cy = (c.top + c.bottom) * 0.5f;
            float d = Math.abs(y - cy);
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }

    private static List<NoteLite> parseFirstSystemXmlNotes(File xmlFile) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        Document doc = dbf.newDocumentBuilder().parse(xmlFile);
        NodeList measureNodes = doc.getElementsByTagName("measure");
        List<NoteLite> out = new ArrayList<NoteLite>();
        for (int i = 0; i < measureNodes.getLength(); i++) {
            Element measure = (Element) measureNodes.item(i);
            String numText = measure.getAttribute("number");
            int number;
            try {
                number = Integer.parseInt(numText);
            } catch (NumberFormatException ignored) {
                continue;
            }
            if (number < 4 || number > 7) continue;
            NodeList noteNodes = measure.getElementsByTagName("note");
            for (int j = 0; j < noteNodes.getLength(); j++) {
                Element note = (Element) noteNodes.item(j);
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
                out.add(new NoteLite(name + octave, textOfFirst(note, "type")));
            }
        }
        return out;
    }

    private static String textOfFirst(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        if (list.getLength() == 0) return null;
        return list.item(0).getTextContent();
    }
}
