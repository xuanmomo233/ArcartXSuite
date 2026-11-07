package xuanmo.arcartxsuite.qqbot.onebot;

import com.google.gson.JsonObject;

/** Acknowledged result. Negative codes are local failures; -2 means delivery is unknown. */
public record OneBotResult(boolean success, int retcode, String message, JsonObject data) {
    public static OneBotResult success(JsonObject data) {
        return new OneBotResult(true, 0, "发送成功", data == null ? new JsonObject() : data.deepCopy());
    }
    public static OneBotResult failure(int code, String message) {
        return new OneBotResult(false, code, message, new JsonObject());
    }
}
