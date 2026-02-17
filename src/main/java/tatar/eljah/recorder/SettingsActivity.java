package tatar.eljah.recorder;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

import tatar.eljah.fluitblox.R;

public class SettingsActivity extends AppCompatActivity {

    private static final String[] LANG_CODES = new String[]{"ru", "en", "de", "fr", "es", "pt", "tr", "tt", "vi", "zh", "ja", "ar"};
    private static final int[] LANG_LABELS = new int[]{
            R.string.language_russian,
            R.string.language_english,
            R.string.language_german,
            R.string.language_french,
            R.string.language_spanish,
            R.string.language_portuguese,
            R.string.language_turkish,
            R.string.language_tatar,
            R.string.language_vietnamese,
            R.string.language_chinese,
            R.string.language_japanese,
            R.string.language_arabic
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppLocaleManager.applySavedLocale(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        final Spinner spinner = findViewById(R.id.spinner_language);
        String[] labels = new String[LANG_LABELS.length];
        int selected = 0;
        String current = AppLocaleManager.savedLanguage(this);
        for (int i = 0; i < LANG_LABELS.length; i++) {
            labels[i] = getString(LANG_LABELS[i]);
            if (LANG_CODES[i].equals(current)) {
                selected = i;
            }
        }

        spinner.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        spinner.setSelection(selected);

        Button apply = findViewById(R.id.btn_apply_settings);
        Button audioCalibration = findViewById(R.id.btn_open_audio_calibration);
        audioCalibration.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new android.content.Intent(SettingsActivity.this, AudioSettingsActivity.class));
            }
        });
        apply.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int index = spinner.getSelectedItemPosition();
                if (index >= 0 && index < LANG_CODES.length) {
                    AppLocaleManager.saveAndApply(SettingsActivity.this, LANG_CODES[index]);
                    setResult(RESULT_OK);
                }
                recreate();
            }
        });
    }
}
