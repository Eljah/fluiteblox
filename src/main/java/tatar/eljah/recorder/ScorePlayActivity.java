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

import tatar.eljah.audio.PitchAnalyzer;
import tatar.eljah.fluitblox.R;

public class ScorePlayActivity extends AppCompatActivity {
    public static final String EXTRA_PIECE_ID = "piece_id";

    private final PitchAnalyzer pitchAnalyzer = new PitchAnalyzer();
    private final RecorderNoteMapper mapper = new RecorderNoteMapper();

    private ScorePiece piece;
    private int pointer = 0;

    private TextView status;
    private PitchOverlayView overlayView;

    private volatile boolean midiPlaybackRequested;
    private Thread midiThread;
    private static final int SYNTH_SAMPLE_RATE = 22050;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_score_play);

        String pieceId = getIntent().getStringExtra(EXTRA_PIECE_ID);
        piece = new ScoreLibraryRepository(this).findById(pieceId);

        status = findViewById(R.id.text_status);
        overlayView = findViewById(R.id.pitch_overlay);

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
                    startMidiPlayback();
                } else {
                    stopMidiPlayback();
                    ensureMicListening();
                }
            }
        });

        ensureMicListening();
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
        });
    }

    private void consumePitch(float hz) {
        if (piece == null || pointer >= piece.notes.size()) {
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
            pitchAnalyzer.stop();
        }
    }

    private void startMidiPlayback() {
        pitchAnalyzer.stop();
        midiPlaybackRequested = true;
        pointer = 0;
        overlayView.setPointer(pointer);
        status.setText(R.string.play_midi_started);
        if (midiThread != null && midiThread.isAlive()) {
            return;
        }

        midiThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    playNotesWithSynth();
                } catch (Throwable ignored) {
                    midiPlaybackRequested = false;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            status.setText(R.string.play_midi_failed);
                        }
                    });
                }
            }
        }, "midi-playback");
        midiThread.start();
    }

    private void stopMidiPlayback() {
        midiPlaybackRequested = false;
        if (midiThread != null) {
            midiThread.interrupt();
            midiThread = null;
        }
    }

    private void playNotesWithSynth() {
        if (piece == null || piece.notes.isEmpty()) {
            return;
        }
        int minBuffer = AudioTrack.getMinBufferSize(SYNTH_SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        if (minBuffer <= 0) {
            if (midiPlaybackRequested) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        status.setText(R.string.play_midi_finished);
                    }
                });
            }
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
                return;
            }

            track.play();
            short[] buffer = new short[SYNTH_SAMPLE_RATE / 8];
            for (int i = 0; i < piece.notes.size() && midiPlaybackRequested; i++) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
                final int idx = i;
                final NoteEvent note = piece.notes.get(i);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        overlayView.setPointer(idx);
                        status.setText(getString(R.string.play_midi_note,
                                MusicNotation.toEuropeanLabel(note.noteName, note.octave)));
                    }
                });

                double freq = midiToFrequency(MusicNotation.midiFor(note.noteName, note.octave));
                int ms = durationMs(note.duration);
                int totalSamples = SYNTH_SAMPLE_RATE * ms / 1000;
                int written = 0;
                while (written < totalSamples && midiPlaybackRequested) {
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
                        break;
                    }
                    written += result;
                }
            }
        } catch (IllegalStateException ignored) {
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
            midiThread = null;
            if (midiPlaybackRequested) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        status.setText(R.string.play_midi_finished);
                    }
                });
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
    }
}
