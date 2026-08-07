package xuanmo.arcartxsuite.qqbot.config;

/**
 * QQBot 模块的 OneBot 协议连接配置 record。
 * <p>
 * 关联模块：{@code qqbot}。定义 WebSocket 地址、鉴权令牌、
 * 重连与心跳间隔，以及内嵌 SnowLuma 实例的子配置。
 */
public record QQBotOneBotConfig(
    /** OneBot 实现（如 SnowLuma）的 WebSocket 服务地址 */
    String wsUrl,
    /** 访问令牌，用于 WebSocket 鉴权，为空表示不鉴权 */
    String accessToken,
    /** 连接断开后的重连间隔（秒） */
    int reconnectIntervalSeconds,
    /** 心跳发送间隔（秒），{@code <=0} 表示不发送心跳 */
    int heartbeatIntervalSeconds,
    /** SnowLuma 内嵌实例配置 */
    QQBotSnowLumaConfig snowluma
) {}
