package ke.linda.demo;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.telecom.Call;
import android.telecom.CallScreeningService;

import org.json.JSONObject;

import java.util.Iterator;

/**
 * Linda as the phone's "caller ID and spam app". Android tells us about every incoming call before it rings.
 * We look the number up in Safaricom's fraud list (already saved on the phone, so it works offline) and show a
 * label as a notification. We never block a call. If the person turns it on, a confirmed scam number rings silently.
 */
public class LindaCallScreen extends CallScreeningService {
    static final String CHANNEL = "linda-calls";

    @Override
    public void onScreenCall(Call.Details details) {
        CallResponse.Builder resp = new CallResponse.Builder();
        String number = null;
        String level = "none";
        JSONObject hit = null;
        try {
            if (details.getCallDirection() == Call.Details.DIRECTION_INCOMING) {
                Uri h = details.getHandle();
                number = h == null ? null : h.getSchemeSpecificPart();
                SharedPreferences sp = getSharedPreferences(LindaScanner.PREFS, Context.MODE_PRIVATE);
                if (number != null && !number.isEmpty() && sp.getBoolean("calls", true)) {
                    if (isContact(this, number)) {
                        level = "contact";
                    } else {
                        hit = lookup(sp.getString("db", "{}"), number);
                        if (hit != null) {
                            String st = hit.optString("status");
                            int count = hit.optInt("count", 0);
                            if ("verified".equals(st)) level = "verified";
                            else if ("confirmed".equals(st)) level = "scam";
                            else if (count >= 3) level = "caution";
                        }
                    }
                    if ("scam".equals(level) && sp.getBoolean("silence", false)) resp.setSilenceCall(true);
                }
                if (!"none".equals(level) && !"contact".equals(level)) {
                    notifyCall(this, number, level, hit, "sw".equals(sp.getString("lang", "en")));
                }
            }
        } catch (Exception e) {
            // never get in the way of a call
        }
        try {
            respondToCall(details, resp.build());
        } catch (Exception ignored) { }
    }

    /** 0712 345 678, +254712345678 and 254712345678 all become 0712345678. Sender names stay as letters. */
    static String norm(String n) {
        if (n == null) return "";
        String d = n.replaceAll("[^0-9]", "");
        if (d.startsWith("254") && d.length() >= 12) d = "0" + d.substring(3);
        return d;
    }

    static JSONObject lookup(String dbJson, String number) {
        String want = norm(number);
        if (want.length() < 3) return null;
        try {
            JSONObject db = new JSONObject(dbJson);
            Iterator<String> it = db.keys();
            while (it.hasNext()) {
                String k = it.next();
                String nk = norm(k);
                if (!nk.isEmpty() && nk.equals(want)) return db.getJSONObject(k);
            }
        } catch (Exception e) {
            // empty list
        }
        return null;
    }

    static boolean isContact(Context ctx, String number) {
        if (ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return false;
        try (Cursor c = ctx.getContentResolver().query(
                Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)),
                new String[] { ContactsContract.PhoneLookup._ID }, null, null, null)) {
            return c != null && c.getCount() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    static void notifyCall(Context ctx, String number, String level, JSONObject hit, boolean sw) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        int count = hit == null ? 0 : hit.optInt("count", 0);
        String title, text;
        if ("verified".equals(level)) {
            String name = hit.optString("name", "");
            title = "Linda: " + (name.isEmpty() ? (sw ? "Namba ya Safaricom" : "Safaricom number") : name);
            text = sw ? "Namba hii imethibitishwa na Safaricom." : "This number is verified by Safaricom.";
        } else if ("scam".equals(level)) {
            title = sw ? "Linda: ONYO, simu ya ulaghai" : "Linda: WARNING, scam caller";
            text = number + (sw
                ? " imethibitishwa kuwa ya ulaghai" + (count > 0 ? " (wateja " + count + " wameripoti)" : "") + ". Usitoe PIN wala kutuma pesa."
                : " is a confirmed scam number" + (count > 0 ? " (" + count + " customers reported it)" : "") + ". Do not share your PIN or send money.");
        } else {
            title = sw ? "Linda: kuwa mwangalifu na simu hii" : "Linda: be careful with this call";
            text = number + (sw
                ? " imeripotiwa na wateja " + count + ". Usitoe PIN wala kutuma pesa."
                : " was reported by " + count + " customers. Do not share your PIN or send money.");
        }
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Caller labels", NotificationManager.IMPORTANCE_HIGH));
        }
        Intent open = new Intent(ctx, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, 7, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(ctx, CHANNEL) : new Notification.Builder(ctx);
        b.setSmallIcon(R.drawable.ic_stat_linda)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(new Notification.BigTextStyle().bigText(text))
            .setCategory(Notification.CATEGORY_CALL)
            .setContentIntent(pi)
            .setAutoCancel(true);
        if (Build.VERSION.SDK_INT >= 26) b.setTimeoutAfter(120000L);
        nm.notify(("call" + norm(number)).hashCode(), b.build());
    }
}
