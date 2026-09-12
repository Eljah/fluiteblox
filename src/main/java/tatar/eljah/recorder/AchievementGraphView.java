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

import tatar.eljah.fluitblox.R;

public class AchievementGraphView extends View {
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint recoveryPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint durationPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint legendHitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint legendRecoveryPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint legendDurationPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private List<PerformanceMetricsStore.PerformanceAttempt> attempts = new ArrayList<PerformanceMetricsStore.PerformanceAttempt>();

    public AchievementGraphView(Context context, AttributeSet attrs) {
        super(context, attrs);

        gridPaint.setColor(Color.parseColor("#CDD6BF"));
        gridPaint.setStrokeWidth(1.5f);

        textPaint.setColor(Color.parseColor("#334036"));
        textPaint.setTextSize(26f);

        hitPaint.setColor(Color.parseColor("#1565C0"));
        hitPaint.setStyle(Paint.Style.STROKE);
        hitPaint.setStrokeWidth(5f);

        recoveryPaint.setColor(Color.parseColor("#2E7D32"));
        recoveryPaint.setStyle(Paint.Style.STROKE);
        recoveryPaint.setStrokeWidth(5f);

        durationPaint.setColor(Color.parseColor("#EF6C00"));
        durationPaint.setStyle(Paint.Style.STROKE);
        durationPaint.setStrokeWidth(5f);

        setupLegendPaint(legendHitPaint, hitPaint.getColor());
        setupLegendPaint(legendRecoveryPaint, recoveryPaint.getColor());
        setupLegendPaint(legendDurationPaint, durationPaint.getColor());
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
        float plotLeft = 62f;
        float plotRight = Math.max(plotLeft + 1f, w - 18f);
        float plotTop = 24f;
        float plotBottom = Math.max(plotTop + 1f, h - 40f);
        float plotHeight = plotBottom - plotTop;

        for (int i = 0; i <= 4; i++) {
            float y = plotTop + (plotHeight * i / 4f);
            canvas.drawLine(plotLeft, y, plotRight, y, gridPaint);
            int percent = 100 - (i * 25);
            canvas.drawText(percent + "%", 8f, Math.max(28f, y + 8f), textPaint);
        }

        if (attempts == null || attempts.size() < 2) {
            canvas.drawText(getContext().getString(R.string.achievements_graph_not_enough), plotLeft + 14f, h / 2f + 10f, textPaint);
            return;
        }

        drawLine(canvas, attempts, hitPaint, plotLeft, plotRight, plotTop, plotBottom, new ValueAccessor() {
            @Override
            public float value(PerformanceMetricsStore.PerformanceAttempt attempt) {
                return attempt.hitRatio;
            }
        });
        drawLine(canvas, attempts, recoveryPaint, plotLeft, plotRight, plotTop, plotBottom, new ValueAccessor() {
            @Override
            public float value(PerformanceMetricsStore.PerformanceAttempt attempt) {
                return attempt.recoveryRatio;
            }
        });
        drawLine(canvas, attempts, durationPaint, plotLeft, plotRight, plotTop, plotBottom, new ValueAccessor() {
            @Override
            public float value(PerformanceMetricsStore.PerformanceAttempt attempt) {
                return attempt.durationRatio;
            }
        });

        canvas.drawText(getContext().getString(R.string.achievements_graph_hits), plotLeft, h - 72f, legendHitPaint);
        canvas.drawText(getContext().getString(R.string.achievements_graph_recovery), plotLeft, h - 42f, legendRecoveryPaint);
        canvas.drawText(getContext().getString(R.string.achievements_graph_duration), plotLeft, h - 12f, legendDurationPaint);
    }

    private void drawLine(Canvas canvas,
                          List<PerformanceMetricsStore.PerformanceAttempt> attempts,
                          Paint paint,
                          float plotLeft,
                          float plotRight,
                          float plotTop,
                          float plotBottom,
                          ValueAccessor accessor) {
        Path path = new Path();
        float plotWidth = plotRight - plotLeft;
        float plotHeight = plotBottom - plotTop;
        for (int i = 0; i < attempts.size(); i++) {
            float x = plotLeft + (plotWidth * i / (float) Math.max(1, attempts.size() - 1));
            float value = clamp(accessor.value(attempts.get(i)));
            float y = plotBottom - (value * plotHeight);
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

    private void setupLegendPaint(Paint paint, int color) {
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(24f);
    }

    private interface ValueAccessor {
        float value(PerformanceMetricsStore.PerformanceAttempt attempt);
    }
}
