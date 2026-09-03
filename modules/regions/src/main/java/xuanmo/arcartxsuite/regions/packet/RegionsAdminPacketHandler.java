package xuanmo.arcartxsuite.regions.packet;
import xuanmo.arcartxsuite.api.util.AxsScheduler;

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
import xuanmo.arcartxsuite.regions.config.RegionsConfiguration;
import xuanmo.arcartxsuite.regions.model.Region;
import xuanmo.arcartxsuite.regions.model.RegionFlag;
import xuanmo.arcartxsuite.regions.model.Selection;
import xuanmo.arcartxsuite.regions.service.RegionManager;
import xuanmo.arcartxsuite.regions.service.WorldRulesService;

/**
 * Regions 管理员区域面板的 Packet Handler。
 * <p>
 * 处理客户端 UI 回包：navigate / select_region / create_region / redefine_region /
 * rename_region / set_flag / remove_flag / add_member / remove_member /
 * set_priority / set_parent / delete_region /
 * select_world_rule / toggle_no_fly / toggle_no_piston / toggle_interaction
 */
public final class RegionsAdminPacketHandler implements ClientPacketHandler {

    public static final String PACKET_ID = "AXS_REGIONS_ADMIN";
    public static final String UI_RESOURCE_PATH = "arcartx/ui/regions_admin.yml";
    public static final String UI_FILE_PATH = "ui/regions_admin.yml";

    private final JavaPlugin plugin;
    private final PacketBridgeAPI packetBridge;
    private final PacketGuardAPI packetGuard;
    private final RegionManager regionManager;
    private final RegionsConfiguration config;
    private final WorldRulesService worldRulesService;
    private final String uiId;

    public RegionsAdminPacketHandler(JavaPlugin plugin, PacketBridgeAPI packetBridge,
                                     PacketGuardAPI packetGuard, RegionManager regionManager,
                                     RegionsConfiguration config, WorldRulesService worldRulesService,
                                     String uiId) {
        this.plugin = plugin;
        this.packetBridge = packetBridge;
        this.packetGuard = packetGuard;
        this.regionManager = regionManager;
        this.config = config;
        this.worldRulesService = worldRulesService;
        this.uiId = uiId;
    }

    @Override
    public boolean handleClientPacket(@NotNull Player player, @NotNull String packetId, @NotNull List<String> data) {
        if (!PACKET_ID.equalsIgnoreCase(packetId)) return false;
        if (!player.hasPermission("axs.regions.admin")) return true;
        String action = data.isEmpty() ? "refresh" : safe(data.get(0)).toLowerCase(Locale.ROOT);
        // PacketGuard 已由 ClientPacketRouter 在路由层统一调用，此处不再重复检查。

        switch (action) {
            case "navigate" -> pushData(player, hasValue(data, 1) ? data.get(1) : "regions", null);
            case "refresh" -> pushData(player, hasValue(data, 1) ? data.get(1) : "regions", null);
            case "select_region" -> {
                if (hasValue(data, 1)) {
                    pushData(player, "edit", data.get(1));
                }
            }
            case "create_region" -> {
                // data: [action, regionName] — 用玩家当前选区创建区域
                if (hasValue(data, 1)) {
                    handleCreateRegion(player, data.get(1));
                }
            }
            case "redefine_region" -> {
                // data: [action, regionId] — 用玩家当前选区重定义区域范围
                if (hasValue(data, 1)) {
                    handleRedefineRegion(player, data.get(1));
                }
            }
            case "rename_region" -> {
                // data: [action, oldId, newId]
                if (hasValue(data, 2)) {
                    handleRenameRegion(player, data.get(1), data.get(2));
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
                        // 支持离线玩家：优先在线匹配，否则走 OfflinePlayer（同步查 Mojang 仅在必要时）
                        Player online = Bukkit.getPlayerExact(data.get(2));
                        java.util.UUID targetUuid;
                        if (online != null) {
                            targetUuid = online.getUniqueId();
                        } else {
                            targetUuid = Bukkit.getOfflinePlayer(data.get(2)).getUniqueId();
                        }
                        if ("owner".equalsIgnoreCase(data.get(3))) {
                            region.addOwner(targetUuid);
                        } else {
                            region.addMember(targetUuid);
                        }
                        trySave(region);
                        pushData(player, "edit", region.id());
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
            case "select_world_rule" -> pushWorldRulesData(player);
            case "toggle_no_fly" -> {
                // data: [action, worldName]
                if (hasValue(data, 1) && worldRulesService != null) {
                    worldRulesService.toggleNoFlyWorld(data.get(1));
                    pushWorldRulesData(player);
                }
            }
            case "toggle_no_piston" -> {
                if (hasValue(data, 1) && worldRulesService != null) {
                    worldRulesService.toggleNoPistonWorld(data.get(1));
                    pushWorldRulesData(player);
                }
            }
            case "toggle_interaction" -> {
                // data: [action, worldName, materialName]
                if (hasValue(data, 2) && worldRulesService != null) {
                    org.bukkit.Material mat = org.bukkit.Material.matchMaterial(data.get(2));
                    if (mat != null) {
                        worldRulesService.toggleDisabledInteraction(data.get(1), mat);
                    }
                    pushWorldRulesData(player);
                }
            }
            default -> pushData(player, "regions", null);
        }
        return true;
    }

    public void openMenu(Player player) {
        if (packetBridge == null) return;
        packetBridge.openUi(player, uiId);
        AxsScheduler.runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                pushData(player, "regions", null);
            }
        }, 2L);
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

    /**
     * 用玩家当前选区创建区域。校验：选区完成、体积不超限、名称不重复、数量不超限。
     */
    private void handleCreateRegion(Player player, String name) {
        Selection sel = regionManager.getSelection(player.getUniqueId());
        if (!sel.isComplete()) {
            pushToast(player, "选区未完成，请先用木斧选定两个点");
            pushData(player, "regions", null);
            return;
        }
        if (sel.volume() > config.selection().maxVolume()) {
            pushToast(player, "选区体积超过限制 (" + config.selection().maxVolume() + ")");
            pushData(player, "regions", null);
            return;
        }
        if (regionManager.getRegion(name, sel.world()) != null) {
            pushToast(player, "区域 " + name + " 已存在");
            pushData(player, "regions", null);
            return;
        }
        int maxPer = config.selection().maxRegionsPerPlayer();
        if (maxPer > 0 && !player.hasPermission("axs.regions.bypass.limit")
            && regionManager.countRegionsByOwner(player.getUniqueId()) >= maxPer) {
            pushToast(player, "你已达到最大区域数量限制 (" + maxPer + ")");
            pushData(player, "regions", null);
            return;
        }
        try {
            Region region = regionManager.createRegion(name, sel);
            region.addOwner(player.getUniqueId());
            regionManager.saveRegion(region);
            regionManager.clearSelection(player.getUniqueId());
            pushToast(player, "区域 " + name + " 已创建");
            pushData(player, "edit", name);
        } catch (Exception e) {
            pushToast(player, "创建失败: " + e.getMessage());
            pushData(player, "regions", null);
        }
    }

    /**
     * 用玩家当前选区重定义区域范围。
     */
    private void handleRedefineRegion(Player player, String regionId) {
        Region region = regionManager.getRegion(regionId);
        if (region == null) {
            pushToast(player, "未找到区域: " + regionId);
            pushData(player, "regions", null);
            return;
        }
        Selection sel = regionManager.getSelection(player.getUniqueId());
        if (!sel.isComplete()) {
            pushToast(player, "选区未完成，请先用木斧选定两个点");
            pushData(player, "edit", regionId);
            return;
        }
        if (sel.volume() > config.selection().maxVolume()) {
            pushToast(player, "选区体积超过限制 (" + config.selection().maxVolume() + ")");
            pushData(player, "edit", regionId);
            return;
        }
        region.redefine(sel.x1(), sel.y1(), sel.z1(), sel.x2(), sel.y2(), sel.z2());
        trySave(region);
        pushToast(player, "区域 " + regionId + " 范围已更新");
        pushData(player, "edit", regionId);
    }

    /**
     * 重命名区域。校验新名称不重复、不与现有区域冲突。
     */
    private void handleRenameRegion(Player player, String oldId, String newId) {
        Region region = regionManager.getRegion(oldId);
        if (region == null) {
            pushToast(player, "未找到区域: " + oldId);
            pushData(player, "regions", null);
            return;
        }
        if (regionManager.getRegion(newId, region.world()) != null) {
            pushToast(player, "名称 " + newId + " 已存在");
            pushData(player, "edit", oldId);
            return;
        }
        try {
            regionManager.renameRegion(region, newId);
            pushToast(player, "区域 " + oldId + " 已重命名为 " + newId);
            pushData(player, "edit", newId);
        } catch (Exception e) {
            pushToast(player, "重命名失败: " + e.getMessage());
            pushData(player, "edit", oldId);
        }
    }

    /**
     * 推送世界规则数据：所有已加载世界 + 每个世界的禁飞/禁活塞/禁交互状态。
     */
    public void pushWorldRulesData(Player player) {
        if (packetBridge == null) return;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("page", "world_rules");
        payload.put("packetId", PACKET_ID);

        List<Map<String, String>> worldList = new ArrayList<>();
        for (org.bukkit.World w : Bukkit.getWorlds()) {
            Map<String, String> info = new HashMap<>();
            info.put("name", w.getName());
            info.put("noFly", String.valueOf(worldRulesService != null && worldRulesService.isNoFlyWorld(w.getName())));
            info.put("noPiston", String.valueOf(worldRulesService != null && worldRulesService.noPistonWorlds().contains(w.getName())));
            // 禁交互物品列表（逗号分隔）
            String interactions = "";
            if (worldRulesService != null) {
                var set = worldRulesService.disabledInteractions().get(w.getName());
                if (set != null && !set.isEmpty()) {
                    interactions = set.stream().map(org.bukkit.Material::name)
                        .reduce((a, b) -> a + "," + b).orElse("");
                }
            }
            info.put("interactions", interactions);
            worldList.add(info);
        }
        payload.put("worlds", worldList);
        payload.put("worldCount", String.valueOf(worldList.size()));
        packetBridge.sendPacket(player, uiId, "init", payload);
    }

    /**
     * 向玩家推送一条 toast 消息（复用 init 通道的 toast 字段，UI 端 var.toast 显示）。
     */
    private void pushToast(Player player, String message) {
        // 简单实现：通过 chat 发送，UI 端无需特殊处理
        player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&',
            "&8[&6Regions&8] &r" + message));
    }

    private static boolean hasValue(List<String> data, int index) {
        return data != null && data.size() > index && !safe(data.get(index)).isBlank();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
