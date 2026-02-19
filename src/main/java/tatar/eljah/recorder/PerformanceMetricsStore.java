package tatar.eljah.recorder;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class PerformanceMetricsStore {
    private static final String PREFS = "performance_metrics";
    private static final String KEY_ATTEMPTS = "attempts";
    private static final String KEY_STARTED = "started";

    private final SharedPreferences sharedPreferences;

    public PerformanceMetricsStore(Context context) {
        this.sharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void incrementStartedAttempt(String pieceId) {
        if (pieceId == null) {
            return;
        }
        int current = getStartedAttempts(pieceId);
        sharedPreferences.edit().putInt(KEY_STARTED + "_" + pieceId, current + 1).apply();
    }

    public int getStartedAttempts(String pieceId) {
        if (pieceId == null) {
            return 0;
        }
        return sharedPreferences.getInt(KEY_STARTED + "_" + pieceId, 0);
    }

    public void saveCompletedAttempt(String pieceId, PerformanceAttempt attempt) {
        if (pieceId == null || attempt == null) {
            return;
        }
        List<PerformanceAttempt> existing = getAttempts(pieceId);
        attempt.completedAttemptNumber = existing.size() + 1;
        existing.add(attempt);
        persist(pieceId, existing);
    }

    public List<PerformanceAttempt> getAttempts(String pieceId) {
        List<PerformanceAttempt> result = new ArrayList<PerformanceAttempt>();
        if (pieceId == null) {
            return result;
        }
        String raw = sharedPreferences.getString(KEY_ATTEMPTS + "_" + pieceId, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                PerformanceAttempt attempt = new PerformanceAttempt();
                attempt.completedAttemptNumber = item.optInt("completedAttemptNumber", i + 1);
                attempt.hitRatio = (float) item.optDouble("hitRatio", 0d);
                attempt.recoveryRatio = (float) item.optDouble("recoveryRatio", 0d);
                attempt.durationRatio = (float) item.optDouble("durationRatio", 0d);
                attempt.savedAt = item.optLong("savedAt", 0L);
                result.add(attempt);
            }
        } catch (JSONException ignored) {
            result.clear();
        }
        return result;
    }

    private void persist(String pieceId, List<PerformanceAttempt> attempts) {
        JSONArray array = new JSONArray();
        for (PerformanceAttempt attempt : attempts) {
            JSONObject object = new JSONObject();
            try {
                object.put("completedAttemptNumber", attempt.completedAttemptNumber);
                object.put("hitRatio", attempt.hitRatio);
                object.put("recoveryRatio", attempt.recoveryRatio);
                object.put("durationRatio", attempt.durationRatio);
                object.put("savedAt", attempt.savedAt);
                array.put(object);
            } catch (JSONException ignored) {
            }
        }
        sharedPreferences.edit().putString(KEY_ATTEMPTS + "_" + pieceId, array.toString()).apply();
    }

    public static class PerformanceAttempt {
        public int completedAttemptNumber;
        public float hitRatio;
        public float recoveryRatio;
        public float durationRatio;
        public long savedAt;
    }
}
