package xuanmo.arcartxsuite.essentials.packet;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.capability.ChatMutable;
import xuanmo.arcartxsuite.api.util.AttributeResolver;
import xuanmo.arcartxsuite.essentials.command.EssentialsCommandDelegate;
import xuanmo.arcartxsuite.essentials.service.PlayerManagementService;
import xuanmo.arcartxsuite.essentials.service.WarpDirectory;
import xuanmo.arcartxsuite.essentials.storage.EssentialsRepository;

public final class EssentialsAdminPacketHandler extends EssentialsPanel {

    public static final String PACKET_ID = "AXS_ESS_ADMIN";
    public static final String UI_RESOURCE_PATH = "arcartx/ui/essentials_admin.yml";
    public static final String UI_FILE_PATH = "ui/essentials/essentials_admin.yml";
    private final PlayerManagementService players;
    private final EssentialsRepository repository;
    private final Supplier<ChatMutable> chatMutableSupplier;
    private final WarpDirectory warps;

    public EssentialsAdminPacketHandler(JavaPlugin plugin, PacketBridgeAPI bridge, PlayerManagementService players,
                                         EssentialsRepository repository, Supplier<ChatMutable> chatMutableSupplier,
                                         WarpDirectory warps, String uiId, EssentialsCommandDelegate commands, xuanmo.arcartxsuite.essentials.service.EssentialsWork work) {
        super(plugin, bridge, uiId, PACKET_ID, "axs.essentials.admin", "players",
            Set.of("players", "bans", "mutes", "warps", "world"), commands, work);
        this.players = players;
        this.repository = repository;
        this.chatMutableSupplier = chatMutableSupplier;
        this.warps = warps;
    }

    @Override
    void execute(Player player, String action, List<String> data) {
        String target = value(data, 2, "");
        String reason = value(data, 3, "无");
        switch (action) {
            case "heal", "feed", "fly", "god" -> {
                if (!target.isEmpty()) commands.executePanelAction(player, action, target);
            }
            case "teleport_to" -> commands.executePanelAction(player, "tp", target);
            case "teleport_here" -> commands.executePanelAction(player, "tp", target, player.getName());
            case "mute", "kick", "ban" -> {
                if (!target.isEmpty()) commands.executePanelAction(player, action, target, reason);
            }
            case "unmute", "unban" -> {
                if (!target.isEmpty()) commands.executePanelAction(player, action, target);
            }
            case "ban_history" -> commands.executePanelAction(player, "banhistory", target);
            case "set_time" -> {
                if (target.matches("[0-9]+")) commands.executePanelAction(player, "time", "set", target);
                else commands.executePanelAction(player, "time", target);
            }
            case "set_weather" -> commands.executePanelAction(player, "weather", target);
            case "set_warp" -> commands.executePanelAction(player, "setwarp", target);
            case "del_warp" -> commands.executePanelAction(player, "delwarp", target);
            case "set_spawn" -> commands.executePanelAction(player, "setspawn");
        }
    }

    @Override
    boolean canView(Player player, Page page) {
        return switch (page.name()) {
            case "bans" -> allowed(player, "ban") || allowed(player, "unban") || allowed(player, "banhistory");
            case "mutes" -> allowed(player, "mute") || allowed(player, "unmute");
            case "warps" -> allowed(player, "setwarp") || allowed(player, "delwarp");
            case "world" -> allowed(player, "time") || allowed(player, "weather") || allowed(player, "setspawn");
            default -> true;
        };
    }

    @Override
    CompletionStage<Map<String, Object>> snapshot(Player player, Page page, Map<String, Object> payload) {
        List<? extends Player> online = Bukkit.getOnlinePlayers().stream()
            .filter(target -> players.canSee(player, target))
            .sorted(java.util.Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER)).toList();
        payload.put("players", List.of());
        payload.put("playerCount", String.valueOf(online.size()));
        payload.put("warps", List.of());
        payload.put("warpShown", 0);
        payload.put("bans", List.of());
        payload.put("banCount", "0");
        payload.put("banShown", 0);
        payload.put("mutes", List.of());
        payload.put("muteCount", "0");
        ChatMutable chat = chatMutableSupplier.get();
        payload.put("chatAvailable", String.valueOf(chat != null));
        World world = player.getWorld();
        payload.put("worldName", world.getName());
        payload.put("worldTime", String.valueOf(world.getTime()));
        payload.put("worldWeather", world.isThundering() ? "thunder" : world.hasStorm() ? "rain" : "clear");

        return switch (page.name()) {
            case "players" -> {
                var rows = pageSlice(online, page).stream().map(target -> Map.of(
                    "name", target.getName(),
                    "health", String.format(Locale.ROOT, "%.0f/%.0f", target.getHealth(), AttributeResolver.getMaxHealth(target)),
                    "isFlying", String.valueOf(players.isFlyEnabled(target.getUniqueId())),
                    "isGod", String.valueOf(players.isGodEnabled(target.getUniqueId())),
                    "world", target.getWorld().getName())).toList();
                pageItems(payload, "players", rows);
                yield null;
            }
            case "warps" -> {
                var rows = pageSlice(warps.adminNames(page.query()), page);
                pageItems(payload, "warps", rows);
                payload.put("warpShown", Math.min(rows.size(), PAGE_SIZE));
                yield null;
            }
            case "bans" -> read(() -> {
                Map<String, Object> result = new HashMap<>();
                var rows = repository.listActiveBans(PAGE_SIZE + 1, page.offset()).stream().map(record -> Map.of(
                    "uuid", record.uuid().toString(), "name", record.playerName(),
                    "reason", record.reason() == null ? "" : record.reason(), "operator", record.operator(),
                    "permanent", String.valueOf(record.isPermanent()))).toList();
                pageItems(result, "bans", rows);
                result.put("banCount", String.valueOf(Math.min(rows.size(), PAGE_SIZE)));
                result.put("banShown", Math.min(rows.size(), PAGE_SIZE));
                return result;
            });
            case "mutes" -> chat == null ? null : chat.listMutes(PAGE_SIZE + 1, page.offset()).thenApply(mutes -> {
                var rows = mutes.stream().map(info -> Map.of(
                    "uuid", info.playerUuid().toString(), "name", info.playerName() == null ? "未知" : info.playerName(),
                    "reason", info.reason() == null ? "" : info.reason(), "operator", info.mutedBy() == null ? "" : info.mutedBy(),
                    "permanent", String.valueOf(info.isPermanent()))).toList();
                Map<String, Object> result = new HashMap<>();
                pageItems(result, "mutes", rows);
                result.put("muteCount", String.valueOf(Math.min(rows.size(), PAGE_SIZE)));
                return result;
            });
            default -> null;
        };
    }
}
