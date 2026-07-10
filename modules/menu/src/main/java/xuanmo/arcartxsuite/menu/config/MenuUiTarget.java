package xuanmo.arcartxsuite.menu.config;

/**
 * 单个 UI 发包目标：指定往哪个 UI 用哪个 handler 发包。
 * <p>
 * 与 tab 模块的 {@code xuanmo.arcartxsuite.tab.config.UiTarget} 语义一致，
 * 支持简写 {@code ui-id + packet-handler} 或列表 {@code ui-targets}。
 */
public record MenuUiTarget(String uiId, String packetHandler) {

    public MenuUiTarget {
        if (uiId == null || uiId.isBlank()) {
            throw new IllegalArgumentException("MenuUiTarget.uiId must not be blank");
        }
        if (packetHandler == null || packetHandler.isBlank()) {
            throw new IllegalArgumentException("MenuUiTarget.packetHandler must not be blank");
        }
        uiId = uiId.trim();
        packetHandler = packetHandler.trim();
    }

    @Override
    public String toString() {
        return uiId + ":" + packetHandler;
    }
}
