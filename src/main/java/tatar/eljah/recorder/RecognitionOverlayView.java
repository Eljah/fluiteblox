package tatar.eljah.recorder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class RecognitionOverlayView extends View {
    public interface OnNotesEditedListener {
        void onNotesEdited(List<NoteEvent> notes);
    }

    private enum PopupAction {
        DELETE, DURATION, MOVE_UP, MOVE_DOWN,
        ADD_QUARTER, ADD_HALF, ADD_EIGHTH, ADD_UP, ADD_DOWN
    }

    private static class PopupItem {
        RectF rect;
        String label;
        PopupAction action;

        PopupItem(RectF rect, String label, PopupAction action) {
            this.rect = rect;
            this.label = label;
            this.action = action;
        }
    }

    private final Paint staffPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint notePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedNotePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint corridorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint popupPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint popupTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final List<NoteEvent> notes = new ArrayList<NoteEvent>();
    private final List<OpenCvScoreProcessor.StaffCorridor> staffCorridors = new ArrayList<OpenCvScoreProcessor.StaffCorridor>();
    private final RectF imageBounds = new RectF(0f, 0f, 1f, 1f);

    private OnNotesEditedListener onNotesEditedListener;

    private final ScaleGestureDetector scaleDetector;
    private float zoom = 1f;
    private float panX = 0f;
    private float panY = 0f;
    private float lastTouchX = Float.NaN;
    private float lastTouchY = Float.NaN;

    private int selectedIndex = -1;
    private float lastTapNormX = -1f;
    private float lastTapNormY = -1f;
    private boolean popupVisible;
    private final List<PopupItem> popupItems = new ArrayList<PopupItem>();

    public enum InteractionMode { EDIT, PAN_ONLY }

    private View underlayView;
    private InteractionMode interactionMode = InteractionMode.EDIT;
    private boolean suppressNextUpAfterPopup;

    public RecognitionOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        staffPaint.setColor(Color.argb(190, 30, 30, 30));
        staffPaint.setStrokeWidth(2f);

        notePaint.setColor(Color.argb(210, 25, 118, 210));
        selectedNotePaint.setColor(Color.argb(230, 244, 67, 54));
        labelPaint.setColor(Color.argb(220, 46, 125, 50));
        labelPaint.setTextSize(24f);

        corridorPaint.setColor(Color.argb(180, 186, 85, 211));
        corridorPaint.setStyle(Paint.Style.STROKE);
        corridorPaint.setStrokeWidth(1.2f);

        popupPaint.setColor(Color.argb(235, 33, 33, 33));
        popupTextPaint.setColor(Color.WHITE);
        popupTextPaint.setTextSize(22f);

        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                zoom *= detector.getScaleFactor();
                zoom = Math.max(1f, Math.min(4f, zoom));
                clampPan();
                syncUnderlayTransform();
                invalidate();
                return true;
            }
        });
    }

    public void setUnderlayView(View view) {
        this.underlayView = view;
        syncUnderlayTransform();
    }

    public void setInteractionMode(InteractionMode mode) {
        this.interactionMode = mode == null ? InteractionMode.EDIT : mode;
        popupVisible = false;
        popupItems.clear();
        selectedIndex = -1;
        invalidate();
    }

    public void setOnNotesEditedListener(OnNotesEditedListener listener) {
        this.onNotesEditedListener = listener;
    }

    public void setRecognizedNotes(List<NoteEvent> source) {
        notes.clear();
        if (source != null) {
            notes.addAll(source);
        }
        selectedIndex = -1;
        popupVisible = false;
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
        syncUnderlayTransform();
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        if (event.getPointerCount() > 1) {
            popupVisible = false;
            lastTouchX = Float.NaN;
            lastTouchY = Float.NaN;
            invalidate();
            return true;
        }

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            float x = event.getX();
            float y = event.getY();

            if (interactionMode == InteractionMode.EDIT && popupVisible) {
                boolean consumed = handlePopupTap(x, y);
                suppressNextUpAfterPopup = consumed;
                lastTouchX = Float.NaN;
                lastTouchY = Float.NaN;
                return consumed;
            }
            popupVisible = false;

            lastTouchX = x;
            lastTouchY = y;
            return true;
        }

        if (event.getAction() == MotionEvent.ACTION_MOVE && zoom > 1.01f) {
            if (suppressNextUpAfterPopup) {
                return true;
            }
            if (!Float.isNaN(lastTouchX) && !Float.isNaN(lastTouchY)) {
                panX += event.getX() - lastTouchX;
                panY += event.getY() - lastTouchY;
                clampPan();
                syncUnderlayTransform();
                invalidate();
            }
            lastTouchX = event.getX();
            lastTouchY = event.getY();
            return true;
        }

        if (event.getAction() == MotionEvent.ACTION_UP) {
            if (suppressNextUpAfterPopup) {
                suppressNextUpAfterPopup = false;
                lastTouchX = Float.NaN;
                lastTouchY = Float.NaN;
                return true;
            }
            float dx = Float.isNaN(lastTouchX) ? 0f : Math.abs(event.getX() - lastTouchX);
            float dy = Float.isNaN(lastTouchY) ? 0f : Math.abs(event.getY() - lastTouchY);
            if (interactionMode == InteractionMode.EDIT && dx < 12f && dy < 12f) {
                openContextPopupAt(event.getX(), event.getY());
            }
            lastTouchX = Float.NaN;
            lastTouchY = Float.NaN;
            return true;
        }

        if (event.getAction() == MotionEvent.ACTION_CANCEL) {
            lastTouchX = Float.NaN;
            lastTouchY = Float.NaN;
        }
        return super.onTouchEvent(event);
    }

    private void openContextPopupAt(float viewX, float viewY) {
        float[] norm = toNormalized(viewX, viewY);
        float nx = norm[0];
        float ny = norm[1];
        if (nx < 0f || nx > 1f || ny < 0f || ny > 1f) {
            popupVisible = false;
            invalidate();
            return;
        }

        int nearest = findNearestNote(nx, ny);
        selectedIndex = nearest;
        lastTapNormX = nx;
        lastTapNormY = ny;
        buildPopupItems(viewX, viewY, nearest >= 0);
        popupVisible = !popupItems.isEmpty();
        invalidate();
    }

    private void buildPopupItems(float cx, float cy, boolean hasNote) {
        popupItems.clear();
        float w = 128f;
        float h = 54f;
        float gap = 8f;

        String[] labels;
        PopupAction[] actions;
        if (hasNote) {
            labels = new String[]{"Del", "Dur", "Up", "Down"};
            actions = new PopupAction[]{PopupAction.DELETE, PopupAction.DURATION, PopupAction.MOVE_UP, PopupAction.MOVE_DOWN};
        } else {
            labels = new String[]{"+1/4", "+1/2", "+1/8", "+Up", "+Down"};
            actions = new PopupAction[]{PopupAction.ADD_QUARTER, PopupAction.ADD_HALF, PopupAction.ADD_EIGHTH, PopupAction.ADD_UP, PopupAction.ADD_DOWN};
        }

        float startX = cx - (labels.length * w + (labels.length - 1) * gap) * 0.5f;
        float y = cy - 82f;
        for (int i = 0; i < labels.length; i++) {
            float x = startX + i * (w + gap);
            RectF r = new RectF(x, y, x + w, y + h);
            popupItems.add(new PopupItem(r, labels[i], actions[i]));
        }
    }

    private boolean handlePopupTap(float x, float y) {
        for (PopupItem item : popupItems) {
            if (item.rect.contains(x, y)) {
                applyPopupAction(item.action);
                popupVisible = false;
                popupItems.clear();
                invalidate();
                return true;
            }
        }
        popupVisible = false;
        popupItems.clear();
        invalidate();
        return true;
    }

    private void applyPopupAction(PopupAction action) {
        switch (action) {
            case DELETE:
                if (selectedIndex >= 0 && selectedIndex < notes.size()) {
                    notes.remove(selectedIndex);
                    selectedIndex = -1;
                    notifyEdited();
                }
                break;
            case DURATION:
                if (selectedIndex >= 0 && selectedIndex < notes.size()) {
                    NoteEvent old = notes.get(selectedIndex);
                    notes.set(selectedIndex, new NoteEvent(old.noteName, old.octave, nextDuration(old.duration), old.measure, old.x, old.y));
                    notifyEdited();
                }
                break;
            case MOVE_UP:
                moveSelected(1);
                break;
            case MOVE_DOWN:
                moveSelected(-1);
                break;
            case ADD_QUARTER:
                addAtTap("quarter", 0);
                break;
            case ADD_HALF:
                addAtTap("half", 0);
                break;
            case ADD_EIGHTH:
                addAtTap("eighth", 0);
                break;
            case ADD_UP:
                addAtTap("quarter", 1);
                break;
            case ADD_DOWN:
                addAtTap("quarter", -1);
                break;
        }
    }

    private void addAtTap(String duration, int shiftSteps) {
        if (lastTapNormX < 0f || lastTapNormY < 0f) return;
        float y = Math.max(0f, Math.min(1f, lastTapNormY - shiftSteps * inferHalfStepNorm(lastTapNormY)));
        int[] pitch = inferPitchFromPosition(y);
        NoteEvent n = new NoteEvent(noteNameForMidi(pitch[0]), octaveForMidi(pitch[0]), duration, 1, lastTapNormX, y);
        notes.add(n);
        sortNotesReadingOrder();
        selectedIndex = notes.indexOf(n);
        notifyEdited();
    }

    private void moveSelected(int direction) {
        if (selectedIndex < 0 || selectedIndex >= notes.size()) return;
        NoteEvent old = notes.get(selectedIndex);
        float stepNorm = inferHalfStepNorm(old.y);
        float newY = Math.max(0f, Math.min(1f, old.y - direction * stepNorm));
        int[] pitch = inferPitchFromPosition(newY);
        notes.set(selectedIndex, new NoteEvent(noteNameForMidi(pitch[0]), octaveForMidi(pitch[0]), old.duration, old.measure, old.x, newY));
        sortNotesReadingOrder();
        notifyEdited();
    }

    private String nextDuration(String d) {
        if ("whole".equals(d)) return "half";
        if ("half".equals(d)) return "quarter";
        if ("quarter".equals(d)) return "eighth";
        if ("eighth".equals(d)) return "sixteenth";
        return "whole";
    }

    private void syncUnderlayTransform() {
        if (underlayView == null) return;
        float cx = imageBounds.centerX();
        float cy = imageBounds.centerY();
        underlayView.setPivotX(cx);
        underlayView.setPivotY(cy);
        underlayView.setScaleX(zoom);
        underlayView.setScaleY(zoom);
        underlayView.setTranslationX(panX);
        underlayView.setTranslationY(panY);
    }

    private void clampPan() {
        float width = imageBounds.width();
        float height = imageBounds.height();
        float maxPanX = Math.max(0f, (zoom - 1f) * width * 0.5f);
        float maxPanY = Math.max(0f, (zoom - 1f) * height * 0.5f);
        panX = Math.max(-maxPanX, Math.min(maxPanX, panX));
        panY = Math.max(-maxPanY, Math.min(maxPanY, panY));
    }

    private int[] inferPitchFromPosition(float ny) {
        OpenCvScoreProcessor.StaffCorridor c = nearestCorridor(ny);
        float top = c == null ? 0.2f : c.top;
        float bottom = c == null ? 0.8f : c.bottom;
        float spacing = (bottom - top) / 6f;
        float linesY4 = top + spacing * 4f;
        int stepFromBottom = Math.round((linesY4 - ny) / (spacing / 2f));
        return new int[]{midiForTrebleStaffStep(stepFromBottom)};
    }

    private float inferHalfStepNorm(float ny) {
        OpenCvScoreProcessor.StaffCorridor c = nearestCorridor(ny);
        if (c == null) return 0.02f;
        return Math.max(0.008f, ((c.bottom - c.top) / 6f) / 2f);
    }

    private int midiForTrebleStaffStep(int stepFromBottom) {
        String[] naturalCycle = new String[]{"C", "D", "E", "F", "G", "A", "B"};
        int baseIndex = 2;
        int noteIndex = baseIndex + stepFromBottom;
        int octaveShift = Math.floorDiv(noteIndex, 7);
        int idx = ((noteIndex % 7) + 7) % 7;
        int octave = 4 + octaveShift;
        return MusicNotation.midiFor(naturalCycle[idx], octave);
    }

    private String noteNameForMidi(int midi) {
        String[] names = new String[]{"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
        return names[((midi % 12) + 12) % 12];
    }

    private int octaveForMidi(int midi) {
        return (midi / 12) - 1;
    }

    private OpenCvScoreProcessor.StaffCorridor nearestCorridor(float y) {
        OpenCvScoreProcessor.StaffCorridor best = null;
        float bestDist = Float.MAX_VALUE;
        for (OpenCvScoreProcessor.StaffCorridor c : staffCorridors) {
            float cy = (c.top + c.bottom) * 0.5f;
            float d = Math.abs(y - cy);
            if (d < bestDist) {
                bestDist = d;
                best = c;
            }
        }
        return best;
    }

    private void sortNotesReadingOrder() {
        Collections.sort(notes, new Comparator<NoteEvent>() {
            @Override
            public int compare(NoteEvent a, NoteEvent b) {
                OpenCvScoreProcessor.StaffCorridor ca = nearestCorridor(a.y);
                OpenCvScoreProcessor.StaffCorridor cb = nearestCorridor(b.y);
                float ay = ca == null ? a.y : (ca.top + ca.bottom) * 0.5f;
                float by = cb == null ? b.y : (cb.top + cb.bottom) * 0.5f;
                if (Math.abs(ay - by) > 0.02f) return ay < by ? -1 : 1;
                return a.x < b.x ? -1 : (a.x > b.x ? 1 : 0);
            }
        });
    }

    private int findNearestNote(float nx, float ny) {
        int best = -1;
        float bestDist = Float.MAX_VALUE;
        for (int i = 0; i < notes.size(); i++) {
            NoteEvent n = notes.get(i);
            if (n.x < 0f || n.y < 0f) continue;
            float dx = n.x - nx;
            float dy = n.y - ny;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return bestDist <= 0.08f ? best : -1;
    }

    private float[] toNormalized(float viewX, float viewY) {
        float width = imageBounds.width();
        float height = imageBounds.height();
        if (width <= 1f || height <= 1f) {
            width = getWidth();
            height = getHeight();
            imageBounds.set(0f, 0f, width, height);
        }

        float cx = imageBounds.centerX();
        float cy = imageBounds.centerY();
        float localX = (viewX - cx - panX) / zoom + cx;
        float localY = (viewY - cy - panY) / zoom + cy;

        float nx = (localX - imageBounds.left) / Math.max(1f, width);
        float ny = (localY - imageBounds.top) / Math.max(1f, height);
        return new float[]{nx, ny};
    }

    private void notifyEdited() {
        if (onNotesEditedListener == null) return;
        onNotesEditedListener.onNotesEdited(new ArrayList<NoteEvent>(notes));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = imageBounds.width();
        float height = imageBounds.height();
        if (width <= 1f || height <= 1f) {
            width = getWidth();
            height = getHeight();
            imageBounds.set(0f, 0f, width, height);
        }
        float noteRadius = Math.max(6f, width / 110f);
        float rowGap = Math.max(8f, height / 90f);

        float cx = imageBounds.centerX();
        float cy = imageBounds.centerY();
        canvas.save();
        canvas.translate(panX, panY);
        canvas.scale(zoom, zoom, cx, cy);

        for (OpenCvScoreProcessor.StaffCorridor corridor : staffCorridors) {
            float x0 = imageBounds.left + Math.max(0f, Math.min(1f, corridor.left)) * width;
            float x1 = imageBounds.left + Math.max(0f, Math.min(1f, corridor.right)) * width;
            float y0 = imageBounds.top + Math.max(0f, Math.min(1f, corridor.top)) * height;
            float y1 = imageBounds.top + Math.max(0f, Math.min(1f, corridor.bottom)) * height;
            canvas.drawRect(new RectF(x0, y0, x1, y1), corridorPaint);
        }

        for (int idx = 0; idx < notes.size(); idx++) {
            NoteEvent note = notes.get(idx);
            if (note.x < 0f || note.y < 0f) continue;
            float x = imageBounds.left + Math.max(0f, Math.min(1f, note.x)) * width;
            float y = imageBounds.top + Math.max(0f, Math.min(1f, note.y)) * height;

            for (int i = -2; i <= 2; i++) {
                canvas.drawLine(x - 38f, y + i * rowGap, x + 38f, y + i * rowGap, staffPaint);
            }
            Paint p = idx == selectedIndex ? selectedNotePaint : notePaint;
            canvas.drawOval(new RectF(x - noteRadius, y - noteRadius * 0.8f, x + noteRadius, y + noteRadius * 0.8f), p);
            canvas.drawText(MusicNotation.toEuropeanLabel(note.noteName, note.octave) + " " + note.duration, x - 40f, y - rowGap * 2.8f, labelPaint);
        }
        canvas.restore();

        if (popupVisible) {
            for (PopupItem item : popupItems) {
                canvas.drawRoundRect(item.rect, 12f, 12f, popupPaint);
                canvas.drawText(item.label, item.rect.left + 18f, item.rect.centerY() + 8f, popupTextPaint);
            }
        }
    }
}
