package xuanmo.arcartxsuite.onlinerewards.config;

public record OnlineRewardsRedisConfiguration(
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
