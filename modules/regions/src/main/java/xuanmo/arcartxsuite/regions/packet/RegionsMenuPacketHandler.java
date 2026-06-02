package xuanmo.arcartxsuite.regions.packet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import xuanmo.arcartxsuite.api.ClientPacketHandler;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;
import xuanmo.arcartxsuite.regions.model.Region;
import xuanmo.arcartxsuite.regions.model.RegionFlag;
import xuanmo.arcartxsuite.regions.service.RegionManager;

/**
 * Regions 玩家区域查看菜单的 Packet Handler。
 * <p>
 * 处理客户端 UI 回包：navigate / refresh
 */
public final class RegionsMenuPacketHandler implements ClientPacketHandler {

    public static final String PACKET_ID = "AXS_REGIONS_MENU";
    public static final String UI_RESOURCE_PATH = "arcartx/ui/regions_menu.yml";
    public static final String UI_FILE_PATH = "ui/regions_menu.yml";

    private final JavaPlugin plugin;
    private final PacketBridgeAPI packetBridge;
    private final PacketGuardAPI packetGuard;
    private final RegionManager regionManager;
    private final String uiId;

    public RegionsMenuPacketHandler(JavaPlugin plugin, PacketBridgeAPI packetBridge,
                                    PacketGuardAPI packetGuard, RegionManager regionManager,
                                    String uiId) {
        this.plugin = plugin;
        this.packetBridge = packetBridge;
        this.packetGuard = packetGuard;
        this.regionManager = regionManager;
        this.uiId = uiId;
    }

    @Override
    public boolean handleClientPacket(@NotNull Player player, @NotNull String packetId, @NotNull List<String> data) {
        if (!PACKET_ID.equalsIgnoreCase(packetId)) return false;
        if (packetGuard != null && !packetGuard.allow(player, "regions", "menu", false)) return true;

        String action = data.isEmpty() ? "refresh" : safe(data.get(0)).toLowerCase(Locale.ROOT);
        switch (action) {
            case "navigate" -> pushData(player, hasValue(data, 1) ? data.get(1) : "current");
            case "refresh" -> pushData(player, hasValue(data, 1) ? data.get(1) : "current");
            case "select_region" -> {
                if (hasValue(data, 1)) {
                    pushRegionDetail(player, data.get(1));
                }
            }
            default -> pushData(player, "current");
        }
        return true;
    }

    public void openMenu(Player player) {
        if (packetBridge == null) return;
        packetBridge.openUi(player, uiId);
        pushData(player, "current");
    }

    public void pushData(Player player, String page) {
        if (packetBridge == null) return;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("page", page);
        payload.put("packetId", PACKET_ID);

        // 当前所在区域
        Location loc = player.getLocation();
        Region current = regionManager.getHighestPriorityRegion(
            loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        if (current != null) {
            payload.put("currentRegion", current.id());
            payload.put("currentWorld", current.world());
            payload.put("currentPriority", String.valueOf(current.priority()));
            payload.put("currentOwners", String.valueOf(current.owners().size()));
            payload.put("currentMembers", String.valueOf(current.members().size()));
            // 当前区域的标志摘要
            List<Map<String, String>> flagSummary = new ArrayList<>();
            for (var entry : current.flags().entrySet()) {
                Map<String, String> f = new HashMap<>();
                f.put("name", entry.getKey().configKey());
                f.put("desc", entry.getKey().description());
                f.put("state", entry.getValue().name().toLowerCase(Locale.ROOT));
                flagSummary.add(f);
            }
            payload.put("currentFlags", flagSummary);
        } else {
            payload.put("currentRegion", "");
            payload.put("currentWorld", loc.getWorld().getName());
            payload.put("currentPriority", "0");
            payload.put("currentOwners", "0");
            payload.put("currentMembers", "0");
            payload.put("currentFlags", List.of());
        }

        // 玩家拥有/参与的区域列表
        List<Map<String, String>> myRegions = new ArrayList<>();
        for (Region region : regionManager.getAllRegions()) {
            if (region.isMember(player.getUniqueId())) {
                Map<String, String> info = new HashMap<>();
                info.put("id", region.id());
                info.put("world", region.world());
                info.put("priority", String.valueOf(region.priority()));
                info.put("isOwner", String.valueOf(region.isOwner(player.getUniqueId())));
                myRegions.add(info);
            }
        }
        payload.put("myRegions", myRegions);
        payload.put("myRegionCount", String.valueOf(myRegions.size()));

        packetBridge.sendPacket(player, uiId, "init", payload);
    }

    private void pushRegionDetail(Player player, String regionId) {
        if (packetBridge == null) return;
        Region region = regionManager.getRegion(regionId);
        if (region == null) {
            pushData(player, "current");
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("page", "detail");
        payload.put("packetId", PACKET_ID);
        payload.put("regionId", region.id());
        payload.put("regionWorld", region.world());
        payload.put("regionPriority", String.valueOf(region.priority()));
        payload.put("regionMin", region.minX() + ", " + region.minY() + ", " + region.minZ());
        payload.put("regionMax", region.maxX() + ", " + region.maxY() + ", " + region.maxZ());
        payload.put("regionVolume", String.valueOf(region.volume()));
        payload.put("regionParent", region.parentId() != null ? region.parentId() : "");

        // 标志列表
        List<Map<String, String>> flags = new ArrayList<>();
        for (var entry : region.flags().entrySet()) {
            Map<String, String> f = new HashMap<>();
            f.put("name", entry.getKey().configKey());
            f.put("desc", entry.getKey().description());
            f.put("state", entry.getValue().name().toLowerCase(Locale.ROOT));
            flags.add(f);
        }
        payload.put("flags", flags);

        // 成员
        List<String> owners = new ArrayList<>();
        for (var uuid : region.owners()) {
            var op = org.bukkit.Bukkit.getOfflinePlayer(uuid);
            owners.add(op.getName() != null ? op.getName() : uuid.toString().substring(0, 8));
        }
        payload.put("owners", owners);
        List<String> members = new ArrayList<>();
        for (var uuid : region.members()) {
            var op = org.bukkit.Bukkit.getOfflinePlayer(uuid);
            members.add(op.getName() != null ? op.getName() : uuid.toString().substring(0, 8));
        }
        payload.put("members", members);

        packetBridge.sendPacket(player, uiId, "init", payload);
    }

    private static boolean hasValue(List<String> data, int index) {
        return data != null && data.size() > index && !safe(data.get(index)).isBlank();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
