package tatar.eljah.recorder;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import tatar.eljah.fluitblox.R;
import tatar.eljah.audio.PitchAnalyzer;

public class ScorePlayActivity extends AppCompatActivity {
    public static final String EXTRA_PIECE_ID = "piece_id";

    private final PitchAnalyzer pitchAnalyzer = new PitchAnalyzer();
    private final RecorderNoteMapper mapper = new RecorderNoteMapper();

    private ScorePiece piece;
    private int pointer = 0;

    private TextView status;
    private PitchOverlayView overlayView;

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
    }
}
