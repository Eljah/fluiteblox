package tatar.eljah.recorder;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

import com.recordercoach.app.R;

public class LibraryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_library);

        final List<ScorePiece> pieces = new ScoreLibraryRepository(this).getAllPieces();
        List<String> titles = new ArrayList<String>();
        for (ScorePiece piece : pieces) {
            titles.add(piece.title + " · " + piece.notes.size() + " нот");
        }
        if (titles.isEmpty()) {
            titles.add(getString(R.string.library_empty));
        }

        ListView listView = findViewById(R.id.list_library);
        listView.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, titles));
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (pieces.isEmpty()) {
                    return;
                }
                Intent intent = new Intent(LibraryActivity.this, ScorePlayActivity.class);
                intent.putExtra(ScorePlayActivity.EXTRA_PIECE_ID, pieces.get(position).id);
                startActivity(intent);
            }
        });

        findViewById(R.id.btn_library_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }
}
