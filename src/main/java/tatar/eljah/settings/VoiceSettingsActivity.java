package tatar.eljah.settings;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.RadioGroup;

import tatar.eljah.R;
import tatar.eljah.tts.TtsVoicePreferences;

public class VoiceSettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice_settings);
        setTitle(R.string.title_voice_settings);

        RadioGroup genderGroup = findViewById(R.id.radio_voice_gender);
        String current = TtsVoicePreferences.getVoiceGender(this);
        if (TtsVoicePreferences.VOICE_GENDER_MALE.equals(current)) {
            genderGroup.check(R.id.radio_voice_gender_male);
        } else if (TtsVoicePreferences.VOICE_GENDER_FEMALE.equals(current)) {
            genderGroup.check(R.id.radio_voice_gender_female);
        } else {
            genderGroup.check(R.id.radio_voice_gender_auto);
        }

        genderGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                String selection = TtsVoicePreferences.VOICE_GENDER_AUTO;
                if (checkedId == R.id.radio_voice_gender_male) {
                    selection = TtsVoicePreferences.VOICE_GENDER_MALE;
                } else if (checkedId == R.id.radio_voice_gender_female) {
                    selection = TtsVoicePreferences.VOICE_GENDER_FEMALE;
                }
                TtsVoicePreferences.setVoiceGender(VoiceSettingsActivity.this, selection);
            }
        });
    }
}
