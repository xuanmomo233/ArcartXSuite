package xuanmo.arcartxsuite.tab.config;

public record TabRedisConfiguration(
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
