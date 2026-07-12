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
        uiId = uiId.trim();
        packetHandler = (packetHandler == null || packetHandler.isBlank()) ? null : packetHandler.trim();
    }

    /**
     * 是否指定了自定义 handler 名称。
     * <p>
     * 未指定时，菜单按内置的 {@code init}/{@code update} 生命周期驱动该 UI，
     * 这样自定义 UI 可直接复用 menu_panel/menu_esc 的 packetHandler 写法。
     */
    public boolean hasPacketHandler() {
        return packetHandler != null && !packetHandler.isBlank();
    }

    @Override
    public String toString() {
        return uiId + ":" + (packetHandler == null ? "<init/update>" : packetHandler);
    }
}
