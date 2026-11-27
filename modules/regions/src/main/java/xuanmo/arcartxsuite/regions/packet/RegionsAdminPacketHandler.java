package xuanmo.arcartxsuite.regions.packet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import xuanmo.arcartxsuite.api.ClientPacketHandler;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.security.ActionTokenGuard;
import xuanmo.arcartxsuite.regions.model.Region;
import xuanmo.arcartxsuite.regions.model.RegionFlag;
import xuanmo.arcartxsuite.regions.service.RegionManager;
import xuanmo.arcartxsuite.regions.service.RegionProfiles;

public final class RegionsAdminPacketHandler implements ClientPacketHandler {
    public static final String PACKET_ID = "AXS_REGIONS_ADMIN";
    public static final String UI_RESOURCE_PATH = "arcartx/ui/regions_admin.yml";
    public static final String UI_FILE_PATH = "ui/regions/regions_control.yml";

    private static final int PAGE_SIZE = 8;
    private final JavaPlugin plugin;
    private final PacketBridgeAPI packets;
    private final RegionManager manager;
    private final String uiId;
    private final ActionTokenGuard tokens = new ActionTokenGuard();
    private final Map<UUID, View> views = new HashMap<>();

    public RegionsAdminPacketHandler(JavaPlugin plugin, PacketBridgeAPI packets, RegionManager manager, String uiId) {
        this.plugin = plugin;
        this.packets = packets;
        this.manager = manager;
        this.uiId = uiId;
    }

    public void openMenu(Player player) {
        if (!player.hasPermission("axs.regions.admin") || !packets.openUi(player, uiId)) return;
        var view = new View();
        views.put(player.getUniqueId(), view);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (active(player, view)) initialize(player, view);
        }, 2L);
    }

    public void clearPlayer(UUID id) {
        views.remove(id);
        tokens.clear(id);
    }

    public void close() {
        views.clear();
        tokens.clearAll();
    }

    @Override
    public boolean handleClientPacket(@NotNull Player player, @NotNull String packetId, @NotNull List<String> data) {
        if (!PACKET_ID.equalsIgnoreCase(packetId)) return false;
        View view = views.get(player.getUniqueId());
        if (data.isEmpty() || !active(player, view)) return true;
        try {
            switch (data.get(0)) {
                case "ready" -> initialize(player, view);
                case "list" -> {
                    boolean worlds = switch (arg(data, 1)) {
                        case "worlds" -> true;
                        case "regions" -> false;
                        default -> throw new IllegalArgumentException("未知目录。");
                    };
                    if (view.worlds != worlds) {
                        view.worlds = worlds;
                        view.selected = "";
                        showDetail(player, view);
                    }
                    showList(player, view, page(data, 2));
                }
                case "select" -> {
                    String selected = arg(data, 1);
                    if (view.worlds ? !worldNames().contains(selected) : findRegion(selected) == null) {
                        throw new IllegalArgumentException("记录已变更，请刷新列表。");
                    }
                    view.selected = selected;
                    view.pane = "rules";
                    view.category = RegionFlag.Category.BLOCK;
                    view.contentPage = 0;
                    showDetail(player, view);
                    showContent(player, view);
                }
                case "rules" -> {
                    view.category = RegionFlag.Category.valueOf(arg(data, 1).toUpperCase(Locale.ROOT));
                    openPane(player, view, "rules", page(data, 2));
                }
                case "members" -> openPane(player, view, "members", page(data, 1));
                case "settings" -> openPane(player, view, "settings", 0);
                case "materials" -> openPane(player, view, "materials", page(data, 1));
                case "preview" -> {
                    Region previous = "new".equals(optional(data, 1)) ? null : selectedRegion(view);
                    Region area = manager.selectionRegion(player, "preview");
                    if (previous != null && !previous.world().equals(area.world())) {
                        throw new IllegalArgumentException("请在同一世界设置选区。");
                    }
                    notice(player, "选区包含 " + area.volume() + " 个方块，与 " + manager.overlaps(area, previous).size() + " 个区域重叠。");
                }
                case "create", "flag", "message", "add_member", "remove_member", "blocked_material",
                     "rename", "priority", "parent", "redefine", "delete" -> mutate(player, view, data);
                default -> { }
            }
        } catch (IllegalArgumentException error) {
            notice(player, error.getMessage());
        }
        return true;
    }

    private void initialize(Player player, View view) {
        if (view.initialized) return;
        view.initialized = true;
        credentials(player);
        showList(player, view, 0);
        showDetail(player, view);
    }

    private void showList(Player player, View view, int requested) {
        if (view.worlds) {
            var slice = slice(worldNames(), requested);
            view.listPage = slice.page();
            send(player, "list", Map.of("scope", "worlds", "page", view.listPage, "pages", slice.pages(),
                "rows", slice.items().stream().map(world -> Map.of("id", world, "title", world, "subtitle", "世界默认规则")).toList()));
        } else {
            List<Region> regions = manager.getBoundedRegions();
            var slice = slice(regions, requested);
            view.listPage = slice.page();
            send(player, "list", Map.of("scope", "regions", "page", view.listPage, "pages", slice.pages(),
                "rows", slice.items().stream().map(region -> Map.of(
                    "id", region.uid().toString(), "title", region.id(), "subtitle", region.world()
                        + (region.kind() == Region.Kind.CLAIM ? " · 玩家领地" : " · 管理区域"))).toList()));
        }
    }

    private List<String> worldNames() {
        var names = new TreeSet<String>();
        Bukkit.getWorlds().stream().map(World::getName).forEach(names::add);
        manager.getAllRegions().forEach(region -> names.add(region.world()));
        return List.copyOf(names);
    }

    private void showDetail(Player player, View view) {
        Region region = selectedRegion(view);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", view.selected);
        detail.put("world", view.worlds);
        detail.put("title", view.worlds ? view.selected : region == null ? "" : region.id());
        detail.put("subtitle", view.worlds ? "对该世界内未另设旗标的位置生效"
            : region == null ? "" : region.world() + "     " + region.minX() + ", " + region.minY() + ", " + region.minZ()
                + "  至  " + region.maxX() + ", " + region.maxY() + ", " + region.maxZ());
        detail.put("priority", region == null ? "" : Integer.toString(region.priority()));
        detail.put("parent", region == null || region.parentId() == null ? "" : region.parentId());
        send(player, "detail", detail);
    }

    private void openPane(Player player, View view, String pane, int requested) {
        if (view.selected.isEmpty()) return;
        if (view.worlds && ("members".equals(pane) || "settings".equals(pane))
                || !view.worlds && "materials".equals(pane)) return;
        view.pane = pane;
        view.contentPage = requested;
        showContent(player, view);
    }

    private void showContent(Player player, View view) {
        if (!view.worlds && selectedRegion(view) == null) {
            view.selected = "";
            showDetail(player, view);
            showList(player, view, view.listPage);
            return;
        }
        view.contentRequest++;
        switch (view.pane) {
            case "rules" -> showRules(player, view);
            case "members" -> showMembers(player, view);
            case "settings" -> {
                showDetail(player, view);
                send(player, "settings", Map.of("pane", "settings"));
            }
            case "materials" -> showMaterials(player, view);
            default -> throw new IllegalStateException("未知管理面板。");
        }
    }

    private void showRules(Player player, View view) {
        Region region = selectedRegion(view);
        var flags = java.util.Arrays.stream(RegionFlag.values()).filter(flag -> flag.category() == view.category).toList();
        var slice = slice(flags, view.contentPage);
        view.contentPage = slice.page();
        var rules = slice.items().stream().map(flag -> ruleRow(region, flag)).toList();
        send(player, "rules", Map.of("category", view.category.name().toLowerCase(Locale.ROOT),
            "page", view.contentPage, "pages", slice.pages(), "rows", rules));
    }

    private Map<String, Object> ruleRow(Region region, RegionFlag flag) {
        var state = region == null ? RegionFlag.State.NONE : region.getFlag(flag);
        var inherited = RegionFlag.State.NONE;
        if (state == RegionFlag.State.NONE && region != null && region.kind() != Region.Kind.GLOBAL) {
            inherited = manager.inheritedRule(region, flag);
        }
        String message = region == null ? null : region.getFlagData(flag);
        return Map.of("id", flag.configKey(), "title", flag.description(), "state", state.name().toLowerCase(Locale.ROOT),
            "hint", switch (state) {
                case ALLOW -> "已允许";
                case DENY -> "已禁止";
                case NONE -> switch (inherited) {
                    case ALLOW -> "继承 · 允许";
                    case DENY -> "继承 · 禁止";
                    case NONE -> "跟随默认";
                };
            }, "message", message == null ? "" : message,
            "custom", flag == RegionFlag.GREETING || flag == RegionFlag.FAREWELL);
    }

    private void showMembers(Player player, View view) {
        Region region = selectedRegion(view);
        var members = new ArrayList<Membership>();
        appendMembers(members, region.owners(), true);
        appendMembers(members, region.members(), false);
        appendGroups(members, region.ownerGroups(), true);
        appendGroups(members, region.memberGroups(), false);
        var slice = slice(members, view.contentPage);
        view.contentPage = slice.page();
        var unresolved = new ArrayList<UUID>();
        var known = new HashMap<UUID, String>();
        for (Membership member : slice.items()) {
            if (member.uuid() == null) continue;
            Player online = Bukkit.getPlayer(member.uuid());
            if (online == null) unresolved.add(member.uuid());
            else known.put(member.uuid(), online.getName());
        }
        send(player, "members", memberPage(slice, known, "正在读取名称…"));
        if (unresolved.isEmpty() || view.resolvingMembers) return;
        view.resolvingMembers = true;
        long request = view.contentRequest;
        RegionProfiles.background(plugin, () -> {
            var resolved = new HashMap<>(known);
            for (UUID id : unresolved) {
                String name = RegionProfiles.offlineName(id);
                if (name != null && !name.isBlank()) resolved.put(id, name);
            }
            return resolved;
        }).whenComplete((names, error) -> {
            view.resolvingMembers = false;
            if (!active(player, view) || !"members".equals(view.pane)) return;
            if (request != view.contentRequest) {
                if (selectedRegion(view) != null) showMembers(player, view);
            } else {
                send(player, "members", memberPage(slice, error == null ? names : known, "未知玩家"));
            }
        });
    }

    private static Map<String, Object> memberPage(Page<Membership> slice, Map<UUID, String> names, String fallback) {
        var entries = slice.items().stream().map(member -> Map.of(
            "id", member.id(), "title", member.uuid() == null ? member.id().substring(2)
                : names.getOrDefault(member.uuid(), fallback),
            "subtitle", (member.owner() ? "所有者" : "成员") + (member.uuid() == null ? "权限组" : ""),
            "role", member.owner() ? "owner" : "member")).toList();
        return Map.of("page", slice.page(), "pages", slice.pages(), "rows", entries);
    }

    private static void appendMembers(List<Membership> rows, Collection<UUID> members, boolean owner) {
        members.stream().sorted().forEach(id -> rows.add(new Membership(id.toString(), id, owner)));
    }

    private static void appendGroups(List<Membership> rows, Collection<String> groups, boolean owner) {
        groups.stream().sorted().forEach(group -> rows.add(new Membership("g:" + group, null, owner)));
    }

    private void showMaterials(Player player, View view) {
        Region global = manager.getGlobal(view.selected);
        var materials = global == null ? List.<String>of() : global.blockedInteractions().stream().map(Material::name).sorted().toList();
        var slice = slice(materials, view.contentPage);
        view.contentPage = slice.page();
        send(player, "materials", Map.of("page", slice.page(), "pages", slice.pages(),
            "rows", slice.items().stream().map(name -> Map.of("id", name, "title", name)).toList()));
    }

    private void mutate(Player player, View view, List<String> data) {
        UUID id = player.getUniqueId();
        if (!tokens.begin(id, uiId, arg(data, 1))) {
            if (view.writing) notice(player, "正在保存，请稍候。");
            return;
        }
        view.writing = true;
        String action = data.get(0);
        String selected = view.selected;
        boolean worlds = view.worlds;
        Region region = selectedRegion(view);
        CompletableFuture<? extends Region> future;
        try {
            if (!action.equals("create") && (!selected.equals(arg(data, 2)) || selected.isEmpty())) {
                throw new IllegalArgumentException("当前选中的记录已变更。");
            }
            future = switch (action) {
                case "create" -> manager.createRegion(player, arg(data, 2), copy -> {});
                case "flag", "message" -> {
                    RegionFlag flag = RegionFlag.fromKey(arg(data, 3));
                    if (flag == null) throw new IllegalArgumentException("未知规则。");
                    RegionFlag.State state = action.equals("message") ? RegionFlag.State.ALLOW : RegionFlag.State.parse(arg(data, 4));
                    String message = action.equals("message") ? arg(data, 4) : null;
                    yield worlds ? manager.editWorld(player, selected, copy -> flag.apply(copy, state, message))
                        : manager.setFlag(player, requireRegion(region), flag, state, message);
                }
                case "add_member", "remove_member" -> membership(player, view, requireRegion(region), data);
                case "blocked_material" -> {
                    if (!worlds) throw new IllegalArgumentException("请选择一个世界。");
                    Material material = Material.matchMaterial(arg(data, 3));
                    if (material == null || !material.isBlock()) throw new IllegalArgumentException("请输入有效的方块材质名称。");
                    boolean blocked = switch (arg(data, 4)) {
                        case "deny" -> true;
                        case "allow" -> false;
                        default -> throw new IllegalArgumentException("交互状态无效。");
                    };
                    yield manager.editWorld(player, selected, copy -> copy.setBlockedInteraction(material, blocked));
                }
                case "rename" -> manager.renameRegion(player, requireBounded(view, region), arg(data, 3));
                case "priority" -> manager.update(player, requireBounded(view, region), "priority",
                    copy -> copy.setPriority(Integer.parseInt(arg(data, 3))));
                case "parent" -> manager.setParent(player, requireBounded(view, region), optional(data, 3));
                case "redefine" -> manager.redefine(player, requireBounded(view, region));
                case "delete" -> manager.deleteRegion(player, requireBounded(view, region)).thenApply(ignored -> null);
                default -> throw new IllegalArgumentException("未知保存操作。");
            };
        } catch (RuntimeException error) {
            future = CompletableFuture.failedFuture(error);
        }
        future.whenComplete((updated, error) -> {
            view.writing = false;
            if (views.get(id) != view) {
                // 重新打开面板时，旧请求仍占用槽位，退出重连后则已由 quit 清理。
                if (Bukkit.getPlayer(id) == player) tokens.finish(id);
                return;
            }
            tokens.finish(id);
            if (!active(player, view)) return;
            credentials(player);
            if (error != null) {
                Throwable cause = error instanceof CompletionException ? error.getCause() : error;
                if (cause instanceof IllegalArgumentException || cause instanceof IllegalStateException) {
                    notice(player, cause.getMessage());
                } else {
                    plugin.getLogger().log(Level.WARNING, "区域设置保存失败。", cause);
                    notice(player, "保存失败，请查看服务端日志。");
                }
                return;
            }
            notice(player, "已保存");
            if (view.worlds != worlds || !view.selected.equals(selected)) return;
            switch (action) {
                case "create", "rename", "delete" -> {
                    view.worlds = false;
                    view.selected = updated == null ? "" : updated.uid().toString();
                    showList(player, view, view.listPage);
                    showDetail(player, view);
                    if (updated != null) showContent(player, view);
                }
                case "priority", "parent", "redefine" -> showDetail(player, view);
                case "flag", "message" -> {
                    if ("rules".equals(view.pane)) showRules(player, view);
                }
                case "add_member", "remove_member" -> {
                    if ("members".equals(view.pane)) showContent(player, view);
                }
                case "blocked_material" -> {
                    if ("materials".equals(view.pane)) showMaterials(player, view);
                }
                default -> { }
            }
        });
    }

    private CompletableFuture<Region> membership(Player player, View view, Region region, List<String> data) {
        requireBounded(view, region);
        boolean add = "add_member".equals(data.get(0));
        String target = arg(data, 3);
        boolean owner = switch (arg(data, 4)) {
            case "owner" -> true;
            case "member" -> false;
            default -> throw new IllegalArgumentException("成员身份无效。");
        };
        String group = target.startsWith("g:") ? target.substring(2).toLowerCase(Locale.ROOT) : null;
        if (group != null && !group.matches("[a-z0-9_-]{1,64}")) throw new IllegalArgumentException("权限组名称无效。");
        var resolved = group != null ? CompletableFuture.<UUID>completedFuture(null)
            : add ? RegionProfiles.resolve(plugin, target) : CompletableFuture.completedFuture(UUID.fromString(target));
        return resolved.thenCompose(uuid -> {
            if (!active(player, view)) throw new IllegalArgumentException("管理面板已关闭。");
            return manager.update(player, region, data.get(0), copy -> {
                if (group != null) {
                    var groups = owner ? copy.ownerGroups() : copy.memberGroups();
                    if (add) groups.add(group);
                    else groups.remove(group);
                } else {
                    var members = owner ? copy.owners() : copy.members();
                    if (add) members.add(uuid);
                    else members.remove(uuid);
                }
            });
        });
    }

    private Region selectedRegion(View view) {
        return view.worlds ? manager.getGlobal(view.selected) : findRegion(view.selected);
    }

    private Region findRegion(String key) {
        if (key.isEmpty()) return null;
        Region region = manager.getRegion(UUID.fromString(key));
        return region != null && region.kind() != Region.Kind.GLOBAL ? region : null;
    }

    private static Region requireBounded(View view, Region region) {
        if (view.worlds) throw new IllegalArgumentException("请选择一个有界区域。");
        return requireRegion(region);
    }

    private static Region requireRegion(Region region) {
        if (region == null) throw new IllegalArgumentException("区域不存在，请刷新列表。");
        return region;
    }

    private boolean active(Player player, View view) {
        return view != null && views.get(player.getUniqueId()) == view && player.isOnline()
            && player.hasPermission("axs.regions.admin") && packets.isUiOpen(player, uiId);
    }

    private void credentials(Player player) {
        send(player, "credentials", Map.of("token", tokens.rotate(player.getUniqueId(), uiId)));
    }

    private void notice(Player player, String message) {
        send(player, "notice", Map.of("text", message));
    }

    private void send(Player player, String channel, Map<String, Object> payload) {
        packets.sendPacket(player, uiId, channel, payload);
    }

    private static String arg(List<String> data, int index) {
        String value = optional(data, index);
        if (value.isBlank()) throw new IllegalArgumentException("请填写必填项。");
        return value;
    }

    private static String optional(List<String> data, int index) {
        return data.size() > index && data.get(index) != null ? data.get(index).strip() : "";
    }

    private static int page(List<String> data, int index) {
        String value = optional(data, index);
        return value.isEmpty() ? 0 : Integer.parseInt(value);
    }

    static <T> Page<T> slice(List<T> items, int requested) {
        int pages = Math.max(1, (items.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requested, pages - 1));
        int start = page * PAGE_SIZE;
        return new Page<>(items.subList(start, Math.min(start + PAGE_SIZE, items.size())), page, pages);
    }

    record Page<T>(List<T> items, int page, int pages) {}
    private record Membership(String id, UUID uuid, boolean owner) {}
    private static final class View {
        boolean initialized;
        boolean worlds;
        boolean writing;
        boolean resolvingMembers;
        String selected = "";
        String pane = "rules";
        int listPage;
        int contentPage;
        long contentRequest;
        RegionFlag.Category category = RegionFlag.Category.BLOCK;
    }
}
