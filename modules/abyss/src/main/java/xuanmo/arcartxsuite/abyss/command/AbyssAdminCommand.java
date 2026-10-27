package xuanmo.arcartxsuite.abyss.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstance;
import xuanmo.arcartxsuite.abyss.instance.FailReason;
import xuanmo.arcartxsuite.abyss.service.AbyssService;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;

/**
 * 管理命令：/axs abyss <action>
 * <p>
 * 动作：help / status / reload / list / info / enter / leave / force_complete /
 *       force_fail / cancel / stamina / cooldown / tp / puzzle
 */
public final class AbyssAdminCommand implements ModuleCommandHandler {

    private static final List<String> ACTIONS = List.of(
        "help", "status", "reload", "list", "info", "enter", "leave",
        "force_complete", "force_fail", "cancel", "stamina", "cooldown", "tp",
        "puzzle", "design", "region", "capture");

    private final AbyssService service;
    private final java.util.logging.Logger logger;

    public AbyssAdminCommand(AbyssService service, java.util.logging.Logger logger) {
        this.service = service;
        this.logger = logger;
    }

    @Override public String commandId() { return "abyss"; }
    @Override public List<String> actions() { return ACTIONS; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label,
                             @NotNull String[] args) {
        if (sender instanceof Player p && !p.hasPermission("arcartxsuite.abyss.admin")) {
            sender.sendMessage(service.msg("error.no-permission"));
            return true;
        }
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "help";
        try {
            switch (action) {
                case "help" -> sendHelp(sender, label);
                case "status" -> sendStatus(sender);
                case "reload" -> {
                    service.reloadTemplates();
                    sender.sendMessage(service.msg("admin-action.reload-done",
                        "count", String.valueOf(service.instances().getAllTemplates().size())));
                }
                case "list" -> sendList(sender);
                case "info" -> sendInfo(sender, args);
                case "enter" -> cmdEnter(sender, args);
                case "leave" -> cmdLeave(sender);
                case "force_complete" -> cmdForce(sender, args, true);
                case "force_fail" -> cmdForce(sender, args, false);
                case "cancel" -> cmdCancel(sender, args);
                case "stamina" -> cmdStamina(sender, args);
                case "cooldown" -> cmdCooldown(sender, args);
                case "tp" -> cmdTp(sender, args);
                case "puzzle" -> cmdPuzzle(sender, args);
                case "design" -> cmdDesign(sender, args);
                case "region" -> cmdRegion(sender, args);
                case "capture" -> cmdCapture(sender, args);
                default -> sender.sendMessage(service.msg("usage.admin-unknown", "label", label));
            }
        } catch (Exception e) {
            sender.sendMessage(service.msg("usage.admin-error",
                "error", String.valueOf(e.getMessage())));
            logger.warning("[abyss] 命令异常: " + e);
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull String[] args) {
        if (args.length == 2) return filter(ACTIONS, args[1]);
        String action = args[1].toLowerCase(Locale.ROOT);
        if (args.length == 3) {
            return switch (action) {
                case "region" -> filter(
                    List.of("pos1", "pos2", "sel", "save", "list", "info", "remove"), args[2]);
                case "capture" -> filter(List.of("list", "point"), args[2]);
                case "design" -> {
                    List<String> c = new ArrayList<>(
                        service.instances().getAllTemplates().stream()
                            .map(xuanmo.arcartxsuite.abyss.template.AbyssTemplate::id).toList());
                    c.addAll(List.of("save", "exit", "ui"));
                    yield filter(c, args[2]);
                }
                case "info", "enter", "cancel", "tp" ->
                    filter(service.instances().getAllTemplates().stream()
                        .map(xuanmo.arcartxsuite.abyss.template.AbyssTemplate::id).toList(), args[2]);
                case "leave", "stamina", "cooldown" -> null; // 玩家名
                case "force_complete", "force_fail" -> null;
                default -> List.of();
            };
        }
        if (args.length == 4) {
            return switch (action) {
                case "enter" -> null; // 玩家名
                case "stamina" -> filter(List.of("give", "set"), args[3]);
                case "cooldown" -> filter(List.of("clear", "check"), args[3]);
                case "puzzle" -> filter(
                    List.of("state", "reset", "solve", "fail", "prompt"), args[3]);
                case "region" -> switch (args[2].toLowerCase(Locale.ROOT)) {
                    case "save", "list", "info", "remove" ->
                        filter(service.instances().getAllTemplates().stream()
                            .map(xuanmo.arcartxsuite.abyss.template.AbyssTemplate::id)
                            .toList(), args[3]);
                    default -> List.of();
                };
                case "capture" -> filter(
                    service.roomSets() != null ? service.roomSets().listSets()
                        : List.of(), args[3]);
                default -> List.of();
            };
        }
        if (args.length == 5) {
            if ("stamina".equals(action)) {
                return filter(List.of("1", "10", "50"), args[4]);
            }
            if ("region".equals(action)) {
                var tpl = service.instances().getTemplate(args[3]);
                if (tpl != null && service.regions() != null
                    && ("info".equalsIgnoreCase(args[2])
                        || "remove".equalsIgnoreCase(args[2]))) {
                    return filter(service.regions().list(tpl), args[4]);
                }
            }
            if ("capture".equals(action)) {
                if ("point".equalsIgnoreCase(args[2]) && service.roomSets() != null) {
                    var set = service.roomSets().get(args[3]);
                    return set == null ? List.of()
                        : filter(new ArrayList<>(set.keySet()), args[4]);
                }
                // capture <集> <房> <类型>：第 5 参补房型
                if (!"list".equalsIgnoreCase(args[2])
                    && !"point".equalsIgnoreCase(args[2])) {
                    return filter(List.of("start", "combat", "elite", "boss",
                        "shop", "treasure", "event", "rest"), args[4]);
                }
            }
        }
        return List.of();
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(service.msg("admin-help.title"));
        for (String k : List.of("status", "list", "info", "enter", "leave", "force",
                "cancel", "stamina", "cooldown", "tp", "reload", "design", "region")) {
            sender.sendMessage(service.msg("admin-help." + k, "label", label));
        }
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(service.msg("admin-status.title"));
        sender.sendMessage(service.msg("admin-status.templates",
            "count", String.valueOf(service.instances().getAllTemplates().size())));
        sender.sendMessage(service.msg("admin-status.instances",
            "count", String.valueOf(service.instances().getAllInstances().size())));
        for (AbyssInstance i : service.instances().getAllInstances()) {
            sender.sendMessage(service.msg("admin-status.instance-row",
                "id", i.getTemplate().id(),
                "instance", i.getInstanceId().toString().substring(0, 8),
                "status", String.valueOf(i.getStatus()),
                "players", String.valueOf(i.getPlayerUUIDs().size()),
                "time", String.valueOf(i.getRunningTime())));
        }
    }

    private void sendList(CommandSender sender) {
        sender.sendMessage(service.msg("admin-list.title"));
        for (var t : service.instances().getAllTemplates()) {
            int running = service.instances().getInstances(t.id()).size();
            sender.sendMessage(service.msg("admin-list.row",
                "id", t.id(), "name", t.name(),
                "min", String.valueOf(t.requirements().minPlayers()),
                "max", String.valueOf(t.requirements().maxPlayers()),
                "max_inst", String.valueOf(t.maxInstances()),
                "running", String.valueOf(running)));
        }
    }

    private void sendInfo(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(service.msg("usage.admin-info"));
            return;
        }
        var t = service.instances().getTemplate(args[2]);
        if (t == null) {
            sender.sendMessage(service.msg("admin-action.dungeon-not-found", "dungeon", args[2]));
            return;
        }
        sender.sendMessage(service.msg("admin-info.title", "id", t.id()));
        sender.sendMessage(service.msg("admin-info.name", "name", t.name()));
        sender.sendMessage(service.msg("admin-info.world",
            "kind", String.valueOf(t.worldSource().kind()),
            "file", t.worldSource().file().getName()));
        sender.sendMessage(service.msg("admin-info.players",
            "min", String.valueOf(t.requirements().minPlayers()),
            "max", String.valueOf(t.requirements().maxPlayers())));
        sender.sendMessage(service.msg("admin-info.time",
            "seconds", String.valueOf(t.timeLimitSeconds()),
            "max_inst", String.valueOf(t.maxInstances())));
        sender.sendMessage(service.msg("admin-info.groups",
            "groups", String.valueOf(t.monsters().groups().size()),
            "obstacles", String.valueOf(t.obstacles().obstacles().size()),
            "interacts", String.valueOf(t.interacts().interacts().size()),
            "npcs", String.valueOf(t.npcs().npcs().size()),
            "rewards", String.valueOf(t.rewards().rewards().size())));
    }

    private void cmdEnter(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(service.msg("usage.admin-enter"));
            return;
        }
        Player target;
        if (args.length >= 4) {
            target = Bukkit.getPlayer(args[3]);
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage(service.msg("error.console-need-player"));
            return;
        }
        if (target == null) {
            sender.sendMessage(service.msg("error.player-not-online"));
            return;
        }
        var r = service.enterDungeon(target, args[2]);
        sender.sendMessage(r.ok() ? service.msg("admin-action.entered")
            : service.msg("admin-action.enter-failed", "reason", r.reason()));
    }

    private void cmdLeave(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(service.msg("admin-action.only-player"));
            return;
        }
        AbyssInstance i = service.instances().getInstance(p);
        if (i == null) {
            sender.sendMessage(service.msg("admin-action.player-not-in-dungeon"));
            return;
        }
        service.playerExit(i, p, xuanmo.arcartxsuite.abyss.instance.LeaveReason.ADMIN);
    }

    private void cmdForce(CommandSender sender, String[] args, boolean complete) {
        Player target = args.length >= 3 ? Bukkit.getPlayer(args[2])
            : sender instanceof Player p ? p : null;
        if (target == null) {
            sender.sendMessage(service.msg("error.need-online-player"));
            return;
        }
        AbyssInstance i = service.instances().getInstance(target);
        if (i == null) {
            sender.sendMessage(service.msg("error.target-not-in-dungeon"));
            return;
        }
        if (complete) {
            service.completeDungeon(i);
            sender.sendMessage(service.msg("admin-action.force-complete",
                "instance", String.valueOf(i.getInstanceId())));
        } else {
            service.failDungeon(i, FailReason.ADMIN);
            sender.sendMessage(service.msg("admin-action.force-fail",
                "instance", String.valueOf(i.getInstanceId())));
        }
    }

    private void cmdCancel(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(service.msg("usage.admin-cancel"));
            return;
        }
        int n = 0;
        for (AbyssInstance i : service.instances().getInstances(args[2])) {
            service.cancelDungeon(i);
            n++;
        }
        sender.sendMessage(service.msg("admin-action.cancelled", "count", String.valueOf(n)));
    }

    private void cmdStamina(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage(service.msg("usage.admin-stamina"));
            return;
        }
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage(service.msg("error.player-not-online"));
            return;
        }
        int v;
        try { v = Integer.parseInt(args[4]); } catch (NumberFormatException e) {
            sender.sendMessage(service.msg("error.invalid-number"));
            return;
        }
        switch (args[3].toLowerCase(Locale.ROOT)) {
            case "give" -> service.stamina().add(target.getUniqueId(), v);
            case "set" -> service.stamina().set(target.getUniqueId(), v);
            default -> {
                sender.sendMessage(service.msg("usage.admin-stamina-op"));
                return;
            }
        }
        sender.sendMessage(service.msg("admin-action.stamina-done",
            "player", target.getName(),
            "value", String.valueOf(service.stamina().getStamina(target.getUniqueId()))));
    }

    private void cmdCooldown(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(service.msg("usage.admin-cooldown"));
            return;
        }
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage(service.msg("error.player-not-online"));
            return;
        }
        String dungeon = args.length >= 5 ? args[4] : "";
        if ("clear".equalsIgnoreCase(args[3])) {
            service.cooldowns().clear(target.getUniqueId(), dungeon);
            sender.sendMessage(service.msg("admin-action.cooldown-cleared"));
        } else {
            long left = service.cooldowns().remaining(target.getUniqueId(), dungeon);
            sender.sendMessage(service.msg("admin-action.cooldown-left",
                "seconds", String.valueOf(left)));
        }
    }

    private void cmdTp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(service.msg("admin-action.only-player"));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(service.msg("usage.admin-tp"));
            return;
        }
        for (AbyssInstance i : service.instances().getInstances(args[2])) {
            var loc = service.spawnLocation(i);
            if (loc != null) {
                p.teleport(loc);
                sender.sendMessage(service.msg("admin-action.teleported",
                    "instance", String.valueOf(i.getInstanceId())));
                return;
            }
        }
        sender.sendMessage(service.msg("admin-action.no-instance"));
    }

    /**
     * 机关局内管理（只改当前实例内存状态，不动磁盘配置）：
     *   /axs abyss puzzle                         → 列出当前实例全部机关
     *   /axs abyss puzzle &lt;id&gt;              → 单机关详情
     *   /axs abyss puzzle &lt;id&gt; reset|solve|fail
     *   /axs abyss puzzle &lt;id&gt; prompt [玩家] → 给玩家打开输入面板
     */
    private void cmdPuzzle(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(service.msg("admin-action.only-player"));
            return;
        }
        var pm = service.puzzles();
        if (pm == null) {
            sender.sendMessage(service.msg("puzzle.ui-unavailable"));
            return;
        }
        AbyssInstance inst = service.instances().getInstance(p);
        if (inst == null) {
            sender.sendMessage(service.msg("error.not-in-dungeon"));
            return;
        }
        var defs = inst.getTemplate().puzzles().puzzles();
        if (defs.isEmpty()) {
            sender.sendMessage(service.msg("puzzle.none"));
            return;
        }
        // 无 id → 列表
        if (args.length < 3) {
            sender.sendMessage(service.msg("puzzle.list-title",
                "dungeon", inst.getTemplate().name()));
            for (var e : defs.entrySet()) {
                var puzzle = e.getValue();
                int prog = pm.progress(inst, e.getKey());
                String state = pm.isLocked(inst, e.getKey()) ? "LOCKED"
                    : pm.isDone(inst, e.getKey()) ? "DONE"
                    : (prog < 0 ? "-" : String.valueOf(prog));
                sender.sendMessage(service.msg("puzzle.list-row",
                    "id", e.getKey(), "type", puzzle.type().name(),
                    "name", puzzle.name(), "state", state));
            }
            return;
        }
        String puzzleId = args[2];
        if (!defs.containsKey(puzzleId)) {
            sender.sendMessage(service.msg("puzzle.not-found", "id", puzzleId));
            return;
        }
        String op = args.length >= 4 ? args[3].toLowerCase(Locale.ROOT) : "state";
        switch (op) {
            case "reset" -> {
                pm.reset(inst, puzzleId);
                sender.sendMessage(service.msg("puzzle.op-reset", "id", puzzleId));
            }
            case "solve" -> {
                pm.forceComplete(inst, puzzleId, p);
                sender.sendMessage(service.msg("puzzle.op-solved", "id", puzzleId));
            }
            case "fail" -> {
                pm.forceFail(inst, puzzleId, p);
                sender.sendMessage(service.msg("puzzle.op-failed", "id", puzzleId));
            }
            case "prompt" -> {
                Player target = args.length >= 5 ? Bukkit.getPlayer(args[4]) : p;
                if (target == null) {
                    sender.sendMessage(service.msg("error.player-not-online"));
                    return;
                }
                boolean ok = pm.openInput(inst, target, puzzleId);
                sender.sendMessage(service.msg(ok ? "puzzle.prompt-sent" : "puzzle.prompt-denied",
                    "id", puzzleId, "player", target.getName()));
            }
            default -> {
                // state：输出机关详情
                var puzzle = defs.get(puzzleId);
                int prog = pm.progress(inst, puzzleId);
                sender.sendMessage(service.msg("puzzle.detail",
                    "id", puzzleId, "type", puzzle.type().name(),
                    "state", pm.isLocked(inst, puzzleId) ? "LOCKED"
                        : pm.isDone(inst, puzzleId) ? "DONE" : String.valueOf(prog),
                    "steps", String.valueOf(puzzle.steps().size())));
            }
        }
    }

    /**
     * 设计模式：/axs abyss design &lt;地牢id&gt; [玩家] —— 进可写副本实例，
     * 冻结玩法，标记杖+AX 面板编辑配置，保存回写 YAML。
     * /axs abyss design save|exit —— 会话内快捷命令（等价面板按钮）。
     */
    private void cmdDesign(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(service.msg("admin-action.only-player"));
            return;
        }
        String sub = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "save" -> service.saveDesign(p, args.length >= 4
                && "world".equalsIgnoreCase(args[3]));
            case "exit" -> service.exitDesign(p);
            case "ui" -> service.openDesignUi(p);
            default -> {
                // design <地牢id>：进入设计模式
                if (sub.isEmpty()) {
                    sender.sendMessage(service.msg("usage.admin-design"));
                    return;
                }
                var r = service.enterDesign(p, sub);
                if (!r.ok()) {
                    sender.sendMessage(service.msg("admin-action.enter-failed",
                        "reason", r.reason() != null ? r.reason() : "?"));
                }
            }
        }
    }

    /**
     * 区域模板管理：
     *   /axs abyss region pos1|pos2         → 标记脚下为选区角点
     *   /axs abyss region sel               → 查看当前选区
     *   /axs abyss region save &lt;地牢&gt; &lt;名&gt; → 快照选区存为 地牢/regions/&lt;名&gt;.yml
     *   /axs abyss region list &lt;地牢&gt;         → 列出模板
     *   /axs abyss region info &lt;地牢&gt; &lt;名&gt; → 尺寸/调色板
     *   /axs abyss region remove &lt;地牢&gt; &lt;名&gt;→ 删除
     * 脚本调用：world-init 内 paste_region &lt;名&gt; &lt;x,y,z&gt; [full]
     */
    private void cmdRegion(CommandSender sender, String[] args) {
        var lib = service.regions();
        if (lib == null) {
            sender.sendMessage(service.msg("region.unavailable"));
            return;
        }
        String sub = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "pos1", "pos2" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(service.msg("admin-action.only-player"));
                    return;
                }
                var sel = lib.mark(p, "pos1".equals(sub) ? 1 : 2);
                sender.sendMessage(service.msg("region.marked",
                    "pos", sub, "x", String.valueOf(
                        "pos1".equals(sub) ? sel.x1() : sel.x2()),
                    "y", String.valueOf(
                        "pos1".equals(sub) ? sel.y1() : sel.y2()),
                    "z", String.valueOf(
                        "pos1".equals(sub) ? sel.z1() : sel.z2())));
            }
            case "sel" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(service.msg("admin-action.only-player"));
                    return;
                }
                var sel = lib.selection(p.getUniqueId());
                sender.sendMessage(sel != null && sel.complete()
                    ? service.msg("region.sel",
                        "world", sel.world(),
                        "x1", String.valueOf(sel.x1()), "y1", String.valueOf(sel.y1()),
                        "z1", String.valueOf(sel.z1()), "x2", String.valueOf(sel.x2()),
                        "y2", String.valueOf(sel.y2()), "z2", String.valueOf(sel.z2()))
                    : service.msg("region.sel-empty"));
            }
            case "save" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(service.msg("admin-action.only-player"));
                    return;
                }
                if (args.length < 5) {
                    sender.sendMessage(service.msg("usage.region-save"));
                    return;
                }
                var tpl = service.instances().getTemplate(args[3]);
                if (tpl == null) {
                    sender.sendMessage(service.msg("admin-action.dungeon-not-found",
                        "dungeon", args[3]));
                    return;
                }
                String err = lib.save(tpl, args[4], p);
                if (err != null) {
                    sender.sendMessage(service.msg("region.save-failed", "error", err));
                    return;
                }
                var rt = lib.load(tpl, args[4]);
                sender.sendMessage(service.msg("region.saved",
                    "name", args[4], "dungeon", tpl.id(),
                    "size", rt != null
                        ? rt.width() + "×" + rt.height() + "×" + rt.depth() : "?"));
            }
            case "list" -> {
                if (args.length < 4) {
                    sender.sendMessage(service.msg("usage.region-list"));
                    return;
                }
                var tpl = service.instances().getTemplate(args[3]);
                if (tpl == null) {
                    sender.sendMessage(service.msg("admin-action.dungeon-not-found",
                        "dungeon", args[3]));
                    return;
                }
                var names = lib.list(tpl);
                sender.sendMessage(names.isEmpty()
                    ? service.msg("region.list-empty", "dungeon", tpl.id())
                    : service.msg("region.list",
                        "dungeon", tpl.id(), "names", String.join(", ", names)));
            }
            case "info" -> {
                if (args.length < 5) {
                    sender.sendMessage(service.msg("usage.region-info"));
                    return;
                }
                var tpl = service.instances().getTemplate(args[3]);
                if (tpl == null) {
                    sender.sendMessage(service.msg("admin-action.dungeon-not-found",
                        "dungeon", args[3]));
                    return;
                }
                var rt = lib.load(tpl, args[4]);
                sender.sendMessage(rt == null
                    ? service.msg("region.not-found", "name", args[4])
                    : service.msg("region.info", "name", args[4],
                        "size", rt.width() + "×" + rt.height() + "×" + rt.depth(),
                        "volume", String.valueOf(rt.volume()),
                        "palette", String.valueOf(rt.paletteSize())));
            }
            case "remove" -> {
                if (args.length < 5) {
                    sender.sendMessage(service.msg("usage.region-remove"));
                    return;
                }
                var tpl = service.instances().getTemplate(args[3]);
                if (tpl == null) {
                    sender.sendMessage(service.msg("admin-action.dungeon-not-found",
                        "dungeon", args[3]));
                    return;
                }
                sender.sendMessage(lib.remove(tpl, args[4])
                    ? service.msg("region.removed", "name", args[4])
                    : service.msg("region.not-found", "name", args[4]));
            }
            default -> sender.sendMessage(service.msg("usage.region"));
        }
    }

    // ═══ procgen 房间构件捕获 ═══

    /**
     * /axs abyss capture <集> <房> <类型>      — 选区快照为房间构件（region+meta）
     * /axs abyss capture point <集> <房> <目标> — 补标锚点（spawn/mob:/chest:/npc:/interact:）
     * /axs abyss capture list <集>              — 列出集内房间
     */
    private void cmdCapture(CommandSender sender, String[] args) {
        var lib = service.roomSets();
        if (lib == null) {
            sender.sendMessage(service.msg("region.unavailable"));
            return;
        }
        String sub = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "list" -> {
                if (args.length < 4) {
                    sender.sendMessage(service.msg("usage.capture-list"));
                    return;
                }
                var set = lib.get(args[3]);
                sender.sendMessage(set == null || set.isEmpty()
                    ? service.msg("capture.empty", "set", args[3])
                    : service.msg("capture.list", "set", args[3],
                        "rooms", String.join(", ", set.keySet())));
            }
            case "point" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(service.msg("admin-action.only-player"));
                    return;
                }
                if (args.length < 6) {
                    sender.sendMessage(service.msg("usage.capture-point"));
                    return;
                }
                String err = capturePoint(lib, args[3], args[4], args[5], p);
                if (err != null) {
                    sender.sendMessage(service.msg("capture.failed", "error", err));
                } else {
                    sender.sendMessage(service.msg("capture.point-added",
                        "room", args[4], "target", args[5]));
                }
            }
            default -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(service.msg("admin-action.only-player"));
                    return;
                }
                if (args.length < 5) {
                    sender.sendMessage(service.msg("usage.capture"));
                    return;
                }
                String err = captureRoom(lib, args[2], args[3], args[4], p);
                if (err != null) {
                    sender.sendMessage(service.msg("capture.failed", "error", err));
                } else {
                    sender.sendMessage(service.msg("capture.saved",
                        "set", args[2], "room", args[3], "type", args[4]));
                }
            }
        }
    }

    /** 选区 → 房间构件（region.yml + yml meta）。 */
    private @Nullable String captureRoom(
            xuanmo.arcartxsuite.abyss.procgen.RoomSetLibrary lib,
            String set, String room, String type, Player p) {
        if (!xuanmo.arcartxsuite.abyss.procgen.RoomSetLibrary.validName(room)) return "非法房间名";
        var lib2 = service.regions();
        var sel = lib2 != null ? lib2.selection(p.getUniqueId()) : null;
        if (sel == null || !sel.complete()) {
            return "先用 /axs abyss region pos1|pos2 标好两个角";
        }
        if (!p.getWorld().getName().equals(sel.world())) {
            return "你已离开选区所在世界（" + sel.world() + "）";
        }
        // 类型合法性
        try {
            xuanmo.arcartxsuite.abyss.procgen.ProcGenRoomAsset.RoomKind
                .valueOf(type.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return "非法房型（start/combat/elite/boss/shop/treasure/event/rest）";
        }
        var rt = xuanmo.arcartxsuite.abyss.template.RegionTemplate.capture(
            p.getWorld(), sel.x1(), sel.y1(), sel.z1(), sel.x2(), sel.y2(), sel.z2());
        java.io.File dir = lib.dir(set);
        dir.mkdirs();
        // 归一最小角 → capture-origin（point 命令换算房内坐标用）
        int ox = Math.min(sel.x1(), sel.x2()), oy = Math.min(sel.y1(), sel.y2()),
            oz = Math.min(sel.z1(), sel.z2());
        try {
            rt.save(new java.io.File(dir, room + ".region.yml"));
        } catch (java.io.IOException e) {
            return "region 保存失败: " + e.getMessage();
        }
        // meta yml（已存在时合并：只更新 type/capture-origin，不动手填的锚点表）
        java.io.File metaFile = new java.io.File(dir, room + ".yml");
        org.bukkit.configuration.file.YamlConfiguration meta =
            org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(metaFile);
        meta.set("type", type.toLowerCase(Locale.ROOT));
        meta.set("weight", meta.getInt("weight", 10));
        meta.set("capture-origin",
            sel.world() + "," + ox + "," + oy + "," + oz);
        // 玩家位置 → 相对出生点（若在选区内）
        int[] rel = relTo(p.getLocation(), sel.world(), ox, oy, oz);
        if (rel != null) {
            meta.set("spawn", rel[0] + "," + rel[1] + "," + rel[2]);
        }
        try {
            meta.save(metaFile);
        } catch (java.io.IOException e) {
            return "meta 保存失败: " + e.getMessage();
        }
        lib.reload();
        return null;
    }

    /** 补标锚点：spawn / mob:<id>[:波] / chest:<id> / npc:<id> / interact:<id>。 */
    private @Nullable String capturePoint(
            xuanmo.arcartxsuite.abyss.procgen.RoomSetLibrary lib,
            String set, String room, String target, Player p) {
        if (!xuanmo.arcartxsuite.abyss.procgen.RoomSetLibrary.validName(room)) return "非法房间名";
        java.io.File metaFile = new java.io.File(lib.dir(set), room + ".yml");
        if (!metaFile.isFile()) return "房间不存在（先 capture 建构件）";
        org.bukkit.configuration.file.YamlConfiguration meta =
            org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(metaFile);
        String o = meta.getString("capture-origin", "");
        String[] op = o.split(",");
        if (op.length != 4) return "房间缺 capture-origin（重新 capture 一次生成）";
        int[] rel = relTo(p.getLocation(), op[0],
            parseInt(op[1], 0), parseInt(op[2], 0), parseInt(op[3], 0));
        if (rel == null) return "你不在该房间的捕获世界/范围内";
        String loc = rel[0] + "," + rel[1] + "," + rel[2];

        String[] t = target.split(":", 2);
        if (!"spawn".equalsIgnoreCase(t[0]) && (t.length < 2 || t[1].isBlank()))
            return "目标需要填写引用ID，例如 mob:vanilla:zombie 或 chest:宝箱ID";
        switch (t[0].toLowerCase(Locale.ROOT)) {
            case "spawn" -> meta.set("spawn", loc);
            case "chest" -> appendList(meta, "chests", loc + ": " + t[1]);
            case "npc" -> appendList(meta, "npcs", loc + ": " + t[1]);
            case "interact" -> appendList(meta, "interacts", loc + ": " + t[1]);
            case "mob" -> {
                // mob:<id>[:波] → mobs 或 mobs-wave-N
                String ref = t.length > 1 ? t[1] : "";
                int w = 1;
                int wi = ref.lastIndexOf(':');
                if (wi > 0 && ref.substring(wi + 1).matches("\\d+")) {
                    w = Integer.parseInt(ref.substring(wi + 1));
                    ref = ref.substring(0, wi);
                }
                appendList(meta, w <= 1 ? "mobs" : "mobs-wave-" + w,
                    loc + ": " + ref);
            }
            default -> {
                return "非法目标（spawn/mob:<id>[:波]/chest:<id>/npc:<id>/interact:<id>）";
            }
        }
        try {
            meta.save(metaFile);
        } catch (java.io.IOException e) {
            return "meta 保存失败: " + e.getMessage();
        }
        lib.reload();
        return null;
    }

    /** 玩家块位置 → 相对 capture-origin 的房内坐标；不在同世界返回 null。 */
    private static int[] relTo(org.bukkit.Location l, String world,
                               int ox, int oy, int oz) {
        if (l.getWorld() == null || !l.getWorld().getName().equals(world)) return null;
        return new int[]{l.getBlockX() - ox, l.getBlockY() - oy, l.getBlockZ() - oz};
    }

    private static void appendList(
            org.bukkit.configuration.file.YamlConfiguration yml,
            String key, String value) {
        java.util.List<String> l = yml.getStringList(key);
        l.add(value);
        yml.set(key, l);
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); }
        catch (Exception e) { return def; }
    }

    private static List<String> filter(List<String> candidates, String input) {
        String n = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String c : candidates) {
            if (c.toLowerCase(Locale.ROOT).startsWith(n)) out.add(c);
        }
        return out;
    }
}
