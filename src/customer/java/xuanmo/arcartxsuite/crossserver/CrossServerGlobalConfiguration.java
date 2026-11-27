package xuanmo.arcartxsuite.crossserver;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * 宿主 {@code config.yml} 中 {@code cross-server} 节。
 */
public record CrossServerGlobalConfiguration(
    String nodeId,
    boolean redisEnabled,
    String redisHost,
    int redisPort,
    String redisUsername,
    String redisPassword,
    int redisDatabase,
    String redisChannel,
    int redisConnectTimeoutMs,
    long redisStreamMaxBytes,
    long redisStreamReaderLeaseMs,
    long redisOutboxMaxBytes,
    int redisOutboxReplicaAcks,
    boolean proxyEnabled,
    String proxyMessengerChannel,
    String proxyForwardTarget,
    boolean signatureEnabled,
    String signatureSecret,
    boolean signatureVerify,
    long dedupeTtlMs,
    int maxPayloadChars
) {

    public static final int DEFAULT_MAX_PAYLOAD_CHARS = 512 * 1024;
    private static final long DEFAULT_REDIS_STREAM_BYTES = 256L * 1024 * 1024;
    static final long MAX_FUTURE_SKEW_MS = 5_000L;
    static final int MAX_ENVELOPE_FIELD_LENGTH = 128;

    public static CrossServerGlobalConfiguration from(FileConfiguration configuration) {
        var section = configuration.getConfigurationSection("cross-server");
        if (section == null) {
            return disabled();
        }
        String nodeId = nullToEmpty(section.getString("node-id", "default")).trim();
        if (nodeId.isBlank()) {
            nodeId = "default";
        }
        var redis = section.getConfigurationSection("redis");
        var proxy = section.getConfigurationSection("proxy");
        var signature = section.getConfigurationSection("signature");
        boolean sigEnabled = signature != null && signature.getBoolean("enabled", false);
        return new CrossServerGlobalConfiguration(
            nodeId,
            redis != null && redis.getBoolean("enabled", false),
            redis == null ? "127.0.0.1" : nullToEmpty(redis.getString("host", "127.0.0.1")),
            redis == null ? 6379 : Math.max(1, redis.getInt("port", 6379)),
            redis == null ? "" : nullToEmpty(redis.getString("username", "")).trim(),
            redis == null ? "" : nullToEmpty(redis.getString("password", "")),
            redis == null ? 0 : Math.max(0, redis.getInt("database", 0)),
            redis == null ? "AXS:CROSS" : nullToEmpty(redis.getString("channel", "AXS:CROSS")),
            redis == null ? 5000 : Math.max(1000, redis.getInt("connect-timeout-ms", 5000)),
            redis == null ? DEFAULT_REDIS_STREAM_BYTES : Math.min(2L * 1024 * 1024 * 1024,
                Math.max(8L * 1024 * 1024, redis.getLong("stream-max-bytes", DEFAULT_REDIS_STREAM_BYTES))),
            redis == null ? 120_000L : Math.min(86_400_000L,
                Math.max(30_000L, redis.getLong("stream-reader-lease-ms", 120_000L))),
            redis == null ? DEFAULT_REDIS_STREAM_BYTES : Math.min(2L * 1024 * 1024 * 1024,
                Math.max(8L * 1024 * 1024, redis.getLong("outbox-max-bytes", DEFAULT_REDIS_STREAM_BYTES))),
            redis == null ? 0 : Math.max(0, Math.min(16, redis.getInt("outbox-replica-acks", 0))),
            proxy != null && proxy.getBoolean("enabled", false),
            proxy == null ? "AXS_CROSS" : nullToEmpty(proxy.getString("messenger-channel", "AXS_CROSS")),
            proxy == null ? "ALL" : nullToEmpty(proxy.getString("forward-target", "ALL")),
            sigEnabled,
            signature == null ? "" : nullToEmpty(signature.getString("secret", "")),
            signature != null && signature.getBoolean("verify", sigEnabled),
            Math.max(1000L, section.getLong("dedupe-ttl-ms", 60_000L)),
            Math.max(1024, section.getInt("max-payload-chars", DEFAULT_MAX_PAYLOAD_CHARS))
        );
    }

    public static CrossServerGlobalConfiguration disabled() {
        return new CrossServerGlobalConfiguration(
            "default", false, "127.0.0.1", 6379, "", "", 0, "AXS:CROSS", 5000, DEFAULT_REDIS_STREAM_BYTES, 120_000L,
            DEFAULT_REDIS_STREAM_BYTES, 0,
            false, "AXS_CROSS", "ALL", false, "", false, 60_000L, DEFAULT_MAX_PAYLOAD_CHARS
        );
    }

    public boolean shouldSign() {
        return signatureEnabled && hasSignatureSecret();
    }

    public boolean shouldVerify() {
        return signatureVerify && hasSignatureSecret();
    }

    boolean missingSignatureSecret() {
        return (signatureEnabled || signatureVerify) && !hasSignatureSecret();
    }

    boolean acceptsTimestamp(long timestamp, long now) {
        return timestamp > 0 && timestamp >= now - dedupeTtlMs
            && timestamp <= now + MAX_FUTURE_SKEW_MS;
    }

    static boolean validEnvelopeField(String value) {
        return value != null && !value.isBlank() && value.length() <= MAX_ENVELOPE_FIELD_LENGTH
            && value.chars().noneMatch(Character::isISOControl);
    }

    private boolean hasSignatureSecret() {
        return signatureSecret != null && !signatureSecret.isBlank();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
