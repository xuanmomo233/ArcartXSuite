package xuanmo.arcartxsuite.security;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongSupplier;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.security.ClientPacketGuardMode;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;

public final class ClientPacketGuard implements PacketGuardAPI {

    private final JavaPlugin plugin;
    private final ClientPacketGuardConfiguration configuration;
    private final LongSupplier timeSupplier;
    private final ConcurrentMap<RouteKey, GuardState> states = new ConcurrentHashMap<>();

    private BukkitTask cleanupTask;

    public ClientPacketGuard(JavaPlugin plugin, ClientPacketGuardConfiguration configuration) {
        this(plugin, configuration, System::currentTimeMillis);
    }

    ClientPacketGuard(JavaPlugin plugin, ClientPacketGuardConfiguration configuration, LongSupplier timeSupplier) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.timeSupplier = timeSupplier == null ? System::currentTimeMillis : timeSupplier;
    }

    public void start() {
        shutdown();
        if (plugin == null || !configuration.enabled()) {
            return;
        }
        cleanupTask = Bukkit.getScheduler().runTaskTimer(
            plugin,
            this::cleanup,
            configuration.cleanupIntervalTicks(),
            configuration.cleanupIntervalTicks()
        );
    }

    public void shutdown() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        states.clear();
    }

    @Override
    public boolean allow(Player player, String module, String action, boolean debugLogging) {
        if (player == null) {
            return false;
        }
        return allow(
            player.getUniqueId(),
            player.getName(),
            module,
            action,
            debugLogging,
            new FeedbackSink() {
                @Override
                public void notifyPlayer(String message) {
                    if (!message.isBlank()) {
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
                    }
                }

                @Override
                public void punish(String command) {
                    if (!command.isBlank()) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("<player>", player.getName()));
                    }
                }
            }
        );
    }

    boolean allow(
        UUID playerId,
        String playerName,
        String module,
        String action,
        boolean debugLogging,
        FeedbackSink feedbackSink
    ) {
        if (playerId == null || !configuration.enabled()) {
            return true;
        }

        String normalizedModule = normalizeKey(module);
        String normalizedAction = normalizeKey(action);
        ClientPacketGuardRule rule = configuration.resolve(normalizedModule, normalizedAction);
        if (!rule.enabled()) {
            return true;
        }

        long now = timeSupplier.getAsLong();
        RouteKey routeKey = new RouteKey(normalizedModule, normalizedAction, playerId);
        GuardState state = states.computeIfAbsent(routeKey, ignored -> new GuardState());
        boolean allowed;
        synchronized (state) {
            state.prune(now, rule.windowMs());
            if (state.hitTimestamps.size() >= rule.maxHits()) {
                state.lastTouchedAt = now;
                if (rule.mode() == ClientPacketGuardMode.NOTIFY
                    && (state.lastNotifyAt <= 0L || now - state.lastNotifyAt >= rule.notifyCooldownMs())) {
                    feedbackSink.notifyPlayer(rule.notifyMessage());
                    state.lastNotifyAt = now;
                } else if (rule.mode() == ClientPacketGuardMode.PUNISH) {
                    feedbackSink.punish(rule.punishCommand());
                }
                allowed = false;
            } else {
                state.hitTimestamps.addLast(now);
                state.lastTouchedAt = now;
                allowed = true;
            }
        }

        if (!allowed && debugLogging && plugin != null) {
            plugin.getLogger().info(
                "ClientPacketGuard 拒绝回包 -> player="
                    + playerName
                    + " | route="
                    + normalizedModule
                    + "."
                    + normalizedAction
                    + " | windowMs="
                    + rule.windowMs()
                    + " | maxHits="
                    + rule.maxHits()
                    + " | mode="
                    + rule.mode().configValue()
            );
        }
        return allowed;
    }

    private void cleanup() {
        long now = timeSupplier.getAsLong();
        for (Map.Entry<RouteKey, GuardState> entry : states.entrySet()) {
            ClientPacketGuardRule rule = configuration.resolve(entry.getKey().module(), entry.getKey().action());
            GuardState state = entry.getValue();
            boolean remove;
            synchronized (state) {
                state.prune(now, rule.windowMs());
                long retentionMs = Math.max(rule.windowMs(), rule.notifyCooldownMs());
                remove = state.hitTimestamps.isEmpty() && now - Math.max(state.lastTouchedAt, state.lastNotifyAt) > retentionMs;
            }
            if (remove) {
                states.remove(entry.getKey(), state);
            }
        }
    }

    private static String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    interface FeedbackSink {
        void notifyPlayer(String message);

        void punish(String command);
    }

    private record RouteKey(String module, String action, UUID playerId) {
    }

    private static final class GuardState {
        private final ArrayDeque<Long> hitTimestamps = new ArrayDeque<>();
        private long lastNotifyAt;
        private long lastTouchedAt;

        private void prune(long now, long windowMs) {
            while (!hitTimestamps.isEmpty() && now - hitTimestamps.peekFirst() >= windowMs) {
                hitTimestamps.removeFirst();
            }
        }
    }
}
