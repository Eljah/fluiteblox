package tatar.eljah.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class ToneVisualizerView extends View {

    private final List<Float> referenceData = new ArrayList<>();
    private final List<Float> userData = new ArrayList<>();
    private final Paint referencePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint userPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ToneVisualizerView(Context context) {
        super(context);
        init();
    }

    public ToneVisualizerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ToneVisualizerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        referencePaint.setStrokeWidth(4f);
        referencePaint.setStyle(Paint.Style.STROKE);
        referencePaint.setColor(Color.BLUE);

        userPaint.setStrokeWidth(4f);
        userPaint.setStyle(Paint.Style.STROKE);
        userPaint.setColor(Color.RED);
    }

    public void setReferenceData(List<Float> data) {
        referenceData.clear();
        if (data != null) {
            referenceData.addAll(data);
        }
        invalidate();
    }

    public void setUserData(List<Float> data) {
        userData.clear();
        if (data != null) {
            userData.addAll(data);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawSeries(canvas, referenceData, referencePaint);
        drawSeries(canvas, userData, userPaint);
    }

    private void drawSeries(Canvas canvas, List<Float> data, Paint paint) {
        if (data == null || data.size() < 2) {
            return;
        }
        float width = getWidth();
        float height = getHeight();

        float maxPitch = 400f;
        float minPitch = 50f;

        int size = data.size();
        float dx = width / (float) (size - 1);

        Path path = new Path();
        boolean started = false;

        for (int i = 0; i < size; i++) {
            Float value = data.get(i);
            if (value == null || value <= 0f) {
                continue;
            }
            float x = i * dx;
            float norm = (value - minPitch) / (maxPitch - minPitch);
            if (norm < 0f) {
                norm = 0f;
            }
            if (norm > 1f) {
                norm = 1f;
            }
            float y = height - norm * height;
            if (!started) {
                path.moveTo(x, y);
                started = true;
            } else {
                path.lineTo(x, y);
            }
        }
        if (started) {
            canvas.drawPath(path, paint);
        }
    }
}
