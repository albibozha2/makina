package al.makina;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Ekrani HUD: unaza rrotulluese, berthama qe pulson, kllapat e verdha. */
public class HudView extends View {

    public static final int IDLE = 0, LISTEN = 1, THINK = 2, SPEAK = 3;

    static final int YELLOW = Color.rgb(245, 197, 24);
    static final int CYAN = Color.rgb(79, 209, 232);
    static final int WHITE = Color.rgb(235, 235, 235);
    static final int AMBER = Color.rgb(255, 176, 0);

    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dash = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final long start = System.currentTimeMillis();
    private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm:ss", Locale.ROOT);
    private final float dp;

    private int state = IDLE;
    private float level = 0f;      // 0..1 niveli i mikrofonit
    private float shown = 0f;      // niveli i zbutur
    private String admin = "";
    private String subtitle = "";

    public HudView(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        dash.setStyle(Paint.Style.STROKE);
        text.setTypeface(Typeface.MONOSPACE);
        setClickable(true);
        setLongClickable(true);
    }

    public void setState(int s) { state = s; if (s != LISTEN) level = 0f; }
    public int getState() { return state; }
    public void setAdmin(String a) { admin = a == null ? "" : a.toUpperCase(Locale.ROOT); }
    public void setSubtitle(String s) { subtitle = s == null ? "" : s; }

    /** rms nga SpeechRecognizer: zakonisht -2..10 dB */
    public void setRms(float rms) { level = Math.max(0f, Math.min(1f, (rms + 2f) / 12f)); }

    private int color() {
        switch (state) {
            case LISTEN: return CYAN;
            case THINK: return WHITE;
            case SPEAK: return AMBER;
            default: return YELLOW;
        }
    }

    private String label() {
        switch (state) {
            case LISTEN: return "DUKE DËGJUAR";
            case THINK: return "DUKE ANALIZUAR";
            case SPEAK: return "DUKE FOLUR";
            default: return "NË PRITJE";
        }
    }

    private static int alpha(int c, int a) {
        return Color.argb(a, Color.red(c), Color.green(c), Color.blue(c));
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float t = (System.currentTimeMillis() - start) / 1000f;
        int col = color();
        shown += (level - shown) * 0.25f;

        c.drawColor(Color.rgb(5, 5, 5));

        // rrjeta me pika
        fill.setShader(null);
        fill.setColor(alpha(YELLOW, 22));
        float step = 28 * dp;
        for (float x = step / 2; x < w; x += step)
            for (float y = step / 2; y < h; y += step)
                c.drawCircle(x, y, 1.1f * dp, fill);

        float cx = w / 2f;
        float cy = h * 0.34f;
        float R = Math.min(w, h * 0.62f) * 0.30f;
        float speed = state == THINK ? 3.5f : state == LISTEN ? 1.6f : 1f;

        // kllapat e qosheve te ekranit
        stroke.setColor(alpha(YELLOW, 200));
        stroke.setStrokeWidth(3 * dp);
        stroke.setStrokeCap(Paint.Cap.SQUARE);
        float m = 14 * dp, L = 26 * dp;
        bracket(c, m, m, L, 1, 1);
        bracket(c, w - m, m, L, -1, 1);
        bracket(c, m, h - m, L, 1, -1);
        bracket(c, w - m, h - m, L, -1, -1);

        // kllapat rreth berthames (stili "Person of Interest")
        stroke.setColor(col);
        stroke.setStrokeWidth(2.5f * dp);
        float B = R * 1.38f, bl = R * 0.32f;
        bracket(c, cx - B, cy - B, bl, 1, 1);
        bracket(c, cx + B, cy - B, bl, -1, 1);
        bracket(c, cx - B, cy + B, bl, 1, -1);
        bracket(c, cx + B, cy + B, bl, -1, -1);
        stroke.setStrokeCap(Paint.Cap.ROUND);

        // shkallezimi i jashtem
        c.save();
        c.rotate(t * 6 * speed, cx, cy);
        stroke.setStrokeWidth(1.4f * dp);
        for (int i = 0; i < 72; i++) {
            double a = Math.toRadians(i * 5);
            float r1 = R * 1.16f, r2 = (i % 6 == 0) ? R * 1.26f : R * 1.20f;
            stroke.setColor(alpha(col, i % 6 == 0 ? 220 : 110));
            c.drawLine(cx + (float) Math.cos(a) * r1, cy + (float) Math.sin(a) * r1,
                    cx + (float) Math.cos(a) * r2, cy + (float) Math.sin(a) * r2, stroke);
        }
        c.restore();

        // unaza 1: tre harqe
        stroke.setStrokeWidth(4 * dp);
        stroke.setColor(col);
        oval.set(cx - R, cy - R, cx + R, cy + R);
        float a1 = t * 40 * speed;
        for (int i = 0; i < 3; i++) c.drawArc(oval, a1 + i * 120, 70, false, stroke);

        // unaza 2: dy harqe ne drejtim te kundert
        float r2 = R * 0.82f;
        stroke.setStrokeWidth(2.5f * dp);
        stroke.setColor(alpha(col, 180));
        oval.set(cx - r2, cy - r2, cx + r2, cy + r2);
        float a2 = -t * 25 * speed;
        for (int i = 0; i < 2; i++) c.drawArc(oval, a2 + i * 180, 120, false, stroke);

        // unaza 3: e nderprere
        float r3 = R * 0.66f;
        dash.setStrokeWidth(1.5f * dp);
        dash.setColor(alpha(col, 140));
        dash.setPathEffect(new DashPathEffect(new float[]{6 * dp, 6 * dp}, t * 30 * dp));
        c.drawCircle(cx, cy, r3, dash);

        // vale zanore rreth berthames
        float amp = state == LISTEN ? shown
                : state == SPEAK ? 0.45f + 0.35f * (float) Math.abs(Math.sin(t * 9))
                : state == THINK ? 0.25f : 0.08f;
        stroke.setStrokeWidth(2 * dp);
        stroke.setColor(alpha(col, 200));
        int bars = 60;
        for (int i = 0; i < bars; i++) {
            double a = 2 * Math.PI * i / bars;
            float n = (float) (0.5 + 0.5 * Math.sin(i * 1.7 + t * 7) * Math.cos(i * 0.6 - t * 4));
            float len = R * 0.04f + R * 0.16f * amp * n;
            float rb = R * 0.50f;
            c.drawLine(cx + (float) Math.cos(a) * rb, cy + (float) Math.sin(a) * rb,
                    cx + (float) Math.cos(a) * (rb + len), cy + (float) Math.sin(a) * (rb + len), stroke);
        }

        // berthama
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * (state == IDLE ? 2 : 5));
        float core = R * 0.36f * (0.92f + 0.10f * pulse + 0.18f * amp);
        fill.setShader(new RadialGradient(cx, cy, core,
                new int[]{alpha(col, 255), alpha(col, 120), alpha(col, 0)},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, core, fill);
        fill.setShader(null);
        fill.setColor(Color.rgb(5, 5, 5));
        c.drawCircle(cx, cy, core * 0.42f, fill);
        stroke.setColor(col);
        stroke.setStrokeWidth(2 * dp);
        c.drawCircle(cx, cy, core * 0.42f, stroke);

        // titulli
        text.setTextAlign(Paint.Align.LEFT);
        text.setColor(YELLOW);
        text.setTextSize(20 * dp);
        text.setFakeBoldText(true);
        c.drawText("MAKINA", 26 * dp, 46 * dp, text);
        text.setFakeBoldText(false);
        text.setTextSize(11 * dp);
        text.setColor(alpha(YELLOW, 170));
        c.drawText("ADMIN: " + (admin.isEmpty() ? "I PANJOHUR" : admin), 26 * dp, 64 * dp, text);
        text.setTextAlign(Paint.Align.RIGHT);
        text.setTextSize(16 * dp);
        text.setColor(WHITE);
        c.drawText(clock.format(new Date()), w - 26 * dp, 46 * dp, text);
        text.setTextSize(11 * dp);
        text.setColor(alpha(col, 220));
        c.drawText("● " + label(), w - 26 * dp, 64 * dp, text);

        // gjendja poshte unazave
        text.setTextAlign(Paint.Align.CENTER);
        float ty = cy + B + 30 * dp;
        if (!subtitle.isEmpty()) {
            text.setColor(WHITE);
            text.setTextSize(15 * dp);
            String s = subtitle.length() > 42 ? "…" + subtitle.substring(subtitle.length() - 41) : subtitle;
            c.drawText(s, cx, ty, text);
        } else {
            text.setColor(alpha(YELLOW, 120));
            text.setTextSize(10.5f * dp);
            c.drawText("PREK BËRTHAMËN PËR TË FOLUR  ·  MBAJ SHTYPUR PËR MENUNË", cx, ty, text);
        }

        postInvalidateOnAnimation();
    }

    private void bracket(Canvas c, float x, float y, float len, int dx, int dy) {
        c.drawLine(x, y, x + dx * len, y, stroke);
        c.drawLine(x, y, x, y + dy * len, stroke);
    }
}
