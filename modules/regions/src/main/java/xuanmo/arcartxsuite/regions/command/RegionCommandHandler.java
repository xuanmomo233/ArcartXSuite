package xuanmo.arcartxsuite.regions.command;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.regions.config.RegionsConfiguration;
import xuanmo.arcartxsuite.regions.config.RegionTemplates;
import xuanmo.arcartxsuite.regions.model.Region;
import xuanmo.arcartxsuite.regions.model.RegionFlag;
import xuanmo.arcartxsuite.regions.model.Selection;
import xuanmo.arcartxsuite.regions.service.RegionManager;
import xuanmo.arcartxsuite.regions.service.RegionProfiles;
import xuanmo.arcartxsuite.regions.service.RegionBackups;
import xuanmo.arcartxsuite.regions.service.SelectionParticleTask;
import xuanmo.arcartxsuite.regions.storage.RegionSnapshots;

/**
 * 区域管理命令处理器。
 * 命令格式: /axs regions <action> [args...]
 */
public final class RegionCommandHandler implements ModuleCommandHandler {

    private static final List<String> ACTIONS = List.of(
        "help", "status",
        "define", "remove", "redefine", "rename",
        "list", "info", "pos1", "pos2", "wand", "show",
        "flag", "removeflag", "flags",
        "addowner", "removeowner", "addmember", "removemember",
        "priority", "parent", "tp", "classify", "worlddefault", "why", "preview", "template",
        "history", "diff", "undo", "export", "import", "profile"
    );

    private final RegionManager manager;
    private final RegionsConfiguration config;
    private final MessageProvider messages;
    private final SelectionParticleTask particleTask;
    private final RegionTemplates templates;
    private final RegionBackups backups;
    private final Plugin plugin;

    public RegionCommandHandler(RegionManager manager, RegionsConfiguration config, MessageProvider messages,
                                SelectionParticleTask particleTask, RegionTemplates templates, RegionBackups backups,
                                Plugin plugin) {
        this.manager = manager;
        this.config = config;
        this.messages = messages;
        this.particleTask = particleTask;
        this.templates = templates;
        this.backups = backups;
        this.plugin = plugin;
    }

    private String fullMsg(String key, Object... args) {
        if (messages == null) return "";
        return messages.get("prefix") + messages.get(key, args);
    }

    @Override public String commandId() { return "regions"; }
    @Override public List<String> actions() { return ACTIONS; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        String action = args.length < 2 ? "help" : args[1].toLowerCase(Locale.ROOT);
        try {
            switch (action) {
                case "help" -> sendHelp(sender, label);
                case "status" -> sendStatus(sender);
                case "define" -> handleDefine(sender, args);
                case "remove" -> handleRemove(sender, args);
                case "redefine" -> handleRedefine(sender, args);
                case "list" -> handleList(sender, args);
                case "info" -> handleInfo(sender, args);
                case "pos1" -> handlePos(sender, 1);
                case "pos2" -> handlePos(sender, 2);
                case "wand" -> handleWand(sender);
                case "show" -> handleShow(sender);
                case "flag" -> handleFlag(sender, args);
                case "removeflag" -> handleRemoveFlag(sender, args);
                case "flags" -> handleFlags(sender, args);
                case "addowner" -> handleAddMember(sender, args, "owner");
                case "removeowner" -> handleRemoveMember(sender, args, "owner");
                case "addmember" -> handleAddMember(sender, args, "member");
                case "removemember" -> handleRemoveMember(sender, args, "member");
                case "priority" -> handlePriority(sender, args);
                case "parent" -> handleParent(sender, args);
                case "tp" -> handleTeleport(sender, args);
                case "rename" -> handleRename(sender, args);
                case "classify" -> handleClassify(sender, args);
                case "worlddefault" -> handleWorldDefault(sender, args);
                case "why" -> handleWhy(sender, args);
                case "preview" -> handlePreview(sender, args);
                case "template" -> handleTemplate(sender, args);
                case "history" -> handleHistory(sender, args);
                case "diff" -> handleDiff(sender, args);
                case "undo" -> handleUndo(sender, args);
                case "export" -> handleExport(sender, args);
                case "import" -> handleImport(sender, args);
                case "profile" -> handleProfile(sender, args);
                default -> sender.sendMessage(fullMsg("common.unknown", label));
            }
        } catch (IllegalArgumentException e) {
            sender.sendMessage(prefix() + ChatColor.RED + e.getMessage());
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 2) return filter(ACTIONS, args[1]);
        if (args.length == 3) {
            return switch (args[1].toLowerCase()) {
                case "remove", "redefine", "rename", "classify", "info", "flag", "removeflag", "flags", "preview",
                     "addowner", "removeowner", "addmember", "removemember",
                     "priority", "parent", "tp" -> filterRegions(args[2]);
                case "why" -> filterFlags(args[2]);
                case "template" -> filter(List.of("list", "show", "apply"), args[2]);
                case "profile" -> RegionManager.isAdmin(sender) ? filter(List.of("start", "stop"), args[2]) : List.of();
                default -> List.of();
            };
        }
        if (args.length == 4 && ("flag".equalsIgnoreCase(args[1]) || "removeflag".equalsIgnoreCase(args[1]))) {
            return filterFlags(args[3]);
        }
        if (args.length == 4 && "why".equalsIgnoreCase(args[1]) && RegionManager.isAdmin(sender)) {
            return filterOnline(args[3]);
        }
        if (args.length == 4 && ("template".equalsIgnoreCase(args[1]) || "define".equalsIgnoreCase(args[1]))) {
            return filter(templates.names().stream().sorted().toList(), args[3]);
        }
        if (args.length == 5 && "flag".equalsIgnoreCase(args[1])) {
            return filter(List.of("allow", "deny", "none"), args[4]);
        }
        if (args.length == 4 && ("addowner".equalsIgnoreCase(args[1]) || "addmember".equalsIgnoreCase(args[1])
            || "removeowner".equalsIgnoreCase(args[1]) || "removemember".equalsIgnoreCase(args[1]))) {
            return filterOnline(args[3]);
        }
        return List.of();
    }

    // ─── 命令实现 ───

    private void sendHelp(CommandSender sender, String label) {
        String cmd = label.equalsIgnoreCase("rg") || label.equalsIgnoreCase("axsrg") ? "/" + label : "/" + label + " regions";
        sender.sendMessage(fullMsg("help.title"));
        sender.sendMessage(fullMsg("help.status", cmd));
        sender.sendMessage(fullMsg("help.define", cmd));
        sender.sendMessage(fullMsg("help.remove", cmd));
        sender.sendMessage(fullMsg("help.redefine", cmd));
        sender.sendMessage(fullMsg("help.list", cmd));
        sender.sendMessage(fullMsg("help.info", cmd));
        sender.sendMessage(fullMsg("help.flag", cmd));
        sender.sendMessage(fullMsg("help.removeflag", cmd));
        sender.sendMessage(fullMsg("help.flags", cmd));
        sender.sendMessage(fullMsg("help.addowner", cmd));
        sender.sendMessage(fullMsg("help.removeowner", cmd));
        sender.sendMessage(fullMsg("help.priority", cmd));
        sender.sendMessage(fullMsg("help.parent", cmd));
        sender.sendMessage(fullMsg("help.tp", cmd));
        sender.sendMessage(ChatColor.YELLOW + cmd + " why <标志> [玩家]  查看当前位置的规则来源");
        sender.sendMessage(ChatColor.YELLOW + cmd + " preview [区域]  检查选区与现有区域的重叠");
        sender.sendMessage(ChatColor.YELLOW + cmd + " template list|show|apply  查看或应用区域模板");
        if (RegionManager.isAdmin(sender)) {
            sender.sendMessage(ChatColor.YELLOW + cmd + " history|diff|undo  查看或撤销区域变更");
            sender.sendMessage(ChatColor.YELLOW + cmd + " export|import <快照名>  导出或预检区域快照");
            sender.sendMessage(ChatColor.YELLOW + cmd + " profile <start|stop>  采集区域查询与边界回调耗时");
        }
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(fullMsg("status.title"));
        sender.sendMessage(fullMsg("status.loaded", manager.getAllRegions().size()));
        sender.sendMessage(fullMsg("status.flags", RegionFlag.values().length));
    }

    private void handleDefine(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage(fullMsg("common.only-player")); return; }
        if (args.length < 3) { sender.sendMessage(fullMsg("admin.define.usage")); return; }
        var settings = args.length >= 4 ? templates.get(args[3], RegionManager.isAdmin(sender))
            : Map.<RegionFlag, RegionTemplates.Setting>of();
        complete(sender, manager.createRegion(player, args[2], copy -> RegionTemplates.apply(copy, settings)),
            region -> sender.sendMessage(prefix() + fmtMsg(config.messages().regionCreated().replace("{name}", region.key()))));
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage(fullMsg("admin.remove.usage")); return; }
        Region region = requireRegion(sender, args[2]);
        complete(sender, manager.deleteRegion(sender, region),
            ignored -> sender.sendMessage(prefix() + fmtMsg(config.messages().regionDeleted().replace("{name}", region.key()))));
    }

    private void handleRedefine(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage(fullMsg("common.only-player")); return; }
        if (args.length < 3) { sender.sendMessage(fullMsg("admin.redefine.usage")); return; }
        Region region = requireRegion(sender, args[2]);
        complete(sender, manager.redefine(player, region),
            updated -> sender.sendMessage(fullMsg("admin.redefine.success", updated.key())));
    }

    private void handleRename(CommandSender sender, String[] args) {
        if (args.length < 4) throw new IllegalArgumentException("用法 /rg rename <区域> <新ID>");
        complete(sender, manager.renameRegion(sender, requireRegion(sender, args[2]), args[3]),
            renamed -> sender.sendMessage(prefix() + ChatColor.GREEN + "区域已重命名为 " + renamed.key() + "。"));
    }

    private void handleClassify(CommandSender sender, String[] args) {
        if (args.length < 4) throw new IllegalArgumentException("用法 /rg classify <区域> <managed|claim>");
        Region region = requireRegion(sender, args[2]);
        Region.Kind kind = switch (args[3].toLowerCase(Locale.ROOT)) {
            case "managed" -> Region.Kind.MANAGED;
            case "claim" -> Region.Kind.CLAIM;
            default -> throw new IllegalArgumentException("类型须为 managed 或 claim。");
        };
        complete(sender, manager.classify(sender, region, kind),
            updated -> sender.sendMessage(prefix() + ChatColor.GREEN + updated.key() + " 已设为 " + args[3] + "。"));
    }

    private void handleWorldDefault(CommandSender sender, String[] args) {
        if (args.length < 3) throw new IllegalArgumentException("用法 /rg worlddefault <世界>");
        if (Bukkit.getWorld(args[2]) == null && manager.getRegionsInWorld(args[2]).isEmpty()) {
            throw new IllegalArgumentException("未知世界 " + args[2] + "。");
        }
        complete(sender, manager.createGlobal(sender, args[2]), global -> sender.sendMessage(prefix()
            + ChatColor.GREEN + "世界默认区域 " + global.key() + " 已就绪。使用 /rg flag 设置规则。"));
    }

    private void handleWhy(CommandSender sender, String[] args) {
        if (args.length < 3) throw new IllegalArgumentException("用法 /rg why <标志> [玩家]");
        RegionFlag flag = requireFlag(args[2]);
        Player target;
        if (args.length >= 4) {
            if (!RegionManager.isAdmin(sender)) throw new IllegalArgumentException("只有管理员可以查询其他玩家。");
            target = Bukkit.getPlayerExact(args[3]);
        } else {
            target = sender instanceof Player player ? player : null;
        }
        if (target == null) throw new IllegalArgumentException("请指定一位在线玩家。");
        var location = target.getLocation();
        String world = location.getWorld().getName();
        int x = location.getBlockX(), y = location.getBlockY(), z = location.getBlockZ();
        var decision = manager.decide(world, x, y, z, flag, target);
        String reason = switch (decision.reason()) {
            case UNSET -> "无显式规则";
            case BYPASS -> "管理员绕过";
            case MEMBER_EXEMPT -> "成员豁免";
            case DIRECT -> "区域规则";
            case INHERITED -> "继承规则";
        };
        sender.sendMessage(prefix() + flag.configKey() + " " + decision.state().name().toLowerCase(Locale.ROOT)
            + " · " + reason + " · " + target.getName() + " @ " + world + " " + x + "," + y + "," + z);
        if (decision.source() != null) {
            sender.sendMessage("命中 " + decision.matched().key() + "，规则来自 " + decision.source().key());
        }
        List<Region> candidates = manager.getRegionsAt(world, x, y, z);
        Region global = manager.getGlobal(world);
        if (global != null) candidates.add(global);
        if (candidates.isEmpty()) sender.sendMessage("当前位置没有区域规则。");
        for (Region candidate : candidates.stream().limit(12).toList()) {
            var rule = manager.regionRule(candidate, flag);
            String source = rule.source() != null && rule.source() != candidate ? " ← " + rule.source().key() : "";
            sender.sendMessage("  " + candidate.key() + " [" + candidate.kind() + " P" + candidate.priority() + "] "
                + rule.state().name().toLowerCase(Locale.ROOT) + source);
        }
        if (candidates.size() > 12) sender.sendMessage("还有 " + (candidates.size() - 12) + " 个候选区域。");
    }

    private void handlePreview(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) throw new IllegalArgumentException("请在游戏内设置选区。");
        Region existing = args.length >= 3 ? requireRegion(sender, args[2]) : null;
        if (existing != null && !manager.canManage(sender, existing)) {
            throw new IllegalArgumentException("你没有管理此区域的权限。");
        }
        Region area = manager.selectionRegion(player, existing != null ? existing.id() : "preview");
        if (existing != null && (existing.kind() == Region.Kind.GLOBAL || !area.world().equals(existing.world()))) {
            throw new IllegalArgumentException("请选择与区域处于同一世界的有效选区。");
        }
        List<Region> overlaps = manager.overlaps(area, existing);
        sender.sendMessage(prefix() + "选区位于 " + area.world() + "，体积 " + area.volume() + " 方块，"
            + "重叠 " + overlaps.size() + " 个区域。");
        for (Region overlap : overlaps.stream().limit(12).toList()) {
            sender.sendMessage("  " + overlap.key() + " [" + overlap.kind() + " P" + overlap.priority() + "]");
        }
        if (!overlaps.isEmpty() && !RegionManager.isAdmin(sender)) {
            sender.sendMessage(ChatColor.RED + "请缩小或移动选区，玩家领地不能与其他区域重叠。");
        }
    }

    private void handleTemplate(CommandSender sender, String[] args) {
        String action = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "list";
        if (action.equals("list")) {
            sender.sendMessage(prefix() + "可用模板 " + String.join("、", templates.names().stream().sorted().toList()));
            return;
        }
        if (args.length < 4 || !List.of("show", "apply").contains(action)) {
            throw new IllegalArgumentException("用法 /rg template show <模板> 或 /rg template apply <模板> <区域>");
        }
        String name = args[3];
        var settings = templates.get(name, RegionManager.isAdmin(sender));
        if (action.equals("show")) {
            sender.sendMessage(prefix() + "模板 " + name + " 会设置以下标志。");
            settings.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                sender.sendMessage("  " + entry.getKey().configKey() + " " + entry.getValue().state().name().toLowerCase(Locale.ROOT)));
        } else {
            if (args.length < 5) throw new IllegalArgumentException("用法 /rg template apply <模板> <区域>");
            Region region = requireRegion(sender, args[4]);
            complete(sender, manager.update(sender, region, "template:" + name, copy -> RegionTemplates.apply(copy, settings)),
                updated -> sender.sendMessage(prefix() + ChatColor.GREEN + "已应用模板 " + name + " 到 " + updated.key() + "。"));
        }
    }

    private void handleHistory(CommandSender sender, String[] args) {
        int limit = args.length >= 3 ? Integer.parseInt(args[2]) : 10;
        var entries = manager.history(sender, limit);
        sender.sendMessage(prefix() + "最近 " + entries.size() + " 条区域变更。使用 /rg diff <编号> 查看内容。");
        for (var entry : entries) {
            sender.sendMessage("#" + entry.id() + " " + Instant.ofEpochMilli(entry.createdAt())
                + " " + entry.actor() + " " + entry.action());
        }
    }

    private void handleDiff(CommandSender sender, String[] args) {
        var entry = manager.audit(sender, auditId(args, "diff"));
        sender.sendMessage(prefix() + "#" + entry.summary().id() + " " + entry.summary().actor() + " " + entry.summary().action());
        Map<UUID, Region> before = new java.util.LinkedHashMap<>();
        Map<UUID, Region> after = new java.util.LinkedHashMap<>();
        entry.before().forEach(region -> before.put(region.uid(), region));
        entry.after().forEach(region -> after.put(region.uid(), region));
        var affected = new java.util.LinkedHashSet<>(before.keySet());
        affected.addAll(after.keySet());
        for (UUID id : affected.stream().limit(20).toList()) {
            Region old = before.get(id);
            Region updated = after.get(id);
            if (old == null || updated == null) {
                sender.sendMessage((old == null ? "+ " : "- ") + (old == null ? updated.key() : old.key()));
                continue;
            }
            sender.sendMessage("~ " + old.key());
            var oldFields = RegionSnapshots.fields(old);
            var newFields = RegionSnapshots.fields(updated);
            var fields = new java.util.LinkedHashSet<>(oldFields.keySet());
            fields.addAll(newFields.keySet());
            for (String field : fields) {
                Object a = oldFields.get(field);
                Object b = newFields.get(field);
                if (!java.util.Objects.equals(a, b)) sender.sendMessage("  " + field + " " + brief(a) + " → " + brief(b));
            }
        }
        if (affected.size() > 20) sender.sendMessage("还有 " + (affected.size() - 20) + " 个受影响区域。");
    }

    private static String brief(Object value) {
        String text = value == null ? "无" : value.toString().replace('\n', ' ');
        return text.length() > 160 ? text.substring(0, 160) + "…" : text;
    }

    private void handleUndo(CommandSender sender, String[] args) {
        long id = auditId(args, "undo");
        complete(sender, manager.undo(sender, id),
            ignored -> sender.sendMessage(prefix() + ChatColor.GREEN + "已撤销区域变更 #" + id + "。"));
    }

    private static long auditId(String[] args, String action) {
        if (args.length != 3) throw new IllegalArgumentException("用法 /rg " + action + " <编号>");
        try {
            return Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("变更编号须为整数。");
        }
    }

    private void handleExport(CommandSender sender, String[] args) {
        requireAdmin(sender);
        if (args.length < 3 || args.length > 4) throw new IllegalArgumentException("用法 /rg export <快照名> [世界]");
        List<Region> regions = args.length == 4 ? manager.getRegionsInWorld(args[3]) : manager.getAllRegions();
        if (regions.isEmpty()) throw new IllegalArgumentException("没有可导出的区域。");
        complete(sender, RegionProfiles.background(plugin, () -> backups.exportRegions(args[2], regions)),
            path -> sender.sendMessage(prefix() + "已导出 " + regions.size() + " 个区域到 region-backups/" + path.getFileName()));
    }

    private void handleImport(CommandSender sender, String[] args) {
        requireAdmin(sender);
        boolean apply = args.length == 5 && "apply".equalsIgnoreCase(args[3]);
        if (args.length != 3 && !apply) throw new IllegalArgumentException("用法 /rg import <快照名> [apply <校验码>]");
        complete(sender, RegionProfiles.background(plugin, () -> backups.load(args[2])),
            snapshot -> reviewImport(sender, args, apply, snapshot));
    }

    private void reviewImport(CommandSender sender, String[] args, boolean apply, RegionBackups.Snapshot snapshot) {
        if (apply && !snapshot.checksum().equalsIgnoreCase(args[4])) {
            throw new IllegalArgumentException("快照内容已变化或校验码不匹配，请重新预检。");
        }
        var review = manager.reviewImport(snapshot.regions());
        sender.sendMessage(prefix() + "新增 " + review.additions().size() + "，已相同 " + review.unchanged()
            + "，错误 " + review.errors().size() + "，重叠提示 " + review.warnings().size() + "。");
        review.errors().stream().limit(10).forEach(error -> sender.sendMessage(ChatColor.RED + error));
        review.warnings().stream().limit(10).forEach(warning -> sender.sendMessage(ChatColor.YELLOW + warning));
        if (!review.errors().isEmpty() || review.additions().isEmpty()) return;
        if (apply) {
            complete(sender, manager.importRegions(sender, snapshot.regions()), count ->
                sender.sendMessage(prefix() + ChatColor.GREEN + "已导入 " + count + " 个区域，可通过 /rg history 查看变更。"));
        } else {
            sender.sendMessage("应用命令 /rg import " + args[2] + " apply " + snapshot.checksum());
        }
    }

    private static void requireAdmin(CommandSender sender) {
        if (!RegionManager.isAdmin(sender)) throw new IllegalArgumentException("此操作需要区域管理员权限。");
    }

    private void handleList(CommandSender sender, String[] args) {
        String world = args.length >= 3 ? args[2] : (sender instanceof Player p ? p.getWorld().getName() : null);
        List<Region> list = world != null ? manager.getRegionsInWorld(world) : manager.getAllRegions();
        if (list.isEmpty()) {
            sender.sendMessage(fullMsg("admin.list.empty"));
            return;
        }
        sender.sendMessage(fullMsg("admin.list.title", (world != null ? world : "所有世界"), list.size()));
        for (Region r : list) {
            sender.sendMessage(fullMsg("admin.list.entry", r.id(), r.priority(), r.minX() + "," + r.minY() + "," + r.minZ(), r.maxX() + "," + r.maxY() + "," + r.maxZ()));
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage(fullMsg("admin.info.usage")); return; }
        Region region = find(sender, args[2]);
        if (region == null) { sender.sendMessage(prefix() + fmtMsg(config.messages().regionNotFound().replace("{name}", args[2]))); return; }

        sender.sendMessage(fullMsg("admin.info.title", region.key()));
        sender.sendMessage("区域类型 " + region.kind().name().toLowerCase(Locale.ROOT));
        sender.sendMessage(fullMsg("admin.info.world", region.world()));
        if (region.kind() != Region.Kind.GLOBAL) {
            sender.sendMessage(fullMsg("admin.info.bounds", region.minX() + "," + region.minY() + "," + region.minZ(),
                region.maxX() + "," + region.maxY() + "," + region.maxZ()));
            sender.sendMessage(fullMsg("admin.info.volume", region.volume()));
        }
        sender.sendMessage(fullMsg("admin.info.priority", region.priority()));
        sender.sendMessage(fullMsg("admin.info.parent", (region.parentId() != null ? region.parentId() : "无")));
        sender.sendMessage(fullMsg("admin.info.owners", formatUuids(region.owners())));
        sender.sendMessage(fullMsg("admin.info.members", formatUuids(region.members())));
        sender.sendMessage(fullMsg("admin.info.flags", region.flags().size()));
    }

    private void handlePos(CommandSender sender, int pos) {
        if (!(sender instanceof Player player)) { sender.sendMessage(fullMsg("common.only-player")); return; }
        Selection sel = manager.getSelection(player.getUniqueId());
        if (pos == 1) sel.setPos1(player.getLocation());
        else if (!sel.setPos2(player.getLocation())) {
            sender.sendMessage(prefix() + fmtMsg("&c选区点 2 必须与点 1 在同一世界 (&f" + sel.world() + "&c)。"));
            return;
        }
        String msg = (pos == 1 ? config.messages().wandPos1() : config.messages().wandPos2())
            .replace("{x}", String.valueOf(player.getLocation().getBlockX()))
            .replace("{y}", String.valueOf(player.getLocation().getBlockY()))
            .replace("{z}", String.valueOf(player.getLocation().getBlockZ()));
        player.sendMessage(prefix() + fmtMsg(msg));
    }

    /**
     * 给予玩家一把选区工具（与 selection.wand-item 配置一致）。
     */
    private void handleWand(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage(fullMsg("common.only-player")); return; }
        if (!player.hasPermission("axs.regions.select")) {
            sender.sendMessage(fullMsg("common.no-permission"));
            return;
        }
        org.bukkit.inventory.ItemStack wand = new org.bukkit.inventory.ItemStack(config.selection().wandItem());
        var meta = wand.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&6区域选区工具 &7(左键设点1 / 右键设点2)"));
            wand.setItemMeta(meta);
        }
        player.getInventory().addItem(wand);
        player.sendMessage(prefix() + fmtMsg("&a已给予选区工具。手持它左键设点 1、右键设点 2。"));
    }

    /**
     * 切换区域边框常驻粒子可视化（仅显示自己拥有/参与的区域；admin 显示所有）。
     */
    private void handleShow(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage(fullMsg("common.only-player")); return; }
        if (particleTask == null) {
            sender.sendMessage(prefix() + fmtMsg("&c粒子可视化服务未启动。"));
            return;
        }
        boolean enabled = particleTask.toggleRegionBorder(player);
        if (enabled) {
            player.sendMessage(prefix() + fmtMsg("&a已开启区域边框可视化 &7(再次输入 /rg show 关闭)"));
        } else {
            player.sendMessage(prefix() + fmtMsg("&c已关闭区域边框可视化。"));
        }
    }

    private void handleFlag(CommandSender sender, String[] args) {
        if (args.length < 5) { sender.sendMessage(fullMsg("admin.flag.usage")); return; }
        Region region = requireRegion(sender, args[2]);
        RegionFlag flag = requireFlag(args[3]);
        RegionFlag.State state = RegionFlag.State.parse(args[4]);
        String data = args.length > 5 ? String.join(" ", java.util.Arrays.copyOfRange(args, 5, args.length)) : null;
        complete(sender, manager.setFlag(sender, region, flag, state, data), updated ->
            sender.sendMessage(prefix() + fmtMsg(config.messages().flagSet()
                .replace("{region}", updated.key()).replace("{flag}", flag.configKey())
                .replace("{value}", state.name().toLowerCase(Locale.ROOT)))));
    }

    private void handleRemoveFlag(CommandSender sender, String[] args) {
        if (args.length < 4) { sender.sendMessage(fullMsg("admin.removeflag.usage")); return; }
        Region region = requireRegion(sender, args[2]);
        RegionFlag flag = requireFlag(args[3]);
        complete(sender, manager.setFlag(sender, region, flag, RegionFlag.State.NONE, null), updated ->
            sender.sendMessage(prefix() + fmtMsg(config.messages().flagRemoved()
                .replace("{region}", updated.key()).replace("{flag}", flag.configKey()))));
    }

    private void handleFlags(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage(fullMsg("admin.flags.usage")); return; }
        Region region = find(sender, args[2]);
        if (region == null) { sender.sendMessage(prefix() + fmtMsg(config.messages().regionNotFound().replace("{name}", args[2]))); return; }

        Map<RegionFlag, RegionFlag.State> flags = region.flags();
        if (flags.isEmpty()) {
            sender.sendMessage(fullMsg("admin.flags.empty", region.id()));
            return;
        }
        sender.sendMessage(fullMsg("admin.flags.title", region.id()));
        for (var entry : flags.entrySet()) {
            String data = region.getFlagData(entry.getKey());
            String extra = data != null ? ChatColor.DARK_GRAY + " (" + data + ")" : "";
            ChatColor color = entry.getValue() == RegionFlag.State.ALLOW ? ChatColor.GREEN : ChatColor.RED;
            sender.sendMessage(fullMsg("admin.flags.entry", entry.getKey().configKey(), color + entry.getValue().name().toLowerCase() + extra));
        }
    }

    private void handleAddMember(CommandSender sender, String[] args, String role) {
        handleMembership(sender, args, role, true);
    }

    private void handleRemoveMember(CommandSender sender, String[] args, String role) {
        handleMembership(sender, args, role, false);
    }

    private void handleMembership(CommandSender sender, String[] args, String role, boolean add) {
        if (args.length < 4) throw new IllegalArgumentException("请指定区域和玩家或 g:权限组。");
        Region region = requireRegion(sender, args[2]);
        if (!manager.canManage(sender, region)) throw new IllegalArgumentException("你没有管理此区域的权限。");
        String targetName = args[3];
        boolean owner = "owner".equals(role);
        String group = targetName.startsWith("g:") ? targetName.substring(2).toLowerCase(Locale.ROOT) : null;
        if (group != null && !group.matches("[a-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("权限组名称无效。");
        }
        CompletableFuture<UUID> target = group == null
            ? RegionProfiles.resolve(plugin, targetName) : CompletableFuture.completedFuture(null);
        complete(sender, target.thenCompose(uuid -> manager.update(sender, region, (add ? "add-" : "remove-") + role, copy -> {
            if (group != null) {
                var groups = owner ? copy.ownerGroups() : copy.memberGroups();
                if (add) groups.add(group);
                else groups.remove(group);
            } else {
                var players = owner ? copy.owners() : copy.members();
                if (add) players.add(uuid);
                else players.remove(uuid);
            }
        })), updated -> {
            String message = add ? config.messages().memberAdded() : config.messages().memberRemoved();
            sender.sendMessage(prefix() + fmtMsg(message.replace("{player}", targetName).replace("{region}", updated.key())
                .replace("{role}", owner ? "所有者" : "成员")));
        });
    }

    private void handlePriority(CommandSender sender, String[] args) {
        if (args.length < 4) { sender.sendMessage(fullMsg("admin.priority.usage")); return; }
        Region region = requireRegion(sender, args[2]);
        if (!RegionManager.isAdmin(sender)) throw new IllegalArgumentException("只有管理员可以修改优先级。");
        try {
            int priority = Integer.parseInt(args[3]);
            complete(sender, manager.update(sender, region, "priority", copy -> copy.setPriority(priority)),
                updated -> sender.sendMessage(fullMsg("admin.priority.success", updated.key(), priority)));
        } catch (NumberFormatException e) {
            sender.sendMessage(fullMsg("admin.priority.invalid", args[3]));
        }
    }

    private void handleParent(CommandSender sender, String[] args) {
        if (args.length < 4) { sender.sendMessage(fullMsg("admin.parent.usage")); return; }
        Region region = requireRegion(sender, args[2]);
        complete(sender, manager.setParent(sender, region, args[3]),
            updated -> sender.sendMessage(fullMsg("admin.parent.success", updated.key(), args[3])));
    }

    private void handleTeleport(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage(fullMsg("common.only-player")); return; }
        if (args.length < 3) { sender.sendMessage(fullMsg("admin.tp.usage")); return; }
        Region region = find(sender, args[2]);
        if (region == null) { sender.sendMessage(prefix() + fmtMsg(config.messages().regionNotFound().replace("{name}", args[2]))); return; }

        if (region.kind() == Region.Kind.GLOBAL) throw new IllegalArgumentException("世界默认规则没有传送点。");
        var world = Bukkit.getWorld(region.world());
        if (world == null) { sender.sendMessage(fullMsg("admin.tp.world-offline", region.world())); return; }
        int cx = (int) Math.floorDiv((long) region.minX() + region.maxX(), 2);
        int cz = (int) Math.floorDiv((long) region.minZ() + region.maxZ(), 2);
        int highest = Math.min(region.maxY(), world.getMaxHeight() - 2);
        int lowest = Math.max(region.minY(), world.getMinHeight() + 1);
        for (int y = highest; y >= lowest; y--) {
            var feet = world.getBlockAt(cx, y, cz);
            var head = world.getBlockAt(cx, y + 1, cz);
            var floor = world.getBlockAt(cx, y - 1, cz);
            if (!feet.getType().isAir() || !head.getType().isAir() || !floor.getType().isSolid()
                || floor.getType() == org.bukkit.Material.MAGMA_BLOCK) continue;
            if (player.teleport(new org.bukkit.Location(world, cx + 0.5, y, cz + 0.5))) {
                sender.sendMessage(fullMsg("admin.tp.success", region.key()));
            }
            return;
        }
        throw new IllegalArgumentException("区域中心列内找不到安全落点。");
    }

    // ─── 工具方法 ───

    private Region find(CommandSender sender, String ref) {
        return manager.findRegion(ref, sender instanceof Player player ? player.getWorld().getName() : null);
    }

    private Region requireRegion(CommandSender sender, String ref) {
        Region region = find(sender, ref);
        if (region == null) throw new IllegalArgumentException("找不到区域 " + ref + "。");
        return region;
    }

    private static RegionFlag requireFlag(String key) {
        RegionFlag flag = RegionFlag.fromKey(key);
        if (flag == null) throw new IllegalArgumentException("未知标志 " + key + "。");
        return flag;
    }

    private String prefix() {
        return ChatColor.translateAlternateColorCodes('&', config.messages().prefix());
    }

    private String fmtMsg(String msg) {
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    private String formatUuids(java.util.Set<UUID> uuids) {
        return uuids.isEmpty() ? "无" : String.join("、", uuids.stream().map(UUID::toString).sorted().toList());
    }

    private void handleProfile(CommandSender sender, String[] args) {
        requireAdmin(sender);
        if (args.length != 3) throw new IllegalArgumentException("用法 /rg profile <start|stop>");
        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "start" -> {
                manager.timings().start();
                sender.sendMessage(prefix() + "已开始采集区域查询与边界回调耗时。");
            }
            case "stop" -> manager.timings().stop().forEach(sender::sendMessage);
            default -> throw new IllegalArgumentException("用法 /rg profile <start|stop>");
        }
    }

    private <T> void complete(CommandSender sender, CompletableFuture<T> future, Consumer<T> success) {
        future.thenAccept(success).exceptionally(error -> {
            Throwable cause = error instanceof CompletionException ? error.getCause() : error;
            if (cause instanceof IllegalArgumentException || cause instanceof IllegalStateException) {
                sender.sendMessage(prefix() + ChatColor.RED + cause.getMessage());
            } else {
                plugin.getLogger().log(Level.WARNING, "区域文件或玩家资料操作失败。", cause);
                sender.sendMessage(prefix() + ChatColor.RED + "操作失败，请查看服务端日志。");
            }
            return null;
        });
    }

    private List<String> filterRegions(String input) {
        String n = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (Region r : manager.getAllRegions()) {
            if (r.key().toLowerCase(Locale.ROOT).startsWith(n)) result.add(r.key());
            else if (r.id().toLowerCase(Locale.ROOT).startsWith(n)) result.add(r.key());
        }
        return result;
    }

    private List<String> filterFlags(String input) {
        String n = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (RegionFlag flag : RegionFlag.values()) {
            if (flag.configKey().startsWith(n)) result.add(flag.configKey());
        }
        return result;
    }

    private List<String> filterOnline(String input) {
        String n = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().toLowerCase(Locale.ROOT).startsWith(n)) result.add(p.getName());
        }
        return result;
    }

    private static List<String> filter(List<String> candidates, String input) {
        String n = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> r = new ArrayList<>();
        for (String c : candidates) if (c.toLowerCase(Locale.ROOT).startsWith(n)) r.add(c);
        return r;
    }
}
