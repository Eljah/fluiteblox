package tatar.eljah.recorder;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import tatar.eljah.audio.AudioSettingsStore;
import tatar.eljah.audio.PitchAnalyzer;

public class TankDefenseActivity extends AppCompatActivity {
    private static final int REQ_RECORD_AUDIO = 2101;
    private static final long INPUT_COOLDOWN_MS = 140L;

    private final PitchAnalyzer pitchAnalyzer = new PitchAnalyzer();
    private final RecorderNoteMapper mapper = new RecorderNoteMapper();

    private TankDefenseGameView gameView;
    private volatile float currentInputIntensity;
    private float intensityThreshold;
    private long lastShotAtMs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppLocaleManager.applySavedLocale(this);
        super.onCreate(savedInstanceState);
        gameView = new TankDefenseGameView(this);
        setContentView(gameView);
        intensityThreshold = AudioSettingsStore.intensityThreshold(this);
        ensureMicListening();
    }

    @Override
    protected void onResume() {
        super.onResume();
        intensityThreshold = AudioSettingsStore.intensityThreshold(this);
    }

    private void ensureMicListening() {
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_RECORD_AUDIO);
        } else {
            startListening();
        }
    }

    private void startListening() {
        pitchAnalyzer.startRealtimePitch(new PitchAnalyzer.PitchListener() {
            @Override
            public void onPitch(final float pitchHz) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        consumePitch(pitchHz);
                    }
                });
            }
        }, null, new PitchAnalyzer.AudioListener() {
            @Override
            public void onAudio(short[] samples, int length, int sampleRate) {
                currentInputIntensity = rms(samples, length);
            }
        });
    }

    private void consumePitch(float pitchHz) {
        if (gameView == null || pitchHz <= 0f || currentInputIntensity < intensityThreshold) {
            return;
        }
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastShotAtMs < INPUT_COOLDOWN_MS) {
            return;
        }
        lastShotAtMs = now;
        gameView.onDetectedNote(mapper.fromFrequency(normalizeDetectedPitch(pitchHz)), currentInputIntensity);
    }

    private float normalizeDetectedPitch(float detectedHz) {
        if (detectedHz <= 0f) {
            return detectedHz;
        }
        float minMappedHz = mapper.frequencyFor("D4");
        float maxMappedHz = mapper.frequencyFor("D6");
        while (detectedHz > maxMappedHz && detectedHz / 2f >= minMappedHz) {
            detectedHz /= 2f;
        }
        while (detectedHz < minMappedHz && detectedHz * 2f <= maxMappedHz) {
            detectedHz *= 2f;
        }
        return detectedHz;
    }

    private float rms(short[] samples, int length) {
        if (samples == null || length <= 0) {
            return 0f;
        }
        double sum = 0d;
        for (int i = 0; i < length; i++) {
            double n = samples[i] / 32768.0;
            sum += n * n;
        }
        return (float) Math.sqrt(sum / length);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_RECORD_AUDIO
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startListening();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        pitchAnalyzer.stop();
    }
}
