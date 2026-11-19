package xuanmo.arcartxsuite.qqbot.onebot;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;
import org.jetbrains.annotations.Nullable;

public final class OneBotEvent {

    private final JsonObject raw;

    public OneBotEvent(JsonObject raw) {
        this.raw = raw.deepCopy();
    }

    public JsonObject raw() {
        return raw.deepCopy();
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

    public boolean isOwnMessage() {
        long self = getLong("self_id", 0L);
        return self > 0L && userId() == self;
    }

    public int messageId() {
        return getInt("message_id", 0);
    }

    public String rawMessage() {
        JsonElement message = raw.get("message");
        if (message == null || message.isJsonNull()) message = raw.get("raw_message");
        return OneBotMessageCodec.text(message);
    }

    /** 从消息段数组中提取被 @ 的 QQ 号列表 */
    public List<Long> atQQs() {
        JsonElement message = raw.get("message");
        if (message == null || message.isJsonNull()) message = raw.get("raw_message");
        return OneBotMessageCodec.mentions(message);
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
