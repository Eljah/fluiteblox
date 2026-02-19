package tatar.eljah.recorder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class AchievementGraphView extends View {
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint recoveryPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint durationPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private List<PerformanceMetricsStore.PerformanceAttempt> attempts = new ArrayList<PerformanceMetricsStore.PerformanceAttempt>();

    public AchievementGraphView(Context context, AttributeSet attrs) {
        super(context, attrs);

        gridPaint.setColor(Color.LTGRAY);
        gridPaint.setStrokeWidth(1.5f);

        textPaint.setColor(Color.DKGRAY);
        textPaint.setTextSize(28f);

        hitPaint.setColor(Color.parseColor("#1565C0"));
        hitPaint.setStyle(Paint.Style.STROKE);
        hitPaint.setStrokeWidth(5f);

        recoveryPaint.setColor(Color.parseColor("#2E7D32"));
        recoveryPaint.setStyle(Paint.Style.STROKE);
        recoveryPaint.setStrokeWidth(5f);

        durationPaint.setColor(Color.parseColor("#EF6C00"));
        durationPaint.setStyle(Paint.Style.STROKE);
        durationPaint.setStrokeWidth(5f);
    }

    public void setAttempts(List<PerformanceMetricsStore.PerformanceAttempt> attempts) {
        if (attempts == null) {
            this.attempts = new ArrayList<PerformanceMetricsStore.PerformanceAttempt>();
        } else {
            this.attempts = attempts;
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();

        for (int i = 0; i <= 4; i++) {
            float y = h * i / 4f;
            canvas.drawLine(0f, y, w, y, gridPaint);
            int percent = 100 - (i * 25);
            canvas.drawText(percent + "%", 8f, Math.max(28f, y - 8f), textPaint);
        }

        if (attempts == null || attempts.size() < 2) {
            canvas.drawText("Недостаточно завершенных попыток для графика", 24f, h / 2f, textPaint);
            return;
        }

        drawLine(canvas, attempts, hitPaint, new ValueAccessor() {
            @Override
            public float value(PerformanceMetricsStore.PerformanceAttempt attempt) {
                return attempt.hitRatio;
            }
        });
        drawLine(canvas, attempts, recoveryPaint, new ValueAccessor() {
            @Override
            public float value(PerformanceMetricsStore.PerformanceAttempt attempt) {
                return attempt.recoveryRatio;
            }
        });
        drawLine(canvas, attempts, durationPaint, new ValueAccessor() {
            @Override
            public float value(PerformanceMetricsStore.PerformanceAttempt attempt) {
                return attempt.durationRatio;
            }
        });

        canvas.drawText("Синий: попадания", 18f, h - 72f, hitPaint);
        canvas.drawText("Зеленый: продолжение", 18f, h - 42f, recoveryPaint);
        canvas.drawText("Оранжевый: длительность", 18f, h - 12f, durationPaint);
    }

    private void drawLine(Canvas canvas,
                          List<PerformanceMetricsStore.PerformanceAttempt> attempts,
                          Paint paint,
                          ValueAccessor accessor) {
        Path path = new Path();
        for (int i = 0; i < attempts.size(); i++) {
            float x = getWidth() * i / (float) Math.max(1, attempts.size() - 1);
            float value = clamp(accessor.value(attempts.get(i)));
            float y = getHeight() - (value * getHeight());
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        canvas.drawPath(path, paint);
    }

    private float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private interface ValueAccessor {
        float value(PerformanceMetricsStore.PerformanceAttempt attempt);
    }
}
