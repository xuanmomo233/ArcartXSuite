package xuanmo.arcartxsuite.qqbot.config;

public record QQBotOneBotConfig(
    String wsUrl,
    String accessToken,
    int reconnectIntervalSeconds,
    int heartbeatIntervalSeconds,
    QQBotSnowLumaConfig snowluma
) {}
