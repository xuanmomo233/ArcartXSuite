package xuanmo.arcartxsuite.api.crossserver;

import java.util.concurrent.CompletionStage;

/**
 * 模块跨服发布通道句柄。
 */
public interface CrossServerChannel extends AutoCloseable {

    /** 模块 id。 */
    String moduleId();

    /** 当前通道允许收发，且至少一种已启用的后端（Redis / Proxy）可用。 */
    boolean isActive();

    /**
     * 提交跨服消息，由宿主负责封装 {@link CrossServerEnvelope}、签名与发送。
     * 此方法不返回受理结果。Redis 消息使用本地发件箱，Proxy 消息仍为尽力投递。
     *
     * @param payload 模块自定义 payload（JSON / YAML 等）
     */
    void publish(String payload);

    /**
     * 异步提交 Redis 发件箱。true 表示本地事务已提交，Redis 投递会继续进行。
     * false 表示未受理。调用者可据此重试，不能把调用返回当作落盘确认。
     */
    CompletionStage<Boolean> publishDurably(String payload);

    /**
     * 按通道配置发送。Redis 可用时以本地发件箱落盘为准，仅走 Proxy 时以交给在线载体为准。
     */
    CompletionStage<Boolean> publishWithReceipt(String payload);

    /** Proxy forwarding can miss peers without online carriers, so shared-state consumers need catch-up. */
    boolean requiresSharedStateCatchUp();

    @Override
    void close();
}
