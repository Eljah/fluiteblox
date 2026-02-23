package tatar.eljah.recorder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class RecognitionOverlayView extends View {
    private final Paint staffPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint notePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint corridorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final List<NoteEvent> notes = new ArrayList<NoteEvent>();
    private final List<OpenCvScoreProcessor.StaffCorridor> staffCorridors = new ArrayList<OpenCvScoreProcessor.StaffCorridor>();
    private final RectF imageBounds = new RectF(0f, 0f, 1f, 1f);

    public RecognitionOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        staffPaint.setColor(Color.argb(190, 30, 30, 30));
        staffPaint.setStrokeWidth(2f);

        notePaint.setColor(Color.argb(210, 25, 118, 210));
        labelPaint.setColor(Color.argb(220, 46, 125, 50));
        labelPaint.setTextSize(24f);

        corridorPaint.setColor(Color.argb(180, 186, 85, 211));
        corridorPaint.setStyle(Paint.Style.STROKE);
        corridorPaint.setStrokeWidth(1.2f);
    }

    public void setRecognizedNotes(List<NoteEvent> source) {
        notes.clear();
        if (source != null) {
            notes.addAll(source);
        }
        invalidate();
    }

    public void setStaffCorridors(List<OpenCvScoreProcessor.StaffCorridor> source) {
        staffCorridors.clear();
        if (source != null) {
            staffCorridors.addAll(source);
        }
        invalidate();
    }

    public void setImageBounds(float left, float top, float right, float bottom) {
        imageBounds.set(left, top, right, bottom);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (notes.isEmpty()) {
            return;
        }

        float width = imageBounds.width();
        float height = imageBounds.height();
        if (width <= 1f || height <= 1f) {
            width = getWidth();
            height = getHeight();
            imageBounds.set(0f, 0f, width, height);
        }
        float noteRadius = Math.max(6f, width / 110f);
        float rowGap = Math.max(8f, height / 90f);

        for (OpenCvScoreProcessor.StaffCorridor corridor : staffCorridors) {
            float x0 = imageBounds.left + Math.max(0f, Math.min(1f, corridor.left)) * width;
            float x1 = imageBounds.left + Math.max(0f, Math.min(1f, corridor.right)) * width;
            float y0 = imageBounds.top + Math.max(0f, Math.min(1f, corridor.top)) * height;
            float y1 = imageBounds.top + Math.max(0f, Math.min(1f, corridor.bottom)) * height;
            canvas.drawRect(new RectF(x0, y0, x1, y1), corridorPaint);
        }

        for (NoteEvent note : notes) {
            if (note.x < 0f || note.y < 0f) {
                continue;
            }
            float nx = Math.max(0f, Math.min(1f, note.x));
            float ny = Math.max(0f, Math.min(1f, note.y));
            float x = imageBounds.left + nx * width;
            float y = imageBounds.top + ny * height;

            for (int i = -2; i <= 2; i++) {
                canvas.drawLine(x - 38f, y + i * rowGap, x + 38f, y + i * rowGap, staffPaint);
            }
            canvas.drawOval(new RectF(x - noteRadius, y - noteRadius * 0.8f, x + noteRadius, y + noteRadius * 0.8f), notePaint);
            canvas.drawText(MusicNotation.toEuropeanLabel(note.noteName, note.octave), x - 32f, y - rowGap * 2.8f, labelPaint);
        }
    }
}
