package xuanmo.arcartxsuite.tab;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Owns formatted original Tab names for the current backend, on the server thread. */
final class NativeTabNames implements AutoCloseable {
    private record Applied(WeakReference<Player> player, String original, String last, boolean owned) {}

    private final Map<UUID, Applied> tracked = new HashMap<>();

    void apply(Map<Player, String> names) {
        var present = new HashSet<UUID>();
        for (var entry : names.entrySet()) {
            Player player = entry.getKey();
            UUID id = player.getUniqueId();
            if (present.add(id)) applyName(id, player, entry.getValue());
        }

        var entries = tracked.entrySet().iterator();
        while (entries.hasNext()) {
            var entry = entries.next();
            if (present.contains(entry.getKey())) continue;
            restore(entry.getKey(), entry.getValue());
            entries.remove();
        }
    }

    private void applyName(UUID id, Player player, String desired) {
        Applied before = tracked.get(id);
        if (before != null && before.player().get() != player) {
            tracked.remove(id);
            before = null;
        }
        if (before != null && !before.owned()) return;

        String current = player.getPlayerListName();
        if (before != null && !Objects.equals(current, before.last())) {
            tracked.put(id, new Applied(before.player(), before.original(), current, false));
            return;
        }
        if (before == null) {
            before = new Applied(new WeakReference<>(player), current, current, true);
            tracked.put(id, before);
        }
        if (!Objects.equals(current, desired)) {
            tracked.put(id, new Applied(before.player(), before.original(), desired, true));
            player.setPlayerListName(desired);
        }
    }

    private static void restore(UUID id, Applied applied) {
        if (!applied.owned() || Objects.equals(applied.original(), applied.last())) return;
        Player player = applied.player().get();
        if (player != null && Bukkit.getPlayer(id) == player && Objects.equals(player.getPlayerListName(), applied.last())) {
            player.setPlayerListName(applied.original());
        }
    }

    @Override public void close() {
        IllegalStateException failed = null;
        for (var entry : tracked.entrySet()) {
            try {
                restore(entry.getKey(), entry.getValue());
            } catch (RuntimeException | LinkageError failure) {
                if (failed == null) failed = new IllegalStateException("Could not restore native Tab list names", failure);
                else failed.addSuppressed(failure);
            }
        }
        tracked.clear();
        if (failed != null) throw failed;
    }
}
