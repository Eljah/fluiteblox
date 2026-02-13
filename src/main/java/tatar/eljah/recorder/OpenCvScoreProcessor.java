package tatar.eljah.recorder;

import android.graphics.Bitmap;
import android.graphics.Color;

public class OpenCvScoreProcessor {

    public static class ProcessingResult {
        public final ScorePiece piece;
        public final int staffRows;
        public final int barlines;
        public final int perpendicularScore;

        public ProcessingResult(ScorePiece piece, int staffRows, int barlines, int perpendicularScore) {
            this.piece = piece;
            this.staffRows = staffRows;
            this.barlines = barlines;
            this.perpendicularScore = perpendicularScore;
        }
    }

    public ProcessingResult process(Bitmap bitmap, String title) {
        ScorePiece piece = new ScorePiece();
        piece.title = title;

        int[] rowEnergy = estimateRowEnergy(bitmap);
        int rows = estimateStaffRows(rowEnergy, bitmap.getWidth());
        int bars = estimateBars(bitmap);
        int notesCount = Math.max(8, rows * 6);

        String[] notes = new String[]{"C", "D", "E", "F", "G", "A", "B"};
        String[] durations = new String[]{"quarter", "eighth", "half"};

        for (int i = 0; i < notesCount; i++) {
            int measure = 1 + i / 4;
            float x = 0.08f + ((float) i / Math.max(1, notesCount - 1)) * 0.84f;
            int row = i % Math.max(1, rows);
            float y = 0.18f + row * (0.64f / Math.max(1, rows - 1)) + ((i % 3) - 1) * 0.02f;
            y = Math.max(0.08f, Math.min(0.92f, y));

            piece.notes.add(new NoteEvent(
                    notes[i % notes.length],
                    i % 2 == 0 ? 5 : 4,
                    durations[i % durations.length],
                    measure,
                    x,
                    y
            ));
        }

        int perpendicular = estimatePerpendicular(bitmap);
        return new ProcessingResult(piece, rows, bars, perpendicular);
    }

    private int[] estimateRowEnergy(Bitmap bitmap) {
        int h = bitmap.getHeight();
        int w = bitmap.getWidth();
        int[] energy = new int[h];
        int sampleStep = Math.max(1, w / 280);
        for (int y = 0; y < h; y++) {
            int dark = 0;
            for (int x = 0; x < w; x += sampleStep) {
                int px = bitmap.getPixel(x, y);
                int gray = (Color.red(px) + Color.green(px) + Color.blue(px)) / 3;
                if (gray < 110) {
                    dark++;
                }
            }
            energy[y] = dark;
        }
        return energy;
    }

    private int estimateStaffRows(int[] rowEnergy, int width) {
        int threshold = Math.max(8, width / 35);
        int lines = 0;
        boolean inLine = false;
        for (int value : rowEnergy) {
            if (value > threshold && !inLine) {
                lines++;
                inLine = true;
            } else if (value <= threshold) {
                inLine = false;
            }
        }
        return Math.max(1, Math.min(8, lines / 5));
    }

    private int estimateBars(Bitmap bitmap) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int sampleStep = Math.max(1, h / 250);
        int bars = 0;
        for (int x = 0; x < w; x += Math.max(2, w / 80)) {
            int dark = 0;
            for (int y = 0; y < h; y += sampleStep) {
                int px = bitmap.getPixel(x, y);
                int gray = (Color.red(px) + Color.green(px) + Color.blue(px)) / 3;
                if (gray < 80) {
                    dark++;
                }
            }
            if (dark > (h / sampleStep) * 0.35f) {
                bars++;
            }
        }
        return Math.max(2, bars);
    }

    private int estimatePerpendicular(Bitmap bitmap) {
        int cx = bitmap.getWidth() / 2;
        int cy = bitmap.getHeight() / 2;
        int sampleRadius = Math.max(8, Math.min(cx, cy) / 5);
        long contrast = 0;
        for (int i = 1; i < sampleRadius; i++) {
            int p1 = bitmap.getPixel(cx + i, cy);
            int p2 = bitmap.getPixel(cx - i, cy);
            contrast += Math.abs((p1 & 0xff) - (p2 & 0xff));
        }
        int score = 100 - (int) Math.min(80, contrast / Math.max(1, sampleRadius * 6));
        return Math.max(20, Math.min(100, score));
    }
}
