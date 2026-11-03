package ke.linda.demo;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;

/**
 * Watches the phone's call state after Linda showed a warning. As soon as the call is answered or declined the
 * warning card and notification are removed. When the call is over, a quiet "Report this number" reminder is left.
 * Needs the "Phone state" permission; without it the warning simply times out.
 */
final class LindaCallWatcher {
    private static Object active;           // current listener, so a new call replaces the old one
    private static TelephonyManager tmActive;
    private static final Handler H = new Handler(Looper.getMainLooper());

    private LindaCallWatcher() {}

    static void watch(final Context ctx, final String number, final int alertId, final String level, final boolean sw) {
        if (ctx.checkSelfPermission("android.permission.READ_PHONE_STATE") != PackageManager.PERMISSION_GRANTED) return;
        H.post(new Runnable() {
            @Override public void run() {
                try {
                    stop();
                    final TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
                    if (tm == null) return;
                    final boolean[] seenRinging = { false };
                    final boolean[] handled = { false };
                    class Handle {
                        void change(int state) {
                            if (state == TelephonyManager.CALL_STATE_RINGING) { seenRinging[0] = true; return; }
                            // OFFHOOK = answered. IDLE = ended, declined or missed. Either way the decision is made.
                            LindaOverlay.hide(ctx);
                            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
                            if (nm != null) nm.cancel(alertId);
                            if (state == TelephonyManager.CALL_STATE_IDLE && !handled[0]) {
                                handled[0] = true;
                                if (!"verified".equals(level)) afterCall(ctx, number, level, sw);
                                stop();
                            }
                        }
                    }
                    final Handle hd = new Handle();
                    tmActive = tm;
                    if (Build.VERSION.SDK_INT >= 31) {
                        class CB extends TelephonyCallback implements TelephonyCallback.CallStateListener {
                            @Override public void onCallStateChanged(int state) { hd.change(state); }
                        }
                        CB cb = new CB();
                        active = cb;
                        tm.registerTelephonyCallback(ctx.getMainExecutor(), cb);
                    } else {
                        PhoneStateListener l = new PhoneStateListener() {
                            @Override public void onCallStateChanged(int state, String incomingNumber) { hd.change(state); }
                        };
                        active = l;
                        tm.listen(l, PhoneStateListener.LISTEN_CALL_STATE);
                    }
                } catch (Exception e) {
                    // the warning will time out by itself
                }
            }
        });
    }

    static void stop() {
        try {
            if (active != null && tmActive != null) {
                if (Build.VERSION.SDK_INT >= 31 && active instanceof TelephonyCallback) tmActive.unregisterTelephonyCallback((TelephonyCallback) active);
                else if (active instanceof PhoneStateListener) tmActive.listen((PhoneStateListener) active, PhoneStateListener.LISTEN_NONE);
            }
        } catch (Exception e) {
            // ignore
        }
        active = null;
        tmActive = null;
    }

    /** A quiet reminder left after the call, so the person can report the number in one tap. */
    private static void afterCall(Context ctx, String number, String level, boolean sw) {
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            String ch = LindaCallScreen.CHANNEL + "-quiet";
            if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(new NotificationChannel(ch, "Caller labels (quiet)", NotificationManager.IMPORTANCE_DEFAULT));
            Intent open = new Intent(ctx, MainActivity.class);
            open.putExtra("linda_call", number == null ? "" : number);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(ctx, ("a" + LindaCallScreen.norm(number)).hashCode(), open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            String title = sw ? "Linda: simu kutoka " + number + " imeisha" : "Linda: call from " + number + " ended";
            String text = sw ? "Ilikuwa ya ulaghai? Gusa uiripoti kwa Safaricom." : "Did it feel like a scam? Tap to report it to Safaricom.";
            Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(ctx, ch) : new Notification.Builder(ctx);
            b.setSmallIcon(R.drawable.ic_stat_linda).setContentTitle(title).setContentText(text).setContentIntent(pi).setAutoCancel(true);
            if (Build.VERSION.SDK_INT >= 26) b.setTimeoutAfter(600000L);
            nm.notify(("after" + LindaCallScreen.norm(number)).hashCode(), b.build());
        } catch (Exception e) {
            // ignore
        }
    }
}
