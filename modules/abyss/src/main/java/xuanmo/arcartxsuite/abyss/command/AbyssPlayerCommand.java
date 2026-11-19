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
                // 可选第三参：作战方案 id（模板定义了 plans 时生效）
                String plan = args.length >= 3 ? args[2] : null;
                var r = service.enterFromPlayer(player, args[1], plan);
                if (!r.ok()) player.sendMessage(service.msg("error.enter-failed", "reason", r.reason()));
            }
            case "rejoin" -> {
                var access = service.reconnectAccess();
                var status = access.reconnectStatus(player.getUniqueId());
                if (status.eligible()) status = access.requestReconnect(player.getUniqueId(), status.runId());
                player.sendMessage(status.code().equals("REJOINED") ? "§a已回到原副本。" : "§e无法回归副本：" + status.code());
            }
            case "leave", "quit" -> {
                AbyssInstance i = service.instances().getInstance(player);
                if (i == null) {
                    player.sendMessage(service.msg("error.not-in-dungeon"));
                    return true;
                }
                service.nativeRuns().leave(player);
            }
            case "run", "tactical", "rescue" -> service.nativeRuns().open(player, "rescue");
            case "loot" -> service.nativeRuns().open(player, "loot");
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
            case "rewards" -> service.rewardGames().openPending(player);
            case "stats", "result" -> {
                AbyssInstance instance=service.instances().getInstance(player);
                if(service.uiFacade() instanceof xuanmo.arcartxsuite.abyss.ui.AbyssUiService ui) {
                    if(instance!=null)ui.openStats(player,instance);
                    else if(!ui.openRecentStats(player))player.sendMessage("§e当前没有可查看的结算，请在本次登录内完成副本后查看。");
                }else player.sendMessage(service.msg("error.ui-disabled"));
            }
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
        p.sendMessage("§e/"+label+" stats §7- 查看当前统计或本次登录最近的结算");
        p.sendMessage("§e/"+label+" run §7- 自救、队友救援和撤离；/"+label+" loot §7- 本局战利品");
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
                var result = pm.invite(player, target);
                if (result instanceof PartyManager.InviteResult.Ok ok) {
                    player.sendMessage(service.msg("party.invited",
                        "player", target.getName(), "timeout", String.valueOf(pm.inviteTimeout())));
                    target.sendMessage(service.msg("party.invite-received", "leader", player.getName()));
                    pushUpdate(ok.party());
                } else if (result instanceof PartyManager.InviteResult.Cooldown cd) {
                    player.sendMessage(service.msg("party.invite-cooldown",
                        "seconds", String.valueOf(cd.remainingSeconds())));
                } else if (result instanceof PartyManager.InviteResult.OptOut) {
                    player.sendMessage(service.msg("party.invite-opted-out",
                        "player", target.getName()));
                } else if (result instanceof PartyManager.InviteResult.SelfInvite) {
                    player.sendMessage(service.msg("error.self-invite"));
                } else {
                    player.sendMessage(service.msg("error.already-in-party"));
                }
            }
            case "accept" -> {
                PartyManager.JoinResult result;
                if (args.length == 1) {
                    result = pm.accept(player);
                } else {
                    try {
                        result = pm.accept(player, java.util.UUID.fromString(args[1]));
                    } catch (IllegalArgumentException exception) {
                        result = PartyManager.JoinResult.NO_INVITE;
                    }
                }
                if (result instanceof PartyManager.JoinResult.Ok ok) {
                    ok.party().appendFeed(null, player.getName() + " 加入了队伍");
                    ok.party().broadcast(service.msg("party.joined", "player", player.getName()));
                    pushUpdate(ok.party());
                } else if (result instanceof PartyManager.JoinResult.Full) {
                    player.sendMessage(service.msg("error.party-full"));
                } else if (result instanceof PartyManager.JoinResult.Denied d) {
                    player.sendMessage(service.msg("error.join-denied", "reason", d.reason()));
                } else {
                    player.sendMessage(service.msg("error.invite-expired"));
                }
            }
            case "leave" -> {
                if (party == null) {
                    player.sendMessage(service.msg("error.not-in-party"));
                    return true;
                }
                party.appendFeed(null, player.getName() + " 离开了队伍");
                pm.leave(player);
                player.sendMessage(service.msg("party.left", "player", player.getName()));
                party.broadcast(service.msg("party.left", "player", player.getName()));
                pushUpdate(party);
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
                party.appendFeed(null, target.getName() + " 被移出队伍");
                party.broadcast(service.msg("party.kicked", "player", target.getName()));
                target.sendMessage(service.msg("party.kicked", "player", target.getName()));
                pushUpdate(party);
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
                party.appendFeed(null, target.getName() + " 成为新队长");
                party.broadcast(service.msg("party.leader-changed", "player", target.getName()));
                pushUpdate(party);
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
            case "ready" -> {
                if (party == null) {
                    player.sendMessage(service.msg("error.not-in-party"));
                    return true;
                }
                boolean ready = args.length > 1 && ("on".equalsIgnoreCase(args[1]) || "off".equalsIgnoreCase(args[1]))
                    ? pm.setReady(player, "on".equalsIgnoreCase(args[1])) : pm.toggleReady(player);
                player.sendMessage(service.msg(ready ? "party.ready-on" : "party.ready-off"));
                pushUpdate(party);
            }
            case "public", "open" -> {
                if (party == null || !party.isLeader(player.getUniqueId())) {
                    player.sendMessage(service.msg("error.not-party-leader"));
                    return true;
                }
                party.listed(true);
                player.sendMessage(service.msg("party.listed-on"));
                pushUpdate(party);
            }
            case "private" -> {
                if (party == null || !party.isLeader(player.getUniqueId())) {
                    player.sendMessage(service.msg("error.not-party-leader"));
                    return true;
                }
                party.listed(false);
                player.sendMessage(service.msg("party.listed-off"));
                pushUpdate(party);
            }
            case "joinmode" -> {
                if (party == null || !party.isLeader(player.getUniqueId())) {
                    player.sendMessage(service.msg("error.not-party-leader"));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(service.msg("usage.joinmode", "label", label));
                    return true;
                }
                party.joinMode("open".equalsIgnoreCase(args[1])
                    ? Party.JoinMode.OPEN : Party.JoinMode.APPLY);
                player.sendMessage(service.msg("party.joinmode-set",
                    "mode", party.joinMode() == Party.JoinMode.OPEN ? "直接加入" : "需要审批"));
                pushUpdate(party);
            }
            case "dungeon" -> {
                if (party == null || !party.isLeader(player.getUniqueId())) {
                    player.sendMessage(service.msg("error.not-party-leader"));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(service.msg("usage.dungeon", "label", label));
                    return true;
                }
                AbyssTemplate t = service.instances().getTemplate(args[1]);
                if (t == null) {
                    player.sendMessage(service.msg("error.dungeon-not-found",
                        "dungeon", args[1]));
                    return true;
                }
                party.dungeonId(t.id());
                party.appendFeed(null, "目标副本已设置为 " + t.name());
                player.sendMessage(service.msg("party.dungeon-set", "name", t.name()));
                pushUpdate(party);
            }
            case "apply" -> {
                if (args.length < 2) {
                    player.sendMessage(service.msg("usage.apply", "label", label));
                    return true;
                }
                java.util.UUID pid;
                try {
                    pid = java.util.UUID.fromString(args[1]);
                } catch (IllegalArgumentException e) {
                    player.sendMessage(service.msg("error.party-not-found"));
                    return true;
                }
                var r = pm.joinViaLobby(player, pid);
                if (r instanceof PartyManager.JoinLobbyResult.Ok ok) {
                    player.sendMessage(service.msg("party.joined", "player", player.getName()));
                    ok.party().appendFeed(null, player.getName() + " 加入了队伍");
                    pushUpdate(ok.party());
                } else if (r instanceof PartyManager.JoinLobbyResult.Applied) {
                    player.sendMessage(service.msg("party.apply-sent"));
                } else if (r instanceof PartyManager.JoinLobbyResult.Full) {
                    player.sendMessage(service.msg("error.party-full"));
                } else if (r instanceof PartyManager.JoinLobbyResult.AlreadyMember) {
                    player.sendMessage(service.msg("error.already-member"));
                } else if (r instanceof PartyManager.JoinLobbyResult.Denied d) {
                    player.sendMessage(service.msg("error.join-denied", "reason", d.reason()));
                } else {
                    player.sendMessage(service.msg("error.party-not-found"));
                }
            }
            case "approve" -> {
                if (party == null || !party.isLeader(player.getUniqueId())) {
                    player.sendMessage(service.msg("error.not-party-leader"));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(service.msg("usage.approve", "label", label));
                    return true;
                }
                Player ap = Bukkit.getPlayer(args[1]);
                if (ap == null) {
                    player.sendMessage(service.msg("error.player-not-found", "player", args[1]));
                    return true;
                }
                var r = pm.approveApplication(player, ap.getUniqueId());
                if (r instanceof PartyManager.JoinResult.Ok ok) {
                    ok.party().appendFeed(null, ap.getName() + " 加入了队伍");
                    ok.party().broadcast(service.msg("party.joined", "player", ap.getName()));
                    pushUpdate(ok.party());
                } else if (r instanceof PartyManager.JoinResult.Denied d) {
                    player.sendMessage(service.msg("error.join-denied", "reason", d.reason()));
                } else {
                    player.sendMessage(service.msg("error.no-application", "player", args[1]));
                }
            }
            case "deny" -> {
                if (party == null || !party.isLeader(player.getUniqueId())) {
                    player.sendMessage(service.msg("error.not-party-leader"));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(service.msg("usage.deny", "label", label));
                    return true;
                }
                Player ap = Bukkit.getPlayer(args[1]);
                if (ap == null || !pm.denyApplication(player, ap.getUniqueId())) {
                    player.sendMessage(service.msg("error.no-application", "player", args[1]));
                    return true;
                }
                player.sendMessage(service.msg("party.application-denied", "player", ap.getName()));
                pushUpdate(party);
            }
            case "allowinvite" -> {
                boolean allow = !pm.inviteAllowed(player.getUniqueId());
                pm.setInviteAllowed(player.getUniqueId(), allow);
                player.sendMessage(service.msg(allow
                    ? "party.invite-allowed-on" : "party.invite-allowed-off"));
            }
            case "lobby" -> {
                if (service.uiFacade() != null) {
                    service.uiFacade().openLobby(player);
                } else {
                    // 无 UI 时列出公开队伍
                    player.sendMessage(service.msg("party.lobby-title"));
                    for (var e : pm.listPublic(null, null)) {
                        player.sendMessage(service.msg("party.lobby-row",
                            "name", e.name(), "leader", e.leaderName(),
                            "size", String.valueOf(e.size()),
                            "max", String.valueOf(e.max()),
                            "dungeon", e.dungeonId(),
                            "mode", e.joinMode() == Party.JoinMode.OPEN ? "直接加入" : "需审批"));
                    }
                }
            }
            default -> sendPartyHelp(player, label);
        }
        return true;
    }

    private void sendPartyHelp(Player p, String label) {
        p.sendMessage(service.msg("party-help.title"));
        for (String k : List.of("create", "invite", "accept", "leave", "ready",
                "chat", "list", "public", "joinmode", "dungeon", "apply",
                "approve", "deny", "allowinvite", "kick", "leader", "disband")) {
            p.sendMessage(service.msg("party-help." + k, "label", label));
        }
    }

    /** 队伍变更后向房间 UI 推送更新（无 UI 桥接时静默跳过）。 */
    private void pushUpdate(Party party) {
        if (party != null && service.uiFacade() != null) {
            service.uiFacade().pushPartyUpdate(party);
        }
    }

    private static String[] dropFirst(String[] args) {
        return java.util.Arrays.copyOfRange(args, 1, args.length);
    }

    /** /party 命令的子命令列表（供 abyss/party 两个入口的 tab 补全共用）。 */
    public static List<String> partySubCommands() {
        return List.of("create", "invite", "accept", "apply", "approve", "deny",
            "leave", "ready", "chat", "list", "public", "private", "joinmode",
            "dungeon", "allowinvite", "lobby", "kick", "leader", "disband");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String label,
                                                @NotNull String[] args) {
        boolean partyCmd = "party".equalsIgnoreCase(command.getName())
            || "axsparty".equalsIgnoreCase(command.getName());
        if (partyCmd) {
            return partyTabComplete(sender, args);
        }
        // /abyss ...
        if (args.length == 1) {
            return filter(List.of("enter", "rejoin", "leave", "list", "info", "stamina", "rewards",
                "rank", "party", "ui", "stats", "run", "tactical", "rescue", "loot"), args[0]);
        }
        if (args.length == 2 && ("enter".equalsIgnoreCase(args[0])
            || "rank".equalsIgnoreCase(args[0]) || "top".equalsIgnoreCase(args[0]))) {
            return filter(service.instances().getAllTemplates().stream()
                .map(AbyssTemplate::id).toList(), args[1]);
        }
        // enter 第三参：作战方案 id（该模板的 plans 键名）
        if (args.length == 3 && "enter".equalsIgnoreCase(args[0])) {
            AbyssTemplate t = service.instances().getTemplate(args[1]);
            return t != null
                ? filter(new ArrayList<>(t.plans().keySet()), args[2])
                : List.of();
        }
        if (args.length >= 2 && "party".equalsIgnoreCase(args[0])) {
            return partyTabComplete(sender,
                java.util.Arrays.copyOfRange(args, 1, args.length));
        }
        return List.of();
    }

    /** /party 系列补全：子命令 → 玩家名/副本 id/模式。 */
    public List<String> partyTabComplete(@NotNull CommandSender sender, String[] args) {
        if (args.length <= 1) {
            return filter(partySubCommands(), args.length == 0 ? "" : args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (sub) {
                case "invite", "approve", "deny" ->
                    filter(onlinePlayerNames(), args[1]);
                case "kick", "leader", "transfer" ->
                    filter(memberNames(sender), args[1]);
                case "joinmode" -> filter(List.of("open", "apply"), args[1]);
                case "dungeon" -> filter(service.instances().getAllTemplates().stream()
                    .map(AbyssTemplate::id).toList(), args[1]);
                case "apply" -> filter(service.party().listPublic(null, null).stream()
                    .map(e -> e.partyId().toString()).toList(), args[1]);
                default -> List.of();
            };
        }
        return List.of();
    }

    private static List<String> onlinePlayerNames() {
        List<String> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        return out;
    }

    /** 队伍成员名（供 kick/leader 补全）；发送者不在队伍时退回在线玩家名。 */
    private List<String> memberNames(CommandSender sender) {
        if (sender instanceof Player p) {
            Party party = service.party().partyOf(p);
            if (party != null) {
                List<String> out = new ArrayList<>();
                for (java.util.UUID u : party.members()) {
                    Player m = Bukkit.getPlayer(u);
                    out.add(m != null ? m.getName() : u.toString().substring(0, 8));
                }
                return out;
            }
        }
        return onlinePlayerNames();
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
