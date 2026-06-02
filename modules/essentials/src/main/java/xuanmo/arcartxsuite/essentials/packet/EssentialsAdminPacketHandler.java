package xuanmo.arcartxsuite.essentials.packet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import xuanmo.arcartxsuite.api.ClientPacketHandler;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;
import xuanmo.arcartxsuite.essentials.service.PlayerManagementService;
import xuanmo.arcartxsuite.essentials.service.TeleportService;
import xuanmo.arcartxsuite.essentials.storage.EssentialsRepository;

/**
 * Essentials 管理员面板的 Packet Handler。
 * <p>
 * 处理客户端 UI 回包：refresh / heal / feed / fly / god / kick / ban / unban /
 * unmute / set_time / set_weather / set_warp / del_warp / set_spawn
 */
public final class EssentialsAdminPacketHandler implements ClientPacketHandler {

    public static final String PACKET_ID = "AXS_ESS_ADMIN";
    public static final String UI_RESOURCE_PATH = "arcartx/ui/essentials_admin.yml";
    public static final String UI_FILE_PATH = "ui/essentials_admin.yml";

    private final JavaPlugin plugin;
    private final PacketBridgeAPI packetBridge;
    private final PacketGuardAPI packetGuard;
    private final PlayerManagementService playerService;
    private final TeleportService teleportService;
    private final EssentialsRepository repository;
    private final String uiId;

    public EssentialsAdminPacketHandler(JavaPlugin plugin, PacketBridgeAPI packetBridge,
                                        PacketGuardAPI packetGuard,
                                        PlayerManagementService playerService,
                                        TeleportService teleportService,
                                        EssentialsRepository repository,
                                        String uiId) {
        this.plugin = plugin;
        this.packetBridge = packetBridge;
        this.packetGuard = packetGuard;
        this.playerService = playerService;
        this.teleportService = teleportService;
        this.repository = repository;
        this.uiId = uiId;
    }

    @Override
    public boolean handleClientPacket(@NotNull Player player, @NotNull String packetId, @NotNull List<String> data) {
        if (!PACKET_ID.equalsIgnoreCase(packetId)) return false;
        if (packetGuard != null && !packetGuard.allow(player, "essentials", "admin", false)) return true;
        if (!player.hasPermission("axs.essentials.admin")) return true;

        String action = data.isEmpty() ? "refresh" : safe(data.get(0)).toLowerCase(Locale.ROOT);
        switch (action) {
            case "refresh" -> pushAdminData(player, hasValue(data, 1) ? data.get(1) : "players");
            case "navigate" -> pushAdminData(player, hasValue(data, 1) ? data.get(1) : "players");
            case "heal" -> {
                Player target = resolveTarget(data, 1);
                if (target != null) { playerService.heal(target); pushAdminData(player, "players"); }
            }
            case "feed" -> {
                Player target = resolveTarget(data, 1);
                if (target != null) { playerService.feed(target); pushAdminData(player, "players"); }
            }
            case "fly" -> {
                Player target = resolveTarget(data, 1);
                if (target != null) { playerService.toggleFly(player, target); pushAdminData(player, "players"); }
            }
            case "god" -> {
                Player target = resolveTarget(data, 1);
                if (target != null) { playerService.toggleGod(target); pushAdminData(player, "players"); }
            }
            case "kick" -> {
                Player target = resolveTarget(data, 1);
                if (target != null) {
                    String reason = hasValue(data, 2) ? data.get(2) : "被管理员踢出";
                    target.kickPlayer(ChatColor.RED + reason);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> pushAdminData(player, "players"), 5L);
                }
            }
            case "ban" -> {
                Player target = resolveTarget(data, 1);
                if (target != null) {
                    try {
                        String reason = hasValue(data, 2) ? data.get(2) : "违规";
                        String ip = target.getAddress() != null ? target.getAddress().getHostString() : null;
                        repository.ban(target.getUniqueId(), target.getName(), reason, player.getName(), -1, ip);
                        target.kickPlayer(ChatColor.RED + "你已被封禁: " + reason);
                        pushAdminData(player, "bans");
                    } catch (Exception ignored) {}
                }
            }
            case "unban" -> {
                if (hasValue(data, 1)) {
                    try {
                        java.util.UUID uuid = java.util.UUID.fromString(data.get(1));
                        repository.unban(uuid);
                        pushAdminData(player, "bans");
                    } catch (Exception ignored) {}
                }
            }
            case "set_time" -> {
                if (hasValue(data, 1)) {
                    World world = player.getWorld();
                    long ticks = switch (data.get(1).toLowerCase(Locale.ROOT)) {
                        case "day" -> 1000L;
                        case "night" -> 13000L;
                        case "noon" -> 6000L;
                        case "midnight" -> 18000L;
                        default -> {
                            try { yield Long.parseLong(data.get(1)); }
                            catch (NumberFormatException e) { yield -1L; }
                        }
                    };
                    if (ticks >= 0) { world.setTime(ticks); pushAdminData(player, "world"); }
                }
            }
            case "set_weather" -> {
                if (hasValue(data, 1)) {
                    World world = player.getWorld();
                    switch (data.get(1).toLowerCase(Locale.ROOT)) {
                        case "clear" -> { world.setStorm(false); world.setThundering(false); }
                        case "rain" -> { world.setStorm(true); world.setThundering(false); }
                        case "thunder" -> { world.setStorm(true); world.setThundering(true); }
                    }
                    pushAdminData(player, "world");
                }
            }
            case "set_warp" -> {
                if (hasValue(data, 1)) {
                    teleportService.setWarp(player, data.get(1));
                    pushAdminData(player, "warps");
                }
            }
            case "del_warp" -> {
                if (hasValue(data, 1)) {
                    teleportService.deleteWarp(player, data.get(1));
                    pushAdminData(player, "warps");
                }
            }
            case "set_spawn" -> {
                teleportService.setSpawn(player);
                pushAdminData(player, "world");
            }
            default -> pushAdminData(player, "players");
        }
        return true;
    }

    /**
     * 打开管理面板并推送初始数据。
     */
    public void openMenu(Player player) {
        if (packetBridge == null) return;
        packetBridge.openUi(player, uiId);
        pushAdminData(player, "players");
    }

    /**
     * 推送管理面板数据到客户端。
     */
    public void pushAdminData(Player player, String page) {
        if (packetBridge == null) return;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("page", page);
        payload.put("packetId", PACKET_ID);

        // 在线玩家列表
        List<Map<String, Object>> players = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Map<String, Object> info = new HashMap<>();
            info.put("name", p.getName());
            info.put("health", String.format("%.0f/%.0f", p.getHealth(), p.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue()));
            info.put("isFlying", String.valueOf(playerService.isFlyEnabled(p.getUniqueId())));
            info.put("isGod", String.valueOf(playerService.isGodEnabled(p.getUniqueId())));
            info.put("world", p.getWorld().getName());
            players.add(info);
        }
        payload.put("players", players);
        payload.put("playerCount", String.valueOf(players.size()));

        // Warp 列表
        List<String> warps = teleportService.getWarpNames();
        payload.put("warps", warps);

        // 世界信息
        World world = player.getWorld();
        payload.put("worldName", world.getName());
        payload.put("worldTime", String.valueOf(world.getTime()));
        payload.put("worldWeather", world.isThundering() ? "thunder" : world.hasStorm() ? "rain" : "clear");

        // 封禁列表
        try {
            List<Map<String, String>> bans = new ArrayList<>();
            for (var record : repository.getAllBans()) {
                Map<String, String> item = new HashMap<>();
                item.put("uuid", record.uuid().toString());
                item.put("name", record.playerName());
                item.put("reason", record.reason() != null ? record.reason() : "");
                item.put("operator", record.operator());
                item.put("permanent", String.valueOf(record.isPermanent()));
                bans.add(item);
            }
            payload.put("bans", bans);
            payload.put("banCount", String.valueOf(bans.size()));
        } catch (Exception e) {
            payload.put("bans", List.of());
            payload.put("banCount", "0");
        }

        packetBridge.sendPacket(player, uiId, "init", payload);
    }

    private Player resolveTarget(List<String> data, int index) {
        if (!hasValue(data, index)) return null;
        return Bukkit.getPlayer(data.get(index));
    }

    private static boolean hasValue(List<String> data, int index) {
        return data != null && data.size() > index && !safe(data.get(index)).isBlank();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
