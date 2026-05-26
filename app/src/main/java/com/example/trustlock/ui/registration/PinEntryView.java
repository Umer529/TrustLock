package com.example.trustlock.ui.registration;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.trustlock.R;

/**
 * Draws 4 circles representing PIN digit slots. Filled = digit entered, outline = empty.
 */
public class PinEntryView extends View {

    private static final int PIN_LENGTH = 4;
    private static final float CIRCLE_RADIUS_DP = 14f;
    private static final float CIRCLE_SPACING_DP = 28f;
    private static final float STROKE_WIDTH_DP = 2.5f;

    private final Paint filledPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;

    private final StringBuilder pin = new StringBuilder();

    public PinEntryView(Context context) {
        this(context, null);
    }

    public PinEntryView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = context.getResources().getDisplayMetrics().density;

        int purple = ContextCompat.getColor(context, R.color.purple_primary);
        int hint = ContextCompat.getColor(context, R.color.dot_inactive);

        filledPaint.setStyle(Paint.Style.FILL);
        filledPaint.setColor(purple);

        emptyPaint.setStyle(Paint.Style.STROKE);
        emptyPaint.setStrokeWidth(STROKE_WIDTH_DP * density);
        emptyPaint.setColor(hint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float r = CIRCLE_RADIUS_DP * density;
        float spacing = CIRCLE_SPACING_DP * density;
        int totalWidth = (int) (PIN_LENGTH * 2 * r + (PIN_LENGTH - 1) * spacing);
        int totalHeight = (int) (2 * r);
        setMeasuredDimension(totalWidth, totalHeight);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float r = CIRCLE_RADIUS_DP * density;
        float spacing = CIRCLE_SPACING_DP * density;
        float cy = r;

        for (int i = 0; i < PIN_LENGTH; i++) {
            float cx = r + i * (2 * r + spacing);
            Paint paint = (i < pin.length()) ? filledPaint : emptyPaint;
            canvas.drawCircle(cx, cy, r, paint);
        }
    }

    public void addDigit(int digit) {
        if (pin.length() < PIN_LENGTH) {
            pin.append(digit);
            invalidate();
        }
    }

    public void removeDigit() {
        if (pin.length() > 0) {
            pin.deleteCharAt(pin.length() - 1);
            invalidate();
        }
    }

    public String getPin() {
        return pin.toString();
    }

    public void clearPin() {
        pin.setLength(0);
        invalidate();
    }

    public boolean isFull() {
        return pin.length() == PIN_LENGTH;
    }
}
