package xuanmo.arcartxsuite.tab.config;

/**
 * 客户端翻页：客户端发 {@code Packet.send(packetId, action [, pageIndex])}
 * 即可通知服务端切换 viewer 当前页。
 */
public record TabPaginationConfiguration(
    boolean enabled,
    int pageSize,
    String packetId,
    String nextAction,
    String prevAction,
    String setAction
) {
    public TabPaginationConfiguration {
        pageSize = Math.max(1, pageSize);
        packetId = packetId == null || packetId.isBlank() ? "TAB_PAGE" : packetId.trim();
        nextAction = nextAction == null || nextAction.isBlank() ? "next" : nextAction.trim();
        prevAction = prevAction == null || prevAction.isBlank() ? "prev" : prevAction.trim();
        setAction = setAction == null || setAction.isBlank() ? "set" : setAction.trim();
    }

    public static TabPaginationConfiguration disabled() {
        return new TabPaginationConfiguration(false, 80, "TAB_PAGE", "next", "prev", "set");
    }
}
