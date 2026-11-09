package com.example.oldmusic;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class LyricView extends View {
    private List<LrcParser.Line> lines = new ArrayList<LrcParser.Line>();
    private int current = -1;
    private final Paint normalPaint;
    private final Paint highlightPaint;
    private final Paint emptyPaint;
    private final int lineHeight;

    public LyricView(Context context) {
        super(context);
        lineHeight = (int) (36 * context.getResources().getDisplayMetrics().density);
        normalPaint = newPaint(0x88FFFFFF, 14f); // 未播放行半透明白
        highlightPaint = newPaint(0xFFFFD54F, 18f); // 当前行金黄色
        emptyPaint = newPaint(0x44FFFFFF, 14f);
    }

    public LyricView(Context context, AttributeSet attrs) {
        super(context, attrs);
        lineHeight = (int) (36 * context.getResources().getDisplayMetrics().density);
        normalPaint = newPaint(0x88FFFFFF, 14f);
        highlightPaint = newPaint(0xFFFFD54F, 18f);
        emptyPaint = newPaint(0x44FFFFFF, 14f);
    }

    private Paint newPaint(int color, float sizeSp) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setTextSize(sizeSp * getResources().getDisplayMetrics().scaledDensity);
        p.setTextAlign(Paint.Align.CENTER);
        return p;
    }

    public void setLines(List<LrcParser.Line> lines) {
        this.lines = lines == null ? new ArrayList<LrcParser.Line>() : lines;
        this.current = -1;
        invalidate();
    }

    public void setCurrentIndex(int index) {
        if (index == current) return;
        current = index;
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
        if (current < 0) current = 0;

        for (int i = 0; i < lines.size(); i++) {
            float y = centerY + (i - current) * lineHeight;
            if (y < -lineHeight || y > h + lineHeight) continue;
            Paint p = (i == current) ? highlightPaint : normalPaint;
            canvas.drawText(lines.get(i).text, w / 2f, y, p);
        }
    }
}
