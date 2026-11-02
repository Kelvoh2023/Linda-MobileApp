package ke.linda.demo;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;

import org.json.JSONObject;

/**
 * Lets the Linda web app read the SMS inbox and the contacts on this phone, and hears about new SMS.
 * Everything stays on the phone. Nothing here sends data anywhere.
 */
@CapacitorPlugin(
    name = "LindaSms",
    permissions = {
        @Permission(strings = { Manifest.permission.READ_SMS }, alias = "readSms"),
        @Permission(strings = { Manifest.permission.RECEIVE_SMS }, alias = "receiveSms"),
        @Permission(strings = { Manifest.permission.READ_CONTACTS }, alias = "contacts")
    }
)
public class LindaSmsPlugin extends Plugin {

    static volatile LindaSmsPlugin instance;
    static volatile boolean foreground = false;
    static volatile String launchAddr = null;
    static volatile long launchDate = 0;

    /** Remember which Linda notification the person tapped, so the app can open that message. */
    static void setLaunch(Intent i) {
        if (i != null && i.hasExtra("linda_addr")) {
            launchAddr = i.getStringExtra("linda_addr");
            launchDate = i.getLongExtra("linda_date", 0);
            i.removeExtra("linda_addr");
            i.removeExtra("linda_date");
        }
    }

    @PluginMethod
    public void takeLaunch(PluginCall call) {
        JSObject ret = new JSObject();
        if (launchAddr != null) {
            ret.put("address", launchAddr);
            ret.put("date", launchDate);
            launchAddr = null;
        }
        call.resolve(ret);
    }

    @PluginMethod
    public void appInfo(PluginCall call) {
        JSObject ret = new JSObject();
        try {
            ret.put("version", getContext().getPackageManager().getPackageInfo(getContext().getPackageName(), 0).versionName);
        } catch (Exception e) {
            ret.put("version", "");
        }
        call.resolve(ret);
    }

    @Override
    public void load() {
        instance = this;
    }

    @Override
    protected void handleOnResume() {
        foreground = true;
    }

    @Override
    protected void handleOnPause() {
        foreground = false;
    }

    @Override
    protected void handleOnDestroy() {
        foreground = false;
        if (instance == this) instance = null;
    }

    /** Newest messages first. */
    @PluginMethod
    public void getInbox(PluginCall call) {
        if (getPermissionState("readSms") != PermissionState.GRANTED) {
            call.reject("SMS permission is not granted");
            return;
        }
        int limit = call.getInt("limit", 150);
        JSArray out = new JSArray();
        try (Cursor c = getContext().getContentResolver().query(
                Uri.parse("content://sms/inbox"),
                new String[] { "_id", "address", "body", "date" },
                null, null, "date DESC")) {
            if (c != null) {
                int n = 0;
                while (n < limit && c.moveToNext()) {
                    JSObject o = new JSObject();
                    o.put("id", c.getLong(0));
                    o.put("address", c.getString(1));
                    o.put("body", c.getString(2));
                    o.put("date", c.getLong(3));
                    out.put(o);
                    n++;
                }
            }
        } catch (Exception e) {
            call.reject("Could not read messages: " + e.getMessage());
            return;
        }
        JSObject ret = new JSObject();
        ret.put("messages", out);
        call.resolve(ret);
    }

    @PluginMethod
    public void getContacts(PluginCall call) {
        JSArray out = new JSArray();
        if (getPermissionState("contacts") == PermissionState.GRANTED) {
            try (Cursor c = getContext().getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[] {
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                    },
                    null, null, null)) {
                if (c != null) {
                    int n = 0;
                    while (n < 3000 && c.moveToNext()) {
                        String name = c.getString(0);
                        String number = c.getString(1);
                        if (name == null || number == null) continue;
                        JSObject o = new JSObject();
                        o.put("name", name);
                        o.put("number", number);
                        out.put(o);
                        n++;
                    }
                }
            } catch (Exception e) {
                // contacts are optional, carry on without them
            }
        }
        JSObject ret = new JSObject();
        ret.put("contacts", out);
        call.resolve(ret);
    }

    /** The app tells the phone's background scanner what it needs: on or off, language, Safaricom's fraud list. */
    @PluginMethod
    public void syncState(PluginCall call) {
        SharedPreferences.Editor e = getContext().getSharedPreferences(LindaScanner.PREFS, Context.MODE_PRIVATE).edit();
        if (call.hasOption("enabled")) e.putBoolean("enabled", Boolean.TRUE.equals(call.getBoolean("enabled")));
        String db = call.getString("db");
        if (db != null) e.putString("db", db);
        String lang = call.getString("lang");
        if (lang != null) e.putString("lang", lang);
        String trusted = call.getString("trusted");
        if (trusted != null) e.putString("trusted", trusted);
        e.apply();
        call.resolve();
    }

    /** If Linda is open on screen, hand the SMS to the app. Returns true when the app took it. */
    static boolean tryDeliver(String address, String body, long date) {
        LindaSmsPlugin p = instance;
        if (p != null && foreground && p.hasListeners("smsReceived")) {
            JSObject o = new JSObject();
            o.put("address", address);
            o.put("body", body);
            o.put("date", date);
            p.notifyListeners("smsReceived", o, true);
            return true;
        }
        return false;
    }

    /** Linda is closed or in the background: scan here with the same model, and notify if it looks risky. */
    static void scanInBackground(Context ctx, String address, String body, long date) {
        SharedPreferences sp = ctx.getSharedPreferences(LindaScanner.PREFS, Context.MODE_PRIVATE);
        if (!sp.getBoolean("enabled", false)) return; // protection is off, or the app has never turned it on
        try {
            JSONObject r = LindaScanner.scan(ctx, address, body);
            if (!"ok".equals(r.optString("level", "ok"))) LindaAlert.show(ctx, address, r, sp.getString("lang", "en"), date);
        } catch (Throwable t) {
            // The scanner could not run. Fall back to the simple keyword check so the person still gets a nudge.
            if (quickRisky(address, body)) nudge(ctx, address);
        }
    }

    private static final String[] RISKY = {
        "reverse", "wrong number", "by mistake", "you won", "winner", "prize", "refund", "claim",
        "http", "www.", "congratulations", "new number", "lost my phone", "haraka", "ada ya",
        "processing fee", "send your pin", "tuma pin"
    };

    static boolean quickRisky(String address, String body) {
        String a = address == null ? "" : address.toUpperCase();
        String n = a.replaceAll("[^A-Z0-9]", "");
        if (n.startsWith("MPESA") || n.startsWith("SAFARICOM") || n.startsWith("STAFFUPDATE") || n.startsWith("STAFFNEWS")) return false;
        String b = body == null ? "" : body.toLowerCase();
        for (String k : RISKY) if (b.contains(k)) return true;
        return false;
    }

    private static void nudge(Context ctx, String address) {
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(new NotificationChannel("linda-alerts", "Scam alerts", NotificationManager.IMPORTANCE_HIGH));
            }
            Intent open = new Intent(ctx, MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(ctx, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(ctx, "linda-alerts") : new Notification.Builder(ctx);
            b.setSmallIcon(R.drawable.ic_stat_linda)
                .setContentTitle("Linda: check this message")
                .setContentText("New text from " + (address == null ? "an unknown sender" : address) + ". Tap to let Linda check it.")
                .setContentIntent(pi)
                .setAutoCancel(true);
            nm.notify((int) (System.currentTimeMillis() % 1000000), b.build());
        } catch (Exception e) {
            // never crash the receiver
        }
    }
}
