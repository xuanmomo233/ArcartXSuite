package xuanmo.arcartxsuite.abyss.mob;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

/** Group-scoped leases. Existing tickets owned by the host plugin are borrowed, never removed. */
final class ChunkTickets {
    private record Owner(UUID run, String group) { }
    private record Key(World world, int x, int z) { }
    private record Ticket(boolean owned, Set<Owner> owners) { }
    private final Plugin plugin;
    private final java.util.logging.Logger logger;
    private final Map<Key, Ticket> tickets = new HashMap<>();
    private final Map<Owner, Set<Key>> groups = new HashMap<>();

    ChunkTickets(Plugin plugin) { this(plugin, java.util.logging.Logger.getLogger("Abyss")); }
    ChunkTickets(Plugin plugin, java.util.logging.Logger logger) { this.plugin = plugin; this.logger = logger; }

    boolean acquire(UUID run, String group, World world, int x, int z) {
        // Tests and isolated script runners do not have a Bukkit plugin lifecycle.
        if (plugin == null) return true;
        var owner = new Owner(run, group);
        var key = new Key(world, x, z);
        var ticket = tickets.get(key);
        if (ticket == null) {
            boolean added = world.addPluginChunkTicket(x, z, plugin);
            if (!added && !world.getPluginChunkTickets(x, z).contains(plugin)) return false;
            ticket = new Ticket(added, new HashSet<>());
            tickets.put(key, ticket);
        }
        ticket.owners().add(owner);
        groups.computeIfAbsent(owner, ignored -> new HashSet<>()).add(key);
        return true;
    }

    void release(UUID run, String group) {
        var owner = new Owner(run, group);
        var keys = groups.remove(owner);
        if (keys == null) return;
        for (var key : keys) {
            var ticket = tickets.get(key);
            if (ticket == null || !ticket.owners().remove(owner) || !ticket.owners().isEmpty()) continue;
            if (ticket.owned()) {
                // Keep a failed removal registered so shutdown can retry it.
                if (!remove(key)) continue;
            }
            tickets.remove(key);
        }
    }

    void releaseRun(UUID run) {
        for (var owner : Set.copyOf(groups.keySet())) if (owner.run().equals(run)) release(run, owner.group());
        retryEmpty();
    }

    void close() {
        for (var owner : Set.copyOf(groups.keySet())) release(owner.run(), owner.group());
        retryEmpty();
    }

    private void retryEmpty() {
        for (var entry : Map.copyOf(tickets).entrySet()) {
            var ticket = entry.getValue();
            var key = entry.getKey();
            if (ticket.owners().isEmpty() && (!ticket.owned() || remove(key))) tickets.remove(key);
        }
    }

    private boolean remove(Key key) {
        try {
            return key.world().removePluginChunkTicket(key.x(), key.z(), plugin)
                || !key.world().getPluginChunkTickets(key.x(), key.z()).contains(plugin);
        } catch (RuntimeException failure) {
            logger.warning("[abyss] 区块票据释放待重试: " + key.x() + "," + key.z() + " " + failure.getMessage());
            return false;
        }
    }
}
