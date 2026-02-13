package tatar.eljah.recorder;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import tatar.eljah.fluitblox.R;

public class CaptureSheetActivity extends AppCompatActivity {
    private static final int REQ_CAMERA = 410;
    private static final int REQ_CAMERA_PERMISSION = 411;

    private Bitmap capturedBitmap;
    private TextView analysisText;
    private RecognitionOverlayView notesOverlay;
    private OpenCvScoreProcessor.ProcessingResult latestResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_capture_sheet);
        ReferenceComposition.loadFromAssets(getAssets());

        final EditText titleInput = findViewById(R.id.input_piece_title);
        final ImageView preview = findViewById(R.id.image_preview);
        analysisText = findViewById(R.id.text_analysis);
        notesOverlay = findViewById(R.id.image_notes_overlay);

        findViewById(R.id.btn_open_camera).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openCamera();
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
                OpenCvScoreProcessor.ProcessingResult result = latestResult;
                if (result == null) {
                    result = new OpenCvScoreProcessor().process(capturedBitmap, title);
                }
                result.piece.title = title;
                new ScoreLibraryRepository(CaptureSheetActivity.this).savePiece(result.piece);
                Toast.makeText(CaptureSheetActivity.this, R.string.capture_saved, Toast.LENGTH_SHORT).show();
                startActivity(new Intent(CaptureSheetActivity.this, LibraryActivity.class));
                finish();
            }
        });

        preview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openCamera();
            }
        });
    }

    private void openCamera() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.CAMERA},
                    REQ_CAMERA_PERMISSION);
            return;
        }

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) == null) {
            Toast.makeText(this, R.string.capture_camera_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        startActivityForResult(intent, REQ_CAMERA);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, R.string.capture_camera_permission_denied, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CAMERA && resultCode == RESULT_OK && data != null && data.getExtras() != null) {
            Bitmap bmp = (Bitmap) data.getExtras().get("data");
            if (bmp != null) {
                capturedBitmap = bmp;
                latestResult = new OpenCvScoreProcessor().process(bmp, "draft");
                Bitmap previewBitmap = latestResult.debugOverlay != null ? latestResult.debugOverlay : bmp;
                ((ImageView) findViewById(R.id.image_preview)).setImageBitmap(previewBitmap);
                notesOverlay.setRecognizedNotes(latestResult.piece.notes);
                analysisText.setText(getString(R.string.capture_analysis_template,
                        latestResult.perpendicularScore,
                        latestResult.staffRows,
                        latestResult.barlines,
                        latestResult.piece.notes.size()) + "\n" + getString(R.string.capture_debug_colors) + "\n" + getString(R.string.capture_expected_notes));
            }
        }
    }
}
