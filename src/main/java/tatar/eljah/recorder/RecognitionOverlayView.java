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
    public enum EditMode {
        NONE,
        ADD,
        DELETE,
        CHANGE_DURATION,
        MOVE_UP,
        MOVE_DOWN
    }

    public interface OnNotesEditedListener {
        void onNotesEdited(List<NoteEvent> notes);
    }

    private final Paint staffPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint notePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedNotePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint corridorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final List<NoteEvent> notes = new ArrayList<NoteEvent>();
    private final List<OpenCvScoreProcessor.StaffCorridor> staffCorridors = new ArrayList<OpenCvScoreProcessor.StaffCorridor>();
    private final RectF imageBounds = new RectF(0f, 0f, 1f, 1f);

    private EditMode editMode = EditMode.NONE;
    private OnNotesEditedListener onNotesEditedListener;

    private final ScaleGestureDetector scaleDetector;
    private float zoom = 1f;
    private float panX = 0f;
    private float panY = 0f;
    private float lastTouchX = Float.NaN;
    private float lastTouchY = Float.NaN;

    private int selectedIndex = -1;

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

        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                zoom *= detector.getScaleFactor();
                zoom = Math.max(1f, Math.min(4f, zoom));
                invalidate();
                return true;
            }
        });
    }

    public void setEditMode(EditMode mode) {
        this.editMode = mode == null ? EditMode.NONE : mode;
    }

    public EditMode getEditMode() {
        return editMode;
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
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        if (event.getPointerCount() > 1) {
            lastTouchX = Float.NaN;
            lastTouchY = Float.NaN;
            return true;
        }

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            lastTouchX = event.getX();
            lastTouchY = event.getY();
            handleTap(event.getX(), event.getY());
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_MOVE && zoom > 1.01f && editMode == EditMode.NONE) {
            if (!Float.isNaN(lastTouchX) && !Float.isNaN(lastTouchY)) {
                panX += event.getX() - lastTouchX;
                panY += event.getY() - lastTouchY;
                clampPan();
                invalidate();
            }
            lastTouchX = event.getX();
            lastTouchY = event.getY();
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            lastTouchX = Float.NaN;
            lastTouchY = Float.NaN;
        }
        return super.onTouchEvent(event);
    }

    private void clampPan() {
        float width = imageBounds.width();
        float height = imageBounds.height();
        float maxPanX = Math.max(0f, (zoom - 1f) * width * 0.5f);
        float maxPanY = Math.max(0f, (zoom - 1f) * height * 0.5f);
        panX = Math.max(-maxPanX, Math.min(maxPanX, panX));
        panY = Math.max(-maxPanY, Math.min(maxPanY, panY));
    }

    private void handleTap(float viewX, float viewY) {
        if (notes.isEmpty() && editMode != EditMode.ADD) {
            return;
        }

        float[] norm = toNormalized(viewX, viewY);
        float nx = norm[0];
        float ny = norm[1];
        if (nx < 0f || nx > 1f || ny < 0f || ny > 1f) {
            return;
        }

        int nearest = findNearestNote(nx, ny);
        switch (editMode) {
            case ADD:
                addNoteAt(nx, ny);
                break;
            case DELETE:
                if (nearest >= 0) {
                    notes.remove(nearest);
                    selectedIndex = -1;
                    notifyEdited();
                    invalidate();
                }
                break;
            case CHANGE_DURATION:
                if (nearest >= 0) {
                    NoteEvent old = notes.get(nearest);
                    notes.set(nearest, new NoteEvent(old.noteName, old.octave, nextDuration(old.duration), old.measure, old.x, old.y));
                    selectedIndex = nearest;
                    notifyEdited();
                    invalidate();
                }
                break;
            case MOVE_UP:
                if (nearest >= 0) {
                    moveNoteStep(nearest, 1);
                }
                break;
            case MOVE_DOWN:
                if (nearest >= 0) {
                    moveNoteStep(nearest, -1);
                }
                break;
            case NONE:
            default:
                selectedIndex = nearest;
                invalidate();
                break;
        }
    }

    private void addNoteAt(float nx, float ny) {
        int[] pitch = inferPitchFromPosition(ny);
        String name = noteNameForMidi(pitch[0]);
        int octave = octaveForMidi(pitch[0]);
        String duration = "quarter";
        NoteEvent event = new NoteEvent(name, octave, duration, 1, nx, ny);
        notes.add(event);
        sortNotesReadingOrder();
        selectedIndex = notes.indexOf(event);
        notifyEdited();
        invalidate();
    }

    private void moveNoteStep(int index, int direction) {
        if (index < 0 || index >= notes.size()) return;
        NoteEvent old = notes.get(index);
        float stepNorm = inferHalfStepNorm(old.y);
        float newY = Math.max(0f, Math.min(1f, old.y - direction * stepNorm));
        int[] pitch = inferPitchFromPosition(newY);
        notes.set(index, new NoteEvent(noteNameForMidi(pitch[0]), octaveForMidi(pitch[0]), old.duration, old.measure, old.x, newY));
        selectedIndex = index;
        sortNotesReadingOrder();
        notifyEdited();
        invalidate();
    }

    private String nextDuration(String d) {
        if ("whole".equals(d)) return "half";
        if ("half".equals(d)) return "quarter";
        if ("quarter".equals(d)) return "eighth";
        if ("eighth".equals(d)) return "sixteenth";
        return "whole";
    }

    private int[] inferPitchFromPosition(float ny) {
        OpenCvScoreProcessor.StaffCorridor c = nearestCorridor(ny);
        float top;
        float bottom;
        if (c != null) {
            top = c.top;
            bottom = c.bottom;
        } else {
            top = 0.2f;
            bottom = 0.8f;
        }
        float spacing = (bottom - top) / 6f;
        float linesY4 = top + spacing * 4f;
        int stepFromBottom = Math.round((linesY4 - ny) / (spacing / 2f));
        int midi = midiForTrebleStaffStep(stepFromBottom);
        return new int[]{midi};
    }

    private float inferHalfStepNorm(float ny) {
        OpenCvScoreProcessor.StaffCorridor c = nearestCorridor(ny);
        if (c == null) return 0.02f;
        float spacing = (c.bottom - c.top) / 6f;
        return Math.max(0.008f, spacing / 2f);
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
        List<NoteEvent> copy = new ArrayList<NoteEvent>(notes);
        onNotesEditedListener.onNotesEdited(copy);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (notes.isEmpty()) {
            return;
        }

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
            if (note.x < 0f || note.y < 0f) {
                continue;
            }
            float nx = Math.max(0f, Math.min(1f, note.x));
            float ny = Math.max(0f, Math.min(1f, note.y));
            float x = imageBounds.left + nx * width;
            float y = imageBounds.top + ny * height;

            for (int i = -2; i <= 2; i++) {
                canvas.drawLine(x - 38f, y + i * rowGap, x + 38f, y + i * rowGap, staffPaint);
            }
            Paint np = idx == selectedIndex ? selectedNotePaint : notePaint;
            canvas.drawOval(new RectF(x - noteRadius, y - noteRadius * 0.8f, x + noteRadius, y + noteRadius * 0.8f), np);
            canvas.drawText(MusicNotation.toEuropeanLabel(note.noteName, note.octave) + " " + note.duration, x - 40f, y - rowGap * 2.8f, labelPaint);
        }

        canvas.restore();
    }
}
