package tatar.eljah.recorder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
public class TankDefenseGameView extends View {
    private static final String[] LANES = {"C", "D", "E", "F", "G", "A", "B"};
    private static final long FIRST_NOTE_DELAY_MS = 2200L;
    private static final long NOTE_STEP_MS = 720L;
    private static final long TANK_TRAVEL_MS = 4300L;
    private static final long HIT_WINDOW_MS = 460L;
    private static final long SHOT_DURATION_MS = 220L;
    private static final int STAFF_LINE_COUNT = 5;
    private static final float STAFF_STEP = 13f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Target> targets = new ArrayList<Target>();
    private final List<Shot> shots = new ArrayList<Shot>();

    private long startMs;
    private int score;
    private int misses;
    private long lastInputAtMs;

    public TankDefenseGameView(Context context) {
        super(context);
        setBackgroundColor(Color.rgb(17, 22, 28));
        buildLevel();
        restart();
    }

    public void restart() {
        startMs = SystemClock.elapsedRealtime();
        score = 0;
        misses = 0;
        lastInputAtMs = 0L;
        shots.clear();
        for (Target target : targets) {
            target.hit = false;
            target.escaped = false;
            target.hitAtMs = 0L;
        }
        invalidate();
    }

    public void onDetectedNote(String fullName, float intensity) {
        if (fullName == null || fullName.length() == 0) {
            return;
        }
        lastInputAtMs = elapsedMs();
        String lane = baseNote(fullName);
        long now = elapsedMs();
        Target best = null;
        long bestDistance = Long.MAX_VALUE;
        for (Target target : targets) {
            if (target.hit || target.escaped || !target.noteName.equals(lane)) {
                continue;
            }
            long distance = Math.abs(now - target.dueMs);
            if (distance <= HIT_WINDOW_MS && distance < bestDistance) {
                best = target;
                bestDistance = distance;
            }
        }
        if (best != null) {
            best.hit = true;
            best.hitAtMs = now;
            score++;
            shots.add(new Shot(laneIndex(best.noteName), now, tankXFor(best, now)));
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = elapsedMs();
        updateMisses(now);
        drawHeader(canvas, now);
        drawBattlefield(canvas, now);
        postInvalidateDelayed(16L);
    }

    private void buildLevel() {
        String[] notes = {
                "E5", "E5", "E5", "E5", "E5", "E5", "E5", "G5", "C5", "D5", "E5",
                "F5", "F5", "F5", "F5", "F5", "E5", "E5", "E5", "E5", "D5", "D5", "E5", "D5", "G5",
                "E5", "E5", "E5", "E5", "E5", "E5", "E5", "G5", "C5", "D5", "E5"
        };
        for (int i = 0; i < notes.length; i++) {
            String fullName = notes[i];
            targets.add(new Target(fullName, baseNote(fullName), FIRST_NOTE_DELAY_MS + i * NOTE_STEP_MS));
        }
    }

    private void drawHeader(Canvas canvas, long now) {
        int width = getWidth();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(240, 238, 226));
        canvas.drawRect(0, 0, width, topStripHeight(), paint);

        drawStaff(canvas, now);

        paint.setTextSize(24f);
        paint.setColor(Color.rgb(34, 43, 50));
        canvas.drawText("Score " + score + "/" + targets.size() + "   Miss " + misses, 24f, 36f, paint);

        if (now - lastInputAtMs < 180L) {
            paint.setColor(Color.rgb(74, 165, 104));
            canvas.drawCircle(width - 34f, 30f, 10f, paint);
        }
    }

    private void drawStaff(Canvas canvas, long now) {
        float left = 22f;
        float right = getWidth() - 22f;
        float centerY = staffCenterY();
        float bottomLineY = centerY + 2f * STAFF_STEP;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        paint.setColor(Color.rgb(33, 38, 42));
        for (int i = 0; i < STAFF_LINE_COUNT; i++) {
            float y = bottomLineY - i * STAFF_STEP;
            canvas.drawLine(left, y, right, y, paint);
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(56f);
        paint.setColor(Color.rgb(33, 38, 42));
        canvas.drawText("G", left + 6f, centerY + 18f, paint);

        float playX = timingZoneX();
        paint.setColor(Color.rgb(209, 60, 53));
        paint.setStrokeWidth(5f);
        canvas.drawLine(playX, centerY - 48f, playX, centerY + 58f, paint);

        float pixelsPerMs = 0.18f;
        for (Target target : targets) {
            float x = playX + (now - target.dueMs) * pixelsPerMs;
            if (x < -70f || x > getWidth() + 70f) {
                continue;
            }
            drawStaffNote(canvas, x, staffYFor(target.fullName), target);
        }
    }

    private void drawStaffNote(Canvas canvas, float x, float y, Target target) {
        paint.setStyle(Paint.Style.FILL);
        if (target.hit) {
            paint.setColor(Color.rgb(74, 165, 104));
        } else if (target.escaped) {
            paint.setColor(Color.rgb(201, 72, 64));
        } else {
            paint.setColor(Color.rgb(19, 24, 29));
        }
        canvas.save();
        canvas.rotate(-18f, x, y);
        RectF head = new RectF(x - 14f, y - 9f, x + 14f, y + 9f);
        canvas.drawOval(head, paint);
        canvas.restore();

        paint.setStrokeWidth(4f);
        canvas.drawLine(x + 12f, y, x + 12f, y - 48f, paint);
        if (requiresLedgerLine(target.fullName)) {
            paint.setStrokeWidth(3f);
            canvas.drawLine(x - 20f, y, x + 20f, y, paint);
        }
    }

    private void drawBattlefield(Canvas canvas, long now) {
        int top = topStripHeight();
        int height = getHeight() - top;
        int width = getWidth();
        float laneHeight = height / (float) LANES.length;

        paint.setTextSize(24f);
        for (int i = 0; i < LANES.length; i++) {
            float centerY = top + laneHeight * (i + 0.5f);
            paint.setColor(i % 2 == 0 ? Color.rgb(35, 48, 48) : Color.rgb(31, 43, 47));
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRect(0, top + laneHeight * i, width, top + laneHeight * (i + 1), paint);

            paint.setColor(Color.rgb(93, 122, 127));
            paint.setStrokeWidth(2f);
            canvas.drawLine(86f, centerY, width - 24f, centerY, paint);

            drawCannon(canvas, 48f, centerY, i);
        }

        for (Target target : targets) {
            if (target.escaped) {
                continue;
            }
            if (target.hit && now - target.hitAtMs > 520L) {
                continue;
            }
            int lane = laneIndex(target.noteName);
            if (lane < 0) {
                continue;
            }
            float y = top + laneHeight * (lane + 0.5f);
            float x = tankXFor(target, now);
            if (x < -80f || x > width + 120f) {
                continue;
            }
            if (target.hit) {
                drawExplosion(canvas, x, y, now - target.hitAtMs);
            } else {
                drawTank(canvas, x, y);
            }
        }

        drawShots(canvas, now, top, laneHeight);
    }

    private void drawCannon(Canvas canvas, float x, float y, int lane) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(cannonColor(lane));
        canvas.drawRect(x - 18f, y - 18f, x + 18f, y + 18f, paint);
        paint.setStrokeWidth(10f);
        paint.setColor(brighter(cannonColor(lane)));
        canvas.drawLine(x + 12f, y, x + 56f, y, paint);
    }

    private void drawTank(Canvas canvas, float x, float y) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(93, 112, 67));
        RectF body = new RectF(x - 35f, y - 18f, x + 35f, y + 14f);
        canvas.drawRoundRect(body, 7f, 7f, paint);
        paint.setColor(Color.rgb(119, 139, 83));
        RectF turret = new RectF(x - 12f, y - 32f, x + 18f, y - 10f);
        canvas.drawRoundRect(turret, 6f, 6f, paint);
        paint.setStrokeWidth(7f);
        canvas.drawLine(x - 10f, y - 20f, x - 46f, y - 22f, paint);
        paint.setColor(Color.rgb(32, 37, 31));
        canvas.drawCircle(x - 22f, y + 18f, 7f, paint);
        canvas.drawCircle(x + 22f, y + 18f, 7f, paint);
    }

    private void drawExplosion(Canvas canvas, float x, float y, long ageMs) {
        float radius = 14f + Math.min(1f, ageMs / 520f) * 34f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(245, 176, 53));
        canvas.drawCircle(x, y, radius, paint);
        paint.setColor(Color.rgb(238, 88, 54));
        canvas.drawCircle(x, y, radius * 0.55f, paint);
    }

    private void drawShots(Canvas canvas, long now, int top, float laneHeight) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(249, 232, 126));
        for (int i = shots.size() - 1; i >= 0; i--) {
            Shot shot = shots.get(i);
            float progress = (now - shot.startedAtMs) / (float) SHOT_DURATION_MS;
            if (progress >= 1f) {
                shots.remove(i);
                continue;
            }
            float y = top + laneHeight * (shot.lane + 0.5f);
            float x = 92f + (shot.targetX - 92f) * Math.max(0f, progress);
            canvas.drawCircle(x, y, 8f, paint);
        }
    }

    private void updateMisses(long now) {
        int missCount = 0;
        for (Target target : targets) {
            if (!target.hit && !target.escaped && now > target.dueMs + HIT_WINDOW_MS) {
                target.escaped = true;
            }
            if (target.escaped) {
                missCount++;
            }
        }
        misses = missCount;
    }

    private float tankXFor(Target target, long now) {
        float startX = getWidth() + 70f;
        float hitX = Math.max(180f, getWidth() * 0.68f);
        float baseX = 92f;
        if (now <= target.dueMs) {
            float progress = (now - (target.dueMs - TANK_TRAVEL_MS)) / (float) TANK_TRAVEL_MS;
            progress = Math.max(0f, Math.min(1f, progress));
            return startX + (hitX - startX) * progress;
        }
        float progress = (now - target.dueMs) / (float) HIT_WINDOW_MS;
        progress = Math.max(0f, Math.min(1f, progress));
        return hitX + (baseX - hitX) * progress;
    }

    private int topStripHeight() {
        return Math.max(176, getHeight() / 3);
    }

    private float timingZoneX() {
        return getWidth() * 0.64f;
    }

    private float staffCenterY() {
        return topStripHeight() * 0.58f;
    }

    private float staffYFor(String fullName) {
        int step = staffStepFromBottomLine(fullName);
        return staffCenterY() + 2f * STAFF_STEP - step * (STAFF_STEP / 2f);
    }

    private int staffStepFromBottomLine(String fullName) {
        MusicNotation.ParsedNote parsed = MusicNotation.parseNormalizedNoteKey(
                MusicNotation.normalizeNoteKey(fullName));
        if (parsed == null || parsed.noteName.length() == 0) {
            return 4;
        }
        String[] cycle = {"C", "D", "E", "F", "G", "A", "B"};
        int noteIndex = 0;
        String base = String.valueOf(Character.toUpperCase(parsed.noteName.charAt(0)));
        for (int i = 0; i < cycle.length; i++) {
            if (cycle[i].equals(base)) {
                noteIndex = i;
                break;
            }
        }
        int absolute = parsed.octave * 7 + noteIndex;
        int e4 = 4 * 7 + 2;
        return absolute - e4;
    }

    private boolean requiresLedgerLine(String fullName) {
        int step = staffStepFromBottomLine(fullName);
        return step <= -2 || step >= 10;
    }

    private int cannonColor(int lane) {
        int[] colors = {
                Color.rgb(58, 130, 171),
                Color.rgb(64, 153, 119),
                Color.rgb(196, 143, 58),
                Color.rgb(174, 95, 92),
                Color.rgb(119, 110, 180),
                Color.rgb(79, 154, 166),
                Color.rgb(156, 117, 67)
        };
        return colors[Math.max(0, Math.min(colors.length - 1, lane))];
    }

    private int brighter(int color) {
        return Color.rgb(
                Math.min(255, Color.red(color) + 36),
                Math.min(255, Color.green(color) + 36),
                Math.min(255, Color.blue(color) + 36));
    }

    private long elapsedMs() {
        return SystemClock.elapsedRealtime() - startMs;
    }

    private static String baseNote(String fullName) {
        if (fullName == null || fullName.length() == 0) {
            return "";
        }
        return String.valueOf(Character.toUpperCase(fullName.charAt(0)));
    }

    private static int laneIndex(String noteName) {
        for (int i = 0; i < LANES.length; i++) {
            if (LANES[i].equals(noteName)) {
                return i;
            }
        }
        return -1;
    }

    static int levelTargetCount() {
        return 36;
    }

    private static final class Target {
        final String fullName;
        final String noteName;
        final long dueMs;
        boolean hit;
        boolean escaped;
        long hitAtMs;

        Target(String fullName, String noteName, long dueMs) {
            this.fullName = fullName;
            this.noteName = noteName;
            this.dueMs = dueMs;
        }
    }

    private static final class Shot {
        final int lane;
        final long startedAtMs;
        final float targetX;

        Shot(int lane, long startedAtMs, float targetX) {
            this.lane = lane;
            this.startedAtMs = startedAtMs;
            this.targetX = targetX;
        }
    }
}
