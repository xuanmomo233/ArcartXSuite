package xuanmo.arcartxsuite.questgps.service;

import java.util.List;
import java.util.Locale;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.questgps.QuestGpsCategory;
import xuanmo.arcartxsuite.questgps.QuestGpsPage;

final class QuestGpsUiPacketHandler {

    private final QuestGpsService service;
    private final String packetId;

    QuestGpsUiPacketHandler(QuestGpsService service, String packetId) {
        this.service = service;
        this.packetId = packetId;
    }

    boolean handleClientPacket(Player player, String packetId, List<String> data) {
        if (player == null || !player.isOnline() || packetId == null || !this.packetId.equalsIgnoreCase(packetId)) {
            return false;
        }
        String action = data == null || data.isEmpty() ? "refresh" : safe(data.get(0)).toLowerCase(Locale.ROOT);
        if (!service.allowClientPacket(player, action)) {
            return true;
        }
        switch (action) {
            case "refresh" -> service.refreshMenu(player);
            case "switch_category" -> service.switchCategory(player, QuestGpsCategory.parse(data.size() > 1 ? data.get(1) : "", service.categoryRegistry()));
            case "switch_page" -> service.switchPage(player, QuestGpsPage.parse(data.size() > 1 ? data.get(1) : ""));
            case "select_quest" -> service.selectQuest(player, data.size() > 1 ? data.get(1) : "");
            case "accept_quest" -> service.acceptQuest(player, data.size() > 1 ? data.get(1) : "");
            case "abandon_quest" -> service.abandonQuest(player, data.size() > 1 ? data.get(1) : "");
            case "track_quest" -> service.trackQuest(player, data.size() > 1 ? data.get(1) : "");
            case "track_task" -> service.trackTask(
                player,
                data.size() > 1 ? data.get(1) : "",
                data.size() > 2 ? data.get(2) : ""
            );
            case "clear_track" -> service.clearTrack(player);
            case "open_map" -> {
                xuanmo.arcartxsuite.api.capability.MapNavigable map = service.mapNavigableProvider().get();
                if (map != null) {
                    map.openMenuFor(player, "");
                } else {
                    service.refreshMenu(player);
                }
            }
            default -> service.refreshMenu(player);
        }
        return true;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
