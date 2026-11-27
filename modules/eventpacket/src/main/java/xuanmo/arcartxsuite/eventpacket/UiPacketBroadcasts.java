package xuanmo.arcartxsuite.eventpacket;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.scheduler.ScheduledTask;
import xuanmo.arcartxsuite.eventpacket.RealtimeRules.Snapshot;
import xuanmo.arcartxsuite.eventpacket.RuleConfiguration.UiAudience;

/** Server-thread queue. Only the active broadcast retains a snapshot of its recipients. */
final class UiPacketBroadcasts implements AutoCloseable {
    private static final int MAX_PENDING = 32;
    private static final int WORK_PER_TICK = 16;
    private static final long BUDGET_NANOS = 750_000L;

    private final Function<Runnable, ScheduledTask> schedule;
    private final ArrayDeque<Broadcast> pending = new ArrayDeque<>();
    private ScheduledTask task;
    private Iterator<Snapshot> recipients;

    UiPacketBroadcasts(Function<Runnable, ScheduledTask> schedule) {
        this.schedule = schedule;
    }

    boolean enqueue(Snapshot origin, UiAudience audience, Consumer<Player> send) {
        if (pending.size() >= MAX_PENDING) return false;
        if (task == null) task = schedule.apply(this::tick);
        pending.addLast(new Broadcast(origin, audience, send));
        return true;
    }

    private void tick() {
        Broadcast broadcast = pending.peekFirst();
        if (broadcast == null) return;
        if (currentPlayer(broadcast.origin()) == null) {
            finish();
            return;
        }
        if (recipients == null) recipients = collect(broadcast);
        var batch = recipients;
        long start = System.nanoTime();
        for (int work = 0; work < WORK_PER_TICK && pending.peekFirst() == broadcast && batch.hasNext()
                && System.nanoTime() - start < BUDGET_NANOS; work++) {
            Player player = currentPlayer(batch.next());
            if (player != null) broadcast.send().accept(player);
        }
        if (pending.peekFirst() == broadcast && !batch.hasNext()) finish();
    }

    private static Iterator<Snapshot> collect(Broadcast broadcast) {
        var players = new ArrayList<Snapshot>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            var id = player.getUniqueId();
            if (broadcast.audience() == UiAudience.OTHERS && id.equals(broadcast.origin().player())) continue;
            var session = AuthAccess.snapshot(id);
            if (session.admitted() && session.connectionId() != null) players.add(Snapshot.from(session, Map.of()));
        }
        return players.iterator();
    }

    private static Player currentPlayer(Snapshot expected) {
        Player player = Bukkit.getPlayer(expected.player());
        return player != null && player.isOnline() && expected.sameSession(AuthAccess.snapshot(expected.player()))
            ? player : null;
    }

    private void finish() {
        pending.removeFirst();
        recipients = null;
        if (pending.isEmpty()) close();
    }

    @Override public void close() {
        pending.clear();
        recipients = null;
        var old = task;
        task = null;
        if (old != null) old.cancel();
    }

    private record Broadcast(Snapshot origin, UiAudience audience, Consumer<Player> send) {}
}
