package tatar.eljah.recorder;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.RadioGroup;
import android.widget.TextView;

import tatar.eljah.audio.AudioSettingsStore;
import tatar.eljah.audio.PitchAnalyzer;
import tatar.eljah.fluitblox.R;

public class ScorePlayActivity extends AppCompatActivity {
    public static final String EXTRA_PIECE_ID = "piece_id";
    private static final String TAG = "ScorePlayActivity";
    private static final int SYNTH_SAMPLE_RATE = 22050;
    private static final int SAFE_SAMPLE_RATE = 44100;
    private static final int ENVELOPE_FADE_MS = 8;

    private final PitchAnalyzer pitchAnalyzer = new PitchAnalyzer();
    private final RecorderNoteMapper mapper = new RecorderNoteMapper();
    private final Object focusLock = new Object();

    private ScorePiece piece;
    private int pointer = 0;

    private TextView status;
    private PitchOverlayView overlayView;
    private AudioManager audioManager;

    private volatile boolean midiPlaybackRequested;
    private Thread midiThread;
    private int midiFocusToken;

    private volatile boolean tablaturePlaybackRequested;
    private Thread tablatureThread;
    private int tablatureFocusToken;

    private int focusCounter;
    private int activeFocusToken;

    private volatile float currentInputIntensity;
    private float intensityThreshold;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_score_play);

        String pieceId = getIntent().getStringExtra(EXTRA_PIECE_ID);
        piece = new ScoreLibraryRepository(this).findById(pieceId);

        status = findViewById(R.id.text_status);
        overlayView = findViewById(R.id.pitch_overlay);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

        if (piece == null || piece.notes.isEmpty()) {
            status.setText(R.string.play_no_piece);
            return;
        }

        ((TextView) findViewById(R.id.text_piece_title)).setText(piece.title);
        overlayView.setNotes(piece.notes);
        overlayView.setPointer(pointer);
        if (!piece.notes.isEmpty()) {
            NoteEvent firstExpected = piece.notes.get(pointer);
            overlayView.setFrequencies(expectedFrequencyFor(firstExpected), 0f);
        }

        RadioGroup modeGroup = findViewById(R.id.group_play_mode);
        modeGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.radio_mode_midi) {
                    overlayView.setMicMode(false);
                    stopTablaturePlayback();
                    pitchAnalyzer.stop();
                    startMidiPlayback();
                } else if (checkedId == R.id.radio_mode_tablature) {
                    overlayView.setMicMode(false);
                    stopMidiPlayback();
                    ensureMicListening();
                    startTablaturePlayback();
                } else {
                    overlayView.setMicMode(true);
                    stopMidiPlayback();
                    stopTablaturePlayback();
                    ensureMicListening();
                }
            }
        });

        overlayView.setMicMode(true);
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
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1001);
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
        }, new PitchAnalyzer.SpectrumListener() {
            @Override
            public void onSpectrum(final float[] magnitudes, final int sampleRate) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (currentInputIntensity < intensityThreshold) {
                            overlayView.setSpectrum(new float[magnitudes.length], sampleRate);
                        } else {
                            overlayView.setSpectrum(magnitudes, sampleRate);
                        }
                    }
                });
            }
        }, new PitchAnalyzer.AudioListener() {
            @Override
            public void onAudio(short[] samples, int length, int sampleRate) {
                currentInputIntensity = rms(samples, length);
            }
        });
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

    private void consumePitch(float hz) {
        if (piece == null || pointer >= piece.notes.size()) {
            return;
        }

        NoteEvent expected = piece.notes.get(pointer);
        String expectedName = expected.fullName();
        float expectedFrequency = expectedFrequencyFor(expected);

        if (currentInputIntensity < intensityThreshold) {
            overlayView.setFrequencies(expectedFrequency, 0f);
            overlayView.setPointer(pointer);
            status.setText(getString(R.string.play_waiting_intensity, intensityThreshold));
            return;
        }

        float normalizedHz = normalizeDetectedPitch(hz, expectedFrequency);
        String detected = mapper.fromFrequency(normalizedHz);

        overlayView.setFrequencies(expectedFrequency, normalizedHz);
        overlayView.setPointer(pointer);
        status.setText(getString(R.string.play_status_template,
                MusicNotation.toEuropeanLabel(expected.noteName, expected.octave),
                toEuropeanLabelFromFull(detected),
                (int) normalizedHz));

        if (!detected.equals(expectedName)) {
            return;
        }

        pointer++;
        if (pointer < piece.notes.size()) {
            overlayView.setPointer(pointer);
            NoteEvent nextExpected = piece.notes.get(pointer);
            overlayView.setFrequencies(expectedFrequencyFor(nextExpected), 0f);
        } else {
            status.setText(R.string.play_done);
            stopTablaturePlayback();
        }
    }


    private float expectedFrequencyFor(NoteEvent note) {
        if (note == null) {
            return 0f;
        }
        float mapped = mapper.frequencyFor(note.fullName());
        if (mapped > 0f) {
            return mapped;
        }
        int midi = MusicNotation.midiFor(note.noteName, note.octave);
        return (float) (440.0 * Math.pow(2.0, (midi - 69) / 12.0));
    }

    private float normalizeDetectedPitch(float detectedHz, float expectedFrequency) {
        if (detectedHz <= 0f) {
            return detectedHz;
        }

        float halvedHz = detectedHz / 2f;
        float minMappedHz = mapper.frequencyFor("D4");
        float maxMappedHz = mapper.frequencyFor("A6");

        boolean directInRange = detectedHz >= minMappedHz && detectedHz <= maxMappedHz;
        boolean halvedInRange = halvedHz >= minMappedHz && halvedHz <= maxMappedHz;

        if (!directInRange && halvedInRange) {
            return halvedHz;
        }

        if (expectedFrequency <= 0f) {
            return detectedHz;
        }

        float directDiff = Math.abs(detectedHz - expectedFrequency);
        float halvedDiff = Math.abs(halvedHz - expectedFrequency);

        if (halvedInRange && halvedDiff < directDiff) {
            return halvedHz;
        }

        return detectedHz;
    }

    private void startMidiPlayback() {
        int focusToken = requestMusicFocus();
        if (focusToken == 0) {
            status.setText(R.string.play_midi_failed);
            return;
        }
        midiFocusToken = focusToken;
        midiPlaybackRequested = true;
        pointer = 0;
        overlayView.setPointer(pointer);
        status.setText(R.string.play_midi_started);
        if (midiThread != null && midiThread.isAlive()) {
            return;
        }

        final int threadFocusToken = focusToken;
        midiThread = new Thread(new Runnable() {
            @Override
            public void run() {
                playNotesWithSynth(true, threadFocusToken);
            }
        }, "midi-playback");
        midiThread.start();
    }

    private void stopMidiPlayback() {
        midiPlaybackRequested = false;
        Thread thread = midiThread;
        if (thread != null) {
            thread.interrupt();
            joinThreadQuietly(thread);
            midiThread = null;
        }
        abandonMusicFocusIfOwned(midiFocusToken);
        midiFocusToken = 0;
    }

    private void startTablaturePlayback() {
        int focusToken = requestMusicFocus();
        if (focusToken == 0) {
            status.setText(R.string.play_midi_failed);
            return;
        }
        tablatureFocusToken = focusToken;
        tablaturePlaybackRequested = true;
        pointer = 0;
        overlayView.setPointer(pointer);
        analyzeTablatureFrequencies();
        if (tablatureThread != null && tablatureThread.isAlive()) {
            return;
        }
        status.setText(R.string.play_tablature_started);

        final int threadFocusToken = focusToken;
        tablatureThread = new Thread(new Runnable() {
            @Override
            public void run() {
                playNotesWithSynth(false, threadFocusToken);
            }
        }, "tablature-playback");
        tablatureThread.start();
    }

    private void stopTablaturePlayback() {
        tablaturePlaybackRequested = false;
        Thread thread = tablatureThread;
        if (thread != null) {
            thread.interrupt();
            joinThreadQuietly(thread);
            tablatureThread = null;
        }
        abandonMusicFocusIfOwned(tablatureFocusToken);
        tablatureFocusToken = 0;
    }

    private void joinThreadQuietly(Thread thread) {
        try {
            thread.join(250);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private void playNotesWithSynth(final boolean midiMode, final int focusToken) {
        if (piece == null || piece.notes.isEmpty()) {
            return;
        }

        final int channelConfig = AudioFormat.CHANNEL_OUT_MONO;
        final int encoding = AudioFormat.ENCODING_PCM_16BIT;
        int sampleRate = SYNTH_SAMPLE_RATE;
        int channelCount = 1;
        int bytesPerSample = 2;
        int frameSize = channelCount * bytesPerSample;

        int minBuffer = AudioTrack.getMinBufferSize(sampleRate, channelConfig, encoding);
        int targetFrames = Math.max(sampleRate / 4, 1024);
        int requestedBuffer = targetFrames * frameSize;

        if (minBuffer <= 0 || sampleRate <= 0) {
            sampleRate = SAFE_SAMPLE_RATE;
            minBuffer = AudioTrack.getMinBufferSize(sampleRate, channelConfig, encoding);
            targetFrames = Math.max(sampleRate / 4, 1024);
            requestedBuffer = targetFrames * frameSize;
        }

        int bufferSize = Math.max(minBuffer, requestedBuffer);
        if (bufferSize % frameSize != 0) {
            bufferSize += frameSize - (bufferSize % frameSize);
        }

        Log.i(TAG, "AudioTrack config: sampleRate=" + sampleRate
                + ", channelConfig=" + channelConfig
                + ", encoding=" + encoding
                + ", minBuf=" + minBuffer
                + ", requestedBuf=" + requestedBuffer
                + ", bufferSize=" + bufferSize);

        if (minBuffer <= 0 || bufferSize <= 0) {
            postPlaybackError();
            setPlaybackRequested(midiMode, false);
            return;
        }

        AudioTrack track = null;
        try {
            track = new AudioTrack(AudioManager.STREAM_MUSIC,
                    sampleRate,
                    channelConfig,
                    encoding,
                    bufferSize,
                    AudioTrack.MODE_STREAM);
            if (track.getState() != AudioTrack.STATE_INITIALIZED) {
                postPlaybackError();
                setPlaybackRequested(midiMode, false);
                return;
            }
            track.play();

            short[] buffer = new short[Math.max(sampleRate / 8, 512)];
            for (int i = 0; i < piece.notes.size() && playbackRequested(midiMode); i++) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
                final NoteEvent note = piece.notes.get(i);
                final int idx = i;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!midiMode) {
                            pointer = idx;
                        }
                        overlayView.setPointer(idx);
                        status.setText(getString(midiMode ? R.string.play_midi_note : R.string.play_tablature_note,
                                MusicNotation.toEuropeanLabel(note.noteName, note.octave)));
                    }
                });

                double freq = midiMode
                        ? midiToFrequency(MusicNotation.midiFor(note.noteName, note.octave))
                        : resolveTablatureFrequency(note);
                int ms = durationMs(note.duration);
                int totalSamples = sampleRate * ms / 1000;
                int fadeSamples = Math.min(sampleRate * ENVELOPE_FADE_MS / 1000, totalSamples / 2);
                int written = 0;
                while (written < totalSamples && playbackRequested(midiMode)) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }
                    int chunk = Math.min(buffer.length, totalSamples - written);
                    for (int s = 0; s < chunk; s++) {
                        double t = (written + s) / (double) sampleRate;
                        float envelope = amplitudeEnvelope(written + s, totalSamples, fadeSamples);
                        buffer[s] = (short) (Math.sin(2d * Math.PI * freq * t) * 12000 * envelope);
                    }
                    int result = track.write(buffer, 0, chunk);
                    if (result <= 0) {
                        setPlaybackRequested(midiMode, false);
                        postPlaybackError();
                        break;
                    }
                    written += result;
                }
            }
        } catch (IllegalStateException ignored) {
            setPlaybackRequested(midiMode, false);
            postPlaybackError();
        } finally {
            if (track != null) {
                try {
                    if (track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                        track.stop();
                    }
                } catch (IllegalStateException ignored) {
                }
                track.release();
            }
            if (midiMode) {
                midiThread = null;
                midiFocusToken = 0;
            } else {
                tablatureThread = null;
                tablatureFocusToken = 0;
            }
            if (playbackRequested(midiMode)) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        status.setText(midiMode ? R.string.play_midi_finished : R.string.play_tablature_finished);
                    }
                });
            }
            setPlaybackRequested(midiMode, false);
            abandonMusicFocusIfOwned(focusToken);
        }
    }

    private void postPlaybackError() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                status.setText(R.string.play_midi_failed);
            }
        });
    }

    private boolean playbackRequested(boolean midiMode) {
        return midiMode ? midiPlaybackRequested : tablaturePlaybackRequested;
    }

    private void setPlaybackRequested(boolean midiMode, boolean value) {
        if (midiMode) {
            midiPlaybackRequested = value;
        } else {
            tablaturePlaybackRequested = value;
        }
    }

    private int requestMusicFocus() {
        if (audioManager == null) {
            synchronized (focusLock) {
                activeFocusToken = ++focusCounter;
                return activeFocusToken;
            }
        }
        int result = audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            return 0;
        }
        synchronized (focusLock) {
            activeFocusToken = ++focusCounter;
            return activeFocusToken;
        }
    }

    private void abandonMusicFocusIfOwned(int focusToken) {
        if (focusToken == 0) {
            return;
        }
        synchronized (focusLock) {
            if (activeFocusToken != focusToken) {
                return;
            }
            activeFocusToken = 0;
            if (audioManager != null) {
                audioManager.abandonAudioFocus(null);
            }
        }
    }

    private int durationMs(String duration) {
        if ("eighth".equals(duration)) return 240;
        if ("half".equals(duration)) return 900;
        return 450;
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

    private double resolveTablatureFrequency(NoteEvent note) {
        String noteName = note.fullName();
        float mappedFrequency = mapper.frequencyFor(noteName);
        if (mappedFrequency > 0f) {
            return mappedFrequency;
        }
        return midiToFrequency(MusicNotation.midiFor(note.noteName, note.octave));
    }

    private void analyzeTablatureFrequencies() {
        if (piece == null || piece.notes.isEmpty()) {
            return;
        }
        int exactMatches = 0;
        for (NoteEvent note : piece.notes) {
            String expected = note.fullName();
            String synthesized = mapper.fromFrequency((float) resolveTablatureFrequency(note));
            if (expected.equals(synthesized)) {
                exactMatches++;
            } else {
                Log.w(TAG, "Tablature frequency label mismatch: expected=" + expected + ", synthesized=" + synthesized);
            }
        }
        Log.i(TAG, "Tablature frequency pre-check: " + exactMatches + "/" + piece.notes.size() + " labels match mapper mapping");
    }

    private double midiToFrequency(int midi) {
        return 440.0 * Math.pow(2.0, (midi - 69) / 12.0);
    }

    private String toEuropeanLabelFromFull(String fullName) {
        if (fullName == null || fullName.length() < 2) {
            return fullName;
        }
        String note = fullName.substring(0, fullName.length() - 1);
        int octave;
        try {
            octave = Integer.parseInt(fullName.substring(fullName.length() - 1));
        } catch (NumberFormatException ex) {
            return fullName;
        }
        return MusicNotation.toEuropeanLabel(note, octave);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startListening();
            } else {
                status.setText(R.string.play_microphone_denied);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        pitchAnalyzer.stop();
        stopMidiPlayback();
        stopTablaturePlayback();
    }
}
