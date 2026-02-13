package tatar.eljah.recorder;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.RadioGroup;
import android.widget.TextView;

import tatar.eljah.audio.AudioSettingsStore;
import tatar.eljah.audio.PitchAnalyzer;
import tatar.eljah.fluitblox.R;

public class ScorePlayActivity extends AppCompatActivity {
    public static final String EXTRA_PIECE_ID = "piece_id";
    private static final int SYNTH_SAMPLE_RATE = 22050;

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

        RadioGroup modeGroup = findViewById(R.id.group_play_mode);
        modeGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.radio_mode_midi) {
                    stopTablaturePlayback();
                    pitchAnalyzer.stop();
                    startMidiPlayback();
                } else if (checkedId == R.id.radio_mode_tablature) {
                    stopMidiPlayback();
                    ensureMicListening();
                    startTablaturePlayback();
                } else {
                    stopMidiPlayback();
                    stopTablaturePlayback();
                    ensureMicListening();
                }
            }
        });

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
                        overlayView.setSpectrum(magnitudes, sampleRate);
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
        if (currentInputIntensity < intensityThreshold) {
            status.setText(getString(R.string.play_waiting_intensity, intensityThreshold));
            return;
        }

        String detected = mapper.fromFrequency(hz);

        NoteEvent expected = piece.notes.get(pointer);
        String expectedName = expected.fullName();
        overlayView.setFrequencies(mapper.frequencyFor(expectedName), hz);
        overlayView.setPointer(pointer);
        status.setText(getString(R.string.play_status_template,
                MusicNotation.toEuropeanLabel(expected.noteName, expected.octave),
                toEuropeanLabelFromFull(detected),
                (int) hz));

        if (!detected.equals(expectedName)) {
            return;
        }

        pointer++;
        if (pointer < piece.notes.size()) {
            overlayView.setPointer(pointer);
        } else {
            status.setText(R.string.play_done);
            stopTablaturePlayback();
        }
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
        int minBuffer = AudioTrack.getMinBufferSize(SYNTH_SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        if (minBuffer <= 0) {
            postPlaybackError();
            setPlaybackRequested(midiMode, false);
            return;
        }

        AudioTrack track = null;
        try {
            track = new AudioTrack(AudioManager.STREAM_MUSIC,
                    SYNTH_SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    Math.max(minBuffer, SYNTH_SAMPLE_RATE / 2),
                    AudioTrack.MODE_STREAM);
            if (track.getState() != AudioTrack.STATE_INITIALIZED) {
                postPlaybackError();
                setPlaybackRequested(midiMode, false);
                return;
            }
            track.play();

            short[] buffer = new short[SYNTH_SAMPLE_RATE / 8];
            for (int i = 0; i < piece.notes.size() && playbackRequested(midiMode); i++) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
                final NoteEvent note = piece.notes.get(i);
                final int idx = i;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        overlayView.setPointer(idx);
                        status.setText(getString(midiMode ? R.string.play_midi_note : R.string.play_tablature_note,
                                MusicNotation.toEuropeanLabel(note.noteName, note.octave)));
                    }
                });

                double freq = midiToFrequency(MusicNotation.midiFor(note.noteName, note.octave));
                int ms = durationMs(note.duration);
                int totalSamples = SYNTH_SAMPLE_RATE * ms / 1000;
                int written = 0;
                while (written < totalSamples && playbackRequested(midiMode)) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }
                    int chunk = Math.min(buffer.length, totalSamples - written);
                    for (int s = 0; s < chunk; s++) {
                        double t = (written + s) / (double) SYNTH_SAMPLE_RATE;
                        buffer[s] = (short) (Math.sin(2d * Math.PI * freq * t) * 12000);
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
