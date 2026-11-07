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
import android.app.role.RoleManager;
import androidx.activity.result.ActivityResult;
import com.getcapacitor.annotation.ActivityCallback;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;

import org.json.JSONObject;
import android.provider.Settings;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;
import android.provider.CallLog;

/**
 * Lets the Linda web app read the SMS inbox and the contacts on this phone, and hears about new SMS.
 * Everything stays on the phone. Nothing here sends data anywhere.
 */
@CapacitorPlugin(
    name = "LindaSms",
    permissions = {
        @Permission(strings = { Manifest.permission.READ_SMS }, alias = "readSms"),
        @Permission(strings = { Manifest.permission.RECEIVE_SMS }, alias = "receiveSms"),
        @Permission(strings = { Manifest.permission.READ_CONTACTS }, alias = "contacts"),
        @Permission(strings = { Manifest.permission.READ_CALL_LOG }, alias = "callLog"),
        @Permission(strings = { "android.permission.POST_NOTIFICATIONS" }, alias = "notifications"),
        @Permission(strings = { "android.permission.READ_PHONE_STATE" }, alias = "phoneState")
    }
)
public class LindaSmsPlugin extends Plugin {

    static volatile LindaSmsPlugin instance;
    static volatile boolean foreground = false;
    static volatile String launchAddr = null;
    static volatile long launchDate = 0;
    static volatile String launchCall = null;

    /** Remember which Linda notification the person tapped, so the app can open that message. */
    static void setLaunch(Intent i) {
        if (i != null && i.hasExtra("linda_call")) {
            launchCall = i.getStringExtra("linda_call");
            i.removeExtra("linda_call");
        }
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
        if (launchCall != null) { ret.put("call", launchCall); launchCall = null; }
        if (launchAddr != null) {
            ret.put("address", launchAddr);
            ret.put("date", launchDate);
            launchAddr = null;
        }
        call.resolve(ret);
    }

    /** Is Linda the phone's caller-ID app yet? (Android 10 and newer) */
    private boolean callRoleHeld() {
        if (Build.VERSION.SDK_INT < 29) return false;
        RoleManager rm = (RoleManager) getContext().getSystemService(Context.ROLE_SERVICE);
        return rm != null && rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) && rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING);
    }

    @PluginMethod
    public void callScreeningStatus(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("supported", Build.VERSION.SDK_INT >= 29);
        ret.put("held", callRoleHeld());
        call.resolve(ret);
    }

    /** Opens Android's own "make Linda your caller ID app" dialog. */
    @PluginMethod
    public void requestCallScreening(PluginCall call) {
        if (Build.VERSION.SDK_INT < 29) { call.reject("Needs Android 10 or newer"); return; }
        RoleManager rm = (RoleManager) getContext().getSystemService(Context.ROLE_SERVICE);
        if (rm == null || !rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) { call.reject("Not available on this phone"); return; }
        if (rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) { JSObject r = new JSObject(); r.put("held", true); call.resolve(r); return; }
        startActivityForResult(call, rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), "roleResult");
    }

    @ActivityCallback
    private void roleResult(PluginCall call, ActivityResult result) {
        if (call == null) return;
        JSObject r = new JSObject();
        r.put("held", callRoleHeld());
        call.resolve(r);
    }

    /** Can Linda show notifications? (Android 13 and newer must ask first.) */
    @PluginMethod
    public void notifStatus(PluginCall call) {
        JSObject ret = new JSObject();
        boolean ok = Build.VERSION.SDK_INT < 33 || getPermissionState("notifications") == PermissionState.GRANTED;
        if (ok) { NotificationManager nm = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE); ok = nm == null || nm.areNotificationsEnabled(); }
        ret.put("granted", ok);
        call.resolve(ret);
    }

    @PluginMethod
    public void requestNotif(PluginCall call) {
        if (Build.VERSION.SDK_INT >= 33 && getPermissionState("notifications") != PermissionState.GRANTED) {
            requestPermissionForAlias("notifications", call, "notifDone");
        } else notifStatus(call);
    }

    @com.getcapacitor.annotation.PermissionCallback
    private void notifDone(PluginCall call) { notifStatus(call); }

    /** Shows a sample "scam caller" label so the person can check notifications work, without needing a real call. */
    @PluginMethod
    public void overlayStatus(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("granted", LindaOverlay.allowed(getContext()));
        call.resolve(ret);
    }

    /** Opens Android's "Display over other apps" page for Linda. */
    @PluginMethod
    public void requestOverlay(PluginCall call) {
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getContext().getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
        } catch (Exception e) { /* ignore */ }
        call.resolve();
    }

    /** Starts the ~15 minute background update of the fraud list (works with Linda closed). */
    @PluginMethod
    public void scheduleSync(PluginCall call) {
        String url = call.getString("url", ""), key = call.getString("key", "");
        if (url.isEmpty() || key.isEmpty()) { call.resolve(); return; }
        getContext().getSharedPreferences(LindaScanner.PREFS, Context.MODE_PRIVATE).edit().putString("cfg_url", url.replaceAll("/+$", "")).putString("cfg_key", key).apply();
        try {
            PeriodicWorkRequest w = new PeriodicWorkRequest.Builder(LindaSyncWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build();
            WorkManager.getInstance(getContext()).enqueueUniquePeriodicWork("linda-sync", ExistingPeriodicWorkPolicy.KEEP, w);
        } catch (Exception e) { /* ignore */ }
        call.resolve();
    }

    @PluginMethod
    public void callLabelTest(PluginCall call) {
        try {
            JSONObject hit = new JSONObject();
            hit.put("status", "confirmed"); hit.put("count", 5);
            boolean sw = "sw".equals(getContext().getSharedPreferences(LindaScanner.PREFS, Context.MODE_PRIVATE).getString("lang", "en"));
            LindaCallScreen.notifyCall(getContext(), "0712 345 678", "scam", hit, sw);
        } catch (Exception e) { /* ignore */ }
        call.resolve();
    }

    /** Recent incoming, missed and rejected calls, so the person can report a scam caller after the call. */
    @PluginMethod
    public void getCalls(PluginCall call) {
        if (getPermissionState("callLog") != PermissionState.GRANTED) {
            if (call.getBoolean("ask", false)) requestPermissionForAlias("callLog", call, "callsDone");
            else { JSObject r = new JSObject(); r.put("denied", true); call.resolve(r); }
            return;
        }
        callsDone(call);
    }

    @com.getcapacitor.annotation.PermissionCallback
    private void callsDone(PluginCall call) {
        JSObject ret = new JSObject();
        if (getPermissionState("callLog") != PermissionState.GRANTED) { ret.put("denied", true); call.resolve(ret); return; }
        JSArray out = new JSArray();
        try (Cursor c = getContext().getContentResolver().query(CallLog.Calls.CONTENT_URI,
                new String[] { CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.CACHED_NAME },
                CallLog.Calls.TYPE + " IN (1,3,5)", null, CallLog.Calls.DATE + " DESC")) {
            int n = 0;
            while (c != null && n < 60 && c.moveToNext()) {
                String num = c.getString(0);
                if (num == null || num.isEmpty()) continue;
                JSObject o = new JSObject();
                o.put("number", num); o.put("type", c.getInt(1)); o.put("date", c.getLong(2)); o.put("name", c.getString(3) == null ? "" : c.getString(3));
                out.put(o); n++;
            }
        } catch (Exception e) { /* empty */ }
        ret.put("calls", out);
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
        int total = 0;
        JSArray out = new JSArray();
        try (Cursor c = getContext().getContentResolver().query(
                Uri.parse("content://sms/inbox"),
                new String[] { "_id", "address", "body", "date" },
                null, null, "date DESC")) {
            if (c != null) {
                total = c.getCount();
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
        ret.put("total", total);
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
        String gp = call.getString("gprotect"), gw = call.getString("gwatch"), gs = call.getString("gsince");
        if (gp != null) e.putString("gprotect", gp);
        if (gw != null) e.putString("gwatch", gw);
        if (gs != null && !gs.isEmpty()) e.putString("g_app_since", gs);
        String rep = call.getString("reported");
        if (rep != null) e.putString("reported", rep);
        if (call.hasOption("calls")) e.putBoolean("calls", Boolean.TRUE.equals(call.getBoolean("calls")));
        if (call.hasOption("silence")) e.putBoolean("silence", Boolean.TRUE.equals(call.getBoolean("silence")));
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
            if ("scam".equals(r.optString("level", "ok"))) {
                boolean imp = false;
                org.json.JSONArray rs = r.optJSONArray("reasons");
                for (int i = 0; rs != null && i < rs.length(); i++) { JSONObject o = rs.optJSONObject(i); if (o != null && "impersonation".equals(o.optString("k"))) imp = true; }
                LindaGuardian.notifySync(ctx, imp ? "imp" : "sms", address, 0);
            }
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
