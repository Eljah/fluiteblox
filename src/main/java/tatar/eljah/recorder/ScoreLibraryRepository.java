package tatar.eljah.recorder;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class ScoreLibraryRepository {
    private static final String PREFS = "recorder_library";
    private static final String KEY_ITEMS = "items";

    private final SharedPreferences sharedPreferences;

    public ScoreLibraryRepository(Context context) {
        sharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void savePiece(ScorePiece piece) {
        List<ScorePiece> all = getAllPieces();
        if (piece.id == null) {
            piece.id = UUID.randomUUID().toString();
        }
        if (piece.createdAt == 0L) {
            piece.createdAt = System.currentTimeMillis();
        }
        all.add(0, piece);
        persist(all);
    }

    public List<ScorePiece> getAllPieces() {
        String raw = sharedPreferences.getString(KEY_ITEMS, "[]");
        List<ScorePiece> result = new ArrayList<ScorePiece>();
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                ScorePiece piece = new ScorePiece();
                piece.id = item.optString("id");
                piece.title = item.optString("title");
                piece.createdAt = item.optLong("createdAt");
                JSONArray notes = item.optJSONArray("notes");
                if (notes != null) {
                    for (int j = 0; j < notes.length(); j++) {
                        JSONObject note = notes.getJSONObject(j);
                        piece.notes.add(new NoteEvent(
                                note.optString("noteName"),
                                note.optInt("octave", 4),
                                note.optString("duration", "quarter"),
                                note.optInt("measure", 1),
                                (float) note.optDouble("x", -1d),
                                (float) note.optDouble("y", -1d)
                        ));
                    }
                }
                result.add(piece);
            }
        } catch (JSONException ignored) {
            return Collections.emptyList();
        }
        return result;
    }

    public ScorePiece findById(String id) {
        List<ScorePiece> all = getAllPieces();
        for (ScorePiece piece : all) {
            if (piece.id != null && piece.id.equals(id)) {
                return piece;
            }
        }
        return null;
    }

    private void persist(List<ScorePiece> items) {
        JSONArray array = new JSONArray();
        for (ScorePiece piece : items) {
            JSONObject obj = new JSONObject();
            JSONArray notesArray = new JSONArray();
            try {
                obj.put("id", piece.id);
                obj.put("title", piece.title);
                obj.put("createdAt", piece.createdAt);
                for (NoteEvent note : piece.notes) {
                    JSONObject noteObj = new JSONObject();
                    noteObj.put("noteName", note.noteName);
                    noteObj.put("octave", note.octave);
                    noteObj.put("duration", note.duration);
                    noteObj.put("measure", note.measure);
                    noteObj.put("x", note.x);
                    noteObj.put("y", note.y);
                    notesArray.put(noteObj);
                }
                obj.put("notes", notesArray);
                array.put(obj);
            } catch (JSONException ignored) {
            }
        }
        sharedPreferences.edit().putString(KEY_ITEMS, array.toString()).apply();
    }
}
