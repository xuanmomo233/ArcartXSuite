package xuanmo.arcartxsuite.tab;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Owns original Tab list priorities for the current backend, on the server thread. */
final class NativeTabOrder implements AutoCloseable {
    private static final int FIRST_ORDER_PROTOCOL = 768; // Minecraft Java 1.21.2

    interface Access {
        int order(Player player);
        void order(Player player, int order);
        int protocol(Player player);
    }

    private record Applied(WeakReference<Player> player, int original, int last, boolean owned) {}

    private final Access access;
    private final Map<UUID, Applied> tracked = new HashMap<>();

    NativeTabOrder(Access access) {
        this.access = access;
    }

    static NativeTabOrder discover() {
        try {
            Method getter = Player.class.getMethod("getPlayerListOrder");
            Method setter = Player.class.getMethod("setPlayerListOrder", int.class);
            Method protocol = Player.class.getMethod("getProtocolVersion");
            if (getter.getReturnType() != int.class || setter.getReturnType() != void.class
                || protocol.getReturnType() != int.class) return null;
            return new NativeTabOrder(new ReflectedAccess(getter, setter, protocol));
        } catch (NoSuchMethodException | SecurityException | LinkageError unavailable) {
            return null;
        }
    }

    boolean supportsViewer(Player viewer) {
        try {
            return access.protocol(viewer) >= FIRST_ORDER_PROTOCOL;
        } catch (RuntimeException | LinkageError unavailable) {
            return false;
        }
    }

    boolean apply(List<Player> orderedPlayers) {
        var present = new HashSet<UUID>();
        // Minecraft sorts higher values first. A fixed start keeps existing orders on tail joins.
        int order = Integer.MAX_VALUE;
        for (Player player : orderedPlayers) {
            UUID id = player.getUniqueId();
            if (present.add(id) && !applyOrder(id, player, order--)) return false;
        }

        var entries = tracked.entrySet().iterator();
        while (entries.hasNext()) {
            var entry = entries.next();
            if (present.contains(entry.getKey())) continue;
            restore(entry.getKey(), entry.getValue());
            entries.remove();
        }
        return true;
    }

    private boolean applyOrder(UUID id, Player player, int order) {
        Applied before = tracked.get(id);
        if (before != null && before.player().get() != player) {
            tracked.remove(id);
            before = null;
        }
        if (before != null && !before.owned()) return false;

        int current = access.order(player);
        if (before != null && current != before.last()) {
            // Another owner changed this connection's priority. Leave that value in place.
            tracked.put(id, new Applied(before.player(), before.original(), current, false));
            return false;
        }
        if (before == null) {
            before = new Applied(new WeakReference<>(player), current, current, true);
            tracked.put(id, before);
        }
        if (current != order) {
            tracked.put(id, new Applied(before.player(), before.original(), order, true));
            access.order(player, order);
        }
        return true;
    }

    private void restore(UUID id, Applied applied) {
        if (!applied.owned() || applied.original() == applied.last()) return;
        Player player = applied.player().get();
        if (player != null && Bukkit.getPlayer(id) == player && access.order(player) == applied.last()) {
            access.order(player, applied.original());
        }
    }

    @Override public void close() {
        IllegalStateException failed = null;
        for (var entry : tracked.entrySet()) {
            try {
                restore(entry.getKey(), entry.getValue());
            } catch (RuntimeException | LinkageError failure) {
                if (failed == null) failed = new IllegalStateException("Could not restore native Tab list order", failure);
                else failed.addSuppressed(failure);
            }
        }
        tracked.clear();
        if (failed != null) throw failed;
    }

    private record ReflectedAccess(Method getter, Method setter, Method protocolMethod) implements Access {
        @Override public int order(Player player) {
            return (int) invoke(getter, player);
        }

        @Override public void order(Player player, int order) {
            invoke(setter, player, order);
        }

        @Override public int protocol(Player player) {
            return (int) invoke(protocolMethod, player);
        }

        private static Object invoke(Method method, Player player, Object... arguments) {
            try {
                return method.invoke(player, arguments);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Tab could not call " + method.getName(), failure);
            }
        }
    }
}
