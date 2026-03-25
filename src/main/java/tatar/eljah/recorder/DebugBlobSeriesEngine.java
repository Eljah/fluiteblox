package tatar.eljah.recorder;

import android.graphics.Bitmap;
import android.graphics.Color;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

class DebugBlobSeriesEngine {
    static final int STAGE_COUNT = 7;
    private static volatile boolean openCvLoaded;

    private static void ensureOpenCvLoaded() {
        if (openCvLoaded) return;
        UnsatisfiedLinkError last = null;
        for (String lib : new String[]{"opencv_java460", "opencv_java4", "opencv_java"}) {
            try {
                System.loadLibrary(lib);
                openCvLoaded = true;
                return;
            } catch (UnsatisfiedLinkError e) {
                last = e;
            }
        }
        if (!openCvLoaded) {
            throw new IllegalStateException("OpenCV native library is not loaded", last);
        }
    }

    static class BlobSignature {
        final float xNorm;
        final float yNorm;
        final float areaNorm;

        BlobSignature(float xNorm, float yNorm, float areaNorm) {
            this.xNorm = xNorm;
            this.yNorm = yNorm;
            this.areaNorm = areaNorm;
        }
    }

    static class BlobInfo {
        final Rect rect;
        final BlobSignature signature;

        BlobInfo(Rect rect, BlobSignature signature) {
            this.rect = rect;
            this.signature = signature;
        }
    }

    static class DeletionEntry {
        final int sourceStage;
        final BlobSignature signature;

        DeletionEntry(int sourceStage, BlobSignature signature) {
            this.sourceStage = sourceStage;
            this.signature = signature;
        }
    }

    static class StageState {
        final Bitmap preview;
        final ArrayList<BlobInfo> sourceBlobs;
        final ArrayList<BlobInfo> blobs;

        StageState(Bitmap preview, ArrayList<BlobInfo> sourceBlobs, ArrayList<BlobInfo> blobs) {
            this.preview = preview;
            this.sourceBlobs = sourceBlobs;
            this.blobs = blobs;
        }
    }

    static class Session {
        final Bitmap source;
        final ArrayList<DeletionEntry> deletions = new ArrayList<DeletionEntry>();
        final StageState[] stages = new StageState[STAGE_COUNT];

        Session(Bitmap source) {
            this.source = source;
        }
    }

    Session rebuildSession(Bitmap source, Session prev) {
        ensureOpenCvLoaded();
        Session next = new Session(source);
        if (prev != null) next.deletions.addAll(prev.deletions);

        StageBuffer[] stages = buildStages(source);
        try {
            for (int i = 0; i < STAGE_COUNT; i++) {
                StageBuffer stage = stages[i];
                ArrayList<BlobInfo> sourceBlobs = detectBlobs(stage.mask);
                applyDeletionsForStage(i + 1, stage.mask, next.deletions, stage.mask.cols(), stage.mask.rows());
                ArrayList<BlobInfo> filteredBlobs = detectBlobs(stage.mask);
                Bitmap preview = stage.preview != null ? stage.preview : toPreview(stage.mask);
                if (i == 5) {
                    preview = drawAreaOrderPreview(stage.mask, filteredBlobs);
                }
                next.stages[i] = new StageState(preview, sourceBlobs, filteredBlobs);
                if (i == 0) {
                    pruneInvalidBySourceStage(next.deletions, next.stages[i].sourceBlobs);
                }
            }
            return next;
        } finally {
            for (StageBuffer s : stages) {
                if (s != null && s.mask != null) s.mask.release();
            }
        }
    }

    Bitmap buildEditedBitmap(Session session) {
        Bitmap out = session.source.copy(Bitmap.Config.ARGB_8888, true);
        int w = out.getWidth();
        int h = out.getHeight();
        for (DeletionEntry d : session.deletions) {
            int stageIdx = Math.max(0, Math.min(STAGE_COUNT - 1, d.sourceStage - 1));
            StageState stageState = session.stages[stageIdx];
            if (stageState == null) continue;
            BlobInfo b = findBest(stageState.sourceBlobs, d.signature);
            if (b == null) continue;
            Rect r = b.rect;
            int x0 = Math.max(0, r.x);
            int y0 = Math.max(0, r.y);
            int x1 = Math.min(w - 1, r.x + r.width - 1);
            int y1 = Math.min(h - 1, r.y + r.height - 1);
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    out.setPixel(x, y, Color.WHITE);
                }
            }
        }
        return out;
    }

    private void pruneInvalidBySourceStage(ArrayList<DeletionEntry> deletions, List<BlobInfo> sourceBlobs) {
        Iterator<DeletionEntry> it = deletions.iterator();
        while (it.hasNext()) {
            DeletionEntry d = it.next();
            if (d.sourceStage != 1) continue;
            if (findBest(sourceBlobs, d.signature) == null) {
                it.remove();
            }
        }
    }

    private void applyDeletionsForStage(int stageNumber,
                                        Mat stageMask,
                                        ArrayList<DeletionEntry> deletions,
                                        int w,
                                        int h) {
        for (DeletionEntry d : deletions) {
            if (d.sourceStage > stageNumber) continue;
            ArrayList<BlobInfo> blobs = detectBlobs(stageMask);
            BlobInfo best = findBest(blobs, d.signature);
            if (best == null) continue;
            Rect r = best.rect;
            int x0 = Math.max(0, r.x);
            int y0 = Math.max(0, r.y);
            int x1 = Math.min(w - 1, r.x + r.width - 1);
            int y1 = Math.min(h - 1, r.y + r.height - 1);
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    stageMask.put(y, x, 0);
                }
            }
        }
    }

    private BlobInfo findBest(List<BlobInfo> blobs, BlobSignature sig) {
        BlobInfo best = null;
        float bestScore = Float.MAX_VALUE;
        for (BlobInfo b : blobs) {
            float dx = b.signature.xNorm - sig.xNorm;
            float dy = b.signature.yNorm - sig.yNorm;
            float da = Math.abs(b.signature.areaNorm - sig.areaNorm);
            float score = dx * dx + dy * dy + da * 3f;
            if (score < bestScore) {
                bestScore = score;
                best = b;
            }
        }
        return bestScore <= 0.0035f ? best : null;
    }

    private StageBuffer[] buildStages(Bitmap source) {
        Mat src = new Mat();
        Utils.bitmapToMat(source, src);
        Mat gray = new Mat();
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY);
        src.release();

        Mat binary = new Mat();
        Imgproc.adaptiveThreshold(gray, binary, 255, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV, 31, 7);
        gray.release();

        int w = binary.cols();
        int h = binary.rows();
        Mat horizontal = new Mat();
        Mat vertical = new Mat();
        Mat kH = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(Math.max(18, w / 12), 1));
        Mat kV = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(1, Math.max(10, h / 16)));
        Imgproc.morphologyEx(binary, horizontal, Imgproc.MORPH_OPEN, kH);
        Imgproc.morphologyEx(binary, vertical, Imgproc.MORPH_OPEN, kV);
        kH.release();
        kV.release();

        Mat s1 = new Mat();
        Core.subtract(binary, horizontal, s1);
        Core.subtract(s1, vertical, s1);

        Mat s2 = new Mat();
        Mat kStem = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(1, Math.max(8, h / 14)));
        Mat stem = new Mat();
        Imgproc.morphologyEx(s1, stem, Imgproc.MORPH_OPEN, kStem);
        Core.subtract(s1, stem, s2);
        kStem.release();
        stem.release();
        Mat kSinglePixelEat = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(2, 2));
        Imgproc.morphologyEx(s2, s2, Imgproc.MORPH_OPEN, kSinglePixelEat);
        kSinglePixelEat.release();

        Mat s3 = new Mat();
        Imgproc.GaussianBlur(s2, s3, new Size(5, 5), 0);
        Imgproc.threshold(s3, s3, 142, 255, Imgproc.THRESH_BINARY);
        Core.max(s3, s2, s3);
        Imgproc.threshold(s3, s3, 127, 255, Imgproc.THRESH_BINARY);

        Mat s4 = s3.clone();
        ArrayList<BlobInfo> step5Blobs = detectBlobs(s4);
        for (BlobInfo b : step5Blobs) {
            int hh = Math.max(1, b.rect.height);
            int ww = Math.max(1, b.rect.width);
            if (hh <= 4 && ww >= hh * 4) {
                Imgproc.rectangle(s4, b.rect.tl(), b.rect.br(), new Scalar(0), -1);
            }
        }
        Imgproc.threshold(s4, s4, 127, 255, Imgproc.THRESH_BINARY);

        Mat s5 = new Mat();
        Mat kMerge = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(1, 3));
        Imgproc.morphologyEx(s4, s5, Imgproc.MORPH_CLOSE, kMerge);
        kMerge.release();
        Imgproc.threshold(s5, s5, 127, 255, Imgproc.THRESH_BINARY);

        Mat s6 = s5.clone();
        Mat s7 = s5.clone();
        ArrayList<BlobInfo> aspectBlobs = detectBlobs(s7);
        for (BlobInfo b : aspectBlobs) {
            float aspect = b.rect.width / Math.max(1f, (float) b.rect.height);
            if (aspect > 2.6f) {
                Imgproc.rectangle(s7, b.rect.tl(), b.rect.br(), new Scalar(0), -1);
            }
        }
        Imgproc.threshold(s7, s7, 127, 255, Imgproc.THRESH_BINARY);

        binary.release();
        horizontal.release();
        vertical.release();
        return new StageBuffer[]{
                new StageBuffer(s1, null),
                new StageBuffer(s2, null),
                new StageBuffer(s3, null),
                new StageBuffer(s4, null),
                new StageBuffer(s5, null),
                new StageBuffer(s6, null),
                new StageBuffer(s7, null)
        };
    }

    private Bitmap toPreview(Mat binary) {
        Bitmap out = Bitmap.createBitmap(binary.cols(), binary.rows(), Bitmap.Config.ARGB_8888);
        Mat bgr = new Mat();
        Imgproc.cvtColor(binary, bgr, Imgproc.COLOR_GRAY2RGBA);
        Utils.matToBitmap(bgr, out);
        bgr.release();
        return out;
    }

    private Bitmap drawAreaOrderPreview(Mat mask, List<BlobInfo> blobs) {
        Bitmap out = toPreview(mask);
        Mat rgba = new Mat();
        Utils.bitmapToMat(out, rgba);
        try {
            ArrayList<BlobInfo> ordered = new ArrayList<BlobInfo>(blobs);
            java.util.Collections.sort(ordered, new java.util.Comparator<BlobInfo>() {
                @Override
                public int compare(BlobInfo a, BlobInfo b) {
                    int aa = Math.max(1, a.rect.width) * Math.max(1, a.rect.height);
                    int bb = Math.max(1, b.rect.width) * Math.max(1, b.rect.height);
                    return Integer.compare(bb, aa);
                }
            });
            int rank = 1;
            for (BlobInfo b : ordered) {
                Imgproc.rectangle(rgba, b.rect.tl(), b.rect.br(), new Scalar(0, 165, 255, 255), 1);
                Imgproc.putText(
                        rgba,
                        String.valueOf(rank++),
                        b.rect.tl(),
                        Imgproc.FONT_HERSHEY_SIMPLEX,
                        0.35,
                        new Scalar(255, 80, 80, 255),
                        1
                );
            }
            Utils.matToBitmap(rgba, out);
            return out;
        } finally {
            rgba.release();
        }
    }

    private ArrayList<BlobInfo> detectBlobs(Mat mask) {
        ArrayList<BlobInfo> out = new ArrayList<BlobInfo>();
        Mat contoursInput = mask.clone();
        Mat hierarchy = new Mat();
        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        try {
            Imgproc.findContours(contoursInput, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
            int w = Math.max(1, mask.cols());
            int h = Math.max(1, mask.rows());
            float areaBase = Math.max(1f, w * h);
            for (MatOfPoint c : contours) {
                Rect r = Imgproc.boundingRect(c);
                if (r.width < 2 || r.height < 2) {
                    c.release();
                    continue;
                }
                float xNorm = (r.x + r.width * 0.5f) / Math.max(1f, (float) (w - 1));
                float yNorm = (r.y + r.height * 0.5f) / Math.max(1f, (float) (h - 1));
                float areaNorm = (r.width * r.height) / areaBase;
                out.add(new BlobInfo(r, new BlobSignature(xNorm, yNorm, areaNorm)));
                c.release();
            }
            return out;
        } finally {
            contoursInput.release();
            hierarchy.release();
        }
    }
    private static class StageBuffer {
        final Mat mask;
        Bitmap preview;

        StageBuffer(Mat mask, Bitmap preview) {
            this.mask = mask;
            this.preview = preview;
        }
    }
}
