package tatar.eljah.recorder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class RecognitionOverlayView extends View {
    private final Paint staffPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint notePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final List<NoteEvent> notes = new ArrayList<NoteEvent>();

    public RecognitionOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        staffPaint.setColor(Color.argb(190, 30, 30, 30));
        staffPaint.setStrokeWidth(2f);

        notePaint.setColor(Color.argb(210, 25, 118, 210));
        labelPaint.setColor(Color.argb(220, 46, 125, 50));
        labelPaint.setTextSize(24f);
    }

    public void setRecognizedNotes(List<NoteEvent> source) {
        notes.clear();
        if (source != null) {
            notes.addAll(source);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (notes.isEmpty()) {
            return;
        }

        float width = getWidth();
        float height = getHeight();
        float noteRadius = Math.max(6f, width / 110f);
        float rowGap = Math.max(8f, height / 90f);

        for (NoteEvent note : notes) {
            if (note.x < 0f || note.y < 0f) {
                continue;
            }
            float x = note.x * width;
            float y = note.y * height;

            for (int i = -2; i <= 2; i++) {
                canvas.drawLine(x - 38f, y + i * rowGap, x + 38f, y + i * rowGap, staffPaint);
            }
            canvas.drawOval(new RectF(x - noteRadius, y - noteRadius * 0.8f, x + noteRadius, y + noteRadius * 0.8f), notePaint);
            canvas.drawText(MusicNotation.toEuropeanLabel(note.noteName, note.octave), x - 32f, y - rowGap * 2.8f, labelPaint);
        }
    }
}
