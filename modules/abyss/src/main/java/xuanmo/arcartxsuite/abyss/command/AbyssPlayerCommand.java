package xuanmo.arcartxsuite.abyss.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstance;
import xuanmo.arcartxsuite.abyss.instance.LeaveReason;
import xuanmo.arcartxsuite.abyss.party.Party;
import xuanmo.arcartxsuite.abyss.party.PartyManager;
import xuanmo.arcartxsuite.abyss.service.AbyssService;
import xuanmo.arcartxsuite.abyss.template.AbyssTemplate;

/**
 * 玩家命令：/abyss（进本/离开/信息/体力/排行/队伍）+ /party（组队）。
 * <p>
 * module.yml player-commands 注册，模块 onEnable 时绑定 executor。
 */
public final class AbyssPlayerCommand implements TabExecutor {

    private final AbyssService service;

    public AbyssPlayerCommand(AbyssService service) {
        this.service = service;
    }

    // ═══ /abyss ═══

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("该命令仅玩家可执行");
            return true;
        }
        if (args.length == 0) {
            // 无参：打开地牢浏览 UI（或输出帮助）
            if (service.uiFacade() != null) {
                service.uiFacade().openBrowser(player);
            } else {
                sendHelp(player, label);
            }
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "enter", "join" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /" + label + " enter <地牢>");
                    return true;
                }
                // 多人队伍进本：若玩家在队伍中且为队长 → 带全队
                Party party = service.party().partyOf(player);
                List<Player> members;
                if (party != null && party.isLeader(player.getUniqueId()) && party.size() > 1) {
                    members = party.onlineMembers();
                } else {
                    members = List.of(player);
                }
                var r = service.enterDungeon(members, args[1]);
                if (!r.ok()) player.sendMessage("§c" + r.reason());
            }
            case "leave", "quit" -> {
                AbyssInstance i = service.instances().getInstance(player);
                if (i == null) {
                    player.sendMessage(service.msg("error.not-in-dungeon"));
                    return true;
                }
                service.playerExit(i, player, LeaveReason.QUIT);
            }
            case "list" -> {
                player.sendMessage("§6═══ 可进入的地牢 ═══");
                for (AbyssTemplate t : service.instances().getAllTemplates()) {
                    player.sendMessage("§e" + t.id() + " §7- §f" + t.name()
                        + " §8(" + t.requirements().minPlayers() + "-"
                        + t.requirements().maxPlayers() + "人)");
                }
            }
            case "info" -> {
                AbyssInstance i = service.instances().getInstance(player);
                if (i == null) {
                    player.sendMessage(service.msg("error.not-in-dungeon"));
                    return true;
                }
                player.sendMessage("§6═══ 当前副本 ═══");
                player.sendMessage("§e地牢: §f" + i.getTemplate().name());
                player.sendMessage("§e状态: §f" + i.getStatus()
                    + " §7用时: " + AbyssService.formatTime(i.getRunningTime()));
                player.sendMessage("§e玩家: §f" + i.getPlayerUUIDs().size()
                    + " §7死亡: " + i.getDeaths(player.getUniqueId()));
            }
            case "stamina" -> player.sendMessage("§e体力: §f"
                + service.stamina().getStamina(player.getUniqueId())
                + "/" + service.stamina().max());
            case "rank", "top" -> {
                if (service.uiFacade() != null && args.length >= 2) {
                    service.uiFacade().openLeaderboard(player, args[1]);
                } else {
                    player.sendMessage("§c排行 UI 未启用，用法: /" + label + " rank <地牢>");
                }
            }
            case "party" -> onPartyCommand(player, dropFirst(args), "party");
            case "ui" -> {
                if (service.uiFacade() != null) {
                    service.uiFacade().openBrowser(player);
                } else {
                    player.sendMessage("§cUI 未启用");
                }
            }
            default -> sendHelp(player, label);
        }
        return true;
    }

    private void sendHelp(Player p, String label) {
        p.sendMessage("§6═══ Abyss 地牢 ═══");
        p.sendMessage("§e/" + label + " §7- 打开地牢浏览");
        p.sendMessage("§e/" + label + " enter <地牢> §7- 进入地牢");
        p.sendMessage("§e/" + label + " leave §7- 离开副本");
        p.sendMessage("§e/" + label + " list §7- 地牢列表");
        p.sendMessage("§e/" + label + " info §7- 当前副本信息");
        p.sendMessage("§e/" + label + " stamina §7- 查看体力");
        p.sendMessage("§e/" + label + " party §7- 队伍命令");
        p.sendMessage("§e/" + label + " rank <地牢> §7- 排行榜");
    }

    // ═══ /party ═══

    public boolean onPartyCommand(Player player, String[] args, String label) {
        if (args.length == 0) {
            if (service.uiFacade() != null) {
                service.uiFacade().openParty(player);
            } else {
                sendPartyHelp(player, label);
            }
            return true;
        }
        PartyManager pm = service.party();
        Party party = pm.partyOf(player);
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> {
                if (party != null) {
                    player.sendMessage("§c你已在队伍中");
                    return true;
                }
                pm.create(player);
                player.sendMessage(service.msg("party.created"));
            }
            case "invite" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /" + label + " invite <玩家>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage(service.msg("error.player-not-found", "player", args[1]));
                    return true;
                }
                if (target.getUniqueId().equals(player.getUniqueId())) {
                    player.sendMessage("§c不能邀请自己");
                    return true;
                }
                if (pm.inParty(target.getUniqueId())) {
                    player.sendMessage(service.msg("error.already-in-party"));
                    return true;
                }
                Party p = pm.invite(player, target);
                player.sendMessage(service.msg("party.invited",
                    "player", target.getName(), "timeout", String.valueOf(pm.inviteTimeout())));
                target.sendMessage(service.msg("party.invite-received", "leader", player.getName()));
                p.broadcast("§7" + player.getName() + " 邀请了 " + target.getName());
            }
            case "accept" -> {
                var result = pm.accept(player);
                if (result instanceof PartyManager.JoinResult.Ok ok) {
                    ok.party().broadcast(service.msg("party.joined", "player", player.getName()));
                } else if (result instanceof PartyManager.JoinResult.Full) {
                    player.sendMessage(service.msg("error.party-full"));
                } else {
                    player.sendMessage(service.msg("error.invite-expired"));
                }
            }
            case "leave" -> {
                if (party == null) {
                    player.sendMessage(service.msg("error.not-in-party"));
                    return true;
                }
                pm.leave(player);
                player.sendMessage(service.msg("party.left", "player", player.getName()));
                party.broadcast(service.msg("party.left", "player", player.getName()));
            }
            case "kick" -> {
                if (party == null || !party.isLeader(player.getUniqueId())) {
                    player.sendMessage(service.msg("error.not-party-leader"));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage("§c用法: /" + label + " kick <玩家>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage(service.msg("error.player-not-found", "player", args[1]));
                    return true;
                }
                pm.kick(player, target);
                party.broadcast(service.msg("party.kicked", "player", target.getName()));
                target.sendMessage(service.msg("party.kicked", "player", target.getName()));
            }
            case "leader", "transfer" -> {
                if (party == null || !party.isLeader(player.getUniqueId())) {
                    player.sendMessage(service.msg("error.not-party-leader"));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage("§c用法: /" + label + " leader <玩家>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null || !party.isMember(target.getUniqueId())) {
                    player.sendMessage("§c对方不在队伍中");
                    return true;
                }
                pm.transferLeader(player, target);
                party.broadcast(service.msg("party.leader-changed", "player", target.getName()));
            }
            case "chat" -> {
                if (party == null) {
                    player.sendMessage(service.msg("error.not-in-party"));
                    return true;
                }
                if (args.length >= 2) {
                    // /party chat <消息>
                    service.party().sendChat(player, String.join(" ",
                        java.util.Arrays.copyOfRange(args, 1, args.length)));
                } else {
                    boolean on = party.toggleChat(player.getUniqueId());
                    player.sendMessage(on ? "§a已开启队伍聊天模式" : "§c已关闭队伍聊天模式");
                }
            }
            case "list", "members" -> {
                if (party == null) {
                    player.sendMessage(service.msg("error.not-in-party"));
                    return true;
                }
                player.sendMessage("§6═══ 队伍 " + party.name() + " ═══");
                for (java.util.UUID u : party.members()) {
                    Player m = Bukkit.getPlayer(u);
                    player.sendMessage((party.isLeader(u) ? "§6★ " : "§7- ")
                        + (m != null ? m.getName() : u.toString().substring(0, 8))
                        + (m == null ? " §8(离线)" : ""));
                }
            }
            case "disband" -> {
                if (party == null || !party.isLeader(player.getUniqueId())) {
                    player.sendMessage(service.msg("error.not-party-leader"));
                    return true;
                }
                party.broadcast(service.msg("party.disbanded"));
                pm.disband(party);
            }
            default -> sendPartyHelp(player, label);
        }
        return true;
    }

    private void sendPartyHelp(Player p, String label) {
        p.sendMessage("§6═══ 队伍 ═══");
        p.sendMessage("§e/" + label + " create §7- 创建队伍");
        p.sendMessage("§e/" + label + " invite <玩家> §7- 邀请");
        p.sendMessage("§e/" + label + " accept §7- 接受邀请");
        p.sendMessage("§e/" + label + " leave §7- 离开队伍");
        p.sendMessage("§e/" + label + " chat [消息] §7- 队伍聊天");
        p.sendMessage("§e/" + label + " list §7- 成员列表");
        p.sendMessage("§e/" + label + " kick|leader|disband §7- 队长操作");
    }

    private static String[] dropFirst(String[] args) {
        return java.util.Arrays.copyOfRange(args, 1, args.length);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String label,
                                                @NotNull String[] args) {
        if (args.length == 1) {
            return filter(List.of("enter", "leave", "list", "info", "stamina",
                "rank", "party", "ui"), args[0]);
        }
        if (args.length == 2 && "enter".equalsIgnoreCase(args[0])) {
            return filter(service.instances().getAllTemplates().stream()
                .map(AbyssTemplate::id).toList(), args[1]);
        }
        if (args.length == 2 && "rank".equalsIgnoreCase(args[0])) {
            return filter(service.instances().getAllTemplates().stream()
                .map(AbyssTemplate::id).toList(), args[1]);
        }
        if (args.length == 2 && "party".equalsIgnoreCase(args[0])) {
            return filter(List.of("create", "invite", "accept", "leave", "chat",
                "list", "kick", "leader", "disband"), args[1]);
        }
        return List.of();
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
