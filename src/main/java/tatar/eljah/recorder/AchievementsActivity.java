package tatar.eljah.recorder;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import tatar.eljah.fluitblox.R;

public class AchievementsActivity extends AppCompatActivity {
    private static final float PERFECT_RATIO = 0.999f;

    private Spinner pieceSpinner;
    private AchievementGraphView graphView;
    private TextView overallSummary;
    private TextView summary;

    private List<ScorePiece> pieces = new ArrayList<ScorePiece>();
    private PerformanceMetricsStore metricsStore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_achievements);

        pieceSpinner = findViewById(R.id.spinner_piece);
        graphView = findViewById(R.id.achievement_graph);
        overallSummary = findViewById(R.id.text_achievement_overall);
        summary = findViewById(R.id.text_achievement_summary);

        metricsStore = new PerformanceMetricsStore(this);
        pieces = new ScoreLibraryRepository(this).getAllPieces();

        overallSummary.setText(buildOverallSummary());

        List<String> titles = new ArrayList<String>();
        for (ScorePiece piece : pieces) {
            titles.add(piece.title);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item,
                titles);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        pieceSpinner.setAdapter(adapter);

        pieceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                if (position >= 0 && position < pieces.size()) {
                    bindPiece(pieces.get(position));
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        if (!pieces.isEmpty()) {
            bindPiece(pieces.get(0));
        } else {
            summary.setText(R.string.achievements_no_pieces);
            graphView.setAttempts(new ArrayList<PerformanceMetricsStore.PerformanceAttempt>());
        }
    }

    private String buildOverallSummary() {
        if (pieces.isEmpty()) {
            return getString(R.string.achievements_overall_empty);
        }

        int completedPiecesCount = 0;
        int startedAttemptsCount = 0;
        int completedWithoutNoteErrorsCount = 0;
        int completedWithoutDurationErrorsCount = 0;

        for (ScorePiece piece : pieces) {
            startedAttemptsCount += metricsStore.getStartedAttempts(piece.id);
            List<PerformanceMetricsStore.PerformanceAttempt> attempts = metricsStore.getAttempts(piece.id);
            if (!attempts.isEmpty()) {
                completedPiecesCount++;
            }
            for (PerformanceMetricsStore.PerformanceAttempt attempt : attempts) {
                if (attempt.hitRatio >= PERFECT_RATIO) {
                    completedWithoutNoteErrorsCount++;
                }
                if (attempt.durationRatio >= PERFECT_RATIO) {
                    completedWithoutDurationErrorsCount++;
                }
            }
        }

        return getString(R.string.achievements_overall_template,
                completedPiecesCount,
                startedAttemptsCount,
                completedWithoutNoteErrorsCount,
                completedWithoutDurationErrorsCount);
    }

    private void bindPiece(ScorePiece piece) {
        List<PerformanceMetricsStore.PerformanceAttempt> attempts = metricsStore.getAttempts(piece.id);
        graphView.setAttempts(attempts);
        summary.setText(buildSummary(attempts));
    }

    private String buildSummary(List<PerformanceMetricsStore.PerformanceAttempt> attempts) {
        if (attempts == null || attempts.isEmpty()) {
            return getString(R.string.achievements_empty);
        }
        PerformanceMetricsStore.PerformanceAttempt latest = attempts.get(attempts.size() - 1);
        return getString(R.string.achievements_summary_template,
                attempts.size(),
                formatPercent(latest.hitRatio),
                formatPercent(latest.recoveryRatio),
                formatPercent(latest.durationRatio));
    }

    private String formatPercent(float ratio) {
        return String.format(Locale.US, "%.1f%%", ratio * 100f);
    }
}
