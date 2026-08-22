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
import xuanmo.arcartxsuite.api.util.AxsScheduler;
import xuanmo.arcartxsuite.api.security.ClientPacketGuardMode;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;

/**
 * {@link PacketGuardAPI} 的默认实现，按"模块+动作"维度对客户端包进行滑动窗口频率限制。
 * <p>
 * 超限时按 {@link ClientPacketGuardMode} 执行静默/通知/惩罚，并定期清理过期状态。
 */
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
        cleanupTask = AxsScheduler.runTaskTimer(
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
                        String safeName = sanitizePlayerName(player.getName());
                        if (safeName != null) {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("<player>", safeName));
                        }
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
        if (playerId == null) {
            // 与公开重载 allow(Player,...) 在 player==null 时返回 false 保持一致：
            // null playerId 视为非法调用方，拒绝放行，避免未来直接传 UUID 的调用方绕过 guard。
            return false;
        }
        if (!configuration.enabled()) {
            // guard 未启用时放行（与原语义一致）
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
            xuanmo.arcartxsuite.module.AxsLog.logger().info(
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

    /**
     * 校验玩家名是否安全（仅允许字母、数字、下划线），防止命令注入。
     * <p>
     * 离线模式下玩家名可包含特殊字符（如 {@code ;}、{@code |}），
     * 若直接替换到控制台命令中可导致 RCE。此处仅放行正则 {@code [A-Za-z0-9_]+} 的名字。
     *
     * @return 安全的玩家名；不安全时返回 null（调用方应跳过惩罚命令执行）
     */
    private static String sanitizePlayerName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9') || c == '_')) {
                return null;
            }
        }
        return name;
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
