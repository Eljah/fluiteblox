package tatar.eljah.recorder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class TankDefenseGameView extends View {
    public interface CurrentNoteListener {
        void onCurrentNoteChanged(String fullName);
    }

    public interface GameResultListener {
        void onGameFinished(GameResult result);
    }

    private static final String[] LANES = {"C", "D", "E", "F", "G", "A", "B"};
    private static final long FIRST_NOTE_DELAY_MS = 2200L;
    private static final long TANK_TRAVEL_MS = 4300L;
    private static final long HIT_WINDOW_MS = 460L;
    private static final long SHOT_DURATION_MS = 220L;
    private static final int STAFF_LINE_COUNT = 5;
    private static final float STAFF_STEP = 13f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Target> targets = new ArrayList<Target>();
    private final List<Shot> shots = new ArrayList<Shot>();
    private ScorePiece piece;

    private long startMs;
    private float speedMultiplier = 1f;
    private int score;
    private int misses;
    private long lastInputAtMs;
    private boolean demoAutoFire;
    private boolean durationMode;
    private boolean completionNotified;
    private String currentTitleNote;
    private CurrentNoteListener currentNoteListener;
    private GameResultListener gameResultListener;

    public TankDefenseGameView(Context context, ScorePiece piece) {
        super(context);
        this.piece = piece;
        setBackgroundColor(Color.rgb(17, 22, 28));
        paint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        buildLevel();
        restart();
    }

    public void restart() {
        startMs = SystemClock.elapsedRealtime();
        score = 0;
        misses = 0;
        lastInputAtMs = 0L;
        completionNotified = false;
        currentTitleNote = null;
        shots.clear();
        for (Target target : targets) {
            target.hit = false;
            target.escaped = false;
            target.hitAtMs = 0L;
            target.hitX = 0f;
        }
        invalidate();
    }

    public void setCurrentNoteListener(CurrentNoteListener listener) {
        currentNoteListener = listener;
        notifyCurrentNoteChanged(currentNoteForTitle(elapsedMs()));
    }

    public void setGameResultListener(GameResultListener listener) {
        gameResultListener = listener;
    }

    public void setSpeedMultiplier(float speedMultiplier) {
        float newSpeed = Math.max(0.05f, Math.min(1.8f, speedMultiplier));
        if (Math.abs(newSpeed - this.speedMultiplier) < 0.001f) {
            return;
        }
        long now = elapsedMs();
        float oldSpeed = this.speedMultiplier;
        this.speedMultiplier = newSpeed;
        reschedulePendingTargets(now, oldSpeed, newSpeed);
        invalidate();
    }

    public float speedMultiplier() {
        return speedMultiplier;
    }

    public void setPiece(ScorePiece piece) {
        this.piece = piece;
        buildLevel();
        restart();
    }

    public void setDemoAutoFire(boolean demoAutoFire) {
        this.demoAutoFire = demoAutoFire;
        invalidate();
    }

    public void setDurationMode(boolean durationMode) {
        this.durationMode = durationMode;
        invalidate();
    }

    public boolean isDurationMode() {
        return durationMode;
    }

    public void onDetectedNote(String fullName, float intensity) {
        onDetectedNote(fullName, intensity, Long.MAX_VALUE);
    }

    public void onDetectedNote(String fullName, float intensity, long performedDurationMs) {
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
            if (distance <= hitWindowMs() && distance < bestDistance) {
                best = target;
                bestDistance = distance;
            }
        }
        if (best != null) {
            float hitX = tankXFor(best, now);
            boolean enoughDuration = !durationMode || performedDurationMs >= noteDurationMs(best);
            shots.add(new Shot(laneIndex(best.noteName), now, hitX, performedDurationMs, enoughDuration));
            if (enoughDuration) {
                best.hit = true;
                best.hitAtMs = now;
                best.hitX = hitX;
                score++;
            }
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = elapsedMs();
        autoFireDemoTargets(now);
        updateMisses(now);
        updateCurrentTitleNote(now);
        drawHeader(canvas, now);
        drawBattlefield(canvas, now);
        postInvalidateDelayed(16L);
    }

    private void buildLevel() {
        targets.clear();
        if (piece == null || piece.notes == null) {
            return;
        }
        long dueMs = firstNoteDelayMs();
        for (int i = 0; i < piece.notes.size(); i++) {
            NoteEvent note = piece.notes.get(i);
            String fullName = note.fullName();
            targets.add(new Target(fullName, baseNote(fullName), note.duration, dueMs));
            dueMs += scaledDurationMs(note.duration);
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
        String title = piece == null || piece.title == null ? "" : piece.title;
        canvas.drawText("Score " + score + "/" + targets.size() + "   Miss " + misses, 24f, 34f, paint);
        paint.setTextSize(20f);
        canvas.drawText(title, 24f, 62f, paint);

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
            float x = playX + (target.dueMs - now) * pixelsPerMs;
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
        boolean hollow = isHalf(target.duration) || isWhole(target.duration);
        canvas.save();
        canvas.rotate(-18f, x, y);
        RectF head = new RectF(x - 14f, y - 9f, x + 14f, y + 9f);
        if (hollow) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4f);
            canvas.drawOval(head, paint);
        } else {
            canvas.drawOval(head, paint);
        }
        canvas.restore();

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f);
        if (!isWhole(target.duration)) {
            float stemTop = y - 48f;
            canvas.drawLine(x + 12f, y, x + 12f, stemTop, paint);
            if (isEighth(target.duration) || isSixteenth(target.duration)) {
                drawFlag(canvas, x + 12f, stemTop, 0f);
            }
            if (isSixteenth(target.duration)) {
                drawFlag(canvas, x + 12f, stemTop + 14f, 0f);
            }
        }
        if (requiresLedgerLine(target.fullName)) {
            paint.setStrokeWidth(3f);
            canvas.drawLine(x - 20f, y, x + 20f, y, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(14f);
        paint.setColor(Color.rgb(75, 83, 88));
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(durationLabel(target.duration), x, y + 30f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawFlag(Canvas canvas, float stemX, float y, float offsetX) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f);
        canvas.drawLine(stemX + offsetX, y, stemX + offsetX + 22f, y + 10f, paint);
        canvas.drawLine(stemX + offsetX + 22f, y + 10f, stemX + offsetX + 10f, y + 22f, paint);
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
                long hitAgeMs = now - target.hitAtMs;
                if (hitAgeMs < SHOT_DURATION_MS) {
                    drawTank(canvas, target.hitX, y, target);
                } else {
                    drawExplosion(canvas, target.hitX, y, hitAgeMs - SHOT_DURATION_MS);
                }
            } else {
                drawTank(canvas, x, y, target);
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

    private void drawTank(Canvas canvas, float x, float y, Target target) {
        float scale = durationScale(target.duration);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(93, 112, 67));
        RectF body = new RectF(x - 30f * scale, y - 16f, x + 30f * scale, y + 16f);
        canvas.drawRect(body, paint);
        paint.setColor(Color.rgb(119, 139, 83));
        RectF turret = new RectF(x - 12f * scale, y - 32f, x + 18f * scale, y - 10f);
        canvas.drawRect(turret, paint);
        paint.setStrokeWidth(7f);
        canvas.drawLine(x - 10f, y - 20f, x - 46f, y - 22f, paint);
        paint.setColor(Color.rgb(32, 37, 31));
        canvas.drawRect(x - 26f * scale, y + 18f, x - 14f * scale, y + 30f, paint);
        canvas.drawRect(x + 14f * scale, y + 18f, x + 26f * scale, y + 30f, paint);
    }

    private void drawExplosion(Canvas canvas, float x, float y, long ageMs) {
        float radius = 14f + Math.min(1f, ageMs / 520f) * 34f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(245, 176, 53));
        canvas.drawRect(x - radius, y - radius, x + radius, y + radius, paint);
        paint.setColor(Color.rgb(238, 88, 54));
        canvas.drawRect(x - radius * 0.55f, y - radius * 0.55f, x + radius * 0.55f, y + radius * 0.55f, paint);
    }

    private void drawShots(Canvas canvas, long now, int top, float laneHeight) {
        paint.setStyle(Paint.Style.FILL);
        for (int i = shots.size() - 1; i >= 0; i--) {
            Shot shot = shots.get(i);
            float progress = (now - shot.startedAtMs) / (float) SHOT_DURATION_MS;
            if (progress >= 1f) {
                shots.remove(i);
                continue;
            }
            float y = top + laneHeight * (shot.lane + 0.5f);
            float x = 92f + (shot.targetX - 92f) * Math.max(0f, progress);
            float scale = shotScale(shot.performedDurationMs);
            paint.setColor(shot.destroys ? Color.rgb(249, 232, 126) : Color.rgb(230, 105, 80));
            canvas.drawRect(x - 9f * scale, y - 5f * scale, x + 13f * scale, y + 5f * scale, paint);
        }
    }

    private void updateMisses(long now) {
        int missCount = 0;
        for (Target target : targets) {
            if (!target.hit && !target.escaped && now > target.dueMs + hitWindowMs()) {
                target.escaped = true;
            }
            if (target.escaped) {
                missCount++;
            }
        }
        misses = missCount;
        notifyGameFinishedIfNeeded();
    }

    private void autoFireDemoTargets(long now) {
        if (!demoAutoFire) {
            return;
        }
        boolean anyPending = false;
        for (Target target : targets) {
            if (target.hit || target.escaped) {
                continue;
            }
            if (now >= target.dueMs) {
                float hitX = tankXFor(target, now);
                target.hit = true;
                target.hitAtMs = now;
                target.hitX = hitX;
                score++;
                lastInputAtMs = now;
                shots.add(new Shot(laneIndex(target.noteName), now, hitX, Long.MAX_VALUE, true));
            } else {
                anyPending = true;
            }
        }
        if (!anyPending) {
            demoAutoFire = false;
        }
    }

    private void updateCurrentTitleNote(long now) {
        notifyCurrentNoteChanged(currentNoteForTitle(now));
    }

    private String currentNoteForTitle(long now) {
        Target best = null;
        long bestDue = Long.MAX_VALUE;
        long graceMs = hitWindowMs();
        for (Target target : targets) {
            if (target.hit || target.escaped) {
                continue;
            }
            if (target.dueMs < now - graceMs) {
                continue;
            }
            if (target.dueMs < bestDue) {
                best = target;
                bestDue = target.dueMs;
            }
        }
        return best == null ? null : best.fullName;
    }

    private void notifyCurrentNoteChanged(String fullName) {
        if (sameString(currentTitleNote, fullName)) {
            return;
        }
        currentTitleNote = fullName;
        if (currentNoteListener != null) {
            currentNoteListener.onCurrentNoteChanged(fullName);
        }
    }

    private float tankXFor(Target target, long now) {
        float startX = getWidth() + 70f;
        float hitX = Math.max(180f, getWidth() * 0.68f);
        float baseX = 92f;
        if (now <= target.dueMs) {
            long travelMs = tankTravelMs();
            float progress = (now - (target.dueMs - travelMs)) / (float) travelMs;
            progress = Math.max(0f, Math.min(1f, progress));
            return startX + (hitX - startX) * progress;
        }
        float progress = (now - target.dueMs) / (float) hitWindowMs();
        progress = Math.max(0f, Math.min(1f, progress));
        return hitX + (baseX - hitX) * progress;
    }

    private int topStripHeight() {
        return Math.max(176, getHeight() / 3);
    }

    long firstNoteDelayMs() {
        return FIRST_NOTE_DELAY_MS;
    }

    List<NoteEvent> levelNotes() {
        List<NoteEvent> notes = new ArrayList<NoteEvent>();
        if (piece == null || piece.notes == null) {
            return notes;
        }
        for (int i = 0; i < piece.notes.size(); i++) {
            notes.add(piece.notes.get(i));
        }
        return notes;
    }

    long noteDurationMs(NoteEvent note) {
        return scaledDurationMs(note == null ? null : note.duration);
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

    private void reschedulePendingTargets(long now, float oldSpeed, float newSpeed) {
        long nextDue = Long.MIN_VALUE;
        for (int i = 0; i < targets.size(); i++) {
            Target target = targets.get(i);
            if (target.hit || target.escaped) {
                continue;
            }
            if (nextDue == Long.MIN_VALUE) {
                long remaining = target.dueMs - now;
                if (remaining > 0L) {
                    target.dueMs = now + Math.max(120L, Math.round(remaining * oldSpeed / newSpeed));
                }
                nextDue = target.dueMs + noteDurationMs(target);
            } else {
                target.dueMs = nextDue;
                nextDue += noteDurationMs(target);
            }
        }
    }

    private void notifyGameFinishedIfNeeded() {
        if (completionNotified || targets.isEmpty()) {
            return;
        }
        for (int i = 0; i < targets.size(); i++) {
            Target target = targets.get(i);
            if (!target.hit && !target.escaped) {
                return;
            }
        }
        completionNotified = true;
        if (gameResultListener != null) {
            gameResultListener.onGameFinished(new GameResult(score, targets.size(), misses, speedMultiplier, durationMode));
        }
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

    private static boolean sameString(String a, String b) {
        if (a == null) {
            return b == null;
        }
        return a.equals(b);
    }

    private static long durationMs(String duration) {
        if ("16th".equals(duration)) return 190L;
        if ("eighth".equals(duration)) return 320L;
        if ("half".equals(duration)) return 960L;
        if ("whole".equals(duration)) return 1800L;
        return 560L;
    }

    private static boolean isWhole(String duration) {
        return "whole".equals(duration);
    }

    private static boolean isHalf(String duration) {
        return "half".equals(duration);
    }

    private static boolean isEighth(String duration) {
        return "eighth".equals(duration);
    }

    private static boolean isSixteenth(String duration) {
        return "16th".equals(duration);
    }

    private static String durationLabel(String duration) {
        if ("whole".equals(duration)) return "1";
        if ("half".equals(duration)) return "1/2";
        if ("eighth".equals(duration)) return "1/8";
        if ("16th".equals(duration)) return "1/16";
        return "1/4";
    }

    private long noteDurationMs(Target target) {
        return scaledDurationMs(target == null ? null : target.duration);
    }

    private long scaledDurationMs(String duration) {
        return scaled(durationMs(duration));
    }

    private static float durationScale(String duration) {
        if ("whole".equals(duration)) return 1.75f;
        if ("half".equals(duration)) return 1.35f;
        if ("eighth".equals(duration)) return 0.82f;
        if ("16th".equals(duration)) return 0.64f;
        return 1f;
    }

    private float shotScale(long performedDurationMs) {
        if (performedDurationMs == Long.MAX_VALUE) {
            return 1.45f;
        }
        long quarter = scaledDurationMs("quarter");
        if (performedDurationMs >= scaledDurationMs("whole")) return 1.75f;
        if (performedDurationMs >= scaledDurationMs("half")) return 1.35f;
        if (performedDurationMs >= quarter) return 1f;
        if (performedDurationMs >= scaledDurationMs("eighth")) return 0.82f;
        return 0.64f;
    }

    private long tankTravelMs() {
        return scaled(TANK_TRAVEL_MS);
    }

    private long hitWindowMs() {
        return Math.max(220L, scaled(HIT_WINDOW_MS));
    }

    private long scaled(long ms) {
        return Math.max(80L, (long) (ms / speedMultiplier));
    }

    private static final class Target {
        final String fullName;
        final String noteName;
        final String duration;
        long dueMs;
        boolean hit;
        boolean escaped;
        long hitAtMs;
        float hitX;

        Target(String fullName, String noteName, String duration, long dueMs) {
            this.fullName = fullName;
            this.noteName = noteName;
            this.duration = duration;
            this.dueMs = dueMs;
        }
    }

    private static final class Shot {
        final int lane;
        final long startedAtMs;
        final float targetX;
        final long performedDurationMs;
        final boolean destroys;

        Shot(int lane, long startedAtMs, float targetX, long performedDurationMs, boolean destroys) {
            this.lane = lane;
            this.startedAtMs = startedAtMs;
            this.targetX = targetX;
            this.performedDurationMs = performedDurationMs;
            this.destroys = destroys;
        }
    }

    public static final class GameResult {
        public final int score;
        public final int total;
        public final int misses;
        public final float speedMultiplier;
        public final boolean durationMode;

        GameResult(int score, int total, int misses, float speedMultiplier, boolean durationMode) {
            this.score = score;
            this.total = total;
            this.misses = misses;
            this.speedMultiplier = speedMultiplier;
            this.durationMode = durationMode;
        }
    }
}
