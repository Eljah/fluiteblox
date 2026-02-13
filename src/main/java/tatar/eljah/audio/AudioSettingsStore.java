package tatar.eljah.audio;

import android.content.Context;
import android.content.SharedPreferences;

public final class AudioSettingsStore {
    private static final String PREFS = "audio_settings";
    private static final String KEY_INTENSITY_THRESHOLD = "intensity_threshold";
    private static final float DEFAULT_INTENSITY_THRESHOLD = 0.03f;

    private AudioSettingsStore() {
    }

    public static float intensityThreshold(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.getFloat(KEY_INTENSITY_THRESHOLD, DEFAULT_INTENSITY_THRESHOLD);
    }

    public static void setIntensityThreshold(Context context, float value) {
        float clamped = Math.max(0f, Math.min(1f, value));
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putFloat(KEY_INTENSITY_THRESHOLD, clamped).apply();
    }
}
