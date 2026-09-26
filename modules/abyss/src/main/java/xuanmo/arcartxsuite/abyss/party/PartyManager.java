package xuanmo.arcartxsuite.abyss.party;

import java.util.Map;
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
    private final AbyssModuleConfiguration.PartySection cfg;
    private final Consumer<String> messageFormatter; // 前缀格式化（messages().get）

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
        Party party = new Party(leader.getUniqueId(), cfg.maxMembers(), cfg.lootMode());
        parties.put(party.id(), party);
        playerParty.put(leader.getUniqueId(), party);
        return party;
    }

    /** 邀请玩家（自动建队）。 */
    public Party invite(Player inviter, Player target) {
        Party party = playerParty.computeIfAbsent(inviter.getUniqueId(),
            k -> {
                Party p = new Party(k, cfg.maxMembers(), cfg.lootMode());
                parties.put(p.id(), p);
                return p;
            });
        party.invite(target.getUniqueId(), cfg.inviteTimeout());
        return party;
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

    /** 接受邀请。 */
    public JoinResult accept(Player player) {
        for (Party p : parties.values()) {
            if (p.hasInvite(player.getUniqueId())) {
                if (p.size() >= cfg.maxMembers()) return JoinResult.FULL;
                if (p.addMember(player.getUniqueId(), cfg.maxMembers())) {
                    playerParty.put(player.getUniqueId(), p);
                    return new JoinResult.Ok(p);
                }
            }
        }
        return JoinResult.NO_INVITE;
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
        Party p = partyOf(leader);
        if (p == null || !p.isLeader(leader.getUniqueId())) return;
        if (target.getUniqueId().equals(leader.getUniqueId())) return;
        if (p.removeMember(target.getUniqueId())) {
            playerParty.remove(target.getUniqueId());
        }
    }

    public void transferLeader(Player leader, Player target) {
        Party p = partyOf(leader);
        if (p == null || !p.isLeader(leader.getUniqueId())) return;
        p.setLeader(target.getUniqueId());
    }

    public void disband(Party party) {
        for (UUID u : party.members()) playerParty.remove(u);
        parties.remove(party.id());
    }

    /** 队伍聊天消息分发（监听器转发聊天事件）。 */
    public void sendChat(Player sender, String message) {
        Party p = partyOf(sender);
        if (p == null) return;
        String formatted = "§d[队伍] §f" + sender.getName() + "§7: §f" + message;
        for (Player m : p.onlineMembers()) {
            m.sendMessage(formatted);
        }
    }

    /** 玩家断线清理（不移出队伍——允许重连回队；按配置也可在此移出）。 */
    public void onQuit(Player player) {
        // QueDP：断线保留队伍关系，队伍为空自动解散
        Party p = partyOf(player);
        if (p != null && p.onlineMembers().size() <= 1 && p.size() == 1) {
            leave(player);
        }
    }

    /** 掉落分配：按 lootMode 决定归属者。 */
    public @Nullable UUID lootOwner(Party party, @Nullable UUID killer) {
        return switch (party.lootMode()) {
            case FREE -> killer;
            case ROUND_ROBIN -> party.nextLootOwner();
            case LEADER -> party.leader();
        };
    }

    public int maxMembers() { return cfg.maxMembers(); }
    public int inviteTimeout() { return cfg.inviteTimeout(); }
    public int storagePages() { return cfg.storagePages(); }
    public AbyssModuleConfiguration.ExpShareMode expShareMode() { return cfg.expShareMode(); }
    public boolean allowCustomName() { return cfg.allowCustomName(); }

    public void shutdown() {
        playerParty.clear();
        parties.clear();
    }
}
