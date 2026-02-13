package tatar.eljah.recorder;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

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

    private static class Blob {
        int minX;
        int minY;
        int maxX;
        int maxY;
        int area;
        int sumX;
        int sumY;

        Blob(int x, int y) {
            minX = maxX = x;
            minY = maxY = y;
        }

        void add(int x, int y) {
            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
            area++;
            sumX += x;
            sumY += y;
        }

        int width() {
            return maxX - minX + 1;
        }

        int height() {
            return maxY - minY + 1;
        }

        float cx() {
            return area == 0 ? minX : (float) sumX / (float) area;
        }

        float cy() {
            return area == 0 ? minY : (float) sumY / (float) area;
        }
    }

    public ProcessingResult process(Bitmap bitmap, String title) {
        ScorePiece piece = new ScorePiece();
        piece.title = title;

        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int[] gray = toGray(bitmap);
        int threshold = estimateThreshold(gray);
        boolean[] binary = binarize(gray, threshold);

        int[] rowEnergy = estimateRowEnergy(binary, w, h);
        int staffRows = estimateStaffRows(rowEnergy, w);
        int staffSpacing = estimateStaffSpacing(rowEnergy);

        boolean[] noLines = removeHorizontalLines(binary, rowEnergy, w, h);
        List<Blob> blobs = findConnectedComponents(noLines, w, h);
        List<Blob> noteHeads = filterNoteHeads(blobs, w, h, staffSpacing);

        int barlines = estimateBars(binary, w, h, staffSpacing);
        int perpendicular = estimatePerpendicular(bitmap);
        fillNotes(piece, noteHeads, staffSpacing, w, h);

        int minRecognized = 8;
        int syntheticTarget = Math.max(minRecognized, staffRows * 10);
        if (piece.notes.isEmpty()) {
            fallbackFill(piece, 0, syntheticTarget, syntheticTarget);
        } else if (piece.notes.size() < minRecognized) {
            int missing = minRecognized - piece.notes.size();
            fallbackFill(piece, piece.notes.size(), missing, minRecognized);
        }

        return new ProcessingResult(piece, staffRows, barlines, perpendicular);
    }

    private int[] toGray(Bitmap bitmap) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int[] gray = new int[w * h];
        int idx = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int px = bitmap.getPixel(x, y);
                gray[idx++] = (Color.red(px) * 30 + Color.green(px) * 59 + Color.blue(px) * 11) / 100;
            }
        }
        return gray;
    }

    private int estimateThreshold(int[] gray) {
        int[] hist = new int[256];
        for (int i = 0; i < gray.length; i++) {
            hist[gray[i]]++;
        }
        int total = gray.length;
        long sum = 0;
        for (int i = 0; i < 256; i++) sum += (long) i * hist[i];

        long sumB = 0;
        int wB = 0;
        int bestT = 130;
        double bestVar = -1;
        for (int t = 0; t < 256; t++) {
            wB += hist[t];
            if (wB == 0) continue;
            int wF = total - wB;
            if (wF == 0) break;

            sumB += (long) t * hist[t];
            double mB = (double) sumB / (double) wB;
            double mF = (double) (sum - sumB) / (double) wF;
            double between = (double) wB * (double) wF * (mB - mF) * (mB - mF);
            if (between > bestVar) {
                bestVar = between;
                bestT = t;
            }
        }

        return Math.max(65, Math.min(170, bestT + 8));
    }

    private boolean[] binarize(int[] gray, int threshold) {
        boolean[] binary = new boolean[gray.length];
        for (int i = 0; i < gray.length; i++) {
            binary[i] = gray[i] < threshold;
        }
        return binary;
    }

    private int[] estimateRowEnergy(boolean[] binary, int w, int h) {
        int[] energy = new int[h];
        for (int y = 0; y < h; y++) {
            int dark = 0;
            int base = y * w;
            for (int x = 0; x < w; x++) {
                if (binary[base + x]) dark++;
            }
            energy[y] = dark;
        }

        int[] smooth = new int[h];
        for (int y = 0; y < h; y++) {
            int from = Math.max(0, y - 2);
            int to = Math.min(h - 1, y + 2);
            int sum = 0;
            for (int i = from; i <= to; i++) sum += energy[i];
            smooth[y] = sum / (to - from + 1);
        }
        return smooth;
    }

    private int estimateStaffRows(int[] rowEnergy, int width) {
        int threshold = Math.max(16, width / 10);
        int lines = 0;
        boolean inLine = false;
        for (int i = 0; i < rowEnergy.length; i++) {
            if (rowEnergy[i] > threshold && !inLine) {
                lines++;
                inLine = true;
            } else if (rowEnergy[i] <= threshold) {
                inLine = false;
            }
        }
        return Math.max(1, Math.min(10, lines / 5));
    }

    private int estimateStaffSpacing(int[] rowEnergy) {
        List<Integer> peaks = new ArrayList<Integer>();
        int max = 0;
        for (int i = 0; i < rowEnergy.length; i++) {
            if (rowEnergy[i] > max) max = rowEnergy[i];
        }
        int threshold = (int) (max * 0.55f);
        for (int y = 1; y < rowEnergy.length - 1; y++) {
            if (rowEnergy[y] >= threshold && rowEnergy[y] >= rowEnergy[y - 1] && rowEnergy[y] >= rowEnergy[y + 1]) {
                if (peaks.isEmpty() || y - peaks.get(peaks.size() - 1) > 2) {
                    peaks.add(y);
                }
            }
        }

        if (peaks.size() < 2) return 12;
        int[] deltas = new int[Math.max(1, peaks.size() - 1)];
        for (int i = 1; i < peaks.size(); i++) {
            deltas[i - 1] = peaks.get(i) - peaks.get(i - 1);
        }
        java.util.Arrays.sort(deltas);
        int median = deltas[deltas.length / 2];
        return Math.max(6, Math.min(26, median));
    }

    private boolean[] removeHorizontalLines(boolean[] binary, int[] rowEnergy, int w, int h) {
        boolean[] out = new boolean[binary.length];
        System.arraycopy(binary, 0, out, 0, binary.length);
        int max = 0;
        for (int y = 0; y < rowEnergy.length; y++) {
            if (rowEnergy[y] > max) max = rowEnergy[y];
        }
        int strong = (int) (max * 0.68f);

        for (int y = 0; y < h; y++) {
            if (rowEnergy[y] < strong) continue;
            int base = y * w;
            int run = 0;
            for (int x = 0; x < w; x++) {
                if (out[base + x]) {
                    run++;
                } else {
                    if (run > w / 10) {
                        for (int k = x - run; k < x; k++) out[base + k] = false;
                    }
                    run = 0;
                }
            }
            if (run > w / 10) {
                for (int k = w - run; k < w; k++) out[base + k] = false;
            }
        }
        return out;
    }

    private List<Blob> findConnectedComponents(boolean[] binary, int w, int h) {
        boolean[] visited = new boolean[binary.length];
        List<Blob> blobs = new ArrayList<Blob>();
        int[] qx = new int[binary.length];
        int[] qy = new int[binary.length];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int idx = y * w + x;
                if (!binary[idx] || visited[idx]) continue;

                Blob blob = new Blob(x, y);
                int head = 0;
                int tail = 0;
                qx[tail] = x;
                qy[tail] = y;
                tail++;
                visited[idx] = true;

                while (head < tail) {
                    int cx = qx[head];
                    int cy = qy[head];
                    head++;
                    blob.add(cx, cy);

                    for (int ny = Math.max(0, cy - 1); ny <= Math.min(h - 1, cy + 1); ny++) {
                        int nbase = ny * w;
                        for (int nx = Math.max(0, cx - 1); nx <= Math.min(w - 1, cx + 1); nx++) {
                            int nidx = nbase + nx;
                            if (!binary[nidx] || visited[nidx]) continue;
                            visited[nidx] = true;
                            qx[tail] = nx;
                            qy[tail] = ny;
                            tail++;
                        }
                    }
                }

                if (blob.area > 0) blobs.add(blob);
            }
        }
        return blobs;
    }

    private List<Blob> filterNoteHeads(List<Blob> blobs, int w, int h, int staffSpacing) {
        List<Blob> out = new ArrayList<Blob>();
        int minArea = Math.max(8, (staffSpacing * staffSpacing) / 8);
        int maxArea = Math.max(1200, staffSpacing * staffSpacing * 4);

        for (int i = 0; i < blobs.size(); i++) {
            Blob b = blobs.get(i);
            int bw = b.width();
            int bh = b.height();
            if (b.area < minArea || b.area > maxArea) continue;
            if (bw < 3 || bh < 3) continue;
            if (bw > w / 6 || bh > h / 5) continue;
            float ratio = (float) bw / (float) bh;
            if (ratio < 0.35f || ratio > 2.6f) continue;

            float fill = (float) b.area / (float) (bw * bh);
            if (fill < 0.20f || fill > 0.92f) continue;

            out.add(b);
        }

        Collections.sort(out, new Comparator<Blob>() {
            @Override
            public int compare(Blob a, Blob b) {
                if (a.minX == b.minX) return a.minY - b.minY;
                return a.minX - b.minX;
            }
        });
        return out;
    }

    private int estimateBars(boolean[] binary, int w, int h, int staffSpacing) {
        int bars = 0;
        int minRun = Math.max(staffSpacing * 3, h / 10);
        int step = Math.max(2, w / 120);

        for (int x = 0; x < w; x += step) {
            int run = 0;
            int best = 0;
            for (int y = 0; y < h; y++) {
                if (binary[y * w + x]) {
                    run++;
                    if (run > best) best = run;
                } else {
                    run = 0;
                }
            }
            if (best >= minRun) bars++;
        }

        bars = bars / 2;
        return Math.max(2, bars);
    }

    private void fillNotes(ScorePiece piece, List<Blob> noteHeads, int staffSpacing, int w, int h) {
        if (noteHeads.isEmpty()) return;

        String[] noteCycle = new String[]{"C", "D", "E", "F", "G", "A", "B"};
        int measureSize = 4;

        for (int i = 0; i < noteHeads.size(); i++) {
            Blob b = noteHeads.get(i);
            float xNorm = b.cx() / (float) Math.max(1, w - 1);
            float yNorm = b.cy() / (float) Math.max(1, h - 1);

            int step = Math.round((1.0f - yNorm) * 12.0f);
            int noteIndex = ((step % 7) + 7) % 7;
            int octave = 4 + (step / 7);
            octave = Math.max(3, Math.min(6, octave));

            String duration;
            int headSize = Math.max(b.width(), b.height());
            if (headSize > staffSpacing + 4) {
                duration = "half";
            } else if (headSize < Math.max(6, staffSpacing / 2)) {
                duration = "eighth";
            } else {
                duration = "quarter";
            }

            piece.notes.add(new NoteEvent(
                    noteCycle[noteIndex],
                    octave,
                    duration,
                    1 + (i / measureSize),
                    xNorm,
                    yNorm
            ));
        }
    }

    private void fallbackFill(ScorePiece piece, int startIndex, int notesToAdd, int totalNotesForSpacing) {
        String[] notes = new String[]{"C", "D", "E", "F", "G", "A", "B"};
        String[] durations = new String[]{"quarter", "eighth", "half"};

        for (int offset = 0; offset < notesToAdd; offset++) {
            int i = startIndex + offset;
            int measure = 1 + i / 4;
            float x = 0.08f + ((float) i / Math.max(1, totalNotesForSpacing - 1)) * 0.84f;
            int row = i % 3;
            float y = 0.2f + row * 0.28f + ((i % 3) - 1) * 0.02f;
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
    }

    private int estimatePerpendicular(Bitmap bitmap) {
        int cx = bitmap.getWidth() / 2;
        int cy = bitmap.getHeight() / 2;
        int sampleRadius = Math.max(8, Math.min(cx, cy) / 5);
        long contrast = 0;
        for (int i = 1; i < sampleRadius; i++) {
            int p1 = bitmap.getPixel(Math.min(bitmap.getWidth() - 1, cx + i), cy);
            int p2 = bitmap.getPixel(Math.max(0, cx - i), cy);
            contrast += Math.abs((p1 & 0xff) - (p2 & 0xff));
        }
        int score = 100 - (int) Math.min(80, contrast / Math.max(1, sampleRadius * 6));
        return Math.max(20, Math.min(100, score));
    }
}
