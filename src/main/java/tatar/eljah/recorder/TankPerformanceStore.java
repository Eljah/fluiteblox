package tatar.eljah.recorder;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TankPerformanceStore {
    private static final String PREFS = "tank_performance";
    private static final String KEY_ATTEMPTS = "attempts";
    private static final int MAX_ATTEMPTS = 120;

    private final SharedPreferences sharedPreferences;

    public TankPerformanceStore(Context context) {
        sharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void saveAttempt(String pieceId, TankDefenseGameView.GameResult result) {
        if (result == null) {
            return;
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
        attempts.add(attempt);
        while (attempts.size() > MAX_ATTEMPTS) {
            attempts.remove(0);
        }
        persist(attempts);
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
        if (attempts.isEmpty()) {
            return "Tankdrome achievements: no finished real games yet.";
        }
        int pitchPerfect = 0;
        int durationPerfect = 0;
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
        return String.format(Locale.US,
                "Tankdrome achievements:\nAttempts: %d\nPitch perfect: %d\nDuration perfect: %d\nBest pitch: %.1f%%\nBest duration: %.1f%%\nFastest perfect: %.2fx",
                attempts.size(),
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
}
