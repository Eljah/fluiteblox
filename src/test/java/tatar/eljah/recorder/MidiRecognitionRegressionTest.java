package tatar.eljah.recorder;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MidiRecognitionRegressionTest {
    private static final int SAMPLE_RATE = 22050;
    private static final int FRAME_SIZE = 1024;
    private static final int HOP_SIZE = 512;
    private static final int MIN_MATCH_HOLD_MS = 110;
    private static final float MIN_MATCH_HOLD_DURATION_FRACTION = 0.45f;

    private static class RecognitionResult {
        int recognizedCount;
        boolean[] matched;
        boolean[] mismatch;
    }

    public void synthesizedReferenceScoreShouldRecognizeAllNotes() throws Exception {
        List<NoteEvent> notes = parseReferenceScore(new File("src/main/assets/reference_score.xml"));
        if (notes.isEmpty()) {
            throw new AssertionError("reference score must not be empty");
        }

        short[] pcm = synthesizeScore(notes);
        File outWav = new File("target/reference_score_synth.wav");
        writeWav(pcm, SAMPLE_RATE, outWav);

        RecognitionResult result = runRecognition(notes, pcm);
        if (result.recognizedCount != notes.size()) {
            throw new AssertionError("All notes should be recognized sequentially: " + result.recognizedCount + "/" + notes.size());
        }
        for (int i = 0; i < notes.size(); i++) {
            if (!result.matched[i]) {
                throw new AssertionError("Note " + (i + 1) + " is not marked as matched (green)");
            }
            if (result.mismatch[i]) {
                throw new AssertionError("Note " + (i + 1) + " remains mismatched (not green)");
            }
        }
    }

    private List<NoteEvent> parseReferenceScore(File xmlFile) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        Document doc = dbf.newDocumentBuilder().parse(xmlFile);
        NodeList noteNodes = doc.getElementsByTagName("note");
        List<NoteEvent> notes = new ArrayList<NoteEvent>();
        int measure = 0;
        for (int i = 0; i < noteNodes.getLength(); i++) {
            Element noteElement = (Element) noteNodes.item(i);
            NodeList pitchNodes = noteElement.getElementsByTagName("pitch");
            if (pitchNodes.getLength() == 0) {
                continue;
            }
            Element pitch = (Element) pitchNodes.item(0);
            String step = textOfFirst(pitch, "step");
            if (step == null || step.isEmpty()) {
                continue;
            }
            String octaveText = textOfFirst(pitch, "octave");
            if (octaveText == null || octaveText.isEmpty()) {
                continue;
            }
            String alter = textOfFirst(pitch, "alter");
            String noteName = step + ("1".equals(alter) ? "#" : "");
            int octave = Integer.parseInt(octaveText.trim());

            String duration = textOfFirst(noteElement, "type");
            if (duration == null || duration.isEmpty()) {
                duration = "quarter";
            }
            notes.add(new NoteEvent(noteName, octave, duration, measure));
        }
        return notes;
    }

    private String textOfFirst(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        if (list.getLength() == 0) {
            return null;
        }
        return list.item(0).getTextContent();
    }

    private short[] synthesizeScore(List<NoteEvent> notes) {
        List<Short> out = new ArrayList<Short>();
        for (NoteEvent note : notes) {
            int ms = durationMs(note.duration);
            int totalSamples = SAMPLE_RATE * ms / 1000;
            int fadeSamples = Math.min((int) (SAMPLE_RATE * 0.008f), totalSamples / 2);
            int midi = MusicNotation.midiFor(note.noteName, note.octave);
            double freq = 440.0 * Math.pow(2.0, (midi - 69) / 12.0);

            for (int i = 0; i < totalSamples; i++) {
                double t = i / (double) SAMPLE_RATE;
                float env = 1f;
                if (fadeSamples > 0) {
                    if (i < fadeSamples) {
                        env = i / (float) fadeSamples;
                    }
                    int toEnd = totalSamples - i;
                    if (toEnd <= fadeSamples) {
                        env = Math.min(env, Math.max(0f, toEnd / (float) fadeSamples));
                    }
                }
                short sample = (short) (Math.sin(2.0 * Math.PI * freq * t) * 12000 * env);
                out.add(sample);
            }
        }

        short[] pcm = new short[out.size()];
        for (int i = 0; i < out.size(); i++) {
            pcm[i] = out.get(i);
        }
        return pcm;
    }

    private RecognitionResult runRecognition(List<NoteEvent> notes, short[] pcm) {
        RecorderNoteMapper mapper = new RecorderNoteMapper();
        int pointer = 0;
        long lastMatchAcceptedAtMs = -1L;
        RecognitionResult result = new RecognitionResult();
        result.matched = new boolean[notes.size()];
        result.mismatch = new boolean[notes.size()];

        for (int pos = 0; pos + FRAME_SIZE <= pcm.length && pointer < notes.size(); pos += HOP_SIZE) {
            NoteEvent expected = notes.get(pointer);
            float expectedHz = expectedFrequencyFor(expected, mapper);
            float detectedHz = estimatePitch(pcm, pos, FRAME_SIZE, SAMPLE_RATE);
            detectedHz = normalizeDetectedPitch(detectedHz, expectedHz, mapper);
            String detected = mapper.fromFrequency(detectedHz);
            if (!samePitch(detected, expected.fullName())) {
                result.mismatch[pointer] = true;
                continue;
            }

            long nowMs = (pos * 1000L) / SAMPLE_RATE;
            long minHoldMs = Math.max(MIN_MATCH_HOLD_MS,
                    (long) (durationMs(expected.duration) * MIN_MATCH_HOLD_DURATION_FRACTION));
            if (lastMatchAcceptedAtMs >= 0L && nowMs - lastMatchAcceptedAtMs < minHoldMs) {
                continue;
            }
            lastMatchAcceptedAtMs = nowMs;
            result.mismatch[pointer] = false;
            result.matched[pointer] = true;
            pointer++;
        }

        result.recognizedCount = pointer;
        return result;
    }

    private float expectedFrequencyFor(NoteEvent note, RecorderNoteMapper mapper) {
        float mapped = mapper.frequencyFor(note.fullName());
        if (mapped > 0f) {
            return mapped;
        }
        int midi = MusicNotation.midiFor(note.noteName, note.octave);
        return (float) (440.0 * Math.pow(2.0, (midi - 69) / 12.0));
    }

    private float normalizeDetectedPitch(float detectedHz, float expectedFrequency, RecorderNoteMapper mapper) {
        if (detectedHz <= 0f) {
            return detectedHz;
        }

        float halvedHz = detectedHz / 2f;
        float minMappedHz = mapper.frequencyFor("D4");
        float maxMappedHz = mapper.frequencyFor("A6");

        boolean directInRange = detectedHz >= minMappedHz && detectedHz <= maxMappedHz;
        boolean halvedInRange = halvedHz >= minMappedHz && halvedHz <= maxMappedHz;

        if (!directInRange && halvedInRange) {
            return halvedHz;
        }

        if (expectedFrequency <= 0f) {
            return detectedHz;
        }

        float directDiff = Math.abs(detectedHz - expectedFrequency);
        float halvedDiff = Math.abs(halvedHz - expectedFrequency);

        if (halvedInRange && halvedDiff < directDiff) {
            return halvedHz;
        }

        return detectedHz;
    }

    private float estimatePitch(short[] pcm, int start, int length, int sampleRate) {
        if (length < 64 || sampleRate <= 0) {
            return 0f;
        }

        double mean = 0d;
        for (int i = 0; i < length; i++) {
            mean += pcm[start + i];
        }
        mean /= length;

        double rms = 0d;
        for (int i = 0; i < length; i++) {
            double centered = pcm[start + i] - mean;
            rms += centered * centered;
        }
        rms = Math.sqrt(rms / length);
        if (rms < 250d) {
            return 0f;
        }

        int minLag = Math.max(4, sampleRate / 2600);
        int maxLag = Math.min(length / 2, sampleRate / 120);
        if (maxLag <= minLag) {
            return 0f;
        }

        double[] corr = new double[maxLag + 1];
        for (int lag = minLag; lag <= maxLag; lag++) {
            double sum = 0d;
            double energyA = 0d;
            double energyB = 0d;
            int limit = length - lag;
            for (int i = 0; i < limit; i++) {
                double a = pcm[start + i] - mean;
                double b = pcm[start + i + lag] - mean;
                sum += a * b;
                energyA += a * a;
                energyB += b * b;
            }
            if (energyA <= 0d || energyB <= 0d) {
                continue;
            }
            corr[lag] = sum / Math.sqrt(energyA * energyB);
        }

        int bestLag = -1;
        final double minCorr = 0.55d;
        for (int lag = minLag + 1; lag < maxLag; lag++) {
            double normalized = corr[lag];
            if (normalized < minCorr) {
                continue;
            }
            if (normalized > corr[lag - 1] && normalized >= corr[lag + 1]) {
                bestLag = lag;
                break;
            }
        }

        if (bestLag <= 0) {
            return 0f;
        }

        float frequency = sampleRate / (float) bestLag;
        if (frequency < 120f || frequency > 2600f) {
            return 0f;
        }
        return frequency;
    }

    private boolean samePitch(String firstFullName, String secondFullName) {
        if (firstFullName == null || secondFullName == null) {
            return false;
        }
        if (firstFullName.equals(secondFullName)) {
            return true;
        }
        if (firstFullName.length() < 2 || secondFullName.length() < 2) {
            return false;
        }
        try {
            String firstNote = firstFullName.substring(0, firstFullName.length() - 1);
            int firstOctave = Integer.parseInt(firstFullName.substring(firstFullName.length() - 1));
            String secondNote = secondFullName.substring(0, secondFullName.length() - 1);
            int secondOctave = Integer.parseInt(secondFullName.substring(secondFullName.length() - 1));
            return MusicNotation.midiFor(firstNote, firstOctave) == MusicNotation.midiFor(secondNote, secondOctave);
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private int durationMs(String duration) {
        if ("eighth".equals(duration)) {
            return 240;
        }
        if ("half".equals(duration)) {
            return 900;
        }
        return 450;
    }

    private void writeWav(short[] pcm, int sampleRate, File file) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        byte[] bytes = new byte[pcm.length * 2];
        for (int i = 0; i < pcm.length; i++) {
            short sample = pcm[i];
            bytes[i * 2] = (byte) (sample & 0xFF);
            bytes[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }

        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        AudioInputStream stream = new AudioInputStream(new ByteArrayInputStream(bytes), format, pcm.length);
        AudioSystem.write(stream, AudioFileFormat.Type.WAVE, file);
        stream.close();
    }

    public static void main(String[] args) throws Exception {
        MidiRecognitionRegressionTest test = new MidiRecognitionRegressionTest();
        test.synthesizedReferenceScoreShouldRecognizeAllNotes();
        System.out.println("recognized=OK");
    }
}
