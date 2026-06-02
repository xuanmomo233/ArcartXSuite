package xuanmo.arcartxsuite.chat.config;

public record ChatRedisConfiguration(
    boolean enabled,
    String host,
    int port,
    String password,
    int database,
    String channel,
    String nodeId,
    int connectTimeoutMs
) {
}
