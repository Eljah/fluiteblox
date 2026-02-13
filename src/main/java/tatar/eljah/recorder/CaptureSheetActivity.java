package tatar.eljah.recorder;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.recordercoach.app.R;

public class CaptureSheetActivity extends AppCompatActivity {
    private static final int REQ_CAMERA = 410;

    private Bitmap capturedBitmap;
    private TextView analysisText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_capture_sheet);

        final EditText titleInput = findViewById(R.id.input_piece_title);
        final ImageView preview = findViewById(R.id.image_preview);
        analysisText = findViewById(R.id.text_analysis);

        findViewById(R.id.btn_open_camera).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                startActivityForResult(intent, REQ_CAMERA);
            }
        });

        findViewById(R.id.btn_save_piece).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (capturedBitmap == null) {
                    Toast.makeText(CaptureSheetActivity.this, R.string.capture_take_photo_first, Toast.LENGTH_SHORT).show();
                    return;
                }
                String title = titleInput.getText().toString().trim();
                if (title.length() == 0) {
                    Toast.makeText(CaptureSheetActivity.this, R.string.capture_title_required, Toast.LENGTH_SHORT).show();
                    return;
                }
                OpenCvScoreProcessor.ProcessingResult result = new OpenCvScoreProcessor().process(capturedBitmap, title);
                new ScoreLibraryRepository(CaptureSheetActivity.this).savePiece(result.piece);
                Toast.makeText(CaptureSheetActivity.this, R.string.capture_saved, Toast.LENGTH_SHORT).show();
                startActivity(new Intent(CaptureSheetActivity.this, LibraryActivity.class));
                finish();
            }
        });

        preview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                startActivityForResult(intent, REQ_CAMERA);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CAMERA && resultCode == RESULT_OK && data != null && data.getExtras() != null) {
            Bitmap bmp = (Bitmap) data.getExtras().get("data");
            if (bmp != null) {
                capturedBitmap = bmp;
                ((ImageView) findViewById(R.id.image_preview)).setImageBitmap(bmp);
                OpenCvScoreProcessor.ProcessingResult result = new OpenCvScoreProcessor().process(bmp, "draft");
                analysisText.setText(getString(R.string.capture_analysis_template,
                        result.perpendicularScore,
                        result.staffRows,
                        result.barlines,
                        result.piece.notes.size()));
            }
        }
    }
}
