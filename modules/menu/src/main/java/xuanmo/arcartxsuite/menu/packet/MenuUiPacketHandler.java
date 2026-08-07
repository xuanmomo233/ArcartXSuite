package xuanmo.arcartxsuite.menu.packet;

import java.util.List;
import java.util.Locale;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.menu.service.MenuService;

/**
 * 菜单 UI 数据包处理器，接收客户端发来的菜单交互数据包并分发到对应的服务端逻辑。
 * <p>
 * 支持的动作包括刷新菜单、打开 ESC 菜单、关闭菜单、翻页、按钮点击和底栏按钮点击。
 */
public final class MenuUiPacketHandler {

    private final MenuService service;
    private final String packetId;

    public MenuUiPacketHandler(MenuService service, String packetId) {
        this.service = service;
        this.packetId = packetId;
    }

    /**
     * 处理客户端发来的菜单数据包，根据动作类型分发到对应的服务端处理逻辑。
     *
     * @param player           发送数据包的玩家
     * @param incomingPacketId 数据包 ID
     * @param data             数据包内容列表
     * @return {@code true} 表示数据包已被处理
     */
    public boolean handleClientPacket(Player player, String incomingPacketId, List<String> data) {
        if (player == null || !player.isOnline() || incomingPacketId == null) {
            return false;
        }
        if (!packetId.equalsIgnoreCase(incomingPacketId)) {
            return false;
        }
        String action = data == null || data.isEmpty() ? "refresh" : safe(data.get(0)).toLowerCase(Locale.ROOT);
        if (!service.allowClientPacket(player, action)) {
            return true;
        }
        switch (action) {
            case "refresh" -> service.refreshMenu(player);
            case "esc_open" -> service.openEscMenu(player);
            case "close" -> service.closeMenu(player);
            case "page" -> {
                String pageToken = data.size() > 1 ? data.get(1) : "";
                service.changePage(player, pageToken);
            }
            case "click" -> {
                String buttonId = data.size() > 1 ? data.get(1) : "";
                service.handleButtonClick(player, buttonId, false);
            }
            case "footer" -> {
                String buttonId = data.size() > 1 ? data.get(1) : "";
                service.handleButtonClick(player, buttonId, true);
            }
            default -> service.refreshMenu(player);
        }
        return true;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
