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

public class StemDurationRecognitionRegressionTest {
    public static void main(String[] args) throws Exception {
        File photo = new File("photo_2026-02-13_14-27-38.jpg");
        File xml = new File("Free-trial-photo-2026-02-13-14-27-38.xml");
        File midi = new File("Free-trial-photo-2026-02-13-14-27-38.mid");

        if (!photo.exists() || !xml.exists() || !midi.exists()) {
            throw new AssertionError("Required regression files are missing in repository root");
        }

        List<NoteEvent> expectedXml = parseXmlNotes(xml);
        List<String> expectedMidiDurations = parseMidiDurations(Files.readAllBytes(midi.toPath()));
        if (expectedMidiDurations.size() < expectedXml.size()) {
            throw new AssertionError("MIDI parsed durations are shorter than XML note list");
        }

        int crossChecked = 0;
        for (int i = 0; i < expectedXml.size(); i++) {
            if (isStemDuration(expectedXml.get(i).duration)) {
                String midiDur = expectedMidiDurations.get(i);
                if (!durationEquivalent(expectedXml.get(i).duration, midiDur)) {
                    throw new AssertionError("XML vs MIDI duration mismatch at index " + i
                            + " xml=" + expectedXml.get(i).duration + " midi=" + midiDur);
                }
                crossChecked++;
            }
        }

        BufferedImage image = ImageIO.read(photo);
        if (image == null) throw new AssertionError("Unable to decode photo file");
        int w = image.getWidth();
        int h = image.getHeight();
        int[] argb = image.getRGB(0, 0, w, h, null, 0, w);

        OpenCvScoreProcessor processor = new OpenCvScoreProcessor();
        OpenCvScoreProcessor.ProcessingResult result = processor.processArgb(w, h, argb, "stem-duration-regression");
        List<NoteEvent> actual = result.piece.notes;

        List<String> expectedStem = new ArrayList<String>();
        for (NoteEvent n : expectedXml) {
            if (isStemDuration(n.duration)) expectedStem.add(normalizeDuration(n.duration));
        }
        List<String> actualStem = new ArrayList<String>();
        for (NoteEvent n : actual) {
            if (isStemDuration(n.duration)) actualStem.add(normalizeDuration(n.duration));
        }

        if (actualStem.isEmpty()) {
            throw new AssertionError("Recognizer returned no stem-based durations");
        }

        int lcs = lcsLength(expectedStem, actualStem);
        float recall = expectedStem.isEmpty() ? 1f : lcs / (float) expectedStem.size();
        float precision = actualStem.isEmpty() ? 1f : lcs / (float) actualStem.size();

        if (recall < 0.10f) {
            throw new AssertionError("Stem duration recall is critically low: lcs=" + lcs
                    + " expectedStem=" + expectedStem.size() + " actualStem=" + actualStem.size()
                    + " recall=" + recall + " precision=" + precision);
        }

        System.out.println("Stem duration regression passed: lcs=" + lcs
                + ", expectedStem=" + expectedStem.size()
                + ", actualStem=" + actualStem.size()
                + ", recall=" + recall
                + ", precision=" + precision
                + ", xml-midi cross-check=" + crossChecked);
    }

    private static int lcsLength(List<String> a, List<String> b) {
        int n = a.size();
        int m = b.size();
        int[][] dp = new int[n + 1][m + 1];
        for (int i = 1; i <= n; i++) {
            String ai = a.get(i - 1);
            for (int j = 1; j <= m; j++) {
                if (ai.equals(b.get(j - 1))) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }
        return dp[n][m];
    }

    private static boolean isStemDuration(String d) {
        return "half".equals(d) || "quarter".equals(d) || "eighth".equals(d) || "sixteenth".equals(d) || "16th".equals(d);
    }

    private static boolean durationEquivalent(String a, String b) {
        return normalizeDuration(a).equals(normalizeDuration(b));
    }

    private static String normalizeDuration(String d) {
        if (d == null) return "quarter";
        if ("16th".equals(d)) return "sixteenth";
        return d;
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
            if (note.getElementsByTagName("rest").getLength() > 0) continue;

            NodeList pitchNodes = note.getElementsByTagName("pitch");
            if (pitchNodes.getLength() == 0) continue;
            Element pitch = (Element) pitchNodes.item(0);

            String step = textOfFirst(pitch, "step");
            String alter = textOfFirst(pitch, "alter");
            String octaveText = textOfFirst(pitch, "octave");
            String duration = textOfFirst(note, "type");
            if (step == null || octaveText == null) continue;

            String name = step;
            if ("1".equals(alter)) name += "#";
            if ("-1".equals(alter)) name += "b";
            if (duration == null || duration.length() == 0) duration = "quarter";
            out.add(new NoteEvent(name, Integer.parseInt(octaveText.trim()), normalizeDuration(duration), 1 + out.size() / 4));
        }
        return out;
    }

    private static String textOfFirst(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        if (list.getLength() == 0) return null;
        return list.item(0).getTextContent();
    }

    private static List<String> parseMidiDurations(byte[] bytes) throws IOException {
        MidiReader r = new MidiReader(bytes);
        if (r.readInt() != 0x4d546864) throw new IOException("Invalid MIDI header");
        int headerLen = r.readInt();
        int format = r.readU16();
        int trackCount = r.readU16();
        int division = r.readU16();
        if (headerLen > 6) r.skip(headerLen - 6);

        int ticksPerQuarter = division & 0x7FFF;
        if ((division & 0x8000) != 0) {
            throw new IOException("SMPTE time division is not supported");
        }

        List<NoteSpan> spans = new ArrayList<NoteSpan>();
        for (int t = 0; t < trackCount && !r.eof(); t++) {
            int chunk = r.readInt();
            int len = r.readInt();
            if (chunk != 0x4d54726b) {
                r.skip(len);
                continue;
            }
            int end = r.pos + len;
            long tick = 0L;
            int runningStatus = 0;
            java.util.HashMap<Integer, Long> activeOn = new java.util.HashMap<Integer, Long>();
            while (r.pos < end) {
                tick += r.readVarLen();
                int b = r.readUnsignedByte();
                int status;
                int data1;
                if ((b & 0x80) != 0) {
                    status = b;
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
                    data1 = r.readUnsignedByte();
                } else {
                    if (runningStatus == 0) throw new IOException("Invalid running status");
                    status = runningStatus;
                    data1 = b;
                }

                int type = status & 0xF0;
                if (type == 0x90) {
                    int vel = r.readUnsignedByte();
                    if (vel > 0) {
                        activeOn.put(data1, tick);
                    } else {
                        finishSpan(spans, activeOn, data1, tick);
                    }
                } else if (type == 0x80) {
                    r.readUnsignedByte();
                    finishSpan(spans, activeOn, data1, tick);
                } else if (type == 0xA0 || type == 0xB0 || type == 0xE0) {
                    r.readUnsignedByte();
                } else if (type == 0xC0 || type == 0xD0) {
                    // one data byte already read
                }
            }
        }

        if (format == 1 && spans.isEmpty()) {
            throw new IOException("No MIDI note spans parsed");
        }

        List<String> out = new ArrayList<String>();
        long q = Math.max(1, ticksPerQuarter);
        for (NoteSpan s : spans) {
            out.add(classifyByTicks(s.durationTicks, q));
        }
        return out;
    }

    private static void finishSpan(List<NoteSpan> spans, java.util.HashMap<Integer, Long> activeOn, int note, long offTick) {
        Long onTick = activeOn.remove(note);
        if (onTick == null) return;
        long dur = Math.max(1L, offTick - onTick);
        spans.add(new NoteSpan(note, onTick, dur));
    }

    private static String classifyByTicks(long ticks, long quarter) {
        long half = quarter * 2;
        long eighth = Math.max(1, quarter / 2);
        long sixteenth = Math.max(1, quarter / 4);

        long dQuarter = Math.abs(ticks - quarter);
        long dHalf = Math.abs(ticks - half);
        long dEighth = Math.abs(ticks - eighth);
        long dSixteenth = Math.abs(ticks - sixteenth);

        long best = dQuarter;
        String label = "quarter";
        if (dHalf < best) {
            best = dHalf;
            label = "half";
        }
        if (dEighth < best) {
            best = dEighth;
            label = "eighth";
        }
        if (dSixteenth < best) {
            label = "sixteenth";
        }
        return label;
    }

    private static class NoteSpan {
        final int midi;
        final long onTick;
        final long durationTicks;

        NoteSpan(int midi, long onTick, long durationTicks) {
            this.midi = midi;
            this.onTick = onTick;
            this.durationTicks = durationTicks;
        }
    }

    private static class MidiReader {
        private final byte[] data;
        private int pos;

        MidiReader(byte[] data) { this.data = data; }

        boolean eof() { return pos >= data.length; }

        int readUnsignedByte() throws IOException {
            if (pos >= data.length) throw new IOException("Unexpected EOF");
            return data[pos++] & 0xFF;
        }

        int readU16() throws IOException {
            return (readUnsignedByte() << 8) | readUnsignedByte();
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
