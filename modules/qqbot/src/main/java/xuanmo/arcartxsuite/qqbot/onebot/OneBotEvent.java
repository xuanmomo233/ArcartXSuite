package xuanmo.arcartxsuite.qqbot.onebot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

public final class OneBotEvent {

    private final JsonObject raw;

    public OneBotEvent(JsonObject raw) {
        this.raw = raw;
    }

    public JsonObject raw() {
        return raw;
    }

    public String postType() {
        return getString("post_type", "");
    }

    public boolean isMessage() {
        return "message".equals(postType());
    }

    public boolean isGroupMessage() {
        return isMessage() && "group".equals(getString("message_type", ""));
    }

    public boolean isMetaEvent() {
        return "meta_event".equals(postType());
    }

    public boolean isNotice() {
        return "notice".equals(postType());
    }

    /** notice 事件子类型，如 group_increase / group_decrease / group_ban */
    public String noticeType() {
        return getString("notice_type", "");
    }

    /** 群成员增加事件 */
    public boolean isGroupMemberIncrease() {
        return isNotice() && "group_increase".equals(noticeType());
    }

    /** 群成员减少事件 */
    public boolean isGroupMemberDecrease() {
        return isNotice() && "group_decrease".equals(noticeType());
    }

    /** 群禁言事件 */
    public boolean isGroupBan() {
        return isNotice() && "group_ban".equals(noticeType());
    }

    /** 事件子类型（如禁言的 ban / lift_ban） */
    public String subType() {
        return getString("sub_type", "");
    }

    /** 操作者 QQ（如禁言操作的管理员） */
    public long operatorId() {
        return getLong("operator_id", 0L);
    }

    /** 禁言时长（秒），lift_ban 时为 0 */
    public long banDuration() {
        return getLong("duration", 0L);
    }

    public boolean isHeartbeat() {
        return isMetaEvent() && "heartbeat".equals(getString("meta_event_type", ""));
    }

    public boolean isLifecycle() {
        return isMetaEvent() && "lifecycle".equals(getString("meta_event_type", ""));
    }

    public long groupId() {
        return getLong("group_id", 0L);
    }

    public long userId() {
        return getLong("user_id", 0L);
    }

    public int messageId() {
        return getInt("message_id", 0);
    }

    public String rawMessage() {
        // 优先用 raw_message（纯文本），回退到 message
        String raw = getString("raw_message", null);
        if (raw != null) return raw;
        // message 可能是字符串或消息段数组
        JsonElement msgEl = this.raw.get("message");
        if (msgEl == null || msgEl.isJsonNull()) return "";
        if (msgEl.isJsonPrimitive()) return msgEl.getAsString();
        if (msgEl.isJsonArray()) return extractTextFromSegments(msgEl.getAsJsonArray());
        return "";
    }

    /**
     * 从 OneBot 消息段数组中提取纯文本。
     * 格式: [{"type":"text","data":{"text":"hello"}}, {"type":"image",...}]
     */
    private static String extractTextFromSegments(JsonArray segments) {
        StringBuilder sb = new StringBuilder();
        for (JsonElement seg : segments) {
            if (!seg.isJsonObject()) continue;
            JsonObject obj = seg.getAsJsonObject();
            String type = obj.has("type") ? obj.get("type").getAsString() : "";
            JsonObject data = obj.has("data") && obj.get("data").isJsonObject()
                ? obj.getAsJsonObject("data") : null;
            if ("text".equals(type) && data != null && data.has("text")) {
                sb.append(data.get("text").getAsString());
            } else if ("at".equals(type) && data != null && data.has("qq")) {
                sb.append("@").append(data.get("qq").getAsString());
            } else if ("face".equals(type)) {
                sb.append("[表情]");
            } else if ("image".equals(type)) {
                sb.append("[图片]");
            } else if ("reply".equals(type)) {
                // 忽略回复引用
            } else if (!"text".equals(type)) {
                sb.append("[").append(type).append("]");
            }
        }
        return sb.toString();
    }

    /** 从消息段数组中提取被 @ 的 QQ 号列表 */
    public List<Long> atQQs() {
        List<Long> list = new ArrayList<>();
        JsonElement msgEl = raw.get("message");
        if (msgEl == null || !msgEl.isJsonArray()) return list;
        JsonArray segments = msgEl.getAsJsonArray();
        for (JsonElement seg : segments) {
            if (!seg.isJsonObject()) continue;
            JsonObject obj = seg.getAsJsonObject();
            if (!"at".equals(obj.has("type") ? obj.get("type").getAsString() : "")) continue;
            JsonObject data = obj.has("data") && obj.get("data").isJsonObject()
                ? obj.getAsJsonObject("data") : null;
            if (data != null && data.has("qq")) {
                try {
                    String qqStr = data.get("qq").getAsString();
                    if (!"all".equals(qqStr)) {
                        list.add(Long.parseLong(qqStr));
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        return list;
    }

    @Nullable
    public String senderNickname() {
        JsonElement sender = raw.get("sender");
        if (sender != null && sender.isJsonObject()) {
            JsonElement nick = sender.getAsJsonObject().get("nickname");
            if (nick != null && !nick.isJsonNull()) return nick.getAsString();
            JsonElement card = sender.getAsJsonObject().get("card");
            if (card != null && !card.isJsonNull() && !card.getAsString().isEmpty()) {
                return card.getAsString();
            }
        }
        return null;
    }

    @Nullable
    public String senderCard() {
        JsonElement sender = raw.get("sender");
        if (sender != null && sender.isJsonObject()) {
            JsonElement card = sender.getAsJsonObject().get("card");
            if (card != null && !card.isJsonNull() && !card.getAsString().isEmpty()) {
                return card.getAsString();
            }
        }
        return null;
    }

    public String senderRole() {
        JsonElement sender = raw.get("sender");
        if (sender != null && sender.isJsonObject()) {
            JsonElement role = sender.getAsJsonObject().get("role");
            if (role != null && !role.isJsonNull()) return role.getAsString();
        }
        return "member";
    }

    public boolean isSenderAdmin() {
        String role = senderRole();
        return "admin".equals(role) || "owner".equals(role);
    }

    private String getString(String key, String defaultValue) {
        JsonElement el = raw.get(key);
        if (el == null || el.isJsonNull()) return defaultValue;
        if (!el.isJsonPrimitive()) return defaultValue;
        return el.getAsString();
    }

    private long getLong(String key, long defaultValue) {
        JsonElement el = raw.get(key);
        if (el == null || el.isJsonNull()) return defaultValue;
        try {
            return el.getAsLong();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private int getInt(String key, int defaultValue) {
        JsonElement el = raw.get(key);
        if (el == null || el.isJsonNull()) return defaultValue;
        try {
            return el.getAsInt();
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
