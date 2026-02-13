package tatar.eljah.recorder;

import android.content.res.AssetManager;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public final class ReferenceComposition {
    private ReferenceComposition() {}

    public static final int EXPECTED_NOTES = 54;

    private static List<NoteEvent> cached;

    private static final String[] PITCHES = new String[]{"F","F","E","C","D","A","C","Bb","F","G","A","G","F","G","A","Bb","G","A","Bb","C","A","D","F","E","D","C","D","E","F","F","E","C","D","A","C","Bb","A","G","F","E","D","G","A","Bb","C","D","E","F","G","F","E","D","E","F"};
    private static final int[] OCTAVES = new int[]{5,5,5,5,5,4,5,4,4,4,4,4,4,4,4,4,4,4,4,5,4,5,5,5,5,5,5,5,5,5,5,5,5,4,5,4,4,4,4,4,4,4,4,4,5,5,5,5,5,5,5,5,5,5};
    private static final String[] DURATIONS = new String[]{"eighth","eighth","eighth","eighth","quarter","quarter","eighth","eighth","eighth","eighth","eighth","eighth","quarter","eighth","eighth","eighth","eighth","eighth","eighth","eighth","eighth","eighth","eighth","eighth","eighth","eighth","eighth","quarter","eighth","eighth","eighth","eighth","quarter","quarter","eighth","eighth","eighth","eighth","eighth","eighth","quarter","eighth","eighth","eighth","eighth","eighth","eighth","eighth","eighth","eighth","eighth","eighth","eighth","eighth"};

    public static synchronized void loadFromAssets(AssetManager assets) {
        try {
            InputStream xmlIn = assets.open("reference_score.xml");
            InputStream midiBase64In = assets.open("reference_score.mid.b64");
            byte[] midiBytes = ReferenceCompositionExtractor.decodeBase64Midi(midiBase64In);
            List<NoteEvent> extracted = ReferenceCompositionExtractor.extractFromXmlAndMidi(xmlIn, midiBytes, EXPECTED_NOTES);
            if (extracted.size() == EXPECTED_NOTES) {
                cached = extracted;
            }
        } catch (Exception ignored) {
        }
    }

    public static synchronized List<NoteEvent> expected54() {
        if (cached != null && cached.size() == EXPECTED_NOTES) {
            return new ArrayList<NoteEvent>(cached);
        }
        List<NoteEvent> out = new ArrayList<NoteEvent>();
        for (int i = 0; i < EXPECTED_NOTES; i++) {
            out.add(new NoteEvent(PITCHES[i], OCTAVES[i], DURATIONS[i], 1 + (i / 4)));
        }
        return out;
    }
}
