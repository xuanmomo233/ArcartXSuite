package xuanmo.arcartxsuite.mail.config;

public record MailRedisConfiguration(
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
