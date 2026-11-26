package ir.sms2bale;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.HttpsURLConnection;

public final class BaleApi {
    private BaleApi() {}

    public static final class ApiResult {
        public final boolean success;
        public final boolean retryable;
        public final String description;

        ApiResult(boolean success, boolean retryable, String description) {
            this.success = success;
            this.retryable = retryable;
            this.description = description;
        }
    }

    public static ApiResult sendMessage(String token, String chatId, String text) throws IOException {
        if (token == null || token.trim().isEmpty() ||
                token.contains("/") || token.contains("?") || token.contains("#") ||
                token.contains(" ")) {
            return new ApiResult(false, false, "توکن ربات معتبر نیست.");
        }
        if (chatId == null || chatId.trim().isEmpty()) {
            return new ApiResult(false, false, "شناسه گفتگو وارد نشده است.");
        }

        HttpsURLConnection connection = null;
        try {
            URL url = new URL("https://tapi.bale.ai/bot" + token.trim() + "/sendMessage");
            connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");

            JSONObject payload = new JSONObject();
            try {
                payload.put("chat_id", Long.parseLong(chatId.trim()));
            } catch (NumberFormatException e) {
                payload.put("chat_id", chatId.trim());
            }
            payload.put("text", text);

            byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
                output.flush();
            }

            int statusCode = connection.getResponseCode();
            InputStream input = statusCode >= 200 && statusCode < 400
                    ? connection.getInputStream() : connection.getErrorStream();
            String responseText = readFully(input);
            JSONObject response;
            try {
                response = new JSONObject(responseText);
            } catch (Exception e) {
                boolean retry = statusCode == 429 || statusCode >= 500;
                return new ApiResult(false, retry, "پاسخ نامعتبر از سرور بله (HTTP " + statusCode + ").");
            }

            boolean ok = response.optBoolean("ok", false);
            if (statusCode >= 200 && statusCode < 300 && ok) {
                return new ApiResult(true, false, "پیام با موفقیت ارسال شد.");
            }

            int errorCode = response.optInt("error_code", statusCode);
            String description = response.optString("description", "ارسال پیام ناموفق بود.");
            boolean retry = statusCode == 429 || statusCode >= 500 || errorCode >= 500;
            return new ApiResult(false, retry, description);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String readFully(InputStream input) throws IOException {
        if (input == null) return "";
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            return out.toString("UTF-8");
        }
    }
}
