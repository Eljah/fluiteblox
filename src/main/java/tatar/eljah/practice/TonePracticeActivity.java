package tatar.eljah.practice;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;

import tatar.eljah.R;
import tatar.eljah.audio.PitchAnalyzer;
import tatar.eljah.model.ToneSample;
import tatar.eljah.model.VietnameseSyllable;
import tatar.eljah.ui.SpectrogramView;
import tatar.eljah.ui.ToneVisualizerView;
import tatar.eljah.util.TextDiffUtil;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TonePracticeActivity extends AppCompatActivity {

    private static final int REQUEST_RECORD_AUDIO = 1001;
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
    private TextView tvTarget;
    private TextView tvRecognized;
    private TextView tvDiff;
    private TextView tvToneResult;
    private Button btnPlayReference;
    private Button btnRecordUser;
    private SpectrogramView spectrogramView;
    private Spinner practiceConsonantSpinner;
    private Spinner practiceVowelSpinner;
    private Spinner practiceToneSpinner;

    private VietnameseSyllable targetSyllable;
    private ToneSample referenceSample;
    private ToneSample userSample;

    private TextToSpeech textToSpeech;
    private boolean isTtsReady = false;
    private static final float REFERENCE_SPEECH_RATE = 0.8f;
    private static final float DEFAULT_REFERENCE_THRESHOLD = 12f;
    private static final int DEFAULT_REFERENCE_MIN_SAMPLES = 2;
    private static final int REFERENCE_RECORDING_DURATION_MS = 500;
    private static final int USER_RECORDING_DURATION_MS = 3000;

    private PitchAnalyzer pitchAnalyzer;
    private final List<Float> userPitch = new ArrayList<>();
    private boolean isRecording = false;
    private boolean shouldRecognizeSpeech = false;
    private String referenceFileUtteranceId;
    private boolean pendingStartRecording = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable stopRecordingRunnable = new Runnable() {
        @Override
        public void run() {
            stopRecordingAndAnalyze(shouldRecognizeSpeech);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tone_practice);

        visualizerView = findViewById(R.id.toneVisualizerView);
        tvTarget = findViewById(R.id.tv_target);
        tvRecognized = findViewById(R.id.tv_recognized);
        tvDiff = findViewById(R.id.tv_diff);
        tvToneResult = findViewById(R.id.tv_tone_result);
        btnPlayReference = findViewById(R.id.btn_play_reference);
        btnRecordUser = findViewById(R.id.btn_record_user);
        spectrogramView = findViewById(R.id.spectrogramView);
        practiceConsonantSpinner = findViewById(R.id.spinner_practice_consonant);
        practiceVowelSpinner = findViewById(R.id.spinner_practice_vowel);
        practiceToneSpinner = findViewById(R.id.spinner_practice_tone);

        pitchAnalyzer = new PitchAnalyzer();

        setupSpinners();
        updateTargetFromSelection();
        ensureRecordingPermission();

        textToSpeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    Locale vietnameseLocale = Locale.forLanguageTag("vi-VN");
                    int languageStatus = textToSpeech.setLanguage(vietnameseLocale);
                    isTtsReady = languageStatus != TextToSpeech.LANG_MISSING_DATA
                            && languageStatus != TextToSpeech.LANG_NOT_SUPPORTED;
                    if (isTtsReady) {
                        textToSpeech.setSpeechRate(REFERENCE_SPEECH_RATE);
                    }
                }
            }
        }, "com.google.android.tts");

        referenceSample = createSimpleReferenceSample();
        visualizerView.setReferenceData(referenceSample.getPitchHz());
        visualizerView.setUserData(null);

        btnPlayReference.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playReference();
            }
        });

        btnRecordUser.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                recordUser();
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

        practiceConsonantSpinner.setAdapter(consonantAdapter);
        practiceVowelSpinner.setAdapter(vowelAdapter);
        practiceToneSpinner.setAdapter(toneAdapter);

        AdapterView.OnItemSelectedListener updateListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateTargetFromSelection();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        };
        practiceConsonantSpinner.setOnItemSelectedListener(updateListener);
        practiceVowelSpinner.setOnItemSelectedListener(updateListener);
        practiceToneSpinner.setOnItemSelectedListener(updateListener);
    }

    private void updateTargetFromSelection() {
        String syllable = buildSyllable(practiceConsonantSpinner, practiceVowelSpinner, practiceToneSpinner);
        String tone = String.valueOf(practiceToneSpinner.getSelectedItem());
        targetSyllable = new VietnameseSyllable(syllable, tone, 0);
        tvTarget.setText(syllable);
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

    private ToneSample createSimpleReferenceSample() {
        List<Float> data = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            data.add(150f + i);
        }
        return new ToneSample(data, 20);
    }

    private void playReference() {
        visualizerView.setReferenceData(referenceSample.getPitchHz());
        visualizerView.setUserData(null);
        if (spectrogramView != null) {
            spectrogramView.clear();
        }
        synthesizeReferenceToFile();
    }

    private void playReferenceAudio() {
        if (targetSyllable == null) {
            return;
        }
        if (!isTtsReady || textToSpeech == null) {
            return;
        }
        textToSpeech.stop();
        textToSpeech.speak(targetSyllable.getText(), TextToSpeech.QUEUE_FLUSH, null, "reference-utterance");
    }

    private void synthesizeReferenceToFile() {
        if (targetSyllable == null || !isTtsReady || textToSpeech == null) {
            return;
        }
        final File outputFile;
        try {
            outputFile = File.createTempFile("reference_tts_", ".wav", getCacheDir());
        } catch (IOException e) {
            return;
        }

        referenceFileUtteranceId = "reference-file-" + System.currentTimeMillis();
        textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
            }

            @Override
            public void onDone(String utteranceId) {
                if (utteranceId != null && utteranceId.equals(referenceFileUtteranceId)) {
                    analyzeReferenceFile(outputFile);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            playReferenceAudio();
                        }
                    });
                }
            }

            @Override
            public void onError(String utteranceId) {
                if (utteranceId != null && utteranceId.equals(referenceFileUtteranceId)) {
                    deleteTempFile(outputFile);
                }
            }
        });

        Bundle params = new Bundle();
        textToSpeech.synthesizeToFile(targetSyllable.getText(), params, outputFile, referenceFileUtteranceId);
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
                        referenceSample = new ToneSample(pitchData, 20);
                        visualizerView.setReferenceData(referenceSample.getPitchHz());
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

    private void stopReferenceAudio() {
        if (textToSpeech != null) {
            textToSpeech.stop();
        }
    }

    private void recordUser() {
        if (!ensureRecordingPermission()) {
            pendingStartRecording = true;
            return;
        }
        startRecording(true);
    }

    private boolean ensureRecordingPermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        ActivityCompat.requestPermissions(
                this,
                new String[]{android.Manifest.permission.RECORD_AUDIO},
                REQUEST_RECORD_AUDIO
        );
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_RECORD_AUDIO) {
            return;
        }
        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (granted && pendingStartRecording) {
            pendingStartRecording = false;
            startRecording(true);
        } else if (!granted) {
            pendingStartRecording = false;
        }
    }

    private void startRecording(boolean recognizeSpeech) {
        if (isRecording) {
            handler.removeCallbacks(stopRecordingRunnable);
            pitchAnalyzer.stop();
        }
        isRecording = true;
        shouldRecognizeSpeech = recognizeSpeech;

        userPitch.clear();
        visualizerView.setUserData(userPitch);
        if (spectrogramView != null) {
            spectrogramView.clear();
        }
        if (recognizeSpeech) {
            tvRecognized.setText("");
            tvDiff.setText("");
        }
        tvToneResult.setText("");

        pitchAnalyzer.startRealtimePitch(new PitchAnalyzer.PitchListener() {
            @Override
            public void onPitch(float pitchHz) {
                userPitch.add(pitchHz);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        visualizerView.setUserData(userPitch);
                    }
                });
            }
        }, new PitchAnalyzer.SpectrumListener() {
            @Override
            public void onSpectrum(final float[] magnitudes, final int sampleRate) {
                if (spectrogramView == null) {
                    return;
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        spectrogramView.addSpectrumFrame(magnitudes, sampleRate, magnitudes.length * 2);
                    }
                });
            }
        });

        int recordingDurationMs = recognizeSpeech
                ? USER_RECORDING_DURATION_MS
                : REFERENCE_RECORDING_DURATION_MS;
        handler.postDelayed(stopRecordingRunnable, recordingDurationMs);
    }

    private void stopRecordingAndAnalyze(boolean recognizeSpeech) {
        pitchAnalyzer.stop();
        isRecording = false;
        userSample = new ToneSample(new ArrayList<>(userPitch), 20);
        compareToneDirection(recognizeSpeech);
        if (recognizeSpeech) {
            startSpeechRecognition();
        }
    }

    private void compareToneDirection(boolean strictAnalysis) {
        ToneSample.Direction referenceDirection = referenceSample.getDirection();
        ToneSample.Direction userDirection;
        if (strictAnalysis) {
            userDirection = userSample.getDirection();
        } else {
            userDirection = userSample.getDirection(
                    DEFAULT_REFERENCE_THRESHOLD,
                    DEFAULT_REFERENCE_MIN_SAMPLES
            );
        }

        String text;
        if (userPitch.isEmpty()) {
            text = getString(R.string.tone_result_no_data);
        } else if (referenceDirection == userDirection) {
            text = getString(R.string.tone_result_match);
        } else {
            text = getString(R.string.tone_result_diff);
        }
        tvToneResult.setText(text);
    }

    private void startSpeechRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            return;
        }

        final SpeechRecognizer recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "vi-VN");
        intent.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true);

        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
            }

            @Override
            public void onBeginningOfSpeech() {
            }

            @Override
            public void onRmsChanged(float rmsdB) {
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
            }

            @Override
            public void onEndOfSpeech() {
            }

            @Override
            public void onError(int error) {
                recognizer.destroy();
            }

            @Override
            public void onResults(Bundle results) {
                List<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                String recognized = "";
                if (matches != null && !matches.isEmpty()) {
                    recognized = matches.get(0);
                }
                tvRecognized.setText(recognized);
                tvDiff.setText(TextDiffUtil.highlightDiff(targetSyllable.getText(), recognized));
                recognizer.destroy();
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
            }

            @Override
            public void onEvent(int eventType, Bundle params) {
            }
        });

        recognizer.startListening(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(stopRecordingRunnable);
        pitchAnalyzer.stop();
        stopReferenceAudio();
        if (textToSpeech != null) {
            textToSpeech.shutdown();
            textToSpeech = null;
        }
    }
}
