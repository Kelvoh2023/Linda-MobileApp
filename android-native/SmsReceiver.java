package ke.linda.demo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

/** Hears about every new SMS, even when Linda is closed and the screen is off. */
public class SmsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
        SmsMessage[] parts = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (parts == null || parts.length == 0) return;
        StringBuilder sb = new StringBuilder();
        for (SmsMessage m : parts) sb.append(m.getMessageBody());
        final String address = parts[0].getOriginatingAddress();
        final String body = sb.toString();
        final long date = parts[0].getTimestampMillis();
        final Context app = context.getApplicationContext();
        // Linda is open: the app scans it on screen.
        if (LindaSmsPlugin.tryDeliver(address, body, date)) return;
        // Linda is closed: scan here. goAsync keeps the receiver alive while the scan runs on a worker thread.
        final PendingResult pending = goAsync();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    LindaSmsPlugin.scanInBackground(app, address, body, date);
                } catch (Throwable ignored) {
                    // never crash the receiver
                } finally {
                    pending.finish();
                }
            }
        }).start();
    }
}
