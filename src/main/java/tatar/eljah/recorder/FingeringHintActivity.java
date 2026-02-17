package tatar.eljah.recorder;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import tatar.eljah.fluitblox.R;

public class FingeringHintActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fingering_hint);

        String expectedFullName = sanitizeNoteKey(getIntent().getStringExtra("expected"));
        String actualFullName = sanitizeNoteKey(getIntent().getStringExtra("actual"));
        int noteIndex = getIntent().getIntExtra("note_index", -1);
        boolean durationMismatch = getIntent().getBooleanExtra("duration_mismatch", false);
        String expectedDuration = getIntent().getStringExtra("expected_duration");
        RecorderNoteMapper mapper = new RecorderNoteMapper();

        String expectedLabel = toEuropean(expectedFullName);
        String actualLabel = toEuropean(actualFullName);

        if (noteIndex > 0) {
            ((TextView) findViewById(R.id.text_note_index)).setText(getString(R.string.hint_note_index, noteIndex));
        }

        TextView durationHintView = findViewById(R.id.text_duration_hint);
        if (durationMismatch) {
            durationHintView.setText(getString(R.string.hint_duration_mismatch, durationLabel(expectedDuration)));
        } else {
            durationHintView.setText(R.string.hint_duration_ok);
        }

        ((TextView) findViewById(R.id.text_expected_note)).setText(getString(
                R.string.hint_expected,
                expectedLabel,
                mapper.fingeringFor(expectedFullName)));
        ((TextView) findViewById(R.id.text_actual_note)).setText(getString(
                R.string.hint_actual,
                actualLabel,
                mapper.fingeringFor(actualFullName)));
    }

    private String sanitizeNoteKey(String fullName) {
        if (fullName == null) {
            return "";
        }
        return fullName.trim();
    }

    private String durationLabel(String duration) {
        if ("whole".equals(duration)) return getString(R.string.hint_duration_whole);
        if ("half".equals(duration)) return getString(R.string.hint_duration_half);
        if ("quarter".equals(duration)) return getString(R.string.hint_duration_quarter);
        if ("eighth".equals(duration)) return getString(R.string.hint_duration_eighth);
        if ("16th".equals(duration)) return getString(R.string.hint_duration_sixteenth);
        return duration == null ? "?" : duration;
    }

    private String toEuropean(String fullName) {
        if (fullName == null || fullName.length() < 2) {
            return "?";
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
}
