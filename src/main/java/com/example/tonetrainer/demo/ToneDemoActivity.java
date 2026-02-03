package com.example.tonetrainer.demo;

import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import com.example.tonetrainer.R;
import com.example.tonetrainer.audio.PitchAnalyzer;
import com.example.tonetrainer.ui.SpectrogramView;
import com.example.tonetrainer.ui.ToneVisualizerView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ToneDemoActivity extends AppCompatActivity {

    private static final String[] CONSONANTS = {"", "m", "n", "l", "b", "d", "g", "h", "k", "ng"};
    private static final String[] VOWELS = {
            "a", "ă", "â", "e", "ê", "i", "o", "ô", "ơ", "u", "ư", "y"
    };
    private static final String[] TONES = {"ngang", "sắc", "huyền", "hỏi", "ngã", "nặng"};
    private static final Map<String, String[]> TONE_FORMS = new HashMap<>();

    static {
        TONE_FORMS.put("a", new String[]{"a", "á", "à", "ả", "ã", "ạ"});
        TONE_FORMS.put("ă", new String[]{"ă", "ắ", "ằ", "ẳ", "ẵ", "ặ"});
        TONE_FORMS.put("â", new String[]{"â", "ấ", "ầ", "ẩ", "ẫ", "ậ"});
        TONE_FORMS.put("e", new String[]{"e", "é", "è", "ẻ", "ẽ", "ẹ"});
        TONE_FORMS.put("ê", new String[]{"ê", "ế", "ề", "ể", "ễ", "ệ"});
        TONE_FORMS.put("i", new String[]{"i", "í", "ì", "ỉ", "ĩ", "ị"});
        TONE_FORMS.put("o", new String[]{"o", "ó", "ò", "ỏ", "õ", "ọ"});
        TONE_FORMS.put("ô", new String[]{"ô", "ố", "ồ", "ổ", "ỗ", "ộ"});
        TONE_FORMS.put("ơ", new String[]{"ơ", "ớ", "ờ", "ở", "ỡ", "ợ"});
        TONE_FORMS.put("u", new String[]{"u", "ú", "ù", "ủ", "ũ", "ụ"});
        TONE_FORMS.put("ư", new String[]{"ư", "ứ", "ừ", "ử", "ữ", "ự"});
        TONE_FORMS.put("y", new String[]{"y", "ý", "ỳ", "ỷ", "ỹ", "ỵ"});
    }

    private ToneVisualizerView visualizerView;
    private SpectrogramView spectrogramView;
    private PitchAnalyzer pitchAnalyzer;
    private final List<Float> currentPitch = new ArrayList<>();
    private TextToSpeech textToSpeech;
    private boolean isTtsReady;

    private Spinner sampleConsonantSpinner;
    private Spinner sampleVowelSpinner;
    private Spinner sampleToneSpinner;
    private TextView samplePreview;

    private Spinner demoFirstConsonantSpinner;
    private Spinner demoFirstVowelSpinner;
    private Spinner demoFirstToneSpinner;
    private Spinner demoSecondConsonantSpinner;
    private Spinner demoSecondVowelSpinner;
    private Spinner demoSecondToneSpinner;
    private TextView demoPhraseView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tone_demo);

        visualizerView = findViewById(R.id.toneVisualizerView);
        spectrogramView = findViewById(R.id.spectrogramView);
        pitchAnalyzer = new PitchAnalyzer();

        sampleConsonantSpinner = findViewById(R.id.spinner_sample_consonant);
        sampleVowelSpinner = findViewById(R.id.spinner_sample_vowel);
        sampleToneSpinner = findViewById(R.id.spinner_sample_tone);
        samplePreview = findViewById(R.id.tv_sample_preview);

        demoFirstConsonantSpinner = findViewById(R.id.spinner_demo_first_consonant);
        demoFirstVowelSpinner = findViewById(R.id.spinner_demo_first_vowel);
        demoFirstToneSpinner = findViewById(R.id.spinner_demo_first_tone);
        demoSecondConsonantSpinner = findViewById(R.id.spinner_demo_second_consonant);
        demoSecondVowelSpinner = findViewById(R.id.spinner_demo_second_vowel);
        demoSecondToneSpinner = findViewById(R.id.spinner_demo_second_tone);
        demoPhraseView = findViewById(R.id.tv_demo_phrase);

        setupSpinners();
        setupTextToSpeech();

        Button playSampleButton = findViewById(R.id.btn_play_reference);
        playSampleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String syllable = buildSyllable(
                        sampleConsonantSpinner,
                        sampleVowelSpinner,
                        sampleToneSpinner
                );
                samplePreview.setText(getString(R.string.label_sample_selected, syllable));
                playTextWithAnalysis(syllable);
            }
        });

        Button playDemoButton = findViewById(R.id.btn_play_demo);
        playDemoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String first = buildSyllable(
                        demoFirstConsonantSpinner,
                        demoFirstVowelSpinner,
                        demoFirstToneSpinner
                );
                String second = buildSyllable(
                        demoSecondConsonantSpinner,
                        demoSecondVowelSpinner,
                        demoSecondToneSpinner
                );
                String phrase = first + " " + second;
                demoPhraseView.setText(getString(R.string.label_demo_phrase, phrase));
                playTextWithAnalysis(phrase);
            }
        });
    }

    private void setupSpinners() {
        ArrayAdapter<String> consonantAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, CONSONANTS);
        consonantAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        ArrayAdapter<String> vowelAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, VOWELS);
        vowelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        ArrayAdapter<String> toneAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, TONES);
        toneAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        sampleConsonantSpinner.setAdapter(consonantAdapter);
        sampleVowelSpinner.setAdapter(vowelAdapter);
        sampleToneSpinner.setAdapter(toneAdapter);

        demoFirstConsonantSpinner.setAdapter(consonantAdapter);
        demoFirstVowelSpinner.setAdapter(vowelAdapter);
        demoFirstToneSpinner.setAdapter(toneAdapter);

        demoSecondConsonantSpinner.setAdapter(consonantAdapter);
        demoSecondVowelSpinner.setAdapter(vowelAdapter);
        demoSecondToneSpinner.setAdapter(toneAdapter);
    }

    private void setupTextToSpeech() {
        textToSpeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    Locale vietnameseLocale = Locale.forLanguageTag("vi-VN");
                    int languageStatus = textToSpeech.setLanguage(vietnameseLocale);
                    isTtsReady = languageStatus != TextToSpeech.LANG_MISSING_DATA
                            && languageStatus != TextToSpeech.LANG_NOT_SUPPORTED;
                }
            }
        }, "com.google.android.tts");
    }

    private String buildSyllable(Spinner consonantSpinner, Spinner vowelSpinner, Spinner toneSpinner) {
        String consonant = String.valueOf(consonantSpinner.getSelectedItem());
        String vowel = String.valueOf(vowelSpinner.getSelectedItem());
        String tone = String.valueOf(toneSpinner.getSelectedItem());
        String tonedVowel = applyTone(vowel, tone);
        return (consonant + tonedVowel).trim();
    }

    private String applyTone(String vowel, String toneLabel) {
        int toneIndex = 0;
        for (int i = 0; i < TONES.length; i++) {
            if (TONES[i].equals(toneLabel)) {
                toneIndex = i;
                break;
            }
        }
        String[] forms = TONE_FORMS.get(vowel);
        if (forms == null || toneIndex >= forms.length) {
            return vowel;
        }
        return forms[toneIndex];
    }

    private void playTextWithAnalysis(final String text) {
        stopPlayback();

        currentPitch.clear();
        visualizerView.setReferenceData(currentPitch);
        visualizerView.setUserData(null);
        if (spectrogramView != null) {
            spectrogramView.clear();
        }

        if (!isTtsReady || textToSpeech == null) {
            return;
        }
        textToSpeech.stop();

        final File outputFile;
        try {
            outputFile = File.createTempFile("demo_tts_", ".wav", getCacheDir());
        } catch (IOException e) {
            return;
        }

        final String fileUtteranceId = "demo-file-" + System.currentTimeMillis();
        textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
            }

            @Override
            public void onDone(String utteranceId) {
                if (utteranceId != null && utteranceId.equals(fileUtteranceId)) {
                    analyzeReferenceFile(outputFile);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "demo-utterance");
                        }
                    });
                }
            }

            @Override
            public void onError(String utteranceId) {
                deleteTempFile(outputFile);
            }
        });

        textToSpeech.synthesizeToFile(text, null, outputFile, fileUtteranceId);
    }

    private void analyzeReferenceFile(final File outputFile) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                WavData wavData;
                try {
                    wavData = readWavFile(outputFile);
                } catch (IOException e) {
                    deleteTempFile(outputFile);
                    return;
                }
                final List<Float> pitchData = new ArrayList<>();
                final List<float[]> spectrumFrames = new ArrayList<>();
                pitchAnalyzer.analyzePcm(
                        wavData.samples,
                        wavData.sampleRate,
                        new PitchAnalyzer.PitchListener() {
                            @Override
                            public void onPitch(float pitchHz) {
                                pitchData.add(pitchHz);
                            }
                        },
                        new PitchAnalyzer.SpectrumListener() {
                            @Override
                            public void onSpectrum(float[] magnitudes, int sampleRate) {
                                spectrumFrames.add(magnitudes);
                            }
                        }
                );
                deleteTempFile(outputFile);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        currentPitch.clear();
                        currentPitch.addAll(pitchData);
                        visualizerView.setReferenceData(currentPitch);
                        if (spectrogramView != null) {
                            spectrogramView.clear();
                            for (float[] frame : spectrumFrames) {
                                spectrogramView.addSpectrumFrame(frame, wavData.sampleRate, frame.length * 2);
                            }
                        }
                    }
                });
            }
        }).start();
    }

    private void deleteTempFile(File file) {
        if (file == null) {
            return;
        }
        if (!file.delete()) {
            file.deleteOnExit();
        }
    }

    private static class WavData {
        private final short[] samples;
        private final int sampleRate;

        private WavData(short[] samples, int sampleRate) {
            this.samples = samples;
            this.sampleRate = sampleRate;
        }
    }

    private WavData readWavFile(File file) throws IOException {
        byte[] bytes = readAllBytes(file);
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (buffer.remaining() < 12) {
            throw new IOException("Invalid WAV header");
        }
        byte[] riff = new byte[4];
        buffer.get(riff);
        buffer.getInt();
        byte[] wave = new byte[4];
        buffer.get(wave);
        int sampleRate = 0;
        short channels = 0;
        short bitsPerSample = 0;
        int dataOffset = -1;
        int dataSize = 0;
        while (buffer.remaining() >= 8) {
            byte[] chunkIdBytes = new byte[4];
            buffer.get(chunkIdBytes);
            String chunkId = new String(chunkIdBytes);
            int chunkSize = buffer.getInt();
            if ("fmt ".equals(chunkId)) {
                short audioFormat = buffer.getShort();
                channels = buffer.getShort();
                sampleRate = buffer.getInt();
                buffer.getInt();
                buffer.getShort();
                bitsPerSample = buffer.getShort();
                if (chunkSize > 16) {
                    buffer.position(buffer.position() + (chunkSize - 16));
                }
                if (audioFormat != 1) {
                    throw new IOException("Unsupported WAV format");
                }
            } else if ("data".equals(chunkId)) {
                dataOffset = buffer.position();
                dataSize = chunkSize;
                buffer.position(buffer.position() + chunkSize);
            } else {
                buffer.position(buffer.position() + chunkSize);
            }
        }
        if (dataOffset < 0 || bitsPerSample != 16 || channels == 0 || sampleRate == 0) {
            throw new IOException("Invalid WAV data");
        }
        ByteBuffer dataBuffer = ByteBuffer.wrap(bytes, dataOffset, dataSize).order(ByteOrder.LITTLE_ENDIAN);
        int totalSamples = dataSize / 2 / channels;
        short[] samples = new short[totalSamples];
        for (int i = 0; i < totalSamples; i++) {
            short sample = dataBuffer.getShort();
            if (channels > 1) {
                for (int c = 1; c < channels; c++) {
                    dataBuffer.getShort();
                }
            }
            samples[i] = sample;
        }
        return new WavData(samples, sampleRate);
    }

    private byte[] readAllBytes(File file) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        try (FileInputStream inputStream = new FileInputStream(file)) {
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
        }
        return outputStream.toByteArray();
    }

    private void stopPlayback() {
        pitchAnalyzer.stop();
        if (textToSpeech != null) {
            textToSpeech.stop();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPlayback();
        if (textToSpeech != null) {
            textToSpeech.shutdown();
            textToSpeech = null;
        }
    }
}
