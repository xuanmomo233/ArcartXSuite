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
                    sender.sendMessage("§a[abyss] 模板已重载: "
                        + service.instances().getAllTemplates().size() + " 个地牢");
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
                default -> sender.sendMessage("§c未知子命令，输入 /" + label + " abyss help");
            }
        } catch (Exception e) {
            sender.sendMessage("§c[abyss] 执行出错: " + e.getMessage());
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
        String c = "/" + label + " abyss";
        sender.sendMessage("§6═══ Abyss 地牢管理 ═══");
        sender.sendMessage("§e" + c + " status §7- 模块状态");
        sender.sendMessage("§e" + c + " list §7- 地牢模板/实例列表");
        sender.sendMessage("§e" + c + " info <地牢> §7- 模板详情");
        sender.sendMessage("§e" + c + " enter <地牢> [玩家] §7- 强制进本");
        sender.sendMessage("§e" + c + " leave §7- 离开当前副本");
        sender.sendMessage("§e" + c + " force_complete|force_fail [玩家] §7- 强制结算");
        sender.sendMessage("§e" + c + " cancel <地牢> §7- 取消该地牢全部实例");
        sender.sendMessage("§e" + c + " stamina <玩家> give|set <值> §7- 体力管理");
        sender.sendMessage("§e" + c + " cooldown <玩家> clear|check [地牢] §7- 冷却管理");
        sender.sendMessage("§e" + c + " reload §7- 重载地牢模板");
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage("§6═══ Abyss 状态 ═══");
        sender.sendMessage("§e地牢模板: §f" + service.instances().getAllTemplates().size());
        sender.sendMessage("§e运行实例: §f" + service.instances().getAllInstances().size());
        for (AbyssInstance i : service.instances().getAllInstances()) {
            sender.sendMessage("§7- " + i.getTemplate().id() + " §8" + i.getInstanceId()
                .toString().substring(0, 8) + " §7状态=" + i.getStatus()
                + " 玩家=" + i.getPlayerUUIDs().size()
                + " 存活怪=" + "?" + " 用时=" + i.getRunningTime() + "s");
        }
    }

    private void sendList(CommandSender sender) {
        sender.sendMessage("§6═══ 地牢模板 ═══");
        for (var t : service.instances().getAllTemplates()) {
            int running = service.instances().getInstances(t.id()).size();
            sender.sendMessage("§e" + t.id() + " §7" + t.name()
                + " §8人数" + t.requirements().minPlayers() + "-" + t.requirements().maxPlayers()
                + " 实例上限" + t.maxInstances() + " 当前" + running);
        }
    }

    private void sendInfo(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§c用法: /axs abyss info <地牢>");
            return;
        }
        var t = service.instances().getTemplate(args[2]);
        if (t == null) {
            sender.sendMessage("§c地牢不存在: " + args[2]);
            return;
        }
        sender.sendMessage("§6═══ " + t.id() + " ═══");
        sender.sendMessage("§e名称: §f" + t.name());
        sender.sendMessage("§e世界来源: §f" + t.worldSource().kind()
            + " §7" + t.worldSource().file().getName());
        sender.sendMessage("§e人数: §f" + t.requirements().minPlayers()
            + "-" + t.requirements().maxPlayers());
        sender.sendMessage("§e时限: §f" + t.timeLimitSeconds() + "s §7实例上限=" + t.maxInstances());
        sender.sendMessage("§e怪物组: §f" + t.monsters().groups().size()
            + " §7障碍=" + t.obstacles().obstacles().size()
            + " 交互=" + t.interacts().interacts().size()
            + " NPC=" + t.npcs().npcs().size()
            + " 奖励=" + t.rewards().rewards().size());
    }

    private void cmdEnter(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§c用法: /axs abyss enter <地牢> [玩家]");
            return;
        }
        Player target;
        if (args.length >= 4) {
            target = Bukkit.getPlayer(args[3]);
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage("§c控制台需指定玩家");
            return;
        }
        if (target == null) {
            sender.sendMessage("§c玩家不在线");
            return;
        }
        var r = service.enterDungeon(target, args[2]);
        sender.sendMessage(r.ok() ? "§a已进入副本流程" : "§c进入失败: " + r.reason());
    }

    private void cmdLeave(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§c仅玩家可执行");
            return;
        }
        AbyssInstance i = service.instances().getInstance(p);
        if (i == null) {
            sender.sendMessage("§c你不在副本中");
            return;
        }
        service.playerExit(i, p, xuanmo.arcartxsuite.abyss.instance.LeaveReason.ADMIN);
    }

    private void cmdForce(CommandSender sender, String[] args, boolean complete) {
        Player target = args.length >= 3 ? Bukkit.getPlayer(args[2])
            : sender instanceof Player p ? p : null;
        if (target == null) {
            sender.sendMessage("§c需指定在线玩家");
            return;
        }
        AbyssInstance i = service.instances().getInstance(target);
        if (i == null) {
            sender.sendMessage("§c该玩家不在副本中");
            return;
        }
        if (complete) {
            service.completeDungeon(i);
            sender.sendMessage("§a已强制通关实例 " + i.getInstanceId());
        } else {
            service.failDungeon(i, FailReason.ADMIN);
            sender.sendMessage("§a已强制失败实例 " + i.getInstanceId());
        }
    }

    private void cmdCancel(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§c用法: /axs abyss cancel <地牢>");
            return;
        }
        int n = 0;
        for (AbyssInstance i : service.instances().getInstances(args[2])) {
            service.cancelDungeon(i);
            n++;
        }
        sender.sendMessage("§a已取消 " + n + " 个实例");
    }

    private void cmdStamina(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage("§c用法: /axs abyss stamina <玩家> give|set <值>");
            return;
        }
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage("§c玩家不在线");
            return;
        }
        int v;
        try { v = Integer.parseInt(args[4]); } catch (NumberFormatException e) {
            sender.sendMessage("§c数值无效");
            return;
        }
        switch (args[3].toLowerCase(Locale.ROOT)) {
            case "give" -> service.stamina().add(target.getUniqueId(), v);
            case "set" -> service.stamina().set(target.getUniqueId(), v);
            default -> {
                sender.sendMessage("§c用法: give|set");
                return;
            }
        }
        sender.sendMessage("§a已更新 " + target.getName() + " 体力: "
            + service.stamina().getStamina(target.getUniqueId()));
    }

    private void cmdCooldown(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§c用法: /axs abyss cooldown <玩家> clear|check [地牢]");
            return;
        }
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage("§c玩家不在线");
            return;
        }
        String dungeon = args.length >= 5 ? args[4] : "";
        if ("clear".equalsIgnoreCase(args[3])) {
            service.cooldowns().clear(target.getUniqueId(), dungeon);
            sender.sendMessage("§a已清除冷却");
        } else {
            long left = service.cooldowns().remaining(target.getUniqueId(), dungeon);
            sender.sendMessage("§e冷却剩余: " + left + "s");
        }
    }

    private void cmdTp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§c仅玩家可执行");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage("§c用法: /axs abyss tp <地牢>（传送到该地牢运行中实例）");
            return;
        }
        for (AbyssInstance i : service.instances().getInstances(args[2])) {
            var loc = service.spawnLocation(i);
            if (loc != null) {
                p.teleport(loc);
                sender.sendMessage("§a已传送到实例 " + i.getInstanceId());
                return;
            }
        }
        sender.sendMessage("§c无运行中实例");
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
