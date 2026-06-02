package xuanmo.arcartxsuite.announcer.config;

public record AnnouncerProxyConfiguration(
    boolean enabled,
    String messengerChannel,
    String forwardTarget,
    String nodeId
) {
    public static final AnnouncerProxyConfiguration DISABLED =
        new AnnouncerProxyConfiguration(false, "AXS_ANNOUNCER", "ALL", "");
}
