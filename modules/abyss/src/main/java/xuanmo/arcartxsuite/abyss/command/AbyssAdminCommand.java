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
 *       force_fail / cancel / stamina / cooldown / tp
 */
public final class AbyssAdminCommand implements ModuleCommandHandler {

    private static final List<String> ACTIONS = List.of(
        "help", "status", "reload", "list", "info", "enter", "leave",
        "force_complete", "force_fail", "cancel", "stamina", "cooldown", "tp");

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
                default -> List.of();
            };
        }
        if (args.length == 5 && "stamina".equals(action)) {
            return filter(List.of("1", "10", "50"), args[4]);
        }
        return List.of();
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(service.msg("admin-help.title"));
        for (String k : List.of("status", "list", "info", "enter", "leave", "force",
                "cancel", "stamina", "cooldown", "tp", "reload")) {
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

    private static List<String> filter(List<String> candidates, String input) {
        String n = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String c : candidates) {
            if (c.toLowerCase(Locale.ROOT).startsWith(n)) out.add(c);
        }
        return out;
    }
}
