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

import javax.imageio.ImageIO;
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

    private static final float HARD_ROUND_LARGE_BOUNDARY = 50.364708f;

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
        File input = new File("experiment.png");
        if (!input.exists()) {
            throw new AssertionError("Missing experiment.png");
        }
        BufferedImage image = ImageIO.read(input);
        if (image == null) {
            throw new AssertionError("Unable to decode experiment.png");
        }

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
            // Step4 must be monotonic relative to step3: do not resurrect black pixels erased on step3.
            Core.max(blurRebinarized, step3PipelineMask, blurRebinarized);
            // Final hard rebinarization to cut gray/stripe leftovers introduced by blur/open+guard combinations.
            Imgproc.threshold(blurRebinarized, blurRebinarized, 127, 255, Imgproc.THRESH_BINARY);
            blurRebinarized.copyTo(step4PipelineMask);

            // Step5 must consume exact Step4 output.
            // Step5: vertical-only merge to join note-head halves split by removed staff lines.
            step4PipelineMask.copyTo(mergedNarrowGaps);

            // Vertical close to merge note-head halves split by removed staff line (gap ~= lineThickness).
            int mergeHeight = Math.max(3, lineThickness * 2 + 1);
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

            BufferedImage filteredBlobView = drawFilteredBlobs(gray, stage2Mono.kept, mergeRemoved(stage2Overlap.removed, stage2Mono.removed));
            // Sorted-by-area view must use merged step5 data.
            BufferedImage sortedByAreaView = drawAreaOrderOnMergedMask(mergedView, stage2);
            List<Rect> topRoundLarge = selectTopRoundLarge(stage2, 13);
            BufferedImage roundLargeView = drawRoundLargeSelection(mergedView, stage2, topRoundLarge);
            List<Rect> recognitionCandidates = filterByHardBoundary(topRoundLarge, HARD_ROUND_LARGE_BOUNDARY);
            BufferedImage allBlobView = drawBlobsOnGray(gray, recognitionCandidates, new Scalar(0, 120, 255));

            File outDir = new File("docs/diagnostics");
            if (!outDir.exists() && !outDir.mkdirs()) {
                throw new IllegalStateException("Cannot create output directory: " + outDir.getAbsolutePath());
            }

            savePngAndBase64(linesOverlay, new File(outDir, "experiment_step1_lines_overlay"));
            savePngAndBase64(subtractedView, new File(outDir, "experiment_step2_lines_subtracted"));
            savePngAndBase64(stemSubtractedView, new File(outDir, "experiment_step3_stems_subtracted"));
            savePngAndBase64(blurThinView, new File(outDir, "experiment_step4_thin_artifacts_blurred"));
            savePngAndBase64(mergedView, new File(outDir, "experiment_step5_blobs_merged_narrow_gaps"));
            savePngAndBase64(sortedByAreaView, new File(outDir, "experiment_step6_blobs_sorted_area_annotated"));
            savePngAndBase64(roundLargeView, new File(outDir, "experiment_step7_noteheads_round_large_top13"));
            savePngAndBase64(allBlobView, new File(outDir, "experiment_step8_blobs_all"));
            savePngAndBase64(filteredBlobView, new File(outDir, "experiment_step9_blobs_filtered_overlap_monophony"));

            RecognitionProxyStats before = computeProxyStats(image, stage0Mono.kept);
            RecognitionProxyStats afterBlur = computeProxyStats(image, stage1Mono.kept);
            RecognitionProxyStats afterMerge = computeProxyStats(image, stage2Mono.kept);

            System.out.println("Artifacts exported to docs/diagnostics");
            System.out.println("Stage0(noStems) blobs: raw=" + stage0.size() + ", overlapKept=" + stage0Overlap.kept.size() + ", monoKept=" + stage0Mono.kept.size());
            System.out.println("Stage1(blur thin) blobs: raw=" + stage1.size() + ", overlapKept=" + stage1Overlap.kept.size() + ", monoKept=" + stage1Mono.kept.size());
            System.out.println("Stage2(merge narrow gaps) blobs: raw=" + stage2.size() + ", overlapKept=" + stage2Overlap.kept.size() + ", monoKept=" + stage2Mono.kept.size());
            System.out.println("Step8(blobs from step7 over hard boundary=" + HARD_ROUND_LARGE_BOUNDARY + "): " + recognitionCandidates.size());
            printProxyStats("Before new steps (noStems)", before);
            printProxyStats("After blur thin artifacts", afterBlur);
            printProxyStats("After merge narrow gaps", afterMerge);
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
            if (kH != null) kH.release();
            if (kV != null) kV.release();
            if (kStem != null) kStem.release();
            if (kThinErase != null) kThinErase.release();
            if (kSinglePixelEat != null) kSinglePixelEat.release();
            if (kMergeV != null) kMergeV.release();
        }
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



    private static List<Rect> filterByHardBoundary(List<Rect> rects, float boundary) {
        List<Rect> out = new ArrayList<Rect>();
        for (Rect r : rects) {
            if (roundLargeScore(r) >= boundary) {
                out.add(r);
            }
        }
        return out.isEmpty() ? new ArrayList<Rect>(rects) : out;
    }

    private static List<Rect> selectTopRoundLarge(List<Rect> rects, int topN) {
        List<Rect> sorted = new ArrayList<Rect>(rects);
        Collections.sort(sorted, new Comparator<Rect>() {
            @Override
            public int compare(Rect a, Rect b) {
                return Float.compare(roundLargeScore(b), roundLargeScore(a));
            }
        });
        if (sorted.size() > topN) {
            return new ArrayList<Rect>(sorted.subList(0, topN));
        }
        return sorted;
    }

    private static float roundLargeScore(Rect r) {
        float area = (float) Math.max(1.0, r.area());
        float aspect = r.width / (float) Math.max(1, r.height);
        float roundness = 1.0f / (1.0f + Math.abs(aspect - 1.0f));
        return area * (0.5f + 0.5f * roundness);
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

    private static void savePngAndBase64(BufferedImage img, File basePathNoExt) throws Exception {
        File png = new File(basePathNoExt.getPath() + ".png");
        File b64 = new File(basePathNoExt.getPath() + ".png.b64");
        ImageIO.write(img, "png", png);
        byte[] bytes = Files.readAllBytes(png.toPath());
        String encoded = Base64.getEncoder().encodeToString(bytes);
        Files.write(b64.toPath(), encoded.getBytes(StandardCharsets.UTF_8));
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
