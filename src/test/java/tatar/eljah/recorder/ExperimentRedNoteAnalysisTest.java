package tatar.eljah.recorder;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ExperimentRedNoteAnalysisTest {

    public static void main(String[] args) throws Exception {
        File experiment = new File("experiment.png");
        File experimentRed = new File("experiment_red.png");
        File xml = new File("Free-trial-photo-2026-02-13-14-27-38.xml");
        if (!experiment.exists() || !experimentRed.exists() || !xml.exists()) {
            throw new AssertionError("Missing one of required files: experiment.png, experiment_red.png, xml");
        }

        BufferedImage blackWhite = ImageIO.read(experiment);
        BufferedImage redRef = ImageIO.read(experimentRed);
        if (blackWhite == null || redRef == null) {
            throw new AssertionError("Unable to decode experiment images");
        }

        int w = blackWhite.getWidth();
        int h = blackWhite.getHeight();
        int[] argb = blackWhite.getRGB(0, 0, w, h, null, 0, w);

        OpenCvScoreProcessor processor = new OpenCvScoreProcessor();
        OpenCvScoreProcessor.ProcessingResult result = processor.processArgb(w, h, argb, "experiment", OpenCvScoreProcessor.ProcessingOptions.defaults().withRequireOpenCv(true));
        System.out.println("Processing mode: " + result.processingMode + ", OpenCV=" + result.openCvUsed);
        if (!result.openCvUsed) {
            throw new AssertionError("OpenCV mode required for experiment analysis, got " + result.processingMode);
        }

        OpenCvScoreProcessor.StaffCorridor top = topCorridor(result.staffCorridors);
        if (top == null) {
            throw new AssertionError("No staff corridor found in OpenCV mode");
        }

        List<AnchorPoint> expectedCenters = detectRedCenters(redRef, top);
        List<NoteEvent> recognizedTop = filterNotesByCorridor(result.piece.notes, top);
        List<NoteEvent> expectedXmlTop = firstN(parseXmlNotes(xml), expectedCenters.size());

        System.out.println("Top staff expected centers (from experiment_red): " + expectedCenters.size());
        System.out.println("Top staff recognized notes (from experiment): " + recognizedTop.size());
        System.out.println("Top staff XML reference slice: " + expectedXmlTop.size());

        analyzePerExpectedPoint(expectedCenters, recognizedTop, expectedXmlTop);
        runBlobForensics(blackWhite, expectedCenters, top);
    }

    private static void analyzePerExpectedPoint(List<AnchorPoint> expected,
                                                List<NoteEvent> recognized,
                                                List<NoteEvent> xmlReference) {
        boolean[] used = new boolean[recognized.size()];
        int ok = 0;
        int miss = 0;
        int pitchMismatch = 0;
        int durationMismatch = 0;
        List<String> okNotes = new ArrayList<String>();

        for (int i = 0; i < expected.size(); i++) {
            AnchorPoint p = expected.get(i);
            int idx = nearestUnmatchedByX(recognized, used, p.xNorm);
            NoteEvent actual = idx >= 0 ? recognized.get(idx) : null;
            NoteEvent ref = i < xmlReference.size() ? xmlReference.get(i) : null;

            String reason;
            if (actual == null) {
                miss++;
                reason = "MISS: нет распознанной ноты рядом по X (возможна потеря головки после фильтрации/дедупликации)";
            } else {
                float dx = Math.abs(actual.x - p.xNorm);
                float dy = Math.abs(actual.y - p.yNorm);
                boolean near = dx <= 0.028f;
                if (!near) {
                    miss++;
                    reason = "MISS: ближайшая нота слишком далеко по X (dx=" + fmt(dx) + ")";
                } else {
                    used[idx] = true;
                    if (ref != null && (!safeEq(ref.noteName, actual.noteName) || ref.octave != actual.octave)) {
                        pitchMismatch++;
                        reason = "PITCH_MISMATCH: ожидалось " + ref.noteName + ref.octave + ", получено "
                                + actual.noteName + actual.octave + " (dy=" + fmt(dy) + ")";
                    } else if (ref != null && !safeEq(normalizeDuration(ref.duration), normalizeDuration(actual.duration))) {
                        durationMismatch++;
                        reason = "DURATION_MISMATCH: ожидалось " + ref.duration + ", получено " + actual.duration;
                    } else {
                        ok++;
                        okNotes.add("#" + (i + 1) + " " + (ref == null ? "n/a" : (ref.noteName + ref.octave + " " + normalizeDuration(ref.duration))));
                        reason = "OK";
                    }
                }
            }

            String refText = ref == null ? "n/a" : (ref.noteName + ref.octave + " " + ref.duration);
            String actText = actual == null ? "none" : (actual.noteName + actual.octave + " " + actual.duration
                    + " @(" + fmt(actual.x) + "," + fmt(actual.y) + ")");
            System.out.println("#" + (i + 1)
                    + " center=(" + fmt(p.xNorm) + "," + fmt(p.yNorm) + ")"
                    + " ref=" + refText
                    + " actual=" + actText
                    + " => " + reason);
        }

        System.out.println("Summary: OK=" + ok
                + ", MISS=" + miss
                + ", PITCH_MISMATCH=" + pitchMismatch
                + ", DURATION_MISMATCH=" + durationMismatch);
        System.out.println("Correctly recognized notes:");
        for (String n : okNotes) {
            System.out.println("  " + n);
        }
    }

    private static String normalizeDuration(String d) {
        if (d == null) return "quarter";
        if ("16th".equals(d)) return "sixteenth";
        return d;
    }

    private static boolean safeEq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private static String fmt(float v) {
        return String.format(java.util.Locale.US, "%.4f", v);
    }

    private static int nearestUnmatchedByX(List<NoteEvent> notes, boolean[] used, float x) {
        int best = -1;
        float bestDx = Float.MAX_VALUE;
        for (int i = 0; i < notes.size(); i++) {
            if (used != null && i < used.length && used[i]) continue;
            float dx = Math.abs(notes.get(i).x - x);
            if (dx < bestDx) {
                bestDx = dx;
                best = i;
            }
        }
        return best;
    }

    private static List<NoteEvent> filterNotesByCorridor(List<NoteEvent> notes, OpenCvScoreProcessor.StaffCorridor corridor) {
        List<NoteEvent> out = new ArrayList<NoteEvent>();
        for (NoteEvent n : notes) {
            if (n.x >= corridor.left && n.x <= corridor.right && n.y >= corridor.top && n.y <= corridor.bottom) {
                out.add(n);
            }
        }
        Collections.sort(out, new Comparator<NoteEvent>() {
            @Override
            public int compare(NoteEvent a, NoteEvent b) {
                return Float.compare(a.x, b.x);
            }
        });
        return out;
    }

    private static OpenCvScoreProcessor.StaffCorridor topCorridor(List<OpenCvScoreProcessor.StaffCorridor> corridors) {
        if (corridors == null || corridors.isEmpty()) return null;
        OpenCvScoreProcessor.StaffCorridor best = corridors.get(0);
        for (OpenCvScoreProcessor.StaffCorridor c : corridors) {
            if (c.top < best.top) best = c;
        }
        return best;
    }


    private static List<AnchorPoint> detectRedCenters(BufferedImage img, OpenCvScoreProcessor.StaffCorridor top) {
        int w = img.getWidth();
        int h = img.getHeight();
        List<AnchorPoint> pts = new ArrayList<AnchorPoint>();
        for (int y = 0; y < h; y++) {
            float yn = y / (float) Math.max(1, h - 1);
            if (yn < top.top || yn > top.bottom) continue;
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                if (r >= 200 && g <= 80 && b <= 80) {
                    pts.add(new AnchorPoint(x / (float) Math.max(1, w - 1), yn));
                }
            }
        }

        pts = dedupeRedPoints(pts, 0.0065f);
        Collections.sort(pts, new Comparator<AnchorPoint>() {
            @Override
            public int compare(AnchorPoint a, AnchorPoint b) {
                return Float.compare(a.xNorm, b.xNorm);
            }
        });
        return pts;
    }

    private static List<AnchorPoint> dedupeRedPoints(List<AnchorPoint> points, float minDist) {
        List<AnchorPoint> out = new ArrayList<AnchorPoint>();
        for (AnchorPoint p : points) {
            boolean close = false;
            for (AnchorPoint k : out) {
                float dx = p.xNorm - k.xNorm;
                float dy = p.yNorm - k.yNorm;
                if (Math.sqrt(dx * dx + dy * dy) <= minDist) {
                    close = true;
                    break;
                }
            }
            if (!close) out.add(p);
        }
        return out;
    }


    private static List<NoteEvent> parseXmlNotes(File xmlFile) throws Exception {
        String xml = new String(Files.readAllBytes(xmlFile.toPath()), StandardCharsets.UTF_8);
        List<NoteEvent> out = new ArrayList<NoteEvent>();

        Pattern notePattern = Pattern.compile("<note\\b.*?>.*?</note>", Pattern.DOTALL);
        Matcher noteMatcher = notePattern.matcher(xml);
        while (noteMatcher.find()) {
            String note = noteMatcher.group();
            if (note.contains("<rest")) {
                continue;
            }
            String step = firstTagValue(note, "step");
            String alter = firstTagValue(note, "alter");
            String octaveText = firstTagValue(note, "octave");
            String duration = firstTagValue(note, "type");
            if (step == null || octaveText == null) {
                continue;
            }
            String name = step.trim();
            if ("1".equals(trimOrNull(alter))) name += "#";
            if ("-1".equals(trimOrNull(alter))) name += "b";
            if (duration == null || duration.trim().isEmpty()) duration = "quarter";
            out.add(new NoteEvent(name, Integer.parseInt(octaveText.trim()), duration.trim(), 1 + out.size() / 4));
        }
        return out;
    }

    private static String firstTagValue(String xmlBlock, String tag) {
        Pattern p = Pattern.compile("<" + tag + "\\b[^>]*>(.*?)</" + tag + ">", Pattern.DOTALL);
        Matcher m = p.matcher(xmlBlock);
        if (!m.find()) return null;
        return m.group(1).replaceAll("<.*?>", "").trim();
    }

    private static String trimOrNull(String v) {
        return v == null ? null : v.trim();
    }

    private static List<NoteEvent> firstN(List<NoteEvent> notes, int n) {
        List<NoteEvent> out = new ArrayList<NoteEvent>();
        for (int i = 0; i < notes.size() && i < n; i++) out.add(notes.get(i));
        return out;
    }

    private static void runBlobForensics(BufferedImage image,
                                         List<AnchorPoint> expectedCenters,
                                         OpenCvScoreProcessor.StaffCorridor top) {
        Mat gray = bufferedToGray(image);
        Mat binary = new Mat();
        Mat horizontal = new Mat();
        Mat vertical = new Mat();
        Mat noHoriz = new Mat();
        Mat noLines = new Mat();
        Mat intersections = new Mat();
        Mat intersectionDilated = new Mat();
        Mat kernelH = null;
        Mat kernelV = null;
        Mat dilateKernel = null;
        try {
            Imgproc.adaptiveThreshold(gray, binary, 255,
                    Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                    Imgproc.THRESH_BINARY_INV,
                    31,
                    7);

            int w = binary.cols();
            int h = binary.rows();
            int y0 = Math.max(0, Math.round(top.top * (h - 1)));
            int y1 = Math.min(h - 1, Math.round(top.bottom * (h - 1)));
            Rect topRect = new Rect(0, y0, w, Math.max(1, y1 - y0 + 1));

            Mat topBinary = new Mat(binary, topRect);

            int hKernelLen = Math.max(20, w / 10);
            int vKernelLen = Math.max(10, (y1 - y0) / 3);
            kernelH = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(hKernelLen, 1));
            kernelV = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(1, vKernelLen));

            Imgproc.morphologyEx(topBinary, horizontal, Imgproc.MORPH_OPEN, kernelH);
            Imgproc.morphologyEx(topBinary, vertical, Imgproc.MORPH_OPEN, kernelV);

            Core.subtract(topBinary, horizontal, noHoriz);
            Core.subtract(noHoriz, vertical, noLines);

            Core.bitwise_and(horizontal, vertical, intersections);
            dilateKernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(5, 5));
            Imgproc.dilate(intersections, intersectionDilated, dilateKernel);

            List<BlobInfo> blobsOriginal = collectBlobs(topBinary, y0, image.getHeight(), expectedCenters, intersections, 4, 3200);
            List<BlobInfo> blobsNoLines = collectBlobs(noLines, y0, image.getHeight(), expectedCenters, intersectionDilated, 4, 3200);

            int origAtIntersections = countAtIntersections(blobsOriginal);
            int cleanAtIntersections = countAtIntersections(blobsNoLines);
            int origUnmatched = countUnmatched(blobsOriginal);
            int cleanUnmatched = countUnmatched(blobsNoLines);

            System.out.println("--- Blob forensics (top staff) ---");
            System.out.println("Original symbol blobs: " + blobsOriginal.size()
                    + ", unmatchedByRed=" + origUnmatched
                    + ", onLineIntersections=" + origAtIntersections);
            System.out.println("After line subtraction blobs: " + blobsNoLines.size()
                    + ", unmatchedByRed=" + cleanUnmatched
                    + ", onLineIntersections=" + cleanAtIntersections);

            int unmatchedThinHorizontal = 0;
            int unmatchedIntersection = 0;
            int unmatchedSmallArea = 0;
            System.out.println("Suspicious blobs without red markers (after subtraction):");
            int printed = 0;
            for (BlobInfo b : blobsNoLines) {
                if (b.matchesRed) continue;
                float ratio = b.height <= 0 ? 99f : (b.width / (float) b.height);
                if (b.height <= 4 && ratio >= 2.6f) unmatchedThinHorizontal++;
                if (b.onIntersection) unmatchedIntersection++;
                if (b.area <= 24) unmatchedSmallArea++;
                if (printed >= 12) continue;
                System.out.println("  blob cx=" + fmt(b.cxNorm) + " cy=" + fmt(b.cyNorm)
                        + " w=" + b.width + " h=" + b.height + " area=" + b.area
                        + " ratio=" + fmt(ratio)
                        + " onIntersection=" + b.onIntersection);
                printed++;
            }
            System.out.println("Unmatched blob properties: thinHorizontal=" + unmatchedThinHorizontal
                    + ", onIntersection=" + unmatchedIntersection
                    + ", smallArea=" + unmatchedSmallArea);
        } finally {
            gray.release();
            binary.release();
            horizontal.release();
            vertical.release();
            noHoriz.release();
            noLines.release();
            intersections.release();
            intersectionDilated.release();
            if (kernelH != null) kernelH.release();
            if (kernelV != null) kernelV.release();
            if (dilateKernel != null) dilateKernel.release();
        }
    }

    private static int countAtIntersections(List<BlobInfo> blobs) {
        int c = 0;
        for (BlobInfo b : blobs) if (b.onIntersection) c++;
        return c;
    }

    private static int countUnmatched(List<BlobInfo> blobs) {
        int c = 0;
        for (BlobInfo b : blobs) if (!b.matchesRed) c++;
        return c;
    }

    private static List<BlobInfo> collectBlobs(Mat mask,
                                               int yOffset,
                                               int fullHeight,
                                               List<AnchorPoint> red,
                                               Mat intersectionMask,
                                               int minArea,
                                               int maxArea) {
        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        Mat hierarchy = new Mat();
        Mat input = mask.clone();
        List<BlobInfo> out = new ArrayList<BlobInfo>();
        try {
            Imgproc.findContours(input, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
            int w = mask.cols();
            int h = mask.rows();
            for (MatOfPoint c : contours) {
                double area = Imgproc.contourArea(c);
                if (area < minArea || area > maxArea) continue;
                Rect r = Imgproc.boundingRect(c);
                float cx = (r.x + r.width * 0.5f) / Math.max(1f, w - 1f);
                float cy = (yOffset + r.y + r.height * 0.5f) / Math.max(1f, fullHeight - 1f);

                BlobInfo b = new BlobInfo();
                b.cxNorm = cx;
                b.cyNorm = cy;
                b.width = r.width;
                b.height = r.height;
                b.area = (int) Math.round(area);
                b.matchesRed = nearestRedDistance(cx, cy, red) <= 0.035f;
                b.onIntersection = overlapsMask(intersectionMask, r);
                out.add(b);
            }
            Collections.sort(out, new Comparator<BlobInfo>() {
                @Override
                public int compare(BlobInfo a, BlobInfo b) {
                    return Float.compare(a.cxNorm, b.cxNorm);
                }
            });
            return out;
        } finally {
            for (MatOfPoint c : contours) c.release();
            hierarchy.release();
            input.release();
        }
    }

    private static boolean overlapsMask(Mat mask, Rect r) {
        int x0 = Math.max(0, r.x);
        int y0 = Math.max(0, r.y);
        int x1 = Math.min(mask.cols() - 1, r.x + r.width - 1);
        int y1 = Math.min(mask.rows() - 1, r.y + r.height - 1);
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                if (mask.get(y, x)[0] > 0) return true;
            }
        }
        return false;
    }

    private static float nearestRedDistance(float cx, float cy, List<AnchorPoint> red) {
        float best = Float.MAX_VALUE;
        for (AnchorPoint p : red) {
            float dx = cx - p.xNorm;
            float dy = cy - p.yNorm;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d < best) best = d;
        }
        return best;
    }

    private static Mat bufferedToGray(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        Mat gray = new Mat(h, w, CvType.CV_8UC1);
        byte[] row = new byte[w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                row[x] = (byte) ((r * 299 + g * 587 + b * 114) / 1000);
            }
            gray.put(y, 0, row);
        }
        return gray;
    }
    private static class BlobInfo {
        float cxNorm;
        float cyNorm;
        int width;
        int height;
        int area;
        boolean matchesRed;
        boolean onIntersection;
    }

    private static class AnchorPoint {
        final float xNorm;
        final float yNorm;

        AnchorPoint(float xNorm, float yNorm) {
            this.xNorm = xNorm;
            this.yNorm = yNorm;
        }
    }
}
