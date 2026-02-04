package tatar.eljah.tts;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public final class TtsVoicePreferences {
    public static final String PREF_VOICE_GENDER = "pref_voice_gender";
    public static final String VOICE_GENDER_AUTO = "auto";
    public static final String VOICE_GENDER_MALE = "male";
    public static final String VOICE_GENDER_FEMALE = "female";

    private TtsVoicePreferences() {
    }

    public static String getVoiceGender(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String value = preferences.getString(PREF_VOICE_GENDER, VOICE_GENDER_AUTO);
        if (VOICE_GENDER_MALE.equals(value) || VOICE_GENDER_FEMALE.equals(value)) {
            return value;
        }
        return VOICE_GENDER_AUTO;
    }

    public static void setVoiceGender(Context context, String value) {
        String sanitizedValue = value;
        if (!VOICE_GENDER_MALE.equals(value) && !VOICE_GENDER_FEMALE.equals(value)) {
            sanitizedValue = VOICE_GENDER_AUTO;
        }
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        preferences.edit().putString(PREF_VOICE_GENDER, sanitizedValue).apply();
    }
}
