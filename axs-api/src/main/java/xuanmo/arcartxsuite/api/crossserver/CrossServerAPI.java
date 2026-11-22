package xuanmo.arcartxsuite.api.crossserver;

import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import xuanmo.arcartxsuite.api.bridge.ApiStability;

/**
 * 宿主统一跨服传输 API（Redis + BungeeCord Forward 双后端、统一信封与 HMAC）。
 */
@ApiStability.Stable
public interface CrossServerAPI {

    /** 当前子服节点 id（来自宿主 {@code cross-server.node-id}）。 */
    String nodeId();

    /**
     * 注册模块跨服通道。重复 {@code openChannel} 同 moduleId 会先关闭旧通道。
     *
     * @param moduleId 模块 id，写入信封 {@code module} 字段
     * @param config   模块级开关
     * @param consumer 入站消息回调（已在主线程调度）
     */
    CrossServerChannel openChannel(
        String moduleId,
        CrossServerChannelConfig config,
        Consumer<CrossServerDelivery> consumer
    );

    /**
     * Like openChannel, but false leaves the message at the head of the transport's inbound batch
     * until the module can accept it. The predicate runs on the server thread and must not block.
     */
    CrossServerChannel openChannelAdmitting(String moduleId, CrossServerChannelConfig config,
                                                    Predicate<CrossServerDelivery> admit);

    /**
     * Keeps Redis and proxy deliveries in the local inbox until the stage completes with true.
     * A false or failed stage leaves the message eligible for retry. Consumers should commit
     * their effects idempotently because a server may stop between the effect and its receipt.
     * Return null without side effects when no module queue slot is available.
     * The admission callback itself runs on the server thread.
     */
    CrossServerChannel openChannelCompleting(String moduleId, CrossServerChannelConfig config,
            Function<CrossServerDelivery, CompletionStage<Boolean>> admit);
}
