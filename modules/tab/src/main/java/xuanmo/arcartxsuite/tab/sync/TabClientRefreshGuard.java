package xuanmo.arcartxsuite.tab.sync;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongSupplier;
import java.util.logging.Logger;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.api.security.ClientPacketGuardMode;
import xuanmo.arcartxsuite.tab.config.TabClientRefreshGuardConfiguration;
import xuanmo.arcartxsuite.tab.config.TabDefinition;

final class TabClientRefreshGuard {

    private final Logger logger;
    private final LongSupplier timeSupplier;
    private final ConcurrentMap<RouteKey, GuardState> states = new ConcurrentHashMap<>();

    TabClientRefreshGuard(Logger logger) {
        this(logger, System::currentTimeMillis);
    }

    TabClientRefreshGuard(Logger logger, LongSupplier timeSupplier) {
        this.logger = logger;
        this.timeSupplier = timeSupplier == null ? System::currentTimeMillis : timeSupplier;
    }

    boolean allow(Player player, TabDefinition definition, boolean debugLogging) {
        if (player == null || definition == null) {
            return false;
        }
        return allow(
            player.getUniqueId(),
            player.getName(),
            definition,
            debugLogging,
            message -> {
                if (!message.isBlank()) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
                }
            }
        );
    }

    boolean allow(
        UUID playerId,
        String playerName,
        TabDefinition definition,
        boolean debugLogging,
        FeedbackSink feedbackSink
    ) {
        if (playerId == null || definition == null) {
            return false;
        }

        TabClientRefreshGuardConfiguration configuration = definition.clientRefreshGuard();
        if (configuration == null || !configuration.enabled()) {
            return true;
        }

        long now = timeSupplier.getAsLong();
        RouteKey routeKey = new RouteKey(definition.id(), playerId);
        GuardState state = states.computeIfAbsent(routeKey, ignored -> new GuardState());
        boolean allowed;
        synchronized (state) {
            state.prune(now, configuration.windowMs());
            if (state.hitTimestamps.size() >= configuration.maxHits()) {
                state.lastTouchedAt = now;
                if (configuration.mode() == ClientPacketGuardMode.NOTIFY
                    && (state.lastNotifyAt <= 0L || now - state.lastNotifyAt >= configuration.notifyCooldownMs())) {
                    feedbackSink.notifyPlayer(configuration.notifyMessage());
                    state.lastNotifyAt = now;
                }
                allowed = false;
            } else {
                state.hitTimestamps.addLast(now);
                state.lastTouchedAt = now;
                allowed = true;
            }
        }

        if (!allowed && debugLogging && logger != null) {
            logger.info(
                "ArcartXTab 拒绝客户端刷新 -> player="
                    + playerName
                    + " | tab="
                    + definition.id()
                    + " | windowMs="
                    + configuration.windowMs()
                    + " | maxHits="
                    + configuration.maxHits()
                    + " | mode="
                    + configuration.mode().configValue()
            );
        }
        return allowed;
    }

    void cleanup(Map<String, TabDefinition> definitionsById) {
        long now = timeSupplier.getAsLong();
        for (Map.Entry<RouteKey, GuardState> entry : states.entrySet()) {
            TabDefinition definition = definitionsById == null ? null : definitionsById.get(entry.getKey().tabId());
            if (definition == null) {
                states.remove(entry.getKey(), entry.getValue());
                continue;
            }
            TabClientRefreshGuardConfiguration configuration = definition.clientRefreshGuard();
            GuardState state = entry.getValue();
            boolean remove;
            synchronized (state) {
                state.prune(now, configuration.windowMs());
                long retentionMs = Math.max(configuration.windowMs(), configuration.notifyCooldownMs());
                remove = state.hitTimestamps.isEmpty() && now - Math.max(state.lastTouchedAt, state.lastNotifyAt) > retentionMs;
            }
            if (remove) {
                states.remove(entry.getKey(), state);
            }
        }
    }

    void clear() {
        states.clear();
    }

    interface FeedbackSink {
        void notifyPlayer(String message);
    }

    private record RouteKey(String tabId, UUID playerId) {
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
