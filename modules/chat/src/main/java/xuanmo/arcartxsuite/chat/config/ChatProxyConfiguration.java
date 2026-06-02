package xuanmo.arcartxsuite.chat.config;

public record ChatProxyConfiguration(
    boolean enabled,
    String messengerChannel,
    String forwardTarget,
    String nodeId
) {
}
