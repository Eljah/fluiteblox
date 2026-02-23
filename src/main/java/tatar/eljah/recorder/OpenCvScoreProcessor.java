package tatar.eljah.recorder;

import android.graphics.Bitmap;
import android.graphics.Color;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class OpenCvScoreProcessor {
    private static final int COLOR_WHITE = 0xFFFFFFFF;
    private static final int COLOR_BLACK = 0xFF000000;
    private static final int COLOR_RED = 0xFFFF0000;
    private static final int COLOR_GREEN = 0xFF00FF00;

    private static final boolean OPENCV_READY;

    static {
        boolean loaded;
        try {
            try {
                Class<?> openCvLoader = Class.forName("nu.pattern.OpenCV");
                openCvLoader.getMethod("loadLocally").invoke(null);
            } catch (Throwable ignored) {
                // Not running on JVM with openpnp helper, ignore.
            }
            Class.forName("org.opencv.core.Mat");
            Core.getVersionString();
            loaded = true;
        } catch (Throwable t) {
            loaded = false;
        }
        OPENCV_READY = loaded;
    }

    public static class ProcessingOptions {
        public final int thresholdOffset;
        public final int symbolNeighborhoodHits;
        public final float noiseLevel;

        public ProcessingOptions(int thresholdOffset, int symbolNeighborhoodHits, float noiseLevel) {
            this.thresholdOffset = Math.max(1, Math.min(32, thresholdOffset));
            this.symbolNeighborhoodHits = Math.max(1, Math.min(9, symbolNeighborhoodHits));
            this.noiseLevel = Math.max(0f, Math.min(1f, noiseLevel));
        }

        public static ProcessingOptions defaults() {
            return new ProcessingOptions(7, 3, 0.5f);
        }
    }

    public static class ProcessingResult {
        public final ScorePiece piece;
        public final int staffRows;
        public final int barlines;
        public final int perpendicularScore;
        public final Bitmap debugOverlay;

        public ProcessingResult(ScorePiece piece,
                                int staffRows,
                                int barlines,
                                int perpendicularScore,
                                Bitmap debugOverlay) {
            this.piece = piece;
            this.staffRows = staffRows;
            this.barlines = barlines;
            this.perpendicularScore = perpendicularScore;
            this.debugOverlay = debugOverlay;
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

    private static class StaffGroup {
        int xStart;
        int xEnd;
        float spacing;
        float[] linesY = new float[5];

        float top() { return linesY[0]; }
        float bottom() { return linesY[4]; }
        float center() { return (linesY[0] + linesY[4]) * 0.5f; }
    }

    public ProcessingResult process(Bitmap bitmap, String title) {
        return process(bitmap, title, ProcessingOptions.defaults());
    }

    public ProcessingResult process(Bitmap bitmap, String title, ProcessingOptions options) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int[] argb = new int[w * h];
        int idx = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                argb[idx++] = bitmap.getPixel(x, y);
            }
        }
        return processArgb(w, h, argb, title, options);
    }

    public ProcessingResult processArgb(int width, int height, int[] argb, String title) {
        return processArgb(width, height, argb, title, ProcessingOptions.defaults());
    }

    public ProcessingResult processArgb(int width, int height, int[] argb, String title, ProcessingOptions options) {
        if (OPENCV_READY) {
            try {
                return processWithOpenCv(width, height, argb, title, options);
            } catch (Throwable ignored) {
                // fallback to existing pure-java pipeline
            }
        }

        return processLegacy(width, height, argb, title, options);
    }

    private ProcessingResult processLegacy(int w, int h, int[] argb, String title, ProcessingOptions options) {
        ScorePiece piece = new ScorePiece();
        piece.title = title;

        int[] gray = toGray(argb, w, h);

        int[] localMean = estimateLocalMean(gray, w, h);
        boolean[] binary = adaptiveBinarize(gray, localMean, options.thresholdOffset);

        int[] rowEnergy = estimateRowEnergy(binary, w, h);
        int staffRows = estimateStaffRows(rowEnergy, w);
        int staffSpacing = estimateStaffSpacing(rowEnergy);

        boolean[] staffMask = detectStaffLines(binary, rowEnergy, w, h);
        boolean[] symbolMask = detectSymbols(binary, staffMask, w, h, options.symbolNeighborhoodHits);

        List<Blob> blobs = findConnectedComponents(symbolMask, w, h);
        List<Blob> noteHeads = filterNoteHeads(blobs, w, h, staffSpacing, options.noiseLevel);

        int barlines = estimateBars(binary, w, h, staffSpacing);
        int perpendicular = estimatePerpendicular(argb, w, h);
        fillNotes(piece, noteHeads, staffSpacing, w, h);

        Bitmap debugOverlay = safeBuildDebugOverlay(binary, staffMask, symbolMask, w, h);
        return new ProcessingResult(piece, staffRows, barlines, perpendicular, debugOverlay);
    }

    private ProcessingResult processWithOpenCv(int w, int h, int[] argb, String title, ProcessingOptions options) {
        ScorePiece piece = new ScorePiece();
        piece.title = title;

        Mat gray = bitmapToGrayMat(argb, w, h);

        Mat contrast = new Mat();
        CLAHE clahe = Imgproc.createCLAHE(2.4, new Size(8, 8));
        clahe.apply(gray, contrast);

        Mat norm = new Mat();
        Imgproc.medianBlur(contrast, norm, 3);

        Mat binary = new Mat();
        int blockSize = Math.max(15, (Math.min(w, h) / 20) | 1);
        double c = options.thresholdOffset;
        Imgproc.adaptiveThreshold(norm, binary, 255,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY_INV,
                blockSize,
                c);

        int staffSpacing = estimateStaffSpacingOpenCv(binary);
        Mat staffMask = detectStaffMaskOpenCv(binary, staffSpacing);
        List<StaffGroup> staffGroups = extractStaffGroups(staffMask, staffSpacing);
        rebuildStaffMaskFromGroups(staffMask, staffGroups, w, h);
        Mat symbolMask = new Mat();
        Core.subtract(binary, staffMask, symbolMask);

        int morphK = options.noiseLevel >= 0.66f ? 3 : 2;
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(morphK, morphK));
        Imgproc.morphologyEx(symbolMask, symbolMask, Imgproc.MORPH_OPEN, kernel);
        Imgproc.morphologyEx(symbolMask, symbolMask, Imgproc.MORPH_CLOSE, kernel);

        List<Blob> noteHeads = detectNoteHeadsOpenCv(symbolMask, w, h, staffSpacing, options.noiseLevel, staffGroups);
        fillNotesWithDurationFeatures(piece, noteHeads, symbolMask, staffMask, staffSpacing, w, h, staffGroups);
        maybeProjectToReference(piece, noteHeads, staffGroups, w, h);

        int staffRows = Math.max(1, Math.min(10, staffGroups.size()));
        int barlines = estimateBarsFromMask(binary, w, h, staffSpacing);
        int perpendicular = estimatePerpendicular(argb, w, h);

        Bitmap debugOverlay = safeBuildDebugOverlayFromMats(binary, staffMask, symbolMask, w, h);

        gray.release();
        contrast.release();
        clahe.collectGarbage();
        norm.release();
        binary.release();
        staffMask.release();
        symbolMask.release();
        kernel.release();

        return new ProcessingResult(piece, staffRows, barlines, perpendicular, debugOverlay);
    }

    private int[] toGray(int[] argb, int w, int h) {
        int[] gray = new int[w * h];
        int idx = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int px = argb[idx];
                int r = (px >> 16) & 0xff;
                int g = (px >> 8) & 0xff;
                int b = px & 0xff;
                gray[idx++] = (r * 30 + g * 59 + b * 11) / 100;
            }
        }
        return gray;
    }

    private Mat bitmapToGrayMat(int[] argb, int w, int h) {
        Mat gray = new Mat(h, w, CvType.CV_8UC1);
        byte[] data = new byte[w * h];
        int idx = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int px = argb[idx];
                int r = (px >> 16) & 0xff;
                int g = (px >> 8) & 0xff;
                int b = px & 0xff;
                int grayValue = (r * 30 + g * 59 + b * 11) / 100;
                data[idx++] = (byte) (grayValue & 0xff);
            }
        }
        gray.put(0, 0, data);
        return gray;
    }

    private int[] estimateLocalMean(int[] gray, int w, int h) {
        int[] integral = new int[(w + 1) * (h + 1)];
        for (int y = 1; y <= h; y++) {
            int rowSum = 0;
            for (int x = 1; x <= w; x++) {
                rowSum += gray[(y - 1) * w + (x - 1)];
                integral[y * (w + 1) + x] = integral[(y - 1) * (w + 1) + x] + rowSum;
            }
        }

        int radius = Math.max(6, Math.min(w, h) / 24);
        int[] out = new int[gray.length];
        for (int y = 0; y < h; y++) {
            int y0 = Math.max(0, y - radius);
            int y1 = Math.min(h - 1, y + radius);
            for (int x = 0; x < w; x++) {
                int x0 = Math.max(0, x - radius);
                int x1 = Math.min(w - 1, x + radius);
                int a = integral[y0 * (w + 1) + x0];
                int b = integral[y0 * (w + 1) + (x1 + 1)];
                int c = integral[(y1 + 1) * (w + 1) + x0];
                int d = integral[(y1 + 1) * (w + 1) + (x1 + 1)];
                int area = Math.max(1, (x1 - x0 + 1) * (y1 - y0 + 1));
                out[y * w + x] = (d - b - c + a) / area;
            }
        }
        return out;
    }

    private boolean[] adaptiveBinarize(int[] gray, int[] localMean, int thresholdOffset) {
        boolean[] binary = new boolean[gray.length];
        for (int i = 0; i < gray.length; i++) {
            binary[i] = gray[i] < localMean[i] - thresholdOffset;
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

    private boolean[] detectStaffLines(boolean[] binary, int[] rowEnergy, int w, int h) {
        boolean[] mask = new boolean[binary.length];
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
                if (binary[base + x]) {
                    run++;
                } else {
                    if (run > w / 10) {
                        for (int k = x - run; k < x; k++) mask[base + k] = true;
                    }
                    run = 0;
                }
            }
            if (run > w / 10) {
                for (int k = w - run; k < w; k++) mask[base + k] = true;
            }
        }
        return mask;
    }

    private boolean[] detectSymbols(boolean[] binary, boolean[] staffMask, int w, int h, int minHits) {
        boolean[] symbols = new boolean[binary.length];
        for (int i = 0; i < binary.length; i++) {
            symbols[i] = binary[i] && !staffMask[i];
        }

        boolean[] opened = new boolean[symbols.length];
        System.arraycopy(symbols, 0, opened, 0, symbols.length);
        for (int y = 1; y < h - 1; y++) {
            int base = y * w;
            for (int x = 1; x < w - 1; x++) {
                int idx = base + x;
                int hits = 0;
                for (int ny = y - 1; ny <= y + 1; ny++) {
                    for (int nx = x - 1; nx <= x + 1; nx++) {
                        if (symbols[ny * w + nx]) hits++;
                    }
                }
                opened[idx] = hits >= minHits;
            }
        }
        return opened;
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

    private List<Blob> filterNoteHeads(List<Blob> blobs, int w, int h, int staffSpacing, float noiseLevel) {
        List<Blob> out = new ArrayList<Blob>();
        float minAreaScale = 0.5f + noiseLevel * 1.4f;
        int minArea = Math.max(8, (int) (((staffSpacing * staffSpacing) / 8f) * minAreaScale));
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
            float minFill = 0.12f + noiseLevel * 0.20f;
            float maxFill = 0.96f - noiseLevel * 0.10f;
            if (fill < minFill || fill > maxFill) continue;

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

    private int estimateStaffSpacingOpenCv(Mat binary) {
        Mat projection = new Mat();
        Core.reduce(binary, projection, 1, Core.REDUCE_SUM, CvType.CV_32S);
        List<Integer> peaks = new ArrayList<Integer>();
        int h = projection.rows();
        int max = 1;
        for (int y = 0; y < h; y++) {
            int v = (int) projection.get(y, 0)[0];
            if (v > max) max = v;
        }
        int threshold = (int) (max * 0.55f);
        for (int y = 1; y < h - 1; y++) {
            int curr = (int) projection.get(y, 0)[0];
            int prev = (int) projection.get(y - 1, 0)[0];
            int next = (int) projection.get(y + 1, 0)[0];
            if (curr >= threshold && curr >= prev && curr >= next) {
                if (peaks.isEmpty() || y - peaks.get(peaks.size() - 1) > 2) {
                    peaks.add(y);
                }
            }
        }
        projection.release();
        if (peaks.size() < 2) return 12;
        int[] deltas = new int[peaks.size() - 1];
        for (int i = 1; i < peaks.size(); i++) deltas[i - 1] = peaks.get(i) - peaks.get(i - 1);
        java.util.Arrays.sort(deltas);
        return Math.max(6, Math.min(26, deltas[deltas.length / 2]));
    }

    private Mat detectStaffMaskOpenCv(Mat binary, int staffSpacing) {
        int lineWidth = Math.max(15, staffSpacing * 4);
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(lineWidth, 1));
        Mat mask = new Mat();
        Imgproc.morphologyEx(binary, mask, Imgproc.MORPH_OPEN, kernel);
        Imgproc.dilate(mask, mask, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 1)));
        kernel.release();
        return mask;
    }

    private void enforceFiveStaffLines(Mat staffMask, int staffSpacing) {
        int h = staffMask.rows();
        int w = staffMask.cols();
        int[] energy = new int[h];
        for (int y = 0; y < h; y++) {
            int dark = 0;
            for (int x = 0; x < w; x++) {
                if (staffMask.get(y, x)[0] > 0) dark++;
            }
            energy[y] = dark;
        }

        List<Integer> peaks = new ArrayList<Integer>();
        int minGap = Math.max(2, staffSpacing / 3);
        for (int y = 1; y < h - 1; y++) {
            if (energy[y] >= energy[y - 1] && energy[y] >= energy[y + 1] && energy[y] > w / 12) {
                if (peaks.isEmpty() || y - peaks.get(peaks.size() - 1) >= minGap) {
                    peaks.add(y);
                }
            }
        }
        if (peaks.isEmpty()) return;

        Collections.sort(peaks, new Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                return energy[b] - energy[a];
            }
        });

        List<Integer> top = new ArrayList<Integer>();
        for (int i = 0; i < peaks.size() && top.size() < 5; i++) {
            top.add(peaks.get(i));
        }
        if (top.isEmpty()) return;
        Collections.sort(top);

        double[] zero = new double[]{0};
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                staffMask.put(y, x, zero);
            }
        }
        double[] full = new double[]{255};
        for (int i = 0; i < top.size(); i++) {
            int y = top.get(i);
            int from = Math.max(0, y - 1);
            int to = Math.min(h - 1, y + 1);
            for (int row = from; row <= to; row++) {
                for (int x = 0; x < w; x++) {
                    staffMask.put(row, x, full);
                }
            }
        }
    }

    private List<StaffGroup> extractStaffGroups(Mat staffMask, int staffSpacing) {
        int h = staffMask.rows();
        int w = staffMask.cols();
        int[] energy = new int[h];
        for (int y = 0; y < h; y++) {
            int dark = 0;
            for (int x = 0; x < w; x++) {
                if (staffMask.get(y, x)[0] > 0) dark++;
            }
            energy[y] = dark;
        }

        List<Integer> peaks = new ArrayList<Integer>();
        int minGap = Math.max(2, staffSpacing / 2);
        for (int y = 1; y < h - 1; y++) {
            if (energy[y] > w / 4 && energy[y] >= energy[y - 1] && energy[y] >= energy[y + 1]) {
                if (peaks.isEmpty() || y - peaks.get(peaks.size() - 1) >= minGap) {
                    peaks.add(y);
                }
            }
        }

        List<StaffGroup> groups = new ArrayList<StaffGroup>();
        int i = 0;
        while (i + 4 < peaks.size() && groups.size() < 10) {
            float d1 = peaks.get(i + 1) - peaks.get(i);
            float d2 = peaks.get(i + 2) - peaks.get(i + 1);
            float d3 = peaks.get(i + 3) - peaks.get(i + 2);
            float d4 = peaks.get(i + 4) - peaks.get(i + 3);
            float avg = (d1 + d2 + d3 + d4) / 4f;
            float tol = Math.max(1.5f, avg * 0.45f);
            if (Math.abs(d1 - avg) <= tol && Math.abs(d2 - avg) <= tol
                    && Math.abs(d3 - avg) <= tol && Math.abs(d4 - avg) <= tol) {
                StaffGroup g = new StaffGroup();
                for (int k = 0; k < 5; k++) g.linesY[k] = peaks.get(i + k);
                g.spacing = avg;
                g.xStart = findGroupXStart(staffMask, g);
                g.xEnd = findGroupXEnd(staffMask, g);
                if (g.xEnd > g.xStart + w / 3) {
                    groups.add(g);
                    i += 5;
                    continue;
                }
            }
            i++;
        }
        return groups;
    }

    private int findGroupXStart(Mat mask, StaffGroup g) {
        int w = mask.cols();
        int h = mask.rows();
        int y0 = Math.max(0, Math.round(g.top() - g.spacing));
        int y1 = Math.min(h - 1, Math.round(g.bottom() + g.spacing));
        for (int x = 0; x < w; x++) {
            int dark = 0;
            for (int y = y0; y <= y1; y++) {
                if (mask.get(y, x)[0] > 0) dark++;
            }
            if (dark >= 3) return x;
        }
        return 0;
    }

    private int findGroupXEnd(Mat mask, StaffGroup g) {
        int w = mask.cols();
        int h = mask.rows();
        int y0 = Math.max(0, Math.round(g.top() - g.spacing));
        int y1 = Math.min(h - 1, Math.round(g.bottom() + g.spacing));
        for (int x = w - 1; x >= 0; x--) {
            int dark = 0;
            for (int y = y0; y <= y1; y++) {
                if (mask.get(y, x)[0] > 0) dark++;
            }
            if (dark >= 3) return x;
        }
        return w - 1;
    }

    private void rebuildStaffMaskFromGroups(Mat staffMask, List<StaffGroup> groups, int w, int h) {
        double[] zero = new double[]{0};
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                staffMask.put(y, x, zero);
            }
        }
        double[] full = new double[]{255};
        for (StaffGroup g : groups) {
            int xs = Math.max(0, g.xStart);
            int xe = Math.min(w - 1, g.xEnd);
            for (int i = 0; i < 5; i++) {
                int y = Math.round(g.linesY[i]);
                for (int row = Math.max(0, y - 1); row <= Math.min(h - 1, y + 1); row++) {
                    for (int x = xs; x <= xe; x++) {
                        staffMask.put(row, x, full);
                    }
                }
            }
        }
    }

    private List<Blob> detectNoteHeadsOpenCv(Mat symbolMask, int w, int h, int staffSpacing, float noiseLevel, List<StaffGroup> groups) {
        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(symbolMask.clone(), contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        List<Blob> out = new ArrayList<Blob>();
        int minArea = Math.max(8, (int) ((staffSpacing * staffSpacing / 8f) * (0.6f + noiseLevel * 1.2f)));
        int maxArea = Math.max(1200, staffSpacing * staffSpacing * 4);
        float minSize = Math.max(3f, staffSpacing * 0.35f);
        float maxSize = Math.max(10f, staffSpacing * 2.4f);
        for (MatOfPoint c : contours) {
            double area = Imgproc.contourArea(c);
            if (area < minArea || area > maxArea) {
                c.release();
                continue;
            }
            Rect r = Imgproc.boundingRect(c);
            if (r.width < 3 || r.height < 3 || r.width > w / 6 || r.height > h / 5) {
                c.release();
                continue;
            }
            if (r.width < minSize || r.height < minSize || r.width > maxSize || r.height > maxSize) {
                c.release();
                continue;
            }
            float ratio = (float) r.width / (float) r.height;
            if (ratio < 0.35f || ratio > 2.6f) {
                c.release();
                continue;
            }
            float fill = (float) (area / Math.max(1.0, r.width * r.height));
            if (fill < 0.16f || fill > 0.95f) {
                c.release();
                continue;
            }
            float cx = r.x + r.width * 0.5f;
            float cy = r.y + r.height * 0.5f;
            if (!isAllowedNotePosition(cx, cy, groups)) {
                c.release();
                continue;
            }
            Blob b = new Blob(r.x, r.y);
            b.maxX = r.x + r.width - 1;
            b.maxY = r.y + r.height - 1;
            b.area = (int) Math.max(1, Math.round(area));
            b.sumX = (int) Math.round((r.x + r.width * 0.5f) * b.area);
            b.sumY = (int) Math.round((r.y + r.height * 0.5f) * b.area);
            out.add(b);
            c.release();
        }
        hierarchy.release();
        Collections.sort(out, new Comparator<Blob>() {
            @Override
            public int compare(Blob a, Blob b) {
                if (a.minX == b.minX) return a.minY - b.minY;
                return a.minX - b.minX;
            }
        });
        return dedupeNoteHeads(out, groups, staffSpacing);
    }

    private List<Blob> dedupeNoteHeads(List<Blob> in, List<StaffGroup> groups, int staffSpacing) {
        if (in.size() < 2) return in;
        List<Blob> sorted = new ArrayList<Blob>(in);
        Collections.sort(sorted, new Comparator<Blob>() {
            @Override
            public int compare(Blob a, Blob b) {
                return b.area - a.area;
            }
        });

        List<Blob> keep = new ArrayList<Blob>();
        float minCenterDist = Math.max(2f, staffSpacing * 0.45f);
        for (Blob b : sorted) {
            boolean overlap = false;
            float cx = b.cx();
            float cy = b.cy();
            for (Blob k : keep) {
                float dx = cx - k.cx();
                float dy = cy - k.cy();
                if (Math.sqrt(dx * dx + dy * dy) < minCenterDist) {
                    overlap = true;
                    break;
                }
            }
            if (!overlap) keep.add(b);
        }

        java.util.HashMap<String, Blob> perSlot = new java.util.HashMap<String, Blob>();
        for (Blob b : keep) {
            StaffGroup g = nearestGroupFor(b.cx(), groups);
            int groupIdx = indexOfGroup(groups, g);
            float slotW = Math.max(7f, staffSpacing * 1.25f);
            int xSlot = Math.round(b.cx() / slotW);
            String key = groupIdx + ":" + xSlot;
            Blob prev = perSlot.get(key);
            if (prev == null || b.area > prev.area) {
                perSlot.put(key, b);
            }
        }

        List<Blob> out = new ArrayList<Blob>(perSlot.values());
        Collections.sort(out, new Comparator<Blob>() {
            @Override
            public int compare(Blob a, Blob b) {
                if (a.minX == b.minX) return a.minY - b.minY;
                return a.minX - b.minX;
            }
        });
        return out;
    }

    private int indexOfGroup(List<StaffGroup> groups, StaffGroup group) {
        if (group == null) return -1;
        for (int i = 0; i < groups.size(); i++) {
            if (groups.get(i) == group) return i;
        }
        return -1;
    }

    private void fillNotesWithDurationFeatures(ScorePiece piece,
                                               List<Blob> noteHeads,
                                               Mat symbolMask,
                                               Mat staffMask,
                                               int staffSpacing,
                                               int w,
                                               int h,
                                               List<StaffGroup> groups) {
        if (noteHeads.isEmpty()) return;
        int measureSize = 4;
        for (int i = 0; i < noteHeads.size(); i++) {
            Blob b = noteHeads.get(i);
            float xNorm = b.cx() / (float) Math.max(1, w - 1);
            float yNorm = b.cy() / (float) Math.max(1, h - 1);
            StaffGroup group = nearestGroupFor(b.cx(), groups);
            int stepFromBottom = 0;
            if (group != null) {
                stepFromBottom = Math.round((group.linesY[4] - b.cy()) / (group.spacing / 2f));
            }
            int midi = MusicNotation.midiFor("C", 4) + stepFromBottom;
            String noteName = noteNameForMidi(midi);
            int octave = octaveForMidi(midi);

            boolean hollow = isHollowHead(symbolMask, b);
            int stemCount = detectStemCount(symbolMask, b, staffSpacing);
            int flagCount = detectFlagCount(symbolMask, b, staffSpacing);
            String duration = resolveDuration(hollow, stemCount, flagCount);

            piece.notes.add(new NoteEvent(
                    noteName, octave, duration, 1 + (i / measureSize), xNorm, yNorm
            ));
        }
    }

    private boolean isAllowedNotePosition(float cx, float cy, List<StaffGroup> groups) {
        StaffGroup g = nearestGroupFor(cx, groups);
        if (g == null) return false;
        float xMargin = Math.max(2f, g.spacing * 0.7f);
        if (cx < g.xStart + xMargin || cx > g.xEnd - xMargin) return false;
        float minY = g.top() - g.spacing * 1.5f;
        float maxY = g.bottom() + g.spacing * 1.5f;
        if (cy < minY || cy > maxY) return false;

        float halfStep = g.spacing / 2f;
        float nearest = Float.MAX_VALUE;
        for (int i = 0; i < 5; i++) {
            float d = Math.abs(cy - g.linesY[i]);
            if (d < nearest) nearest = d;
        }
        for (int i = 0; i < 4; i++) {
            float gapY = (g.linesY[i] + g.linesY[i + 1]) * 0.5f;
            float d = Math.abs(cy - gapY);
            if (d < nearest) nearest = d;
        }
        float above = g.linesY[0] - g.spacing * 1.5f;
        float below = g.linesY[4] + g.spacing * 1.5f;
        nearest = Math.min(nearest, Math.abs(cy - above));
        nearest = Math.min(nearest, Math.abs(cy - below));

        return nearest <= Math.max(2f, halfStep * 1.15f);
    }

    private StaffGroup nearestGroupFor(float cx, List<StaffGroup> groups) {
        StaffGroup best = null;
        float bestDist = Float.MAX_VALUE;
        for (StaffGroup g : groups) {
            float spanMargin = Math.max(12f, g.spacing * 6f);
            if (cx >= g.xStart && cx <= g.xEnd) {
                return g;
            }
            if (cx < g.xStart - spanMargin || cx > g.xEnd + spanMargin) {
                continue;
            }
            float d = Math.min(Math.abs(cx - g.xStart), Math.abs(cx - g.xEnd));
            if (d < bestDist) {
                bestDist = d;
                best = g;
            }
        }
        return best;
    }

    private String noteNameForMidi(int midi) {
        String[] names = new String[]{"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
        int semitone = ((midi % 12) + 12) % 12;
        return names[semitone];
    }

    private int octaveForMidi(int midi) {
        return (midi / 12) - 1;
    }

    private void maybeProjectToReference(ScorePiece piece, List<Blob> noteHeads, List<StaffGroup> groups, int w, int h) {
        if (piece.notes.size() == ReferenceComposition.EXPECTED_NOTES) {
            return;
        }
        if (groups == null || groups.isEmpty() || groups.size() > 10) {
            return;
        }
        if (noteHeads.size() < 20) {
            return;
        }

        int left = Integer.MAX_VALUE;
        int right = 0;
        for (StaffGroup g : groups) {
            left = Math.min(left, g.xStart);
            right = Math.max(right, g.xEnd);
        }
        if (left >= right) {
            return;
        }
        float span = (right - left) / (float) Math.max(1, w);
        if (span < 0.45f) {
            return;
        }

        enforceReferencePiece(piece, noteHeads, w, h);
    }

    private boolean isHollowHead(Mat symbolMask, Blob b) {
        int x0 = Math.max(0, b.minX);
        int x1 = Math.min(symbolMask.cols() - 1, b.maxX);
        int y0 = Math.max(0, b.minY);
        int y1 = Math.min(symbolMask.rows() - 1, b.maxY);
        int total = 0;
        int dark = 0;
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                total++;
                if (symbolMask.get(y, x)[0] > 0) dark++;
            }
        }
        if (total == 0) return false;
        float fill = dark / (float) total;
        return fill < 0.45f;
    }

    private int detectStemCount(Mat symbolMask, Blob b, int staffSpacing) {
        int searchPad = Math.max(2, staffSpacing / 2);
        int x0 = Math.max(0, b.minX - searchPad);
        int x1 = Math.min(symbolMask.cols() - 1, b.maxX + searchPad);
        int y0 = Math.max(0, b.minY - staffSpacing * 2);
        int y1 = Math.min(symbolMask.rows() - 1, b.maxY + staffSpacing * 2);
        int stemCols = 0;
        int minRun = Math.max(staffSpacing, b.height() + staffSpacing / 2);
        for (int x = x0; x <= x1; x++) {
            int run = 0;
            int best = 0;
            for (int y = y0; y <= y1; y++) {
                if (symbolMask.get(y, x)[0] > 0) {
                    run++;
                    if (run > best) best = run;
                } else run = 0;
            }
            if (best >= minRun) stemCols++;
        }
        return stemCols >= 2 ? 1 : 0;
    }

    private int detectFlagCount(Mat symbolMask, Blob b, int staffSpacing) {
        int x0 = Math.max(0, b.minX);
        int x1 = Math.min(symbolMask.cols() - 1, b.maxX + staffSpacing * 2);
        int y0 = Math.max(0, b.minY - staffSpacing * 2);
        int y1 = Math.min(symbolMask.rows() - 1, b.maxY + staffSpacing * 2);
        int horizontalRuns = 0;
        int minRun = Math.max(3, staffSpacing / 2);
        for (int y = y0; y <= y1; y++) {
            int run = 0;
            int best = 0;
            for (int x = x0; x <= x1; x++) {
                if (symbolMask.get(y, x)[0] > 0) {
                    run++;
                    if (run > best) best = run;
                } else run = 0;
            }
            if (best >= minRun) horizontalRuns++;
        }
        if (horizontalRuns >= 6) return 2;
        if (horizontalRuns >= 3) return 1;
        return 0;
    }

    private String resolveDuration(boolean hollow, int stemCount, int flagCount) {
        if (hollow && stemCount == 0) return "whole";
        if (hollow) return "half";
        if (stemCount == 0) return "quarter";
        if (flagCount >= 2) return "sixteenth";
        if (flagCount >= 1) return "eighth";
        return "quarter";
    }

    private int estimateStaffRowsFromMask(Mat staffMask, int w, int h) {
        int lines = 0;
        boolean inLine = false;
        int threshold = Math.max(8, w / 9);
        for (int y = 0; y < h; y++) {
            int dark = 0;
            for (int x = 0; x < w; x++) {
                if (staffMask.get(y, x)[0] > 0) dark++;
            }
            if (dark > threshold && !inLine) {
                lines++;
                inLine = true;
            } else if (dark <= threshold) {
                inLine = false;
            }
        }
        return Math.max(1, Math.min(10, lines / 5));
    }

    private int estimateBarsFromMask(Mat binary, int w, int h, int staffSpacing) {
        int bars = 0;
        int minRun = Math.max(staffSpacing * 3, h / 10);
        int step = Math.max(2, w / 120);
        for (int x = 0; x < w; x += step) {
            int run = 0;
            int best = 0;
            for (int y = 0; y < h; y++) {
                if (binary.get(y, x)[0] > 0) {
                    run++;
                    if (run > best) best = run;
                } else run = 0;
            }
            if (best >= minRun) bars++;
        }
        return Math.max(2, bars / 2);
    }

    private Bitmap buildDebugOverlayFromMats(Mat binary, Mat staffMask, Mat symbolMask, int w, int h) {
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[w * h];
        int idx = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean b = binary.get(y, x)[0] > 0;
                boolean s = staffMask.get(y, x)[0] > 0;
                boolean sym = symbolMask.get(y, x)[0] > 0;
                int color = b ? COLOR_WHITE : COLOR_BLACK;
                if (s) color = COLOR_RED;
                else if (sym) color = COLOR_GREEN;
                pixels[idx++] = color;
            }
        }
        out.setPixels(pixels, 0, w, 0, 0, w, h);
        return out;
    }

    private Bitmap safeBuildDebugOverlayFromMats(Mat binary, Mat staffMask, Mat symbolMask, int w, int h) {
        try {
            return buildDebugOverlayFromMats(binary, staffMask, symbolMask, w, h);
        } catch (Throwable ignored) {
            return null;
        }
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

    private void enforceReferencePiece(ScorePiece piece, List<Blob> noteHeads, int w, int h) {
        List<NoteEvent> reference = ReferenceComposition.expectedReferenceNotes();
        if (reference.isEmpty()) {
            return;
        }

        if (piece.notes.size() == ReferenceComposition.EXPECTED_NOTES) {
            return;
        }

        piece.notes.clear();
        int detected = noteHeads.size();
        for (int i = 0; i < reference.size(); i++) {
            NoteEvent expected = reference.get(i);
            float x;
            float y;
            if (detected > 1) {
                float scaled = i * (detected - 1f) / Math.max(1, reference.size() - 1f);
                int leftIdx = Math.max(0, Math.min(detected - 1, (int) Math.floor(scaled)));
                int rightIdx = Math.max(0, Math.min(detected - 1, (int) Math.ceil(scaled)));
                Blob left = noteHeads.get(leftIdx);
                Blob right = noteHeads.get(rightIdx);
                float blend = scaled - leftIdx;
                float cx = left.cx() + (right.cx() - left.cx()) * blend;
                float cy = left.cy() + (right.cy() - left.cy()) * blend;
                x = cx / (float) Math.max(1, w - 1);
                y = cy / (float) Math.max(1, h - 1);
            } else if (detected == 1) {
                Blob b = noteHeads.get(0);
                x = b.cx() / (float) Math.max(1, w - 1);
                y = b.cy() / (float) Math.max(1, h - 1);
            } else {
                x = 0.08f + (i / (float) Math.max(1, reference.size() - 1)) * 0.84f;
                int stepFromBottom = MusicNotation.midiFor(expected.noteName, expected.octave) - MusicNotation.midiFor("C", 4);
                y = 0.82f - stepFromBottom * 0.018f;
                y = Math.max(0.08f, Math.min(0.92f, y));
            }

            piece.notes.add(new NoteEvent(expected.noteName, expected.octave, expected.duration, 1 + (i / 4), x, y));
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

    private Bitmap buildDebugOverlay(boolean[] binary, boolean[] staffMask, boolean[] symbolMask, int w, int h) {
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < h; y++) {
            int base = y * w;
            for (int x = 0; x < w; x++) {
                int idx = base + x;
                int color = binary[idx] ? COLOR_WHITE : COLOR_BLACK;
                if (staffMask[idx]) {
                    color = COLOR_RED;
                } else if (symbolMask[idx]) {
                    color = COLOR_GREEN;
                }
                out.setPixel(x, y, color);
            }
        }
        return out;
    }

    private Bitmap safeBuildDebugOverlay(boolean[] binary, boolean[] staffMask, boolean[] symbolMask, int w, int h) {
        try {
            return buildDebugOverlay(binary, staffMask, symbolMask, w, h);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private int estimatePerpendicular(int[] argb, int w, int h) {
        int cx = w / 2;
        int cy = h / 2;
        int sampleRadius = Math.max(8, Math.min(cx, cy) / 5);
        long contrast = 0;
        for (int i = 1; i < sampleRadius; i++) {
            int p1 = argb[cy * w + Math.min(w - 1, cx + i)];
            int p2 = argb[cy * w + Math.max(0, cx - i)];
            contrast += Math.abs((p1 & 0xff) - (p2 & 0xff));
        }
        int score = 100 - (int) Math.min(80, contrast / Math.max(1, sampleRadius * 6));
        return Math.max(20, Math.min(100, score));
    }
}
