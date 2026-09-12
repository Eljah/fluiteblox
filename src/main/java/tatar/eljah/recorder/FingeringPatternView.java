package tatar.eljah.recorder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

public class FingeringPatternView extends View {
    private static final int CLOSED = 1;
    private static final int HALF = 2;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int[] holes = new int[7];

    public FingeringPatternView(Context context) {
        super(context);
        setPattern("");
    }

    public void setPattern(String pattern) {
        int index = 0;
        for (int i = 0; i < holes.length; i++) {
            holes[i] = 0;
        }
        if (pattern != null) {
            for (int i = 0; i < pattern.length() && index < holes.length; i++) {
                char ch = pattern.charAt(i);
                if (ch == '|') {
                    continue;
                }
                holes[index++] = ch == 'x' ? CLOSED : (ch == 'h' ? HALF : 0);
            }
        }
        setVisibility(index == 0 ? GONE : VISIBLE);
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(dp(174), dp(34));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = dp(18);
        float gap = dp(5);
        float groupGap = dp(12);
        float top = (getHeight() - size) / 2f;
        float x = 0f;
        for (int i = 0; i < holes.length; i++) {
            if (i == 3) {
                x += groupGap;
            }
            drawHole(canvas, x, top, size, holes[i]);
            x += size + gap;
        }
    }

    private void drawHole(Canvas canvas, float left, float top, float size, int state) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(255, 239, 153));
        canvas.drawRect(left, top, left + size, top + size, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setColor(Color.rgb(42, 30, 18));
        canvas.drawRect(left, top, left + size, top + size, paint);
        if (state == CLOSED) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(42, 30, 18));
            canvas.drawRect(left + dp(3), top + dp(3), left + size - dp(3), top + size - dp(3), paint);
        } else if (state == HALF) {
            Path path = new Path();
            path.moveTo(left + dp(3), top + dp(3));
            path.lineTo(left + size - dp(3), top + dp(3));
            path.lineTo(left + dp(3), top + size - dp(3));
            path.close();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(42, 30, 18));
            canvas.drawPath(path, paint);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
