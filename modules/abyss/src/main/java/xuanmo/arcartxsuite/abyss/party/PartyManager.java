package xuanmo.arcartxsuite.abyss.party;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.abyss.config.AbyssModuleConfiguration;

/**
 * 队伍管理器：创建/解散/成员管理/邀请/聊天/仓库/掉落分配/经验共享。
 */
public final class PartyManager {

    private final Map<UUID, Party> playerParty = new ConcurrentHashMap<>();  // playerUuid -> party
    private final Map<UUID, Party> parties = new ConcurrentHashMap<>();      // partyId -> party
    /** 邀请冷却：inviter:target -> 上次邀请时间（毫秒）。 */
    private final Map<String, Long> inviteCooldowns = new ConcurrentHashMap<>();
    /** 关闭"允许被邀请"的玩家（组队大厅免打扰开关）。 */
    private final Set<UUID> inviteOptOut = ConcurrentHashMap.newKeySet();
    private final AbyssModuleConfiguration.PartySection cfg;
    private final Consumer<String> messageFormatter; // 前缀格式化（messages().get）

    /** 邀请冷却时长（毫秒）。 */
    public static final long INVITE_COOLDOWN_MS = 60_000L;

    public PartyManager(AbyssModuleConfiguration.PartySection cfg,
                        Consumer<String> messageFormatter) {
        this.cfg = cfg;
        this.messageFormatter = messageFormatter;
    }

    public @Nullable Party partyOf(UUID player) { return playerParty.get(player); }
    public @Nullable Party partyOf(Player p) { return playerParty.get(p.getUniqueId()); }
    public boolean inParty(UUID player) { return playerParty.containsKey(player); }

    public Party create(Player leader) {
        Party existing = playerParty.get(leader.getUniqueId());
        if (existing != null) return existing;
        Party party = new Party(leader.getUniqueId(), cfg.maxMembers());
        parties.put(party.id(), party);
        playerParty.put(leader.getUniqueId(), party);
        return party;
    }

    /** 邀请结果。 */
    public sealed interface InviteResult {
        record Ok(Party party) implements InviteResult { }
        record Cooldown(long remainingSeconds) implements InviteResult { }
        record OptOut() implements InviteResult { }
        OptOut OPT_OUT = new OptOut();
        record SelfInvite() implements InviteResult { }
        SelfInvite SELF_INVITE = new SelfInvite();
        record TargetInParty() implements InviteResult { }
        TargetInParty TARGET_IN_PARTY = new TargetInParty();
    }

    /**
     * 邀请玩家（自动建队）。带邀请冷却（同一邀请者对同一目标 60 秒）
     * 和目标免打扰（组队大厅关闭"允许被邀请"）检查。
     */
    public InviteResult invite(Player inviter, Player target) {
        if (inviter.getUniqueId().equals(target.getUniqueId())) {
            return InviteResult.SELF_INVITE;
        }
        if (inParty(target.getUniqueId())) {
            return InviteResult.TARGET_IN_PARTY;
        }
        if (inviteOptOut.contains(target.getUniqueId())) {
            return InviteResult.OPT_OUT;
        }
        String cdKey = inviter.getUniqueId() + ":" + target.getUniqueId();
        Long last = inviteCooldowns.get(cdKey);
        long now = System.currentTimeMillis();
        if (last != null && now - last < INVITE_COOLDOWN_MS) {
            long remain = (INVITE_COOLDOWN_MS - (now - last) + 999) / 1000;
            return new InviteResult.Cooldown(remain);
        }
        Party party = playerParty.computeIfAbsent(inviter.getUniqueId(),
            k -> {
                Party p = new Party(k, cfg.maxMembers());
                parties.put(p.id(), p);
                return p;
            });
        inviteCooldowns.put(cdKey, now);
        party.invite(target.getUniqueId(), cfg.inviteTimeout());
        party.appendFeed(null, "已向 " + target.getName() + " 发送邀请");
        return new InviteResult.Ok(party);
    }

    /** 查询玩家当前待处理的邀请来源（邀请方队长名列表，UI 展示用）。 */
    public java.util.List<String> inviteSources(UUID player) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (Party p : parties.values()) {
            if (p.hasInvite(player)) {
                org.bukkit.entity.Player leader = org.bukkit.Bukkit.getPlayer(p.leader());
                out.add(leader != null ? leader.getName()
                    : p.leader().toString().substring(0, 8));
            }
        }
        return out;
    }

    /** 收到的邀请条目（大厅 UI 用：可定位队伍 + 显示队名/邀请人）。 */
    public record InviteEntry(UUID partyId, String partyName, String from) { }

    public java.util.List<InviteEntry> inviteEntries(UUID player) {
        java.util.List<InviteEntry> out = new java.util.ArrayList<>();
        for (Party p : parties.values()) {
            if (p.hasInvite(player)) {
                org.bukkit.entity.Player leader = org.bukkit.Bukkit.getPlayer(p.leader());
                out.add(new InviteEntry(p.id(), p.name(),
                    leader != null ? leader.getName()
                        : p.leader().toString().substring(0, 8)));
            }
        }
        return out;
    }

    /** 接受邀请。 */
    public JoinResult accept(Player player) {
        for (Party p : parties.values()) {
            if (p.hasInvite(player.getUniqueId())) {
                return accept(player, p.id());
            }
        }
        return JoinResult.NO_INVITE;
    }

    /** 接受指定队伍的邀请（组队大厅入口）。 */
    public JoinResult accept(Player player, UUID partyId) {
        Party p = parties.get(partyId);
        if (p == null || !p.hasInvite(player.getUniqueId())) {
            return JoinResult.NO_INVITE;
        }
        if (p.size() >= cfg.maxMembers()) return JoinResult.FULL;
        if (p.addMember(player.getUniqueId(), cfg.maxMembers())) {
            playerParty.put(player.getUniqueId(), p);
            return new JoinResult.Ok(p);
        }
        return JoinResult.NO_INVITE;
    }

    /** 拒绝指定队伍的邀请。 */
    public boolean declineInvite(Player player, UUID partyId) {
        Party p = parties.get(partyId);
        if (p == null) return false;
        // hasInvite 会顺带清理过期邀请；未过期才允许显式拒绝
        if (!p.hasInvite(player.getUniqueId())) return false;
        p.inviteRemove(player.getUniqueId());
        return true;
    }

    // ═══ 组队大厅 ═══

    /** 大厅条目（公开队伍摘要）。 */
    public record LobbyEntry(UUID partyId, String name, String leaderName,
                             int size, int max, String dungeonId,
                             Party.JoinMode joinMode, boolean full) { }

    /**
     * 查询公开队伍列表（组队大厅）。
     * @param dungeonFilter 只显示目标副本为该 id 的队伍；null/空 = 不过滤
     * @param keyword       队伍名/队长名包含关键字；null/空 = 不过滤
     */
    public java.util.List<LobbyEntry> listPublic(@Nullable String dungeonFilter,
                                                 @Nullable String keyword) {
        String df = dungeonFilter == null ? "" : dungeonFilter.trim().toLowerCase();
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();
        java.util.List<LobbyEntry> out = new java.util.ArrayList<>();
        for (Party p : parties.values()) {
            if (!p.listed()) continue;
            if (!df.isEmpty() && (p.dungeonId() == null
                || !p.dungeonId().equalsIgnoreCase(df))) continue;
            Player leader = org.bukkit.Bukkit.getPlayer(p.leader());
            String leaderName = leader != null ? leader.getName()
                : p.leader().toString().substring(0, 8);
            if (!kw.isEmpty()) {
                boolean hit = p.name().toLowerCase().contains(kw)
                    || leaderName.toLowerCase().contains(kw);
                if (!hit) continue;
            }
            out.add(new LobbyEntry(p.id(), p.name(), leaderName,
                p.size(), cfg.maxMembers(),
                p.dungeonId() == null ? "" : p.dungeonId(),
                p.joinMode(), p.size() >= cfg.maxMembers()));
        }
        out.sort((a, b) -> Integer.compare(b.size(), a.size()));
        return out;
    }

    /** 加入结果。 */
    public sealed interface JoinLobbyResult {
        record Ok(Party party) implements JoinLobbyResult { }
        record Applied() implements JoinLobbyResult { }
        record NotFound() implements JoinLobbyResult { }
        record AlreadyMember() implements JoinLobbyResult { }
        record Full() implements JoinLobbyResult { }
        record NotPublic() implements JoinLobbyResult { }
        Applied APPLIED = new Applied();
        NotFound NOT_FOUND = new NotFound();
        AlreadyMember ALREADY_MEMBER = new AlreadyMember();
        Full FULL = new Full();
        NotPublic NOT_PUBLIC = new NotPublic();
    }

    /**
     * 大厅加入：OPEN 队直接加入，APPLY 队提交申请待队长审批。
     */
    public JoinLobbyResult joinViaLobby(Player player, UUID partyId) {
        if (inParty(player.getUniqueId())) return JoinLobbyResult.ALREADY_MEMBER;
        Party p = parties.get(partyId);
        if (p == null) return JoinLobbyResult.NOT_FOUND;
        if (!p.listed()) return JoinLobbyResult.NOT_PUBLIC;
        if (p.size() >= cfg.maxMembers()) return JoinLobbyResult.FULL;
        if (p.joinMode() == Party.JoinMode.OPEN) {
            if (p.addMember(player.getUniqueId(), cfg.maxMembers())) {
                playerParty.put(player.getUniqueId(), p);
                return new JoinLobbyResult.Ok(p);
            }
            return JoinLobbyResult.FULL;
        }
        p.apply(player.getUniqueId());
        return JoinLobbyResult.APPLIED;
    }

    /** 队长审批：同意加入。 */
    public JoinResult approveApplication(Player leader, UUID applicant) {
        Party p = partyOf(leader);
        if (p == null || !p.isLeader(leader.getUniqueId())) return JoinResult.NO_INVITE;
        if (!p.takeApplication(applicant)) return JoinResult.NO_INVITE;
        if (p.size() >= cfg.maxMembers()) return JoinResult.FULL;
        if (p.addMember(applicant, cfg.maxMembers())) {
            playerParty.put(applicant, p);
            return new JoinResult.Ok(p);
        }
        return JoinResult.NO_INVITE;
    }

    /** 队长审批：拒绝。 */
    public boolean denyApplication(Player leader, UUID applicant) {
        Party p = partyOf(leader);
        if (p == null || !p.isLeader(leader.getUniqueId())) return false;
        return p.denyApplication(applicant);
    }

    // ═══ 免打扰开关 ═══

    /** 设置是否允许被邀请（组队大厅开关）。返回设置后的状态。 */
    public boolean setInviteAllowed(UUID player, boolean allow) {
        if (allow) inviteOptOut.remove(player);
        else inviteOptOut.add(player);
        return allow;
    }

    public boolean inviteAllowed(UUID player) {
        return !inviteOptOut.contains(player);
    }

    /** 切换成员准备状态。 */
    public boolean toggleReady(Player player) {
        Party p = partyOf(player);
        if (p == null || p.isLeader(player.getUniqueId())) return true;
        boolean ready = p.toggleReady(player.getUniqueId());
        p.appendFeed(null, player.getName() + (ready ? " 已准备" : " 取消准备"));
        return ready;
    }

    public sealed interface JoinResult {
        record Ok(Party party) implements JoinResult { }
        record NoInvite() implements JoinResult { }
        NoInvite NO_INVITE = new NoInvite();
        record Full() implements JoinResult { }
        Full FULL = new Full();
    }

    /** 离开队伍（队长离开→移交最早加入者；空队解散）。 */
    public void leave(Player player) {
        Party p = playerParty.remove(player.getUniqueId());
        if (p == null) return;
        p.removeMember(player.getUniqueId());
        if (p.size() == 0) {
            parties.remove(p.id());
        }
    }

    public void kick(Player leader, Player target) {
        kick(leader, target.getUniqueId());
    }

    /** 按 UUID 踢人（支持离线成员）。 */
    public void kick(Player leader, java.util.UUID targetUuid) {
        Party p = partyOf(leader);
        if (p == null || !p.isLeader(leader.getUniqueId())) return;
        if (targetUuid.equals(leader.getUniqueId())) return;
        if (p.removeMember(targetUuid)) {
            playerParty.remove(targetUuid);
        }
    }

    public void transferLeader(Player leader, Player target) {
        transferLeader(leader, target.getUniqueId());
    }

    /** 按 UUID 转让队长（支持离线成员）。 */
    public void transferLeader(Player leader, java.util.UUID targetUuid) {
        Party p = partyOf(leader);
        if (p == null || !p.isLeader(leader.getUniqueId())) return;
        if (!p.isMember(targetUuid)) return;
        p.setLeader(targetUuid);
    }

    public void disband(Party party) {
        for (UUID u : party.members()) playerParty.remove(u);
        parties.remove(party.id());
    }

    /** 队伍聊天消息分发（监听器转发聊天事件），同时记入房间消息流。 */
    public void sendChat(Player sender, String message) {
        Party p = partyOf(sender);
        if (p == null) return;
        String formatted = "§d[队伍] §f" + sender.getName() + "§7: §f" + message;
        for (Player m : p.onlineMembers()) {
            m.sendMessage(formatted);
        }
        p.appendFeed(sender.getName(), message);
    }

    /** 玩家断线清理（不移出队伍——允许重连回队；单人队自动解散）。 */
    public void onQuit(Player player) {
        Party p = partyOf(player);
        if (p != null && p.onlineMembers().size() <= 1 && p.size() == 1) {
            leave(player);
        }
    }

    public int maxMembers() { return cfg.maxMembers(); }
    public int inviteTimeout() { return cfg.inviteTimeout(); }
    public boolean allowCustomName() { return cfg.allowCustomName(); }

    public void shutdown() {
        playerParty.clear();
        parties.clear();
        inviteCooldowns.clear();
        inviteOptOut.clear();
    }
}
