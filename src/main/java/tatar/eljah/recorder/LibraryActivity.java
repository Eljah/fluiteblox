package tatar.eljah.recorder;

import android.content.Intent;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AlertDialog;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import tatar.eljah.fluitblox.R;

public class LibraryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppLocaleManager.applySavedLocale(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_library);

        final List<ScorePiece> pieces = new ScoreLibraryRepository(this).getAllPieces();
        List<String> titles = new ArrayList<String>();
        for (ScorePiece piece : pieces) {
            titles.add(getString(R.string.library_item_template, piece.title, piece.notes.size()));
        }
        if (titles.isEmpty()) {
            titles.add(getString(R.string.library_empty));
        }

        ListView listView = findViewById(R.id.list_library);
        listView.setAdapter(new ArrayAdapter<String>(this, R.layout.list_item_blox, titles));
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
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setCustomTitle(buildExportDialogTitle(piece))
                .setAdapter(new ExportOptionAdapter(options), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            Intent intent = new Intent(LibraryActivity.this, TankDefenseActivity.class);
                            intent.putExtra(TankDefenseActivity.EXTRA_PIECE_ID, piece.id);
                            startActivity(intent);
                            return;
                        }
                        exportPiece(piece, which == 1);
                    }
                })
                .create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                ListView list = dialog.getListView();
                if (list != null) {
                    list.setBackgroundColor(Color.rgb(34, 49, 38));
                    list.setDividerHeight(dp(4));
                    list.setCacheColorHint(Color.TRANSPARENT);
                }
            }
        });
        dialog.show();
    }

    private View buildExportDialogTitle(ScorePiece piece) {
        TextView title = new TextView(this);
        title.setText(getString(R.string.library_export_title, piece.title));
        title.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        title.setTextColor(Color.rgb(255, 227, 109));
        title.setTextSize(21f);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(20), dp(14), dp(20), dp(14));
        title.setBackgroundColor(Color.rgb(34, 49, 38));
        return title;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class ExportOptionAdapter extends ArrayAdapter<String> {
        ExportOptionAdapter(String[] options) {
            super(LibraryActivity.this, android.R.layout.simple_list_item_1, options);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView view = (TextView) super.getView(position, convertView, parent);
            view.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
            view.setTextColor(Color.rgb(242, 240, 216));
            view.setTextSize(17f);
            view.setSingleLine(false);
            view.setPadding(dp(18), dp(14), dp(18), dp(14));
            view.setBackgroundColor(position % 2 == 0
                    ? Color.rgb(42, 69, 49)
                    : Color.rgb(35, 58, 47));
            return view;
        }
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
