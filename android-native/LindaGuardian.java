package ke.linda.demo;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Family Guardian from the phone's background code. When a scam reaches this phone while Linda is closed, the
 * guardians who joined with this person's code are told. Only the kind of scam and the number are sent, never the message.
 */
final class LindaGuardian {
    private LindaGuardian() {}

    /** Call from a background thread. */
    static void notifySync(Context ctx, String kind, String number, int amount) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences(LindaScanner.PREFS, Context.MODE_PRIVATE);
            JSONArray keys = new JSONArray(sp.getString("gprotect", "[]"));
            String base = sp.getString("cfg_url", ""), apikey = sp.getString("cfg_key", "");
            if (keys.length() == 0 || base.isEmpty() || apikey.isEmpty()) return;
            String tok = LindaSyncWorker.token(sp, base, apikey);
            if (tok == null) return;
            for (int i = 0; i < keys.length(); i++) {
                JSONObject body = new JSONObject();
                body.put("p_key", keys.getString(i)).put("p_kind", kind).put("p_number", number == null ? "" : number)
                    .put("p_amount", amount).put("p_level", "red");
                LindaSyncWorker.http("POST", base + "/rest/v1/rpc/guardian_notify", apikey, tok, body.toString());
            }
        } catch (Exception e) {
            // best effort
        }
    }

    /** Call from any thread. */
    static void notifyAsync(final Context ctx, final String kind, final String number, final int amount) {
        final Context app = ctx.getApplicationContext();
        new Thread(new Runnable() { @Override public void run() { notifySync(app, kind, number, amount); } }).start();
    }
}
