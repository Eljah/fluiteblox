package tatar.eljah.recorder;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import com.recordercoach.app.R;

public class FingeringHintActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fingering_hint);

        String expected = getIntent().getStringExtra("expected");
        String actual = getIntent().getStringExtra("actual");
        RecorderNoteMapper mapper = new RecorderNoteMapper();

        ((TextView) findViewById(R.id.text_expected_note)).setText(getString(R.string.hint_expected, expected, mapper.fingeringFor(expected)));
        ((TextView) findViewById(R.id.text_actual_note)).setText(getString(R.string.hint_actual, actual, mapper.fingeringFor(actual)));
    }
}
