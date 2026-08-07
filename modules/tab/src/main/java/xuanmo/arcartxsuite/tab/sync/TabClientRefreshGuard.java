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

/**
 * Tab 客户端刷新频率防护器。
 * <p>
 * 基于「滑动窗口 + 最大命中次数」策略，限制单个玩家对单个 Tab 的客户端主动刷新频率。
 * 超过阈值后拒绝刷新，并按配置模式向玩家发送提示或静默处理。
 * 关联模块：{@code tab}，消费 {@link TabClientRefreshGuardConfiguration}。
 */
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

    /**
     * 判断是否允许该玩家刷新指定 Tab。
     * <p>
     * 未启用防护或配置为 {@code null} 时直接放行。
     *
     * @param player        触发刷新的玩家
     * @param definition    对应的 Tab 定义
     * @param debugLogging  是否记录拒绝日志
     * @return {@code true} 允许刷新；{@code false} 表示命中防护被拒绝
     */
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

    /**
     * 判断是否允许该玩家刷新指定 Tab（带自定义反馈通道）。
     *
     * @param playerId      玩家唯一标识
     * @param playerName    玩家名称，仅用于日志
     * @param definition    对应的 Tab 定义
     * @param debugLogging  是否记录拒绝日志
     * @param feedbackSink  命中防护时的消息反馈通道
     * @return {@code true} 允许刷新；{@code false} 表示命中防护被拒绝
     */
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

    /**
     * 清理不再有效的防护状态。
     * <p>
     * 移除 Tab 定义已不存在或长期未活跃的玩家路由记录，避免内存泄漏。
     *
     * @param definitionsById 当前所有 Tab 定义（按 id 索引），可为 {@code null}
     */
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

    /**
     * 清空所有防护状态。
     */
    void clear() {
        states.clear();
    }

    /**
     * 反馈通道接口，用于在命中防护时向玩家发送提示消息。
     */
    interface FeedbackSink {
        /**
         * 向玩家发送提示消息。
         *
         * @param message 待发送的消息内容
         */
        void notifyPlayer(String message);
    }

    /**
     * 防护路由键，由 Tab id 与玩家 id 组合唯一标识一条防护状态。
     *
     * @param tabId    Tab 定义 id
     * @param playerId 玩家唯一标识
     */
    private record RouteKey(String tabId, UUID playerId) {
    }

    /**
     * 单条路由的防护状态，记录滑动窗口内的命中时间戳与通知冷却。
     */
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
