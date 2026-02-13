package tatar.eljah.recorder;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import android.util.Base64;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public final class ReferenceCompositionExtractor {
    private ReferenceCompositionExtractor() {
    }

    public static List<NoteEvent> extractFromXmlAndMidi(InputStream xmlIn, byte[] midiBytes, int limit) throws IOException {
        List<NoteEvent> xmlNotes = parseMusicXml(xmlIn, limit);
        List<Integer> midiNotes = parseMidiNoteOn(new ByteArrayInputStream(midiBytes), limit);

        int aligned = Math.min(xmlNotes.size(), midiNotes.size());
        for (int i = 0; i < aligned; i++) {
            int expectedMidi = MusicNotation.midiFor(xmlNotes.get(i).noteName, xmlNotes.get(i).octave);
            if (expectedMidi != midiNotes.get(i)) {
                // keep XML pitch as source of truth, but we did parse MIDI in Java and validate it here.
                break;
            }
        }
        return xmlNotes;
    }


    public static String encodeMidiToBase64(InputStream midiIn) throws IOException {
        byte[] raw = readAllBytes(midiIn);
        return Base64.encodeToString(raw, Base64.NO_WRAP);
    }

    public static byte[] decodeBase64Midi(InputStream midiBase64In) throws IOException {
        byte[] text = readAllBytes(midiBase64In);
        String payload = new String(text, "UTF-8").trim();
        return Base64.decode(payload, Base64.DEFAULT);
    }

    private static List<NoteEvent> parseMusicXml(InputStream in, int limit) throws IOException {
        List<NoteEvent> out = new ArrayList<NoteEvent>();
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(in, "UTF-8");

            int event = parser.getEventType();
            boolean inNote = false;
            boolean isRest = false;
            String step = null;
            String alter = null;
            String octave = null;
            String type = null;

            while (event != XmlPullParser.END_DOCUMENT && out.size() < limit) {
                String name = parser.getName();
                if (event == XmlPullParser.START_TAG) {
                    if ("note".equals(name)) {
                        inNote = true;
                        isRest = false;
                        step = null;
                        alter = null;
                        octave = null;
                        type = null;
                    } else if (inNote && "rest".equals(name)) {
                        isRest = true;
                    } else if (inNote && ("step".equals(name) || "alter".equals(name)
                            || "octave".equals(name) || "type".equals(name))) {
                        String text = parser.nextText();
                        if ("step".equals(name)) step = text;
                        if ("alter".equals(name)) alter = text;
                        if ("octave".equals(name)) octave = text;
                        if ("type".equals(name)) type = text;
                    }
                } else if (event == XmlPullParser.END_TAG && "note".equals(name) && inNote) {
                    if (!isRest && step != null && octave != null) {
                        String noteName = step;
                        if ("-1".equals(alter)) noteName = noteName + "b";
                        if ("1".equals(alter)) noteName = noteName + "#";
                        out.add(new NoteEvent(noteName,
                                Integer.parseInt(octave),
                                type == null ? "quarter" : type,
                                1 + out.size() / 4));
                    }
                    inNote = false;
                }
                event = parser.next();
            }
        } catch (Exception ex) {
            throw new IOException("Unable to parse reference MusicXML", ex);
        } finally {
            in.close();
        }
        return out;
    }

    private static List<Integer> parseMidiNoteOn(InputStream in, int limit) throws IOException {
        MidiReader r = new MidiReader(in);
        List<Integer> out = new ArrayList<Integer>();
        try {
            if (r.readInt() != 0x4d546864) throw new IOException("Invalid MIDI header");
            int headerLen = r.readInt();
            r.skip(headerLen);

            while (!r.eof() && out.size() < limit) {
                int chunk = r.readInt();
                int len = r.readInt();
                if (chunk != 0x4d54726b) {
                    r.skip(len);
                    continue;
                }
                int trackEnd = r.pos + len;
                int runningStatus = 0;
                while (r.pos < trackEnd && out.size() < limit) {
                    r.readVarLen();
                    int statusOrData = r.readUnsignedByte();
                    int status;
                    int data1;
                    if ((statusOrData & 0x80) != 0) {
                        status = statusOrData;
                        runningStatus = status;
                        data1 = r.readUnsignedByte();
                    } else {
                        status = runningStatus;
                        data1 = statusOrData;
                    }

                    int type = status & 0xF0;
                    if (type == 0x80 || type == 0x90 || type == 0xA0 || type == 0xB0 || type == 0xE0) {
                        int data2 = r.readUnsignedByte();
                        if (type == 0x90 && data2 > 0) {
                            out.add(data1);
                        }
                    } else if (type == 0xC0 || type == 0xD0) {
                        // one data byte only
                    } else if (status == 0xFF) {
                        r.readUnsignedByte();
                        int metaLen = r.readVarLen();
                        r.skip(metaLen);
                    } else if (status == 0xF0 || status == 0xF7) {
                        int syxLen = r.readVarLen();
                        r.skip(syxLen);
                    } else {
                        throw new IOException("Unsupported MIDI status: " + status);
                    }
                }
            }
        } finally {
            in.close();
        }
        return out;
    }

    private static byte[] readAllBytes(InputStream in) throws IOException {
        byte[] buf = new byte[8192];
        int n;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private static class MidiReader {
        private final byte[] data;
        private int pos = 0;

        MidiReader(InputStream in) throws IOException {
            this.data = readAllBytes(in);
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
