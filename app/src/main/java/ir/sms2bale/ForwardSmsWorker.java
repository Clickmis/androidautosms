package ir.sms2bale;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONObject;

import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;

public class ForwardSmsWorker extends Worker {
    public static final String KEY_PENDING_ID = "pending_sms_id";

    public ForwardSmsWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        String id = getInputData().getString(KEY_PENDING_ID);
        if (id == null) return Result.failure();

        SharedPreferences settings = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        if (!settings.getBoolean("forwarding_enabled", false)) {
            PendingSmsStore.remove(context, id);
            return Result.success();
        }

        JSONObject sms = PendingSmsStore.get(context, id);
        if (sms == null) return Result.success();

        String sender = sms.optString("sender", "نامشخص");
        String body = sms.optString("body", "");
        long receivedAt = sms.optLong("receivedAt", System.currentTimeMillis());

        String token = SecureStore.get(context, "bot_token");
        String chatId = SecureStore.get(context, "chat_id");
        if (token == null || chatId == null) {
            LogStore.add(context, sender, body, "ناموفق: تنظیمات ربات کامل نیست");
            PendingSmsStore.remove(context, id);
            return Result.failure();
        }

        String time = DateFormat.getDateTimeInstance(
                DateFormat.SHORT, DateFormat.SHORT).format(new Date(receivedAt));
        String message = "📩 پیامک جدید\n"
                + "فرستنده: " + sender + "\n"
                + "زمان دریافت: " + time + "\n\n"
                + body;

        try {
            BaleApi.ApiResult result = BaleApi.sendMessage(token, chatId, message);
            if (result.success) {
                LogStore.add(context, sender, body, "ارسال شد");
                PendingSmsStore.remove(context, id);
                return Result.success();
            }

            if (result.retryable && getRunAttemptCount() < 7) {
                return Result.retry();
            }

            LogStore.add(context, sender, body, "ناموفق: " + result.description);
            PendingSmsStore.remove(context, id);
            return Result.failure();
        } catch (IOException e) {
            if (getRunAttemptCount() < 7) return Result.retry();
            LogStore.add(context, sender, body, "ناموفق: خطای اتصال به اینترنت");
            PendingSmsStore.remove(context, id);
            return Result.failure();
        } catch (Exception e) {
            LogStore.add(context, sender, body, "ناموفق: خطای پردازش پیام");
            PendingSmsStore.remove(context, id);
            return Result.failure();
        }
    }
}
