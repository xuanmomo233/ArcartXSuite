package xuanmo.arcartxsuite.qqbot.onebot;

import com.google.gson.JsonObject;

public final class OneBotAction {

    private OneBotAction() {}

    public static String sendGroupMsg(long groupId, String message) {
        JsonObject json = new JsonObject();
        json.addProperty("action", "send_group_msg");
        JsonObject params = new JsonObject();
        params.addProperty("group_id", groupId);
        params.addProperty("message", message);
        json.add("params", params);
        json.addProperty("echo", "send_group_msg_" + System.currentTimeMillis());
        return json.toString();
    }

    public static String sendPrivateMsg(long userId, String message) {
        JsonObject json = new JsonObject();
        json.addProperty("action", "send_private_msg");
        JsonObject params = new JsonObject();
        params.addProperty("user_id", userId);
        params.addProperty("message", message);
        json.add("params", params);
        json.addProperty("echo", "send_private_msg_" + System.currentTimeMillis());
        return json.toString();
    }

    public static String getGroupMemberInfo(long groupId, long userId) {
        JsonObject json = new JsonObject();
        json.addProperty("action", "get_group_member_info");
        JsonObject params = new JsonObject();
        params.addProperty("group_id", groupId);
        params.addProperty("user_id", userId);
        params.addProperty("no_cache", false);
        json.add("params", params);
        json.addProperty("echo", "get_member_" + groupId + "_" + userId);
        return json.toString();
    }

    public static String getLoginInfo() {
        JsonObject json = new JsonObject();
        json.addProperty("action", "get_login_info");
        json.add("params", new JsonObject());
        json.addProperty("echo", "get_login_info");
        return json.toString();
    }

    /**
     * 发送带 @ 的群消息（使用 CQ 码）。
     *
     * @param groupId 群号
     * @param atQq    被 @ 的 QQ 号
     * @param message 消息正文
     */
    public static String sendGroupMsgAt(long groupId, long atQq, String message) {
        return sendGroupMsg(groupId, "[CQ:at,qq=" + atQq + "] " + message);
    }

    /**
     * 群组禁言。
     *
     * @param groupId  群号
     * @param userId   被禁言成员 QQ
     * @param duration 禁言时长（秒），0 表示解除禁言
     */
    public static String setGroupBan(long groupId, long userId, long duration) {
        JsonObject json = new JsonObject();
        json.addProperty("action", "set_group_ban");
        JsonObject params = new JsonObject();
        params.addProperty("group_id", groupId);
        params.addProperty("user_id", userId);
        params.addProperty("duration", duration);
        json.add("params", params);
        json.addProperty("echo", "set_group_ban_" + System.currentTimeMillis());
        return json.toString();
    }
}
