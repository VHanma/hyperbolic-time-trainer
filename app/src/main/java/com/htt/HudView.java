package com.htt;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;

public class HudView extends View {

    private final Paint panelPaint  = new Paint();
    private final Paint levelPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barBg       = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barFill     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pbPaint     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flashPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cuePaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint punchPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float speed, power, tech, level;
    private float pbSpeed, pbPower, pbLevel;
    private String levelLabel  = "ROOKIE";
    private String punchType   = "";
    private String cueText     = "";
    private int  totalStrikes, perfectStrikes;
    private boolean isPerfect, isNewPB;
    private long flashEnd, pbFlashEnd, cueEnd;

    public HudView(Context ctx) { super(ctx); init(); }
    public HudView(Context ctx, AttributeSet a) { super(ctx, a); init(); }

    private void init() {
        panelPaint.setColor(0xCC000000);

        levelPaint.setColor(0xFF00AAFF);
        levelPaint.setTextSize(72f);
        levelPaint.setFakeBoldText(true);
        levelPaint.setShadowLayer(8f, 3f, 3f, Color.BLACK);
        levelPaint.setTextAlign(Paint.Align.LEFT);

        labelPaint.setColor(0xFFAAAAAA);
        labelPaint.setTextSize(26f);
        labelPaint.setShadowLayer(3f, 1f, 1f, Color.BLACK);

        valuePaint.setColor(Color.WHITE);
        valuePaint.setTextSize(34f);
        valuePaint.setFakeBoldText(true);
        valuePaint.setShadowLayer(4f, 2f, 2f, Color.BLACK);

        barBg.setColor(0x33FFFFFF);
        barFill.setColor(0xFF00FF88);

        pbPaint.setColor(0xFFFFD700);
        pbPaint.setTextSize(26f);
        pbPaint.setShadowLayer(3f, 1f, 1f, Color.BLACK);

        flashPaint.setColor(0xFFFF2200);
        flashPaint.setTextSize(64f);
        flashPaint.setFakeBoldText(true);
        flashPaint.setTextAlign(Paint.Align.CENTER);
        flashPaint.setShadowLayer(12f, 4f, 4f, Color.BLACK);

        cuePaint.setColor(0xFFFFFF00);
        cuePaint.setTextSize(36f);
        cuePaint.setFakeBoldText(true);
        cuePaint.setTextAlign(Paint.Align.CENTER);
        cuePaint.setShadowLayer(6f, 2f, 2f, Color.BLACK);

        punchPaint.setColor(0xFF00FFFF);
        punchPaint.setTextSize(42f);
        punchPaint.setFakeBoldText(true);
        punchPaint.setShadowLayer(6f, 2f, 2f, Color.BLACK);
    }

    public void onStrike(StrikeTracker.StrikeResult r,
                         StrikeTracker.StrikeResult pbSpeedR,
                         StrikeTracker.StrikeResult pbPowerR,
                         StrikeTracker.StrikeResult pbLevelR,
                         int total, int perfect, boolean newPB) {
        if (r == null) return;
        speed  = r.speedMs;
        power  = r.powerScore;
        tech   = r.techniqueScore;
        level  = r.powerLevel;
        levelLabel    = r.levelLabel();
        punchType     = r.punchType != null ? r.punchType : "";
        cueText       = r.techniqueNote != null ? r.techniqueNote : "";
        isPerfect     = r.isPerfect;
        isNewPB       = newPB;
        totalStrikes  = total;
        perfectStrikes = perfect;
        if (pbSpeedR != null) pbSpeed = pbSpeedR.speedMs;
        if (pbPowerR != null) pbPower = pbPowerR.powerScore;
        if (pbLevelR != null) pbLevel = pbLevelR.powerLevel;
        if (r.isPerfect) flashEnd  = System.currentTimeMillis() + 2000;
        if (newPB)       pbFlashEnd = System.currentTimeMillis() + 1500;
        cueEnd = System.currentTimeMillis() + 2500;
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        float pad = 16f, panelW = 300f;

        // ── Left stats panel ──────────────────────────────────────────────────
        canvas.drawRoundRect(new RectF(pad, pad, pad + panelW, h * 0.72f),
                14f, 14f, panelPaint);

        float y = pad + 20f;

        // Power level hero number (color by tier)
        levelPaint.setColor(levelColor());
        canvas.drawText(String.format("%.0f", level), pad + 12f, y + 70f, levelPaint);
        labelPaint.setColor(0xFFAAAAAA);
        canvas.drawText(levelLabel, pad + 12f, y + 100f, labelPaint);

        y += 120f;

        // Punch type
        if (!punchType.isEmpty()) {
            punchPaint.setColor(0xFF00FFFF);
            canvas.drawText(punchType, pad + 12f, y, punchPaint);
            y += 46f;
        }

        // Speed bar
        y = drawBar(canvas, pad + 12f, y, panelW - 24f, "SPEED",
                String.format("%.1f m/s", speed), speed / 15f, 0xFF4488FF);
        // Power bar
        y = drawBar(canvas, pad + 12f, y, panelW - 24f, "POWER",
                String.format("%.0f", power), power / 100f, 0xFFFF4444);
        // Technique bar
        y = drawBar(canvas, pad + 12f, y, panelW - 24f, "TECHNIQUE",
                String.format("%.0f", tech), tech / 100f, 0xFF44FF88);

        // Counts
        y += 10f;
        labelPaint.setColor(0xFFAAAAAA);
        canvas.drawText("STRIKES: " + totalStrikes + "  ★: " + perfectStrikes,
                pad + 12f, y + 24f, labelPaint);

        // ── PB row ────────────────────────────────────────────────────────────
        float pbY = h * 0.72f + 20f;
        canvas.drawRoundRect(new RectF(pad, pbY - 20f, pad + panelW, pbY + 60f),
                10f, 10f, panelPaint);
        pbPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(String.format("PB  SPD %.1f  PWR %.0f  LVL %.0f",
                pbSpeed, pbPower, pbLevel), pad + 8f, pbY + 24f, pbPaint);

        // ── Center flash: coaching cue ────────────────────────────────────────
        long now = System.currentTimeMillis();
        if (now < cueEnd && !cueText.isEmpty()) {
            canvas.drawText(cueText, w / 2f, h * 0.55f, cuePaint);
        }

        // ── Center flash: PERFECT ─────────────────────────────────────────────
        if (now < flashEnd) {
            canvas.drawText("★ PERFECT STRIKE ★", w / 2f, h * 0.45f, flashPaint);
        }

        // ── Center flash: NEW PB ──────────────────────────────────────────────
        if (now < pbFlashEnd) {
            flashPaint.setColor(0xFFFFD700);
            canvas.drawText("▲ NEW PERSONAL BEST ▲", w / 2f, h * 0.40f, flashPaint);
            flashPaint.setColor(0xFFFF2200);
        }

        // Keep redrawing while flashes active
        if (now < flashEnd || now < pbFlashEnd || now < cueEnd) postInvalidate();
    }

    private float drawBar(Canvas canvas, float x, float y, float w,
                          String label, String val, float pct, int color) {
        canvas.drawText(label, x, y + 20f, labelPaint);
        canvas.drawText(val, x + w - 90f, y + 20f, valuePaint);
        y += 26f;
        canvas.drawRoundRect(new RectF(x, y, x + w, y + 14f), 7f, 7f, barBg);
        barFill.setColor(color);
        float fill = Math.max(4f, Math.min(w, w * pct));
        canvas.drawRoundRect(new RectF(x, y, x + fill, y + 14f), 7f, 7f, barFill);
        return y + 26f;
    }

    private int levelColor() {
        if (level >= 9000) return 0xFFFF0000;
        if (level >= 6000) return 0xFFFF6600;
        if (level >= 3000) return 0xFFFFFF00;
        if (level >= 1000) return 0xFF00FF44;
        return 0xFF00AAFF;
    }
}
