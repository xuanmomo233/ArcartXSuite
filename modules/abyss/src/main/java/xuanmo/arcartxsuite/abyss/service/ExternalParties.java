package xuanmo.arcartxsuite.abyss.service;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Bukkit;
import xuanmo.arcartxsuite.abyss.party.Party;
import xuanmo.arcartxsuite.api.capability.AbyssAdmissionAccess.Intent;
import xuanmo.arcartxsuite.api.capability.AbyssAdmissionAccess.Registration;
import xuanmo.arcartxsuite.api.capability.AbyssExternalPartyAccess;

final class ExternalParties implements AbyssExternalPartyAccess {
    record Verified(Provider provider, Roster roster) { }
    private final AbyssService service;
    private final Map<String, Provider> providers = new LinkedHashMap<>();
    private boolean verifying;

    ExternalParties(AbyssService service) { this.service = service; }

    @Override public Registration register(String owner, Provider provider) {
        requireMain();
        if (!service.running() || verifying) throw new IllegalStateException("External party service unavailable");
        if (owner == null || !owner.matches("[a-z0-9_.:-]{1,96}")) throw new IllegalArgumentException("Invalid owner");
        if (providers.putIfAbsent(owner, Objects.requireNonNull(provider)) != null)
            throw new IllegalStateException("Party provider already registered");
        return () -> {
            requireMain();
            if (verifying) throw new IllegalStateException("Cannot unregister during verification");
            providers.remove(owner, provider);
        };
    }

    Verified verify(Intent intent) {
        if (intent.externalPartyId() == null || verifying) return null;
        var provider = providers.get(intent.owner());
        if (provider == null || intent.members().stream().anyMatch(service.party()::inParty)) return null;
        verifying = true;
        try {
            var roster = provider.verify(intent);
            if (roster == null || !roster.ready() || !intent.externalPartyId().equals(roster.partyId())
                || !intent.actor().equals(roster.leader()) || intent.rosterRevision() != roster.rosterRevision()
                || intent.selectionRevision() != roster.selectionRevision() || roster.capacity() > 256
                || roster.capacity() < roster.members().size() || roster.members().size() != intent.members().size()
                || !new HashSet<>(roster.members()).equals(new HashSet<>(intent.members()))
                || roster.mode() == null || !roster.mode().matches("[a-zA-Z0-9_.-]{1,64}")) return null;
            return new Verified(provider, roster);
        } catch (RuntimeException failure) {
            return null;
        } finally {
            verifying = false;
        }
    }

    static Party context(Verified snapshot) {
        Roster roster = snapshot.roster();
        var party = new Party(roster.leader(), roster.capacity());
        party.mode(roster.mode());
        for (var member : roster.members()) if (!member.equals(roster.leader())) {
            party.addMember(member);
        }
        for (var member : roster.members()) if (!party.isReady(member)) party.toggleReady(member);
        return party;
    }

    void clear() { providers.clear(); }

    private static void requireMain() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Call external party access on the server thread");
    }
}
