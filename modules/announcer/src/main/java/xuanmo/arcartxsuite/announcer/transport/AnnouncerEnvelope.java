package xuanmo.arcartxsuite.announcer.transport;

/**
 * 跨服公告消息载荷。
 *
 * @param messageId  消息唯一 ID（UUID），用于去重
 * @param originNode 发送节点 ID
 * @param text       公告文本（已渲染，不含 PAPI 变量）
 * @param immediate  是否立即广播（broadcastnow）
 */
public record AnnouncerEnvelope(
    String messageId,
    String originNode,
    String text,
    boolean immediate
) {
    public String dedupeKey() {
        return originNode + ":" + messageId;
    }
}
