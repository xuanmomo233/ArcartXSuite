package xuanmo.arcartxsuite.chat.model;

import java.util.List;

/**
 * 聊天消息信封，封装一条完整聊天消息的全部上下文信息。
 * <p>
 * 用于跨服传输与本地消息分发，包含发送者、接收者、频道、渲染后文本、
 * 物品预览、提及列表等字段。{@link #dedupeKey} 用于跨服去重。
 *
 * @param messageId          消息唯一 ID
 * @param originNodeId       消息来源节点 ID
 * @param serverId           消息来源服务器 ID
 * @param channelId          频道 ID
 * @param senderUuid         发送者 UUID
 * @param senderName         发送者名称
 * @param senderDisplayName  发送者显示名称
 * @param targetUuid         私聊目标 UUID（非私聊为空）
 * @param targetName         私聊目标名称（非私聊为空）
 * @param renderedText       渲染后的常规消息文本
 * @param renderedTargetText 渲染后的私聊目标视角文本
 * @param renderedSpyText    渲染后的监听视角文本
 * @param consoleText        控制台输出文本
 * @param privateMessage     是否为私聊消息
 * @param staffMessage       是否为管理员频道消息
 * @param mentionAll         是否触发了 @all 全员提及
 * @param mentionedNames     被提及的玩家名称列表
 * @param itemPreview        物品预览信息，无物品时为 {@code null}
 * @param rawMessage         原始消息文本（未经格式化）
 */
public record ChatEnvelope(
    String messageId,
    String originNodeId,
    String serverId,
    String channelId,
    String senderUuid,
    String senderName,
    String senderDisplayName,
    String targetUuid,
    String targetName,
    String renderedText,
    String renderedTargetText,
    String renderedSpyText,
    String consoleText,
    boolean privateMessage,
    boolean staffMessage,
    boolean mentionAll,
    List<String> mentionedNames,
    ChatItemPreview itemPreview,
    String rawMessage,
    String audienceKey
) {
    /**
     * 生成跨服去重键，由来源节点 ID 与消息 ID 拼接而成。
     *
     * @return 去重键字符串
     */
    public String dedupeKey() {
        return originNodeId + ":" + messageId;
    }
}
