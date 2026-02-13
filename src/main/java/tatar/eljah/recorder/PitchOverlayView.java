package tatar.eljah.recorder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class PitchOverlayView extends View {
    private final Paint staffPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint notePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint activeNotePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint expectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spectrogramPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spectrogramGridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final List<NoteEvent> notes = new ArrayList<NoteEvent>();
    private final List<Float> history = new ArrayList<Float>();

    private float expectedHz;
    private float actualHz;
    private int pointer;

    public PitchOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        staffPaint.setColor(Color.DKGRAY);
        staffPaint.setStrokeWidth(3f);

        notePaint.setColor(Color.BLACK);
        activeNotePaint.setColor(Color.parseColor("#2E7D32"));

        labelPaint.setColor(Color.parseColor("#424242"));
        labelPaint.setTextSize(28f);

        expectedPaint.setColor(Color.parseColor("#C62828"));
        expectedPaint.setStrokeWidth(3f);

        spectrogramPaint.setColor(Color.parseColor("#1976D2"));
        spectrogramPaint.setStrokeWidth(4f);
        spectrogramPaint.setStyle(Paint.Style.STROKE);

        spectrogramGridPaint.setColor(Color.LTGRAY);
        spectrogramGridPaint.setStrokeWidth(2f);
    }

    public void setNotes(List<NoteEvent> pieceNotes) {
        notes.clear();
        if (pieceNotes != null) {
            notes.addAll(pieceNotes);
        }
        invalidate();
    }

    public void setPointer(int pointer) {
        this.pointer = pointer;
        invalidate();
    }

    public void setFrequencies(float expectedHz, float actualHz) {
        this.expectedHz = expectedHz;
        this.actualHz = actualHz;
        history.add(actualHz);
        int maxPoints = 180;
        if (history.size() > maxPoints) {
            history.remove(0);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();

        float topH = h * 0.58f;
        drawStaffAndNotes(canvas, w, topH);
        drawSpectrogram(canvas, w, h, topH);
    }

    private void drawStaffAndNotes(Canvas canvas, float w, float topH) {
        float lineGap = topH / 8f;
        float firstLineY = lineGap * 1.5f;
        for (int i = 0; i < 5; i++) {
            float y = firstLineY + i * lineGap;
            canvas.drawLine(0, y, w, y, staffPaint);
        }

        if (notes.isEmpty()) {
            return;
        }

        float noteRadius = Math.max(8f, lineGap * 0.35f);
        float leftPad = 26f;
        float rightPad = 20f;
        float available = Math.max(1f, w - leftPad - rightPad);

        for (int i = 0; i < notes.size(); i++) {
            NoteEvent note = notes.get(i);
            float x = leftPad + available * ((float) i / Math.max(1, notes.size() - 1));
            float y = yForMidi(MusicNotation.midiFor(note.noteName, note.octave), firstLineY, lineGap);
            canvas.drawOval(new RectF(x - noteRadius, y - noteRadius * 0.75f, x + noteRadius, y + noteRadius * 0.75f),
                    i == pointer ? activeNotePaint : notePaint);

            if (i % 2 == 0) {
                canvas.drawText(MusicNotation.toEuropeanLabel(note.noteName, note.octave), x - noteRadius, firstLineY - lineGap * 0.6f, labelPaint);
            }
        }
    }

    private void drawSpectrogram(Canvas canvas, float w, float h, float topH) {
        float startY = topH + 12f;
        float bottom = h - 8f;
        if (bottom <= startY) {
            return;
        }

        for (int i = 1; i <= 3; i++) {
            float y = startY + (bottom - startY) * i / 4f;
            canvas.drawLine(0, y, w, y, spectrogramGridPaint);
        }

        float expectedY = yForFrequency(expectedHz, startY, bottom);
        canvas.drawLine(0, expectedY, w, expectedY, expectedPaint);

        if (history.isEmpty()) {
            return;
        }

        Path path = new Path();
        for (int i = 0; i < history.size(); i++) {
            float x = w * i / Math.max(1, history.size() - 1);
            float y = yForFrequency(history.get(i), startY, bottom);
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        canvas.drawPath(path, spectrogramPaint);
    }

    private float yForMidi(int midi, float firstLineY, float lineGap) {
        int refMidi = 64;
        return firstLineY + lineGap * 4f - (midi - refMidi) * (lineGap / 2f);
    }

    private float yForFrequency(float hz, float top, float bottom) {
        if (hz <= 0f) {
            return bottom;
        }
        float norm = Math.max(0f, Math.min(1f, (hz - 220f) / 900f));
        return bottom - norm * (bottom - top);
    }
}
