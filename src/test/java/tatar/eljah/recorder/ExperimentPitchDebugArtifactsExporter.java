package tatar.eljah.recorder;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ExperimentPitchDebugArtifactsExporter {

    private static final float HARD_NOTEHEAD_AREA_BOUNDARY = 48.0f;
    private static final float MAX_HEAD_ASPECT_RATIO = 2.0f;

    static {
        UnsatisfiedLinkError last = null;
        for (String lib : new String[]{"opencv_java460", "opencv_java4", "opencv_java"}) {
            try {
                System.loadLibrary(lib);
                last = null;
                break;
            } catch (UnsatisfiedLinkError e) {
                last = e;
            }
        }
        if (last != null) {
            throw last;
        }
    }

    public static void main(String[] args) throws Exception {
        File input = new File("clear_sreenshot.png");
        if (!input.exists()) {
            throw new AssertionError("Missing clear_sreenshot.png");
        }
        BufferedImage screenshot = ImageIO.read(input);
        if (screenshot == null) {
            throw new AssertionError("Unable to decode clear_sreenshot.png");
        }

        List<Rect> staffInteriors = resolveStaffInteriors(screenshot);
        if (staffInteriors.isEmpty()) {
            throw new AssertionError("No staff interiors found for clear_sreenshot.png");
        }
        Collections.sort(staffInteriors, new Comparator<Rect>() {
            @Override
            public int compare(Rect a, Rect b) {
                if (a.y == b.y) return a.x - b.x;
                return a.y - b.y;
            }
        });

        int staffCount = Math.min(3, staffInteriors.size());
        File outDir = new File("docs/diagnostics");
        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new IllegalStateException("Cannot create output directory: " + outDir.getAbsolutePath());
        }

        System.out.println("Staff crops selected: " + staffInteriors.size() + ", processing first " + staffCount + " staff systems.");
        for (int i = 0; i < staffCount; i++) {
            Rect staffRect = staffInteriors.get(i);
            BufferedImage crop = cropBufferedImage(screenshot, staffRect);
            processSingleStaffCrop(crop, i + 1, outDir);
        }
    }

    private static BufferedImage cropBufferedImage(BufferedImage src, Rect r) {
        int x0 = Math.max(0, r.x);
        int y0 = Math.max(0, r.y);
        int x1 = Math.min(src.getWidth() - 1, r.x + r.width - 1);
        int y1 = Math.min(src.getHeight() - 1, r.y + r.height - 1);
        if (x1 <= x0 || y1 <= y0) {
            return src;
        }
        int w = x1 - x0 + 1;
        int h = y1 - y0 + 1;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out.setRGB(x, y, src.getRGB(x0 + x, y0 + y));
            }
        }
        return out;
    }

    private static void processSingleStaffCrop(BufferedImage image, int staffIndex, File outDir) throws Exception {
        String prefix = "experiment_staff" + staffIndex + "_";

        Mat gray = bufferedToGray(image);
        Mat binary = new Mat();
        Mat horizontal = new Mat();
        Mat vertical = new Mat();
        Mat intersections = new Mat();
        Mat noLines = new Mat();
        Mat noStems = new Mat();
        Mat noStemsClean = new Mat();
        Mat step3PipelineMask = new Mat();
        Mat blurredThin = new Mat();
        Mat blurRebinarized = new Mat();
        Mat step4PipelineMask = new Mat();
        Mat mergedNarrowGaps = new Mat();
        Mat aspectFilteredMask = null;

        Mat kH = null;
        Mat kV = null;
        Mat kStem = null;
        Mat kThinErase = null;
        Mat kSinglePixelEat = null;
        Mat kMergeV = null;
        try {
            Imgproc.adaptiveThreshold(gray, binary, 255,
                    Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                    Imgproc.THRESH_BINARY_INV,
                    31,
                    7);

            int w = gray.cols();
            int h = gray.rows();
            int hKernel = Math.max(18, w / 12);
            int vKernel = Math.max(10, h / 16);
            kH = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(hKernel, 1));
            kV = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(1, vKernel));

            Imgproc.morphologyEx(binary, horizontal, Imgproc.MORPH_OPEN, kH);
            Imgproc.morphologyEx(binary, vertical, Imgproc.MORPH_OPEN, kV);
            int lineThickness = estimateHorizontalLineThickness(horizontal);
            int staffSpacing = estimateStaffSpacing(horizontal);
            Mat verticalFiltered = filterVerticalLinesByGeometry(vertical, lineThickness, staffSpacing);
            vertical.release();
            vertical = verticalFiltered;
            Core.bitwise_and(horizontal, vertical, intersections);

            Core.subtract(binary, horizontal, noLines);
            Core.subtract(noLines, vertical, noLines);

            int minStemHeight = Math.max(8, Math.round(staffSpacing * 1.6f));
            kStem = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(1, minStemHeight));
            Mat stemMask = new Mat();
            try {
                Imgproc.morphologyEx(noLines, stemMask, Imgproc.MORPH_OPEN, kStem);
                Core.subtract(noLines, stemMask, noStems);
            } finally {
                stemMask.release();
            }

            kSinglePixelEat = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(2, 2));
            Imgproc.morphologyEx(noStems, noStemsClean, Imgproc.MORPH_OPEN, kSinglePixelEat);
            noStemsClean.copyTo(step3PipelineMask);

            Imgproc.GaussianBlur(step3PipelineMask, blurredThin, new Size(5, 5), 0.0);
            Imgproc.threshold(blurredThin, blurRebinarized, 142, 255, Imgproc.THRESH_BINARY);
            int thinEraseSize = Math.max(2, lineThickness + 1);
            kThinErase = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(thinEraseSize, thinEraseSize));
            Imgproc.morphologyEx(blurRebinarized, blurRebinarized, Imgproc.MORPH_OPEN, kThinErase);
            Core.max(blurRebinarized, step3PipelineMask, blurRebinarized);
            Imgproc.threshold(blurRebinarized, blurRebinarized, 127, 255, Imgproc.THRESH_BINARY);
            blurRebinarized.copyTo(step4PipelineMask);

            step4PipelineMask.copyTo(mergedNarrowGaps);
            int mergeHeight = Math.max(3, lineThickness + 1);
            if (mergeHeight % 2 == 0) mergeHeight += 1;
            kMergeV = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(1, mergeHeight));
            Imgproc.morphologyEx(mergedNarrowGaps, mergedNarrowGaps, Imgproc.MORPH_CLOSE, kMergeV);
            Imgproc.threshold(mergedNarrowGaps, mergedNarrowGaps, 127, 255, Imgproc.THRESH_BINARY);

            BufferedImage linesOverlay = buildLinesOverlay(gray, horizontal, vertical, intersections);
            BufferedImage subtractedView = binaryMaskToWhiteBg(noLines);
            BufferedImage stemSubtractedView = binaryMaskToWhiteBg(step3PipelineMask);
            BufferedImage blurThinView = binaryMaskToWhiteBg(step4PipelineMask);
            BufferedImage mergedView = binaryMaskToWhiteBg(mergedNarrowGaps);

            List<Rect> stage0 = detectBlobs(step3PipelineMask, 4, 6000);
            BlobFilterResult stage0Overlap = filterOverlappingSmaller(stage0);
            BlobFilterResult stage0Mono = filterMonophonicByX(stage0Overlap.kept, lineThickness, staffSpacing);

            List<Rect> stage1 = detectBlobs(step4PipelineMask, 4, 6000);
            BlobFilterResult stage1Overlap = filterOverlappingSmaller(stage1);
            BlobFilterResult stage1Mono = filterMonophonicByX(stage1Overlap.kept, lineThickness, staffSpacing);

            List<Rect> stage2 = detectBlobs(mergedNarrowGaps, 4, 6000);
            BlobFilterResult stage2Overlap = filterOverlappingSmaller(stage2);
            BlobFilterResult stage2Mono = filterMonophonicByX(stage2Overlap.kept, lineThickness, staffSpacing);

            BufferedImage sortedByAreaView = drawAreaOrderOnMergedMask(mergedView, stage2);
            List<Rect> aspectFiltered = filterByAspectRatio(stage2);
            aspectFilteredMask = filterMaskByAspectRatio(mergedNarrowGaps, MAX_HEAD_ASPECT_RATIO);
            BufferedImage aspectFilteredView = binaryMaskToWhiteBg(aspectFilteredMask);
            List<Rect> step7Blobs = detectBlobs(aspectFilteredMask, 4, 6000);
            List<Rect> topRoundLarge = selectTopByArea(step7Blobs, 13);
            BufferedImage roundLargeView = drawRoundLargeSelection(aspectFilteredView, step7Blobs, topRoundLarge);
            List<Rect> recognitionCandidates = filterByHardAreaBoundary(topRoundLarge, HARD_NOTEHEAD_AREA_BOUNDARY);
            BufferedImage allBlobView = drawBlobsOnGray(gray, recognitionCandidates, new Scalar(0, 120, 255));
            BufferedImage step10LabeledView = drawStep10RecognizedOnStep9(allBlobView, recognitionCandidates, horizontal);

            savePngAndBase64(linesOverlay, new File(outDir, prefix + "step1_lines_overlay"));
            savePngAndBase64(subtractedView, new File(outDir, prefix + "step2_lines_subtracted"));
            savePngAndBase64(stemSubtractedView, new File(outDir, prefix + "step3_stems_subtracted"));
            savePngAndBase64(blurThinView, new File(outDir, prefix + "step4_thin_artifacts_blurred"));
            savePngAndBase64(mergedView, new File(outDir, prefix + "step5_blobs_merged_narrow_gaps"));
            savePngAndBase64(sortedByAreaView, new File(outDir, prefix + "step6_blobs_sorted_area_annotated"));
            savePngAndBase64(aspectFilteredView, new File(outDir, prefix + "step7_aspect_ratio_filtered"));
            savePngAndBase64(roundLargeView, new File(outDir, prefix + "step8_noteheads_area_top13"));
            savePngAndBase64(allBlobView, new File(outDir, prefix + "step9_blobs_all"));
            savePngAndBase64(step10LabeledView, new File(outDir, prefix + "step10_final_recognized_overlay"));

            RecognitionProxyStats before = computeProxyStats(image, stage0Mono.kept);
            RecognitionProxyStats afterBlur = computeProxyStats(image, stage1Mono.kept);
            RecognitionProxyStats afterMerge = computeProxyStats(image, stage2Mono.kept);

            int[] argb = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
            OpenCvScoreProcessor.ProcessingResult staffRun = new OpenCvScoreProcessor().processArgb(
                    image.getWidth(), image.getHeight(), argb,
                    "clear-screenshot-staff-" + staffIndex,
                    OpenCvScoreProcessor.ProcessingOptions.defaults().withRequireOpenCv(true));

            System.out.println("=== Staff #" + staffIndex + " stats ===");
            System.out.println("Stage0(noStems) blobs: raw=" + stage0.size() + ", overlapKept=" + stage0Overlap.kept.size() + ", monoKept=" + stage0Mono.kept.size());
            System.out.println("Stage1(blur thin) blobs: raw=" + stage1.size() + ", overlapKept=" + stage1Overlap.kept.size() + ", monoKept=" + stage1Mono.kept.size());
            System.out.println("Stage2(merge narrow gaps) blobs: raw=" + stage2.size() + ", overlapKept=" + stage2Overlap.kept.size() + ", monoKept=" + stage2Mono.kept.size());
            System.out.println("Step7(aspect ratio filtered): " + aspectFiltered.size());
            System.out.println("Step9(blobs from step8 over hard area boundary=" + HARD_NOTEHEAD_AREA_BOUNDARY + "): " + recognitionCandidates.size());
            printProxyStats("Staff#" + staffIndex + " before new steps (noStems)", before);
            printProxyStats("Staff#" + staffIndex + " after blur thin artifacts", afterBlur);
            printProxyStats("Staff#" + staffIndex + " after merge narrow gaps", afterMerge);

            List<String> noteTokens = new ArrayList<String>();
            for (NoteEvent n : staffRun.piece.notes) {
                noteTokens.add(n.noteName + n.octave + "(" + normalizeDuration(n.duration) + ")");
            }
            System.out.println("Staff#" + staffIndex + " OpenCV notes: count=" + staffRun.piece.notes.size() + " -> " + noteTokens);
            printStep10RecognizedPitches(recognitionCandidates, horizontal, staffSpacing);
        } finally {
            gray.release();
            binary.release();
            horizontal.release();
            vertical.release();
            intersections.release();
            noLines.release();
            noStems.release();
            noStemsClean.release();
            step3PipelineMask.release();
            blurredThin.release();
            blurRebinarized.release();
            step4PipelineMask.release();
            mergedNarrowGaps.release();
            if (aspectFilteredMask != null) aspectFilteredMask.release();
            if (kH != null) kH.release();
            if (kV != null) kV.release();
            if (kStem != null) kStem.release();
            if (kThinErase != null) kThinErase.release();
            if (kSinglePixelEat != null) kSinglePixelEat.release();
            if (kMergeV != null) kMergeV.release();
        }
    }

    private static String normalizeDuration(String d) {
        if (d == null) return "quarter";
        if ("16th".equals(d)) return "sixteenth";
        return d;
    }

    private static void printProxyStats(String stageName, RecognitionProxyStats s) {
        System.out.println(stageName + " => redExpected=" + s.redExpected
                + ", blobs=" + s.blobCount
                + ", matchedExpected=" + s.matchedExpected
                + ", misses=" + s.missedExpected
                + ", unmatchedBlobs=" + s.unmatchedBlobs);
    }

    private static RecognitionProxyStats computeProxyStats(BufferedImage experiment, List<Rect> blobs) throws Exception {
        File experimentRed = new File("experiment_red.png");
        if (!experimentRed.exists()) {
            RecognitionProxyStats empty = new RecognitionProxyStats();
            empty.redExpected = 0;
            empty.blobCount = blobs.size();
            empty.matchedExpected = 0;
            empty.missedExpected = 0;
            empty.unmatchedBlobs = blobs.size();
            return empty;
        }
        BufferedImage red = ImageIO.read(experimentRed);
        List<AnchorPoint> expected = detectRedCenters(red);
        List<AnchorPoint> blobCenters = rectCentersNorm(blobs, experiment.getWidth(), experiment.getHeight());

        boolean[] usedBlob = new boolean[blobCenters.size()];
        int matched = 0;
        final float matchTol = 0.035f;
        for (AnchorPoint e : expected) {
            int best = -1;
            float bestD = Float.MAX_VALUE;
            for (int i = 0; i < blobCenters.size(); i++) {
                if (usedBlob[i]) continue;
                float d = dist(e, blobCenters.get(i));
                if (d < bestD) {
                    bestD = d;
                    best = i;
                }
            }
            if (best >= 0 && bestD <= matchTol) {
                usedBlob[best] = true;
                matched++;
            }
        }

        int used = 0;
        for (boolean b : usedBlob) if (b) used++;

        RecognitionProxyStats s = new RecognitionProxyStats();
        s.redExpected = expected.size();
        s.blobCount = blobCenters.size();
        s.matchedExpected = matched;
        s.missedExpected = Math.max(0, expected.size() - matched);
        s.unmatchedBlobs = Math.max(0, blobCenters.size() - used);
        return s;
    }

    private static List<AnchorPoint> rectCentersNorm(List<Rect> rects, int w, int h) {
        List<AnchorPoint> out = new ArrayList<AnchorPoint>();
        for (Rect r : rects) {
            float x = (r.x + r.width * 0.5f) / Math.max(1f, w - 1f);
            float y = (r.y + r.height * 0.5f) / Math.max(1f, h - 1f);
            out.add(new AnchorPoint(x, y));
        }
        return out;
    }

    private static List<AnchorPoint> detectRedCenters(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        List<AnchorPoint> pts = new ArrayList<AnchorPoint>();
        for (int y = 0; y < h; y++) {
            float yn = y / (float) Math.max(1, h - 1);
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
        return dedupeRedPoints(pts, 0.0065f);
    }

    private static List<AnchorPoint> dedupeRedPoints(List<AnchorPoint> points, float minDist) {
        List<AnchorPoint> out = new ArrayList<AnchorPoint>();
        for (AnchorPoint p : points) {
            boolean close = false;
            for (AnchorPoint k : out) {
                if (dist(p, k) <= minDist) {
                    close = true;
                    break;
                }
            }
            if (!close) out.add(p);
        }
        return out;
    }

    private static float dist(AnchorPoint a, AnchorPoint b) {
        float dx = a.xNorm - b.xNorm;
        float dy = a.yNorm - b.yNorm;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static List<Rect> mergeRemoved(List<Rect> a, List<Rect> b) {
        List<Rect> out = new ArrayList<Rect>(a.size() + b.size());
        out.addAll(a);
        out.addAll(b);
        return out;
    }

    private static Mat filterVerticalLinesByGeometry(Mat verticalMask, int lineThickness, int staffSpacing) {
        Mat out = Mat.zeros(verticalMask.rows(), verticalMask.cols(), CvType.CV_8UC1);
        Mat contoursInput = verticalMask.clone();
        Mat hierarchy = new Mat();
        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        try {
            int maxWidth = Math.max(1, lineThickness * 2);
            int minHeight = Math.max(4, Math.round(staffSpacing * 2.5f));
            Imgproc.findContours(contoursInput, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
            for (MatOfPoint c : contours) {
                Rect r = Imgproc.boundingRect(c);
                if (r.width <= maxWidth && r.height >= minHeight) {
                    Imgproc.drawContours(out, Collections.singletonList(c), -1, new Scalar(255), -1);
                }
                c.release();
            }
            return out;
        } finally {
            contoursInput.release();
            hierarchy.release();
        }
    }

    private static int estimateHorizontalLineThickness(Mat horizontalMask) {
        int h = horizontalMask.rows();
        int w = horizontalMask.cols();
        int sum = 0;
        int samples = 0;
        int step = Math.max(1, w / 64);
        for (int x = 0; x < w; x += step) {
            int run = 0;
            int best = 0;
            for (int y = 0; y < h; y++) {
                if (horizontalMask.get(y, x)[0] > 0) {
                    run++;
                    if (run > best) best = run;
                } else {
                    run = 0;
                }
            }
            if (best > 0) {
                sum += best;
                samples++;
            }
        }
        if (samples == 0) return 1;
        return Math.max(1, Math.min(4, Math.round(sum / (float) samples)));
    }

    private static int estimateStaffSpacing(Mat horizontalMask) {
        int h = horizontalMask.rows();
        int w = horizontalMask.cols();
        int threshold = Math.max(8, w / 12);
        List<Integer> centers = new ArrayList<Integer>();
        boolean inBand = false;
        int start = 0;
        for (int y = 0; y < h; y++) {
            int dark = 0;
            for (int x = 0; x < w; x++) {
                if (horizontalMask.get(y, x)[0] > 0) dark++;
            }
            if (dark >= threshold && !inBand) {
                inBand = true;
                start = y;
            } else if (dark < threshold && inBand) {
                inBand = false;
                centers.add((start + y - 1) / 2);
            }
        }
        if (inBand) centers.add((start + h - 1) / 2);
        if (centers.size() < 2) return Math.max(8, h / 24);
        int sum = 0;
        int cnt = 0;
        for (int i = 1; i < centers.size(); i++) {
            int d = centers.get(i) - centers.get(i - 1);
            if (d > 0 && d < h / 3) {
                sum += d;
                cnt++;
            }
        }
        if (cnt == 0) return Math.max(8, h / 24);
        return Math.max(6, Math.round(sum / (float) cnt));
    }

    private static List<Rect> detectBlobs(Mat mask, int minArea, int maxArea) {
        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        Mat hierarchy = new Mat();
        List<Rect> out = new ArrayList<Rect>();
        try {
            Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
            for (MatOfPoint c : contours) {
                double area = Imgproc.contourArea(c);
                if (area < minArea || area > maxArea) {
                    c.release();
                    continue;
                }
                out.add(Imgproc.boundingRect(c));
                c.release();
            }
            Collections.sort(out, new Comparator<Rect>() {
                @Override
                public int compare(Rect a, Rect b) {
                    return Double.compare(b.area(), a.area());
                }
            });
            return out;
        } finally {
            hierarchy.release();
        }
    }

    private static BlobFilterResult filterOverlappingSmaller(List<Rect> rects) {
        List<Rect> kept = new ArrayList<Rect>();
        List<Rect> removed = new ArrayList<Rect>();
        for (Rect r : rects) {
            boolean overlaps = false;
            for (Rect k : kept) {
                if (intersectsX(r, k) && intersectsY(r, k)) {
                    overlaps = true;
                    break;
                }
            }
            if (overlaps) {
                removed.add(r);
            } else {
                kept.add(r);
            }
        }
        BlobFilterResult res = new BlobFilterResult();
        res.kept = kept;
        res.removed = removed;
        return res;
    }

    private static BlobFilterResult filterMonophonicByX(List<Rect> rects, int lineThickness, int staffSpacing) {
        List<Rect> sorted = new ArrayList<Rect>(rects);
        Collections.sort(sorted, new Comparator<Rect>() {
            @Override
            public int compare(Rect a, Rect b) {
                return Integer.compare(a.x, b.x);
            }
        });

        List<Rect> kept = new ArrayList<Rect>();
        List<Rect> removed = new ArrayList<Rect>();
        for (Rect candidate : sorted) {
            int conflictIdx = -1;
            for (int i = 0; i < kept.size(); i++) {
                Rect k = kept.get(i);
                if (intersectsX(candidate, k)) {
                    conflictIdx = i;
                    break;
                }
            }
            if (conflictIdx < 0) {
                kept.add(candidate);
                continue;
            }
            Rect existing = kept.get(conflictIdx);
            if (blobDominanceScore(candidate, lineThickness, staffSpacing) > blobDominanceScore(existing, lineThickness, staffSpacing)) {
                removed.add(existing);
                kept.set(conflictIdx, candidate);
            } else {
                removed.add(candidate);
            }
        }

        BlobFilterResult res = new BlobFilterResult();
        res.kept = kept;
        res.removed = removed;
        return res;
    }

    private static float blobDominanceScore(Rect r, int lineThickness, int staffSpacing) {
        float area = (float) r.area();
        float aspect = r.width / (float) Math.max(1, r.height);
        float target = Math.max(1.2f, staffSpacing * 0.55f);
        float sizePenalty = Math.abs(r.height - target) / Math.max(target, 1f);
        float aspectPenalty = Math.abs(aspect - 1.1f);
        float thinPenalty = r.height <= Math.max(2, lineThickness) ? 1.5f : 0f;
        return area - 9.0f * sizePenalty - 12.0f * aspectPenalty - 18.0f * thinPenalty;
    }

    private static boolean intersectsX(Rect a, Rect b) {
        return a.x <= (b.x + b.width) && b.x <= (a.x + a.width);
    }

    private static boolean intersectsY(Rect a, Rect b) {
        return a.y <= (b.y + b.height) && b.y <= (a.y + a.height);
    }

    private static BufferedImage buildLinesOverlay(Mat gray, Mat horizontal, Mat vertical, Mat intersections) {
        Mat color = new Mat();
        Imgproc.cvtColor(gray, color, Imgproc.COLOR_GRAY2BGR);
        paintMask(color, horizontal, new Scalar(40, 40, 240));
        paintMask(color, vertical, new Scalar(40, 200, 40));
        paintMask(color, intersections, new Scalar(20, 220, 220));
        BufferedImage out = matBgrToBuffered(color);
        color.release();
        return out;
    }

    private static void paintMask(Mat bgr, Mat mask, Scalar color) {
        int h = bgr.rows();
        int w = bgr.cols();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (mask.get(y, x)[0] > 0) {
                    bgr.put(y, x, new double[]{color.val[0], color.val[1], color.val[2]});
                }
            }
        }
    }

    private static BufferedImage drawBlobsOnGray(Mat gray, List<Rect> rects, Scalar color) {
        Mat out = new Mat();
        Imgproc.cvtColor(gray, out, Imgproc.COLOR_GRAY2BGR);
        for (Rect r : rects) {
            Imgproc.rectangle(out, new Point(r.x, r.y), new Point(r.x + r.width, r.y + r.height), color, 1);
        }
        BufferedImage bi = matBgrToBuffered(out);
        out.release();
        return bi;
    }

    private static BufferedImage drawFilteredBlobs(Mat gray, List<Rect> kept, List<Rect> removed) {
        Mat out = new Mat();
        Imgproc.cvtColor(gray, out, Imgproc.COLOR_GRAY2BGR);
        for (Rect r : removed) {
            Imgproc.rectangle(out, new Point(r.x, r.y), new Point(r.x + r.width, r.y + r.height), new Scalar(0, 0, 220), 1);
        }
        for (Rect r : kept) {
            Imgproc.rectangle(out, new Point(r.x, r.y), new Point(r.x + r.width, r.y + r.height), new Scalar(40, 220, 40), 2);
        }
        BufferedImage bi = matBgrToBuffered(out);
        out.release();
        return bi;
    }

    private static BufferedImage drawAreaOrderOnMergedMask(BufferedImage mergedMaskWhiteBg, List<Rect> mergedBlobs) {
        List<Rect> sorted = new ArrayList<Rect>(mergedBlobs);
        Collections.sort(sorted, new Comparator<Rect>() {
            @Override
            public int compare(Rect a, Rect b) {
                return Double.compare(b.area(), a.area());
            }
        });

        BufferedImage out = new BufferedImage(mergedMaskWhiteBg.getWidth(), mergedMaskWhiteBg.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.drawImage(mergedMaskWhiteBg, 0, 0, null);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setFont(new Font("SansSerif", Font.BOLD, 14));
            g.setStroke(new BasicStroke(2f));
            for (int i = 0; i < sorted.size(); i++) {
                Rect r = sorted.get(i);
                int label = i + 1;
                int tx = Math.max(0, r.x + r.width / 2 - 4);
                int ty = Math.max(14, r.y + r.height / 2 + 5);
                g.setColor(Color.WHITE);
                g.drawString(String.valueOf(label), tx + 1, ty + 1);
                g.setColor(new Color(220, 20, 20));
                g.drawString(String.valueOf(label), tx, ty);
            }
        } finally {
            g.dispose();
        }
        return out;
    }




    private static void printTopRoundLargeDiagnostics(List<Rect> selected) {
        for (int i = 0; i < selected.size(); i++) {
            Rect r = selected.get(i);
            float aspect = r.width / (float) Math.max(1, r.height);
            System.out.println("Step8 top" + (i + 1)
                    + ": w=" + r.width
                    + ", h=" + r.height
                    + ", aspect=" + String.format(java.util.Locale.US, "%.3f", aspect)
                    + ", area=" + String.format(java.util.Locale.US, "%.1f", Math.max(1.0, r.area())));
        }
    }

    private static List<Rect> filterByHardAreaBoundary(List<Rect> rects, float boundary) {
        List<Rect> out = new ArrayList<Rect>();
        for (Rect r : rects) {
            if (Math.max(1.0, r.area()) >= boundary) {
                out.add(r);
            }
        }
        return out.isEmpty() ? new ArrayList<Rect>(rects) : out;
    }

    private static List<Rect> filterByAspectRatio(List<Rect> rects) {
        List<Rect> out = new ArrayList<Rect>();
        for (Rect r : rects) {
            if (isRoundLargeShapeCandidate(r)) out.add(r);
        }
        return out.isEmpty() ? new ArrayList<Rect>(rects) : out;
    }

    private static List<Rect> selectTopByArea(List<Rect> rects, int topN) {
        List<Rect> sorted = new ArrayList<Rect>(rects);
        Collections.sort(sorted, new Comparator<Rect>() {
            @Override
            public int compare(Rect a, Rect b) {
                return Double.compare(b.area(), a.area());
            }
        });
        if (sorted.size() > topN) {
            return new ArrayList<Rect>(sorted.subList(0, topN));
        }
        return sorted;
    }

    private static boolean isRoundLargeShapeCandidate(Rect r) {
        float w = Math.max(1f, r.width);
        float h = Math.max(1f, r.height);
        float aspect = w / h;
        float roundness = 1.0f / (1.0f + Math.abs(aspect - 1.0f));
        if (aspect >= MAX_HEAD_ASPECT_RATIO) return false;
        return roundness >= 0.58f;
    }

    private static Mat filterMaskByAspectRatio(Mat sourceMask, float maxAspectRatio) {
        Mat filtered = sourceMask.clone();
        Mat contoursInput = sourceMask.clone();
        Mat hierarchy = new Mat();
        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        try {
            Imgproc.findContours(contoursInput, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
            for (MatOfPoint c : contours) {
                Rect r = Imgproc.boundingRect(c);
                float aspect = r.width / (float) Math.max(1, r.height);
                if (aspect >= maxAspectRatio) {
                    Imgproc.drawContours(filtered, Collections.singletonList(c), -1, new Scalar(0), -1);
                }
                c.release();
            }
            return filtered;
        } finally {
            contoursInput.release();
            hierarchy.release();
        }
    }

    private static BufferedImage drawRoundLargeSelection(BufferedImage baseMask, List<Rect> allRects, List<Rect> selected) {
        BufferedImage out = new BufferedImage(baseMask.getWidth(), baseMask.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.drawImage(baseMask, 0, 0, null);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setFont(new Font("SansSerif", Font.BOLD, 14));
            for (Rect r : allRects) {
                g.setColor(new Color(255, 140, 0));
                g.drawRect(r.x, r.y, Math.max(1, r.width), Math.max(1, r.height));
            }
            for (int i = 0; i < selected.size(); i++) {
                Rect r = selected.get(i);
                int tx = Math.max(0, r.x + r.width / 2 - 4);
                int ty = Math.max(14, r.y + r.height / 2 + 5);
                g.setColor(new Color(20, 180, 20));
                g.drawRect(r.x, r.y, Math.max(1, r.width), Math.max(1, r.height));
                g.setColor(Color.WHITE);
                g.drawString(String.valueOf(i + 1), tx + 1, ty + 1);
                g.setColor(new Color(20, 180, 20));
                g.drawString(String.valueOf(i + 1), tx, ty);
            }
        } finally {
            g.dispose();
        }
        return out;
    }

    private static BufferedImage binaryMaskToWhiteBg(Mat mask) {
        int w = mask.cols();
        int h = mask.rows();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean on = mask.get(y, x)[0] > 0;
                out.setRGB(x, y, on ? 0xFF000000 : 0xFFFFFFFF);
            }
        }
        return out;
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

    private static Mat bufferedToBgr(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        Mat bgr = new Mat(h, w, CvType.CV_8UC3);
        byte[] row = new byte[w * 3];
        for (int y = 0; y < h; y++) {
            int i = 0;
            for (int x = 0; x < w; x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                row[i++] = (byte) b;
                row[i++] = (byte) g;
                row[i++] = (byte) r;
            }
            bgr.put(y, 0, row);
        }
        return bgr;
    }

    private static BufferedImage matBgrToBuffered(Mat bgr) {
        int w = bgr.cols();
        int h = bgr.rows();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double[] px = bgr.get(y, x);
                int b = clamp((int) Math.round(px[0]));
                int g = clamp((int) Math.round(px[1]));
                int r = clamp((int) Math.round(px[2]));
                int rgb = (r << 16) | (g << 8) | b;
                out.setRGB(x, y, rgb);
            }
        }
        return out;
    }

    private static int clamp(int v) {
        if (v < 0) return 0;
        if (v > 255) return 255;
        return v;
    }


    private static List<Rect> resolveStaffInteriors(BufferedImage image) {
        List<Rect> purple = detectPurpleFrameInteriors(image);
        if (!purple.isEmpty()) {
            return purple;
        }
        List<Rect> fallback = detectStaffInteriorsViaOpenCv(image);
        if (!fallback.isEmpty()) {
            System.out.println("Purple frame not found, fallback to OpenCV staff corridors: " + fallback.size());
            return fallback;
        }
        List<Rect> whole = new ArrayList<Rect>();
        whole.add(new Rect(0, 0, image.getWidth(), image.getHeight()));
        System.out.println("Purple frame and corridors not found, fallback to full screenshot.");
        return whole;
    }

    private static List<Rect> detectStaffInteriorsViaOpenCv(BufferedImage image) {
        List<Rect> out = new ArrayList<Rect>();
        try {
            int w = image.getWidth();
            int h = image.getHeight();
            int[] argb = image.getRGB(0, 0, w, h, null, 0, w);
            OpenCvScoreProcessor.ProcessingResult result = new OpenCvScoreProcessor().processArgb(
                    w, h, argb, "staff-corridor-fallback", OpenCvScoreProcessor.ProcessingOptions.defaults().withRequireOpenCv(true));
            if (result.staffCorridors == null || result.staffCorridors.isEmpty()) return out;
            for (OpenCvScoreProcessor.StaffCorridor c : result.staffCorridors) {
                int x0 = Math.max(0, Math.round(c.left * (w - 1)));
                int y0 = Math.max(0, Math.round(c.top * (h - 1)));
                int x1 = Math.min(w - 1, Math.round(c.right * (w - 1)));
                int y1 = Math.min(h - 1, Math.round(c.bottom * (h - 1)));
                if (x1 <= x0 || y1 <= y0) continue;
                out.add(new Rect(x0, y0, x1 - x0 + 1, y1 - y0 + 1));
            }
            return dedupeOverlappingRects(out);
        } catch (Throwable ignored) {
            return out;
        }
    }

    private static List<Rect> detectPurpleFrameInteriors(BufferedImage image) {
        Mat bgr = bufferedToBgr(image);
        Mat hsv = new Mat();
        Mat purple1 = new Mat();
        Mat purple2 = new Mat();
        Mat purple = new Mat();
        Mat kernel = null;
        Mat contoursInput = null;
        Mat hierarchy = new Mat();
        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        List<Rect> out = new ArrayList<Rect>();
        try {
            Imgproc.cvtColor(bgr, hsv, Imgproc.COLOR_BGR2HSV);

            // Purple/magenta frame strokes on screenshot overlay (two hue bands around magenta).
            Core.inRange(hsv, new Scalar(120, 35, 35), new Scalar(179, 255, 255), purple1);
            Core.inRange(hsv, new Scalar(0, 35, 35), new Scalar(10, 255, 255), purple2);
            Core.bitwise_or(purple1, purple2, purple);

            int closeW = Math.max(9, image.getWidth() / 70);
            int closeH = Math.max(3, image.getHeight() / 220);
            kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(closeW, closeH));
            Imgproc.morphologyEx(purple, purple, Imgproc.MORPH_CLOSE, kernel);

            contoursInput = purple.clone();
            Imgproc.findContours(contoursInput, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
            for (MatOfPoint contour : contours) {
                Rect r = Imgproc.boundingRect(contour);
                int bw = r.width;
                int bh = r.height;
                if (bw < image.getWidth() / 3) continue;
                if (bh < Math.max(10, image.getHeight() / 30)) continue;
                float aspect = bw / (float) Math.max(1, bh);
                if (aspect < 4.5f) continue;

                int inset = Math.max(1, Math.round(Math.max(2f, bh * 0.08f)));
                int ix = Math.max(0, r.x + inset);
                int iy = Math.max(0, r.y + inset);
                int xEnd = Math.min(image.getWidth() - 1, r.x + r.width - 1 - inset);
                int yEnd = Math.min(image.getHeight() - 1, r.y + r.height - 1 - inset);
                if (xEnd <= ix || yEnd <= iy) continue;
                Rect inner = new Rect(ix, iy, xEnd - ix + 1, yEnd - iy + 1);
                if (inner.width < image.getWidth() / 4 || inner.height < image.getHeight() / 40) continue;
                out.add(inner);
            }

            // Deduplicate overlapping/nested boxes and keep top-to-bottom order.
            out = dedupeOverlappingRects(out);
            if (out.isEmpty()) {
                out = detectPurpleFrameInteriorsByHorizontalLines(image);
            }
            Collections.sort(out, new Comparator<Rect>() {
                @Override
                public int compare(Rect a, Rect b) {
                    if (a.y == b.y) return a.x - b.x;
                    return a.y - b.y;
                }
            });
            return out;
        } finally {
            bgr.release();
            hsv.release();
            purple1.release();
            purple2.release();
            purple.release();
            if (kernel != null) kernel.release();
            if (contoursInput != null) contoursInput.release();
            hierarchy.release();
            for (MatOfPoint c : contours) c.release();
        }
    }

    private static List<Rect> detectPurpleFrameInteriorsByHorizontalLines(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int[] rowHits = new int[h];
        boolean[][] purple = new boolean[h][w];
        for (int y = 0; y < h; y++) {
            int hits = 0;
            for (int x = 0; x < w; x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                boolean isPurple = b >= 55 && r >= 45 && Math.abs(r - b) <= 110 && g <= Math.max(r, b) - 8;
                purple[y][x] = isPurple;
                if (isPurple) hits++;
            }
            rowHits[y] = hits;
        }

        int rowThreshold = Math.max(18, w / 5);
        List<Integer> lineCenters = new ArrayList<Integer>();
        int y = 0;
        while (y < h) {
            if (rowHits[y] < rowThreshold) {
                y++;
                continue;
            }
            int y0 = y;
            int y1 = y;
            while (y1 + 1 < h && rowHits[y1 + 1] >= rowThreshold) y1++;
            lineCenters.add((y0 + y1) / 2);
            y = y1 + 1;
        }

        List<Rect> out = new ArrayList<Rect>();
        for (int i = 0; i + 1 < lineCenters.size(); i++) {
            int top = lineCenters.get(i);
            int bottom = lineCenters.get(i + 1);
            int gap = bottom - top;
            if (gap < Math.max(18, h / 28) || gap > Math.max(220, h / 4)) continue;

            int minX = w;
            int maxX = -1;
            for (int x = 0; x < w; x++) {
                if (purple[top][x] || purple[bottom][x]) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                }
            }
            if (maxX <= minX) continue;
            int width = maxX - minX + 1;
            if (width < w / 3) continue;

            int insetX = Math.max(2, Math.round(width * 0.006f));
            int insetY = Math.max(2, Math.round(gap * 0.08f));
            int ix = Math.max(0, minX + insetX);
            int iy = Math.max(0, top + insetY);
            int xEnd = Math.min(w - 1, maxX - insetX);
            int yEnd = Math.min(h - 1, bottom - insetY);
            if (xEnd <= ix || yEnd <= iy) continue;
            out.add(new Rect(ix, iy, xEnd - ix + 1, yEnd - iy + 1));

            i++; // consume pair as one frame
        }

        return dedupeOverlappingRects(out);
    }

    private static List<Rect> dedupeOverlappingRects(List<Rect> input) {
        if (input == null || input.isEmpty()) return input;
        List<Rect> sorted = new ArrayList<Rect>(input);
        Collections.sort(sorted, new Comparator<Rect>() {
            @Override
            public int compare(Rect a, Rect b) {
                int areaA = a.width * a.height;
                int areaB = b.width * b.height;
                return areaB - areaA;
            }
        });
        List<Rect> kept = new ArrayList<Rect>();
        for (Rect r : sorted) {
            boolean overlapsStrongly = false;
            for (Rect k : kept) {
                int ix0 = Math.max(r.x, k.x);
                int iy0 = Math.max(r.y, k.y);
                int ix1 = Math.min(r.x + r.width - 1, k.x + k.width - 1);
                int iy1 = Math.min(r.y + r.height - 1, k.y + k.height - 1);
                if (ix1 < ix0 || iy1 < iy0) continue;
                int inter = (ix1 - ix0 + 1) * (iy1 - iy0 + 1);
                int rArea = Math.max(1, r.width * r.height);
                int kArea = Math.max(1, k.width * k.height);
                float rOverlap = inter / (float) rArea;
                float kOverlap = inter / (float) kArea;
                if (rOverlap >= 0.80f || kOverlap >= 0.90f) {
                    overlapsStrongly = true;
                    break;
                }
            }
            if (!overlapsStrongly) kept.add(r);
        }
        return kept;
    }

    private static BufferedImage keepOnlyPurpleStaffInteriors(BufferedImage image, List<Rect> interiors) {
        BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        g.dispose();

        if (interiors == null || interiors.isEmpty()) {
            return out;
        }

        for (Rect r : interiors) {
            int x0 = Math.max(0, r.x);
            int y0 = Math.max(0, r.y);
            int x1 = Math.min(image.getWidth() - 1, r.x + r.width - 1);
            int y1 = Math.min(image.getHeight() - 1, r.y + r.height - 1);
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    out.setRGB(x, y, image.getRGB(x, y));
                }
            }
        }
        return out;
    }

    private static void savePngAndBase64(BufferedImage img, File basePathNoExt) throws Exception {
        File png = new File(basePathNoExt.getPath() + ".png");
        File b64 = new File(basePathNoExt.getPath() + ".png.b64");
        ImageIO.write(img, "png", png);
        byte[] bytes = Files.readAllBytes(png.toPath());
        String encoded = Base64.getEncoder().encodeToString(bytes);
        Files.write(b64.toPath(), encoded.getBytes(StandardCharsets.UTF_8));
    }

    private static void runStep9ReferenceComparisons(List<Rect> step9Rects, int w, int h) {
        try {
            File xml = new File("Free-trial-photo-2026-02-13-14-27-38.xml");
            File screenshot = new File("clear_sreenshot.png");
            if (!xml.exists() || !screenshot.exists()) {
                System.out.println("Step9 note comparison skipped: missing xml/clear_sreenshot.png files.");
                return;
            }

            List<NoteEvent> expected = parseXmlNotes(xml);
            OpenCvScoreProcessor processor = new OpenCvScoreProcessor();

            BufferedImage screenshotImg = ImageIO.read(screenshot);
            List<Rect> purpleStaves = resolveStaffInteriors(screenshotImg);
            BufferedImage croppedStaves = keepOnlyPurpleStaffInteriors(screenshotImg, purpleStaves);
            int[] screenshotArgb = croppedStaves.getRGB(0, 0, croppedStaves.getWidth(), croppedStaves.getHeight(), null, 0, croppedStaves.getWidth());
            OpenCvScoreProcessor.ProcessingResult screenshotResult = processor.processArgb(croppedStaves.getWidth(), croppedStaves.getHeight(), screenshotArgb,
                    "clear-screenshot-purple-cropped", OpenCvScoreProcessor.ProcessingOptions.defaults().withRequireOpenCv(true));
            reportStep9VsReference("clear-screenshot(step9->notes)", step9Rects, w, h, screenshotResult.piece.notes,
                    expected.subList(0, Math.min(step9Rects.size(), expected.size())));
            reportDirectReference("clear-screenshot(opencv, purple-cropped-staves)", screenshotResult.piece.notes, expected);
        } catch (Throwable t) {
            System.out.println("Step9 note comparison failed: " + t.getClass().getSimpleName() + " - " + t.getMessage());
        }
    }

    private static void reportStep9VsReference(String label,
                                               List<Rect> step9Rects,
                                               int w,
                                               int h,
                                               List<NoteEvent> recognized,
                                               List<NoteEvent> expectedSlice) {
        List<NoteEvent> byStep9 = new ArrayList<NoteEvent>();
        boolean[] used = new boolean[recognized.size()];
        for (Rect r : step9Rects) {
            float xn = (r.x + r.width * 0.5f) / Math.max(1f, (float) (w - 1));
            int best = -1;
            float bestDx = Float.MAX_VALUE;
            for (int i = 0; i < recognized.size(); i++) {
                if (used[i]) continue;
                float dx = Math.abs(recognized.get(i).x - xn);
                if (dx < bestDx) {
                    bestDx = dx;
                    best = i;
                }
            }
            if (best >= 0) {
                used[best] = true;
                byStep9.add(recognized.get(best));
            }
        }
        reportDirectReference(label, byStep9, expectedSlice);
    }

    private static void reportDirectReference(String label, List<NoteEvent> actual, List<NoteEvent> expected) {
        int n = Math.min(actual.size(), expected.size());
        int pitchOk = 0;
        for (int i = 0; i < n; i++) {
            NoteEvent a = actual.get(i);
            NoteEvent e = expected.get(i);
            if (safeEq(a.noteName, e.noteName) && a.octave == e.octave) pitchOk++;
        }
        System.out.println(label + ": actual=" + actual.size() + ", expected=" + expected.size()
                + ", pitchMatchPrefix=" + pitchOk + "/" + n);
    }

    private static boolean safeEq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private static List<NoteEvent> parseXmlNotes(File xmlFile) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        Document doc = dbf.newDocumentBuilder().parse(xmlFile);
        NodeList noteNodes = doc.getElementsByTagName("note");
        List<NoteEvent> out = new ArrayList<NoteEvent>();
        for (int i = 0; i < noteNodes.getLength(); i++) {
            Element note = (Element) noteNodes.item(i);
            if (note.getElementsByTagName("rest").getLength() > 0) continue;
            NodeList pitchNodes = note.getElementsByTagName("pitch");
            if (pitchNodes.getLength() == 0) continue;
            Element pitch = (Element) pitchNodes.item(0);
            String step = textOfFirst(pitch, "step");
            String alter = textOfFirst(pitch, "alter");
            String octaveText = textOfFirst(pitch, "octave");
            String duration = textOfFirst(note, "type");
            if (step == null || octaveText == null) continue;
            String name = step;
            if ("1".equals(alter)) name += "#";
            if ("-1".equals(alter)) name += "b";
            if (duration == null || duration.length() == 0) duration = "quarter";
            out.add(new NoteEvent(name, Integer.parseInt(octaveText.trim()), duration, 1 + out.size() / 4));
        }
        return out;
    }

    private static String textOfFirst(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        if (list.getLength() == 0) return null;
        return list.item(0).getTextContent();
    }

    private static BufferedImage drawStep10RecognizedOnStep9(BufferedImage step9View,
                                                           List<Rect> rects,
                                                           Mat horizontalMask) {
        BufferedImage out = new BufferedImage(step9View.getWidth(), step9View.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.drawImage(step9View, 0, 0, null);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setFont(new Font("SansSerif", Font.BOLD, 14));
            List<Step10Note> notes = computeStep10Notes(rects, horizontalMask);
            int labelBaseY = 20;
            for (int i = 0; i < notes.size(); i++) {
                Step10Note n = notes.get(i);
                int cx = n.rect.x + n.rect.width / 2;
                int cy = Math.round(n.staffY);
                int radiusX = Math.max(6, n.rect.width / 2);
                int radiusY = Math.max(5, n.rect.height / 2);

                g.setColor(new Color(20, 180, 20));
                g.setStroke(new BasicStroke(2f));
                g.drawOval(cx - radiusX, cy - radiusY, radiusX * 2, radiusY * 2);

                int tx = Math.max(0, cx - 12);
                int ty = labelBaseY + (i % 2) * 16;
                g.setColor(Color.WHITE);
                g.drawString(n.label, tx + 1, ty + 1);
                g.setColor(new Color(20, 180, 20));
                g.drawString(n.label, tx, ty);

                g.setColor(new Color(20, 180, 20, 150));
                g.setStroke(new BasicStroke(1f));
                g.drawLine(cx, cy - radiusY - 2, cx, ty + 4);
            }
        } finally {
            g.dispose();
        }
        return out;
    }

    private static List<Step10Note> computeStep10Notes(List<Rect> rects, Mat horizontalMask) {
        List<Step10Note> out = new ArrayList<Step10Note>();
        List<Float> lines = detectStaffLineCenters(horizontalMask);
        if (lines.size() < 5) return out;
        List<Rect> sorted = new ArrayList<Rect>(rects);
        Collections.sort(sorted, new Comparator<Rect>() {
            @Override
            public int compare(Rect a, Rect b) { return Integer.compare(a.x, b.x); }
        });
        float[] top5 = new float[]{lines.get(0), lines.get(1), lines.get(2), lines.get(3), lines.get(4)};
        for (int i = 0; i < sorted.size(); i++) {
            Rect r = sorted.get(i);
            float cy = r.y + r.height * 0.5f;
            int posIndex = nearestStaffPositionIndex(cy, top5);
            int stepFromBottom = 8 - posIndex;
            int midi = midiForTrebleStaffStep(stepFromBottom);
            String label = noteNameForMidi(midi) + octaveForMidi(midi);
            out.add(new Step10Note(r, label, cy, stepFromBottom, staffYForPositionIndex(posIndex, top5)));
        }
        return out;
    }

    private static void printStep10RecognizedPitches(List<Rect> rects, Mat horizontalMask, int staffSpacing) {
        List<Step10Note> notes = computeStep10Notes(rects, horizontalMask);
        if (notes.isEmpty()) {
            System.out.println("Step10(note print): unable to detect 5 staff lines.");
            return;
        }
        System.out.println("Step10: recognized notes by staff pitch (from step9 centers), count=" + notes.size() + ":");
        for (int i = 0; i < notes.size(); i++) {
            Step10Note n = notes.get(i);
            System.out.println("  #" + (i + 1) + " x=" + (n.rect.x + n.rect.width / 2) + " y=" + String.format(java.util.Locale.US, "%.2f", n.cy)
                    + " -> " + n.label + " (stepFromBottom=" + n.stepFromBottom + ")");
        }
    }

    private static List<Float> detectStaffLineCenters(Mat horizontalMask) {
        List<Integer> rows = new ArrayList<Integer>();
        int h = horizontalMask.rows();
        int w = horizontalMask.cols();
        for (int y = 0; y < h; y++) {
            int dark = 0;
            for (int x = 0; x < w; x++) {
                if (horizontalMask.get(y, x)[0] > 0) dark++;
            }
            if (dark >= Math.max(8, w / 14)) rows.add(y);
        }
        List<Float> centers = new ArrayList<Float>();
        if (rows.isEmpty()) return centers;
        int s0 = rows.get(0), p0 = rows.get(0);
        for (int i = 1; i < rows.size(); i++) {
            int y = rows.get(i);
            if (y == p0 + 1) p0 = y;
            else {
                centers.add((s0 + p0) * 0.5f);
                s0 = p0 = y;
            }
        }
        centers.add((s0 + p0) * 0.5f);
        Collections.sort(centers);
        if (centers.size() > 5) return new ArrayList<Float>(centers.subList(0, 5));
        return centers;
    }

    private static int nearestStaffPositionIndex(float cy, float[] linesY) {
        int bestIndex = 0;
        float bestDist = Float.MAX_VALUE;
        for (int i = 0; i < 5; i++) {
            float d = Math.abs(cy - linesY[i]);
            if (d < bestDist) { bestDist = d; bestIndex = i * 2; }
            if (i < 4) {
                float gapY = (linesY[i] + linesY[i + 1]) * 0.5f;
                float gd = Math.abs(cy - gapY);
                if (gd < bestDist) { bestDist = gd; bestIndex = i * 2 + 1; }
            }
        }
        return bestIndex;
    }

    private static int midiForTrebleStaffStep(int stepFromBottom) {
        String[] naturalCycle = new String[]{"C", "D", "E", "F", "G", "A", "B"};
        int baseIndex = 2;
        int noteIndex = baseIndex + stepFromBottom;
        int octaveShift = Math.floorDiv(noteIndex, 7);
        int idx = ((noteIndex % 7) + 7) % 7;
        int octave = 4 + octaveShift;
        return MusicNotation.midiFor(naturalCycle[idx], octave);
    }

    private static String noteNameForMidi(int midi) {
        String[] names = new String[]{"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
        int semitone = ((midi % 12) + 12) % 12;
        return names[semitone];
    }

    private static int octaveForMidi(int midi) {
        return Math.floorDiv(midi, 12) - 1;
    }

    private static class Step10Note {
        final Rect rect;
        final String label;
        final float cy;
        final int stepFromBottom;
        final float staffY;

        Step10Note(Rect rect, String label, float cy, int stepFromBottom, float staffY) {
            this.rect = rect;
            this.label = label;
            this.cy = cy;
            this.stepFromBottom = stepFromBottom;
            this.staffY = staffY;
        }
    }

    private static float staffYForPositionIndex(int posIndex, float[] linesY) {
        int clamped = Math.max(0, Math.min(8, posIndex));
        if ((clamped & 1) == 0) return linesY[clamped / 2];
        int upperLine = clamped / 2;
        return (linesY[upperLine] + linesY[upperLine + 1]) * 0.5f;
    }

    private static class BlobFilterResult {
        List<Rect> kept;
        List<Rect> removed;
    }

    private static class AnchorPoint {
        final float xNorm;
        final float yNorm;

        AnchorPoint(float xNorm, float yNorm) {
            this.xNorm = xNorm;
            this.yNorm = yNorm;
        }
    }

    private static class RecognitionProxyStats {
        int redExpected;
        int blobCount;
        int matchedExpected;
        int missedExpected;
        int unmatchedBlobs;
    }
}
