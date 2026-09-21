package xuanmo.arcartxsuite.essentials.packet;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.ClientPacketHandler;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.capability.ChatMutable;
import xuanmo.arcartxsuite.api.security.ActionTokenGuard;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;
import xuanmo.arcartxsuite.api.util.AttributeResolver;
import xuanmo.arcartxsuite.essentials.service.PlayerManagementService;
import xuanmo.arcartxsuite.essentials.service.TeleportService;
import xuanmo.arcartxsuite.essentials.storage.EssentialsRepository;

/**
 * Essentials 管理员面板的 Packet Handler。
 * <p>
 * 处理客户端 UI 回包：refresh / heal / feed / fly / god / teleport_to / teleport_here /
 * mute / unmute / kick / ban / unban / set_time / set_weather / set_warp / del_warp / set_spawn
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
    private final Supplier<ChatMutable> chatMutableSupplier;
    private final Supplier<Boolean> recordBanIp;
    private final String uiId;
    private final ActionTokenGuard tokenGuard = new ActionTokenGuard();

    public EssentialsAdminPacketHandler(JavaPlugin plugin, PacketBridgeAPI packetBridge,
                                        PacketGuardAPI packetGuard,
                                        PlayerManagementService playerService,
                                        TeleportService teleportService,
                                        EssentialsRepository repository,
                                        Supplier<ChatMutable> chatMutableSupplier,
                                        Supplier<Boolean> recordBanIp,
                                        String uiId) {
        this.plugin = plugin;
        this.packetBridge = packetBridge;
        this.packetGuard = packetGuard;
        this.playerService = playerService;
        this.teleportService = teleportService;
        this.repository = repository;
        this.chatMutableSupplier = chatMutableSupplier;
        this.recordBanIp = recordBanIp;
        this.uiId = uiId;
    }

    @Override
    public boolean handleClientPacket(@NotNull Player player, @NotNull String packetId, @NotNull List<String> data) {
        if (!PACKET_ID.equalsIgnoreCase(packetId)) return false;
        if (!player.hasPermission("axs.essentials.admin")) return true;
        String action = data.isEmpty() ? "refresh" : safe(data.get(0)).toLowerCase(Locale.ROOT);
        // PacketGuard 已由 ClientPacketRouter 在路由层统一调用，此处不再重复检查。
        // 写操作要求动作令牌：data[1] 为客户端回传的 action_token，业务参数自 data[2] 起。
        boolean writeAction = requiresActionToken(action);
        if (writeAction && !beginActionToken(player, data)) return true;
        try {
            switch (action) {
                case "refresh" -> pushAdminData(player, hasValue(data, 1) ? data.get(1) : "players");
                case "navigate" -> pushAdminData(player, hasValue(data, 1) ? data.get(1) : "players");
                case "heal" -> {
                    if (!player.hasPermission("axs.essentials.heal")) break;
                    Player target = resolveTarget(data, 2);
                    if (target != null) { playerService.heal(target); pushAdminData(player, "players"); }
                }
                case "feed" -> {
                    if (!player.hasPermission("axs.essentials.feed")) break;
                    Player target = resolveTarget(data, 2);
                    if (target != null) { playerService.feed(target); pushAdminData(player, "players"); }
                }
                case "fly" -> {
                    if (!player.hasPermission("axs.essentials.fly")) break;
                    Player target = resolveTarget(data, 2);
                    if (target != null) { playerService.toggleFly(player, target); pushAdminData(player, "players"); }
                }
                case "god" -> {
                    if (!player.hasPermission("axs.essentials.god")) break;
                    Player target = resolveTarget(data, 2);
                    if (target != null) { playerService.toggleGod(target); pushAdminData(player, "players"); }
                }
                case "teleport_to" -> {
                    if (!player.hasPermission("axs.essentials.teleport")) break;
                    Player target = resolveTarget(data, 2);
                    if (target != null && target != player) {
                        teleportService.delayedTeleport(player, target.getLocation(), "已传送到 " + target.getName());
                    }
                }
                case "teleport_here" -> {
                    if (!player.hasPermission("axs.essentials.teleport")) break;
                    Player target = resolveTarget(data, 2);
                    if (target != null && target != player) {
                        teleportService.delayedTeleport(target, player.getLocation(), "你已被传送到管理员身边");
                    }
                }
                case "mute" -> {
                    if (!player.hasPermission("axs.essentials.mute")) break;
                    Player target = resolveTarget(data, 2);
                    if (target != null) {
                        ChatMutable chatMutable = chatMutableSupplier != null ? chatMutableSupplier.get() : null;
                        if (chatMutable != null) {
                            String reason = hasValue(data, 3) ? data.get(3) : "无";
                            String result = chatMutable.mutePlayer(target.getName(), null, reason, player.getName());
                            player.sendMessage(prefix(result));
                            pushAdminData(player, "mutes");
                        } else {
                            player.sendMessage(prefix(ChatColor.RED + "聊天模块未加载，无法禁言"));
                        }
                    }
                }
                case "unmute" -> {
                    if (!player.hasPermission("axs.essentials.unmute")) break;
                    if (hasValue(data, 2)) {
                        ChatMutable chatMutable = chatMutableSupplier != null ? chatMutableSupplier.get() : null;
                        if (chatMutable != null) {
                            String result = chatMutable.unmutePlayer(data.get(2));
                            player.sendMessage(prefix(result));
                            pushAdminData(player, "mutes");
                        } else {
                            player.sendMessage(prefix(ChatColor.RED + "聊天模块未加载，无法解禁"));
                        }
                    }
                }
                case "kick" -> {
                    if (!player.hasPermission("axs.essentials.kick")) break;
                    Player target = resolveTarget(data, 2);
                    if (target != null) {
                        String reason = hasValue(data, 3) ? data.get(3) : "被管理员踢出";
                        target.kickPlayer(ChatColor.RED + reason);
                        Bukkit.getScheduler().runTaskLater(plugin, () -> pushAdminData(player, "players"), 5L);
                    }
                }
                case "ban" -> {
                    if (!player.hasPermission("axs.essentials.ban")) break;
                    Player target = resolveTarget(data, 2);
                    if (target != null) {
                        try {
                            String reason = hasValue(data, 3) ? data.get(3) : "违规";
                            String ip = recordBanIp.get() && target.getAddress() != null
                                ? target.getAddress().getHostString() : null;
                            repository.ban(target.getUniqueId(), target.getName(), reason, player.getName(), -1, ip);
                            target.kickPlayer(ChatColor.RED + "你已被封禁: " + reason);
                            pushAdminData(player, "bans");
                        } catch (Exception ignored) {}
                    }
                }
                case "unban" -> {
                    if (!player.hasPermission("axs.essentials.unban")) break;
                    if (hasValue(data, 2)) {
                        try {
                            java.util.UUID uuid = java.util.UUID.fromString(data.get(2));
                            repository.unban(uuid);
                            pushAdminData(player, "bans");
                        } catch (Exception ignored) {}
                    }
                }
                case "set_time" -> {
                    if (hasValue(data, 2)) {
                        World world = player.getWorld();
                        long ticks = switch (data.get(2).toLowerCase(Locale.ROOT)) {
                            case "day" -> 1000L;
                            case "night" -> 13000L;
                            case "noon" -> 6000L;
                            case "midnight" -> 18000L;
                            default -> {
                                try { yield Long.parseLong(data.get(2)); }
                                catch (NumberFormatException e) { yield -1L; }
                            }
                        };
                        if (ticks >= 0) { world.setTime(ticks); pushAdminData(player, "world"); }
                    }
                }
                case "set_weather" -> {
                    if (hasValue(data, 2)) {
                        World world = player.getWorld();
                        switch (data.get(2).toLowerCase(Locale.ROOT)) {
                            case "clear" -> { world.setStorm(false); world.setThundering(false); }
                            case "rain" -> { world.setStorm(true); world.setThundering(false); }
                            case "thunder" -> { world.setStorm(true); world.setThundering(true); }
                        }
                        pushAdminData(player, "world");
                    }
                }
                case "set_warp" -> {
                    if (hasValue(data, 2)) {
                        teleportService.setWarp(player, data.get(2));
                        pushAdminData(player, "warps");
                    }
                }
                case "del_warp" -> {
                    if (hasValue(data, 2)) {
                        teleportService.deleteWarp(player, data.get(2));
                        pushAdminData(player, "warps");
                    }
                }
                case "set_spawn" -> {
                    teleportService.setSpawn(player);
                    pushAdminData(player, "world");
                }
                default -> pushAdminData(player, "players");
            }
        } finally {
            if (writeAction) {
                tokenGuard.finish(player.getUniqueId());
            }
        }
        return true;
    }

    /**
     * 打开管理面板并推送初始数据。
     */
    public void openMenu(Player player) {
        if (packetBridge == null) return;
        tokenGuard.issue(player.getUniqueId(), uiId);
        packetBridge.openUi(player, uiId);
        pushAdminData(player, "players");
    }

    /** 玩家退出时清理其 token 与在途标记（由模块 quit 监听调用）。 */
    public void clearPlayer(java.util.UUID playerId) {
        tokenGuard.clear(playerId);
    }

    /**
     * 推送管理面板数据到客户端。
     */
    public void pushAdminData(Player player, String page) {
        if (packetBridge == null) return;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("page", page);
        payload.put("packetId", PACKET_ID);
        // 每次推送轮换动作令牌：写操作回包必须携带最新 token（防重放/防外部注入）。
        payload.put("action_token", tokenGuard.rotate(player.getUniqueId(), uiId));

        // 在线玩家列表
        List<Map<String, Object>> players = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Map<String, Object> info = new HashMap<>();
            info.put("name", p.getName());
            info.put("health", String.format("%.0f/%.0f", p.getHealth(), AttributeResolver.getMaxHealth(p)));
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

        // 禁言列表（来自 Chat 模块的 ChatMutable capability）
        ChatMutable chatMutable = chatMutableSupplier != null ? chatMutableSupplier.get() : null;
        payload.put("chatAvailable", String.valueOf(chatMutable != null));
        if (chatMutable != null) {
            List<Map<String, String>> mutes = new ArrayList<>();
            for (var info : chatMutable.listMutes()) {
                Map<String, String> item = new HashMap<>();
                item.put("uuid", info.playerUuid().toString());
                item.put("name", info.playerName() != null ? info.playerName() : "未知");
                item.put("reason", info.reason() != null ? info.reason() : "");
                item.put("operator", info.mutedBy() != null ? info.mutedBy() : "");
                item.put("permanent", String.valueOf(info.isPermanent()));
                mutes.add(item);
            }
            payload.put("mutes", mutes);
            payload.put("muteCount", String.valueOf(mutes.size()));
        } else {
            payload.put("mutes", List.of());
            payload.put("muteCount", "0");
        }

        packetBridge.sendPacket(player, uiId, "init", payload);
    }

    /** 写操作动作集合：需要有效动作令牌且在途槽位空闲。 */
    private static boolean requiresActionToken(String action) {
        return switch (action) {
            case "heal", "feed", "fly", "god", "teleport_to", "teleport_here",
                "mute", "unmute", "kick", "ban", "unban",
                "set_time", "set_weather", "set_warp", "del_warp", "set_spawn" -> true;
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

    private Player resolveTarget(List<String> data, int index) {
        if (!hasValue(data, index)) return null;
        return Bukkit.getPlayer(data.get(index));
    }

    private static boolean hasValue(List<String> data, int index) {
        return data != null && data.size() > index && !safe(data.get(index)).isBlank();
    }

    private static String prefix(String message) {
        return ChatColor.DARK_AQUA + "◆ " + ChatColor.GOLD + "ArcartXSuite " + ChatColor.GRAY + "| " + ChatColor.RESET + message;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
