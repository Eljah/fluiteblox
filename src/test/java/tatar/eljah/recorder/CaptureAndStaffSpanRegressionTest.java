package tatar.eljah.recorder;

import org.opencv.core.CvType;
import org.opencv.core.Mat;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class CaptureAndStaffSpanRegressionTest {

    public static void main(String[] args) throws Exception {
        testGallerySampleSize();
        testStaffSpanNormalizationUsesLongestLength();
        testPhotoProcessingOnOriginalGalleryImage();
        System.out.println("Capture/staff regression passed.");
    }

    private static void testGallerySampleSize() {
        assertEquals(1, calculateInSampleSize(1280, 960, 1600), "sample size for baseline photo");
        assertEquals(4, calculateInSampleSize(6000, 4000, 1600), "sample size for big gallery image");
    }

    private static void testStaffSpanNormalizationUsesLongestLength() throws Exception {
        OpenCvScoreProcessor processor = new OpenCvScoreProcessor();
        Class<?> groupClass = Class.forName("tatar.eljah.recorder.OpenCvScoreProcessor$StaffGroup");
        Constructor<?> ctor = groupClass.getDeclaredConstructor();
        ctor.setAccessible(true);

        Object g1 = ctor.newInstance();
        Object g2 = ctor.newInstance();

        setInt(groupClass, g1, "xStart", 20);
        setInt(groupClass, g1, "xEnd", 180);
        setLines(groupClass, g1, new float[]{20f, 30f, 40f, 50f, 60f});

        setInt(groupClass, g2, "xStart", 40);
        setInt(groupClass, g2, "xEnd", 260);
        setLines(groupClass, g2, new float[]{100f, 110f, 120f, 130f, 140f});

        List<Object> groups = new ArrayList<Object>();
        groups.add(g1);
        groups.add(g2);

        Mat staffMask = Mat.zeros(180, 300, CvType.CV_8UC1);
        Method rebuild = OpenCvScoreProcessor.class.getDeclaredMethod("rebuildStaffMaskFromGroups", Mat.class, List.class, int.class, int.class);
        rebuild.setAccessible(true);
        rebuild.invoke(processor, staffMask, groups, 300, 180);

        int start1 = getInt(groupClass, g1, "xStart");
        int end1 = getInt(groupClass, g1, "xEnd");
        int start2 = getInt(groupClass, g2, "xStart");
        int end2 = getInt(groupClass, g2, "xEnd");

        assertEquals(20, start1, "group1 start");
        assertEquals(20, start2, "group2 start");
        assertEquals(221, end1 - start1 + 1, "group1 normalized width");
        assertEquals(221, end2 - start2 + 1, "group2 normalized width");

        staffMask.release();
    }

    private static void testPhotoProcessingOnOriginalGalleryImage() throws Exception {
        File photo = new File("photo_2026-02-13_14-27-38.jpg");
        if (!photo.exists()) {
            throw new AssertionError("photo_2026-02-13_14-27-38.jpg is missing");
        }
        BufferedImage image = ImageIO.read(photo);
        if (image == null) {
            throw new AssertionError("Unable to decode photo_2026-02-13_14-27-38.jpg");
        }
        int width = image.getWidth();
        int height = image.getHeight();
        int[] argb = image.getRGB(0, 0, width, height, null, 0, width);

        OpenCvScoreProcessor.ProcessingResult result = new OpenCvScoreProcessor().processArgb(width, height, argb, "gallery-regression");
        if (result == null || result.piece == null || result.piece.notes == null || result.piece.notes.isEmpty()) {
            throw new AssertionError("Recognition returned no notes for gallery regression image");
        }
    }


    private static int calculateInSampleSize(int width, int height, int maxDim) {
        if (width <= 0 || height <= 0 || maxDim <= 0) {
            return 1;
        }
        int sample = 1;
        while ((width / sample) > maxDim || (height / sample) > maxDim) {
            sample <<= 1;
        }
        return Math.max(1, sample);
    }

    private static void setLines(Class<?> groupClass, Object group, float[] values) throws Exception {
        Field f = groupClass.getDeclaredField("linesY");
        f.setAccessible(true);
        f.set(group, values);
    }

    private static void setInt(Class<?> clazz, Object obj, String field, int value) throws Exception {
        Field f = clazz.getDeclaredField(field);
        f.setAccessible(true);
        f.setInt(obj, value);
    }

    private static int getInt(Class<?> clazz, Object obj, String field) throws Exception {
        Field f = clazz.getDeclaredField(field);
        f.setAccessible(true);
        return f.getInt(obj);
    }

    private static void assertEquals(int expected, int actual, String msg) {
        if (expected != actual) {
            throw new AssertionError(msg + " expected=" + expected + " actual=" + actual);
        }
    }
}
