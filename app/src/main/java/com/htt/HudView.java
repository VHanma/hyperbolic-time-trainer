package com.htt;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

public class HudView extends View {

    private final Paint labelPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barBg       = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barFill     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pbPaint     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint levelPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flashPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint panelPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float speed = 0, power = 0, tech = 0, level = 0;
    private float pbSpeed = 0, pbPower = 0, pbLevel = 0;
    private String levelLabel = "ROOKIE";
    private boolean isPerfect = false;
    private long flashEnd = 0;
    private boolean isPB = false;
    private long pbFlashEnd = 0;
    private int totalStrikes = 0;
    private int perfectStrikes = 0;
    private long sessionStart = System.currentTimeMillis();

    public HudView(Context ctx) { super(ctx); init(); }
    public HudView(Context ctx, AttributeSet a) { super(ctx, a); init(); }

    private void init() {
        labelPaint.setColor(0xFFAAAAAA);
        labelPaint.setTextSize(28f);
        labelPaint.setShadowLayer(3f, 1f, 1f, Color.BLACK);

        valuePaint.setColor(Color.WHITE);
        valuePaint.setTextSize(38f);
        valuePaint.setFakeBoldText(true);
        valuePaint.setShadowLayer(4f, 2f, 2f, Color.BLACK);

        barBg.setColor(0x44FFFFFF);
        barFill.setColor(0xFF00FF88);

        pbPaint.setColor(0xFFFFD700);
        pbPaint.setTextSize(28f);
        pbPaint.setShadowLayer(3f, 1f, 1f, Color.BLACK);

        levelPaint.setColor(0xFFFF6600);
        levelPaint.setTextSize(52f);
        levelPaint.setFakeBoldText(true);
        levelPaint.setShadowLayer(6f, 3f, 3f, Color.BLACK);

        flashPaint.setColor(0xFFFF2200);
        flashPaint.setTextSize(60f);
        flashPaint.setFakeBoldText(true);
        flashPaint.setShadowLayer(10f, 4f, 4f, Color.BLACK);

        panelPaint.setColor(0xCC000000);
    }

    public void updateStrike(StrikeTracker.StrikeResult r,
                              StrikeTracker.StrikeResult pbSpeedR,
                              StrikeTracker.StrikeResult pbPowerR,
                              StrikeTracker.StrikeResult pbLevelR,
                              int total, int perfect, boolean newPB) {
        if (r == null) return;
        speed       = r.speedMs;
        power       = r.powerScore;
        tech        = r.techniqueScore;
        level       = r.powerLevel;
        levelLabel  = r.levelLabel();
        isPerfect   = r.isPerfect;
        totalStrikes   = total;
        perfectStrikes = perfect;
        isPB        = newPB;
        if (pbSpeedR != null) pbSpeed = pbSpeedR.speedMs;
        if (pbPowerR != null) pbPower = pbPowerR.powerScore;
        if (pbLevelR != null) pbLevel = pbLevelR.powerLevel;
        if (r.isPerfect)  flashEnd  = System.currentTimeMillis() + 2000;
        if (newPB)        pbFlashEnd = System.currentTimeMillis() + 1500;
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float W = getWidth(), H = getHeight();
        float pad = 16f, x = pad, y = 60f;
        float barW = 240f, barH = 18f;

        // Panel background
        canvas.drawRoundRect(new RectF(0, 0, barW + pad*2 + 40, 420), 12, 12, panelPaint);

        // POWER LEVEL — big hero number
        String lvlStr = String.format("%.0f", level);
        canvas.drawText("POWER LEVEL", x, y, labelPaint);
        y += 8f;
        levelPaint.setColor(levelColor(level));
        canvas.drawText(lvlStr, x, y + 50f, levelPaint);
        canvas.drawText(levelLabel, x + levelPaint.measureText(lvlStr) + 12f, y + 42f, labelPaint);
        y += 68f;

        // Speed
        canvas.drawText("SPEED", x, y, labelPaint);
        canvas.drawText(String.format("  %.1f m/s", speed), x + 70f, y, valuePaint);
        y += 6f;
        drawBar(canvas, x, y, barW, barH, Math.min(speed / 15f, 1f), 0xFF00AAFF);
        y += barH + 20f;

        // Power
        canvas.drawText("POWER", x, y, labelPaint);
        canvas.drawText(String.format("  %.0f/100", power), x + 70f, y, valuePaint);
        y += 6f;
        drawBar(canvas, x, y, barW, barH, power / 100f, 0xFFFF4400);
        y += barH + 20f;

        // Technique
        canvas.drawText("TECHNIQUE", x, y, labelPaint);
        canvas.drawText(String.format("  %.0f/100", tech), x + 110f, y, valuePaint);
        y += 6f;
        drawBar(canvas, x, y, barW, barH, tech / 100f, 0xFF00FF88);
        y += barH + 20f;

        // Stats row
        labelPaint.setTextSize(24f);
        canvas.drawText("STRIKES: " + totalStrikes + "   PERFECT: " + perfectStrikes, x, y, labelPaint);
        y += 28f;

        // Personal bests
        pbPaint.setTextSize(24f);
        canvas.drawText(String.format("PB SPD %.1fm/s  PWR %.0f  LVL %.0f", pbSpeed, pbPower, pbLevel), x, y, pbPaint);

        labelPaint.setTextSize(28f);

        // Perfect flash center
        long now = System.currentTimeMillis();
        if (now < flashEnd) {
            String msg = "★ PERFECT STRIKE ★";
            float tw = flashPaint.measureText(msg);
            canvas.drawText(msg, (W - tw) / 2f, H * 0.45f, flashPaint);
            postInvalidateDelayed(40);
        }

        // PB flash center
        if (now < pbFlashEnd) {
            String msg = "▲ NEW PERSONAL BEST ▲";
            pbPaint.setTextSize(52f);
            float tw = pbPaint.measureText(msg);
            canvas.drawText(msg, (W - tw) / 2f, H * 0.55f, pbPaint);
            pbPaint.setTextSize(24f);
            postInvalidateDelayed(40);
        }
    }

    private int levelColor(float lvl) {
        if (lvl >= 9000) return 0xFFFF0000;
        if (lvl >= 6000) return 0xFFFF6600;
        if (lvl >= 3000) return 0xFFFFCC00;
        if (lvl >= 1000) return 0xFF00FF88;
        return 0xFF00AAFF;
    }

    private void drawBar(Canvas canvas, float x, float y, float w, float h, float fill, int color) {
        canvas.drawRoundRect(new RectF(x, y, x+w, y+h), 6, 6, barBg);
        barFill.setColor(color);
        if (fill > 0) canvas.drawRoundRect(new RectF(x, y, x + w * fill, y+h), 6, 6, barFill);
    }
}
