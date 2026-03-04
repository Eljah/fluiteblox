package tatar.eljah.recorder;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class FirstStaffConnectivityOverlayRenderer {

    private static class ConnectivityResult {
        boolean connected;
        boolean[][] visited;
        int x0;
        int y0;
    }

    public static void main(String[] args) throws Exception {
        String inPath = args.length > 0 ? args[0] : "clear_sreenshot.png";
        String outPath = args.length > 1 ? args[1] : "target/first_staff_connectivity_overlay.png";

        BufferedImage src = ImageIO.read(new File(inPath));
        if (src == null) {
            throw new IllegalArgumentException("Unable to read input image: " + inPath);
        }

        int w = src.getWidth();
        int h = src.getHeight();
        int[] argb = src.getRGB(0, 0, w, h, null, 0, w);

        OpenCvScoreProcessor processor = new OpenCvScoreProcessor();
        OpenCvScoreProcessor.ProcessingOptions options = new OpenCvScoreProcessor.ProcessingOptions(
                7, 3, 0.5f, false, false, 0.6f, 4.0f, 0.18f, 0.9f, 0.32f, false, 0.55f, null, true
        );
        OpenCvScoreProcessor.ProcessingResult result = processor.processArgb(w, h, argb, "overlay", options);

        int topIndex = 0;
        List<NoteEvent> topNotes = new ArrayList<NoteEvent>();
        for (NoteEvent n : result.piece.notes) {
            if (nearestCorridorIndex(n.y, result.staffCorridors) == topIndex) {
                topNotes.add(n);
            }
        }
        topNotes.sort(new Comparator<NoteEvent>() {
            @Override
            public int compare(NoteEvent a, NoteEvent b) {
                return Float.compare(a.x, b.x);
            }
        });

        boolean[][] binary = makeBinary(src);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(src, 0, 0, null);

        OpenCvScoreProcessor.StaffCorridor c = result.staffCorridors.isEmpty() ? null : result.staffCorridors.get(topIndex);
        float spacing = c == null ? (h / 24f) : ((c.bottom - c.top) * h / 8f);

        int idx = 1;
        for (NoteEvent n : topNotes) {
            int x = clamp(Math.round(n.x * (w - 1)), 0, w - 1);
            int y = clamp(Math.round(n.y * (h - 1)), 0, h - 1);
            float[] lines = estimateStaffLinesAtX(c, h, spacing);
            int nearestLine = nearestLineIndex(y, lines);
            int lineY = clamp(Math.round(lines[nearestLine]), 1, h - 2);

            int xPad = Math.max(3, Math.round(spacing / 3f));
            int yPad = Math.max(2, Math.round(spacing / 3f));
            int x0 = clamp(x - xPad, 0, w - 1);
            int x1 = clamp(x + xPad, 0, w - 1);

            ConnectivityResult above = whiteConnection(binary, x0, x1, clamp(lineY - yPad, 0, h - 1), clamp(lineY - 1, 0, h - 1));
            ConnectivityResult below = whiteConnection(binary, x0, x1, clamp(lineY + 1, 0, h - 1), clamp(lineY + yPad, 0, h - 1));

            paintVisited(out, above, new Color(80, 180, 255, 110));
            paintVisited(out, below, new Color(255, 170, 60, 110));

            g.setColor(above.connected ? new Color(60, 180, 75, 210) : new Color(220, 20, 60, 210));
            g.setStroke(new BasicStroke(1.2f));
            g.drawRect(x0, clamp(lineY - yPad, 0, h - 1), Math.max(1, x1 - x0), Math.max(1, yPad - 1));

            g.setColor(below.connected ? new Color(60, 180, 75, 210) : new Color(220, 20, 60, 210));
            g.drawRect(x0, clamp(lineY + 1, 0, h - 1), Math.max(1, x1 - x0), Math.max(1, yPad - 1));

            int side;
            if (above.connected && !below.connected) side = -1;
            else if (!above.connected && below.connected) side = 1;
            else side = 0;

            Color noteColor = side < 0 ? new Color(0, 200, 255, 220) : (side > 0 ? new Color(255, 90, 220, 220) : new Color(255, 235, 59, 220));
            g.setColor(noteColor);
            g.fillOval(x - 4, y - 4, 8, 8);

            g.setColor(new Color(25, 25, 25, 235));
            g.drawString("#" + idx, x + 6, y - 6);
            idx++;
        }

        g.dispose();
        File outFile = new File(outPath);
        if (outFile.getParentFile() != null) outFile.getParentFile().mkdirs();
        ImageIO.write(out, "png", outFile);
        System.out.println("Overlay written: " + outFile.getPath() + " notes=" + topNotes.size());
    }

    private static void paintVisited(BufferedImage out, ConnectivityResult r, Color color) {
        if (r == null || r.visited == null) return;
        int rgba = color.getRGB();
        for (int yy = 0; yy < r.visited.length; yy++) {
            for (int xx = 0; xx < r.visited[yy].length; xx++) {
                if (r.visited[yy][xx]) {
                    int x = r.x0 + xx;
                    int y = r.y0 + yy;
                    if (x >= 0 && x < out.getWidth() && y >= 0 && y < out.getHeight()) {
                        out.setRGB(x, y, blend(out.getRGB(x, y), rgba));
                    }
                }
            }
        }
    }

    private static int blend(int base, int over) {
        int a = (over >>> 24) & 0xFF;
        float af = a / 255f;
        int br = (base >>> 16) & 0xFF;
        int bg = (base >>> 8) & 0xFF;
        int bb = base & 0xFF;
        int or = (over >>> 16) & 0xFF;
        int og = (over >>> 8) & 0xFF;
        int ob = over & 0xFF;
        int r = Math.round(or * af + br * (1f - af));
        int g = Math.round(og * af + bg * (1f - af));
        int b = Math.round(ob * af + bb * (1f - af));
        return (0xFF << 24) | (r << 16) | (g << 8) | b;
    }

    private static ConnectivityResult whiteConnection(boolean[][] binary, int x0, int x1, int y0, int y1) {
        ConnectivityResult res = new ConnectivityResult();
        if (x1 <= x0 || y1 < y0) return res;

        int w = x1 - x0 + 1;
        int h = y1 - y0 + 1;
        boolean[][] visited = new boolean[h][w];
        ArrayDeque<int[]> q = new ArrayDeque<int[]>();

        for (int y = 0; y < h; y++) {
            if (!binary[y0 + y][x0]) {
                visited[y][0] = true;
                q.add(new int[]{0, y});
            }
        }

        int[] dx = {1, -1, 0, 0};
        int[] dy = {0, 0, 1, -1};
        while (!q.isEmpty()) {
            int[] p = q.removeFirst();
            int cx = p[0], cy = p[1];
            if (cx == w - 1) {
                res.connected = true;
            }
            for (int i = 0; i < 4; i++) {
                int nx = cx + dx[i], ny = cy + dy[i];
                if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                if (visited[ny][nx]) continue;
                if (binary[y0 + ny][x0 + nx]) continue;
                visited[ny][nx] = true;
                q.add(new int[]{nx, ny});
            }
        }

        res.visited = visited;
        res.x0 = x0;
        res.y0 = y0;
        return res;
    }

    private static float[] estimateStaffLinesAtX(OpenCvScoreProcessor.StaffCorridor c, int h, float spacing) {
        float top = c == null ? h * 0.2f : c.top * h;
        float start = top + spacing * 2f;
        float[] lines = new float[5];
        for (int i = 0; i < 5; i++) lines[i] = start + i * spacing;
        return lines;
    }

    private static int nearestLineIndex(int y, float[] lines) {
        int best = 0;
        float bestDist = Float.MAX_VALUE;
        for (int i = 0; i < lines.length; i++) {
            float d = Math.abs(y - lines[i]);
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }

    private static int nearestCorridorIndex(float y, List<OpenCvScoreProcessor.StaffCorridor> corridors) {
        if (corridors == null || corridors.isEmpty()) return -1;
        int best = 0;
        float bestDist = Float.MAX_VALUE;
        for (int i = 0; i < corridors.size(); i++) {
            OpenCvScoreProcessor.StaffCorridor c = corridors.get(i);
            float d = Math.abs(y - (c.top + c.bottom) * 0.5f);
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }

    private static boolean[][] makeBinary(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        boolean[][] out = new boolean[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = src.getRGB(x, y);
                int r = (c >> 16) & 0xFF;
                int g = (c >> 8) & 0xFF;
                int b = c & 0xFF;
                int gray = (r * 30 + g * 59 + b * 11) / 100;
                out[y][x] = gray < 145;
            }
        }
        return out;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
