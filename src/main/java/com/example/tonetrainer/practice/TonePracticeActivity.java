package com.example.tonetrainer.practice;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import android.support.v7.app.AppCompatActivity;

import com.example.tonetrainer.R;
import com.example.tonetrainer.audio.PitchAnalyzer;
import com.example.tonetrainer.model.ToneSample;
import com.example.tonetrainer.model.VietnameseSyllable;
import com.example.tonetrainer.ui.ToneVisualizerView;
import com.example.tonetrainer.util.TextDiffUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TonePracticeActivity extends AppCompatActivity {

    private ToneVisualizerView visualizerView;
    private TextView tvTarget;
    private TextView tvRecognized;
    private TextView tvDiff;
    private TextView tvToneResult;
    private Button btnPlayReference;
    private Button btnRecordUser;

    private VietnameseSyllable targetSyllable;
    private ToneSample referenceSample;
    private ToneSample userSample;

    private TextToSpeech textToSpeech;
    private boolean isTtsReady = false;

    private PitchAnalyzer pitchAnalyzer;
    private final List<Float> userPitch = new ArrayList<>();
    private boolean isRecording = false;
    private boolean shouldRecognizeSpeech = false;

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

        pitchAnalyzer = new PitchAnalyzer();

        targetSyllable = new VietnameseSyllable("má", "sắc", R.raw.ma2);
        tvTarget.setText(targetSyllable.getText());

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
        startRecording(false);
        playReferenceAudio();
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

    private void stopReferenceAudio() {
        if (textToSpeech != null) {
            textToSpeech.stop();
        }
    }

    private void recordUser() {
        startRecording(true);
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
        });

        handler.postDelayed(stopRecordingRunnable, 3000);
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
            userDirection = userSample.getDirection(10f, 1);
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
