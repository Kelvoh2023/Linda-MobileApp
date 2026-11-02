package ke.linda.demo;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.ContactsContract;

import app.cash.quickjs.QuickJs;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Scans one SMS with the SAME model.js the app uses (run inside QuickJS), so the background scan and
 * the on-screen scan can never disagree. Works with the screen off and the app closed.
 */
final class LindaScanner {
    static final String PREFS = "linda_native";
    private static QuickJs js;
    private static String dbLoaded = "";
    private static long contactsAt = 0;

    private LindaScanner() {}

    /** Returns the model's answer: score, level (ok, caution, scam), reasons, plus "texts" = the reasons in words. */
    static synchronized JSONObject scan(Context ctx, String sender, String body) throws Exception {
        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            if (js == null) {
                js = QuickJs.create();
                js.evaluate(readAsset(ctx, "linda/model.js"), "model.js");
                js.evaluate("var __db = {}; var __contacts = [];", "init.js");
                dbLoaded = "";
                contactsAt = 0;
            }
            String db = sp.getString("db", "{}");
            if (!db.equals(dbLoaded)) {
                String clean = "{}";
                try { clean = new JSONObject(db).toString(); } catch (Exception e) { /* keep empty list */ }
                js.evaluate("__db = " + clean + ";", "db.js");
                dbLoaded = db;
            }
            long now = System.currentTimeMillis();
            if (now - contactsAt > 600000L) {
                js.evaluate("__contacts = " + readContacts(ctx) + ";", "contacts.js");
                contactsAt = now;
            }
            String lang = "sw".equals(sp.getString("lang", "en")) ? "sw" : "en";
            String script = "(function(){"
                + "var r=LindaModel.scan(" + JSONObject.quote(body == null ? "" : body)
                + ",{sender:" + JSONObject.quote(sender == null ? "" : sender) + ",db:__db,contacts:__contacts});"
                + "var L=" + JSONObject.quote(lang) + ";"
                + "r.texts=r.reasons.map(function(x){return LindaModel.reasonText(x,L);});"
                + "return JSON.stringify(r);})()";
            Object out = js.evaluate(script, "scan.js");
            return new JSONObject(String.valueOf(out));
        } catch (Exception e) {
            reset(); // start clean next time
            throw e;
        }
    }

    private static void reset() {
        try { if (js != null) js.close(); } catch (Exception ignored) { }
        js = null;
    }

    private static String readAsset(Context ctx, String name) throws Exception {
        try (InputStream in = ctx.getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /** Saved contacts as [{n, name}], the shape model.js expects. Empty if the permission is not granted. */
    private static String readContacts(Context ctx) {
        JSONArray arr = new JSONArray();
        if (ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return arr.toString();
        try (Cursor c = ctx.getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[] { ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER },
                null, null, null)) {
            if (c != null) {
                int count = 0;
                while (count < 3000 && c.moveToNext()) {
                    String name = c.getString(0), number = c.getString(1);
                    if (name == null || number == null) continue;
                    JSONObject o = new JSONObject();
                    o.put("n", number);
                    o.put("name", name);
                    arr.put(o);
                    count++;
                }
            }
        } catch (Exception e) {
            // contacts are optional
        }
        return arr.toString();
    }
}
