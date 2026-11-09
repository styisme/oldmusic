package com.example.oldmusic;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * 轻量歌词 View：手写 onDraw，适配 Android 2.3.3，不依赖 RecyclerView。
 */
public class LyricView extends View {

    private List<LrcParser.Line> lines = new ArrayList<LrcParser.Line>();
    private int current = -1;
    private final Paint normalPaint;
    private final Paint highlightPaint;
    private final Paint emptyPaint;
    private final int lineHeight = 64;

    private boolean userScrolling = false;
    private float touchLastY = 0f;
    private int manualOffset = 0;

    public LyricView(Context context) {
        super(context);
        normalPaint = newPaint(Color.parseColor("#8AFFFFFF"), 36f);
        highlightPaint = newPaint(Color.parseColor("#FFFFFFFF"), 44f);
        emptyPaint = newPaint(Color.parseColor("#66FFFFFF"), 34f);
    }

    public LyricView(Context context, AttributeSet attrs) {
        super(context, attrs);
        normalPaint = newPaint(Color.parseColor("#8AFFFFFF"), 36f);
        highlightPaint = newPaint(Color.parseColor("#FFFFFFFF"), 44f);
        emptyPaint = newPaint(Color.parseColor("#66FFFFFF"), 34f);
    }

    public LyricView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        normalPaint = newPaint(Color.parseColor("#8AFFFFFF"), 36f);
        highlightPaint = newPaint(Color.parseColor("#FFFFFFFF"), 44f);
        emptyPaint = newPaint(Color.parseColor("#66FFFFFF"), 34f);
    }

    private Paint newPaint(int color, float size) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setTextSize(size);
        p.setTextAlign(Paint.Align.CENTER);
        return p;
    }

    public void setLines(List<LrcParser.Line> lines) {
        this.lines = lines == null ? new ArrayList<LrcParser.Line>() : lines;
        this.current = -1;
        this.manualOffset = 0;
        invalidate();
    }

    public void setCurrentIndex(int index) {
        if (index == current) {
            return;
        }
        current = index;
        if (!userScrolling) {
            manualOffset = 0;
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        int centerY = h / 2;

        if (lines == null || lines.isEmpty()) {
            canvas.drawText("暂无歌词", w / 2f, centerY, emptyPaint);
            return;
        }
        if (current < 0) {
            current = 0;
        }

        for (int i = 0; i < lines.size(); i++) {
            float y = centerY + (i - current) * lineHeight + manualOffset;
            if (y < -lineHeight || y > h + lineHeight) {
                continue;
            }
            Paint p = (i == current) ? highlightPaint : normalPaint;
            canvas.drawText(lines.get(i).text, w / 2f, y, p);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                userScrolling = true;
                touchLastY = event.getY();
                return true;
            case MotionEvent.ACTION_MOVE:
                manualOffset += (int) (event.getY() - touchLastY);
                touchLastY = event.getY();
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                userScrolling = false;
                manualOffset = 0;
                invalidate();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }
}
