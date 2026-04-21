package tatar.eljah.recorder;

import android.content.Context;
import android.os.Environment;

import org.audiveris.proxymusic.Attributes;
import org.audiveris.proxymusic.Clef;
import org.audiveris.proxymusic.ClefSign;
import org.audiveris.proxymusic.Key;
import org.audiveris.proxymusic.Note;
import org.audiveris.proxymusic.NoteType;
import org.audiveris.proxymusic.ObjectFactory;
import org.audiveris.proxymusic.PartList;
import org.audiveris.proxymusic.PartName;
import org.audiveris.proxymusic.Pitch;
import org.audiveris.proxymusic.ScorePart;
import org.audiveris.proxymusic.ScorePartwise;
import org.audiveris.proxymusic.Step;
import org.audiveris.proxymusic.Time;
import org.audiveris.proxymusic.util.Marshalling;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

public final class ScoreExportUtil {
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private ScoreExportUtil() {
    }

    public static File exportMusicXml(Context context, ScorePiece piece) throws IOException {
        File file = buildTargetFile(context, piece, "xml");
        FileOutputStream output = new FileOutputStream(file);
        try {
            Marshalling.marshal(buildScorePartwise(piece.notes), output, true, 2);
            output.flush();
        } catch (Exception proxymusicFailure) {
            output.write(buildMusicXmlLegacy(piece.notes).getBytes(UTF8));
            output.flush();
        } finally {
            output.close();
        }
        return file;
    }

    public static File exportMidi(Context context, ScorePiece piece) throws IOException {
        File file = buildTargetFile(context, piece, "mid");
        FileOutputStream output = new FileOutputStream(file);
        output.write(buildMidi(piece.notes));
        output.flush();
        output.close();
        return file;
    }

    private static File buildTargetFile(Context context, ScorePiece piece, String extension) {
        File folder = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (folder == null) {
            folder = context.getFilesDir();
        }
        if (!folder.exists()) {
            folder.mkdirs();
        }
        String safeTitle = piece.title == null ? "piece" : piece.title.replaceAll("[^a-zA-Zа-яА-Я0-9_-]", "_");
        return new File(folder, safeTitle + "_" + piece.id + "." + extension);
    }

    private static ScorePartwise buildScorePartwise(List<NoteEvent> notes) {
        ObjectFactory factory = new ObjectFactory();
        ScorePartwise score = factory.createScorePartwise();
        score.setVersion("4.0");

        PartList partList = factory.createPartList();
        score.setPartList(partList);

        ScorePart scorePart = factory.createScorePart();
        scorePart.setId("P1");
        PartName partName = factory.createPartName();
        partName.setValue("Flute");
        scorePart.setPartName(partName);
        partList.getPartGroupOrScorePart().add(scorePart);

        ScorePartwise.Part part = factory.createScorePartwisePart();
        part.setId(scorePart);
        score.getPart().add(part);

        int measureNumber = 1;
        int beatProgress = 0;
        ScorePartwise.Part.Measure measure = createMeasureSkeleton(factory, measureNumber);
        part.getMeasure().add(measure);

        for (NoteEvent noteEvent : notes) {
            int duration = durationUnits(noteEvent.duration);
            if (beatProgress + duration > 64) {
                measureNumber++;
                beatProgress = 0;
                measure = factory.createScorePartwisePartMeasure();
                measure.setNumber(String.valueOf(measureNumber));
                part.getMeasure().add(measure);
            }
            Note note = factory.createNote();
            note.setPitch(buildPitch(factory, noteEvent));
            note.setDuration(BigDecimal.valueOf(duration));
            NoteType noteType = factory.createNoteType();
            noteType.setValue(xmlType(noteEvent.duration));
            note.setType(noteType);
            measure.getNoteOrBackupOrForward().add(note);
            beatProgress += duration;
        }
        return score;
    }

    private static ScorePartwise.Part.Measure createMeasureSkeleton(ObjectFactory factory, int measureNumber) {
        ScorePartwise.Part.Measure measure = factory.createScorePartwisePartMeasure();
        measure.setNumber(String.valueOf(measureNumber));

        Attributes attributes = factory.createAttributes();
        attributes.setDivisions(BigDecimal.valueOf(16));

        Key key = factory.createKey();
        key.setFifths(BigInteger.ZERO);
        attributes.getKey().add(key);

        Time time = factory.createTime();
        time.getTimeSignature().add(factory.createTimeBeats("4"));
        time.getTimeSignature().add(factory.createTimeBeatType("4"));
        attributes.getTime().add(time);

        Clef clef = factory.createClef();
        clef.setSign(ClefSign.G);
        clef.setLine(BigInteger.valueOf(2));
        attributes.getClef().add(clef);

        measure.getNoteOrBackupOrForward().add(attributes);
        return measure;
    }

    private static Pitch buildPitch(ObjectFactory factory, NoteEvent note) {
        Pitch pitch = factory.createPitch();
        pitch.setStep(stepForNoteName(note.noteName));
        Integer alter = alterForNoteName(note.noteName);
        if (alter != null) {
            pitch.setAlter(BigDecimal.valueOf(alter));
        }
        pitch.setOctave(note.octave);
        return pitch;
    }

    private static Step stepForNoteName(String noteName) {
        if (noteName == null || noteName.length() == 0) {
            return Step.C;
        }
        String step = noteName.substring(0, 1).toUpperCase();
        if ("D".equals(step)) return Step.D;
        if ("E".equals(step)) return Step.E;
        if ("F".equals(step)) return Step.F;
        if ("G".equals(step)) return Step.G;
        if ("A".equals(step)) return Step.A;
        if ("B".equals(step) || "H".equals(step)) return Step.B;
        return Step.C;
    }

    private static Integer alterForNoteName(String noteName) {
        if (noteName == null || noteName.length() < 2) {
            return null;
        }
        if (noteName.contains("#")) {
            return 1;
        }
        if (noteName.contains("b")) {
            return -1;
        }
        return null;
    }

    private static String buildMusicXmlLegacy(List<NoteEvent> notes) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<score-partwise version=\"3.1\">\n");
        xml.append("  <part-list><score-part id=\"P1\"><part-name>Flute</part-name></score-part></part-list>\n");
        xml.append("  <part id=\"P1\">\n");
        int measure = 1;
        xml.append("    <measure number=\"").append(measure).append("\">\n");
        xml.append("      <attributes><divisions>16</divisions><key><fifths>0</fifths></key><time><beats>4</beats><beat-type>4</beat-type></time><clef><sign>G</sign><line>2</line></clef></attributes>\n");
        int beatProgress = 0;
        for (NoteEvent note : notes) {
            int duration = durationUnits(note.duration);
            if (beatProgress + duration > 64) {
                xml.append("    </measure>\n");
                measure++;
                xml.append("    <measure number=\"").append(measure).append("\">\n");
                beatProgress = 0;
            }
            xml.append("      <note>\n");
            xml.append("        <pitch><step>").append(note.noteName.substring(0, 1)).append("</step>");
            if (note.noteName.length() > 1) {
                String acc = note.noteName.substring(1);
                if ("#".equals(acc)) {
                    xml.append("<alter>1</alter>");
                } else if ("b".equals(acc)) {
                    xml.append("<alter>-1</alter>");
                }
            }
            xml.append("<octave>").append(note.octave).append("</octave></pitch>\n");
            xml.append("        <duration>").append(duration).append("</duration>\n");
            xml.append("        <type>").append(xmlType(note.duration)).append("</type>\n");
            xml.append("      </note>\n");
            beatProgress += duration;
        }
        xml.append("    </measure>\n");
        xml.append("  </part>\n");
        xml.append("</score-partwise>\n");
        return xml.toString();
    }

    private static byte[] buildMidi(List<NoteEvent> notes) {
        MidiBuilder builder = new MidiBuilder();
        builder.header(1, 1, 480);
        int velocity = 96;
        int tick = 0;
        builder.trackStart();
        builder.tempo(500000);
        for (NoteEvent note : notes) {
            int midi = MusicNotation.midiFor(note.noteName, note.octave);
            int noteTicks = midiTicks(note.duration);
            builder.noteOn(tick, midi, velocity);
            tick += noteTicks;
            builder.noteOff(tick, midi, 0);
        }
        builder.endOfTrack(tick + 1);
        return builder.build();
    }

    private static int durationUnits(String duration) {
        if ("whole".equals(duration)) return 64;
        if ("half".equals(duration)) return 32;
        if ("eighth".equals(duration)) return 8;
        if ("16th".equals(duration)) return 4;
        return 16;
    }

    private static int midiTicks(String duration) {
        if ("whole".equals(duration)) return 1920;
        if ("half".equals(duration)) return 960;
        if ("eighth".equals(duration)) return 240;
        if ("16th".equals(duration)) return 120;
        return 480;
    }

    private static String xmlType(String duration) {
        if ("whole".equals(duration)) return "whole";
        if ("half".equals(duration)) return "half";
        if ("eighth".equals(duration)) return "eighth";
        if ("16th".equals(duration)) return "16th";
        return "quarter";
    }
}
