package ke.linda.demo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

/** Hears about every new SMS and hands it to Linda. */
public class SmsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
        SmsMessage[] parts = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (parts == null || parts.length == 0) return;
        StringBuilder body = new StringBuilder();
        for (SmsMessage m : parts) body.append(m.getMessageBody());
        LindaSmsPlugin.deliver(context.getApplicationContext(), parts[0].getOriginatingAddress(), body.toString(), parts[0].getTimestampMillis());
    }
}
