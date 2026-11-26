package ir.sms2bale;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.work.WorkManager;
import androidx.core.view.WindowCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQUEST_SMS = 301;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private EditText tokenInput;
    private EditText chatIdInput;
    private CheckBox consentCheck;
    private TextView permissionStatus;
    private TextView forwardingStatus;
    private LinearLayout historyContainer;
    private Button enableButton;
    private Button testButton;

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        getWindow().setStatusBarColor(Color.rgb(248, 250, 252));
        getWindow().setNavigationBarColor(Color.rgb(248, 250, 252));
        if (Build.VERSION.SDK_INT >= 23) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        buildScreen();
        loadSettings();
        refreshStatus();
        renderHistory();
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(248, 250, 252));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(18), dp(16), dp(28));
        page.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroll.addView(page);

        TextView title = text("پیامک به بله", 25, "#172554", true);
        page.addView(title);
        TextView subtitle = text("ارسال پیامک‌های دریافتی به گفتگوی ربات شما", 13, "#64748B", false);
        addTop(page, subtitle, 5);

        LinearLayout warning = card();
        warning.setBackground(round("#FFF7ED", "#FED7AA", 16));
        addText(warning, "قبل از فعال‌سازی، حتماً بخوانید", 15, "#9A3412", true, 0, 0, 0, 8);
        addText(warning,
                "این برنامه پس از اجازه شما، متن پیامک‌های جدید و شماره فرستنده را به ربات بله و شناسه گفتگویی که وارد می‌کنید ارسال می‌کند. پیامک‌های بانکی، رمز یک‌بارمصرف و اطلاعات خصوصی هم ممکن است ارسال شوند. فقط برای گوشی خودتان یا دستگاهی که صاحب آن آگاهانه رضایت داده استفاده کنید.",
                12, "#7C2D12", false, 0, 0, 0, 0);
        addTop(page, warning, 18);

        LinearLayout setup = card();
        addText(setup, "۱. اتصال به ربات بله", 17, "#0F172A", true, 0, 0, 0, 5);
        addText(setup, "توکن ربات را از BotFather بله بگیرید. شناسه گفتگوی مقصد را هم وارد کنید.", 11, "#64748B", false, 0, 0, 0, 12);

        tokenInput = field("توکن ربات بله", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        tokenInput.setSingleLine(true);
        setup.addView(tokenInput, matchWrap());
        addTop(setup, text("توکن فقط روی همین گوشی و با Android Keystore رمزنگاری می‌شود.", 10, "#64748B", false), 5);

        chatIdInput = field("شناسه گفتگو (Chat ID)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        chatIdInput.setSingleLine(true);
        chatIdInput.setTextDirection(View.TEXT_DIRECTION_LTR);
        addTop(setup, chatIdInput, 12);

        Button saveButton = button("ذخیره تنظیمات", "#E2E8F0", "#1E293B");
        saveButton.setOnClickListener(v -> saveSettings());
        addTop(setup, saveButton, 12);

        testButton = button("آزمایش اتصال و ارسال پیام", "#DBEAFE", "#1D4ED8");
        testButton.setOnClickListener(v -> testConnection());
        addTop(setup, testButton, 8);
        addTop(page, setup, 14);

        LinearLayout access = card();
        addText(access, "۲. اجازه و فعال‌سازی", 17, "#0F172A", true, 0, 0, 0, 5);
        addText(access,
                "برنامه برای دریافت پیامک جدید فقط مجوز RECEIVE_SMS را درخواست می‌کند. نیازی به خواندن کل صندوق پیامک‌ها، مخاطبین یا تماس‌ها ندارد.",
                11, "#64748B", false, 0, 0, 0, 10);

        permissionStatus = text("", 12, "#475569", true);
        access.addView(permissionStatus);
        forwardingStatus = text("", 12, "#475569", true);
        addTop(access, forwardingStatus, 7);

        consentCheck = new CheckBox(this);
        consentCheck.setText("متوجه هستم که پیامک‌های دریافتی به ربات بله ارسال می‌شوند و با این کار موافقم.");
        consentCheck.setTextSize(11);
        consentCheck.setTextColor(Color.rgb(51, 65, 85));
        consentCheck.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.rgb(37, 99, 235)));
        addTop(access, consentCheck, 12);

        enableButton = button("درخواست دسترسی و فعال‌سازی", "#1D4ED8", "#FFFFFF");
        enableButton.setOnClickListener(v -> activateForwarding());
        addTop(access, enableButton, 10);

        Button disableButton = button("خاموش کردن ارسال پیامک‌ها", "#FEE2E2", "#B91C1C");
        disableButton.setOnClickListener(v -> disableForwarding());
        addTop(access, disableButton, 8);
        addTop(page, access, 14);

        LinearLayout history = card();
        addText(history, "۳. گفتگوی ارسال‌ها با ربات", 17, "#0F172A", true, 0, 0, 0, 5);
        addText(history, "این بخش پیامک‌های ارسال‌شده و نتیجه ارسال را روی همین گوشی نشان می‌دهد؛ پاسخ‌های دریافتی ربات در این نسخه دریافت نمی‌شوند.", 10, "#64748B", false, 0, 0, 0, 12);
        historyContainer = new LinearLayout(this);
        historyContainer.setOrientation(LinearLayout.VERTICAL);
        history.addView(historyContainer, matchWrap());
        addTop(page, history, 14);

        TextView footer = text("ارتباط با بله از طریق HTTPS انجام می‌شود. ارسال فقط هنگام روشن بودن قابلیت و پس از اعطای مجوز انجام می‌شود.", 10, "#94A3B8", false);
        footer.setGravity(Gravity.CENTER);
        addTop(page, footer, 15);

        setContentView(scroll);
    }

    private LinearLayout card() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(15), dp(15), dp(15), dp(15));
        layout.setBackground(round("#FFFFFF", "#E2E8F0", 18));
        return layout;
    }

    private GradientDrawable round(String fill, String stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor(fill));
        drawable.setCornerRadius(dp(radius));
        if (stroke != null) drawable.setStroke(dp(1), Color.parseColor(stroke));
        return drawable;
    }

    private TextView text(String value, int size, String color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(Color.parseColor(color));
        view.setGravity(Gravity.RIGHT);
        view.setTextDirection(View.TEXT_DIRECTION_RTL);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private void addText(LinearLayout parent, String value, int size, String color,
                         boolean bold, int left, int top, int right, int bottom) {
        TextView view = text(value, size, color, bold);
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        parent.addView(view, params);
    }

    private void addTop(LinearLayout parent, View view, int marginTop) {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(marginTop);
        parent.addView(view, params);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private EditText field(String hint, int inputType) {
        EditText edit = new EditText(this);
        edit.setSingleLine(true);
        edit.setTextSize(13);
        edit.setHint(hint);
        edit.setInputType(inputType);
        edit.setPadding(dp(12), dp(8), dp(12), dp(8));
        edit.setBackground(round("#F8FAFC", "#CBD5E1", 12));
        edit.setTextColor(Color.rgb(15, 23, 42));
        edit.setHintTextColor(Color.rgb(148, 163, 184));
        return edit;
    }

    private Button button(String label, String bg, String fg) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setTextColor(Color.parseColor(fg));
        button.setBackground(round(bg, null, 12));
        button.setMinHeight(dp(46));
        button.setPadding(dp(12), dp(5), dp(12), dp(5));
        return button;
    }

    private void loadSettings() {
        String token = SecureStore.get(this, "bot_token");
        String chatId = SecureStore.get(this, "chat_id");
        if (token != null) tokenInput.setText(token);
        if (chatId != null) chatIdInput.setText(chatId);
    }

    private boolean saveSettings() {
        String token = tokenInput.getText().toString().trim();
        String chatId = chatIdInput.getText().toString().trim();
        if (token.isEmpty() || chatId.isEmpty()) {
            toast("توکن ربات و شناسه گفتگو را وارد کنید.");
            return false;
        }
        boolean first = SecureStore.put(this, "bot_token", token);
        boolean second = SecureStore.put(this, "chat_id", chatId);
        if (!first || !second) {
            toast("ذخیره امن تنظیمات انجام نشد. فضای امن دستگاه را بررسی کنید.");
            return false;
        }
        toast("تنظیمات به‌صورت رمزنگاری‌شده ذخیره شد.");
        return true;
    }

    private void testConnection() {
        if (!saveSettings()) return;
        String token = tokenInput.getText().toString().trim();
        String chatId = chatIdInput.getText().toString().trim();
        testButton.setEnabled(false);
        testButton.setText("در حال آزمایش...");
        executor.execute(() -> {
            BaleApi.ApiResult result;
            try {
                result = BaleApi.sendMessage(token, chatId,
                        "✅ اتصال برنامه «پیامک به بله» با موفقیت آزمایش شد.");
            } catch (Exception e) {
                result = new BaleApi.ApiResult(false, true, "خطا در اتصال به اینترنت یا سرور بله.");
            }
            BaleApi.ApiResult finalResult = result;
            runOnUiThread(() -> {
                testButton.setEnabled(true);
                testButton.setText("آزمایش اتصال و ارسال پیام");
                LogStore.add(this, "آزمایش اتصال", "پیام آزمایشی برنامه", finalResult.success ? "ارسال شد" : "ناموفق: " + finalResult.description);
                renderHistory();
                toast(finalResult.success ? "پیام آزمایشی به ربات ارسال شد." : "ارسال ناموفق: " + finalResult.description);
            });
        });
    }

    private void activateForwarding() {
        if (!consentCheck.isChecked()) {
            new AlertDialog.Builder(this)
                    .setTitle("تأیید آگاهانه لازم است")
                    .setMessage("این برنامه محتوای پیامک‌های دریافتی و شماره فرستنده را به ربات بله ارسال می‌کند. اگر با این موضوع موافق هستید، کادر تأیید را علامت بزنید.")
                    .setPositiveButton("متوجه شدم", (dialog, which) -> consentCheck.setChecked(true))
                    .setNegativeButton("انصراف", null)
                    .show();
            return;
        }
        if (!saveSettings()) return;

        if (checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED) {
            enableForwardingNow();
            return;
        }

        if (shouldShowRequestPermissionRationale(Manifest.permission.RECEIVE_SMS)) {
            new AlertDialog.Builder(this)
                    .setTitle("چرا دسترسی پیامک لازم است؟")
                    .setMessage("بدون مجوز دریافت پیامک، اندروید رویداد رسیدن پیامک جدید را به برنامه اطلاع نمی‌دهد. برنامه فقط پیامک‌های جدید را دریافت می‌کند و صندوق پیامک‌های قبلی را نمی‌خواند.")
                    .setPositiveButton("ادامه و درخواست مجوز", (dialog, which) ->
                            requestPermissions(new String[]{Manifest.permission.RECEIVE_SMS}, REQUEST_SMS))
                    .setNegativeButton("فعلاً نه", null)
                    .show();
        } else {
            requestPermissions(new String[]{Manifest.permission.RECEIVE_SMS}, REQUEST_SMS);
        }
    }

    private void enableForwardingNow() {
        getSharedPreferences("app_settings", MODE_PRIVATE).edit()
                .putBoolean("forwarding_enabled", true).apply();
        toast("ارسال پیامک‌ها فعال شد.");
        refreshStatus();
    }

    private void disableForwarding() {
        getSharedPreferences("app_settings", MODE_PRIVATE).edit()
                .putBoolean("forwarding_enabled", false).apply();
        WorkManager.getInstance(this).cancelAllWorkByTag(SmsReceiver.WORK_TAG);
        toast("ارسال پیامک‌های جدید خاموش شد.");
        refreshStatus();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_SMS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableForwardingNow();
            } else {
                toast("بدون اجازه پیامک، ارسال خودکار فعال نمی‌شود.");
                refreshStatus();
            }
        }
    }

    private void refreshStatus() {
        boolean granted = checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED;
        SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        boolean enabled = prefs.getBoolean("forwarding_enabled", false);

        permissionStatus.setText(granted
                ? "● مجوز دریافت پیامک: داده شده"
                : "○ مجوز دریافت پیامک: هنوز داده نشده");
        permissionStatus.setTextColor(Color.parseColor(granted ? "#059669" : "#B45309"));

        if (enabled && !granted) {
            prefs.edit().putBoolean("forwarding_enabled", false).apply();
            WorkManager.getInstance(this).cancelAllWorkByTag(SmsReceiver.WORK_TAG);
            enabled = false;
        }
        forwardingStatus.setText(enabled ? "● وضعیت ارسال: روشن" : "○ وضعیت ارسال: خاموش");
        forwardingStatus.setTextColor(Color.parseColor(enabled ? "#059669" : "#64748B"));
        enableButton.setText(enabled ? "ارسال فعال است" : "درخواست دسترسی و فعال‌سازی");
        enableButton.setEnabled(!enabled);
    }

    private void renderHistory() {
        if (historyContainer == null) return;
        historyContainer.removeAllViews();
        JSONArray items = LogStore.read(this);
        if (items.length() == 0) {
            TextView empty = text("هنوز پیامی ارسال نشده است.", 11, "#94A3B8", false);
            empty.setGravity(Gravity.CENTER);
            historyContainer.addView(empty, matchWrap());
            return;
        }

        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            String sender = item.optString("sender", "");
            String body = item.optString("body", "");
            String status = item.optString("status", "");
            long time = item.optLong("time", System.currentTimeMillis());

            LinearLayout bubble = new LinearLayout(this);
            bubble.setOrientation(LinearLayout.VERTICAL);
            bubble.setPadding(dp(11), dp(10), dp(11), dp(10));
            bubble.setBackground(round("#EFF6FF", "#DBEAFE", 13));

            TextView senderView = text(sender.isEmpty() ? "گزارش برنامه" : "فرستنده: " + sender,
                    10, "#1D4ED8", true);
            bubble.addView(senderView);

            TextView bodyView = text(body, 11, "#1E293B", false);
            bodyView.setMaxLines(5);
            bodyView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams bodyParams = matchWrap();
            bodyParams.topMargin = dp(5);
            bubble.addView(bodyView, bodyParams);

            String timeText = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(new Date(time));
            TextView meta = text(status + "  ·  " + timeText, 9,
                    status.startsWith("ارسال") ? "#059669" : "#B45309", true);
            LinearLayout.LayoutParams metaParams = matchWrap();
            metaParams.topMargin = dp(7);
            bubble.addView(meta, metaParams);

            LinearLayout.LayoutParams bubbleParams = matchWrap();
            bubbleParams.bottomMargin = dp(8);
            historyContainer.addView(bubble, bubbleParams);
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (permissionStatus != null) refreshStatus();
        if (historyContainer != null) renderHistory();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
