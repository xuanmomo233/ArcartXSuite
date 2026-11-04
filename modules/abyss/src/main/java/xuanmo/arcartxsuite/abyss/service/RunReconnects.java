package xuanmo.arcartxsuite.abyss.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstance;
import xuanmo.arcartxsuite.abyss.instance.AbyssStatus;
import xuanmo.arcartxsuite.api.capability.AbyssReconnectAccess;

final class RunReconnects implements AbyssReconnectAccess {
    record Seat(UUID run, long deadline, Location returnLocation, GameMode mode, boolean dead) { }
    private final Map<UUID, Seat> seats = new LinkedHashMap<>();
    private final AbyssService service;

    RunReconnects(AbyssService service) { this.service = service; }

    void retain(AbyssInstance instance, Player player) {
        if (!instance.admitted() || instance.getStatus() != AbyssStatus.ACTIVE || instance.designMode()) return;
        int seconds = instance.getTemplate().requirements().reconnectGraceSeconds();
        var original = instance.getOriginalLocation(player.getUniqueId());
        if (seconds <= 0 || original == null || original.getWorld() == null) return;
        UUID id = player.getUniqueId();
        var seat = new Seat(instance.getInstanceId(), System.currentTimeMillis() + seconds * 1000L,
            original.clone(), instance.getSavedGameMode(id), instance.isPlayerCurrentlyDead(player));
        seats.put(id, seat);
        service.scheduleDelayedRaw(() -> {
            if (seats.remove(id, seat)) service.expireReconnect(id, seat.run());
        }, seconds * 20L);
    }

    Seat eligible(UUID player) {
        var seat = seats.get(player);
        if (seat == null || seat.deadline() <= System.currentTimeMillis() || !service.running()) return null;
        var run = service.instances().getInstance(seat.run());
        return run != null && run.getStatus() == AbyssStatus.ACTIVE && !run.isTimeout() && run.getWorld() != null ? seat : null;
    }

    boolean retains(UUID run) {
        long now = System.currentTimeMillis();
        return seats.values().stream().anyMatch(seat -> seat.run().equals(run) && seat.deadline() > now);
    }

    void detach(UUID run) {
        var affected = seats.entrySet().stream().filter(entry -> entry.getValue().run().equals(run))
            .map(Map.Entry::getKey).toList();
        for (UUID id : affected) {
            seats.remove(id);
            service.finishExpiredReconnect(id);
        }
    }

    @Override public Status reconnectStatus(UUID player) {
        requireMain();
        var seat = eligible(player);
        return seat == null ? new Status(null, 0, false, "NO_ACTIVE_RESERVATION")
            : new Status(seat.run(), seat.deadline(), true, "AVAILABLE");
    }

    @Override public Status requestReconnect(UUID player, UUID runId) {
        requireMain();
        var seat = eligible(player);
        if (seat == null || !seat.run().equals(runId)) return new Status(runId, 0, false, "NO_ACTIVE_RESERVATION");
        var member = Bukkit.getPlayer(player);
        if (member == null || !member.isOnline()) return new Status(runId, seat.deadline(), true, "OFFLINE");
        String result = service.resumeMember(member, seat);
        if (result.equals("REJOINED")) {
            seats.remove(player, seat);
            return new Status(runId, 0, false, result);
        }
        return new Status(runId, seat.deadline(), eligible(player) != null, result);
    }

    private static void requireMain() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Call Abyss reconnect on the server thread");
    }
}
