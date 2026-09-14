package com.htt;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import java.util.Locale;
import java.util.Random;

/** Transparent cue-and-mask layer for visible, masked, peripheral, low-contrast and symbolic primes. */
public final class SubliminalView extends View {
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mask = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private String cue = "";
    private String mode = "Off";
    private boolean showing;
    private boolean masking;
    private float x = 0.5f, y = 0.5f;

    public SubliminalView(Context context) {
        super(context);
        setClickable(false);
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        text.setTextAlign(Paint.Align.CENTER);
        mask.setStrokeWidth(3f);
        setVisibility(GONE);
    }

    public void present(String message, String selectedMode, int durationMs) {
        handler.removeCallbacksAndMessages(null);
        cue = selectedMode != null && selectedMode.toLowerCase(Locale.US).contains("symbol")
                ? symbolFor(message) : message;
        mode = selectedMode == null ? "Visible" : selectedMode;
        x = 0.22f + random.nextFloat() * 0.56f;
        y = 0.20f + random.nextFloat() * 0.60f;
        if (mode.toLowerCase(Locale.US).contains("peripheral")) {
            x = random.nextBoolean() ? 0.10f : 0.90f;
            y = 0.18f + random.nextFloat() * 0.64f;
        }
        showing = true;
        masking = false;
        setVisibility(VISIBLE);
        invalidate();
        int safeDuration = Math.max(16, Math.min(1800, durationMs));
        handler.postDelayed(() -> {
            showing = false;
            masking = mode.toLowerCase(Locale.US).contains("mask");
            invalidate();
            handler.postDelayed(this::stop, masking ? 180L : 40L);
        }, safeDuration);
    }

    public void stop() {
        handler.removeCallbacksAndMessages(null);
        showing = false;
        masking = false;
        setVisibility(GONE);
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (showing) {
            String lower = mode.toLowerCase(Locale.US);
            float size = Math.max(22f, getWidth() * (lower.contains("symbol") ? 0.16f : 0.062f));
            text.setTextSize(size);
            int alpha = lower.contains("low contrast") ? 28 : (lower.contains("peripheral") ? 90 : 230);
            text.setColor(Color.argb(alpha, 235, 246, 255));
            canvas.drawText(cue, getWidth() * x, getHeight() * y, text);
        } else if (masking) {
            mask.setColor(Color.argb(130, 170, 190, 210));
            for (int i = 0; i < 22; i++) {
                float y0 = random.nextFloat() * getHeight();
                canvas.drawLine(0, y0, getWidth(), y0 + random.nextInt(24) - 12, mask);
            }
        }
    }

    private String symbolFor(String message) {
        String m = message == null ? "" : message.toLowerCase(Locale.US);
        if (m.contains("angle") || m.contains("pivot")) return "↗ ◈";
        if (m.contains("guard") || m.contains("defense")) return "◇ ⛨";
        if (m.contains("hip") || m.contains("power")) return "◉ ⇢";
        if (m.contains("breath") || m.contains("calm")) return "◎";
        if (m.contains("body")) return "◆ ↓";
        return "◈";
    }
}
