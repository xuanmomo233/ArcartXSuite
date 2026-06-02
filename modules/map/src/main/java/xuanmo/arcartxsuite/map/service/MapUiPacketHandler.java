package xuanmo.arcartxsuite.map.service;

import java.util.List;
import java.util.Locale;
import org.bukkit.entity.Player;

public final class MapUiPacketHandler {

    private final ActionTarget target;
    private final String packetId;

    public MapUiPacketHandler(ActionTarget target, String packetId) {
        this.target = target;
        this.packetId = packetId;
    }

    public boolean handleClientPacket(Player player, String packetId, List<String> data) {
        if (player == null || !player.isOnline() || packetId == null || !this.packetId.equalsIgnoreCase(packetId)) {
            return false;
        }
        String action = data == null || data.isEmpty() ? "refresh" : safe(data.get(0)).toLowerCase(Locale.ROOT);
        if (!target.allowClientPacket(player, action)) {
            return true;
        }
        switch (action) {
            case "refresh" -> target.refresh(player);
            case "open_world" -> {
                if (hasValue(data, 1)) {
                    target.openWorld(player, data.get(1));
                } else {
                    target.refresh(player);
                }
            }
            case "select_anchor" -> {
                if (hasValue(data, 1)) {
                    target.selectAnchor(player, data.get(1));
                } else {
                    target.refresh(player);
                }
            }
            case "select_waypoint" -> {
                if (hasValue(data, 1)) {
                    target.selectWaypoint(player, data.get(1));
                } else {
                    target.refresh(player);
                }
            }
            case "select_external" -> {
                if (hasValue(data, 1)) {
                    target.selectExternalTarget(player, data.get(1));
                } else {
                    target.refresh(player);
                }
            }
            case "unlock_anchor" -> {
                if (hasValue(data, 1)) {
                    target.unlockAnchor(player, data.get(1));
                } else {
                    target.refresh(player);
                }
            }
            case "teleport_anchor" -> {
                if (hasValue(data, 1)) {
                    target.teleportAnchor(player, data.get(1));
                } else {
                    target.refresh(player);
                }
            }
            case "track_anchor" -> {
                if (hasValue(data, 1)) {
                    target.trackAnchor(player, data.get(1));
                } else {
                    target.refresh(player);
                }
            }
            case "track_waypoint" -> {
                if (hasValue(data, 1)) {
                    target.trackWaypoint(player, data.get(1));
                } else {
                    target.refresh(player);
                }
            }
            case "track_external" -> {
                if (hasValue(data, 1)) {
                    target.trackExternalTarget(player, data.get(1));
                } else {
                    target.refresh(player);
                }
            }
            case "clear_track" -> target.clearTrack(player);
            case "create_waypoint" -> target.createWaypoint(player);
            case "delete_waypoint" -> {
                if (hasValue(data, 1)) {
                    target.deleteWaypoint(player, data.get(1));
                } else {
                    target.refresh(player);
                }
            }
            case "open_menu" -> target.openMenu(player, hasValue(data, 1) ? data.get(1) : "");
            default -> target.refresh(player);
        }
        return true;
    }

    private static boolean hasValue(List<String> data, int index) {
        return data != null && data.size() > index && !safe(data.get(index)).isBlank();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public interface ActionTarget {
        boolean allowClientPacket(Player player, String action);

        void refresh(Player player);

        void openMenu(Player player, String worldId);

        void openWorld(Player player, String worldId);

        void selectAnchor(Player player, String anchorId);

        void selectWaypoint(Player player, String waypointId);

        void selectExternalTarget(Player player, String targetId);

        void unlockAnchor(Player player, String anchorId);

        void teleportAnchor(Player player, String anchorId);

        void trackAnchor(Player player, String anchorId);

        void trackWaypoint(Player player, String waypointId);

        void trackExternalTarget(Player player, String targetId);

        void clearTrack(Player player);

        void createWaypoint(Player player);

        void deleteWaypoint(Player player, String waypointId);
    }
}
