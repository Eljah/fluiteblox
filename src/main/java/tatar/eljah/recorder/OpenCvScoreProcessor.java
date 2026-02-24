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
import org.opencv.imgproc.Moments;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class OpenCvScoreProcessor {
    private static final int COLOR_WHITE = 0xFFFFFFFF;
    private static final int COLOR_BLACK = 0xFF000000;
    private static final int COLOR_RED = 0xFFFF0000;
    private static final int COLOR_GREEN = 0xFF00FF00;

    private static final boolean OPENCV_READY;
    private static final String OPENCV_INIT_STACKTRACE;
    private static volatile boolean opencvRuntimeDisabled;

    static {
        boolean loaded;
        String initStacktrace = null;
        try {
            try {
                Class<?> openCvLoader = Class.forName("nu.pattern.OpenCV");
                openCvLoader.getMethod("loadLocally").invoke(null);
            } catch (Throwable ignored) {
                // Not running on JVM with openpnp helper, ignore.
            }
            Class.forName("org.opencv.core.Mat");
            verifyOpenCvNativeBinding();
            loaded = true;
        } catch (Throwable t) {
            loaded = false;
            initStacktrace = stackTraceToString(t);
        }
        OPENCV_READY = loaded;
        OPENCV_INIT_STACKTRACE = initStacktrace;
    }

    public static class ProcessingOptions {
        public final int thresholdOffset;
        public final int symbolNeighborhoodHits;
        public final float noiseLevel;
        public final boolean skipAdaptiveBinarization;
        public final boolean skipMorphNoiseSuppression;
        public final float noteMinAreaFactor;
        public final float noteMaxAreaFactor;
        public final float noteMinFill;
        public final float noteMaxFill;
        public final float noteMinCircularity;
        public final boolean recallFirstMode;
        public final float analyticalFilterStrength;
        public final float[] perStaffAnalyticalStrength;
        public final boolean lineStripePitchRefinement;

        public ProcessingOptions(int thresholdOffset, int symbolNeighborhoodHits, float noiseLevel) {
            this(thresholdOffset, symbolNeighborhoodHits, noiseLevel,
                    false, false,
                    0.6f, 4.0f,
                    0.18f, 0.9f,
                    0.32f,
                    false, 0.55f, null, false);
        }

        public ProcessingOptions(int thresholdOffset,
                                 int symbolNeighborhoodHits,
                                 float noiseLevel,
                                 boolean skipAdaptiveBinarization,
                                 boolean skipMorphNoiseSuppression,
                                 float noteMinAreaFactor,
                                 float noteMaxAreaFactor,
                                 float noteMinFill,
                                 float noteMaxFill,
                                 float noteMinCircularity,
                                 boolean recallFirstMode,
                                 float analyticalFilterStrength) {
            this(thresholdOffset, symbolNeighborhoodHits, noiseLevel,
                    skipAdaptiveBinarization, skipMorphNoiseSuppression,
                    noteMinAreaFactor, noteMaxAreaFactor, noteMinFill, noteMaxFill, noteMinCircularity,
                    recallFirstMode, analyticalFilterStrength, null, false);
        }

        public ProcessingOptions(int thresholdOffset,
                                 int symbolNeighborhoodHits,
                                 float noiseLevel,
                                 boolean skipAdaptiveBinarization,
                                 boolean skipMorphNoiseSuppression,
                                 float noteMinAreaFactor,
                                 float noteMaxAreaFactor,
                                 float noteMinFill,
                                 float noteMaxFill,
                                 float noteMinCircularity,
                                 boolean recallFirstMode,
                                 float analyticalFilterStrength,
                                 float[] perStaffAnalyticalStrength,
                                 boolean lineStripePitchRefinement) {
            this.thresholdOffset = Math.max(1, Math.min(32, thresholdOffset));
            this.symbolNeighborhoodHits = Math.max(1, Math.min(9, symbolNeighborhoodHits));
            this.noiseLevel = Math.max(0f, Math.min(1f, noiseLevel));
            this.skipAdaptiveBinarization = skipAdaptiveBinarization;
            this.skipMorphNoiseSuppression = skipMorphNoiseSuppression;
            this.noteMinAreaFactor = Math.max(0.25f, Math.min(2.2f, noteMinAreaFactor));
            this.noteMaxAreaFactor = Math.max(1.5f, Math.min(8.0f, noteMaxAreaFactor));
            this.noteMinFill = Math.max(0.08f, Math.min(0.55f, noteMinFill));
            this.noteMaxFill = Math.max(0.55f, Math.min(0.98f, noteMaxFill));
            this.noteMinCircularity = Math.max(0.08f, Math.min(0.8f, noteMinCircularity));
            this.recallFirstMode = recallFirstMode;
            this.analyticalFilterStrength = Math.max(0.0f, Math.min(1.0f, analyticalFilterStrength));
            this.perStaffAnalyticalStrength = perStaffAnalyticalStrength == null ? null : perStaffAnalyticalStrength.clone();
            this.lineStripePitchRefinement = lineStripePitchRefinement;
        }

        public static ProcessingOptions defaults() {
            return new ProcessingOptions(7, 3, 0.5f);
        }
    }

    public static class NoteDetectionDiagnostics {
        public int totalContours;
        public int rejectedByArea;
        public int rejectedByBounds;
        public int rejectedBySize;
        public int rejectedByAspect;
        public int rejectedByFill;
        public int rejectedByPerimeter;
        public int rejectedByCircularity;
        public int rejectedByStaffPosition;
        public int keptBeforeDedupe;
        public int removedByCenterDistanceDedupe;
        public int removedBySlotDedupe;
        public int rescuedByGapSizedBlob;
        public int filteredAsNonNoteByAnalyticalPass;
        public int finalKept;

        public String summary() {
            return "contours=" + totalContours
                    + ", reject(area=" + rejectedByArea
                    + ", bounds=" + rejectedByBounds
                    + ", size=" + rejectedBySize
                    + ", aspect=" + rejectedByAspect
                    + ", fill=" + rejectedByFill
                    + ", perimeter=" + rejectedByPerimeter
                    + ", circularity=" + rejectedByCircularity
                    + ", staffPos=" + rejectedByStaffPosition + ")"
                    + ", kept(beforeDedupe=" + keptBeforeDedupe
                    + ", final=" + finalKept
                    + ", dedupeCenter=" + removedByCenterDistanceDedupe
                    + ", dedupeSlot=" + removedBySlotDedupe
                    + ", rescuedGapBlob=" + rescuedByGapSizedBlob
                    + ", analyticalFiltered=" + filteredAsNonNoteByAnalyticalPass + ")";
        }
    }

    public static class ProcessingResult {
        public final ScorePiece piece;
        public final int staffRows;
        public final int barlines;
        public final int perpendicularScore;
        public final Bitmap debugOverlay;
        public final List<StaffCorridor> staffCorridors;
        public final String processingMode;
        public final boolean openCvUsed;
        public final String openCvStackTrace;
        public final NoteDetectionDiagnostics noteDiagnostics;

        public ProcessingResult(ScorePiece piece,
                                int staffRows,
                                int barlines,
                                int perpendicularScore,
                                Bitmap debugOverlay) {
            this(piece, staffRows, barlines, perpendicularScore, debugOverlay, new ArrayList<StaffCorridor>(), "legacy", false, null, null);
        }

        public ProcessingResult(ScorePiece piece,
                                int staffRows,
                                int barlines,
                                int perpendicularScore,
                                Bitmap debugOverlay,
                                List<StaffCorridor> staffCorridors) {
            this(piece, staffRows, barlines, perpendicularScore, debugOverlay, staffCorridors, "opencv", true, null, null);
        }

        public ProcessingResult(ScorePiece piece,
                                int staffRows,
                                int barlines,
                                int perpendicularScore,
                                Bitmap debugOverlay,
                                List<StaffCorridor> staffCorridors,
                                String processingMode,
                                boolean openCvUsed,
                                String openCvStackTrace,
                                NoteDetectionDiagnostics noteDiagnostics) {
            this.piece = piece;
            this.staffRows = staffRows;
            this.barlines = barlines;
            this.perpendicularScore = perpendicularScore;
            this.debugOverlay = debugOverlay;
            this.staffCorridors = staffCorridors == null ? new ArrayList<StaffCorridor>() : staffCorridors;
            this.processingMode = processingMode == null ? "legacy" : processingMode;
            this.openCvUsed = openCvUsed;
            this.openCvStackTrace = openCvStackTrace;
            this.noteDiagnostics = noteDiagnostics;
        }
    }

    public static class StaffCorridor {
        public final float left;
        public final float top;
        public final float right;
        public final float bottom;

        public StaffCorridor(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
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
        if (OPENCV_READY && !opencvRuntimeDisabled) {
            try {
                return processWithOpenCv(width, height, argb, title, options);
            } catch (Throwable t) {
                opencvRuntimeDisabled = true;
                return processLegacy(width, height, argb, title, options, stackTraceToString(t));
            }
        }

        String reason = OPENCV_READY ? "OpenCV runtime disabled after previous failure" : OPENCV_INIT_STACKTRACE;
        return processLegacy(width, height, argb, title, options, reason);
    }

    private ProcessingResult processLegacy(int w, int h, int[] argb, String title, ProcessingOptions options) {
        return processLegacy(w, h, argb, title, options, null);
    }

    private ProcessingResult processLegacy(int w, int h, int[] argb, String title, ProcessingOptions options, String openCvStackTrace) {
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
        return new ProcessingResult(piece, staffRows, barlines, perpendicular, debugOverlay, new ArrayList<StaffCorridor>(), "legacy", false, openCvStackTrace, null);
    }

    private ProcessingResult processWithOpenCv(int w, int h, int[] argb, String title, ProcessingOptions options) {
        ScorePiece piece = new ScorePiece();
        piece.title = title;
        Mat gray = null;
        Mat contrast = null;
        CLAHE clahe = null;
        Mat norm = null;
        Mat binary = null;
        Mat staffMask = null;
        Mat symbolMask = null;
        Mat kernel = null;
        try {
            gray = bitmapToGrayMat(argb, w, h);

            binary = new Mat();
            if (options.skipAdaptiveBinarization) {
                Imgproc.threshold(gray, binary, 0, 255, Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU);
            } else {
                contrast = new Mat();
                clahe = Imgproc.createCLAHE(2.4, new Size(8, 8));
                clahe.apply(gray, contrast);

                norm = new Mat();
                Imgproc.medianBlur(contrast, norm, 3);

                int blockSize = Math.max(15, (Math.min(w, h) / 20) | 1);
                double c = options.thresholdOffset;
                Imgproc.adaptiveThreshold(norm, binary, 255,
                        Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                        Imgproc.THRESH_BINARY_INV,
                        blockSize,
                        c);
            }

            int staffSpacing = estimateStaffSpacingOpenCv(binary);
            staffMask = detectStaffMaskOpenCv(binary, staffSpacing);
            List<StaffGroup> staffGroups = extractStaffGroups(staffMask, staffSpacing);
            rebuildStaffMaskFromGroups(staffMask, staffGroups, w, h);
            symbolMask = new Mat();
            Core.subtract(binary, staffMask, symbolMask);

            if (!options.skipMorphNoiseSuppression) {
                int morphK = options.noiseLevel >= 0.66f ? 3 : 2;
                kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(morphK, morphK));
                Imgproc.morphologyEx(symbolMask, symbolMask, Imgproc.MORPH_OPEN, kernel);
                Imgproc.morphologyEx(symbolMask, symbolMask, Imgproc.MORPH_CLOSE, kernel);
            }
            applyStaffCorridorMask(symbolMask, staffGroups, w, h);

            NoteDetectionDiagnostics noteDiagnostics = new NoteDetectionDiagnostics();
            List<Blob> noteHeads = detectNoteHeadsOpenCv(symbolMask, w, h, staffSpacing, options, staffGroups, noteDiagnostics);
            fillNotesWithDurationFeatures(piece, noteHeads, symbolMask, binary, staffMask, staffSpacing, w, h, staffGroups, options);

            int staffRows = Math.max(1, Math.min(10, staffGroups.size()));
            int barlines = estimateBarsFromMask(binary, w, h, staffSpacing);
            int perpendicular = estimatePerpendicular(argb, w, h);
            List<StaffCorridor> corridors = buildStaffCorridors(staffGroups, w, h);

            Bitmap debugOverlay = safeBuildDebugOverlayFromMats(binary, staffMask, symbolMask, w, h);
            return new ProcessingResult(piece, staffRows, barlines, perpendicular, debugOverlay, corridors, "opencv", true, null, noteDiagnostics);
        } finally {
            if (gray != null) gray.release();
            if (contrast != null) contrast.release();
            if (clahe != null) clahe.collectGarbage();
            if (norm != null) norm.release();
            if (binary != null) binary.release();
            if (staffMask != null) staffMask.release();
            if (symbolMask != null) symbolMask.release();
            if (kernel != null) kernel.release();
        }
    }

    private static void verifyOpenCvNativeBinding() {
        try {
            Core.getVersionString();
        } catch (UnsatisfiedLinkError first) {
            UnsatisfiedLinkError last = first;
            for (String lib : Arrays.asList("opencv_java4", "opencv_java3", "opencv_java")) {
                try {
                    System.loadLibrary(lib);
                    Core.getVersionString();
                    return;
                } catch (UnsatisfiedLinkError ignored) {
                    last = ignored;
                }
            }
            throw last;
        }
    }

    private static String stackTraceToString(Throwable t) {
        if (t == null) {
            return null;
        }
        try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            t.printStackTrace(pw);
            pw.flush();
            return sw.toString();
        } catch (Throwable ignored) {
            return t.toString();
        }
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
        int commonStart = w - 1;
        int longestLen = 0;
        for (StaffGroup g : groups) {
            int s = Math.max(0, g.xStart);
            int e = Math.min(w - 1, g.xEnd);
            commonStart = Math.min(commonStart, s);
            longestLen = Math.max(longestLen, Math.max(1, e - s + 1));
        }
        if (commonStart < 0 || commonStart >= w) {
            commonStart = 0;
        }
        if (longestLen <= 0) {
            longestLen = w;
        }
        int commonEnd = Math.min(w - 1, commonStart + longestLen - 1);
        for (StaffGroup g : groups) {
            g.xStart = commonStart;
            g.xEnd = commonEnd;
            int xs = commonStart;
            int xe = commonEnd;
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

    private void applyStaffCorridorMask(Mat symbolMask, List<StaffGroup> groups, int w, int h) {
        if (groups == null || groups.isEmpty()) {
            return;
        }

        Mat corridorMask = Mat.zeros(h, w, CvType.CV_8UC1);
        double[] full = new double[]{255};
        for (StaffGroup g : groups) {
            int xPad = Math.max(6, Math.round(g.spacing * 2.0f));
            int yPad = Math.max(6, Math.round(g.spacing * 2.0f));
            int x0 = Math.max(0, g.xStart - xPad);
            int x1 = Math.min(w - 1, g.xEnd + xPad);
            int y0 = Math.max(0, Math.round(g.top() - yPad));
            int y1 = Math.min(h - 1, Math.round(g.bottom() + yPad));
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    corridorMask.put(y, x, full);
                }
            }
        }

        Core.bitwise_and(symbolMask, corridorMask, symbolMask);
        corridorMask.release();
    }

    private List<StaffCorridor> buildStaffCorridors(List<StaffGroup> groups, int w, int h) {
        List<StaffCorridor> out = new ArrayList<StaffCorridor>();
        if (groups == null || groups.isEmpty()) {
            return out;
        }
        for (StaffGroup g : groups) {
            int xPad = Math.max(6, Math.round(g.spacing * 2.0f));
            int yPad = Math.max(6, Math.round(g.spacing * 2.0f));
            float x0 = Math.max(0, g.xStart - xPad) / (float) Math.max(1, w - 1);
            float x1 = Math.min(w - 1, g.xEnd + xPad) / (float) Math.max(1, w - 1);
            float y0 = Math.max(0, Math.round(g.top() - yPad)) / (float) Math.max(1, h - 1);
            float y1 = Math.min(h - 1, Math.round(g.bottom() + yPad)) / (float) Math.max(1, h - 1);
            out.add(new StaffCorridor(x0, y0, x1, y1));
        }
        return out;
    }

    private List<Blob> detectNoteHeadsOpenCv(Mat symbolMask, int w, int h, int staffSpacing, ProcessingOptions options, List<StaffGroup> groups, NoteDetectionDiagnostics diagnostics) {
        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        Mat hierarchy = new Mat();
        Mat contoursInput = symbolMask.clone();
        try {
            Imgproc.findContours(contoursInput, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        List<Blob> out = new ArrayList<Blob>();
        if (diagnostics != null) diagnostics.totalContours = contours.size();
        float recallScale = options.recallFirstMode ? 0.75f : 1.0f;
        int minArea = Math.max(6, (int) ((staffSpacing * staffSpacing / 8f) * options.noteMinAreaFactor * recallScale));
        int maxArea = Math.max(1200, (int) (staffSpacing * staffSpacing * options.noteMaxAreaFactor * (options.recallFirstMode ? 1.25f : 1.0f)));
        float minSize = Math.max(3f, staffSpacing * (options.recallFirstMode ? 0.28f : 0.35f));
        float maxSize = Math.max(10f, staffSpacing * (options.recallFirstMode ? 2.9f : 2.4f));
        for (MatOfPoint c : contours) {
            double area = Imgproc.contourArea(c);
            Rect r = Imgproc.boundingRect(c);
            float cx = r.x + r.width * 0.5f;
            float cy = r.y + r.height * 0.5f;

            boolean rejected = false;
            boolean areaRejected = area < minArea || area > maxArea;
            if (areaRejected) {
                if (diagnostics != null) diagnostics.rejectedByArea++;
                rejected = true;
            }
            if (r.width < 3 || r.height < 3 || r.width > w / 6 || r.height > h / 5) {
                if (diagnostics != null) diagnostics.rejectedByBounds++;
                rejected = true;
            }
            if (r.width < minSize || r.height < minSize || r.width > maxSize || r.height > maxSize) {
                if (diagnostics != null) diagnostics.rejectedBySize++;
                rejected = true;
            }

            float ratio = (float) r.width / (float) Math.max(1, r.height);
            float minRatio = options.recallFirstMode ? 0.35f : 0.5f;
            float maxRatio = options.recallFirstMode ? 2.8f : 2.0f;
            if (ratio < minRatio || ratio > maxRatio) {
                if (diagnostics != null) diagnostics.rejectedByAspect++;
                rejected = true;
            }

            float fill = (float) (area / Math.max(1.0, r.width * r.height));
            float effectiveMinFill = options.recallFirstMode ? options.noteMinFill : Math.max(0.22f, options.noteMinFill);
            float effectiveMaxFill = options.recallFirstMode ? options.noteMaxFill : Math.min(0.90f, options.noteMaxFill);
            if (fill < effectiveMinFill || fill > effectiveMaxFill) {
                if (diagnostics != null) diagnostics.rejectedByFill++;
                rejected = true;
            }

            org.opencv.core.MatOfPoint2f curve = new org.opencv.core.MatOfPoint2f(c.toArray());
            double perimeter = Imgproc.arcLength(curve, true);
            curve.release();
            if (perimeter <= 0.0) {
                if (diagnostics != null) diagnostics.rejectedByPerimeter++;
                rejected = true;
            }

            double circularity = perimeter <= 0.0 ? 0.0 : (4.0 * Math.PI * area) / (perimeter * perimeter);
            float minCircularity = options.recallFirstMode ? Math.max(0.08f, options.noteMinCircularity * 0.65f) : options.noteMinCircularity;
            if (circularity < minCircularity || circularity > 1.5) {
                if (diagnostics != null) diagnostics.rejectedByCircularity++;
                rejected = true;
            }

            if (!isAllowedNotePosition(cx, cy, groups)) {
                if (diagnostics != null) diagnostics.rejectedByStaffPosition++;
                rejected = true;
            }
            if (options.recallFirstMode && rejected && isGapSizedBlobCandidate(r, area, fill, cx, cy, staffSpacing, groups)) {
                rejected = false;
                if (diagnostics != null) diagnostics.rescuedByGapSizedBlob++;
            }
            if (rejected) {
                c.release();
                continue;
            }

            Blob b = new Blob(r.x, r.y);
            b.maxX = r.x + r.width - 1;
            b.maxY = r.y + r.height - 1;
            b.area = (int) Math.max(1, Math.round(area));
            Moments moments = Imgproc.moments(c);
            float centroidX = moments.get_m00() == 0.0 ? (float) (r.x + r.width * 0.5f) : (float) (moments.get_m10() / moments.get_m00());
            float centroidY = moments.get_m00() == 0.0 ? (float) (r.y + r.height * 0.5f) : (float) (moments.get_m01() / moments.get_m00());
            b.sumX = (int) Math.round(centroidX * b.area);
            b.sumY = (int) Math.round(centroidY * b.area);
            out.add(b);
            c.release();
        }
            Collections.sort(out, new Comparator<Blob>() {
            @Override
            public int compare(Blob a, Blob b) {
                if (a.minX == b.minX) return a.minY - b.minY;
                return a.minX - b.minX;
            }
        });
            if (diagnostics != null) diagnostics.keptBeforeDedupe = out.size();
            List<Blob> deduped = dedupeNoteHeads(out, groups, staffSpacing, diagnostics);
            return filterAnalyticallyNonNoteLike(deduped, staffSpacing, groups, options, diagnostics);
        } finally {
            contoursInput.release();
            hierarchy.release();
        }
    }


    private boolean isGapSizedBlobCandidate(Rect r,
                                            double area,
                                            float fill,
                                            float cx,
                                            float cy,
                                            int staffSpacing,
                                            List<StaffGroup> groups) {
        float minDim = Math.max(3f, staffSpacing * 0.45f);
        float maxDim = Math.max(8f, staffSpacing * 1.90f);
        if (r.width < minDim || r.width > maxDim || r.height < minDim || r.height > maxDim) {
            return false;
        }
        float ratio = (float) r.width / (float) Math.max(1, r.height);
        if (ratio < 0.55f || ratio > 2.20f) {
            return false;
        }
        float minArea = Math.max(6f, staffSpacing * staffSpacing * 0.12f);
        float maxArea = Math.max(36f, staffSpacing * staffSpacing * 2.80f);
        if (area < minArea || area > maxArea) {
            return false;
        }
        if (fill < 0.20f || fill > 0.90f) {
            return false;
        }
        return isAllowedNotePositionRelaxed(cx, cy, groups);
    }

    private boolean isAllowedNotePositionRelaxed(float cx, float cy, List<StaffGroup> groups) {
        StaffGroup g = nearestGroupForPoint(cx, cy, groups);
        if (g == null) return false;
        float xMargin = Math.max(2f, g.spacing * 0.9f);
        if (cx < g.xStart + xMargin || cx > g.xEnd - xMargin) return false;
        float minY = g.top() - g.spacing * 2.2f;
        float maxY = g.bottom() + g.spacing * 2.2f;
        if (cy < minY || cy > maxY) return false;

        float halfStep = g.spacing / 2f;
        float nearest = Float.MAX_VALUE;
        for (int i = 0; i < 5; i++) {
            nearest = Math.min(nearest, Math.abs(cy - g.linesY[i]));
        }
        for (int i = 0; i < 4; i++) {
            float gapY = (g.linesY[i] + g.linesY[i + 1]) * 0.5f;
            nearest = Math.min(nearest, Math.abs(cy - gapY));
        }
        return nearest <= Math.max(2f, halfStep * 1.9f);
    }


    private List<Blob> filterAnalyticallyNonNoteLike(List<Blob> in,
                                                     int staffSpacing,
                                                     List<StaffGroup> groups,
                                                     ProcessingOptions options,
                                                     NoteDetectionDiagnostics diagnostics) {
        if (in.isEmpty()) return in;
        List<Blob> out = new ArrayList<Blob>();
        float minScore = options.recallFirstMode ? 3.6f : 4.0f;
        minScore += (options.analyticalFilterStrength - 0.55f) * 1.8f;

        for (Blob b : in) {
            float strength = analyticalStrengthForPoint(b.cx(), b.cy(), options, groups);
            float score = 0f;
            float bw = b.width();
            float bh = b.height();
            float ratio = bw / Math.max(1f, bh);
            float areaNorm = b.area / Math.max(1f, (float) (staffSpacing * staffSpacing));
            float nearestStaffDist = nearestStaffStepDistance(b.cx(), b.cy(), groups, staffSpacing);

            if (ratio >= 0.45f && ratio <= 2.4f) score += 1f;
            if (areaNorm >= 0.10f && areaNorm <= 2.8f) score += 1f;
            if (Math.max(bw, bh) <= staffSpacing * 2.2f) score += 1f;
            if (Math.min(bw, bh) >= Math.max(2f, staffSpacing * 0.30f)) score += 1f;
            if (nearestStaffDist <= Math.max(2f, staffSpacing * 0.45f)) score += 1f;

            float localMinScore = minScore + (strength - options.analyticalFilterStrength) * 1.8f;
            boolean hardReject = ratio > 3.2f || ratio < 0.28f || areaNorm > 3.8f || areaNorm < 0.05f;
            if (!hardReject && score >= localMinScore) {
                out.add(b);
            } else if (diagnostics != null) {
                diagnostics.filteredAsNonNoteByAnalyticalPass++;
            }
        }
        if (diagnostics != null) diagnostics.finalKept = out.size();
        return out;
    }

    private float nearestStaffStepDistance(float cx, float cy, List<StaffGroup> groups, int staffSpacing) {
        StaffGroup g = nearestGroupForPoint(cx, cy, groups);
        if (g == null) return staffSpacing * 2f;
        float nearest = Float.MAX_VALUE;
        for (int i = 0; i < 5; i++) nearest = Math.min(nearest, Math.abs(cy - g.linesY[i]));
        for (int i = 0; i < 4; i++) {
            float gapY = (g.linesY[i] + g.linesY[i + 1]) * 0.5f;
            nearest = Math.min(nearest, Math.abs(cy - gapY));
        }
        return nearest;
    }


    private float analyticalStrengthForPoint(float cx, float cy, ProcessingOptions options, List<StaffGroup> groups) {
        float base = options.analyticalFilterStrength;
        if (options.perStaffAnalyticalStrength == null || options.perStaffAnalyticalStrength.length == 0) {
            return base;
        }
        StaffGroup g = nearestGroupForPoint(cx, cy, groups);
        int idx = indexOfGroup(groups, g);
        if (idx < 0 || idx >= options.perStaffAnalyticalStrength.length) {
            return base;
        }
        float v = options.perStaffAnalyticalStrength[idx];
        return Math.max(0f, Math.min(1f, v));
    }

    private List<Blob> dedupeNoteHeads(List<Blob> in, List<StaffGroup> groups, int staffSpacing, NoteDetectionDiagnostics diagnostics) {
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
                    if (diagnostics != null) diagnostics.removedByCenterDistanceDedupe++;
                    break;
                }
            }
            if (!overlap) keep.add(b);
        }

        java.util.HashMap<String, Blob> perSlot = new java.util.HashMap<String, Blob>();
        for (Blob b : keep) {
            StaffGroup g = nearestGroupForPoint(b.cx(), b.cy(), groups);
            int groupIdx = indexOfGroup(groups, g);
            float slotW = Math.max(6f, staffSpacing * 1.25f);
            int xSlot = Math.round(b.cx() / slotW);
            String key = groupIdx + ":" + xSlot;
            Blob prev = perSlot.get(key);
            if (prev == null || noteHeadSlotScore(b, g, staffSpacing, groups) > noteHeadSlotScore(prev, g, staffSpacing, groups)) {
                if (prev != null && diagnostics != null) diagnostics.removedBySlotDedupe++;
                perSlot.put(key, b);
            } else if (diagnostics != null) {
                diagnostics.removedBySlotDedupe++;
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
        if (diagnostics != null) diagnostics.finalKept = out.size();
        return out;
    }


    private float noteHeadSlotScore(Blob b, StaffGroup g, int staffSpacing, List<StaffGroup> groups) {
        float spacing = g == null ? Math.max(6f, staffSpacing) : Math.max(4f, g.spacing);
        float areaNorm = b.area / Math.max(1f, spacing * spacing);
        float ratio = b.width() / Math.max(1f, (float) b.height());
        float nearestStepNorm = nearestStaffStepDistance(b.cx(), b.cy(), groups, staffSpacing) / Math.max(1f, spacing * 0.5f);

        float areaScore = 1.2f - Math.abs(areaNorm - 0.75f);
        float ratioScore = 1.1f - Math.abs(ratio - 1.1f);
        float stepScore = 1.0f - nearestStepNorm;
        float compactScore = Math.min(b.width(), b.height()) / Math.max(1f, Math.max(b.width(), b.height()));

        return areaScore + ratioScore + stepScore + compactScore;
    }

    private int indexOfGroup(List<StaffGroup> groups, StaffGroup group) {
        if (group == null) return -1;
        for (int i = 0; i < groups.size(); i++) {
            if (groups.get(i) == group) return i;
        }
        return -1;
    }

    private static class NoteHeadFeatures {
        Blob blob;
        StaffGroup group;
        float xNorm;
        float yNorm;
        String duration;
        int stepFromBottom;
    }

    private void fillNotesWithDurationFeatures(ScorePiece piece,
                                               List<Blob> noteHeads,
                                               Mat symbolMask,
                                               Mat binaryMask,
                                               Mat staffMask,
                                               int staffSpacing,
                                               int w,
                                               int h,
                                               List<StaffGroup> groups,
                                               ProcessingOptions options) {
        if (noteHeads.isEmpty()) return;
        List<Blob> orderedHeads = sortNoteHeadsReadingOrder(noteHeads, groups);
        List<NoteHeadFeatures> features = new ArrayList<NoteHeadFeatures>();

        for (Blob b : orderedHeads) {
            StaffGroup group = nearestGroupForPoint(b.cx(), b.cy(), groups);
            boolean hollow = isHollowHead(symbolMask, b);
            int stemCount = detectStemCount(symbolMask, b, group, staffSpacing);
            int flagCount = detectFlagCount(symbolMask, b, group, staffSpacing);

            NoteHeadFeatures f = new NoteHeadFeatures();
            f.blob = b;
            f.group = group;
            f.xNorm = b.cx() / (float) Math.max(1, w - 1);
            f.yNorm = b.cy() / (float) Math.max(1, h - 1);
            f.duration = resolveDuration(hollow, stemCount, flagCount);
            f.stepFromBottom = 0;
            features.add(f);
        }

        refinePitchStepsSecondPass(features, binaryMask, staffMask, staffSpacing, options);

        int measureSize = 4;
        for (int i = 0; i < features.size(); i++) {
            NoteHeadFeatures f = features.get(i);
            int midi = midiForTrebleStaffStep(f.stepFromBottom);
            piece.notes.add(new NoteEvent(
                    noteNameForMidi(midi),
                    octaveForMidi(midi),
                    f.duration,
                    1 + (i / measureSize),
                    f.xNorm,
                    f.yNorm
            ));
        }
    }




    private void refinePitchStepsSecondPass(List<NoteHeadFeatures> features,
                                            Mat binaryMask,
                                            Mat staffMask,
                                            int staffSpacing,
                                            ProcessingOptions options) {
        for (NoteHeadFeatures f : features) {
            if (f.group == null) {
                f.stepFromBottom = 0;
                continue;
            }
            boolean useStripeRefinement = options != null && options.lineStripePitchRefinement;
            if (!useStripeRefinement) {
                f.stepFromBottom = Math.round((f.group.linesY[4] - f.blob.cy()) / (f.group.spacing / 2f));
                continue;
            }
            f.stepFromBottom = refinedStepByKnownHeadPosition(f.blob, f.group, binaryMask, staffMask, staffSpacing);
        }
    }

    private int refinedStepByKnownHeadPosition(Blob b,
                                                StaffGroup group,
                                                Mat binaryMask,
                                                Mat staffMask,
                                                int staffSpacing) {
        float[] localLines = localStaffLinesAtX(staffMask, group, Math.round(b.cx()), staffSpacing);
        if (localLines == null || localLines.length < 5) {
            localLines = localStaffLinesAtX(binaryMask, group, Math.round(b.cx()), staffSpacing);
        }
        if (localLines == null || localLines.length < 5) {
            localLines = group.linesY;
        }

        float cy = b.cy();
        int nearestLine = 0;
        float nearestLineDist = Float.MAX_VALUE;
        for (int i = 0; i < 5; i++) {
            float d = Math.abs(cy - localLines[i]);
            if (d < nearestLineDist) {
                nearestLineDist = d;
                nearestLine = i;
            }
        }

        float headBand = Math.max(1.8f, Math.min(Math.max(2f, group.spacing * 0.48f), b.height() * 0.42f));
        boolean onLineByCenter = nearestLineDist <= headBand;
        boolean onLineByInk = hasBlackOnBothSidesOfLine(binaryMask, b, localLines[nearestLine], group.spacing, staffSpacing);

        int posIndex;
        if (onLineByCenter && onLineByInk) {
            posIndex = nearestLine * 2;
        } else {
            int connectivitySide = detectGapSideByWhiteConnectivity(binaryMask, b, localLines[nearestLine], staffSpacing);
            if (connectivitySide < 0) {
                posIndex = nearestLine * 2 - 1;
            } else if (connectivitySide > 0) {
                posIndex = nearestLine * 2 + 1;
            } else {
                float delta = cy - localLines[nearestLine];
                if (nearestLine <= 0 && delta < 0f) {
                    posIndex = -1;
                } else if (nearestLine >= 4 && delta > 0f) {
                    posIndex = 9;
                } else {
                    posIndex = delta < 0f ? (nearestLine * 2 - 1) : (nearestLine * 2 + 1);
                }
            }
        }
        return 8 - posIndex;
    }


    private int detectGapSideByWhiteConnectivity(Mat binaryMask, Blob b, float lineY, int staffSpacing) {
        if (binaryMask == null || binaryMask.empty()) return 0;

        int xPad = Math.max(2, staffSpacing / 3);
        int yPad = Math.max(2, staffSpacing / 3);
        int x0 = Math.max(0, b.minX - xPad);
        int x1 = Math.min(binaryMask.cols() - 1, b.maxX + xPad);
        int line = Math.max(1, Math.min(binaryMask.rows() - 2, Math.round(lineY)));

        int aboveY0 = Math.max(0, line - yPad);
        int aboveY1 = Math.max(aboveY0, line - 1);
        int belowY0 = Math.min(binaryMask.rows() - 1, line + 1);
        int belowY1 = Math.min(binaryMask.rows() - 1, line + yPad);

        boolean aboveConnected = hasWhiteConnectionAcrossBand(binaryMask, x0, x1, aboveY0, aboveY1);
        boolean belowConnected = hasWhiteConnectionAcrossBand(binaryMask, x0, x1, belowY0, belowY1);

        if (aboveConnected && !belowConnected) return -1;
        if (!aboveConnected && belowConnected) return 1;
        return 0;
    }

    private boolean hasWhiteConnectionAcrossBand(Mat mask, int x0, int x1, int y0, int y1) {
        if (mask == null || mask.empty() || x1 <= x0 || y1 < y0) return false;

        int rx0 = Math.max(0, x0);
        int rx1 = Math.min(mask.cols() - 1, x1);
        int ry0 = Math.max(0, y0);
        int ry1 = Math.min(mask.rows() - 1, y1);
        if (rx1 <= rx0 || ry1 < ry0) return false;

        Mat roi = mask.submat(ry0, ry1 + 1, rx0, rx1 + 1);
        Mat whiteMask = new Mat();
        Mat labels = new Mat();
        Mat stats = new Mat();
        Mat centroids = new Mat();
        try {
            Imgproc.threshold(roi, whiteMask, 1, 255, Imgproc.THRESH_BINARY_INV);
            int n = Imgproc.connectedComponentsWithStats(whiteMask, labels, stats, centroids, CvType.CV_32S);
            int width = whiteMask.cols();
            for (int label = 1; label < n; label++) {
                int left = (int) stats.get(label, Imgproc.CC_STAT_LEFT)[0];
                int compWidth = (int) stats.get(label, Imgproc.CC_STAT_WIDTH)[0];
                int right = left + compWidth - 1;
                if (left <= 0 && right >= width - 1) {
                    return true;
                }
            }
            return false;
        } finally {
            roi.release();
            whiteMask.release();
            labels.release();
            stats.release();
            centroids.release();
        }
    }

    private int refinedStepFromBottomByLineStripe(Mat binaryMask, Blob b, StaffGroup g, int staffSpacing) {
        float halfStep = Math.max(2f, g.spacing / 2f);
        float[] localLines = localStaffLinesAtX(binaryMask, g, Math.round(b.cx()), staffSpacing);
        if (localLines == null || localLines.length < 5) {
            localLines = g.linesY;
        }

        int bestIndex = -1;
        float bestDistance = Float.MAX_VALUE;

        for (int i = 0; i < 5; i++) {
            float lineY = localLines[i];
            float d = Math.abs(b.cy() - lineY);
            if (d < bestDistance) {
                bestDistance = d;
                bestIndex = i * 2;
            }
            if (i < 4) {
                float gapY = (localLines[i] + localLines[i + 1]) * 0.5f;
                float gd = Math.abs(b.cy() - gapY);
                if (gd < bestDistance) {
                    bestDistance = gd;
                    bestIndex = i * 2 + 1;
                }
            }
        }

        if (bestIndex < 0) {
            return Math.round((localLines[4] - b.cy()) / Math.max(1f, halfStep));
        }

        boolean lineCandidate = (bestIndex % 2 == 0);
        int lineIdx = bestIndex / 2;
        if (lineCandidate && hasBlackOnBothSidesOfLine(binaryMask, b, localLines[lineIdx], g.spacing, staffSpacing)) {
            return 8 - bestIndex;
        }

        if (lineCandidate) {
            int preferGapPosIndex = chooseNearestGapPositionIndexForLine(lineIdx, b.cy(), localLines);
            return 8 - preferGapPosIndex;
        }

        return 8 - bestIndex;
    }


    private float[] localStaffLinesAtX(Mat binaryMask, StaffGroup g, int centerX, int staffSpacing) {
        if (binaryMask == null || binaryMask.empty() || g == null) {
            return g == null ? null : g.linesY;
        }
        float[] out = new float[5];
        int xHalf = Math.max(2, Math.round(Math.max(staffSpacing, g.spacing) * 0.9f));
        int x0 = Math.max(0, centerX - xHalf);
        int x1 = Math.min(binaryMask.cols() - 1, centerX + xHalf);
        int yHalf = Math.max(2, Math.round(Math.max(staffSpacing, g.spacing) * 0.55f));

        for (int i = 0; i < 5; i++) {
            int baseY = Math.round(g.linesY[i]);
            int ys = Math.max(0, baseY - yHalf);
            int ye = Math.min(binaryMask.rows() - 1, baseY + yHalf);
            int bestY = baseY;
            int bestScore = -1;
            for (int y = ys; y <= ye; y++) {
                int dark = 0;
                for (int x = x0; x <= x1; x++) {
                    double[] px = binaryMask.get(y, x);
                    if (px != null && px.length > 0 && px[0] > 0) {
                        dark++;
                    }
                }
                if (dark > bestScore) {
                    bestScore = dark;
                    bestY = y;
                }
            }
            out[i] = bestY;
        }

        for (int i = 1; i < out.length; i++) {
            if (out[i] <= out[i - 1]) {
                out[i] = out[i - 1] + Math.max(1f, g.spacing * 0.45f);
            }
        }
        return out;
    }

    private int chooseNearestGapPositionIndexForLine(int lineIdx, float cy, float[] linesY) {
        if (lineIdx <= 0) {
            float topLineY = linesY[0];
            // For edge ledger notes above the top line keep the outer gap index (-1), not an interior gap.
            return cy < topLineY ? -1 : 1;
        }
        if (lineIdx >= 4) {
            float bottomLineY = linesY[4];
            // For edge ledger notes below the bottom line keep the outer gap index (9), not an interior gap.
            return cy > bottomLineY ? 9 : 7;
        }
        float upperGapY = (linesY[lineIdx - 1] + linesY[lineIdx]) * 0.5f;
        float lowerGapY = (linesY[lineIdx] + linesY[lineIdx + 1]) * 0.5f;
        int upperGapPosIndex = (lineIdx - 1) * 2 + 1;
        int lowerGapPosIndex = lineIdx * 2 + 1;
        return Math.abs(cy - upperGapY) <= Math.abs(cy - lowerGapY) ? upperGapPosIndex : lowerGapPosIndex;
    }

    private boolean hasBlackOnBothSidesOfLine(Mat binaryMask, Blob b, float lineY, float spacing, int staffSpacing) {
        if (binaryMask == null || binaryMask.empty()) {
            return false;
        }
        int y = Math.max(1, Math.min(binaryMask.rows() - 2, Math.round(lineY)));
        int interline = Math.max(2, Math.round(Math.max(spacing, staffSpacing)));
        int corridorHalfH = Math.max(1, interline / 2);
        int corridorWidth = Math.max(3, interline);
        int x0 = Math.max(0, Math.round(b.cx()) - corridorWidth / 2);
        int x1 = Math.min(binaryMask.cols() - 1, Math.round(b.cx()) + corridorWidth / 2);

        int yTop = Math.max(0, y - corridorHalfH);
        int yBottom = Math.min(binaryMask.rows() - 1, y + corridorHalfH);
        int minBodyHeight = Math.max(1, Math.round(interline * 0.25f));

        boolean upperBody = hasLargeBlackBody(binaryMask, x0, x1, yTop, y - 1, minBodyHeight);
        boolean lowerBody = hasLargeBlackBody(binaryMask, x0, x1, y + 1, yBottom, minBodyHeight);
        return upperBody && lowerBody;
    }

    private boolean hasLargeBlackBody(Mat mask,
                                      int x0,
                                      int x1,
                                      int y0,
                                      int y1,
                                      int minBodyHeight) {
        if (y1 < y0 || x1 < x0) return false;
        int width = x1 - x0 + 1;
        int minRowHits = Math.max(1, width / 6);
        int run = 0;
        int bestRun = 0;
        for (int y = y0; y <= y1; y++) {
            int dark = 0;
            for (int x = x0; x <= x1; x++) {
                double[] px = mask.get(y, x);
                if (px != null && px.length > 0 && px[0] > 0) {
                    dark++;
                }
            }
            if (dark >= minRowHits) {
                run++;
                if (run > bestRun) bestRun = run;
            } else {
                run = 0;
            }
        }
        return bestRun >= minBodyHeight;
    }

    private List<Blob> sortNoteHeadsReadingOrder(List<Blob> noteHeads, final List<StaffGroup> groups) {
        List<Blob> ordered = new ArrayList<Blob>(noteHeads);
        Collections.sort(ordered, new Comparator<Blob>() {
            @Override
            public int compare(Blob a, Blob b) {
                StaffGroup ga = nearestGroupForPoint(a.cx(), a.cy(), groups);
                StaffGroup gb = nearestGroupForPoint(b.cx(), b.cy(), groups);
                int ia = indexOfGroup(groups, ga);
                int ib = indexOfGroup(groups, gb);
                if (ia < 0) ia = Integer.MAX_VALUE / 4;
                if (ib < 0) ib = Integer.MAX_VALUE / 4;
                if (ia != ib) {
                    return ia - ib;
                }
                if (a.minX != b.minX) {
                    return a.minX - b.minX;
                }
                return a.minY - b.minY;
            }
        });
        return ordered;
    }

    private boolean isAllowedNotePosition(float cx, float cy, List<StaffGroup> groups) {
        StaffGroup g = nearestGroupForPoint(cx, cy, groups);
        if (g == null) return false;
        float xMargin = Math.max(2f, g.spacing * 0.7f);
        if (cx < g.xStart + xMargin || cx > g.xEnd - xMargin) return false;
        float minY = g.top() - g.spacing * 1.6f;
        float maxY = g.bottom() + g.spacing * 1.6f;
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
        float above1 = g.linesY[0] - g.spacing * 1.0f;
        float above2 = g.linesY[0] - g.spacing * 2.0f;
        float below1 = g.linesY[4] + g.spacing * 1.0f;
        float below2 = g.linesY[4] + g.spacing * 2.0f;
        nearest = Math.min(nearest, Math.abs(cy - above1));
        nearest = Math.min(nearest, Math.abs(cy - above2));
        nearest = Math.min(nearest, Math.abs(cy - below1));
        nearest = Math.min(nearest, Math.abs(cy - below2));

        return nearest <= Math.max(2f, halfStep * 1.30f);
    }

    private StaffGroup nearestGroupFor(float cx, List<StaffGroup> groups) {
        return nearestGroupForPoint(cx, Float.NaN, groups);
    }

    private StaffGroup nearestGroupForPoint(float cx, float cy, List<StaffGroup> groups) {
        StaffGroup best = null;
        float bestDist = Float.MAX_VALUE;
        for (StaffGroup g : groups) {
            float spanMargin = Math.max(12f, g.spacing * 6f);
            if (cx < g.xStart - spanMargin || cx > g.xEnd + spanMargin) {
                continue;
            }
            float xDist;
            if (cx < g.xStart) {
                xDist = g.xStart - cx;
            } else if (cx > g.xEnd) {
                xDist = cx - g.xEnd;
            } else {
                xDist = 0f;
            }
            float yDist = 0f;
            if (!Float.isNaN(cy)) {
                if (cy < g.top()) {
                    yDist = g.top() - cy;
                } else if (cy > g.bottom()) {
                    yDist = cy - g.bottom();
                }
            }
            float d = xDist + (yDist * 1.8f);
            if (d < bestDist) {
                bestDist = d;
                best = g;
            }
        }
        return best;
    }

    private int midiForTrebleStaffStep(int stepFromBottom) {
        String[] naturalCycle = new String[]{"C", "D", "E", "F", "G", "A", "B"};
        int baseIndex = 2; // E
        int noteIndex = baseIndex + stepFromBottom;
        int octaveShift = Math.floorDiv(noteIndex, 7);
        int idx = ((noteIndex % 7) + 7) % 7;
        int octave = 4 + octaveShift;
        return MusicNotation.midiFor(naturalCycle[idx], octave);
    }

    private String noteNameForMidi(int midi) {
        String[] names = new String[]{"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
        int semitone = ((midi % 12) + 12) % 12;
        return names[semitone];
    }

    private int octaveForMidi(int midi) {
        return (midi / 12) - 1;
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

    private int detectStemCount(Mat symbolMask, Blob b, StaffGroup group, int staffSpacing) {
        int searchPad = Math.max(2, staffSpacing / 2);
        int x0 = Math.max(0, b.minX - searchPad);
        int x1 = Math.min(symbolMask.cols() - 1, b.maxX + searchPad);
        int y0;
        int y1;
        if (group != null) {
            int pad = Math.max(staffSpacing, Math.round(group.spacing * 1.4f));
            y0 = Math.max(0, Math.round(group.top()) - pad);
            y1 = Math.min(symbolMask.rows() - 1, Math.round(group.bottom()) + pad);
        } else {
            y0 = Math.max(0, b.minY - staffSpacing * 3);
            y1 = Math.min(symbolMask.rows() - 1, b.maxY + staffSpacing * 3);
        }

        int minRun = Math.max(staffSpacing + 2, (int) (b.height() + staffSpacing * 0.6f));
        int bestColRun = 0;
        int supportCols = 0;
        for (int x = x0; x <= x1; x++) {
            int run = 0;
            int best = 0;
            for (int y = y0; y <= y1; y++) {
                if (symbolMask.get(y, x)[0] > 0) {
                    run++;
                    if (run > best) best = run;
                } else {
                    run = 0;
                }
            }
            if (best > bestColRun) bestColRun = best;
            if (best >= minRun) supportCols++;
        }

        return (bestColRun >= minRun && supportCols >= 2) ? 1 : 0;
    }

    private int detectFlagCount(Mat symbolMask, Blob b, StaffGroup group, int staffSpacing) {
        if (group == null) {
            return 0;
        }

        int searchPad = Math.max(2, staffSpacing / 2);
        int x0 = Math.max(0, b.minX - searchPad);
        int x1 = Math.min(symbolMask.cols() - 1, b.maxX + searchPad);
        int pad = Math.max(staffSpacing, Math.round(group.spacing * 1.4f));
        int y0 = Math.max(0, Math.round(group.top()) - pad);
        int y1 = Math.min(symbolMask.rows() - 1, Math.round(group.bottom()) + pad);

        int minRun = Math.max(staffSpacing + 2, (int) (b.height() + staffSpacing * 0.6f));
        int stemX = -1;
        int stemTop = y1;
        int stemBottom = y0;
        int bestSpan = 0;

        for (int x = x0; x <= x1; x++) {
            int run = 0;
            int best = 0;
            int bestRunTop = -1;
            int bestRunBottom = -1;
            int runStart = -1;
            for (int y = y0; y <= y1; y++) {
                if (symbolMask.get(y, x)[0] > 0) {
                    if (run == 0) runStart = y;
                    run++;
                    if (run > best) {
                        best = run;
                        bestRunTop = runStart;
                        bestRunBottom = y;
                    }
                } else {
                    run = 0;
                    runStart = -1;
                }
            }
            if (best >= minRun && best > bestSpan) {
                bestSpan = best;
                stemX = x;
                stemTop = bestRunTop;
                stemBottom = bestRunBottom;
            }
        }

        if (stemX < 0) return 0;

        boolean upStem = (b.cy() - stemTop) > (stemBottom - b.cy());
        int flagAreaX0;
        int flagAreaX1;
        int flagAreaY0;
        int flagAreaY1;

        if (upStem) {
            flagAreaX0 = stemX;
            flagAreaX1 = Math.min(symbolMask.cols() - 1, stemX + Math.max(staffSpacing * 2, Math.round(group.spacing * 2f)));
            flagAreaY0 = Math.max(0, stemTop - Math.max(staffSpacing, Math.round(group.spacing)));
            flagAreaY1 = Math.min(symbolMask.rows() - 1, stemTop + Math.max(staffSpacing, Math.round(group.spacing * 0.8f)));
        } else {
            flagAreaX0 = Math.max(0, stemX - Math.max(staffSpacing * 2, Math.round(group.spacing * 2f)));
            flagAreaX1 = stemX;
            flagAreaY0 = Math.max(0, stemBottom - Math.max(staffSpacing, Math.round(group.spacing * 0.8f)));
            flagAreaY1 = Math.min(symbolMask.rows() - 1, stemBottom + Math.max(staffSpacing, Math.round(group.spacing)));
        }

        int minHorizRun = Math.max(3, staffSpacing / 2);
        int rowsWithRun = 0;
        for (int y = flagAreaY0; y <= flagAreaY1; y++) {
            int run = 0;
            int best = 0;
            for (int x = flagAreaX0; x <= flagAreaX1; x++) {
                if (symbolMask.get(y, x)[0] > 0) {
                    run++;
                    if (run > best) best = run;
                } else {
                    run = 0;
                }
            }
            if (best >= minHorizRun) rowsWithRun++;
        }

        if (rowsWithRun >= 7) return 2;
        if (rowsWithRun >= 4) return 1;
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
