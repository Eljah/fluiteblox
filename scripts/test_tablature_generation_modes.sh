#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT_DIR="$ROOT/target/tablature-gen-test"
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

cat > "$OUT_DIR/TablatureGenerationModesTestMain.java" <<'JAVA'
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;

import tatar.eljah.recorder.MusicNotation;
import tatar.eljah.recorder.PitchMatchUtil;
import tatar.eljah.recorder.RecorderNoteMapper;

public class TablatureGenerationModesTestMain {
    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static String textOf(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        if (nodes.getLength() == 0) return null;
        return nodes.item(0).getTextContent().trim();
    }

    private static String noteNameFromPitch(Element pitch) {
        String step = textOf(pitch, "step");
        String alter = textOf(pitch, "alter");
        if (step == null || step.isEmpty()) {
            return null;
        }
        if ("1".equals(alter)) {
            return step + "#";
        }
        if ("-1".equals(alter)) {
            return step + "b";
        }
        return step;
    }

    private static double midiToFrequency(int midi) {
        return 440.0 * Math.pow(2.0, (midi - 69) / 12.0);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected path to reference_score.xml");
        }

        RecorderNoteMapper mapper = new RecorderNoteMapper();
        File xml = new File(args[0]);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
        factory.setExpandEntityReferences(false);
        Document doc = factory.newDocumentBuilder().parse(xml);
        NodeList notes = doc.getElementsByTagName("note");

        int pitchedCount = 0;
        int strictMatches = 0;
        int simplifiedMatches = 0;

        for (int i = 0; i < notes.getLength(); i++) {
            Element note = (Element) notes.item(i);
            NodeList pitchNodes = note.getElementsByTagName("pitch");
            if (pitchNodes.getLength() == 0) {
                continue; // rest
            }

            Element pitch = (Element) pitchNodes.item(0);
            String noteName = noteNameFromPitch(pitch);
            String octaveText = textOf(pitch, "octave");
            if (noteName == null || octaveText == null) {
                continue;
            }
            int octave = Integer.parseInt(octaveText);
            String expected = noteName + octave;

            float mapped = mapper.frequencyFor(expected);
            double freq = mapped > 0f ? mapped : midiToFrequency(MusicNotation.midiFor(noteName, octave));
            String synthesized = mapper.fromFrequency((float) freq);

            boolean strict = PitchMatchUtil.samePitch(expected, synthesized, false);
            boolean simplified = PitchMatchUtil.samePitch(expected, synthesized, true);

            if (strict) strictMatches++;
            if (simplified) simplifiedMatches++;
            pitchedCount++;

            if (!strict || !simplified) {
                throw new AssertionError(
                        "Mismatch for note " + expected + " -> synthesized " + synthesized
                                + " [strict=" + strict + ", simplified=" + simplified + "]");
            }
        }

        assertTrue(pitchedCount > 0, "No pitched notes parsed from reference score");
        assertTrue(strictMatches == pitchedCount, "Strict mode is not fully green: " + strictMatches + "/" + pitchedCount);
        assertTrue(simplifiedMatches == pitchedCount, "Simplified mode is not fully green: " + simplifiedMatches + "/" + pitchedCount);

        System.out.println("Tablature generation recognition test passed: strict="
                + strictMatches + "/" + pitchedCount
                + ", simplified=" + simplifiedMatches + "/" + pitchedCount);
    }
}
JAVA

javac -d "$OUT_DIR" \
  "$ROOT/src/main/java/tatar/eljah/recorder/MusicNotation.java" \
  "$ROOT/src/main/java/tatar/eljah/recorder/RecorderNoteMapper.java" \
  "$ROOT/src/main/java/tatar/eljah/recorder/PitchMatchUtil.java" \
  "$OUT_DIR/TablatureGenerationModesTestMain.java"

java -cp "$OUT_DIR" TablatureGenerationModesTestMain "$ROOT/src/main/assets/reference_score.xml"
