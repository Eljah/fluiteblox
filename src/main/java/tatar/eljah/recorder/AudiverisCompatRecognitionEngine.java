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
    private static final int MIN_DIRECT_NOTES = 4;
    private static final int MAX_DIRECT_NOTES = 96;
    private static final int MAX_NOTES_PER_STAFF = 24;
    private static final int MIN_FLUTE_OCTAVE = 3;
    private static final int MAX_FLUTE_OCTAVE = 6;
    private static final float STAFF_CORRIDOR_HALF_HEIGHT = 3.2f;
    private static final float NOTE_MIN_SIZE_FACTOR = 0.35f;
    private static final float NOTE_MAX_SIZE_FACTOR = 1.85f;
    private static final float NOTE_MAX_ASPECT_RATIO = 1.9f;
    private static final float NOTE_MIN_FILL = 0.18f;
    private static final float NOTE_MAX_FILL = 0.88f;

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
        int[] argb = new int[width * height];
        bitmap.getPixels(argb, 0, width, 0, 0, width, height);
        return recognizeArgb(width, height, argb, title, options, bitmap);
    }

    OpenCvScoreProcessor.ProcessingResult recognizeArgbForTest(int width,
                                                               int height,
                                                               int[] argb,
                                                               String title,
                                                               OpenCvScoreProcessor.ProcessingOptions options) {
        return recognizeArgb(width, height, argb, title, options, null);
    }

    List<NoteEvent> recognizeDirectNotesForTest(int width, int height, int[] argb) {
        DirectRecognition direct = recognizeDirectForTest(width, height, argb);
        return direct.notes;
    }

    DirectRecognition recognizeDirectForTest(int width, int height, int[] argb) {
        int[] gray = new int[width * height];
        toGrayscale(argb, gray);
        int threshold = otsuThreshold(gray);
        boolean[] black = new boolean[gray.length];
        for (int i = 0; i < gray.length; i++) {
            black[i] = gray[i] <= threshold;
        }

        List<Integer> linePeaks = detectHorizontalPeaks(black, width, height);
        List<StaffModel> staves = buildStaffModels(linePeaks, width, height, black);
        if (staves.isEmpty()) {
            return new DirectRecognition(new ArrayList<NoteEvent>(), staves, linePeaks);
        }

        removeStaffLines(black, width, height, staves);
        List<NoteEvent> notes = detectNoteheads(black, width, height, staves);
        notes.addAll(detectNoteheadsByWindows(black, width, height, staves));
        notes = remeasureNotes(dedupeAndOrderNotes(notes, staves));
        return new DirectRecognition(notes, staves, linePeaks);
    }

    private OpenCvScoreProcessor.ProcessingResult recognizeArgb(int width,
                                                                int height,
                                                                int[] argb,
                                                                String title,
                                                                OpenCvScoreProcessor.ProcessingOptions options,
                                                                Bitmap overlaySource) {
        int[] gray = new int[width * height];
        toGrayscale(argb, gray);
        int threshold = otsuThreshold(gray);
        boolean[] black = new boolean[gray.length];
        for (int i = 0; i < gray.length; i++) {
            black[i] = gray[i] <= threshold;
        }

        List<Integer> linePeaks = detectHorizontalPeaks(black, width, height);
        List<StaffModel> staves = buildStaffModels(linePeaks, width, height, black);
        if (staves.isEmpty()) {
            OpenCvScoreProcessor.ProcessingResult fb = fallback.recognize(overlaySource, title, options);
            return withMode(fb, "audiveris-compat/fallback-no-staff");
        }

        removeStaffLines(black, width, height, staves);
        List<NoteEvent> notes = detectNoteheads(black, width, height, staves);
        notes.addAll(detectNoteheadsByWindows(black, width, height, staves));
        if (notes.isEmpty()) {
            OpenCvScoreProcessor.ProcessingResult fb = fallback.recognize(overlaySource, title, options);
            return withMode(fb, "audiveris-compat/fallback-no-notes");
        }

        notes = dedupeAndOrderNotes(notes, staves);
        notes = remeasureNotes(notes);
        if (!isPlausible(notes, staves)) {
            OpenCvScoreProcessor.ProcessingResult fb = fallback.recognize(overlaySource, title, options);
            return withMode(fb, "audiveris-compat/fallback-low-confidence");
        }

        ScorePiece piece = new ScorePiece();
        piece.id = String.valueOf(System.currentTimeMillis());
        piece.title = title;
        piece.createdAt = System.currentTimeMillis();
        piece.notes = notes;

        Bitmap overlay = overlaySource == null ? null : drawOverlay(overlaySource, staves, notes);
        List<OpenCvScoreProcessor.StaffCorridor> corridors = new ArrayList<OpenCvScoreProcessor.StaffCorridor>();
        for (StaffModel staff : staves) {
            corridors.add(new OpenCvScoreProcessor.StaffCorridor(staff.left, staff.top - staff.spacing, staff.right,
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

    private void toGrayscale(int[] argb, int[] gray) {
        for (int i = 0; i < argb.length; i++) {
            int c = argb[i];
            int r = (c >> 16) & 0xFF;
            int g = (c >> 8) & 0xFF;
            int b = c & 0xFF;
            gray[i] = (r * 30 + g * 59 + b * 11) / 100;
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

    private List<StaffModel> buildStaffModels(List<Integer> peaks, int width, int height, boolean[] black) {
        if (peaks.size() < 5) return new ArrayList<StaffModel>();
        List<StaffCandidate> candidates = new ArrayList<StaffCandidate>();
        int maxSpacing = Math.max(8, Math.min(32, height / 18));
        for (int i = 0; i < peaks.size(); i++) {
            int y0 = peaks.get(i);
            for (int spacing = 10; spacing <= maxSpacing; spacing++) {
                float tolerance = Math.max(2f, spacing * 0.28f);
                float error = 0f;
                boolean matched = true;
                for (int k = 0; k < 5; k++) {
                    int expected = y0 + k * spacing;
                    int nearest = nearestPeak(peaks, expected, tolerance);
                    if (nearest < 0) {
                        matched = false;
                        break;
                    }
                    error += Math.abs(nearest - expected);
                }
                if (!matched) continue;

                StaffModel staff = new StaffModel();
                staff.top = y0;
                staff.bottom = y0 + spacing * 4f;
                staff.spacing = spacing;
                staff.center = (staff.top + staff.bottom) * 0.5f;
                measureStaffSpan(staff, black, width, height);
                float span = staff.right - staff.left;
                if (span < width * 0.35f) {
                    continue;
                }
                candidates.add(new StaffCandidate(staff, span - error * 12f));
            }
        }

        Collections.sort(candidates, new Comparator<StaffCandidate>() {
            @Override
            public int compare(StaffCandidate left, StaffCandidate right) {
                return Float.compare(right.score, left.score);
            }
        });

        List<StaffModel> result = new ArrayList<StaffModel>();
        for (StaffCandidate candidate : candidates) {
            if (overlapsExistingStaff(candidate.staff, result)) {
                continue;
            }
            result.add(candidate.staff);
        }

        Collections.sort(result, new Comparator<StaffModel>() {
            @Override
            public int compare(StaffModel left, StaffModel right) {
                return Float.compare(left.top, right.top);
            }
        });
        return result;
    }

    private int nearestPeak(List<Integer> peaks, int y, float tolerance) {
        int best = -1;
        float bestDist = Float.MAX_VALUE;
        for (int peak : peaks) {
            float d = Math.abs(peak - y);
            if (d <= tolerance && d < bestDist) {
                best = peak;
                bestDist = d;
            }
        }
        return best;
    }

    private boolean overlapsExistingStaff(StaffModel candidate, List<StaffModel> existing) {
        for (StaffModel staff : existing) {
            float minSpacing = Math.min(candidate.spacing, staff.spacing);
            if (Math.abs(candidate.center - staff.center) < minSpacing * 4.5f) {
                return true;
            }
        }
        return false;
    }

    private void measureStaffSpan(StaffModel staff, boolean[] black, int width, int height) {
        int[] hits = new int[width];
        for (int x = 0; x < width; x++) {
            int score = 0;
            for (int i = 0; i < 5; i++) {
                int y = Math.round(staff.top + i * staff.spacing);
                if (hasBlackNear(black, width, height, x, y, 1)) {
                    score++;
                }
            }
            hits[x] = score;
        }

        int bestStart = 0;
        int bestEnd = width - 1;
        int bestLen = 0;
        int start = -1;
        int gap = 0;
        int maxGap = Math.max(30, width / 12);
        for (int x = 0; x < width; x++) {
            if (hits[x] >= 3) {
                if (start < 0) start = x;
                gap = 0;
            } else if (start >= 0) {
                gap++;
                if (gap > maxGap) {
                    int end = x - gap;
                    if (end - start > bestLen) {
                        bestStart = start;
                        bestEnd = end;
                        bestLen = end - start;
                    }
                    start = -1;
                    gap = 0;
                }
            }
        }
        if (start >= 0) {
            int end = width - 1 - gap;
            if (end - start > bestLen) {
                bestStart = start;
                bestEnd = end;
            }
        }

        int pad = Math.max(4, Math.round(staff.spacing));
        staff.left = Math.max(0, bestStart - pad);
        staff.right = Math.min(width - 1, bestEnd + pad);
    }

    private boolean hasBlackNear(boolean[] black, int width, int height, int x, int y, int radius) {
        for (int dy = -radius; dy <= radius; dy++) {
            int yy = y + dy;
            if (yy < 0 || yy >= height) continue;
            if (black[yy * width + x]) {
                return true;
            }
        }
        return false;
    }

    private void removeStaffLines(boolean[] black, int width, int height, List<StaffModel> staves) {
        for (StaffModel staff : staves) {
            for (int i = 0; i < 5; i++) {
                int y = Math.round(staff.top + i * staff.spacing);
                for (int dy = -1; dy <= 1; dy++) {
                    int yy = y + dy;
                    if (yy < 0 || yy >= height) continue;
                    int row = yy * width;
                    for (int x = Math.round(staff.left); x <= Math.round(staff.right); x++) {
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
        int minArea = Math.max(8, (int) (avgSpacing * avgSpacing * 0.12f));
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

                int bw = maxX - minX + 1;
                int bh = maxY - minY + 1;

                float cx = sumX / (float) area;
                float cy = sumY / (float) area;
                StaffModel staff = nearestStaff(staves, cy);
                if (staff == null) continue;
                if (!isInsideStaffCorridor(staff, cy)) continue;
                if (cx < staff.left - staff.spacing || cx > staff.right + staff.spacing) continue;

                int staffMinArea = Math.max(minArea, (int) (staff.spacing * staff.spacing * 0.20f));
                int staffMaxArea = Math.max(staffMinArea + 4, (int) (staff.spacing * staff.spacing * 2.1f));
                if (area < staffMinArea || area > staffMaxArea) continue;

                float minSize = Math.max(2f, staff.spacing * NOTE_MIN_SIZE_FACTOR);
                float maxSize = Math.max(minSize + 1f, staff.spacing * NOTE_MAX_SIZE_FACTOR);
                if (bw < minSize || bh < minSize || bw > maxSize || bh > maxSize) continue;

                float ratio = bw > bh ? bw / (float) bh : bh / (float) bw;
                if (ratio > NOTE_MAX_ASPECT_RATIO) continue;

                float fill = area / (float) (bw * bh);
                if (fill < NOTE_MIN_FILL || fill > NOTE_MAX_FILL) continue;

                NoteEvent note = makeNoteFromStaffPosition(cx, cy, staff, notes.size() / 16 + 1);
                if (!isSupportedPitch(note)) continue;
                notes.add(note);
            }
        }
        return notes;
    }

    private List<NoteEvent> detectNoteheadsByWindows(boolean[] black, int width, int height, List<StaffModel> staves) {
        List<NoteEvent> out = new ArrayList<NoteEvent>();
        for (StaffModel staff : staves) {
            int left = Math.max(0, Math.round(staff.left + staff.spacing));
            int right = Math.min(width - 1, Math.round(staff.right - staff.spacing));
            if (right <= left) continue;

            float threshold = Math.max(5f, staff.spacing * staff.spacing * 0.12f);
            WindowCandidate active = null;
            int lastHitX = -1;
            int maxGap = Math.max(2, Math.round(staff.spacing * 0.35f));
            for (int x = left; x <= right; x += 2) {
                WindowCandidate best = bestWindowCandidate(black, width, height, staff, x);
                if (best != null && best.score >= threshold) {
                    if (active == null || (lastHitX >= 0 && x - lastHitX > maxGap)) {
                        if (active != null) {
                            out.add(makeNoteFromStaffPosition(active.x, active.y, staff, 1));
                        }
                        active = best;
                    } else if (best.score > active.score) {
                        active = best;
                    }
                    lastHitX = x;
                } else if (active != null && lastHitX >= 0 && x - lastHitX > maxGap) {
                    out.add(makeNoteFromStaffPosition(active.x, active.y, staff, 1));
                    active = null;
                    lastHitX = -1;
                }
            }
            if (active != null) {
                out.add(makeNoteFromStaffPosition(active.x, active.y, staff, 1));
            }
        }
        return out;
    }

    private WindowCandidate bestWindowCandidate(boolean[] black, int width, int height, StaffModel staff, int x) {
        WindowCandidate best = null;
        for (int step = -2; step <= 10; step++) {
            float y = staff.bottom - step * staff.spacing * 0.5f;
            if (!isInsideStaffCorridor(staff, y)) continue;
            float score = windowInkScore(black, width, height, x, y, staff.spacing);
            if (best == null || score > best.score) {
                best = new WindowCandidate(x, y, score);
            }
        }
        return best;
    }

    private float windowInkScore(boolean[] black, int width, int height, int cx, float cy, float spacing) {
        int rx = Math.max(3, Math.round(spacing * 0.55f));
        int ry = Math.max(3, Math.round(spacing * 0.45f));
        int[] rowHits = new int[ry * 2 + 1];
        int[] colHits = new int[rx * 2 + 1];
        int ink = 0;
        int leftInk = 0;
        int rightInk = 0;
        int topInk = 0;
        int bottomInk = 0;
        for (int dy = -ry; dy <= ry; dy++) {
            int y = Math.round(cy) + dy;
            if (y < 0 || y >= height) continue;
            int row = y * width;
            for (int dx = -rx; dx <= rx; dx++) {
                int x = cx + dx;
                if (x < 0 || x >= width) continue;
                float nx = dx / (float) rx;
                float ny = dy / (float) ry;
                if (nx * nx + ny * ny > 1.0f) continue;
                if (black[row + x]) {
                    ink++;
                    rowHits[dy + ry]++;
                    colHits[dx + rx]++;
                    if (dx < 0) leftInk++;
                    if (dx > 0) rightInk++;
                    if (dy < 0) topInk++;
                    if (dy > 0) bottomInk++;
                }
            }
        }

        int rowsWithBody = 0;
        int colsWithBody = 0;
        int minRowHits = Math.max(2, Math.round(rx * 0.45f));
        int minColHits = Math.max(2, Math.round(ry * 0.45f));
        for (int hits : rowHits) {
            if (hits >= minRowHits) rowsWithBody++;
        }
        for (int hits : colHits) {
            if (hits >= minColHits) colsWithBody++;
        }
        if (rowsWithBody < Math.max(2, Math.round(ry * 0.70f))) {
            return 0f;
        }
        if (colsWithBody < Math.max(2, Math.round(rx * 0.70f))) {
            return 0f;
        }

        int horizontalBalance = Math.min(leftInk, rightInk);
        int verticalBalance = Math.min(topInk, bottomInk);
        int horizontalImbalance = Math.abs(leftInk - rightInk);
        int verticalImbalance = Math.abs(topInk - bottomInk);
        return ink + horizontalBalance * 0.8f + verticalBalance * 0.5f
                - horizontalImbalance * 0.35f - verticalImbalance * 0.20f;
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
        if (best == null || bestDist > Math.max(8f, best.spacing * STAFF_CORRIDOR_HALF_HEIGHT)) {
            return null;
        }
        return best;
    }

    private boolean isInsideStaffCorridor(StaffModel staff, float y) {
        float margin = staff.spacing * 1.25f;
        return y >= staff.top - margin && y <= staff.bottom + margin;
    }

    private boolean isSupportedPitch(NoteEvent note) {
        return note.octave >= MIN_FLUTE_OCTAVE && note.octave <= MAX_FLUTE_OCTAVE;
    }

    private boolean isPlausible(List<NoteEvent> notes, List<StaffModel> staves) {
        if (notes.size() < MIN_DIRECT_NOTES) {
            return false;
        }
        int maxNotes = Math.min(MAX_DIRECT_NOTES, Math.max(24, staves.size() * MAX_NOTES_PER_STAFF));
        if (notes.size() > maxNotes) {
            return false;
        }
        if (notes.size() > 24 && hasSingleDuration(notes)) {
            return false;
        }

        int badOctaves = 0;
        for (NoteEvent note : notes) {
            if (!isSupportedPitch(note)) {
                badOctaves++;
            }
        }
        return badOctaves == 0 || badOctaves * 10 <= notes.size();
    }

    private boolean hasSingleDuration(List<NoteEvent> notes) {
        if (notes.isEmpty()) {
            return true;
        }
        String first = notes.get(0).duration;
        for (NoteEvent note : notes) {
            if (first == null) {
                if (note.duration != null) return false;
            } else if (!first.equals(note.duration)) {
                return false;
            }
        }
        return true;
    }

    private List<NoteEvent> dedupeAndOrderNotes(List<NoteEvent> notes, final List<StaffModel> staves) {
        Collections.sort(notes, new Comparator<NoteEvent>() {
            @Override
            public int compare(NoteEvent left, NoteEvent right) {
                StaffModel leftStaff = nearestStaff(staves, left.y);
                StaffModel rightStaff = nearestStaff(staves, right.y);
                float leftTop = leftStaff == null ? left.y : leftStaff.top;
                float rightTop = rightStaff == null ? right.y : rightStaff.top;
                int byStaff = Float.compare(leftTop, rightTop);
                if (byStaff != 0) return byStaff;
                return Float.compare(left.x, right.x);
            }
        });

        List<NoteEvent> kept = new ArrayList<NoteEvent>();
        for (NoteEvent candidate : notes) {
            StaffModel candidateStaff = nearestStaff(staves, candidate.y);
            boolean duplicate = false;
            for (NoteEvent existing : kept) {
                StaffModel existingStaff = nearestStaff(staves, existing.y);
                if (candidateStaff != existingStaff) continue;
                float spacing = candidateStaff == null ? 12f : candidateStaff.spacing;
                if (Math.abs(candidate.x - existing.x) <= spacing * 0.55f
                        && Math.abs(candidate.y - existing.y) <= spacing * 0.70f) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                kept.add(candidate);
            }
        }
        return kept;
    }

    private List<NoteEvent> remeasureNotes(List<NoteEvent> notes) {
        List<NoteEvent> out = new ArrayList<NoteEvent>();
        for (int i = 0; i < notes.size(); i++) {
            NoteEvent note = notes.get(i);
            out.add(new NoteEvent(note.noteName, note.octave, note.duration, 1 + (i / 4), note.x, note.y));
        }
        return out;
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
                canvas.drawLine(staff.left, y, staff.right, y, staffPaint);
            }
        }

        float r = Math.max(4f, (staves.isEmpty() ? 8f : staves.get(0).spacing * 0.45f));
        for (NoteEvent note : notes) {
            canvas.drawCircle(note.x, note.y, r, notePaint);
        }
        return out;
    }

    static class StaffModel {
        float top;
        float bottom;
        float spacing;
        float center;
        float left;
        float right;
    }

    private static class StaffCandidate {
        final StaffModel staff;
        final float score;

        StaffCandidate(StaffModel staff, float score) {
            this.staff = staff;
            this.score = score;
        }
    }

    private static class WindowCandidate {
        final float x;
        final float y;
        final float score;

        WindowCandidate(float x, float y, float score) {
            this.x = x;
            this.y = y;
            this.score = score;
        }
    }

    static class DirectRecognition {
        final List<NoteEvent> notes;
        final List<StaffModel> staves;
        final List<Integer> linePeaks;

        DirectRecognition(List<NoteEvent> notes, List<StaffModel> staves, List<Integer> linePeaks) {
            this.notes = notes;
            this.staves = staves;
            this.linePeaks = linePeaks;
        }
    }
}
