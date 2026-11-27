package xuanmo.arcartxsuite.essentials.packet;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.essentials.command.EssentialsCommandDelegate;
import xuanmo.arcartxsuite.essentials.service.InventoryActionsService;
import xuanmo.arcartxsuite.essentials.service.PlayerManagementService;
import xuanmo.arcartxsuite.essentials.service.TeleportService;
import xuanmo.arcartxsuite.essentials.storage.EssentialsRepository;

public final class EssentialsMenuPacketHandler extends EssentialsPanel {

    public static final String PACKET_ID = "AXS_ESS_MENU";
    public static final String UI_RESOURCE_PATH = "arcartx/ui/essentials_menu.yml";
    public static final String UI_FILE_PATH = "ui/essentials/essentials_menu.yml";
    private final PlayerManagementService players;
    private final EssentialsRepository repository;
    private final InventoryActionsService inventory;
    private final TeleportService teleport;

    public EssentialsMenuPacketHandler(JavaPlugin plugin, PacketBridgeAPI bridge, PlayerManagementService players,
                                        EssentialsRepository repository, InventoryActionsService inventory, TeleportService teleport,
                                        String uiId, EssentialsCommandDelegate commands, xuanmo.arcartxsuite.essentials.service.EssentialsWork work) {
        super(plugin, bridge, uiId, PACKET_ID, "axs.essentials.menu", "home",
            Set.of("home", "homes", "warps", "tpa", "tpa_requests", "settings"), commands, work);
        this.players = players;
        this.repository = repository;
        this.inventory = inventory;
        this.teleport = teleport;
    }

    @Override
    void execute(Player player, String action, List<String> data) {
        String target = value(data, 2, "");
        switch (action) {
            case "teleport_home" -> commands.executePanelAction(player, "home", target);
            case "delete_home" -> commands.executePanelAction(player, "delhome", target);
            case "set_home" -> commands.executePanelAction(player, "sethome", value(data, 2, "home"));
            case "teleport_warp" -> commands.executePanelAction(player, "warp", target);
            case "favorite_warp" -> commands.executePanelAction(player, "warpfavorite", target);
            case "send_tpa" -> commands.executePanelAction(player,
                "here".equalsIgnoreCase(value(data, 3, "")) ? "tpahere" : "tpa", target);
            case "accept_tpa", "deny_tpa" -> {
                if (!target.isEmpty()) commands.executePanelAction(player,
                    action.equals("accept_tpa") ? "tpaccept" : "tpdeny", target);
            }
            case "toggle_tpa" -> commands.executePanelAction(player, "tptoggle");
            case "cancel_tpa" -> commands.executePanelAction(player, "tpcancel", target);
            case "toggle_replant" -> commands.executePanelAction(player, "replant");
            case "toggle_autotool" -> commands.executePanelAction(player, "autotool");
        }
    }

    @Override
    boolean canView(Player player, Page page) {
        return switch (page.name()) {
            case "homes" -> allowed(player, "home") || allowed(player, "sethome") || allowed(player, "delhome");
            case "warps" -> allowed(player, "warp");
            case "tpa", "tpa_requests" -> allowed(player, "tpa") || allowed(player, "tpahere")
                || allowed(player, "tpaccept") || allowed(player, "tpdeny") || allowed(player, "tptoggle");
            default -> true;
        };
    }

    @Override
    CompletionStage<Map<String, Object>> snapshot(Player player, Page page, Map<String, Object> payload) {
        UUID id = player.getUniqueId();
        payload.put("homes", List.of());
        payload.put("warps", List.of());
        payload.put("warpShown", 0);
        payload.put("targetShown", 0);
        payload.put("tpaRequestCount", 0);
        payload.put("favoritesOnly", String.valueOf(page.favoritesOnly()));
        payload.put("tpaRequests", List.of());
        payload.put("tpaReceiving", "未知");
        payload.put("onlinePlayers", List.of());
        payload.put("homeCount", "0");
        payload.put("warpCount", "0");
        payload.put("playerName", player.getName());
        payload.put("isFlying", String.valueOf(players.isFlyEnabled(id)));
        payload.put("isGod", String.valueOf(players.isGodEnabled(id)));
        payload.put("isAfk", String.valueOf(players.isAfk(id)));
        payload.put("flySpeed", String.format(Locale.ROOT, "%.1f", player.getFlySpeed() * 10));
        payload.put("walkSpeed", String.format(Locale.ROOT, "%.1f", player.getWalkSpeed() * 10));
        Location loc = player.getLocation();
        payload.put("location", loc.getWorld().getName() + " " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
        payload.put("replantEnabled", String.valueOf(inventory != null && inventory.isReplantEnabled(id)));
        payload.put("autotoolEnabled", String.valueOf(inventory != null && inventory.isAutoToolEnabled(id)));

        return switch (page.name()) {
            case "home" -> {
                boolean showHomes = allowed(player, "home") || allowed(player, "sethome") || allowed(player, "delhome");
                boolean showWarps = allowed(player, "warp");
                payload.put("warpCount", String.valueOf(showWarps ? teleport.warps().visible(player).size() : 0));
                yield showHomes ? read(() -> Map.of("homeCount", String.valueOf(repository.homeCount(id)))) : null;
            }
            case "homes" -> read(() -> {
                Map<String, Object> result = new java.util.HashMap<>();
                var homes = repository.listHomes(id, PAGE_SIZE + 1, page.offset()).stream().map(home -> Map.of(
                    "name", home.name(), "world", home.world(), "coords", home.x() + ", " + home.y() + ", " + home.z())).toList();
                pageItems(result, "homes", homes);
                return result;
            });
            case "warps" -> {
                var visible = teleport.warps().visible(player);
                yield read(() -> {
                    Map<String, Object> result = new java.util.HashMap<>();
                    var entries = pageSlice(teleport.warps().list(id, visible, page.query(), page.favoritesOnly()), page);
                    pageItems(result, "warps", entries.stream().map(warp -> Map.of(
                        "name", warp.name(), "label", (warp.favorite() ? "★ " : "") + warp.name() + " · " + warp.group())).toList());
                    result.put("warpShown", Math.min(entries.size(), PAGE_SIZE));
                    return result;
                });
            }
            case "tpa" -> {
                List<String> targets = Bukkit.getOnlinePlayers().stream()
                    .filter(target -> !target.equals(player) && players.canSee(player, target))
                    .map(Player::getName).sorted(String.CASE_INSENSITIVE_ORDER).toList();
                pageItems(payload, "onlinePlayers", pageSlice(targets, page));
                payload.put("targetShown", Math.min(Math.max(0, targets.size() - page.offset()), PAGE_SIZE));
                yield null;
            }
            case "tpa_requests" -> {
                var requests = teleport.tpaRequests(player).stream().map(request -> Map.of(
                    "sender", request.sender(),
                    "label", request.sender() + (request.here() ? " · 邀请你过去" : " · 请求传送到你") + " · " + request.seconds() + " 秒")).toList();
                pageItems(payload, "tpaRequests", pageSlice(requests, page));
                payload.put("tpaRequestCount", Math.min(Math.max(0, requests.size() - page.offset()), PAGE_SIZE));
                yield read(() -> Map.of("tpaReceiving", repository.receivesTpa(id) ? "开启" : "关闭"));
            }
            default -> null;
        };
    }
}
