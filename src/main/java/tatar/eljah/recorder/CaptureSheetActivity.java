package tatar.eljah.recorder;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import tatar.eljah.fluitblox.R;

public class CaptureSheetActivity extends AppCompatActivity {
    private static final int REQ_CAMERA = 410;
    private static final int REQ_CAMERA_PERMISSION = 411;
    private static final int REQ_PICK_IMAGE = 412;

    private Bitmap capturedBitmap;
    private TextView analysisText;
    private TextView thresholdValueText;
    private TextView noiseValueText;
    private RecognitionOverlayView notesOverlay;
    private OpenCvScoreProcessor.ProcessingResult latestResult;
    private Bitmap sourceBitmapForProcessing;
    private int thresholdOffset = 7;
    private float noiseLevel = 0.5f;
    private Thread processingThread;
    private int processingToken;
    private LinearLayout staffSlidersLayout;
    private final ArrayList<Float> perStaffFilterStrength = new ArrayList<Float>();
    private final ArrayList<SeekBar> perStaffSeekBars = new ArrayList<SeekBar>();

    private static final float BEST_MIN_AREA = 0.35f;
    private static final float BEST_MAX_AREA = 2.6f;
    private static final float BEST_MIN_FILL = 0.08f;
    private static final float BEST_MIN_CIRCULARITY = 0.14f;
    private static final float BEST_ANALYTICAL_STRENGTH = 0.85f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_capture_sheet);
        ReferenceComposition.loadFromAssets(getAssets());

        final EditText titleInput = findViewById(R.id.input_piece_title);
        final ImageView preview = findViewById(R.id.image_preview);
        analysisText = findViewById(R.id.text_analysis);
        analysisText.setTextIsSelectable(true);
        thresholdValueText = findViewById(R.id.text_threshold_value);
        noiseValueText = findViewById(R.id.text_noise_value);
        notesOverlay = findViewById(R.id.image_notes_overlay);
        staffSlidersLayout = findViewById(R.id.layout_staff_sliders);
        SeekBar thresholdSeek = findViewById(R.id.seek_threshold);
        SeekBar noiseSeek = findViewById(R.id.seek_noise);

        thresholdSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                thresholdOffset = 2 + progress;
                renderControlValues();
                if (fromUser) {
                    rerunProcessing();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                rerunProcessing();
            }
        });
        noiseSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                noiseLevel = progress / 100f;
                renderControlValues();
                if (fromUser) {
                    rerunProcessing();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                rerunProcessing();
            }
        });
        renderControlValues();

        findViewById(R.id.btn_open_camera).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openCamera();
            }
        });

        findViewById(R.id.btn_pick_gallery).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openGallery();
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
                    result = new OpenCvScoreProcessor().process(capturedBitmap, title, currentOptions());
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

    @Override
    protected void onDestroy() {
        processingToken++;
        Thread running = processingThread;
        if (running != null) {
            running.interrupt();
            processingThread = null;
        }
        super.onDestroy();
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

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, getString(R.string.capture_pick_gallery)), REQ_PICK_IMAGE);
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
                sourceBitmapForProcessing = bmp;
                rerunProcessing();
            }
        } else if (requestCode == REQ_PICK_IMAGE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                Bitmap bmp = decodeGalleryBitmap(uri, 1600);
                if (bmp != null) {
                    capturedBitmap = bmp;
                    sourceBitmapForProcessing = bmp;
                    rerunProcessing();
                } else {
                    Toast.makeText(this, R.string.capture_gallery_load_failed, Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(this, R.string.capture_gallery_load_failed, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private Bitmap decodeGalleryBitmap(Uri uri, int maxDim) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        InputStream boundsStream = getContentResolver().openInputStream(uri);
        try {
            BitmapFactory.decodeStream(boundsStream, null, bounds);
        } finally {
            if (boundsStream != null) boundsStream.close();
        }

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        opts.inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDim);
        InputStream decodeStream = getContentResolver().openInputStream(uri);
        try {
            return BitmapFactory.decodeStream(decodeStream, null, opts);
        } finally {
            if (decodeStream != null) decodeStream.close();
        }
    }

    static int calculateInSampleSize(int width, int height, int maxDim) {
        if (width <= 0 || height <= 0 || maxDim <= 0) {
            return 1;
        }
        int sample = 1;
        while ((width / sample) > maxDim || (height / sample) > maxDim) {
            sample <<= 1;
        }
        return Math.max(1, sample);
    }

    private OpenCvScoreProcessor.ProcessingOptions currentOptions() {
        int neighborhoodHits = noiseLevel >= 0.66f ? 5 : (noiseLevel >= 0.33f ? 4 : 3);
        float[] perStaff = null;
        if (!perStaffFilterStrength.isEmpty()) {
            perStaff = new float[perStaffFilterStrength.size()];
            for (int i = 0; i < perStaff.length; i++) {
                perStaff[i] = perStaffFilterStrength.get(i);
            }
        }
        return new OpenCvScoreProcessor.ProcessingOptions(
                thresholdOffset,
                neighborhoodHits,
                noiseLevel,
                true,
                true,
                BEST_MIN_AREA,
                BEST_MAX_AREA,
                BEST_MIN_FILL,
                0.95f,
                BEST_MIN_CIRCULARITY,
                true,
                BEST_ANALYTICAL_STRENGTH,
                perStaff);
    }

    private void rerunProcessing() {
        final Bitmap bmp = sourceBitmapForProcessing;
        if (bmp == null) {
            return;
        }
        final OpenCvScoreProcessor.ProcessingOptions options = currentOptions();
        final int token = ++processingToken;
        Thread previous = processingThread;
        if (previous != null && previous.isAlive()) {
            previous.interrupt();
        }
        processingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final OpenCvScoreProcessor.ProcessingResult result = new OpenCvScoreProcessor().process(bmp, "draft", options);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (isFinishing() || token != processingToken) {
                                return;
                            }
                            latestResult = result;
                            Bitmap previewBitmap = result.debugOverlay != null ? result.debugOverlay : bmp;
                            ImageView preview = findViewById(R.id.image_preview);
                            preview.setImageBitmap(previewBitmap);
                            updateOverlayBounds(preview, previewBitmap);
                            notesOverlay.setRecognizedNotes(result.piece.notes);
                            notesOverlay.setStaffCorridors(result.staffCorridors);
                            syncStaffSliders(result);
                            analysisText.setText(getString(R.string.capture_analysis_template,
                                    result.perpendicularScore,
                                    result.staffRows,
                                    result.barlines,
                                    result.piece.notes.size()) + "\n"
                                    + getString(R.string.capture_parameters_template, thresholdOffset, Math.round(noiseLevel * 100f)) + "\n"
                                    + getString(R.string.capture_staff_knowledge_applied, result.staffRows) + "\n"
                                    + "Processing mode: " + formatProcessingMode(result) + "\n"
                                    + formatOpenCvFailureDetails(result)
                                    + getString(R.string.capture_antiglare_applied) + "\n"
                                    + getString(R.string.capture_debug_colors) + "\n"
                                    + getString(R.string.capture_expected_notes));
                        }
                    });
                } catch (final Throwable t) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (isFinishing() || token != processingToken) {
                                return;
                            }
                            Toast.makeText(CaptureSheetActivity.this, R.string.capture_gallery_load_failed, Toast.LENGTH_SHORT).show();
                            analysisText.setText(t.getClass().getSimpleName());
                        }
                    });
                }
            }
        }, "sheet-processing");
        processingThread.start();
    }

    private String formatOpenCvFailureDetails(OpenCvScoreProcessor.ProcessingResult result) {
        if (result == null || result.openCvUsed) {
            return "";
        }
        String stack = result.openCvStackTrace;
        if (stack == null || stack.trim().length() == 0) {
            return "";
        }
        return "OpenCV fallback stacktrace:\n" + stack + "\n";
    }

    private String formatProcessingMode(OpenCvScoreProcessor.ProcessingResult result) {
        if (result == null) {
            return "legacy";
        }
        if (result.openCvUsed) {
            return "OpenCV";
        }
        return "legacy (fallback)";
    }

    private void updateOverlayBounds(ImageView preview, Bitmap shownBitmap) {
        if (preview == null || shownBitmap == null || notesOverlay == null) {
            return;
        }
        int viewW = preview.getWidth();
        int viewH = preview.getHeight();
        if (viewW <= 0 || viewH <= 0) {
            notesOverlay.setImageBounds(0f, 0f, 1f, 1f);
            return;
        }

        Drawable drawable = preview.getDrawable();
        if (drawable == null || drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
            float scale = Math.min(viewW / (float) shownBitmap.getWidth(), viewH / (float) shownBitmap.getHeight());
            float drawW = shownBitmap.getWidth() * scale;
            float drawH = shownBitmap.getHeight() * scale;
            float left = (viewW - drawW) * 0.5f;
            float top = (viewH - drawH) * 0.5f;
            notesOverlay.setImageBounds(left, top, left + drawW, top + drawH);
            return;
        }

        RectF rect = new RectF(0f, 0f, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        Matrix matrix = new Matrix(preview.getImageMatrix());
        matrix.mapRect(rect);
        rect.offset(preview.getPaddingLeft(), preview.getPaddingTop());
        notesOverlay.setImageBounds(rect.left, rect.top, rect.right, rect.bottom);
    }


    private void syncStaffSliders(OpenCvScoreProcessor.ProcessingResult result) {
        int staffCount = 0;
        if (result != null && result.staffCorridors != null) {
            staffCount = result.staffCorridors.size();
        }
        if (staffCount <= 0 && result != null) {
            staffCount = Math.max(1, result.staffRows);
        }
        if (staffCount <= 0) {
            staffCount = 1;
        }

        if (perStaffFilterStrength.size() != staffCount) {
            perStaffFilterStrength.clear();
            for (int i = 0; i < staffCount; i++) {
                perStaffFilterStrength.add(BEST_ANALYTICAL_STRENGTH);
            }
            rebuildStaffSliders(staffCount);
        }

        int[] noteCounts = countNotesByStaff(result, staffCount);
        for (int i = 0; i < perStaffSeekBars.size(); i++) {
            SeekBar seekBar = perStaffSeekBars.get(i);
            TextView label = (TextView) seekBar.getTag();
            int pct = Math.round(perStaffFilterStrength.get(i) * 100f);
            if (label != null) {
                label.setText(getString(R.string.capture_staff_slider_item, i + 1, pct, noteCounts[i]));
            }
        }
    }

    private int[] countNotesByStaff(OpenCvScoreProcessor.ProcessingResult result, int staffCount) {
        int[] counts = new int[staffCount];
        if (result == null || result.piece == null || result.piece.notes == null) {
            return counts;
        }
        List<OpenCvScoreProcessor.StaffCorridor> corridors = result.staffCorridors;
        for (NoteEvent n : result.piece.notes) {
            int idx = 0;
            if (corridors != null && !corridors.isEmpty()) {
                idx = nearestCorridorIndex(n.y, corridors);
                if (idx < 0) idx = 0;
            } else {
                idx = Math.min(staffCount - 1, Math.max(0, (int) (n.y * staffCount)));
            }
            if (idx >= 0 && idx < counts.length) counts[idx]++;
        }
        return counts;
    }

    private int nearestCorridorIndex(float y, List<OpenCvScoreProcessor.StaffCorridor> corridors) {
        int best = -1;
        float bestDist = Float.MAX_VALUE;
        for (int i = 0; i < corridors.size(); i++) {
            OpenCvScoreProcessor.StaffCorridor c = corridors.get(i);
            float cy = (c.top + c.bottom) * 0.5f;
            float dist = Math.abs(y - cy);
            if (dist < bestDist) {
                bestDist = dist;
                best = i;
            }
        }
        return best;
    }

    private void rebuildStaffSliders(int staffCount) {
        if (staffSlidersLayout == null) return;
        staffSlidersLayout.removeAllViews();
        perStaffSeekBars.clear();
        for (int i = 0; i < staffCount; i++) {
            final int idx = i;
            TextView label = new TextView(this);
            int pct = Math.round(perStaffFilterStrength.get(i) * 100f);
            label.setText(getString(R.string.capture_staff_slider_item, i + 1, pct, 0));
            staffSlidersLayout.addView(label);

            SeekBar seek = new SeekBar(this);
            seek.setMax(100);
            seek.setProgress(pct);
            seek.setTag(label);
            seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    perStaffFilterStrength.set(idx, progress / 100f);
                    TextView tv = (TextView) seekBar.getTag();
                    if (tv != null) {
                        int[] counts = countNotesByStaff(latestResult, perStaffFilterStrength.size());
                        int notes = idx < counts.length ? counts[idx] : 0;
                        tv.setText(getString(R.string.capture_staff_slider_item, idx + 1, progress, notes));
                    }
                    if (fromUser) {
                        rerunProcessing();
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    rerunProcessing();
                }
            });
            staffSlidersLayout.addView(seek);
            perStaffSeekBars.add(seek);
        }
    }

    private void renderControlValues() {
        thresholdValueText.setText(getString(R.string.capture_threshold_template, thresholdOffset));
        noiseValueText.setText(getString(R.string.capture_noise_template, Math.round(noiseLevel * 100f)));
    }
}
