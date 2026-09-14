package com.htt;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.Locale;

/** Compact live combat-analysis HUD with readable detector state. */
public final class HudView extends View {
    private final Paint panel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint value = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accent = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barBack = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barFill = new Paint(Paint.ANTI_ALIAS_FLAG);

    private StrikeTracker.StrikeResult result;
    private String comboPrompt = "";
    private String comboProgress = "";
    private String tracking = "CAMERA MODEL: WAITING";
    private String liveReadout = "Hold guard still to calibrate";
    private boolean newPb;
    private long messageUntil;
    private int totalStrikes;

    public HudView(Context context) { super(context); init(); }
    public HudView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        panel.setColor(0xC9000000);
        title.setColor(Color.WHITE);
        title.setFakeBoldText(true);
        title.setTextSize(34f);
        label.setColor(0xFFB7C1CC);
        label.setTextSize(23f);
        value.setColor(Color.WHITE);
        value.setTextSize(29f);
        value.setFakeBoldText(true);
        accent.setColor(0xFF52E0FF);
        accent.setTextSize(28f);
        accent.setFakeBoldText(true);
        accent.setTextAlign(Paint.Align.CENTER);
        barBack.setColor(0x44FFFFFF);
    }

    public void onStrike(StrikeTracker.StrikeResult r,
                         StrikeTracker.StrikeResult pbSpeed,
                         StrikeTracker.StrikeResult pbPower,
                         StrikeTracker.StrikeResult ignored,
                         int total, int perfect, boolean isNewPb) {
        result = r;
        totalStrikes = total;
        newPb = isNewPb;
        messageUntil = System.currentTimeMillis() + 2600;
        tracking = "TRACKING " + r.confidenceLabel();
        postInvalidate();
    }

    public void setComboPrompt(String prompt, String progress) {
        comboPrompt = prompt == null ? "" : prompt;
        comboProgress = progress == null ? "" : progress;
        postInvalidate();
    }

    public void setTrackingStatus(String text) {
        tracking = text == null ? "" : text;
        postInvalidate();
    }

    public void setLiveReadout(String text) {
        liveReadout = text == null ? "" : text;
        postInvalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        float margin = 14f;
        float panelWidth = Math.min(w * 0.49f, 455f);
        canvas.drawRoundRect(new RectF(margin, margin, margin + panelWidth, h * 0.70f),
                18f, 18f, panel);

        float x = margin + 18f;
        float y = margin + 42f;
        title.setTextSize(Math.max(27f, Math.min(40f, w / 28f)));
        canvas.drawText(result == null ? "LIVE FORGE" : result.punchType, x, y, title);
        y += 36f;
        label.setTextSize(Math.max(18f, Math.min(24f, w / 42f)));
        canvas.drawText(tracking, x, y, label);
        y += 28f;
        label.setTextSize(Math.max(15f, Math.min(20f, w / 48f)));
        drawWrappedLeft(canvas, liveReadout, x, y, panelWidth - 36f, label, 2);
        y += 44f;

        if (result == null) {
            value.setTextSize(Math.max(21f, Math.min(28f, w / 36f)));
            canvas.drawText("1. Full body in frame", x, y, value);
            y += 34f;
            canvas.drawText("2. Hold guard still", x, y, value);
            y += 34f;
            canvas.drawText("3. Punch and return", x, y, value);
            y += 34f;
            label.setTextSize(Math.max(15f, Math.min(20f, w / 48f)));
            canvas.drawText("Best camera angle: 30 to 45 degrees", x, y, label);
        } else {
            value.setTextSize(Math.max(22f, Math.min(30f, w / 35f)));
            canvas.drawText(String.format(Locale.US, "%.1f-%.1f MPH", result.speedLowMph, result.speedHighMph), x, y, value);
            y += 34f;
            canvas.drawText(String.format(Locale.US, "MODELED IMPACT %.0f-%.0f lbf", result.forceLowLbf, result.forceHighLbf), x, y, value);
            y += 34f;
            canvas.drawText(String.format(Locale.US, "RETURN %.0f ms  |  %.0f FPS", result.guardReturnMs, result.frameRate), x, y, label);
            y += 34f;
            y = drawBar(canvas, x, y, panelWidth - 36f, "TECHNIQUE", result.techniqueScore, 0xFF46E58B);
            y = drawBar(canvas, x, y, panelWidth - 36f, "KINETIC CHAIN", result.chainScore, 0xFF52C7FF);
            y = drawBar(canvas, x, y, panelWidth - 36f, "GUARD RETURN", result.guardScore, 0xFFFFC84A);
            y = drawBar(canvas, x, y, panelWidth - 36f, "BALANCE", result.balanceScore, 0xFFC989FF);
            y += 8f;
            canvas.drawText(String.format(Locale.US, "Confidence %.0f%%  |  Strikes %d", result.confidence, totalStrikes), x, y, label);
        }

        if (!comboPrompt.isEmpty()) {
            float top = h * 0.72f;
            canvas.drawRoundRect(new RectF(margin, top, w - margin, h - margin), 18f, 18f, panel);
            accent.setTextSize(Math.max(24f, Math.min(36f, w / 27f)));
            drawWrappedCentered(canvas, comboPrompt, w / 2f, top + 48f, w - 50f, accent);
            if (!comboProgress.isEmpty()) {
                label.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(comboProgress, w / 2f, h - 27f, label);
                label.setTextAlign(Paint.Align.LEFT);
            }
        }

        long now = System.currentTimeMillis();
        if (result != null && now < messageUntil) {
            accent.setColor(newPb ? 0xFFFFD34D : 0xFF75F0FF);
            accent.setTextSize(Math.max(22f, Math.min(32f, w / 31f)));
            drawWrappedCentered(canvas, (newPb ? "NEW BEST  |  " : "") + result.techniqueNote,
                    w * 0.70f, h * 0.56f, w * 0.53f, accent);
            accent.setColor(0xFF52E0FF);
            postInvalidateOnAnimation();
        }
    }

    private float drawBar(Canvas canvas, float x, float y, float width, String name, float score, int color) {
        label.setTextSize(20f);
        canvas.drawText(name, x, y, label);
        canvas.drawText(String.format(Locale.US, "%.0f", score), x + width - 38f, y, value);
        y += 10f;
        canvas.drawRoundRect(new RectF(x, y, x + width, y + 13f), 7f, 7f, barBack);
        barFill.setColor(color);
        canvas.drawRoundRect(new RectF(x, y, x + Math.max(4f, width * score / 100f), y + 13f), 7f, 7f, barFill);
        return y + 33f;
    }

    private void drawWrappedLeft(Canvas canvas, String text, float x, float firstY, float maxWidth, Paint paint, int maxLines) {
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        float y = firstY;
        int lines = 0;
        for (String word : words) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (paint.measureText(candidate) > maxWidth && line.length() > 0) {
                canvas.drawText(line.toString(), x, y, paint);
                y += paint.getTextSize() * 1.10f;
                lines++;
                if (lines >= maxLines) return;
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (line.length() > 0 && lines < maxLines) canvas.drawText(line.toString(), x, y, paint);
    }

    private void drawWrappedCentered(Canvas canvas, String text, float centerX, float firstY, float maxWidth, Paint paint) {
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        float y = firstY;
        for (String word : words) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (paint.measureText(candidate) > maxWidth && line.length() > 0) {
                canvas.drawText(line.toString(), centerX, y, paint);
                y += paint.getTextSize() * 1.12f;
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (line.length() > 0) canvas.drawText(line.toString(), centerX, y, paint);
    }
}
