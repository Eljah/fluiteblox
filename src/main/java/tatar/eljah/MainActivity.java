package tatar.eljah;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import com.recordercoach.app.R;

import tatar.eljah.recorder.CaptureSheetActivity;
import tatar.eljah.recorder.LibraryActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        View captureButton = findViewById(R.id.btn_capture_score);
        View libraryButton = findViewById(R.id.btn_open_library);

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
    }
}
