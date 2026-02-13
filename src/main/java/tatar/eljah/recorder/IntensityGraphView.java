package tatar.eljah.recorder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class IntensityGraphView extends View {
    public interface OnThresholdChangedListener {
        void onThresholdChanged(float value);
    }

    private final List<Float> history = new ArrayList<Float>();
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thresholdPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float threshold = 0.03f;
    private OnThresholdChangedListener listener;

    public IntensityGraphView(Context context, AttributeSet attrs) {
        super(context, attrs);
        linePaint.setColor(Color.parseColor("#1B5E20"));
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(3f);

        gridPaint.setColor(Color.LTGRAY);
        gridPaint.setStrokeWidth(1.5f);

        thresholdPaint.setColor(Color.parseColor("#C62828"));
        thresholdPaint.setStrokeWidth(3f);

        textPaint.setColor(Color.DKGRAY);
        textPaint.setTextSize(26f);
    }

    public void setOnThresholdChangedListener(OnThresholdChangedListener listener) {
        this.listener = listener;
    }

    public void setThreshold(float threshold) {
        this.threshold = clamp(threshold);
        invalidate();
    }

    public void addIntensity(float value) {
        history.add(clamp(value));
        if (history.size() > 300) {
            history.remove(0);
        }
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();

        for (int i = 1; i <= 4; i++) {
            float y = h * i / 5f;
            canvas.drawLine(0, y, w, y, gridPaint);
        }

        float yThreshold = h - threshold * h;
        canvas.drawLine(0, yThreshold, w, yThreshold, thresholdPaint);
        canvas.drawText("Threshold: " + String.format("%.3f", threshold), 8f, Math.max(22f, yThreshold - 8f), textPaint);

        if (history.isEmpty()) {
            return;
        }

        Path path = new Path();
        for (int i = 0; i < history.size(); i++) {
            float x = w * i / Math.max(1, history.size() - 1);
            float y = h - history.get(i) * h;
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        canvas.drawPath(path, linePaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
            float h = Math.max(1f, getHeight());
            float value = 1f - (event.getY() / h);
            threshold = clamp(value);
            if (listener != null) {
                listener.onThresholdChanged(threshold);
            }
            invalidate();
            return true;
        }
        return super.onTouchEvent(event);
    }

    private float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
