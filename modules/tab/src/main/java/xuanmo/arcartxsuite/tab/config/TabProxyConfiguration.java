package xuanmo.arcartxsuite.tab.config;

public record TabProxyConfiguration(
    boolean enabled,
    String messengerChannel,
    String forwardTarget,
    String nodeId
) {
}
