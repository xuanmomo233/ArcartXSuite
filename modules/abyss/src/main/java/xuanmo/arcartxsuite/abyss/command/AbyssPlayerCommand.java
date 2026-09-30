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
            sender.sendMessage(service.msg("error.player-only"));
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
                    player.sendMessage(service.msg("usage.enter", "label", label));
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
                if (!r.ok()) player.sendMessage(service.msg("error.enter-failed", "reason", r.reason()));
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
                player.sendMessage(service.msg("list.title"));
                for (AbyssTemplate t : service.instances().getAllTemplates()) {
                    player.sendMessage(service.msg("list.row",
                        "id", t.id(), "name", t.name(),
                        "min", String.valueOf(t.requirements().minPlayers()),
                        "max", String.valueOf(t.requirements().maxPlayers())));
                }
            }
            case "info" -> {
                AbyssInstance i = service.instances().getInstance(player);
                if (i == null) {
                    player.sendMessage(service.msg("error.not-in-dungeon"));
                    return true;
                }
                player.sendMessage(service.msg("info.title"));
                player.sendMessage(service.msg("info.dungeon", "name", i.getTemplate().name()));
                player.sendMessage(service.msg("info.status",
                    "status", String.valueOf(i.getStatus()),
                    "time", AbyssService.formatTime(i.getRunningTime())));
                player.sendMessage(service.msg("info.players",
                    "count", String.valueOf(i.getPlayerUUIDs().size()),
                    "deaths", String.valueOf(i.getDeaths(player.getUniqueId()))));
            }
            case "stamina" -> player.sendMessage(service.msg("stamina.view",
                "current", String.valueOf(service.stamina().getStamina(player.getUniqueId())),
                "max", String.valueOf(service.stamina().max())));
            case "rank", "top" -> {
                if (service.uiFacade() != null && args.length >= 2) {
                    service.uiFacade().openLeaderboard(player, args[1]);
                } else {
                    player.sendMessage(service.msg("usage.rank", "label", label));
                }
            }
            case "party" -> onPartyCommand(player, dropFirst(args), "party");
            case "ui" -> {
                if (service.uiFacade() != null) {
                    service.uiFacade().openBrowser(player);
                } else {
                    player.sendMessage(service.msg("error.ui-disabled"));
                }
            }
            default -> sendHelp(player, label);
        }
        return true;
    }

    private void sendHelp(Player p, String label) {
        p.sendMessage(service.msg("help.title"));
        for (String k : List.of("open", "enter", "leave", "list", "info", "stamina", "party", "rank")) {
            p.sendMessage(service.msg("help." + k, "label", label));
        }
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
                    player.sendMessage(service.msg("error.already-member"));
                    return true;
                }
                pm.create(player);
                player.sendMessage(service.msg("party.created"));
            }
            case "invite" -> {
                if (args.length < 2) {
                    player.sendMessage(service.msg("usage.invite", "label", label));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage(service.msg("error.player-not-found", "player", args[1]));
                    return true;
                }
                if (target.getUniqueId().equals(player.getUniqueId())) {
                    player.sendMessage(service.msg("error.self-invite"));
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
                p.broadcast(service.msg("party.invite-broadcast",
                    "player", player.getName(), "target", target.getName()));
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
                    player.sendMessage(service.msg("usage.kick", "label", label));
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
                    player.sendMessage(service.msg("usage.leader", "label", label));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null || !party.isMember(target.getUniqueId())) {
                    player.sendMessage(service.msg("error.target-not-in-party"));
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
                    player.sendMessage(service.msg(on ? "party.chat-on" : "party.chat-off"));
                }
            }
            case "list", "members" -> {
                if (party == null) {
                    player.sendMessage(service.msg("error.not-in-party"));
                    return true;
                }
                player.sendMessage(service.msg("party.list-title", "name", party.name()));
                for (java.util.UUID u : party.members()) {
                    Player m = Bukkit.getPlayer(u);
                    player.sendMessage(service.msg("party.list-row",
                        "mark", party.isLeader(u) ? "&6★" : "&7-",
                        "name", m != null ? m.getName() : u.toString().substring(0, 8),
                        "offline", m == null ? " &8(离线)" : ""));
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
        p.sendMessage(service.msg("party-help.title"));
        for (String k : List.of("create", "invite", "accept", "leave", "chat",
                "list", "kick", "leader", "disband")) {
            p.sendMessage(service.msg("party-help." + k, "label", label));
        }
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
