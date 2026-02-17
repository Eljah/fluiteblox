package tatar.eljah.recorder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class PitchOverlayView extends View {
    private static final float MAX_SPECTROGRAM_HZ = 2000f;
    private static final float NOTE_LABEL_MIN_GAP_PX = 2f;
    private static final float STAFF_TOP_PADDING_PX = 28f;
    private static final float STAFF_BOTTOM_PADDING_PX = 12f;
    private static final float NOTE_LABEL_BLOCK_GAP_PX = 14f;
    private static final float PANEL_BOTTOM_PADDING_PX = 8f;
    private static final float LEDGER_STAFF_SPAN_IN_GAPS = 7f; // 1.5 above + 4 staff + 1.5 below
    private static final float STAFF_LINE_GAP_FOR_12_NOTES_PX = 36f;
    private static final float STAFF_PANEL_MIN_HEIGHT_PX = STAFF_LINE_GAP_FOR_12_NOTES_PX * LEDGER_STAFF_SPAN_IN_GAPS;
    private static final float LABEL_PANEL_MIN_HEIGHT_PX = 42f;
    private static final float SPECTROGRAM_PANEL_BASE_HEIGHT_PX = 88f;
    private static final float SPECTROGRAM_FIXED_HEIGHT_PX = SPECTROGRAM_PANEL_BASE_HEIGHT_PX * 2f;
    private static final int REFERENCE_NOTE_COUNT = 56;
    private static final float BASE_LABEL_TEXT_SIZE_PX = 28f;
    private static final float MIN_LABEL_TEXT_SIZE_PX = 14f;

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
    private final List<NoteDrawInfo> noteDrawInfos = new ArrayList<NoteDrawInfo>();
    private final List<LabelHitInfo> labelHitInfos = new ArrayList<LabelHitInfo>();

    private final Paint mismatchNotePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mismatchLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint durationMismatchNotePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint noteStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stemPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private OnPlayedNoteClickListener playedNoteClickListener;
    private final List<String> mismatchActualByIndex = new ArrayList<String>();
    private final List<Boolean> matchedByIndex = new ArrayList<Boolean>();
    private final List<String> matchedActualByIndex = new ArrayList<String>();
    private final List<Boolean> durationMismatchByIndex = new ArrayList<Boolean>();

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
        mismatchNotePaint.setColor(Color.parseColor("#C62828"));

        labelPaint.setColor(Color.parseColor("#424242"));
        labelPaint.setTextSize(BASE_LABEL_TEXT_SIZE_PX);

        activeLabelPaint.setColor(Color.parseColor("#2E7D32"));
        activeLabelPaint.setTextSize(BASE_LABEL_TEXT_SIZE_PX);

        mismatchLabelPaint.setColor(Color.parseColor("#C62828"));
        mismatchLabelPaint.setTextSize(BASE_LABEL_TEXT_SIZE_PX);

        durationMismatchNotePaint.setColor(Color.parseColor("#FB8C00"));
        durationMismatchNotePaint.setTextSize(BASE_LABEL_TEXT_SIZE_PX);

        noteStrokePaint.setColor(Color.BLACK);
        noteStrokePaint.setStrokeWidth(3f);
        noteStrokePaint.setStyle(Paint.Style.STROKE);

        stemPaint.setStyle(Paint.Style.STROKE);
        stemPaint.setStrokeCap(Paint.Cap.ROUND);
        stemPaint.setStrokeWidth(noteStrokePaint.getStrokeWidth() * 2f);

        expectedPaint.setColor(Color.parseColor("#8E24AA"));
        expectedPaint.setStrokeWidth(1.5f);

        spectrogramGridPaint.setColor(Color.LTGRAY);
        spectrogramGridPaint.setStrokeWidth(2f);
    }

    public void setNotes(List<NoteEvent> pieceNotes) { /* unchanged */
        notes.clear();
        if (pieceNotes != null) notes.addAll(pieceNotes);
        mismatchActualByIndex.clear(); matchedByIndex.clear(); matchedActualByIndex.clear(); durationMismatchByIndex.clear();
        for (int i = 0; i < notes.size(); i++) { mismatchActualByIndex.add(null); matchedByIndex.add(false); matchedActualByIndex.add(null); durationMismatchByIndex.add(false);} invalidate();
    }
    public void markMismatch(int index, String actualFullName) { if (index < 0 || index >= notes.size()) return; ensureMismatchCapacity(); mismatchActualByIndex.set(index, actualFullName); invalidate(); }
    public void clearMismatch(int index) { if (index < 0 || index >= notes.size()) return; ensureMismatchCapacity(); mismatchActualByIndex.set(index, null); invalidate(); }
    public void markMatched(int index, String actualFullName) { if (index < 0 || index >= notes.size()) return; ensureMatchedCapacity(); matchedByIndex.set(index, true); matchedActualByIndex.set(index, actualFullName); invalidate(); }
    public void clearMatched(int index) { if (index < 0 || index >= notes.size()) return; ensureMatchedCapacity(); matchedByIndex.set(index, false); matchedActualByIndex.set(index, null); invalidate(); }
    public void markDurationMismatch(int index) { if (index < 0 || index >= notes.size()) return; ensureDurationMismatchCapacity(); durationMismatchByIndex.set(index, true); invalidate(); }
    public void clearDurationMismatch(int index) { if (index < 0 || index >= notes.size()) return; ensureDurationMismatchCapacity(); durationMismatchByIndex.set(index, false); invalidate(); }
    public boolean isDurationMismatch(int index) { return hasDurationMismatch(index); }
    public void setOnPlayedNoteClickListener(OnPlayedNoteClickListener listener) { this.playedNoteClickListener = listener; }
    public void setPointer(int pointer) { this.pointer = pointer; invalidate(); }
    public void setFrequencies(float expectedHz, float actualHz) { this.expectedHz = expectedHz; this.actualHz = actualHz; history.add(actualHz); if (history.size() > 240) history.remove(0); invalidate(); }
    public void setMicMode(boolean micMode) { this.micMode = micMode; invalidate(); }
    public void setSpectrum(float[] magnitudes, int sampleRate) { if (magnitudes == null || magnitudes.length == 0) return; lastSpectrumSampleRate = sampleRate; float[] copy = new float[magnitudes.length]; System.arraycopy(magnitudes, 0, copy, 0, magnitudes.length); spectrumHistory.add(copy); if (spectrumHistory.size() > 260) spectrumHistory.remove(0); invalidate(); }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();

        int estimatedLabelRows = estimateLabelRows(w);
        float baseTextHeight = textHeightForSize(BASE_LABEL_TEXT_SIZE_PX);
        float desiredLabelHeight = Math.max(LABEL_PANEL_MIN_HEIGHT_PX,
                requiredLabelPanelHeight(estimatedLabelRows, baseTextHeight));

        float staffHeight = STAFF_PANEL_MIN_HEIGHT_PX;
        float spectrogramHeight = SPECTROGRAM_FIXED_HEIGHT_PX;

        float requiredHeight = STAFF_TOP_PADDING_PX + staffHeight
                + NOTE_LABEL_BLOCK_GAP_PX + desiredLabelHeight
                + NOTE_LABEL_BLOCK_GAP_PX + spectrogramHeight
                + PANEL_BOTTOM_PADDING_PX;

        float rootHeight = getRootView() == null ? h : getRootView().getHeight();
        float maxAllowedHeight = Math.max(h, rootHeight);
        float targetHeight = Math.min(requiredHeight, maxAllowedHeight);
        ensureOverlayHeight(targetHeight);
        float contentHeight = Math.max(h, targetHeight);

        float staffTop = STAFF_TOP_PADDING_PX;
        float staffBottom = staffTop + staffHeight;
        float labelTop = staffBottom + NOTE_LABEL_BLOCK_GAP_PX;

        float maxLabelHeight = Math.max(1f, contentHeight
                - PANEL_BOTTOM_PADDING_PX
                - spectrogramHeight
                - NOTE_LABEL_BLOCK_GAP_PX
                - labelTop);
        float labelHeight = Math.min(desiredLabelHeight, maxLabelHeight);
        float labelBottom = labelTop + labelHeight;

        float spectrogramTop = labelBottom + NOTE_LABEL_BLOCK_GAP_PX;
        float spectrogramBottom = spectrogramTop + spectrogramHeight;

        drawStaffAndNotes(canvas, w, staffTop, staffBottom, labelTop, labelBottom);
        drawSpectrogram(canvas, w, spectrogramTop, spectrogramBottom);
    }

    private void ensureOverlayHeight(float minHeightPx) {
        android.view.ViewGroup.LayoutParams lp = getLayoutParams();
        if (lp == null) {
            return;
        }
        int needed = (int) Math.ceil(minHeightPx);
        if (lp.height != needed) {
            lp.height = needed;
            setLayoutParams(lp);
            requestLayout();
        }
    }

    private void applyLabelTextSize(float sizePx) {
        float size = Math.max(MIN_LABEL_TEXT_SIZE_PX, Math.min(BASE_LABEL_TEXT_SIZE_PX, sizePx));
        labelPaint.setTextSize(size);
        activeLabelPaint.setTextSize(size);
        mismatchLabelPaint.setTextSize(size);
        durationMismatchNotePaint.setTextSize(size);
    }

    private float textHeightForSize(float textSizePx) {
        Paint probe = new Paint(labelPaint);
        probe.setTextSize(textSizePx);
        Paint.FontMetrics fm = probe.getFontMetrics();
        return fm.descent - fm.ascent;
    }

    private float fittedLabelTextSize(float availableLabelHeight, int rowCount) {
        float baseHeight = textHeightForSize(BASE_LABEL_TEXT_SIZE_PX);
        float needed = requiredLabelPanelHeight(rowCount, baseHeight);
        if (needed <= 0f) {
            return BASE_LABEL_TEXT_SIZE_PX;
        }
        float scale = Math.min(1f, availableLabelHeight / needed);
        return BASE_LABEL_TEXT_SIZE_PX * scale;
    }

    private void drawStaffAndNotes(Canvas canvas, float w, float staffTop, float staffBottom, float labelTop, float labelBottom) {
        float drawableStaffHeight = Math.max(1f, staffBottom - staffTop);
        float lineGap = drawableStaffHeight / LEDGER_STAFF_SPAN_IN_GAPS;
        float firstLineY = staffTop + lineGap * 1.5f;
        float bottomLineY = firstLineY + lineGap * 4f;
        for (int i = 0; i < 5; i++) {
            float y = firstLineY + i * lineGap;
            canvas.drawLine(0, y, w, y, staffPaint);
        }

        if (notes.isEmpty()) {
            noteDrawInfos.clear();
            labelHitInfos.clear();
            return;
        }

        int estimatedRows = estimateLabelRows(w);
        float availableLabelHeightForText = Math.max(1f, labelBottom - labelTop);
        applyLabelTextSize(fittedLabelTextSize(availableLabelHeightForText, estimatedRows));

        float leftPad = 26f;
        float rightPad = 20f;
        float available = Math.max(1f, w - leftPad - rightPad);
        float noteStep = notes.size() <= 1 ? available : available / (notes.size() - 1);
        float referenceStep = available / Math.max(1, REFERENCE_NOTE_COUNT - 1);
        float noteRadius = Math.max(8f, Math.min(lineGap * 0.58f, referenceStep * 0.48f)) * 2f;

        List<LabelLayout> labelsToDraw = new ArrayList<LabelLayout>();
        List<Float> lastLabelRight = new ArrayList<Float>();

        noteDrawInfos.clear();
        labelHitInfos.clear();
        for (int i = 0; i < notes.size(); i++) {
            NoteEvent note = notes.get(i);
            float x = leftPad + available * ((float) i / Math.max(1, notes.size() - 1));
            int step = diatonicStepFromBottomLineE4(note.noteName, note.octave);
            float y = yForStaffStep(step, bottomLineY, lineGap);
            drawLedgerLines(canvas, x, step, lineGap, bottomLineY, noteRadius);

            boolean mismatch = hasMismatch(i);
            boolean matched = isMatched(i);
            boolean durationMismatch = hasDurationMismatch(i);
            Paint circlePaint = mismatch ? mismatchNotePaint : (durationMismatch ? durationMismatchNotePaint : ((matched || i == pointer) ? activeNotePaint : notePaint));
            boolean stemUp = stemUpForNote(i, y, firstLineY, bottomLineY, noteStep, noteRadius);
            float stemOffsetX = stemOffsetForIndex(i, noteStep, noteRadius);
            drawDurationAwareNote(canvas, note, x, y, noteRadius, stemOffsetX, stemUp, circlePaint);

            String label = MusicNotation.toLocalizedLabel(getContext(), note.noteName, note.octave);
            float textWidth = labelPaint.measureText(label);
            float textLeft = Math.max(0f, Math.min(w - textWidth, x - textWidth / 2f));

            int row = selectLabelRow(textLeft, textWidth, lastLabelRight);
            while (lastLabelRight.size() <= row) {
                lastLabelRight.add(Float.NEGATIVE_INFINITY);
            }
            float minAllowedLeft = lastLabelRight.get(row) + NOTE_LABEL_MIN_GAP_PX;
            textLeft = Math.max(textLeft, minAllowedLeft);
            textLeft = Math.max(0f, Math.min(w - textWidth, textLeft));
            lastLabelRight.set(row, textLeft + textWidth);

            labelsToDraw.add(new LabelLayout(i, label, textLeft, row, (matched || i == pointer), mismatch, durationMismatch));
            noteDrawInfos.add(new NoteDrawInfo(i, x, y, Math.max(noteRadius * 2f, 28f)));
        }

        int rowCount = Math.max(1, lastLabelRight.size());
        float textHeight = labelTextHeight();
        float baselineStep = textHeight * 2f;
        float requiredHeight = requiredLabelPanelHeight(rowCount, textHeight);
        float availableLabelHeight = Math.max(1f, labelBottom - labelTop);
        float topInset = textHeight + Math.max(0f, (availableLabelHeight - requiredHeight) * 0.5f);
        Paint.FontMetrics fm = labelPaint.getFontMetrics();
        float baselineOffset = -fm.ascent;
        float firstBaselineY = labelTop + topInset + baselineOffset;

        for (LabelLayout labelLayout : labelsToDraw) {
            Paint textPaint = labelLayout.mismatch ? mismatchLabelPaint : (labelLayout.durationMismatch ? durationMismatchNotePaint : (labelLayout.active ? activeLabelPaint : labelPaint));
            float textY = firstBaselineY + labelLayout.y * baselineStep;
            canvas.drawText(labelLayout.text, labelLayout.x, textY, textPaint);

            Paint.FontMetrics metrics = textPaint.getFontMetrics();
            float textWidth = textPaint.measureText(labelLayout.text);
            float top = textY + metrics.ascent;
            float bottom = textY + metrics.descent;
            labelHitInfos.add(new LabelHitInfo(labelLayout.index, labelLayout.x, top, labelLayout.x + textWidth, bottom));
        }
    }


    private float labelTextHeight() {
        Paint.FontMetrics fm = labelPaint.getFontMetrics();
        return fm.descent - fm.ascent;
    }

    private float requiredLabelPanelHeight(int rowCount, float textHeight) {
        int rows = Math.max(1, rowCount);
        float topPadding = textHeight;
        float bottomPadding = textHeight;
        float totalTextHeight = rows * textHeight;
        float gapsBetweenRows = (rows - 1) * textHeight;
        return topPadding + totalTextHeight + gapsBetweenRows + bottomPadding;
    }


    private int estimateLabelRows(float w) {
        if (notes.isEmpty()) {
            return 1;
        }
        float maxLabelWidth = 0f;
        for (NoteEvent note : notes) {
            String label = MusicNotation.toLocalizedLabel(getContext(), note.noteName, note.octave);
            maxLabelWidth = Math.max(maxLabelWidth, labelPaint.measureText(label));
        }
        float usableWidth = Math.max(1f, w - 46f);
        int labelsPerRow = Math.max(1, (int) Math.floor(usableWidth / Math.max(1f, maxLabelWidth + NOTE_LABEL_MIN_GAP_PX)));
        return Math.max(1, (int) Math.ceil((double) notes.size() / (double) labelsPerRow));
    }

    private int selectLabelRow(float textLeft, float textWidth, List<Float> lastLabelRight) {
        for (int row = 0; row < lastLabelRight.size(); row++) {
            float right = lastLabelRight.get(row);
            if (textLeft >= right + NOTE_LABEL_MIN_GAP_PX) {
                return row;
            }
        }
        return lastLabelRight.size();
    }

    private void drawLedgerLines(Canvas canvas, float x, int step, float lineGap, float bottomLineY, float noteRadius) {
        float ledgerWidth = Math.max(16f, noteRadius * 1.8f);
        if (step <= -2) {
            for (int ledgerStep = -2; ledgerStep >= step; ledgerStep -= 2) {
                float y = yForStaffStep(ledgerStep, bottomLineY, lineGap);
                canvas.drawLine(x - ledgerWidth, y, x + ledgerWidth, y, staffPaint);
            }
        }
        if (step >= 10) {
            for (int ledgerStep = 10; ledgerStep <= step; ledgerStep += 2) {
                float y = yForStaffStep(ledgerStep, bottomLineY, lineGap);
                canvas.drawLine(x - ledgerWidth, y, x + ledgerWidth, y, staffPaint);
            }
        }
    }

    private float yForStaffStep(int step, float bottomLineY, float lineGap) {
        return bottomLineY - step * (lineGap / 2f);
    }

    private boolean hasMismatch(int index) { ensureMismatchCapacity(); return index >= 0 && index < mismatchActualByIndex.size() && mismatchActualByIndex.get(index) != null; }
    private boolean isMatched(int index) { ensureMatchedCapacity(); return index >= 0 && index < matchedByIndex.size() && matchedByIndex.get(index); }
    private boolean hasDurationMismatch(int index) { ensureDurationMismatchCapacity(); return index >= 0 && index < durationMismatchByIndex.size() && durationMismatchByIndex.get(index); }
    private void ensureMismatchCapacity() { while (mismatchActualByIndex.size() < notes.size()) mismatchActualByIndex.add(null); }
    private void ensureDurationMismatchCapacity() { while (durationMismatchByIndex.size() < notes.size()) durationMismatchByIndex.add(false); }
    private void ensureMatchedCapacity() { while (matchedByIndex.size() < notes.size()) matchedByIndex.add(false); while (matchedActualByIndex.size() < notes.size()) matchedActualByIndex.add(null); }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_UP) return true;
        if (playedNoteClickListener == null) return true;
        float touchX = event.getX(); float touchY = event.getY();

        for (LabelHitInfo labelHitInfo : labelHitInfos) {
            if (touchX >= labelHitInfo.left && touchX <= labelHitInfo.right
                    && touchY >= labelHitInfo.top && touchY <= labelHitInfo.bottom) {
                int index = labelHitInfo.index;
                String actual = hasMismatch(index) ? mismatchActualByIndex.get(index)
                        : (isMatched(index) ? matchedActualByIndex.get(index) : null);
                NoteEvent expected = notes.get(index);
                playedNoteClickListener.onPlayedNoteClick(index, expected.fullName(), actual);
                return true;
            }
        }

        for (NoteDrawInfo info : noteDrawInfos) {
            float dx = touchX - info.cx; float dy = touchY - info.cy;
            if (dx * dx + dy * dy <= info.hitRadius * info.hitRadius) {
                boolean mismatch = hasMismatch(info.index);
                boolean matched = isMatched(info.index);
                String actual = mismatch ? mismatchActualByIndex.get(info.index)
                        : (matched ? matchedActualByIndex.get(info.index) : null);
                NoteEvent expected = notes.get(info.index);
                playedNoteClickListener.onPlayedNoteClick(info.index, expected.fullName(), actual);
                return true;
            }
        }
        return true;
    }

    public interface OnPlayedNoteClickListener { void onPlayedNoteClick(int index, String expectedFullName, String actualFullName); }

    private void drawDurationAwareNote(Canvas canvas, NoteEvent note, float x, float y, float noteRadius, float stemOffsetX, boolean stemUp, Paint fillPaint) {
        RectF oval = new RectF(x - noteRadius, y - noteRadius * 0.75f, x + noteRadius, y + noteRadius * 0.75f);
        String duration = note == null ? null : note.duration;
        boolean whole = "whole".equals(duration); boolean half = "half".equals(duration); boolean hollow = whole || half;
        if (hollow) { noteStrokePaint.setColor(fillPaint.getColor()); canvas.drawOval(oval, noteStrokePaint); } else { canvas.drawOval(oval, fillPaint); }
        if (whole) return;
        float stemAnchorX = stemUp ? (x + noteRadius * 0.86f) : (x - noteRadius * 0.86f);
        float stemX = stemAnchorX + stemOffsetX;
        float noteEdgeY = y + (stemUp ? (-noteRadius * 0.58f) : (noteRadius * 0.58f));
        float stemEndY = y + (stemUp ? (-noteRadius * 2.6f) : (noteRadius * 2.6f));
        stemPaint.setColor(fillPaint.getColor()); canvas.drawLine(stemX, noteEdgeY, stemX, stemEndY, stemPaint);
        int flags = flagCountForDuration(duration);
        for (int f = 0; f < flags; f++) {
            float flagStartY = stemEndY + (stemUp ? 1f : -1f) * f * (noteRadius * 0.75f);
            float flagEndX = stemX + (stemUp ? 1f : -1f) * noteRadius * 1.6f;
            float flagEndY = flagStartY + (stemUp ? 1f : -1f) * noteRadius * 0.55f;
            canvas.drawLine(stemX, flagStartY, flagEndX, flagEndY, stemPaint);
        }
    }

    private boolean stemUpForNote(int index, float noteY, float topLineY, float bottomLineY, float noteStep, float noteRadius) {
        float middleLineY = (topLineY + bottomLineY) * 0.5f;
        boolean denseLayout = noteStep < noteRadius * 1.9f;
        if (denseLayout) return index % 2 == 0;
        return noteY >= middleLineY;
    }

    private float stemOffsetForIndex(int index, float noteStep, float noteRadius) {
        float minGapWithoutOffset = noteRadius * 1.9f;
        if (noteStep >= minGapWithoutOffset) return 0f;
        float overlap = minGapWithoutOffset - noteStep;
        float maxOffset = noteRadius * 0.65f;
        float computedOffset = Math.min(maxOffset, overlap * 0.5f);
        return index % 2 == 0 ? -computedOffset : computedOffset;
    }

    private static final class LabelLayout { private final int index; private final String text; private final float x; private final float y; private final boolean active; private final boolean mismatch; private final boolean durationMismatch;
        private LabelLayout(int index, String text, float x, float y, boolean active, boolean mismatch, boolean durationMismatch) { this.index = index; this.text = text; this.x = x; this.y = y; this.active = active; this.mismatch = mismatch; this.durationMismatch = durationMismatch; } }

    private static final class LabelHitInfo { private final int index; private final float left; private final float top; private final float right; private final float bottom;
        private LabelHitInfo(int index, float left, float top, float right, float bottom) { this.index = index; this.left = left; this.top = top; this.right = right; this.bottom = bottom; } }

    private void drawSpectrogram(Canvas canvas, float w, float top, float bottom) {
        if (bottom <= top) return;
        drawSpectrogramGrid(canvas, w, top, bottom); drawSpectrogramHeatmap(canvas, w, top, bottom);
        if (micMode && expectedHz > 0f) {
            float guideHz = Math.min(expectedHz * 2f, MAX_SPECTROGRAM_HZ);
            float expectedY = yForFrequency(guideHz, top, bottom);
            canvas.drawLine(0, expectedY, w, expectedY, expectedPaint);
        }
    }
    private void drawSpectrogramGrid(Canvas canvas, float w, float top, float bottom) {
        int gridLines = Math.max(1, (int) (MAX_SPECTROGRAM_HZ / 1000f));
        for (int i = 0; i <= gridLines; i++) {
            float hz = i * 1000f;
            float y = yForFrequency(hz, top, bottom);
            canvas.drawLine(0, y, w, y, spectrogramGridPaint);
            canvas.drawText(((int) hz) + " Hz", 8f, y - 4f, labelPaint);
        }
    }
    private void drawSpectrogramHeatmap(Canvas canvas, float w, float top, float bottom) {
        if (spectrumHistory.isEmpty()) return;
        int columns = spectrumHistory.size(); float colW = Math.max(1f, w / Math.max(1, columns));
        for (int x = 0; x < columns; x++) {
            float[] frame = spectrumHistory.get(x); if (frame == null || frame.length == 0) continue;
            int bins = frame.length; float frameMax = max(frame); if (frameMax <= 0f) frameMax = 1f;
            for (int bin = 0; bin < bins; bin++) {
                float hz = bin * lastSpectrumSampleRate / (2f * bins); if (hz > MAX_SPECTROGRAM_HZ) break;
                float nextHz = (bin + 1) * lastSpectrumSampleRate / (2f * bins);
                float yTop = yForFrequency(nextHz, top, bottom); float yBottom = yForFrequency(hz, top, bottom);
                float intensity = normalizeMagnitude(frame[bin], frameMax);
                heatPaint.setColor(heatColor(intensity)); heatPaint.setStyle(Paint.Style.FILL);
                float left = x * colW; canvas.drawRect(left, yTop, left + colW + 1f, yBottom, heatPaint);
            }
        }
    }
    private float normalizeMagnitude(float value, float frameMax) { if (value <= 0f || frameMax <= 0f) return 0f; return Math.max(0f, Math.min(1f, value / frameMax)); }
    private int heatColor(float intensity) { float clamped = Math.max(0f, Math.min(1f, intensity)); float hue = (1f - clamped) * 240f; return Color.HSVToColor(new float[]{hue, 1f, clamped}); }
    private float max(float[] values) { float max = 0f; for (float value : values) if (value > max) max = value; return max; }
    private int diatonicStepFromBottomLineE4(String noteName, int octave) { int letterIndex = letterIndex(noteName); int absolute = octave * 7 + letterIndex; int e4Absolute = 4 * 7 + 2; return absolute - e4Absolute; }
    private int letterIndex(String noteName) {
        if (noteName == null || noteName.length() == 0) return 0; char letter = Character.toUpperCase(noteName.charAt(0));
        if (letter == 'C') return 0; if (letter == 'D') return 1; if (letter == 'E') return 2; if (letter == 'F') return 3; if (letter == 'G') return 4; if (letter == 'A') return 5; return 6;
    }
    private int flagCountForDuration(String duration) { if ("eighth".equals(duration)) return 1; if ("16th".equals(duration)) return 2; return 0; }
    private static final class NoteDrawInfo { private final int index; private final float cx; private final float cy; private final float hitRadius; private NoteDrawInfo(int index, float cx, float cy, float hitRadius) { this.index = index; this.cx = cx; this.cy = cy; this.hitRadius = hitRadius; } }
    private float yForFrequency(float hz, float top, float bottom) { float clamped = Math.max(0f, Math.min(MAX_SPECTROGRAM_HZ, hz)); float norm = clamped / MAX_SPECTROGRAM_HZ; return bottom - norm * (bottom - top); }
}
