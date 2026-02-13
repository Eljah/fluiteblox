package tatar.eljah.recorder;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import com.recordercoach.app.R;
import tatar.eljah.audio.PitchAnalyzer;

public class ScorePlayActivity extends AppCompatActivity {
    public static final String EXTRA_PIECE_ID = "piece_id";

    private final PitchAnalyzer pitchAnalyzer = new PitchAnalyzer();
    private final RecorderNoteMapper mapper = new RecorderNoteMapper();
    private final List<TextView> noteViews = new ArrayList<TextView>();

    private ScorePiece piece;
    private int pointer = 0;
    private String lastDetected = "";

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
        renderNotes();

        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1001);
        } else {
            startListening();
        }
    }

    private void renderNotes() {
        LinearLayout holder = findViewById(R.id.notes_holder);
        holder.removeAllViews();
        noteViews.clear();
        for (int i = 0; i < piece.notes.size(); i++) {
            final int index = i;
            final NoteEvent event = piece.notes.get(i);
            TextView tv = new TextView(this);
            tv.setText(event.fullName());
            tv.setTextSize(20f);
            tv.setPadding(16, 16, 16, 16);
            tv.setOnClickListener(new android.view.View.OnClickListener() {
                @Override
                public void onClick(android.view.View v) {
                    if (tv.getCurrentTextColor() == Color.RED) {
                        Intent intent = new Intent(ScorePlayActivity.this, FingeringHintActivity.class);
                        intent.putExtra("expected", event.fullName());
                        intent.putExtra("actual", lastDetected);
                        startActivity(intent);
                    }
                }
            });
            holder.addView(tv);
            noteViews.add(tv);
        }
        noteViews.get(0).setTextColor(Color.parseColor("#2E7D32"));
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
        lastDetected = detected;

        NoteEvent expected = piece.notes.get(pointer);
        String expectedName = expected.fullName();
        overlayView.setFrequencies(mapper.frequencyFor(expectedName), hz);
        status.setText(getString(R.string.play_status_template, expectedName, detected, (int) hz));

        if (!detected.equals(expectedName)) {
            noteViews.get(pointer).setTextColor(Color.RED);
            return;
        }

        noteViews.get(pointer).setTextColor(Color.parseColor("#2E7D32"));
        pointer++;
        if (pointer < noteViews.size()) {
            noteViews.get(pointer).setTextColor(Color.parseColor("#2E7D32"));
        } else {
            status.setText(R.string.play_done);
            pitchAnalyzer.stop();
        }
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
