package xuanmo.arcartxsuite.api.crossserver;

/**
 * 经宿主跨服层校验、去重后投递给模块的消息。
 *
 * @param moduleId  模块 id（如 {@code tab}、{@code chat}）
 * @param nodeId    来源子服节点 id
 * @param messageId 信封 message-id（UUID）
 * @param payload   模块自定义 payload 字符串
 * @param signed    本条消息是否已通过宿主 HMAC 签名校验，由 {@code cross-server.signature.verify}
 *                  控制入站验签。模块可据此拒绝未验证的写操作。
 * @param timestamp 来源节点发送时间
 */
public record CrossServerDelivery(
    String moduleId,
    String nodeId,
    String messageId,
    String payload,
    boolean signed,
    long timestamp
) {
}
