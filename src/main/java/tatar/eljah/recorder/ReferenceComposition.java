package tatar.eljah.recorder;

import java.util.ArrayList;
import java.util.List;

public final class ReferenceComposition {
    private ReferenceComposition() {}

    public static final int EXPECTED_NOTES = 54;

    private static final String[] PITCHES = new String[]{"F","F","E","C","D","A","C","Bb","F","G","A","G","F","G","A","Bb","G","A","Bb","C","A","D","F","E","D","C","D","E","F","F","E","C","D","A","C","Bb","A","G","F","E","D","G","A","Bb","C","D","E","F","G","F","E","D","E","F"};
    private static final int[] OCTAVES = new int[]{5,5,5,5,5,4,5,4,4,4,4,4,4,4,4,4,4,4,4,5,4,5,5,5,5,5,5,5,5,5,5,5,5,4,5,4,4,4,4,4,4,4,4,4,5,5,5,5,5,5,5,5,5,5};
    private static final String[] DURATIONS = new String[]{"eighth","eighth","eighth","eighth","quarter","quarter","eighth","eighth","eighth","eighth","eighth","eighth","quarter","eighth","eighth","eighth","eighth","eighth","eighth","eighth","eighth","eighth","eighth","eighth","eighth","eighth","eighth","quarter","eighth","eighth","eighth","eighth","quarter","quarter","eighth","eighth","eighth","eighth","eighth","eighth","quarter","eighth","eighth","eighth","eighth","eighth","eighth","eighth","eighth","eighth","eighth","eighth","eighth","eighth"};

    public static List<NoteEvent> expected54() {
        List<NoteEvent> out = new ArrayList<NoteEvent>();
        for (int i = 0; i < EXPECTED_NOTES; i++) {
            out.add(new NoteEvent(PITCHES[i], OCTAVES[i], DURATIONS[i], 1 + (i / 4)));
        }
        return out;
    }
}
