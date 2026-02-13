package tatar.eljah.recorder;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import tatar.eljah.audio.AudioSettingsStore;
import tatar.eljah.audio.PitchAnalyzer;
import tatar.eljah.fluitblox.R;

public class AudioSettingsActivity extends AppCompatActivity {
    private final PitchAnalyzer analyzer = new PitchAnalyzer();

    private PitchOverlayView spectrogram;
    private IntensityGraphView intensityGraph;
    private TextView thresholdText;

    private volatile float latestIntensity;
    private volatile float currentThreshold;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_settings);

        spectrogram = findViewById(R.id.settings_spectrogram);
        intensityGraph = findViewById(R.id.settings_intensity_graph);
        thresholdText = findViewById(R.id.settings_threshold_value);

        float threshold = AudioSettingsStore.intensityThreshold(this);
        currentThreshold = threshold;
        updateThreshold(threshold);

        intensityGraph.setOnThresholdChangedListener(new IntensityGraphView.OnThresholdChangedListener() {
            @Override
            public void onThresholdChanged(float value) {
                updateThreshold(value);
                AudioSettingsStore.setIntensityThreshold(AudioSettingsActivity.this, value);
            }
        });

        ensureMic();
    }

    private void ensureMic() {
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 2201);
            return;
        }
        startRealtimePreview();
    }

    private void startRealtimePreview() {
        analyzer.startRealtimePitch(null, new PitchAnalyzer.SpectrumListener() {
            @Override
            public void onSpectrum(final float[] magnitudes, final int sampleRate) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (latestIntensity < currentThreshold) {
                            spectrogram.setSpectrum(new float[magnitudes.length], sampleRate);
                        } else {
                            spectrogram.setSpectrum(magnitudes, sampleRate);
                        }
                    }
                });
            }
        }, new PitchAnalyzer.AudioListener() {
            @Override
            public void onAudio(short[] samples, int length, int sampleRate) {
                final float intensity = calculateRms(samples, length);
                latestIntensity = intensity;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        intensityGraph.addIntensity(intensity);
                    }
                });
            }
        });
    }

    private float calculateRms(short[] samples, int length) {
        if (samples == null || length <= 0) {
            return 0f;
        }
        double sum = 0d;
        for (int i = 0; i < length; i++) {
            double normalized = samples[i] / 32768.0;
            sum += normalized * normalized;
        }
        return (float) Math.sqrt(sum / length);
    }

    private void updateThreshold(float value) {
        currentThreshold = value;
        intensityGraph.setThreshold(value);
        thresholdText.setText(getString(R.string.settings_threshold_value_template, value));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 2201 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startRealtimePreview();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        analyzer.stop();
    }
}
