package xuanmo.arcartxsuite.abyss.party;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.abyss.config.AbyssModuleConfiguration;
import xuanmo.arcartxsuite.api.capability.AbyssPartyAccess;

/**
 * 队伍管理器：创建/解散/成员管理/邀请/大厅审批/聊天。
 * <p>
 * 对外扩展点：
 * <ul>
 *   <li>{@link Party#mode()} 队伍模式标记（free/guild/外部自定义），供公会团本等
 *       场景区分队伍类型，并可被地牢模板 {@code requirements.party-mode} 校验</li>
 *   <li>{@link Party#maxSize()} 每队人数上限（覆盖全局默认），公会 10 人队等</li>
 *   <li>{@link #registerJoinValidator} 按 mode 注册加入校验（同公会检查等）</li>
 *   <li>{@link PartyEventSink} 队伍生命周期事件（EventBus 发布）</li>
 * </ul>
 */
public final class PartyManager {

    private final Map<UUID, Party> playerParty = new ConcurrentHashMap<>();  // playerUuid -> party
    private final Map<UUID, Party> parties = new ConcurrentHashMap<>();      // partyId -> party
    /** 邀请冷却：inviter:target -> 上次邀请时间（毫秒）。 */
    private final Map<String, Long> inviteCooldowns = new ConcurrentHashMap<>();
    /** 关闭"允许被邀请"的玩家（组队大厅免打扰开关）。 */
    private final Set<UUID> inviteOptOut = ConcurrentHashMap.newKeySet();
    /** 按 mode 注册的玩家加入校验器（外部模块注入，如同公会校验）。 */
    private final Map<String, AbyssPartyAccess.JoinValidator> joinValidators = new ConcurrentHashMap<>();
    private final AbyssModuleConfiguration.PartySection cfg;
    private final Consumer<String> messageFormatter; // 前缀格式化（messages().get）
    /** 队伍事件发布（AbyssService.publishEvent 注入；null 时跳过）。 */
    private final @Nullable PartyEventSink events;
    private java.util.function.Consumer<Party> changeListener=p->{};
    public void setChangeListener(java.util.function.Consumer<Party> listener) { changeListener=listener; }

    /** 邀请冷却时长（毫秒）。 */
    public static final long INVITE_COOLDOWN_MS = 60_000L;

    /** 队伍生命周期事件出口（对齐 AbyssService.publishEvent 签名）。 */
    @FunctionalInterface
    public interface PartyEventSink {
        void publish(String topic, @Nullable Player player, Map<String, Object> payload);
    }

    public PartyManager(AbyssModuleConfiguration.PartySection cfg,
                        Consumer<String> messageFormatter,
                        @Nullable PartyEventSink events) {
        this.cfg = cfg;
        this.messageFormatter = messageFormatter;
        this.events = events;
    }

    // ═══ 查询 ═══

    public @Nullable Party partyOf(UUID player) { return playerParty.get(player); }
    public @Nullable Party partyOf(Player p) { return playerParty.get(p.getUniqueId()); }
    public boolean inParty(UUID player) { return playerParty.containsKey(player); }
    public @Nullable Party getParty(UUID partyId) { return parties.get(partyId); }
    public java.util.Collection<Party> allParties() { return parties.values(); }

    // ═══ 创建/校验钩子 ═══

    public Party create(Player leader) {
        return create(leader, "free", cfg.maxMembers());
    }

    /**
     * 创建指定模式/上限的队伍（公会团本等场景编程式建队）。
     *
     * @param mode    队伍模式（"free"/"guild"/外部自定义，null/空=free）
     * @param maxSize 人数上限（<=0 取全局 party.max-members）
     * @return 已存在的队伍（玩家已有队时返回原队，不改模式）
     */
    public Party create(Player leader, @Nullable String mode, int maxSize) {
        Party existing = playerParty.get(leader.getUniqueId());
        if (existing != null) return existing;
        int cap = maxSize > 0 ? maxSize : cfg.maxMembers();
        Party party = new Party(leader.getUniqueId(), cap);
        party.mode(mode);
        parties.put(party.id(), party);
        playerParty.put(leader.getUniqueId(), party);
        party.onChange(()->changeListener.accept(party));
        fire("axs.abyss.party_created", leader, Map.of(
            "party", party.id().toString(),
            "mode", party.mode(),
            "max_size", String.valueOf(party.maxSize())));
        return party;
    }

    /**
     * 按 mode 注册加入校验器：任何玩家进入该模式队伍前回调。
     * 返回 null=允许；非 null 字符串=拒绝原因（发给申请人）。
     * 同 mode 重复注册覆盖。传 null validator 注销。
     */
    public void registerJoinValidator(String mode, @Nullable AbyssPartyAccess.JoinValidator v) {
        if (mode == null || mode.isBlank()) return;
        if (v == null) joinValidators.remove(mode);
        else joinValidators.put(mode, v);
    }

    /**
     * 加入校验：命中该 mode 的 validator 时执行。返回 null=允许。
     */
    private @Nullable String checkJoin(Party party, UUID applicant) {
        if (inParty(applicant)) return "already in party";
        AbyssPartyAccess.JoinValidator v = joinValidators.get(party.mode());
        if (v == null) return null;
        try {
            return v.check(snapshot(party), applicant);
        } catch (Throwable t) {
            return "join validator error";
        }
    }

    /** 队伍快照（对外不可变视图）。 */
    public AbyssPartyAccess.PartySnapshot snapshot(Party p) {
        return new AbyssPartyAccess.PartySnapshot(
            p.id(), p.name(), p.mode(), p.leader(), p.members(),
            p.maxSize(), p.dungeonId());
    }

    // ═══ 邀请 ═══

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
                fire("axs.abyss.party_created", inviter, Map.of(
                    "party", p.id().toString(),
                    "mode", p.mode(),
                    "max_size", String.valueOf(p.maxSize())));
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
        if (p.size() >= p.maxSize()) return JoinResult.FULL;
        String deny = checkJoin(p, player.getUniqueId());
        if (deny != null) return new JoinResult.Denied(deny);
        if (p.addMember(player.getUniqueId())) {
            playerParty.put(player.getUniqueId(), p);
            fireJoin(p, player);
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
                p.size(), p.maxSize(),
                p.dungeonId() == null ? "" : p.dungeonId(),
                p.joinMode(), p.size() >= p.maxSize()));
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
        record Denied(String reason) implements JoinLobbyResult { }
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
        if (p.size() >= p.maxSize()) return JoinLobbyResult.FULL;
        if (p.joinMode() == Party.JoinMode.OPEN) {
            String deny = checkJoin(p, player.getUniqueId());
            if (deny != null) return new JoinLobbyResult.Denied(deny);
            if (p.addMember(player.getUniqueId())) {
                playerParty.put(player.getUniqueId(), p);
                fireJoin(p, player);
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
        if (p.size() >= p.maxSize()) return JoinResult.FULL;
        String deny = checkJoin(p, applicant);
        if (deny != null) return new JoinResult.Denied(deny);
        if (p.addMember(applicant)) {
            playerParty.put(applicant, p);
            Player ap = org.bukkit.Bukkit.getPlayer(applicant);
            if (ap != null) fireJoin(p, ap);
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
        record Denied(String reason) implements JoinResult { }
    }

    // ═══ 离开/踢出/转让/解散 ═══

    /** 离开队伍（队长离开→移交最早加入者；空队解散）。 */
    public void leave(Player player) {
        Party p = playerParty.remove(player.getUniqueId());
        if (p == null) return;
        boolean wasLeader = p.isLeader(player.getUniqueId());
        p.removeMember(player.getUniqueId());
        fire("axs.abyss.party_member_leave", player, Map.of(
            "party", p.id().toString(),
            "mode", p.mode(),
            "reason", "quit"));
        if (p.size() == 0) {
            parties.remove(p.id());
            fire("axs.abyss.party_disbanded", null, Map.of(
                "party", p.id().toString(),
                "mode", p.mode()));
        } else if (wasLeader) {
            // 队长离开：removeMember 已自动移交最早成员 → 发队长变更
            Player nl = org.bukkit.Bukkit.getPlayer(p.leader());
            fire("axs.abyss.party_leader_change", nl, Map.of(
                "party", p.id().toString(),
                "mode", p.mode()));
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
            Player t = org.bukkit.Bukkit.getPlayer(targetUuid);
            fire("axs.abyss.party_member_leave", t, Map.of(
                "party", p.id().toString(),
                "mode", p.mode(),
                "reason", "kick"));
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
        Player t = org.bukkit.Bukkit.getPlayer(targetUuid);
        fire("axs.abyss.party_leader_change", t, Map.of(
            "party", p.id().toString(),
            "mode", p.mode()));
    }

    /**
     * 编程式直加成员（公会拉人等场景；仍过加入校验钩子和上限）。
     * @return null=成功；非 null=失败原因
     */
    public @Nullable String addMemberDirect(UUID partyId, UUID player) {
        Party p = parties.get(partyId);
        if (p == null) return "party not found";
        if (inParty(player)) return "already in party";
        if (p.size() >= p.maxSize()) return "party full";
        String deny = checkJoin(p, player);
        if (deny != null) return deny;
        if (!p.addMember(player)) return "join failed";
        playerParty.put(player, p);
        Player bp = org.bukkit.Bukkit.getPlayer(player);
        if (bp != null) fireJoin(p, bp);
        return null;
    }

    /**
     * 编程式移除成员（支持离线；队长离开自动移交，空队解散）。
     * @return false=队伍不存在或目标非成员
     */
    public boolean removeMember(UUID partyId, UUID player) {
        Party p = parties.get(partyId);
        if (p == null || !p.isMember(player)) return false;
        boolean wasLeader = p.isLeader(player);
        p.removeMember(player);
        playerParty.remove(player);
        Player bp = org.bukkit.Bukkit.getPlayer(player);
        fire("axs.abyss.party_member_leave", bp, Map.of(
            "party", p.id().toString(),
            "mode", p.mode(),
            "reason", "removed"));
        if (p.size() == 0) {
            parties.remove(p.id());
            fire("axs.abyss.party_disbanded", null, Map.of(
                "party", p.id().toString(),
                "mode", p.mode()));
        } else if (wasLeader) {
            Player nl = org.bukkit.Bukkit.getPlayer(p.leader());
            fire("axs.abyss.party_leader_change", nl, Map.of(
                "party", p.id().toString(),
                "mode", p.mode()));
        }
        return true;
    }

    public void disband(Party party) {
        for (UUID u : party.members()) playerParty.remove(u);
        parties.remove(party.id());
        party.changed();
        fire("axs.abyss.party_disbanded", null, Map.of(
            "party", party.id().toString(),
            "mode", party.mode()));
    }

    /** 队伍聊天消息分发（监听器转发聊天事件），同时记入房间消息流。 */
    public void sendChat(Player sender, String message) {
        if(message==null || message.isBlank())return;
        if(message.length()>256)throw new IllegalArgumentException("队聊内容最多256字符");
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

    private void fireJoin(Party p, Player member) {
        fire("axs.abyss.party_member_join", member, Map.of(
            "party", p.id().toString(),
            "mode", p.mode(),
            "size", String.valueOf(p.size())));
    }

    private void fire(String topic, @Nullable Player player, Map<String, Object> payload) {
        if (events == null) return;
        try {
            events.publish(topic, player, payload);
        } catch (Throwable ignored) { }
    }

    public void shutdown() {
        playerParty.clear();
        parties.clear();
        inviteCooldowns.clear();
        inviteOptOut.clear();
        joinValidators.clear();
    }
}
