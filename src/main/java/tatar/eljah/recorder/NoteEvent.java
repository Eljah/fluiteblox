package tatar.eljah.recorder;

public class NoteEvent {
    public final String noteName;
    public final int octave;
    public final String duration;
    public final int measure;
    public final float x;
    public final float y;

    public NoteEvent(String noteName, int octave, String duration, int measure) {
        this(noteName, octave, duration, measure, -1f, -1f);
    }

    public NoteEvent(String noteName, int octave, String duration, int measure, float x, float y) {
        this.noteName = noteName;
        this.octave = octave;
        this.duration = duration;
        this.measure = measure;
        this.x = x;
        this.y = y;
    }

    public String fullName() {
        return noteName + octave;
    }
}
