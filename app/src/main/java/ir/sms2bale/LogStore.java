package ir.sms2bale;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

public final class LogStore {
    private static final String KEY = "message_history";
    private static final int MAX_ITEMS = 30;

    private LogStore() {}

    public static synchronized void add(Context context, String sender, String body, String status) {
        JSONArray old = read(context);
        JSONArray next = new JSONArray();
        try {
            JSONObject item = new JSONObject();
            item.put("sender", sender == null ? "" : sender);
            item.put("body", body == null ? "" : body);
            item.put("status", status);
            item.put("time", System.currentTimeMillis());
            next.put(item);

            for (int i = 0; i < old.length() && next.length() < MAX_ITEMS; i++) {
                next.put(old.getJSONObject(i));
            }
            SecureStore.put(context, KEY, next.toString());
        } catch (Exception ignored) {}
    }

    public static synchronized JSONArray read(Context context) {
        try {
            String value = SecureStore.get(context, KEY);
            return value == null ? new JSONArray() : new JSONArray(value);
        } catch (Exception e) {
            return new JSONArray();
        }
    }
}
