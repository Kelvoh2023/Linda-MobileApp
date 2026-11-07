package ke.linda.demo;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Runs every ~15 minutes even when Linda is closed: downloads the newest fraud list and warnings from the server,
 * and tells the person when a number THEY reported has been confirmed as a scam. Read-only: it never sends reports.
 */
public class LindaSyncWorker extends Worker {
    public LindaSyncWorker(@NonNull Context ctx, @NonNull WorkerParameters p) { super(ctx, p); }

    static String[] http(String method, String url, String key, String bearer, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("apikey", key);
        if (bearer != null) c.setRequestProperty("Authorization", "Bearer " + bearer);
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            try (OutputStream o = c.getOutputStream()) { o.write(body.getBytes(StandardCharsets.UTF_8)); }
        }
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        StringBuilder sb = new StringBuilder();
        if (in != null) {
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
        }
        c.disconnect();
        return new String[] { String.valueOf(code), sb.toString() };
    }

    /** A background-only anonymous sign-in, separate from the one the app uses. It can only read the public fraud list. */
    static String token(SharedPreferences sp, String base, String key) throws Exception {
        long exp = sp.getLong("bg_exp", 0);
        String tok = sp.getString("bg_token", "");
        if (!tok.isEmpty() && exp - System.currentTimeMillis() > 60000) return tok;
        String rt = sp.getString("bg_refresh", "");
        String[] r = null;
        if (!rt.isEmpty()) {
            r = http("POST", base + "/auth/v1/token?grant_type=refresh_token", key, null, new JSONObject().put("refresh_token", rt).toString());
            if (!"200".equals(r[0])) r = null;
        }
        if (r == null) {
            r = http("POST", base + "/auth/v1/signup", key, null, "{}");
            if (!"200".equals(r[0])) return null;
        }
        JSONObject j = new JSONObject(r[1]);
        long life = j.has("expires_in") ? j.getLong("expires_in") * 1000L : 3600000L;
        sp.edit().putString("bg_token", j.getString("access_token")).putString("bg_refresh", j.optString("refresh_token", ""))
            .putLong("bg_exp", System.currentTimeMillis() + life).apply();
        return j.getString("access_token");
    }

    @NonNull @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        SharedPreferences sp = ctx.getSharedPreferences(LindaScanner.PREFS, Context.MODE_PRIVATE);
        String base = sp.getString("cfg_url", ""), key = sp.getString("cfg_key", "");
        if (base.isEmpty() || key.isEmpty()) return Result.success();
        try {
            String tok = token(sp, base, key);
            if (tok == null) return Result.retry();
            String since = sp.getString("bg_since", "");
            String q = "/rest/v1/numbers?select=*&order=updated_at.asc&limit=1000" + (since.isEmpty() ? "" : "&updated_at=gt." + URLEncoder.encode(since, "UTF-8"));
            String[] r = http("GET", base + q, key, tok, null);
            if (!"200".equals(r[0])) { if ("401".equals(r[0])) sp.edit().remove("bg_token").apply(); return Result.retry(); }
            JSONArray rows = new JSONArray(r[1]);
            JSONObject db;
            try { db = new JSONObject(sp.getString("db", "{}")); } catch (Exception e) { db = new JSONObject(); }
            Set<String> reported = new HashSet<>();
            try { JSONArray ra = new JSONArray(sp.getString("reported", "[]")); for (int i = 0; i < ra.length(); i++) reported.add(ra.getString(i)); } catch (Exception e) { /* none */ }
            Set<String> told = new HashSet<>();
            try { JSONArray ta = new JSONArray(sp.getString("bg_told", "[]")); for (int i = 0; i < ta.length(); i++) told.add(ta.getString(i)); } catch (Exception e) { /* none */ }
            boolean sw = "sw".equals(sp.getString("lang", "en"));
            String last = since;
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                String norm = row.optString("number_norm"), display = row.optString("display", norm), status = row.optString("status");
                String useKey = display.isEmpty() ? norm : display;
                Iterator<String> it = db.keys();
                while (it.hasNext()) { String k = it.next(); if (!norm.isEmpty() && LindaCallScreen.norm(k).equals(norm)) { useKey = k; break; } }
                JSONObject rec = new JSONObject();
                String type = row.optString("type", "other");
                rec.put("status", status);
                rec.put("type", "verified".equals(status) ? "verified" : ("unknown".equals(type) ? "other" : type));
                rec.put("count", row.optInt("reporters", 0));
                rec.put("name", row.optString("name", ""));
                rec.put("sample", row.optString("sample", ""));
                db.put(useKey, rec);
                last = row.optString("updated_at", last);
                if ("confirmed".equals(status) && reported.contains(norm) && !told.contains(norm)) {
                    told.add(norm);
                    notify(ctx, norm.hashCode(),
                        sw ? "Linda: ripoti yako imethibitishwa" : "Linda: your report was confirmed",
                        sw ? "Shukrani kwako, " + display + " imethibitishwa kuwa ya ulaghai na sasa inaonywa kwa watumiaji wote wa Linda."
                           : "Thanks to you, " + display + " is now confirmed as a scam and flagged for everyone using Linda.");
                }
            }
            SharedPreferences.Editor e = sp.edit();
            if (rows.length() > 0) e.putString("db", db.toString());
            e.putString("bg_since", last).putString("bg_told", new JSONArray(told).toString());
            e.apply();
            // The active warning from the fraud team
            String[] b = http("GET", base + "/rest/v1/broadcasts?select=id,text&active=eq.true&order=created_at.desc&limit=1", key, tok, null);
            if ("200".equals(b[0])) {
                JSONArray ba = new JSONArray(b[1]);
                String id = ba.length() > 0 ? ba.getJSONObject(0).optString("id") : "none";
                String seen = sp.getString("bg_bc", null);
                if (seen != null && !seen.equals(id) && !"none".equals(id)) {
                    notify(ctx, 4242, sw ? "Onyo kutoka Safaricom" : "Safaricom alert", ba.getJSONObject(0).optString("text"));
                }
                sp.edit().putString("bg_bc", id).apply();
            }
            pollGuardian(ctx, sp, base, key, tok, sw);
            return Result.success();
        } catch (Exception ex) {
            return Result.retry();
        }
    }

    /** If this phone is a guardian: ask the server for new alerts about the people it protects. */
    private void pollGuardian(Context ctx, SharedPreferences sp, String base, String key, String tok, boolean sw) {
        try {
            JSONArray keys = new JSONArray(sp.getString("gwatch", "[]"));
            if (keys.length() == 0) return;
            String mine = sp.getString("g_since", "");
            String app = sp.getString("g_app_since", "");
            String since = mine.compareTo(app) >= 0 ? mine : app;
            if (since.isEmpty()) {
                java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'+00:00'", java.util.Locale.US);
                f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                since = f.format(new java.util.Date());
            }
            String newest = since;
            for (int i = 0; i < keys.length(); i++) {
                String body = new JSONObject().put("p_key", keys.getString(i)).put("p_since", since).toString();
                String[] r = http("POST", base + "/rest/v1/rpc/guardian_poll", key, tok, body);
                if (!"200".equals(r[0])) continue;
                JSONArray rows = new JSONArray(r[1]);
                for (int j = 0; j < rows.length(); j++) {
                    JSONObject a = rows.getJSONObject(j);
                    String who = a.optString("protected_name", ""); if (who.isEmpty()) who = sw ? "Mpendwa wako" : "Someone you protect";
                    String n = a.optString("number", "");
                    String kind = a.optString("kind");
                    String text;
                    if ("sms".equals(kind)) text = sw ? "SMS ya ulaghai imezuiwa kwenye simu ya " + who + ". Ilitoka " + n + "." : "A scam text was blocked on " + who + "'s phone. It came from " + n + ".";
                    else if ("imp".equals(kind)) text = sw ? "Ujumbe unaojifanya mtu anayemjua " + who + " uliomba pesa. Ulitoka " + n + "." : "A text pretending to be someone " + who + " knows asked for money. It came from " + n + ".";
                    else if ("call".equals(kind)) text = sw ? who + " anapigiwa simu na " + n + ", namba iliyoripotiwa kwa ulaghai." : who + " is getting a call from " + n + ", a number reported for fraud.";
                    else if ("payblock".equals(kind)) text = sw ? who + " alikuwa karibu kutuma KSh " + a.optInt("amount") + " kwa " + n + ", kisha akaghairi." : who + " was about to send KSh " + a.optInt("amount") + " to " + n + ", then cancelled after Linda's warning.";
                    else text = sw ? who + " alituma KSh " + a.optInt("amount") + " kwa " + n + " licha ya onyo la Linda. Mpigie sasa." : who + " sent KSh " + a.optInt("amount") + " to " + n + " after Linda warned against it. Call them now.";
                    notify(ctx, ("g" + a.optString("id")).hashCode(), sw ? "Linda: " + who + " yuko hatarini" : "Linda: " + who + " may be at risk", text);
                    String at = a.optString("created_at", "");
                    if (at.compareTo(newest) > 0) newest = at;
                }
            }
            sp.edit().putString("g_since", newest).apply();
        } catch (Exception e) {
            // try again next time
        }
    }

    private void notify(Context ctx, int id, String title, String text) {
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(new NotificationChannel(LindaAlert.CHANNEL, "Scam alerts", NotificationManager.IMPORTANCE_HIGH));
            Intent open = new Intent(ctx, MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(ctx, id, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(ctx, LindaAlert.CHANNEL) : new Notification.Builder(ctx);
            b.setSmallIcon(R.drawable.ic_stat_linda).setContentTitle(title).setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text)).setContentIntent(pi).setAutoCancel(true);
            nm.notify(id, b.build());
        } catch (Exception e) {
            // ignore
        }
    }
}
