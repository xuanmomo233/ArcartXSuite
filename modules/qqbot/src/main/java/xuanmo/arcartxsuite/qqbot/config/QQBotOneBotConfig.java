package xuanmo.arcartxsuite.qqbot.config;

public record QQBotOneBotConfig(
    String wsUrl,
    String accessToken,
    int reconnectIntervalSeconds,
    int heartbeatIntervalSeconds,
    String snowLumaDir,
    boolean snowLumaAutoStart
) {}
