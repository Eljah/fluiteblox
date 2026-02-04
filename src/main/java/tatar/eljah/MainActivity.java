package tatar.eljah;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import tatar.eljah.demo.ToneDemoActivity;
import tatar.eljah.practice.SyllableDiscriminationActivity;
import tatar.eljah.practice.TonePracticeActivity;
import tatar.eljah.settings.VoiceSettingsActivity;
import tatar.eljah.R;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        View demoButton = findViewById(R.id.btn_demo);
        View practiceButton = findViewById(R.id.btn_practice);
        View soundModeButton = findViewById(R.id.btn_sound_mode);
        View toneModeButton = findViewById(R.id.btn_tone_mode);
        View voiceSettingsButton = findViewById(R.id.btn_voice_settings);

        demoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, ToneDemoActivity.class);
                startActivity(intent);
            }
        });

        practiceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, TonePracticeActivity.class);
                startActivity(intent);
            }
        });

        soundModeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, SyllableDiscriminationActivity.class);
                intent.putExtra(SyllableDiscriminationActivity.EXTRA_MODE, SyllableDiscriminationActivity.MODE_SOUND);
                startActivity(intent);
            }
        });

        toneModeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, SyllableDiscriminationActivity.class);
                intent.putExtra(SyllableDiscriminationActivity.EXTRA_MODE, SyllableDiscriminationActivity.MODE_TONE);
                startActivity(intent);
            }
        });

        voiceSettingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, VoiceSettingsActivity.class);
                startActivity(intent);
            }
        });
    }
}
