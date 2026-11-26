package ir.sms2bale;

import android.content.Context;
import org.json.JSONObject;
import java.util.UUID;

public final class PendingSmsStore {
    private PendingSmsStore() {}

    public static String save(Context context, String sender, String body, long receivedAt) {
        String id = UUID.randomUUID().toString();
        try {
            JSONObject json = new JSONObject();
            json.put("sender", sender == null ? "نامشخص" : sender);
            json.put("body", body == null ? "" : body);
            json.put("receivedAt", receivedAt);
            if (!SecureStore.put(context, "pending_" + id, json.toString())) return null;
            return id;
        } catch (Exception e) {
            return null;
        }
    }

    public static JSONObject get(Context context, String id) {
        try {
            String value = SecureStore.get(context, "pending_" + id);
            return value == null ? null : new JSONObject(value);
        } catch (Exception e) {
            return null;
        }
    }

    public static void remove(Context context, String id) {
        SecureStore.remove(context, "pending_" + id);
    }
}
