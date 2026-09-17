package xuanmo.arcartxsuite.api.capability;

import org.jetbrains.annotations.NotNull;

/**
 * QQ 群消息广播能力接口。
 * <p>
 * 由 QQBot 模块实现，供其他模块跨模块推送消息到 QQ 群。
 * <p>
 * 公开 capability：外部插件可经 {@code AxsCapabilities.get(QQBotBroadcastable.class)} 获取。
 */
@PublicCapability
public interface QQBotBroadcastable {

    /**
     * 向指定 QQ 群发送消息。
     *
     * @param groupId QQ 群号
     * @param message 消息内容
     */
    void sendToGroup(long groupId, @NotNull String message);

    /**
     * 向所有已配置的 QQ 群发送消息。
     *
     * @param message 消息内容
     */
    void sendToAllGroups(@NotNull String message);
}
