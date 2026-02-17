package tatar.eljah.recorder;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

import tatar.eljah.MainActivity;
import tatar.eljah.fluitblox.R;

public class SettingsActivity extends AppCompatActivity {

    private static final String[] LANG_CODES = new String[]{"ru", "en", "de", "fr", "es", "pt", "tr", "tt", "vi", "zh", "ja", "ar"};
    private static final String[] LANG_NATIVE_LABELS = new String[]{
            "Русский",
            "English",
            "Deutsch",
            "Français",
            "Español",
            "Português",
            "Türkçe",
            "Татарча",
            "Tiếng Việt",
            "中文",
            "日本語",
            "العربية"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppLocaleManager.applySavedLocale(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        final Spinner spinner = findViewById(R.id.spinner_language);
        int selected = 0;
        String current = AppLocaleManager.savedLanguage(this);
        for (int i = 0; i < LANG_CODES.length; i++) {
            if (LANG_CODES[i].equals(current)) {
                selected = i;
                break;
            }
        }

        spinner.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, LANG_NATIVE_LABELS));
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
                    android.content.Intent home = new android.content.Intent(SettingsActivity.this, MainActivity.class);
                    home.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(home);
                }
                finish();
            }
        });
    }
}
