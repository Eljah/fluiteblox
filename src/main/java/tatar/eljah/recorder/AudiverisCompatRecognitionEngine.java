package tatar.eljah.recorder;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Audiveris-compatible OMR pass implemented for Android runtime.
 *
 * This class ports core ideas used in Audiveris pipelines:
 * - grayscale + Otsu binarization
 * - horizontal projection based staff-line detection
 * - connected-component notehead candidate extraction
 *
 * If this pass yields no notes/staves, we fallback to OpenCV engine.
 */
class AudiverisCompatRecognitionEngine implements ScoreRecognitionEngine {
    private final ScoreRecognitionEngine fallback;

    AudiverisCompatRecognitionEngine(ScoreRecognitionEngine fallback) {
        this.fallback = fallback;
    }

    @Override
    public OpenCvScoreProcessor.ProcessingResult recognize(Bitmap bitmap,
                                                           String title,
                                                           OpenCvScoreProcessor.ProcessingOptions options) {
        if (bitmap == null) {
            return fallback.recognize(bitmap, title, options);
        }

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] gray = new int[width * height];
        toGrayscale(bitmap, gray);
        int threshold = otsuThreshold(gray);
        boolean[] black = new boolean[gray.length];
        for (int i = 0; i < gray.length; i++) {
            black[i] = gray[i] <= threshold;
        }

        List<Integer> linePeaks = detectHorizontalPeaks(black, width, height);
        List<StaffModel> staves = buildStaffModels(linePeaks, width, height);
        if (staves.isEmpty()) {
            OpenCvScoreProcessor.ProcessingResult fb = fallback.recognize(bitmap, title, options);
            return withMode(fb, "audiveris-compat/fallback-no-staff");
        }

        removeStaffLines(black, width, height, staves);
        List<NoteEvent> notes = detectNoteheads(black, width, height, staves);
        if (notes.isEmpty()) {
            OpenCvScoreProcessor.ProcessingResult fb = fallback.recognize(bitmap, title, options);
            return withMode(fb, "audiveris-compat/fallback-no-notes");
        }

        Collections.sort(notes, new Comparator<NoteEvent>() {
            @Override
            public int compare(NoteEvent left, NoteEvent right) {
                return Float.compare(left.x, right.x);
            }
        });

        ScorePiece piece = new ScorePiece();
        piece.id = String.valueOf(System.currentTimeMillis());
        piece.title = title;
        piece.createdAt = System.currentTimeMillis();
        piece.notes = notes;

        Bitmap overlay = drawOverlay(bitmap, staves, notes);
        List<OpenCvScoreProcessor.StaffCorridor> corridors = new ArrayList<OpenCvScoreProcessor.StaffCorridor>();
        for (StaffModel staff : staves) {
            corridors.add(new OpenCvScoreProcessor.StaffCorridor(0, staff.top - staff.spacing, width - 1,
                    staff.bottom + staff.spacing));
        }

        return new OpenCvScoreProcessor.ProcessingResult(
                piece,
                staves.size(),
                0,
                staves.size() * 10,
                overlay,
                corridors,
                "audiveris-compat/android" + AudiverisDependencyBridge.runtimeFlavorSuffix(),
                false,
                null,
                null);
    }

    private OpenCvScoreProcessor.ProcessingResult withMode(OpenCvScoreProcessor.ProcessingResult base, String mode) {
        return new OpenCvScoreProcessor.ProcessingResult(
                base.piece,
                base.staffRows,
                base.barlines,
                base.perpendicularScore,
                base.debugOverlay,
                base.staffCorridors,
                mode + "/" + base.processingMode,
                base.openCvUsed,
                base.openCvStackTrace,
                base.noteDiagnostics);
    }

    private void toGrayscale(Bitmap bitmap, int[] gray) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int idx = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int c = bitmap.getPixel(x, y);
                int r = (c >> 16) & 0xFF;
                int g = (c >> 8) & 0xFF;
                int b = c & 0xFF;
                gray[idx++] = (r * 30 + g * 59 + b * 11) / 100;
            }
        }
    }

    private int otsuThreshold(int[] gray) {
        int[] hist = new int[256];
        for (int v : gray) hist[v]++;
        int total = gray.length;
        long sum = 0;
        for (int i = 0; i < 256; i++) sum += (long) i * hist[i];

        long sumB = 0;
        int wB = 0;
        int wF;
        double maxVar = -1.0;
        int threshold = 128;
        for (int t = 0; t < 256; t++) {
            wB += hist[t];
            if (wB == 0) continue;
            wF = total - wB;
            if (wF == 0) break;
            sumB += (long) t * hist[t];
            double mB = sumB / (double) wB;
            double mF = (sum - sumB) / (double) wF;
            double between = (double) wB * (double) wF * (mB - mF) * (mB - mF);
            if (between > maxVar) {
                maxVar = between;
                threshold = t;
            }
        }
        return threshold;
    }

    private List<Integer> detectHorizontalPeaks(boolean[] black, int width, int height) {
        int[] rowInk = new int[height];
        for (int y = 0; y < height; y++) {
            int count = 0;
            int rowOffset = y * width;
            for (int x = 0; x < width; x++) {
                if (black[rowOffset + x]) count++;
            }
            rowInk[y] = count;
        }

        int mean = 0;
        for (int v : rowInk) mean += v;
        mean = mean / Math.max(1, height);
        int minPeak = Math.max(8, (int) (mean * 1.45f));

        List<Integer> peaks = new ArrayList<Integer>();
        int minGap = Math.max(2, height / 180);
        for (int y = 1; y < height - 1; y++) {
            if (rowInk[y] < minPeak) continue;
            if (rowInk[y] >= rowInk[y - 1] && rowInk[y] >= rowInk[y + 1]) {
                if (peaks.isEmpty() || y - peaks.get(peaks.size() - 1) >= minGap) {
                    peaks.add(y);
                } else if (rowInk[y] > rowInk[peaks.get(peaks.size() - 1)]) {
                    peaks.set(peaks.size() - 1, y);
                }
            }
        }
        return peaks;
    }

    private List<StaffModel> buildStaffModels(List<Integer> peaks, int width, int height) {
        if (peaks.size() < 5) return new ArrayList<StaffModel>();
        List<StaffModel> result = new ArrayList<StaffModel>();
        for (int i = 0; i + 4 < peaks.size(); i++) {
            int y0 = peaks.get(i);
            int y4 = peaks.get(i + 4);
            float spacing = (y4 - y0) / 4f;
            if (spacing < 4 || spacing > height / 6f) continue;
            boolean regular = true;
            for (int k = 0; k < 4; k++) {
                float d = peaks.get(i + k + 1) - peaks.get(i + k);
                if (Math.abs(d - spacing) > Math.max(2f, spacing * 0.45f)) {
                    regular = false;
                    break;
                }
            }
            if (!regular) continue;
            StaffModel staff = new StaffModel();
            staff.top = y0;
            staff.bottom = y4;
            staff.spacing = spacing;
            staff.center = (y0 + y4) * 0.5f;
            result.add(staff);
            i += 4;
        }
        return result;
    }

    private void removeStaffLines(boolean[] black, int width, int height, List<StaffModel> staves) {
        for (StaffModel staff : staves) {
            for (int i = 0; i < 5; i++) {
                int y = Math.round(staff.top + i * staff.spacing);
                for (int dy = -1; dy <= 1; dy++) {
                    int yy = y + dy;
                    if (yy < 0 || yy >= height) continue;
                    int row = yy * width;
                    for (int x = 0; x < width; x++) {
                        black[row + x] = false;
                    }
                }
            }
        }
    }

    private List<NoteEvent> detectNoteheads(boolean[] black, int width, int height, List<StaffModel> staves) {
        boolean[] visited = new boolean[black.length];
        ArrayDeque<Integer> queue = new ArrayDeque<Integer>();
        List<NoteEvent> notes = new ArrayList<NoteEvent>();

        float avgSpacing = 0f;
        for (StaffModel staff : staves) avgSpacing += staff.spacing;
        avgSpacing = avgSpacing / staves.size();
        int minArea = Math.max(8, (int) (avgSpacing * avgSpacing * 0.18f));
        int maxArea = Math.max(minArea + 4, (int) (avgSpacing * avgSpacing * 2.4f));

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int start = y * width + x;
                if (!black[start] || visited[start]) continue;

                int minX = x;
                int maxX = x;
                int minY = y;
                int maxY = y;
                int area = 0;
                int sumX = 0;
                int sumY = 0;

                visited[start] = true;
                queue.clear();
                queue.add(start);
                while (!queue.isEmpty()) {
                    int p = queue.removeFirst();
                    int px = p % width;
                    int py = p / width;
                    area++;
                    sumX += px;
                    sumY += py;
                    if (px < minX) minX = px;
                    if (px > maxX) maxX = px;
                    if (py < minY) minY = py;
                    if (py > maxY) maxY = py;

                    for (int ny = py - 1; ny <= py + 1; ny++) {
                        if (ny < 0 || ny >= height) continue;
                        int row = ny * width;
                        for (int nx = px - 1; nx <= px + 1; nx++) {
                            if (nx < 0 || nx >= width) continue;
                            int np = row + nx;
                            if (black[np] && !visited[np]) {
                                visited[np] = true;
                                queue.add(np);
                            }
                        }
                    }
                }

                if (area < minArea || area > maxArea) continue;
                int bw = maxX - minX + 1;
                int bh = maxY - minY + 1;
                if (bw < 2 || bh < 2) continue;
                float ratio = bw > bh ? bw / (float) bh : bh / (float) bw;
                if (ratio > 2.6f) continue;

                float cx = sumX / (float) area;
                float cy = sumY / (float) area;
                StaffModel staff = nearestStaff(staves, cy);
                if (staff == null) continue;

                NoteEvent note = makeNoteFromStaffPosition(cx, cy, staff, notes.size() / 16 + 1);
                notes.add(note);
            }
        }
        return notes;
    }

    private StaffModel nearestStaff(List<StaffModel> staves, float y) {
        StaffModel best = null;
        float bestDist = Float.MAX_VALUE;
        for (StaffModel staff : staves) {
            float d = Math.abs(y - staff.center);
            if (d < bestDist) {
                bestDist = d;
                best = staff;
            }
        }
        return best;
    }

    private NoteEvent makeNoteFromStaffPosition(float x, float y, StaffModel staff, int measure) {
        // Treble clef reference: bottom line = E4, each half-space/line step changes diatonic step by 1.
        float bottomLine = staff.bottom;
        float halfStepPx = staff.spacing * 0.5f;
        int diatonicOffset = Math.round((bottomLine - y) / halfStepPx);

        // Sequence around E4 (bottom line): E F G A B C D ...
        String[] steps = {"C", "D", "E", "F", "G", "A", "B"};
        int baseIndex = 2; // E
        int absolute = baseIndex + diatonicOffset;
        int octave = 4;
        while (absolute < 0) {
            absolute += 7;
            octave--;
        }
        while (absolute >= 7) {
            absolute -= 7;
            octave++;
        }
        String step = steps[absolute];
        return new NoteEvent(step, octave, "quarter", measure, x, y);
    }

    private Bitmap drawOverlay(Bitmap source, List<StaffModel> staves, List<NoteEvent> notes) {
        Bitmap out = source.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(out);

        Paint staffPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        staffPaint.setColor(Color.argb(180, 0, 255, 0));
        staffPaint.setStrokeWidth(2f);

        Paint notePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        notePaint.setColor(Color.argb(220, 255, 0, 0));
        notePaint.setStyle(Paint.Style.STROKE);
        notePaint.setStrokeWidth(2f);

        for (StaffModel staff : staves) {
            for (int i = 0; i < 5; i++) {
                float y = staff.top + i * staff.spacing;
                canvas.drawLine(0, y, out.getWidth(), y, staffPaint);
            }
        }

        float r = Math.max(4f, (staves.isEmpty() ? 8f : staves.get(0).spacing * 0.45f));
        for (NoteEvent note : notes) {
            canvas.drawCircle(note.x, note.y, r, notePaint);
        }
        return out;
    }

    private static class StaffModel {
        float top;
        float bottom;
        float spacing;
        float center;
    }
}
