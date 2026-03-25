package tatar.eljah.recorder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class BlobDebugOverlayView extends View {
    public interface OnBlobTapListener {
        void onBlobTapped(int index);
    }

    private final ArrayList<RectF> blobs = new ArrayList<RectF>();
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private GestureDetector gestureDetector;
    private OnBlobTapListener listener;

    public BlobDebugOverlayView(Context context) {
        super(context);
        init();
    }

    public BlobDebugOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BlobDebugOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(3f);
        stroke.setColor(Color.argb(220, 255, 165, 0));
        gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                notifyHit(e);
            }
        });
        gestureDetector.setIsLongpressEnabled(true);
    }

    public void setOnBlobTapListener(OnBlobTapListener listener) {
        this.listener = listener;
    }

    public void setBlobs(List<RectF> items) {
        blobs.clear();
        if (items != null) blobs.addAll(items);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (int i = 0; i < blobs.size(); i++) {
            RectF r = blobs.get(i);
            canvas.drawRect(r, stroke);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event);
    }

    private boolean notifyHit(MotionEvent event) {
        if (event == null) return false;
        float x = event.getX();
        float y = event.getY();
        for (int i = blobs.size() - 1; i >= 0; i--) {
            if (!blobs.get(i).contains(x, y)) continue;
            if (listener != null && event.getActionMasked() != MotionEvent.ACTION_CANCEL) {
                listener.onBlobTapped(i);
            }
            return true;
        }
        return false;
    }
}
