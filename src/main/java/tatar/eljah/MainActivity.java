package tatar.eljah;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import tatar.eljah.fluitblox.R;
import tatar.eljah.recorder.SettingsActivity;
import tatar.eljah.recorder.CaptureSheetActivity;
import tatar.eljah.recorder.LibraryActivity;

public class MainActivity extends AppCompatActivity {
    private static final int REQ_SETTINGS = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        tatar.eljah.recorder.AppLocaleManager.applySavedLocale(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        View captureButton = findViewById(R.id.btn_capture_score);
        View libraryButton = findViewById(R.id.btn_open_library);
        View settingsButton = findViewById(R.id.btn_audio_settings);

        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, CaptureSheetActivity.class));
            }
        });

        libraryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, LibraryActivity.class));
            }
        });

        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(new Intent(MainActivity.this, SettingsActivity.class), REQ_SETTINGS);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SETTINGS && resultCode == RESULT_OK) {
            recreate();
        }
    }
}
