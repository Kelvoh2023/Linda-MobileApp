package ke.linda.demo;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A warning card drawn on top of the phone's own incoming-call screen, so the person sees it exactly when
 * they are deciding whether to answer. It sits near the top and leaves the Answer and Decline buttons free.
 * Needs the "Display over other apps" permission, which the person grants once in Android settings.
 */
final class LindaOverlay {
    private static final Handler H = new Handler(Looper.getMainLooper());
    private static View current;
    private static Runnable timeout;

    private LindaOverlay() {}

    static boolean allowed(Context c) {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(c);
    }

    private static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static void show(final Context ctx, final String level, final String title, final String text, final String number, final boolean sw) {
        H.post(new Runnable() {
            @Override public void run() {
                try {
                    hide(ctx);
                    final WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
                    if (wm == null) return;
                    int bg = "scam".equals(level) ? 0xFFB3261E : "verified".equals(level) ? 0xFF1B7F4B : 0xFFB26A00;
                    FrameLayout holder = new FrameLayout(ctx);
                    holder.setPadding(dp(ctx, 12), 0, dp(ctx, 12), 0);
                    LinearLayout card = new LinearLayout(ctx);
                    card.setOrientation(LinearLayout.VERTICAL);
                    card.setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 12));
                    GradientDrawable shape = new GradientDrawable();
                    shape.setColor(bg);
                    shape.setCornerRadius(dp(ctx, 20));
                    card.setBackground(shape);
                    card.setElevation(dp(ctx, 8));
                    TextView t = new TextView(ctx);
                    t.setText(title);
                    t.setTextColor(Color.WHITE);
                    t.setTextSize(17);
                    t.setTypeface(Typeface.DEFAULT_BOLD);
                    TextView s = new TextView(ctx);
                    s.setText(text);
                    s.setTextColor(Color.WHITE);
                    s.setTextSize(14);
                    s.setPadding(0, dp(ctx, 4), 0, dp(ctx, 8));
                    LinearLayout row = new LinearLayout(ctx);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.END);
                    Button open = new Button(ctx);
                    open.setText(sw ? "Fungua Linda" : "Open Linda");
                    open.setAllCaps(false);
                    Button close = new Button(ctx);
                    close.setText(sw ? "Funga" : "Dismiss");
                    close.setAllCaps(false);
                    row.addView(open);
                    row.addView(close);
                    card.addView(t);
                    card.addView(s);
                    card.addView(row);
                    holder.addView(card);
                    open.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            Intent i = new Intent(ctx, MainActivity.class);
                            i.putExtra("linda_call", number == null ? "" : number);
                            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            ctx.startActivity(i);
                            hide(ctx);
                        }
                    });
                    close.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) { hide(ctx); }
                    });
                    WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
                        Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT);
                    lp.gravity = Gravity.TOP;
                    lp.y = dp(ctx, 72);
                    wm.addView(holder, lp);
                    current = holder;
                    timeout = new Runnable() { @Override public void run() { hide(ctx); } };
                    H.postDelayed(timeout, 45000L);
                } catch (Exception e) {
                    // never get in the way of a call
                }
            }
        });
    }

    static void hide(Context ctx) {
        try {
            if (timeout != null) { H.removeCallbacks(timeout); timeout = null; }
            if (current != null) {
                WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
                if (wm != null) wm.removeView(current);
            }
        } catch (Exception e) {
            // already gone
        }
        current = null;
    }
}
