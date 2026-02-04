package tatar.eljah.tts;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class TtsVoiceSelector {
    private TtsVoiceSelector() {
    }

    public static void applyPreferredVoice(Context context, TextToSpeech textToSpeech, Locale locale) {
        if (context == null || textToSpeech == null || locale == null) {
            return;
        }
        String genderPreference = TtsVoicePreferences.getVoiceGender(context);
        if (TtsVoicePreferences.VOICE_GENDER_AUTO.equals(genderPreference)) {
            return;
        }
        Set<Voice> voices = textToSpeech.getVoices();
        if (voices == null || voices.isEmpty()) {
            return;
        }
        List<Voice> candidates = new ArrayList<>();
        for (Voice voice : voices) {
            if (voice == null || voice.isNetworkConnectionRequired()) {
                continue;
            }
            Locale voiceLocale = voice.getLocale();
            if (voiceLocale != null && locale.getLanguage().equals(voiceLocale.getLanguage())) {
                candidates.add(voice);
            }
        }
        if (candidates.isEmpty()) {
            return;
        }
        String targetGender = genderPreference.equals(TtsVoicePreferences.VOICE_GENDER_MALE) ? "male" : "female";
        for (Voice voice : candidates) {
            if (matchesGender(voice, targetGender)) {
                textToSpeech.setVoice(voice);
                return;
            }
        }
    }

    private static boolean matchesGender(Voice voice, String targetGender) {
        String name = voice.getName();
        if (name != null && name.toLowerCase(Locale.US).contains(targetGender)) {
            return true;
        }
        Set<String> features = voice.getFeatures();
        if (features == null) {
            return false;
        }
        for (String feature : features) {
            if (feature != null && feature.toLowerCase(Locale.US).contains(targetGender)) {
                return true;
            }
        }
        return false;
    }
}
