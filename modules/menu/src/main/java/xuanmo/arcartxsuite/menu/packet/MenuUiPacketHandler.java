package xuanmo.arcartxsuite.menu.packet;

import java.util.List;
import java.util.Locale;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.menu.service.MenuService;

public final class MenuUiPacketHandler {

    private final MenuService service;
    private final String packetId;

    public MenuUiPacketHandler(MenuService service, String packetId) {
        this.service = service;
        this.packetId = packetId;
    }

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
