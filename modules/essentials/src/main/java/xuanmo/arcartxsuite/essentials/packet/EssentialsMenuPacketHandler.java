package xuanmo.arcartxsuite.essentials.packet;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import xuanmo.arcartxsuite.api.ClientPacketHandler;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.security.ActionTokenGuard;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;
import xuanmo.arcartxsuite.essentials.service.InventoryActionsService;
import xuanmo.arcartxsuite.essentials.service.PlayerManagementService;
import xuanmo.arcartxsuite.essentials.service.TeleportService;
import xuanmo.arcartxsuite.essentials.storage.EssentialsRepository;

/**
 * Essentials 玩家菜单的 Packet Handler。
 * <p>
 * 处理客户端 UI 回包：navigate / teleport_home / delete_home / set_home /
 * teleport_warp / send_tpa / accept_tpa / deny_tpa /
 * toggle_replant / toggle_autotool / sort
 */
public final class EssentialsMenuPacketHandler implements ClientPacketHandler {

    public static final String PACKET_ID = "AXS_ESS_MENU";
    public static final String UI_RESOURCE_PATH = "arcartx/ui/essentials_menu.yml";
    public static final String UI_FILE_PATH = "ui/essentials_menu.yml";

    private final JavaPlugin plugin;
    private final PacketBridgeAPI packetBridge;
    private final PacketGuardAPI packetGuard;
    private final PlayerManagementService playerService;
    private final TeleportService teleportService;
    private final EssentialsRepository repository;
    private final InventoryActionsService inventoryActionsService;
    private final String uiId;
    private final ActionTokenGuard tokenGuard = new ActionTokenGuard();

    public EssentialsMenuPacketHandler(JavaPlugin plugin, PacketBridgeAPI packetBridge,
                                       PacketGuardAPI packetGuard,
                                       PlayerManagementService playerService,
                                       TeleportService teleportService,
                                       EssentialsRepository repository,
                                       InventoryActionsService inventoryActionsService,
                                       String uiId) {
        this.plugin = plugin;
        this.packetBridge = packetBridge;
        this.packetGuard = packetGuard;
        this.playerService = playerService;
        this.teleportService = teleportService;
        this.repository = repository;
        this.inventoryActionsService = inventoryActionsService;
        this.uiId = uiId;
    }

    @Override
    public boolean handleClientPacket(@NotNull Player player, @NotNull String packetId, @NotNull List<String> data) {
        if (!PACKET_ID.equalsIgnoreCase(packetId)) return false;
        String action = data.isEmpty() ? "refresh" : safe(data.get(0)).toLowerCase(Locale.ROOT);
        // PacketGuard 已由 ClientPacketRouter 在路由层统一调用，此处不再重复检查。
        // 写操作要求动作令牌：data[1] 为客户端回传的 action_token，业务参数自 data[2] 起。
        boolean writeAction = requiresActionToken(action);
        if (writeAction && !beginActionToken(player, data)) return true;
        try {
            switch (action) {
                case "navigate" -> {
                    String page = hasValue(data, 1) ? data.get(1) : "home";
                    pushInitData(player, page);
                }
                case "refresh" -> pushInitData(player, hasValue(data, 1) ? data.get(1) : "home");
                case "teleport_home" -> {
                    if (hasValue(data, 2)) {
                        teleportService.teleportHome(player, data.get(2));
                        pushInitData(player, "homes");
                    }
                }
                case "delete_home" -> {
                    if (hasValue(data, 2)) {
                        teleportService.deleteHome(player, data.get(2));
                        pushInitData(player, "homes");
                    }
                }
                case "set_home" -> {
                    String name = hasValue(data, 2) ? data.get(2) : "home";
                    teleportService.setHome(player, name);
                    pushInitData(player, "homes");
                }
                case "teleport_warp" -> {
                    if (hasValue(data, 2)) {
                        teleportService.teleportWarp(player, data.get(2));
                    }
                }
                case "send_tpa" -> {
                    if (hasValue(data, 2)) {
                        Player target = Bukkit.getPlayer(data.get(2));
                        if (target != null && target.isOnline()) {
                            boolean here = hasValue(data, 3) && "here".equalsIgnoreCase(data.get(3));
                            teleportService.sendTpa(player, target, here);
                        }
                    }
                }
                case "accept_tpa" -> teleportService.acceptTpa(player);
                case "deny_tpa" -> teleportService.denyTpa(player);
                case "toggle_replant" -> {
                    if (inventoryActionsService != null) {
                        inventoryActionsService.toggleReplant(player.getUniqueId());
                        pushInitData(player, "settings");
                    }
                }
                case "toggle_autotool" -> {
                    if (inventoryActionsService != null) {
                        inventoryActionsService.toggleAutoTool(player.getUniqueId());
                        pushInitData(player, "settings");
                    }
                }
                case "sort" -> {
                    if (inventoryActionsService != null) {
                        inventoryActionsService.sortInventory(player);
                    }
                }
                default -> pushInitData(player, "home");
            }
        } finally {
            if (writeAction) {
                tokenGuard.finish(player.getUniqueId());
            }
        }
        return true;
    }

    /**
     * 打开菜单并推送初始数据。
     */
    public void openMenu(Player player) {
        if (packetBridge == null) return;
        tokenGuard.issue(player.getUniqueId(), uiId);
        packetBridge.openUi(player, uiId);
        pushInitData(player, "home");
    }

    /** 玩家退出时清理其 token 与在途标记（由模块 quit 监听调用）。 */
    public void clearPlayer(java.util.UUID playerId) {
        tokenGuard.clear(playerId);
    }

    /**
     * 推送页面数据到客户端。
     */
    public void pushInitData(Player player, String page) {
        if (packetBridge == null) return;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("page", page);
        payload.put("packetId", PACKET_ID);
        // 每次推送轮换动作令牌：写操作回包必须携带最新 token（防重放/防外部注入）。
        payload.put("action_token", tokenGuard.rotate(player.getUniqueId(), uiId));

        // 玩家状态
        payload.put("playerName", player.getName());
        payload.put("isFlying", String.valueOf(playerService.isFlyEnabled(player.getUniqueId())));
        payload.put("isGod", String.valueOf(playerService.isGodEnabled(player.getUniqueId())));
        payload.put("isAfk", String.valueOf(playerService.isAfk(player.getUniqueId())));
        payload.put("flySpeed", String.format("%.1f", player.getFlySpeed() * 10));
        payload.put("walkSpeed", String.format("%.1f", player.getWalkSpeed() * 10));
        Location loc = player.getLocation();
        payload.put("location", loc.getWorld().getName() + " " +
            (int) loc.getX() + ", " + (int) loc.getY() + ", " + (int) loc.getZ());

        // 家列表
        try {
            Map<String, Location> homes = repository.getHomes(player.getUniqueId());
            List<Map<String, String>> homeList = new ArrayList<>();
            for (var entry : homes.entrySet()) {
                Map<String, String> item = new HashMap<>();
                item.put("name", entry.getKey());
                Location h = entry.getValue();
                item.put("world", h.getWorld() != null ? h.getWorld().getName() : "?");
                item.put("coords", (int) h.getX() + ", " + (int) h.getY() + ", " + (int) h.getZ());
                homeList.add(item);
            }
            payload.put("homes", homeList);
            payload.put("homeCount", String.valueOf(homeList.size()));
        } catch (SQLException e) {
            payload.put("homes", List.of());
            payload.put("homeCount", "0");
        }

        // Warp 列表
        List<String> warps = teleportService.getWarpNames();
        payload.put("warps", warps);
        payload.put("warpCount", String.valueOf(warps.size()));

        // 在线玩家（用于 TPA）
        List<String> onlinePlayers = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.equals(player)) onlinePlayers.add(p.getName());
        }
        payload.put("onlinePlayers", onlinePlayers);

        // 设置状态
        if (inventoryActionsService != null) {
            payload.put("replantEnabled", String.valueOf(inventoryActionsService.isReplantEnabled(player.getUniqueId())));
            payload.put("autotoolEnabled", String.valueOf(inventoryActionsService.isAutoToolEnabled(player.getUniqueId())));
        } else {
            payload.put("replantEnabled", "false");
            payload.put("autotoolEnabled", "false");
        }

        packetBridge.sendPacket(player, uiId, "init", payload);
    }

    /** 写操作动作集合：需要有效动作令牌且在途槽位空闲。 */
    private static boolean requiresActionToken(String action) {
        return switch (action) {
            case "teleport_home", "delete_home", "set_home", "teleport_warp",
                "send_tpa", "accept_tpa", "deny_tpa",
                "toggle_replant", "toggle_autotool", "sort" -> true;
            default -> false;
        };
    }

    /**
     * 校验写操作令牌：要求 UI 已打开、data[1] 与当前签发 token 一致、且玩家无在途写操作。
     */
    private boolean beginActionToken(Player player, List<String> data) {
        String supplied = hasValue(data, 1) ? data.get(1) : "";
        return packetBridge != null && packetBridge.isUiOpen(player, uiId)
            && tokenGuard.begin(player.getUniqueId(), uiId, supplied);
    }

    private static boolean hasValue(List<String> data, int index) {
        return data != null && data.size() > index && !safe(data.get(index)).isBlank();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
