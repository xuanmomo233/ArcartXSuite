package xuanmo.arcartxsuite.api.crossserver;

/**
 * 经宿主跨服层校验、去重后投递给模块的消息。
 *
 * @param moduleId  模块 id（如 {@code tab}、{@code chat}）
 * @param nodeId    来源子服节点 id
 * @param messageId 信封 message-id（UUID）
 * @param payload   模块自定义 payload 字符串
 * @param signed    本条消息是否经过 HMAC 签名校验（宿主 {@code cross-server.signature.enabled=true}
 *                  且验签通过时为 {@code true}；签名关闭时为 {@code false}，模块可据此
 *                  实现"严格模式"丢弃未签名写操作）
 */
public record CrossServerDelivery(
    String moduleId,
    String nodeId,
    String messageId,
    String payload,
    boolean signed
) {
}
