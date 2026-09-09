package tatar.eljah.recorder;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import tatar.eljah.fluitblox.R;

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

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (pieces.isEmpty()) {
                    return true;
                }
                showExportDialog(pieces.get(position));
                return true;
            }
        });

        findViewById(R.id.btn_library_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    private void showExportDialog(final ScorePiece piece) {
        String[] options = new String[]{
                getString(R.string.library_play_tanks),
                getString(R.string.library_export_musicxml),
                getString(R.string.library_export_midi)
        };
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.library_export_title, piece.title))
                .setItems(options, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        if (which == 0) {
                            Intent intent = new Intent(LibraryActivity.this, TankDefenseActivity.class);
                            intent.putExtra(TankDefenseActivity.EXTRA_PIECE_ID, piece.id);
                            startActivity(intent);
                            return;
                        }
                        exportPiece(piece, which == 1);
                    }
                })
                .show();
    }

    private void exportPiece(ScorePiece piece, boolean xml) {
        try {
            java.io.File file = xml
                    ? ScoreExportUtil.exportMusicXml(this, piece)
                    : ScoreExportUtil.exportMidi(this, piece);
            Toast.makeText(this, getString(R.string.library_export_success, file.getAbsolutePath()), Toast.LENGTH_LONG).show();
        } catch (Exception error) {
            Toast.makeText(this, getString(R.string.library_export_error), Toast.LENGTH_LONG).show();
        }
    }
}
