package xuanmo.arcartxsuite.regions.packet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import xuanmo.arcartxsuite.api.ClientPacketHandler;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.security.ActionTokenGuard;
import xuanmo.arcartxsuite.regions.model.Region;
import xuanmo.arcartxsuite.regions.model.RegionFlag;
import xuanmo.arcartxsuite.regions.config.RegionsConfiguration;
import xuanmo.arcartxsuite.regions.service.RegionManager;
import xuanmo.arcartxsuite.regions.service.RegionMemberships;

public final class RegionsMenuPacketHandler implements ClientPacketHandler {
    public static final String PACKET_ID = "AXS_REGIONS_MENU";
    public static final String UI_RESOURCE_PATH = "arcartx/ui/regions_menu.yml";
    public static final String UI_FILE_PATH = "ui/regions/regions_view.yml";

    private final JavaPlugin plugin;
    private final PacketBridgeAPI packets;
    private final RegionManager manager;
    private final RegionsConfiguration configuration;
    private final String uiId;
    private final ActionTokenGuard tokens = new ActionTokenGuard();
    private final Map<UUID, RegionMemberships.View> ownedViews = new HashMap<>();

    public RegionsMenuPacketHandler(JavaPlugin plugin, PacketBridgeAPI packets, RegionManager manager,
                                    RegionsConfiguration configuration, String uiId) {
        this.plugin = plugin;
        this.packets = packets;
        this.manager = manager;
        this.configuration = configuration;
        this.uiId = uiId;
    }

    public void openMenu(Player player) {
        if (!canView(player) || !packets.openUi(player, uiId)) return;
        ownedViews.remove(player.getUniqueId());
        String token = tokens.issue(player.getUniqueId(), uiId);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (isOpen(player) && tokens.verify(player.getUniqueId(), uiId, token)) showCurrent(player, 0);
        }, 2L);
    }

    public void clearPlayer(UUID id) {
        tokens.clear(id);
        ownedViews.remove(id);
    }

    public void close() {
        tokens.clearAll();
        ownedViews.clear();
    }

    @Override
    public boolean handleClientPacket(@NotNull Player player, @NotNull String packetId, @NotNull List<String> data) {
        if (!PACKET_ID.equalsIgnoreCase(packetId)) return false;
        if (data.isEmpty() || !isOpen(player)) return true;
        try {
            switch (data.get(0)) {
                case "current" -> showCurrent(player, page(data, 1));
                case "owned" -> showOwned(player, page(data, 1));
                case "detail" -> {
                    if (data.size() < 2) return true;
                    Region region = manager.getRegion(UUID.fromString(data.get(1)));
                    Location location = player.getLocation();
                    if (region != null && (manager.hasAccess(region, player)
                            || region.kind() == Region.Kind.GLOBAL && region.world().equals(location.getWorld().getName())
                            || region.contains(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ()))) {
                        showRegion(player, region, page(data, 2));
                    }
                }
                case "selection" -> showSelection(player);
                case "clear" -> {
                    var id = player.getUniqueId();
                    if (data.size() < 2 || !tokens.begin(id, uiId, data.get(1))) return true;
                    try {
                        manager.clearSelection(id);
                        showSelection(player);
                    } finally {
                        tokens.finish(id);
                    }
                }
                default -> { }
            }
        } catch (IllegalArgumentException error) {
            player.sendMessage("§c区域菜单参数无效。");
        }
        return true;
    }

    private void showCurrent(Player player, int requested) {
        Location location = player.getLocation();
        var at = manager.rulesAt(location);
        List<Map<String, String>> entries = new ArrayList<>();
        for (RegionFlag flag : RegionFlag.values()) {
            // 未配置的旗标无需展示，即使玩家具备绕过权限。
            if (at.decide(flag, null).state() == RegionFlag.State.NONE) continue;
            var decision = switch (flag) {
                case ENDERPEARL, CHORUS_FRUIT -> at.travel(flag, player);
                case TELEPORT -> at.travel(null, player);
                default -> at.decide(flag, player);
            };
            String source = switch (decision.reason()) {
                case BYPASS -> "权限绕过";
                case MEMBER_EXEMPT -> "成员豁免";
                default -> decision.source() == null ? "" : decision.source().kind() == Region.Kind.GLOBAL
                    ? "世界默认" : decision.source().id();
            };
            entries.add(Map.of("title", flag.description(), "state", stateLabel(decision.state()), "source", source));
        }
        var regions = at.regions();
        Region first = regions.isEmpty() ? null : regions.get(0);
        String world = location.getWorld().getName();
        String title = first == null ? world : world + " / " + first.id();
        String subtitle = "当前位置的生效规则 · 操作还可能检查目标位置和材质";
        sendRules(player, first, requested, "current", title, subtitle, entries);
    }

    private void showRegion(Player player, Region region, int requested) {
        var entries = new ArrayList<Map<String, String>>();
        for (RegionFlag flag : RegionFlag.values()) {
            var state = region.getFlag(flag);
            boolean inherited = state == RegionFlag.State.NONE;
            if (inherited) state = manager.inheritedRule(region, flag);
            if (state == RegionFlag.State.NONE) continue;
            entries.add(Map.of("title", flag.description(), "state", stateLabel(state), "source", inherited ? "继承" : "本区域"));
        }
        String subtitle = "区域设置 · " + region.minX() + ", " + region.minY() + ", " + region.minZ()
            + "  至  " + region.maxX() + ", " + region.maxY() + ", " + region.maxZ();
        sendRules(player, region, requested, "detail", region.world() + " / " + region.id(), subtitle, entries);
    }

    private static String stateLabel(RegionFlag.State state) {
        return switch (state) {
            case ALLOW -> "允许";
            case DENY -> "禁止";
            case NONE -> "默认";
        };
    }

    private void sendRules(Player player, Region region, int requested, String view, String title, String subtitle,
                           List<Map<String, String>> entries) {
        var page = RegionsAdminPacketHandler.slice(entries, requested);
        send(player, "region", Map.of("title", title, "subtitle", subtitle,
            "id", region == null ? "" : region.uid().toString(), "page", page.page(), "pages", page.pages(),
            "rows", page.items(), "view", view));
    }

    private void showOwned(Player player, int requested) {
        UUID id = player.getUniqueId();
        var view = manager.membershipIndex().view(player, ownedViews.get(id));
        ownedViews.put(id, view);

        var page = RegionsAdminPacketHandler.slice(view.memberships(), requested);
        send(player, "owned", Map.of("page", page.page(), "pages", page.pages(), "rows",
            page.items().stream().map(entry -> Map.of(
                "id", entry.region().uid().toString(), "title", entry.region().id(), "subtitle", entry.region().world(),
                "role", entry.owner() ? "所有者" : "成员")).toList()));
    }

    private void showSelection(Player player) {
        var selection = manager.getSelection(player.getUniqueId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", tokens.rotate(player.getUniqueId(), uiId));
        data.put("world", selection.world() == null ? player.getWorld().getName() : selection.world());
        data.put("first", selection.isPos1Set() ? selection.x1() + ", " + selection.y1() + ", " + selection.z1() : "未设置");
        data.put("second", selection.isPos2Set() ? selection.x2() + ", " + selection.y2() + ", " + selection.z2() : "未设置");
        data.put("volume", Long.toString(selection.volume()));
        data.put("limit", Long.toString(configuration.selection().maxVolume()));
        send(player, "selection", data);
    }

    private void send(Player player, String channel, Map<String, Object> data) {
        packets.sendPacket(player, uiId, channel, data);
    }

    private boolean isOpen(Player player) {
        return player.isOnline() && canView(player) && packets.isUiOpen(player, uiId);
    }

    private static boolean canView(Player player) {
        return player.hasPermission("axs.regions.select") || player.hasPermission("arcartxsuite.admin");
    }

    private static int page(List<String> data, int index) {
        return data.size() > index ? Integer.parseInt(data.get(index)) : 0;
    }
}
