package tatar.eljah.recorder;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class PhotoRecognitionReferenceRegressionTest {

    public static void main(String[] args) throws Exception {
        File photo = new File("photo_2026-02-13_14-27-38.jpg");
        File xml = new File("Free-trial-photo-2026-02-13-14-27-38.xml");
        File midi = new File("Free-trial-photo-2026-02-13-14-27-38.mid");

        if (!photo.exists() || !xml.exists() || !midi.exists()) {
            throw new AssertionError("Required regression files are missing in repository root");
        }

        BufferedImage image = ImageIO.read(photo);
        if (image == null) {
            throw new AssertionError("Unable to decode photo file");
        }

        int width = image.getWidth();
        int height = image.getHeight();
        int[] argb = image.getRGB(0, 0, width, height, null, 0, width);

        OpenCvScoreProcessor processor = new OpenCvScoreProcessor();
        OpenCvScoreProcessor.ProcessingResult result = processor.processArgb(
                width,
                height,
                argb,
                "regression-photo",
                OpenCvScoreProcessor.ProcessingOptions.defaults().withRequireOpenCv(true));
        List<NoteEvent> recognized = result.piece.notes;

        if (!"opencv".equals(result.processingMode)) {
            throw new AssertionError("Expected OpenCV processing mode, got " + result.processingMode);
        }
        if (recognized == null || recognized.isEmpty()) {
            throw new AssertionError("Recognition returned no notes");
        }

        List<NoteEvent> expectedFromXml = parseXmlNotes(xml);
        List<Integer> expectedMidiOn = parseMidiNoteOns(Files.readAllBytes(midi.toPath()));
        List<Integer> actualMidiOn = new ArrayList<Integer>();
        for (NoteEvent n : recognized) {
            actualMidiOn.add(MusicNotation.midiFor(n.noteName, n.octave));
        }

        int lcsXml = lcsLength(toMidi(expectedFromXml), actualMidiOn);
        int lcsMidi = lcsLength(expectedMidiOn, actualMidiOn);
        int coverageXml = percent(lcsXml, expectedFromXml.size());
        int precisionXml = percent(lcsXml, recognized.size());
        int coverageMidi = percent(lcsMidi, expectedMidiOn.size());
        int precisionMidi = percent(lcsMidi, recognized.size());

        System.out.println("Reference regression (OpenCV): expectedXml=" + expectedFromXml.size()
                + ", recognized=" + recognized.size()
                + ", coverage(xml)=" + coverageXml + "%"
                + ", precision(xml)=" + precisionXml + "%"
                + ", coverage(midi)=" + coverageMidi + "%"
                + ", precision(midi)=" + precisionMidi + "%");
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

    private static List<Integer> parseMidiNoteOns(byte[] bytes) throws IOException {
        MidiReader r = new MidiReader(bytes);
        List<Integer> out = new ArrayList<Integer>();

        if (r.readInt() != 0x4d546864) throw new IOException("Invalid MIDI header");
        int headerLen = r.readInt();
        r.skip(headerLen);

        while (!r.eof()) {
            int chunk = r.readInt();
            int len = r.readInt();
            if (chunk != 0x4d54726b) {
                r.skip(len);
                continue;
            }
            int trackEnd = r.pos + len;
            int runningStatus = 0;
            while (r.pos < trackEnd) {
                r.readVarLen();
                int statusOrData = r.readUnsignedByte();
                int status;
                if ((statusOrData & 0x80) != 0) {
                    status = statusOrData;
                    if (status == 0xFF) {
                        runningStatus = 0;
                        r.readUnsignedByte();
                        int metaLen = r.readVarLen();
                        r.skip(metaLen);
                        continue;
                    }
                    if (status == 0xF0 || status == 0xF7) {
                        runningStatus = 0;
                        int syxLen = r.readVarLen();
                        r.skip(syxLen);
                        continue;
                    }
                    runningStatus = status;
                    int data1 = r.readUnsignedByte();
                    int type = status & 0xF0;
                    if (type == 0x90) {
                        int data2 = r.readUnsignedByte();
                        if (data2 > 0) out.add(data1);
                    } else if (type == 0x80 || type == 0xA0 || type == 0xB0 || type == 0xE0) {
                        r.readUnsignedByte();
                    }
                } else {
                    if (runningStatus == 0) throw new IOException("Invalid running status");
                    status = runningStatus;
                    int type = status & 0xF0;
                    if (type == 0x90) {
                        int data2 = r.readUnsignedByte();
                        if (data2 > 0) out.add(statusOrData);
                    } else if (type == 0x80 || type == 0xA0 || type == 0xB0 || type == 0xE0) {
                        r.readUnsignedByte();
                    }
                }
            }
        }
        return out;
    }

    private static List<Integer> toMidi(List<NoteEvent> notes) {
        List<Integer> midi = new ArrayList<Integer>();
        for (NoteEvent n : notes) {
            midi.add(MusicNotation.midiFor(n.noteName, n.octave));
        }
        return midi;
    }

    private static int percent(int num, int den) {
        if (den <= 0) return 0;
        return Math.round((100f * num) / (float) den);
    }

    private static int lcsLength(List<Integer> a, List<Integer> b) {
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

    private static class MidiReader {
        private final byte[] data;
        private int pos;

        MidiReader(byte[] data) {
            this.data = data;
        }

        boolean eof() {
            return pos >= data.length;
        }

        int readUnsignedByte() throws IOException {
            if (pos >= data.length) throw new IOException("Unexpected EOF");
            return data[pos++] & 0xFF;
        }

        int readInt() throws IOException {
            return (readUnsignedByte() << 24)
                    | (readUnsignedByte() << 16)
                    | (readUnsignedByte() << 8)
                    | readUnsignedByte();
        }

        int readVarLen() throws IOException {
            int value = 0;
            int b;
            do {
                b = readUnsignedByte();
                value = (value << 7) | (b & 0x7F);
            } while ((b & 0x80) != 0);
            return value;
        }

        void skip(int n) throws IOException {
            if (n < 0 || pos + n > data.length) throw new IOException("Invalid skip");
            pos += n;
        }
    }
}
