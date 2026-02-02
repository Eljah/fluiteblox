package com.example.tonetrainer.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class SpectrogramView extends View {
    private static final int MAX_FRAMES = 60;
    private static final int MAX_BINS = 64;
    private static final float TEXT_SIZE_SP = 12f;

    private final Paint cellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final List<float[]> frames = new ArrayList<>();
    private int sampleRate = 22050;
    private int fftBins = 0;
    private float frameDurationMs = 0f;

    public SpectrogramView(Context context) {
        super(context);
        init();
    }

    public SpectrogramView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SpectrogramView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        axisPaint.setColor(Color.WHITE);
        axisPaint.setStrokeWidth(2f);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(TEXT_SIZE_SP * getResources().getDisplayMetrics().scaledDensity);
    }

    public void clear() {
        frames.clear();
        invalidate();
    }

    public void addSpectrumFrame(float[] magnitudes, int sampleRate, int fftSize) {
        if (magnitudes == null || magnitudes.length == 0) {
            return;
        }
        this.sampleRate = sampleRate;
        this.fftBins = magnitudes.length;
        this.frameDurationMs = (fftSize / (float) sampleRate) * 1000f;

        float[] downsampled = downsample(magnitudes, MAX_BINS);
        if (frames.size() >= MAX_FRAMES) {
            frames.remove(0);
        }
        frames.add(downsampled);
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        int leftPadding = getPaddingLeft() + 60;
        int bottomPadding = getPaddingBottom() + 40;
        int topPadding = getPaddingTop() + 16;
        int rightPadding = getPaddingRight() + 16;

        int plotWidth = width - leftPadding - rightPadding;
        int plotHeight = height - topPadding - bottomPadding;
        if (plotWidth <= 0 || plotHeight <= 0) {
            return;
        }

        drawAxes(canvas, leftPadding, topPadding, plotWidth, plotHeight);
        if (frames.isEmpty()) {
            return;
        }

        int frameCount = frames.size();
        int bins = frames.get(0).length;
        float cellWidth = plotWidth / (float) MAX_FRAMES;
        float cellHeight = plotHeight / (float) bins;

        for (int x = 0; x < frameCount; x++) {
            float[] frame = frames.get(x);
            float frameMax = max(frame);
            if (frameMax <= 0f) {
                frameMax = 1f;
            }
            for (int y = 0; y < bins; y++) {
                float magnitude = frame[y] / frameMax;
                int color = colorForMagnitude(magnitude);
                cellPaint.setColor(color);
                float left = leftPadding + x * cellWidth;
                float top = topPadding + plotHeight - (y + 1) * cellHeight;
                canvas.drawRect(left, top, left + cellWidth, top + cellHeight, cellPaint);
            }
        }
    }

    private void drawAxes(Canvas canvas, int left, int top, int width, int height) {
        int bottom = top + height;
        int right = left + width;
        canvas.drawLine(left, top, left, bottom, axisPaint);
        canvas.drawLine(left, bottom, right, bottom, axisPaint);

        float maxFrequency = sampleRate / 2f;
        canvas.drawText("Hz", 8, top + textPaint.getTextSize(), textPaint);
        canvas.drawText("ms", right - 40, bottom + textPaint.getTextSize() + 8, textPaint);

        int ticks = 3;
        for (int i = 0; i <= ticks; i++) {
            float fraction = i / (float) ticks;
            float y = bottom - fraction * height;
            float freq = maxFrequency * fraction;
            canvas.drawLine(left - 8, y, left, y, axisPaint);
            canvas.drawText(String.format("%.0f", freq), 8, y + textPaint.getTextSize() / 2, textPaint);
        }

        float totalMs = frameDurationMs * frames.size();
        canvas.drawText(String.format("%.0f", totalMs), right - 60, bottom + textPaint.getTextSize() + 8, textPaint);
        canvas.drawText("0", left, bottom + textPaint.getTextSize() + 8, textPaint);
    }

    private float[] downsample(float[] magnitudes, int targetBins) {
        int bins = magnitudes.length;
        if (bins <= targetBins) {
            return magnitudes.clone();
        }
        float[] result = new float[targetBins];
        float step = bins / (float) targetBins;
        for (int i = 0; i < targetBins; i++) {
            int start = Math.round(i * step);
            int end = Math.min(bins, Math.round((i + 1) * step));
            float sum = 0f;
            int count = 0;
            for (int j = start; j < end; j++) {
                sum += magnitudes[j];
                count++;
            }
            result[i] = count == 0 ? 0f : sum / count;
        }
        return result;
    }

    private float max(float[] values) {
        float max = 0f;
        for (float value : values) {
            if (value > max) {
                max = value;
            }
        }
        return max;
    }

    private int colorForMagnitude(float value) {
        float clamped = Math.max(0f, Math.min(1f, value));
        float hue = (1f - clamped) * 240f;
        return Color.HSVToColor(new float[]{hue, 1f, clamped});
    }
}
