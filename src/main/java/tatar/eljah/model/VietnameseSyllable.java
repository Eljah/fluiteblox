package tatar.eljah.model;

public class VietnameseSyllable {
    private final String text;
    private final String toneName;
    private final int audioResId;

    public VietnameseSyllable(String text, String toneName, int audioResId) {
        this.text = text;
        this.toneName = toneName;
        this.audioResId = audioResId;
    }

    public String getText() {
        return text;
    }

    public String getToneName() {
        return toneName;
    }

    public int getAudioResId() {
        return audioResId;
    }
}
