package ke.linda.demo;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

/** Shows Linda's own notification for a message the background scan flagged. */
final class LindaAlert {
    static final String CHANNEL = "linda-alerts";

    private LindaAlert() {}

    static void show(Context ctx, String address, JSONObject r, String lang, long date) {
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            boolean sw = "sw".equals(lang);
            boolean scam = "scam".equals(r.optString("level"));
            String who = address == null || address.isEmpty() ? (sw ? "mtumaji asiyejulikana" : "an unknown sender") : address;
            String title = scam
                ? (sw ? "Linda: huenda ni ulaghai kutoka " : "Linda: likely scam from ") + who
                : (sw ? "Linda: kuwa mwangalifu na ujumbe kutoka " : "Linda: be careful with a message from ") + who;
            JSONArray texts = r.optJSONArray("texts");
            StringBuilder big = new StringBuilder();
            String first = "";
            if (texts != null) {
                for (int i = 0; i < texts.length() && i < 3; i++) {
                    String t = texts.optString(i);
                    if (i == 0) first = t;
                    if (big.length() > 0) big.append("\n");
                    big.append("• ").append(t);
                }
            }
            String hint = sw ? "Gusa kufungua Linda uone cha kufanya." : "Tap to open Linda and see what to do.";
            if (big.length() > 0) big.append("\n");
            big.append(hint);
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Scam alerts", NotificationManager.IMPORTANCE_HIGH));
            }
            Intent open = new Intent(ctx, MainActivity.class);
            open.putExtra("linda_addr", address == null ? "" : address);
            open.putExtra("linda_date", date);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(ctx, (int) (date & 0x7fffffffL), open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(ctx, CHANNEL) : new Notification.Builder(ctx);
            b.setSmallIcon(R.drawable.ic_stat_linda)
                .setContentTitle(title)
                .setContentText(first.isEmpty() ? hint : first)
                .setStyle(new Notification.BigTextStyle().bigText(big.toString()))
                .setContentIntent(pi)
                .setAutoCancel(true);
            nm.notify((int) (date & 0x7fffffffL), b.build());
        } catch (Exception e) {
            // never crash the receiver
        }
    }
}
