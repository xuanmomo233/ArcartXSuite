package xuanmo.arcartxsuite.abyss.capability;

import java.util.List;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.abyss.party.Party;
import xuanmo.arcartxsuite.abyss.party.PartyManager;
import xuanmo.arcartxsuite.abyss.service.AbyssService;
import xuanmo.arcartxsuite.api.capability.AbyssPartyAccess;

/**
 * {@link AbyssPartyAccess} 实现：委托 {@link PartyManager}。
 */
public final class AbyssPartyAccessImpl implements AbyssPartyAccess {

    private final AbyssService service;

    public AbyssPartyAccessImpl(AbyssService service) {
        this.service = service;
    }

    private PartyManager pm() { return service.party(); }

    private @Nullable Party findParty(UUID player) {
        PartyManager pm = pm();
        return pm != null ? pm.partyOf(player) : null;
    }

    // ═══ 查询 ═══

    @Override
    public @Nullable PartySnapshot partyOf(@NotNull UUID player) {
        Party p = findParty(player);
        return p != null ? pm().snapshot(p) : null;
    }

    @Override
    public boolean isInParty(@NotNull UUID player) {
        PartyManager pm = pm();
        return pm != null && pm.inParty(player);
    }

    @Override
    public boolean isLeader(@NotNull UUID player) {
        Party p = findParty(player);
        return p != null && p.isLeader(player);
    }

    @Override
    public @Nullable PartySnapshot getParty(@NotNull UUID partyId) {
        PartyManager pm = pm();
        Party p = pm != null ? pm.getParty(partyId) : null;
        return p != null ? pm.snapshot(p) : null;
    }

    @Override
    public @NotNull List<PartySnapshot> publicParties() {
        PartyManager pm = pm();
        if (pm == null) return List.of();
        return pm.listPublic(null, null).stream()
            .map(e -> {
                Party p = pm.getParty(e.partyId());
                return p != null ? pm.snapshot(p) : null;
            })
            .filter(java.util.Objects::nonNull)
            .toList();
    }

    // ═══ 编程式管理 ═══

    @Override
    public @NotNull PartySnapshot createParty(@NotNull Player leader, @Nullable String name,
                                              @NotNull String mode, int maxSize) {
        PartyManager pm = pm();
        Party p = pm.create(leader, mode, maxSize);
        if (name != null && !name.isBlank()) {
            p.name(name);
        }
        return pm.snapshot(p);
    }

    @Override
    public boolean disband(@NotNull UUID partyId) {
        PartyManager pm = pm();
        Party p = pm != null ? pm.getParty(partyId) : null;
        if (p == null) return false;
        pm.disband(p);
        return true;
    }

    @Override
    public @Nullable String addMember(@NotNull UUID partyId, @NotNull UUID player) {
        PartyManager pm = pm();
        return pm != null ? pm.addMemberDirect(partyId, player) : "abyss unavailable";
    }

    @Override
    public boolean removeMember(@NotNull UUID partyId, @NotNull UUID player) {
        PartyManager pm = pm();
        return pm != null && pm.removeMember(partyId, player);
    }

    @Override
    public boolean transferLeader(@NotNull UUID partyId, @NotNull UUID newLeader) {
        PartyManager pm = pm();
        Party p = pm != null ? pm.getParty(partyId) : null;
        if (p == null || !p.isMember(newLeader)) return false;
        p.setLeader(newLeader);
        Player np = org.bukkit.Bukkit.getPlayer(newLeader);
        service.publishEvent("axs.abyss.party_leader_change", np, java.util.Map.of(
            "party", partyId.toString(), "mode", p.mode()));
        return true;
    }

    // ═══ 校验钩子 ═══

    @Override
    public void registerJoinValidator(@NotNull String mode, @NotNull JoinValidator validator) {
        PartyManager pm = pm();
        if (pm != null) pm.registerJoinValidator(mode, validator);
    }

    @Override
    public void unregisterJoinValidator(@NotNull String mode) {
        PartyManager pm = pm();
        if (pm != null) pm.registerJoinValidator(mode, null);
    }
}
