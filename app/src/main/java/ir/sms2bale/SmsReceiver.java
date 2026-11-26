package ir.sms2bale;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Telephony;
import android.telephony.SmsMessage;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public class SmsReceiver extends BroadcastReceiver {
    public static final String WORK_TAG = "sms-forward-to-bale";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            return;
        }

        SharedPreferences settings = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        if (!settings.getBoolean("forwarding_enabled", false)) return;

        SmsMessage[] messages;
        try {
            messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        } catch (Exception e) {
            return;
        }
        if (messages == null || messages.length == 0) return;

        StringBuilder body = new StringBuilder();
        String sender = messages[0].getOriginatingAddress();
        long receivedAt = messages[0].getTimestampMillis();

        for (SmsMessage message : messages) {
            if (message == null) continue;
            if (body.length() > 0) body.append("");
            String part = message.getMessageBody();
            if (part != null) body.append(part);
        }

        if (body.length() == 0) return;

        String pendingId = PendingSmsStore.save(
                context.getApplicationContext(), sender, body.toString(), receivedAt);
        if (pendingId == null) return;

        Data input = new Data.Builder().putString(ForwardSmsWorker.KEY_PENDING_ID, pendingId).build();
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ForwardSmsWorker.class)
                .setInputData(input)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(WORK_TAG)
                .build();

        WorkManager.getInstance(context).enqueue(request);
    }
}
