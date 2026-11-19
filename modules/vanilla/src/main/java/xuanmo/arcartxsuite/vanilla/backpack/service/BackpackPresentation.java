package xuanmo.arcartxsuite.vanilla.backpack.service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.api.bridge.ClientBridgeAPI;
import xuanmo.arcartxsuite.vanilla.backpack.config.BackpackPresentationConfig;
import xuanmo.arcartxsuite.vanilla.backpack.config.BackpackPresentationConfig.Action;

/** Owns one animation sequence per open backpack. All callbacks and timers run on the server thread. */
final class BackpackPresentation {
    private static final long IDLE_RENEWAL_MS = 30_000;
    private static final long IDLE_EXPIRY_MARGIN_MS = 250;

    private final JavaPlugin plugin;
    private final ClientBridgeAPI client;
    private final BackpackPresentationConfig config;
    private final Predicate<Player> viewingBackpack;
    private final Map<UUID, Session> sessions = new HashMap<>();

    BackpackPresentation(JavaPlugin plugin, ClientBridgeAPI client, BackpackPresentationConfig config,
                         Predicate<Player> viewingBackpack) {
        this.plugin = plugin;
        this.client = client;
        this.config = config;
        this.viewingBackpack = viewingBackpack;
    }

    boolean enabled() {
        return config.hasAnimations() && client != null && client.isAvailable();
    }

    void open(Player player) {
        if (!enabled() || !canAnimate(player) || sessions.containsKey(player.getUniqueId())) return;
        Session session = new Session(player);
        sessions.put(player.getUniqueId(), session);
        Action opening = config.opening();
        if (opening == null) {
            idle(session);
        } else if (play(session, opening.animation(), opening.durationMs())) {
            after(session, opening.durationMs(), () -> idle(session));
        }
    }

    void close(Player player) { close(player.getUniqueId(), true); }

    void quit(Player player) { close(player.getUniqueId(), false); }

    void closeAll() {
        for (UUID id : java.util.List.copyOf(sessions.keySet())) close(id, true);
    }

    private void idle(Session session) {
        boolean hasRandom = !config.randomActions().isEmpty();
        long delay = hasRandom ? ThreadLocalRandom.current().nextLong(config.minRandomMs(), config.maxRandomMs() + 1L)
            : IDLE_RENEWAL_MS;
        if (!config.idle().isEmpty() && !play(session, config.idle(), delay + IDLE_EXPIRY_MARGIN_MS)) return;
        if (hasRandom) {
            after(session, delay, () -> randomAction(session));
        } else if (!config.idle().isEmpty()) {
            after(session, delay, () -> idle(session));
        }
    }

    private void randomAction(Session session) {
        Action action = chooseRandom(session.previousRandom);
        if (play(session, action.animation(), action.durationMs())) {
            session.previousRandom = action.animation();
            after(session, action.durationMs(), () -> idle(session));
        }
    }

    private Action chooseRandom(String previous) {
        var pool = config.randomActions();
        boolean hasAlternative = pool.stream().anyMatch(entry -> !entry.action().animation().equals(previous));
        long total = 0;
        for (var entry : pool) {
            if (!hasAlternative || !entry.action().animation().equals(previous)) total += entry.weight();
        }
        long ticket = ThreadLocalRandom.current().nextLong(total);
        for (var entry : pool) {
            if (hasAlternative && entry.action().animation().equals(previous)) continue;
            ticket -= entry.weight();
            if (ticket < 0) return entry.action();
        }
        throw new IllegalStateException("Backpack animation weights changed during selection");
    }

    private boolean play(Session session, String name, long durationMs) {
        Player player = session.player;
        if (!client.sendEntityAnimation(player, player, name, 1, config.blendMs(), durationMs)) {
            close(player.getUniqueId(), false);
            return false;
        }
        session.playing = name;
        session.expires = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(durationMs);
        return true;
    }

    private void after(Session session, long delayMs, Runnable next) {
        UUID id = session.player.getUniqueId();
        session.task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (sessions.get(id) != session) return;
            session.task = null;
            if (canAnimate(session.player)) next.run();
            else close(id, true);
        }, Math.max(1, (delayMs + 49) / 50));
    }

    private boolean canAnimate(Player player) {
        return player.isOnline() && !player.isDead() && player.getGameMode() != GameMode.SPECTATOR
            && viewingBackpack.test(player);
    }

    private void close(UUID id, boolean expireAnimation) {
        Session session = sessions.remove(id);
        if (session == null) return;
        if (session.task != null) session.task.cancel();
        if (expireAnimation && session.playing != null && session.expires - System.nanoTime() > 0) {
            // Replacing the same viewer-local override with a zero-duration one lets the normal animator resume.
            client.sendEntityAnimation(session.player, session.player, session.playing, 1, 0, 0);
        }
    }

    private static final class Session {
        private final Player player;
        private BukkitTask task;
        private String playing;
        private long expires;
        private String previousRandom;

        private Session(Player player) { this.player = player; }
    }
}
