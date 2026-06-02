package xuanmo.arcartxsuite.regions.packet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
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
 * Regions 管理员区域面板的 Packet Handler。
 * <p>
 * 处理客户端 UI 回包：navigate / select_region / set_flag / remove_flag /
 * add_member / remove_member / set_priority / set_parent / delete_region /
 * select_world_rule / set_world_rule
 */
public final class RegionsAdminPacketHandler implements ClientPacketHandler {

    public static final String PACKET_ID = "AXS_REGIONS_ADMIN";
    public static final String UI_RESOURCE_PATH = "arcartx/ui/regions_admin.yml";
    public static final String UI_FILE_PATH = "ui/regions_admin.yml";

    private final JavaPlugin plugin;
    private final PacketBridgeAPI packetBridge;
    private final PacketGuardAPI packetGuard;
    private final RegionManager regionManager;
    private final String uiId;

    public RegionsAdminPacketHandler(JavaPlugin plugin, PacketBridgeAPI packetBridge,
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
        if (packetGuard != null && !packetGuard.allow(player, "regions", "admin", false)) return true;
        if (!player.hasPermission("axs.regions.admin")) return true;

        String action = data.isEmpty() ? "refresh" : safe(data.get(0)).toLowerCase(Locale.ROOT);
        switch (action) {
            case "navigate" -> pushData(player, hasValue(data, 1) ? data.get(1) : "regions", null);
            case "refresh" -> pushData(player, hasValue(data, 1) ? data.get(1) : "regions", null);
            case "select_region" -> {
                if (hasValue(data, 1)) {
                    pushData(player, "edit", data.get(1));
                }
            }
            case "set_flag" -> {
                // data: [action, regionId, flagKey, state]
                if (hasValue(data, 3)) {
                    Region region = regionManager.getRegion(data.get(1));
                    if (region != null) {
                        RegionFlag flag = RegionFlag.fromKey(data.get(2));
                        if (flag != null) {
                            RegionFlag.State state = RegionFlag.State.fromString(data.get(3));
                            region.setFlag(flag, state);
                            trySave(region);
                            pushData(player, "edit", region.id());
                        }
                    }
                }
            }
            case "remove_flag" -> {
                // data: [action, regionId, flagKey]
                if (hasValue(data, 2)) {
                    Region region = regionManager.getRegion(data.get(1));
                    if (region != null) {
                        RegionFlag flag = RegionFlag.fromKey(data.get(2));
                        if (flag != null) {
                            region.setFlag(flag, RegionFlag.State.NONE);
                            trySave(region);
                            pushData(player, "edit", region.id());
                        }
                    }
                }
            }
            case "add_member" -> {
                // data: [action, regionId, playerName, role(owner/member)]
                if (hasValue(data, 3)) {
                    Region region = regionManager.getRegion(data.get(1));
                    if (region != null) {
                        Player target = Bukkit.getPlayer(data.get(2));
                        if (target != null) {
                            if ("owner".equalsIgnoreCase(data.get(3))) {
                                region.addOwner(target.getUniqueId());
                            } else {
                                region.addMember(target.getUniqueId());
                            }
                            trySave(region);
                            pushData(player, "edit", region.id());
                        }
                    }
                }
            }
            case "remove_member" -> {
                // data: [action, regionId, uuid]
                if (hasValue(data, 2)) {
                    Region region = regionManager.getRegion(data.get(1));
                    if (region != null) {
                        try {
                            UUID uuid = UUID.fromString(data.get(2));
                            region.removeOwner(uuid);
                            region.removeMember(uuid);
                            trySave(region);
                        } catch (IllegalArgumentException ignored) {}
                        pushData(player, "edit", region.id());
                    }
                }
            }
            case "set_priority" -> {
                // data: [action, regionId, priority]
                if (hasValue(data, 2)) {
                    Region region = regionManager.getRegion(data.get(1));
                    if (region != null) {
                        try {
                            int priority = Integer.parseInt(data.get(2));
                            region.setPriority(priority);
                            trySave(region);
                        } catch (NumberFormatException ignored) {}
                        pushData(player, "edit", region.id());
                    }
                }
            }
            case "set_parent" -> {
                // data: [action, regionId, parentId]
                if (hasValue(data, 2)) {
                    Region region = regionManager.getRegion(data.get(1));
                    if (region != null) {
                        String parentId = data.get(2);
                        region.setParentId(parentId.isBlank() || "none".equalsIgnoreCase(parentId) ? null : parentId);
                        trySave(region);
                        pushData(player, "edit", region.id());
                    }
                }
            }
            case "delete_region" -> {
                if (hasValue(data, 1)) {
                    Region region = regionManager.getRegion(data.get(1));
                    if (region != null) {
                        try {
                            regionManager.deleteRegion(region);
                        } catch (Exception ignored) {}
                        pushData(player, "regions", null);
                    }
                }
            }
            default -> pushData(player, "regions", null);
        }
        return true;
    }

    public void openMenu(Player player) {
        if (packetBridge == null) return;
        packetBridge.openUi(player, uiId);
        pushData(player, "regions", null);
    }

    public void pushData(Player player, String page, String selectedRegionId) {
        if (packetBridge == null) return;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("page", page);
        payload.put("packetId", PACKET_ID);

        // 所有区域列表
        List<Map<String, String>> regionList = new ArrayList<>();
        for (Region region : regionManager.getAllRegions()) {
            Map<String, String> info = new HashMap<>();
            info.put("id", region.id());
            info.put("world", region.world());
            info.put("priority", String.valueOf(region.priority()));
            info.put("volume", String.valueOf(region.volume()));
            regionList.add(info);
        }
        payload.put("regions", regionList);
        payload.put("regionCount", String.valueOf(regionList.size()));

        // 选中区域的详情
        if (selectedRegionId != null) {
            Region region = regionManager.getRegion(selectedRegionId);
            if (region != null) {
                payload.put("selId", region.id());
                payload.put("selWorld", region.world());
                payload.put("selPriority", String.valueOf(region.priority()));
                payload.put("selParent", region.parentId() != null ? region.parentId() : "");
                payload.put("selMin", region.minX() + ", " + region.minY() + ", " + region.minZ());
                payload.put("selMax", region.maxX() + ", " + region.maxY() + ", " + region.maxZ());
                payload.put("selVolume", String.valueOf(region.volume()));

                // 标志
                List<Map<String, String>> flags = new ArrayList<>();
                for (RegionFlag flag : RegionFlag.values()) {
                    Map<String, String> f = new HashMap<>();
                    f.put("key", flag.configKey());
                    f.put("desc", flag.description());
                    f.put("category", flag.category().name().toLowerCase(Locale.ROOT));
                    f.put("state", region.getFlag(flag).name().toLowerCase(Locale.ROOT));
                    flags.add(f);
                }
                payload.put("selFlags", flags);

                // 成员
                List<Map<String, String>> memberList = new ArrayList<>();
                for (UUID uuid : region.owners()) {
                    var op = Bukkit.getOfflinePlayer(uuid);
                    Map<String, String> m = new HashMap<>();
                    m.put("uuid", uuid.toString());
                    m.put("name", op.getName() != null ? op.getName() : uuid.toString().substring(0, 8));
                    m.put("role", "owner");
                    memberList.add(m);
                }
                for (UUID uuid : region.members()) {
                    var op = Bukkit.getOfflinePlayer(uuid);
                    Map<String, String> m = new HashMap<>();
                    m.put("uuid", uuid.toString());
                    m.put("name", op.getName() != null ? op.getName() : uuid.toString().substring(0, 8));
                    m.put("role", "member");
                    memberList.add(m);
                }
                payload.put("selMembers", memberList);
            }
        }

        packetBridge.sendPacket(player, uiId, "init", payload);
    }

    private void trySave(Region region) {
        try { regionManager.saveRegion(region); } catch (Exception ignored) {}
    }

    private static boolean hasValue(List<String> data, int index) {
        return data != null && data.size() > index && !safe(data.get(index)).isBlank();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
