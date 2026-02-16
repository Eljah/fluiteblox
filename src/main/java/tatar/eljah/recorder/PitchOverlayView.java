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

public class PitchOverlayView extends View {
    private static final float MAX_SPECTROGRAM_HZ = 3000f;
    private static final float NOTE_LABEL_MIN_GAP_PX = 2f;
    private static final float SPECTROGRAM_TOP_PADDING_PX = 34f;

    private final Paint staffPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint notePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint activeNotePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint activeLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint expectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spectrogramGridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint heatPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final List<NoteEvent> notes = new ArrayList<NoteEvent>();
    private final List<Float> history = new ArrayList<Float>();
    private final List<float[]> spectrumHistory = new ArrayList<float[]>();

    private float expectedHz;
    private float actualHz;
    private int pointer;
    private int lastSpectrumSampleRate = 22050;
    private boolean micMode = true;

    public PitchOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        staffPaint.setColor(Color.DKGRAY);
        staffPaint.setStrokeWidth(3f);

        notePaint.setColor(Color.BLACK);
        activeNotePaint.setColor(Color.parseColor("#2E7D32"));

        labelPaint.setColor(Color.parseColor("#424242"));
        labelPaint.setTextSize(28f);

        activeLabelPaint.setColor(Color.parseColor("#2E7D32"));
        activeLabelPaint.setTextSize(28f);

        expectedPaint.setColor(Color.parseColor("#8E24AA"));
        expectedPaint.setStrokeWidth(1.5f);

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
        int maxPoints = 240;
        if (history.size() > maxPoints) {
            history.remove(0);
        }
        invalidate();
    }

    public void setMicMode(boolean micMode) {
        this.micMode = micMode;
        invalidate();
    }

    public void setSpectrum(float[] magnitudes, int sampleRate) {
        if (magnitudes == null || magnitudes.length == 0) {
            return;
        }
        lastSpectrumSampleRate = sampleRate;
        float[] copy = new float[magnitudes.length];
        System.arraycopy(magnitudes, 0, copy, 0, magnitudes.length);
        spectrumHistory.add(copy);
        int maxColumns = 260;
        if (spectrumHistory.size() > maxColumns) {
            spectrumHistory.remove(0);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();

        float topH = h * 0.52f;
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

        float leftPad = 26f;
        float rightPad = 20f;
        float available = Math.max(1f, w - leftPad - rightPad);
        float noteStep = notes.size() <= 1 ? available : available / (notes.size() - 1);
        float noteRadius = Math.max(2.5f, Math.min(lineGap * 0.35f, noteStep * 0.42f));

        List<LabelLayout> labelsToDraw = new ArrayList<LabelLayout>();
        float[] labelRows = new float[]{
                firstLineY + lineGap * 5.35f,
                firstLineY + lineGap * 5.85f,
                firstLineY + lineGap * 6.35f,
                firstLineY + lineGap * 6.85f
        };
        float[] lastLabelRight = new float[]{Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};

        for (int i = 0; i < notes.size(); i++) {
            NoteEvent note = notes.get(i);
            float x = leftPad + available * ((float) i / Math.max(1, notes.size() - 1));
            float y = yForMidi(MusicNotation.midiFor(note.noteName, note.octave), firstLineY, lineGap);
            canvas.drawOval(new RectF(x - noteRadius, y - noteRadius * 0.75f, x + noteRadius, y + noteRadius * 0.75f),
                    i == pointer ? activeNotePaint : notePaint);

            String label = MusicNotation.toEuropeanLabel(note.noteName, note.octave);
            float textWidth = labelPaint.measureText(label);
            float textLeft = x - textWidth / 2f;
            float minLeft = 0f;
            float maxLeft = Math.max(minLeft, w - textWidth);

            int preferredRow = i % labelRows.length;
            int selectedRow = preferredRow;
            boolean foundWithoutOverlap = false;
            for (int attempt = 0; attempt < labelRows.length; attempt++) {
                int rowCandidate = (preferredRow + attempt) % labelRows.length;
                if (textLeft >= lastLabelRight[rowCandidate] + NOTE_LABEL_MIN_GAP_PX) {
                    selectedRow = rowCandidate;
                    foundWithoutOverlap = true;
                    break;
                }
            }

            if (!foundWithoutOverlap) {
                float bestRight = Float.MAX_VALUE;
                for (int row = 0; row < labelRows.length; row++) {
                    if (lastLabelRight[row] < bestRight) {
                        bestRight = lastLabelRight[row];
                        selectedRow = row;
                    }
                }
                textLeft = Math.max(textLeft, lastLabelRight[selectedRow] + NOTE_LABEL_MIN_GAP_PX);
            }

            textLeft = Math.max(minLeft, Math.min(maxLeft, textLeft));
            labelsToDraw.add(new LabelLayout(label, textLeft, labelRows[selectedRow], i == pointer));
            lastLabelRight[selectedRow] = textLeft + textWidth;
        }

        for (LabelLayout labelLayout : labelsToDraw) {
            canvas.drawText(labelLayout.text, labelLayout.x, labelLayout.y,
                    labelLayout.active ? activeLabelPaint : labelPaint);
        }
    }

    private static final class LabelLayout {
        private final String text;
        private final float x;
        private final float y;
        private final boolean active;

        private LabelLayout(String text, float x, float y, boolean active) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.active = active;
        }
    }

    private void drawSpectrogram(Canvas canvas, float w, float h, float topH) {
        float startY = topH + SPECTROGRAM_TOP_PADDING_PX;
        float bottom = h - 8f;
        if (bottom <= startY) {
            return;
        }

        drawSpectrogramGrid(canvas, w, startY, bottom);
        drawSpectrogramHeatmap(canvas, w, startY, bottom);

        if (micMode && expectedHz > 0f) {
            float secondHarmonicHz = expectedHz * 2f;
            float guideHz = Math.min(secondHarmonicHz, MAX_SPECTROGRAM_HZ);
            float expectedY = yForFrequency(guideHz, startY, bottom);
            canvas.drawLine(0, expectedY, w, expectedY, expectedPaint);
        }
    }

    private void drawSpectrogramGrid(Canvas canvas, float w, float top, float bottom) {
        for (int i = 0; i <= 3; i++) {
            float hz = i * 1000f;
            float y = yForFrequency(hz, top, bottom);
            canvas.drawLine(0, y, w, y, spectrogramGridPaint);
            canvas.drawText(((int) hz) + " Hz", 8f, y - 4f, labelPaint);
        }
    }

    private void drawSpectrogramHeatmap(Canvas canvas, float w, float top, float bottom) {
        if (spectrumHistory.isEmpty()) {
            return;
        }

        int columns = spectrumHistory.size();
        float colW = Math.max(1f, w / Math.max(1, columns));
        for (int x = 0; x < columns; x++) {
            float[] frame = spectrumHistory.get(x);
            if (frame == null || frame.length == 0) {
                continue;
            }
            int bins = frame.length;
            float frameMax = max(frame);
            if (frameMax <= 0f) {
                frameMax = 1f;
            }
            for (int bin = 0; bin < bins; bin++) {
                float hz = bin * lastSpectrumSampleRate / (2f * bins);
                if (hz > MAX_SPECTROGRAM_HZ) {
                    break;
                }
                float nextHz = (bin + 1) * lastSpectrumSampleRate / (2f * bins);
                float yTop = yForFrequency(nextHz, top, bottom);
                float yBottom = yForFrequency(hz, top, bottom);
                float intensity = normalizeMagnitude(frame[bin], frameMax);
                heatPaint.setColor(heatColor(intensity));
                heatPaint.setStyle(Paint.Style.FILL);
                float left = x * colW;
                canvas.drawRect(left, yTop, left + colW + 1f, yBottom, heatPaint);
            }
        }
    }

    private float normalizeMagnitude(float value, float frameMax) {
        if (value <= 0f || frameMax <= 0f) {
            return 0f;
        }
        float normalized = value / frameMax;
        return Math.max(0f, Math.min(1f, normalized));
    }

    private int heatColor(float intensity) {
        float clamped = Math.max(0f, Math.min(1f, intensity));
        float hue = (1f - clamped) * 240f;
        return Color.HSVToColor(new float[]{hue, 1f, clamped});
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

    private float yForMidi(int midi, float firstLineY, float lineGap) {
        int refMidi = 64;
        return firstLineY + lineGap * 4f - (midi - refMidi) * (lineGap / 2f);
    }

    private float yForFrequency(float hz, float top, float bottom) {
        float clamped = Math.max(0f, Math.min(MAX_SPECTROGRAM_HZ, hz));
        float norm = clamped / MAX_SPECTROGRAM_HZ;
        return bottom - norm * (bottom - top);
    }
}
