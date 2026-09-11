package tatar.eljah.recorder;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import tatar.eljah.fluitblox.R;

public class TankPerformanceStore {
    private static final String PREFS = "tank_performance";
    private static final String KEY_ATTEMPTS = "attempts";
    private static final String KEY_TOTAL_BLOCKS = "total_blocks";
    private static final String KEY_BUILDER_BLOCKS = "builder_blocks";
    private static final String KEY_BUILDER_DAY = "builder_day";
    private static final String KEY_BUILDER_DAY_COUNT = "builder_day_count";
    private static final String KEY_PLAYED_PIECES = "played_pieces";
    private static final String KEY_LAST_PLAY_DAY = "last_play_day";
    private static final String KEY_PLAY_STREAK = "play_streak";
    private static final String KEY_FASTEST_PERFECT = "fastest_perfect";
    private static final int MAX_ATTEMPTS = 120;
    private static final int BUILDER_DAILY_LIMIT = 6;

    private final Context context;
    private final SharedPreferences sharedPreferences;

    public TankPerformanceStore(Context context) {
        this.context = context.getApplicationContext();
        sharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public Reward saveAttempt(String pieceId, TankDefenseGameView.GameResult result) {
        if (result == null) {
            return Reward.empty();
        }
        TankAttempt attempt = new TankAttempt();
        attempt.pieceId = pieceId == null ? "" : pieceId;
        attempt.score = result.score;
        attempt.total = result.total;
        attempt.misses = result.misses;
        attempt.speedMultiplier = result.speedMultiplier;
        attempt.durationMode = result.durationMode;
        attempt.savedAt = System.currentTimeMillis();

        List<TankAttempt> attempts = getAttempts();
        Reward reward = calculateReward(attempts, attempt);
        attempts.add(attempt);
        while (attempts.size() > MAX_ATTEMPTS) {
            attempts.remove(0);
        }
        persist(attempts);
        persistReward(attempt.pieceId, reward);
        return reward;
    }

    public int addBuilderAction(String action) {
        String today = dayKey(System.currentTimeMillis());
        String currentDay = sharedPreferences.getString(KEY_BUILDER_DAY, "");
        int count = today.equals(currentDay) ? sharedPreferences.getInt(KEY_BUILDER_DAY_COUNT, 0) : 0;
        if (count >= BUILDER_DAILY_LIMIT) {
            return 0;
        }
        sharedPreferences.edit()
                .putString(KEY_BUILDER_DAY, today)
                .putInt(KEY_BUILDER_DAY_COUNT, count + 1)
                .putInt(KEY_BUILDER_BLOCKS, sharedPreferences.getInt(KEY_BUILDER_BLOCKS, 0) + 1)
                .putInt(KEY_TOTAL_BLOCKS, sharedPreferences.getInt(KEY_TOTAL_BLOCKS, 0) + 1)
                .apply();
        return 1;
    }

    public List<TankAttempt> getAttempts() {
        List<TankAttempt> result = new ArrayList<TankAttempt>();
        String raw = sharedPreferences.getString(KEY_ATTEMPTS, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                TankAttempt attempt = new TankAttempt();
                attempt.pieceId = item.optString("pieceId", "");
                attempt.score = item.optInt("score", 0);
                attempt.total = item.optInt("total", 0);
                attempt.misses = item.optInt("misses", 0);
                attempt.speedMultiplier = (float) item.optDouble("speedMultiplier", 1d);
                attempt.durationMode = item.optBoolean("durationMode", false);
                attempt.savedAt = item.optLong("savedAt", 0L);
                result.add(attempt);
            }
        } catch (JSONException ignored) {
            result.clear();
        }
        return result;
    }

    public String buildSummary() {
        List<TankAttempt> attempts = getAttempts();
        int pitchPerfect = 0;
        int durationPerfect = 0;
        int playedPieces = getPlayedPieceCount();
        int blocks = sharedPreferences.getInt(KEY_TOTAL_BLOCKS, 0);
        int builderBlocks = sharedPreferences.getInt(KEY_BUILDER_BLOCKS, 0);
        int streak = sharedPreferences.getInt(KEY_PLAY_STREAK, 0);
        if (attempts.isEmpty()) {
            return context.getString(
                    R.string.tank_achievements_empty_template,
                    blocks,
                    streak,
                    playedPieces,
                    builderBlocks);
        }
        float bestPitchRatio = 0f;
        float bestDurationRatio = 0f;
        float bestPerfectSpeed = 0f;
        for (int i = 0; i < attempts.size(); i++) {
            TankAttempt attempt = attempts.get(i);
            float ratio = attempt.ratio();
            if (attempt.durationMode) {
                bestDurationRatio = Math.max(bestDurationRatio, ratio);
                if (attempt.isPerfect()) {
                    durationPerfect++;
                    bestPerfectSpeed = Math.max(bestPerfectSpeed, attempt.speedMultiplier);
                }
            } else {
                bestPitchRatio = Math.max(bestPitchRatio, ratio);
                if (attempt.isPerfect()) {
                    pitchPerfect++;
                    bestPerfectSpeed = Math.max(bestPerfectSpeed, attempt.speedMultiplier);
                }
            }
        }
        return context.getString(
                R.string.tank_achievements_summary_template,
                blocks,
                streak,
                playedPieces,
                attempts.size(),
                builderBlocks,
                pitchPerfect,
                durationPerfect,
                bestPitchRatio * 100f,
                bestDurationRatio * 100f,
                bestPerfectSpeed);
    }

    private void persist(List<TankAttempt> attempts) {
        JSONArray array = new JSONArray();
        for (int i = 0; i < attempts.size(); i++) {
            TankAttempt attempt = attempts.get(i);
            JSONObject object = new JSONObject();
            try {
                object.put("pieceId", attempt.pieceId);
                object.put("score", attempt.score);
                object.put("total", attempt.total);
                object.put("misses", attempt.misses);
                object.put("speedMultiplier", attempt.speedMultiplier);
                object.put("durationMode", attempt.durationMode);
                object.put("savedAt", attempt.savedAt);
                array.put(object);
            } catch (JSONException ignored) {
            }
        }
        sharedPreferences.edit().putString(KEY_ATTEMPTS, array.toString()).apply();
    }

    private Reward calculateReward(List<TankAttempt> attempts, TankAttempt attempt) {
        Reward reward = new Reward();
        reward.speedMultiplier = attempt.speedMultiplier;
        reward.blocks = 1;
        reward.lines.add(context.getString(R.string.tank_reward_finished_song));
        String today = dayKey(System.currentTimeMillis());
        if (!today.equals(sharedPreferences.getString(KEY_LAST_PLAY_DAY, ""))) {
            reward.blocks++;
            reward.lines.add(context.getString(R.string.tank_reward_daily_return));
        }
        if (attempt.ratio() >= 0.8f) {
            reward.blocks++;
            reward.lines.add(context.getString(R.string.tank_reward_accuracy));
        }
        if (attempt.isPerfect()) {
            reward.blocks++;
            reward.lines.add(context.getString(R.string.tank_reward_perfect_run));
            if (attempt.durationMode) {
                reward.blocks++;
                reward.lines.add(context.getString(R.string.tank_reward_duration_perfect));
            }
            float fastest = sharedPreferences.getFloat(KEY_FASTEST_PERFECT, 0f);
            if (attempt.speedMultiplier > fastest + 0.001f) {
                reward.blocks++;
                reward.newSpeedRecord = true;
                reward.lines.add(context.getString(R.string.tank_reward_speed_record));
            }
        }
        if (!hasPlayedPiece(attempt.pieceId)) {
            reward.blocks++;
            reward.newSong = true;
            reward.lines.add(context.getString(R.string.tank_reward_new_song));
        }
        return reward;
    }

    private void persistReward(String pieceId, Reward reward) {
        Set<String> played = new HashSet<String>(sharedPreferences.getStringSet(KEY_PLAYED_PIECES, new HashSet<String>()));
        if (pieceId != null && pieceId.length() > 0) {
            played.add(pieceId);
        }
        int streak = updatePlayStreak();
        reward.playStreak = streak;
        SharedPreferences.Editor editor = sharedPreferences.edit()
                .putStringSet(KEY_PLAYED_PIECES, played)
                .putInt(KEY_TOTAL_BLOCKS, sharedPreferences.getInt(KEY_TOTAL_BLOCKS, 0) + reward.blocks);
        if (reward.newSpeedRecord) {
            editor.putFloat(KEY_FASTEST_PERFECT, reward.speedMultiplier);
        }
        editor.apply();
    }

    private int updatePlayStreak() {
        long now = System.currentTimeMillis();
        String today = dayKey(now);
        String lastDay = sharedPreferences.getString(KEY_LAST_PLAY_DAY, "");
        int streak = sharedPreferences.getInt(KEY_PLAY_STREAK, 0);
        if (today.equals(lastDay)) {
            return Math.max(1, streak);
        }
        String yesterday = dayKey(now - 24L * 60L * 60L * 1000L);
        streak = yesterday.equals(lastDay) ? streak + 1 : 1;
        sharedPreferences.edit()
                .putString(KEY_LAST_PLAY_DAY, today)
                .putInt(KEY_PLAY_STREAK, streak)
                .apply();
        return streak;
    }

    private boolean hasPlayedPiece(String pieceId) {
        if (pieceId == null || pieceId.length() == 0) {
            return true;
        }
        return sharedPreferences.getStringSet(KEY_PLAYED_PIECES, new HashSet<String>()).contains(pieceId);
    }

    private int getPlayedPieceCount() {
        return sharedPreferences.getStringSet(KEY_PLAYED_PIECES, new HashSet<String>()).size();
    }

    private static String dayKey(long timeMs) {
        java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyyMMdd", Locale.US);
        return format.format(new java.util.Date(timeMs));
    }

    public static final class TankAttempt {
        public String pieceId;
        public int score;
        public int total;
        public int misses;
        public float speedMultiplier;
        public boolean durationMode;
        public long savedAt;

        public float ratio() {
            return total <= 0 ? 0f : score / (float) total;
        }

        public boolean isPerfect() {
            return total > 0 && score == total && misses == 0;
        }
    }

    public static final class Reward {
        public int blocks;
        public int playStreak;
        public float speedMultiplier;
        public boolean newSong;
        public boolean newSpeedRecord;
        public final List<String> lines = new ArrayList<String>();

        static Reward empty() {
            return new Reward();
        }

        public String reasonText() {
            if (lines.isEmpty()) {
                return "";
            }
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < lines.size(); i++) {
                if (i > 0) {
                    builder.append("\n");
                }
                builder.append("+ ").append(lines.get(i));
            }
            return builder.toString();
        }
    }
}
