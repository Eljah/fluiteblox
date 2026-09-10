package tatar.eljah.recorder;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Typeface;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import tatar.eljah.audio.AudioSettingsStore;
import tatar.eljah.audio.PitchAnalyzer;
import tatar.eljah.fluitblox.R;

public class TankDefenseActivity extends AppCompatActivity {
    public static final String EXTRA_PIECE_ID = "tank_piece_id";
    public static final String DEFAULT_TANK_PIECE_ID = "preloaded-world-jingle-bells";

    private static final int REQ_RECORD_AUDIO = 2101;
    private static final long INPUT_COOLDOWN_MS = 140L;
    private static final int SYNTH_SAMPLE_RATE = 22050;
    private static final int ENVELOPE_FADE_MS = 8;

    private final PitchAnalyzer pitchAnalyzer = new PitchAnalyzer();
    private final RecorderNoteMapper mapper = new RecorderNoteMapper();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ScoreLibraryRepository repository;

    private ScorePiece piece;
    private TankDefenseGameView gameView;
    private TextView speedLabel;
    private Button modeButton;
    private TextView titleTextView;
    private FingeringPatternView titleFingeringView;
    private volatile float currentInputIntensity;
    private volatile boolean demoAudioRequested;
    private volatile boolean demoShotsRequested;
    private volatile boolean activityDestroyed;
    private Thread demoThread;
    private float intensityThreshold;
    private long lastShotAtMs;
    private String heldNoteName;
    private long heldNoteStartedAtMs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppLocaleManager.applySavedLocale(this);
        super.onCreate(savedInstanceState);
        repository = new ScoreLibraryRepository(this);
        piece = resolvePiece();
        gameView = new TankDefenseGameView(this, piece);
        gameView.setGameResultListener(new TankDefenseGameView.GameResultListener() {
            @Override
            public void onGameFinished(TankDefenseGameView.GameResult result) {
                recordTankResult(result);
            }
        });
        setContentView(buildContentView());
        installCustomTitle();
        gameView.setCurrentNoteListener(new TankDefenseGameView.CurrentNoteListener() {
            @Override
            public void onCurrentNoteChanged(String fullName) {
                updateTitleFingering(fullName);
            }
        });
        intensityThreshold = AudioSettingsStore.intensityThreshold(this);
        ensureMicListening();
    }

    private ScorePiece resolvePiece() {
        String pieceId = getIntent().getStringExtra(EXTRA_PIECE_ID);
        ScorePiece selected = pieceId == null ? null : repository.findById(pieceId);
        if (selected != null && selected.notes != null && !selected.notes.isEmpty()) {
            return selected;
        }
        selected = repository.findById(DEFAULT_TANK_PIECE_ID);
        if (selected != null && selected.notes != null && !selected.notes.isEmpty()) {
            return selected;
        }
        ScorePiece fallback = new ScorePiece();
        fallback.id = "tank-fallback";
        fallback.title = "Tank Demo";
        fallback.notes.add(new NoteEvent("E", 5, "quarter", 1));
        fallback.notes.add(new NoteEvent("E", 5, "quarter", 1));
        fallback.notes.add(new NoteEvent("G", 5, "quarter", 1));
        fallback.notes.add(new NoteEvent("C", 5, "quarter", 1));
        return fallback;
    }

    private View buildContentView() {
        FrameLayout root = new FrameLayout(this);
        root.addView(gameView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);

        Button slowerButton = new Button(this);
        slowerButton.setText("-");
        styleBlockControl(slowerButton);
        slowerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeSpeed(-0.15f);
            }
        });
        controls.addView(slowerButton, new LinearLayout.LayoutParams(dp(42), LinearLayout.LayoutParams.WRAP_CONTENT));

        speedLabel = new TextView(this);
        speedLabel.setTextColor(android.graphics.Color.WHITE);
        speedLabel.setTextSize(18f);
        speedLabel.setGravity(Gravity.CENTER);
        speedLabel.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        updateSpeedLabel();
        controls.addView(speedLabel, new LinearLayout.LayoutParams(dp(62), LinearLayout.LayoutParams.WRAP_CONTENT));

        Button fasterButton = new Button(this);
        fasterButton.setText("+");
        styleBlockControl(fasterButton);
        fasterButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeSpeed(0.15f);
            }
        });
        controls.addView(fasterButton, new LinearLayout.LayoutParams(dp(42), LinearLayout.LayoutParams.WRAP_CONTENT));

        modeButton = new Button(this);
        styleBlockControl(modeButton);
        updateModeButton();
        modeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                gameView.setDurationMode(!gameView.isDurationMode());
                heldNoteName = null;
                heldNoteStartedAtMs = 0L;
                updateModeButton();
            }
        });
        controls.addView(modeButton, new LinearLayout.LayoutParams(dp(70), LinearLayout.LayoutParams.WRAP_CONTENT));

        Button pieceButton = new Button(this);
        pieceButton.setText("Song");
        styleBlockControl(pieceButton);
        pieceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPieceSelector();
            }
        });
        controls.addView(pieceButton, new LinearLayout.LayoutParams(dp(66), LinearLayout.LayoutParams.WRAP_CONTENT));

        Button demoButton = new Button(this);
        demoButton.setText("Demo");
        styleBlockControl(demoButton);
        demoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startDemoPlayback();
            }
        });
        controls.addView(demoButton, new LinearLayout.LayoutParams(dp(66), LinearLayout.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.RIGHT);
        int margin = dp(8);
        params.setMargins(margin, margin, margin, margin);
        root.addView(controls, params);
        return root;
    }

    private void showPieceSelector() {
        final List<ScorePiece> pieces = repository.getAllPieces();
        final List<ScorePiece> playable = new java.util.ArrayList<ScorePiece>();
        for (int i = 0; i < pieces.size(); i++) {
            ScorePiece candidate = pieces.get(i);
            if (candidate != null && candidate.notes != null && !candidate.notes.isEmpty()) {
                playable.add(candidate);
            }
        }
        if (playable.isEmpty()) {
            return;
        }
        String[] labels = new String[playable.size()];
        int selectedIndex = -1;
        for (int i = 0; i < playable.size(); i++) {
            ScorePiece candidate = playable.get(i);
            String title = candidate.title == null || candidate.title.length() == 0 ? candidate.id : candidate.title;
            labels[i] = title + " (" + candidate.notes.size() + ")";
            if (piece != null && piece.id != null && piece.id.equals(candidate.id)) {
                selectedIndex = i;
            }
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle("Tank melody")
                .setSingleChoiceItems(labels, selectedIndex, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        selectPiece(playable.get(which));
                        dialog.dismiss();
                    }
                })
                .show();
    }

    private void selectPiece(ScorePiece selected) {
        if (selected == null || selected.notes == null || selected.notes.isEmpty()) {
            return;
        }
        stopDemoPlayback();
        piece = selected;
        gameView.setPiece(piece);
    }

    private void updateTitleFingering(String fullName) {
        if (titleTextView != null && titleFingeringView != null) {
            titleTextView.setText(getString(R.string.app_name));
            titleFingeringView.setPattern(fullName == null || fullName.length() == 0
                    ? ""
                    : mapper.fingeringPatternFor(fullName));
            return;
        }
        if (fullName == null || fullName.length() == 0) {
            setTitle(getString(R.string.app_name));
            return;
        }
        setTitle(getString(R.string.play_header_with_fingering, mapper.fingeringFor(fullName)));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void installCustomTitle() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar == null) {
            return;
        }
        LinearLayout titleLayout = new LinearLayout(this);
        titleLayout.setOrientation(LinearLayout.HORIZONTAL);
        titleLayout.setGravity(Gravity.CENTER_VERTICAL);

        titleTextView = new TextView(this);
        titleTextView.setText(getString(R.string.app_name));
        titleTextView.setTextColor(android.graphics.Color.WHITE);
        titleTextView.setTextSize(20f);
        titleTextView.setTypeface(Typeface.DEFAULT_BOLD);
        titleLayout.addView(titleTextView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        titleFingeringView = new FingeringPatternView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(174), dp(34));
        params.leftMargin = dp(12);
        titleLayout.addView(titleFingeringView, params);

        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setDisplayShowCustomEnabled(true);
        actionBar.setCustomView(titleLayout, new ActionBar.LayoutParams(
                ActionBar.LayoutParams.WRAP_CONTENT,
                ActionBar.LayoutParams.MATCH_PARENT,
                Gravity.CENTER_VERTICAL));
        updateTitleFingering(null);
    }

    private void styleBlockControl(Button button) {
        button.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        button.setTextColor(android.graphics.Color.rgb(31, 25, 16));
        button.setAllCaps(false);
    }

    private void changeSpeed(float delta) {
        stopDemoPlayback();
        gameView.setSpeedMultiplier(gameView.speedMultiplier() + delta);
        updateSpeedLabel();
    }

    private void updateSpeedLabel() {
        if (speedLabel != null && gameView != null) {
            speedLabel.setText(String.format(java.util.Locale.US, "%.2fx", gameView.speedMultiplier()));
        }
    }

    private void updateModeButton() {
        if (modeButton != null && gameView != null) {
            modeButton.setText(gameView.isDurationMode() ? "Dur" : "Pitch");
        }
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
        if (demoShotsRequested || gameView == null || pitchHz <= 0f || currentInputIntensity < intensityThreshold) {
            heldNoteName = null;
            heldNoteStartedAtMs = 0L;
            return;
        }
        long now = android.os.SystemClock.elapsedRealtime();
        String noteName = mapper.fromFrequency(normalizeDetectedPitch(pitchHz));
        if (!sameString(heldNoteName, noteName)) {
            heldNoteName = noteName;
            heldNoteStartedAtMs = now;
        }
        if (now - lastShotAtMs < INPUT_COOLDOWN_MS) {
            return;
        }
        lastShotAtMs = now;
        long heldDurationMs = heldNoteStartedAtMs == 0L ? 0L : now - heldNoteStartedAtMs;
        gameView.onDetectedNote(noteName, currentInputIntensity,
                gameView.isDurationMode() ? heldDurationMs : Long.MAX_VALUE);
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

    private void startDemoPlayback() {
        stopDemoPlayback();
        if (gameView == null || piece == null || piece.notes == null || piece.notes.isEmpty()) {
            return;
        }
        pitchAnalyzer.stop();
        currentInputIntensity = 0f;
        gameView.restart();
        demoAudioRequested = true;
        demoShotsRequested = true;
        gameView.setDemoAutoFire(true);
        scheduleDemoEnd();
        demoThread = new Thread(new Runnable() {
            @Override
            public void run() {
                playDemoNotes();
            }
        }, "tank-demo-playback");
        demoThread.start();
    }

    private void scheduleDemoEnd() {
        final java.util.List<NoteEvent> notes = gameView.levelNotes();
        long delay = gameView.firstNoteDelayMs();
        for (int i = 0; i < notes.size(); i++) {
            delay += gameView.noteDurationMs(notes.get(i));
        }
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                demoShotsRequested = false;
                if (gameView != null) {
                    gameView.setDemoAutoFire(false);
                }
            }
        }, delay + 500L);
    }

    private void stopDemoPlayback() {
        demoAudioRequested = false;
        demoShotsRequested = false;
        if (gameView != null) {
            gameView.setDemoAutoFire(false);
        }
        mainHandler.removeCallbacksAndMessages(null);
        Thread thread = demoThread;
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(250);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            demoThread = null;
        }
    }

    private void playDemoNotes() {
        MediaPlayer player = null;
        try {
            java.util.List<NoteEvent> notes = gameView.levelNotes();
            File wav = new File(getCacheDir(), "tank-demo.wav");
            writeDemoWav(wav, notes);
            if (!demoAudioRequested) {
                return;
            }
            requestDemoAudioFocus();
            player = new MediaPlayer();
            player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            player.setDataSource(wav.getAbsolutePath());
            player.prepare();
            player.start();
            while (demoAudioRequested && player.isPlaying()) {
                sleepWhileDemo(80L);
            }
        } catch (IOException ignored) {
        } catch (RuntimeException ignored) {
        } finally {
            if (player != null) {
                try {
                    if (player.isPlaying()) {
                        player.stop();
                    }
                } catch (IllegalStateException ignored) {
                }
                player.release();
            }
            demoAudioRequested = false;
            demoThread = null;
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (!activityDestroyed && !demoAudioRequested) {
                        ensureMicListening();
                    }
                }
            });
        }
    }

    private void requestDemoAudioFocus() {
        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audioManager != null) {
            audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        }
    }

    private void writeDemoWav(File file, java.util.List<NoteEvent> notes) throws IOException {
        int totalSamples = (int) (SYNTH_SAMPLE_RATE * gameView.firstNoteDelayMs() / 1000L);
        for (int i = 0; i < notes.size(); i++) {
            totalSamples += (int) (SYNTH_SAMPLE_RATE * gameView.noteDurationMs(notes.get(i)) / 1000L);
        }
        short[] pcm = new short[Math.max(1, totalSamples)];
        int offset = (int) (SYNTH_SAMPLE_RATE * gameView.firstNoteDelayMs() / 1000L);
        for (int i = 0; i < notes.size() && offset < pcm.length && demoAudioRequested; i++) {
            NoteEvent note = notes.get(i);
            int durationSamples = (int) (SYNTH_SAMPLE_RATE * gameView.noteDurationMs(note) / 1000L);
            appendTone(pcm, offset, Math.min(durationSamples, pcm.length - offset), note);
            offset += durationSamples;
        }
        FileOutputStream out = new FileOutputStream(file);
        try {
            writeWavHeader(out, pcm.length);
            byte[] bytes = new byte[pcm.length * 2];
            for (int i = 0; i < pcm.length; i++) {
                bytes[i * 2] = (byte) (pcm[i] & 0xff);
                bytes[i * 2 + 1] = (byte) ((pcm[i] >> 8) & 0xff);
            }
            out.write(bytes);
        } finally {
            out.close();
        }
    }

    private void appendTone(short[] pcm, int offset, int totalSamples, NoteEvent note) {
        int fadeSamples = Math.min(SYNTH_SAMPLE_RATE * ENVELOPE_FADE_MS / 1000, totalSamples / 2);
        double frequency = 440.0 * Math.pow(2.0,
                (MusicNotation.midiFor(note.noteName, note.octave) - 69) / 12.0);
        for (int i = 0; i < totalSamples; i++) {
            int sampleIndex = offset + i;
            float envelope = amplitudeEnvelope(i, totalSamples, fadeSamples);
            double t = i / (double) SYNTH_SAMPLE_RATE;
            pcm[sampleIndex] = (short) (Math.sin(2d * Math.PI * frequency * t) * 18000 * envelope);
        }
    }

    private void writeWavHeader(FileOutputStream out, int sampleCount) throws IOException {
        int byteRate = SYNTH_SAMPLE_RATE * 2;
        int dataSize = sampleCount * 2;
        writeAscii(out, "RIFF");
        writeIntLe(out, 36 + dataSize);
        writeAscii(out, "WAVE");
        writeAscii(out, "fmt ");
        writeIntLe(out, 16);
        writeShortLe(out, 1);
        writeShortLe(out, 1);
        writeIntLe(out, SYNTH_SAMPLE_RATE);
        writeIntLe(out, byteRate);
        writeShortLe(out, 2);
        writeShortLe(out, 16);
        writeAscii(out, "data");
        writeIntLe(out, dataSize);
    }

    private void writeAscii(FileOutputStream out, String value) throws IOException {
        out.write(value.getBytes("US-ASCII"));
    }

    private void writeIntLe(FileOutputStream out, int value) throws IOException {
        out.write(value & 0xff);
        out.write((value >> 8) & 0xff);
        out.write((value >> 16) & 0xff);
        out.write((value >> 24) & 0xff);
    }

    private void writeShortLe(FileOutputStream out, int value) throws IOException {
        out.write(value & 0xff);
        out.write((value >> 8) & 0xff);
    }

    private float amplitudeEnvelope(int sampleIndex, int totalSamples, int fadeSamples) {
        if (totalSamples <= 0 || fadeSamples <= 0) {
            return 1f;
        }
        if (sampleIndex < fadeSamples) {
            return sampleIndex / (float) fadeSamples;
        }
        int samplesToEnd = totalSamples - sampleIndex;
        if (samplesToEnd <= fadeSamples) {
            return Math.max(0f, samplesToEnd / (float) fadeSamples);
        }
        return 1f;
    }

    private void sleepWhileDemo(long ms) {
        long end = android.os.SystemClock.elapsedRealtime() + ms;
        while (demoAudioRequested && android.os.SystemClock.elapsedRealtime() < end) {
            try {
                Thread.sleep(Math.min(40L, Math.max(1L, end - android.os.SystemClock.elapsedRealtime())));
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            }
        }
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

    private void recordTankResult(TankDefenseGameView.GameResult result) {
        if (result == null || demoAudioRequested || demoShotsRequested || piece == null) {
            return;
        }
        new TankPerformanceStore(this).saveAttempt(piece.id, result);
        Toast.makeText(this, "Tank score: " + result.score + "/" + result.total, Toast.LENGTH_SHORT).show();
    }

    private static boolean sameString(String a, String b) {
        if (a == null) {
            return b == null;
        }
        return a.equals(b);
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
        activityDestroyed = true;
        stopDemoPlayback();
        pitchAnalyzer.stop();
    }
}
