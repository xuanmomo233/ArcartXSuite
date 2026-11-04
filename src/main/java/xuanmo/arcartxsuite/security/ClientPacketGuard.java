package xuanmo.arcartxsuite.security;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongSupplier;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;

import xuanmo.arcartxsuite.api.security.ClientPacketGuardMode;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;

/**
 * {@link PacketGuardAPI} 的默认实现，按"模块+包+动作"维度对客户端包进行滑动窗口频率限制。
 * <p>
 * 限流状态与 action 键上限按 {@code (module, packetId)} 命名空间隔离——
 * 某类包的高基数 action（如 tooltip 物品指纹）只会塞满自己 packetId 的桶，
 * 不会挤占同模块下其他 packetId（如仓库/菜单 UI 操作）的额度。
 * <p>
 * 实例在整个生命周期内保持稳定，{@link #updateConfiguration} 热替换配置，
 * 因此路由层与模块捕获的引用在 reload 后仍然有效。
 * <p>
 * 超限时按 {@link ClientPacketGuardMode} 执行静默/通知/惩罚，并定期清理过期状态。
 */
public final class ClientPacketGuard implements PacketGuardAPI {

    /** 每个 (module, player, packetId) 维度允许的 distinct action 键上限——action 由包内容控制，无上限会被随机 action 撑爆内存。 */
    private static final int MAX_ACTION_KEYS_PER_MODULE_PLAYER = 64;
    /** 每个 (module, player) 维度允许的 distinct packetId 数上限——packetId 同为客户端可控字段，需防止随机 packetId 无限造桶。 */
    private static final int MAX_PACKET_IDS_PER_MODULE_PLAYER = 128;

    private final JavaPlugin plugin;
    private volatile ClientPacketGuardConfiguration configuration;
    private final LongSupplier timeSupplier;
    private final ConcurrentMap<RouteKey, GuardState> states = new ConcurrentHashMap<>();
    /** (module|playerId|packetId) → 已见的 action 键集合，用于上限控制与清理。 */
    private final ConcurrentMap<String, java.util.Set<String>> actionKeysByModulePlayer = new ConcurrentHashMap<>();
    /** (module|playerId) → 已见的 packetId 集合，用于 packetId 维度上限控制。 */
    private final ConcurrentMap<String, java.util.Set<String>> packetIdsByModulePlayer = new ConcurrentHashMap<>();

    private BukkitTask cleanupTask;
    private Listener quitListener;

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
        // 玩家退出时清掉其限流状态：限流窗口均为秒级，重进无法用于绕过限流，
        // 但可让"键集被高基数包塞满"的玩家通过重进自愈，无需重启服务器。
        quitListener = new Listener() {
            @EventHandler(priority = EventPriority.MONITOR)
            public void onQuit(PlayerQuitEvent event) {
                clearPlayer(event.getPlayer().getUniqueId());
            }
        };
        Bukkit.getPluginManager().registerEvents(quitListener, plugin);
    }

    public void shutdown() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        if (quitListener != null) {
            HandlerList.unregisterAll(quitListener);
            quitListener = null;
        }
        states.clear();
        actionKeysByModulePlayer.clear();
        packetIdsByModulePlayer.clear();
    }

    /**
     * 热替换配置。实例本身保持稳定（路由层与模块持有的引用不失效），
     * 配置自下一次 {@link #start()} 起生效。
     */
    public void updateConfiguration(ClientPacketGuardConfiguration newConfiguration) {
        if (newConfiguration != null) {
            this.configuration = newConfiguration;
        }
    }

    /** 路由层丢包日志开关（{@code client-packet-guard.debug}）。 */
    public boolean isDebugLogging() {
        return configuration.debug();
    }

    @Override
    public boolean allow(Player player, String module, String action, boolean debugLogging) {
        return allow(player, module, null, action, debugLogging);
    }

    @Override
    public boolean allow(Player player, String module, String packetId, String action, boolean debugLogging) {
        if (player == null) {
            return false;
        }
        return allow(
            player.getUniqueId(),
            player.getName(),
            module,
            packetId,
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
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("{player}", safeName).replace("<player>", safeName));
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
        String packetId,
        String action,
        boolean debugLogging,
        FeedbackSink feedbackSink
    ) {
        if (playerId == null) {
            // 与公开重载 allow(Player,...) 在 player==null 时返回 false 保持一致：
            // null playerId 视为非法调用方，拒绝放行，避免未来直接传 UUID 的调用方绕过 guard。
            return false;
        }
        ClientPacketGuardConfiguration config = this.configuration;
        if (!config.enabled()) {
            // guard 未启用时放行（与原语义一致）
            return true;
        }

        String normalizedModule = normalizeKey(module);
        String normalizedPacketId = normalizeKey(packetId);
        String normalizedAction = normalizeKey(action);
        ClientPacketGuardRule rule = config.resolve(normalizedModule, normalizedAction);
        if (!rule.enabled()) {
            return true;
        }

        // packetId 上限：packetId 也是客户端可控字段，随机 packetId 会无限造桶，
        // 超过上限直接拒绝且不记录（与 action 键上限同语义）。
        String modulePlayerKey = normalizedModule + "|" + playerId;
        java.util.Set<String> packetIds = packetIdsByModulePlayer.computeIfAbsent(
            modulePlayerKey, ignored -> ConcurrentHashMap.newKeySet());
        if (!packetIds.contains(normalizedPacketId)) {
            if (packetIds.size() >= MAX_PACKET_IDS_PER_MODULE_PLAYER) {
                if (debugLogging && plugin != null) {
                    xuanmo.arcartxsuite.module.AxsLog.logger().info(
                        "ClientPacketGuard 拒绝回包 -> player=" + playerName
                            + " | route=" + normalizedModule + "." + normalizedAction
                            + " | packetId=" + normalizedPacketId
                            + " | 原因=packetId 数超上限 " + MAX_PACKET_IDS_PER_MODULE_PLAYER);
                }
                return false;
            }
            packetIds.add(normalizedPacketId);
        }

        // action 键上限：恶意客户端可随机化 action 无限创建 GuardState，超过上限直接拒绝且不建状态。
        // 键集按 packetId 隔离——高基数包（tooltip 物品指纹等）只塞满自己 packetId 的桶。
        String mpKey = modulePlayerKey + "|" + normalizedPacketId;
        java.util.Set<String> actionKeys = actionKeysByModulePlayer.computeIfAbsent(
            mpKey, ignored -> ConcurrentHashMap.newKeySet());
        if (!actionKeys.contains(normalizedAction)) {
            if (actionKeys.size() >= MAX_ACTION_KEYS_PER_MODULE_PLAYER) {
                if (debugLogging && plugin != null) {
                    xuanmo.arcartxsuite.module.AxsLog.logger().info(
                        "ClientPacketGuard 拒绝回包 -> player=" + playerName
                            + " | route=" + normalizedModule + "." + normalizedAction
                            + " | packetId=" + normalizedPacketId
                            + " | 原因=action 键数超上限 " + MAX_ACTION_KEYS_PER_MODULE_PLAYER);
                }
                return false;
            }
            actionKeys.add(normalizedAction);
        }

        long now = timeSupplier.getAsLong();
        RouteKey routeKey = new RouteKey(normalizedModule, normalizedPacketId, normalizedAction, playerId);
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
                } else if (rule.mode() == ClientPacketGuardMode.PUNISH
                    && (state.lastPunishAt <= 0L || now - state.lastPunishAt >= rule.punishCooldownMs())) {
                    // 惩罚命令按 punish-cooldown-ms 冷却，防止连续超限反复执行控制台命令
                    feedbackSink.punish(rule.punishCommand());
                    state.lastPunishAt = now;
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
                    + " | packetId="
                    + normalizedPacketId
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

    @Override
    public Map<String, ? extends java.util.Collection<String>> actionKeys(UUID playerId) {
        Map<String, java.util.Collection<String>> result = new LinkedHashMap<>();
        if (playerId == null) {
            return result;
        }
        String segment = "|" + playerId + "|";
        for (Map.Entry<String, java.util.Set<String>> entry : actionKeysByModulePlayer.entrySet()) {
            String mpKey = entry.getKey();
            if (!mpKey.contains(segment)) {
                continue;
            }
            // mpKey 格式 module|playerId|packetId → 展示为 module|packetId
            String bucket = mpKey.replace(segment, "|");
            result.put(bucket, java.util.List.copyOf(entry.getValue()));
        }
        return result;
    }

    @Override
    public void clearPlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        states.keySet().removeIf(key -> playerId.equals(key.playerId()));
        actionKeysByModulePlayer.keySet().removeIf(key -> key.contains("|" + playerId + "|"));
        packetIdsByModulePlayer.keySet().removeIf(key -> key.endsWith("|" + playerId));
    }

    private void cleanup() {
        long now = timeSupplier.getAsLong();
        ClientPacketGuardConfiguration config = this.configuration;
        for (Map.Entry<RouteKey, GuardState> entry : states.entrySet()) {
            ClientPacketGuardRule rule = config.resolve(entry.getKey().module(), entry.getKey().action());
            GuardState state = entry.getValue();
            boolean remove;
            synchronized (state) {
                state.prune(now, rule.windowMs());
                long retentionMs = Math.max(Math.max(rule.windowMs(), rule.notifyCooldownMs()), rule.punishCooldownMs());
                remove = state.hitTimestamps.isEmpty()
                    && now - Math.max(Math.max(state.lastTouchedAt, state.lastNotifyAt), state.lastPunishAt) > retentionMs;
            }
            if (remove) {
                states.remove(entry.getKey(), state);
                RouteKey key = entry.getKey();
                String modulePlayerKey = key.module() + "|" + key.playerId();
                String mpKey = modulePlayerKey + "|" + key.packetId();
                java.util.Set<String> actionKeys = actionKeysByModulePlayer.get(mpKey);
                if (actionKeys != null) {
                    actionKeys.remove(key.action());
                    if (actionKeys.isEmpty()) {
                        actionKeysByModulePlayer.remove(mpKey, actionKeys);
                        // 桶空了 → 该 packetId 也从已见集合中释放，腾出 packetId 额度
                        java.util.Set<String> packetIds = packetIdsByModulePlayer.get(modulePlayerKey);
                        if (packetIds != null) {
                            packetIds.remove(key.packetId());
                            if (packetIds.isEmpty()) {
                                packetIdsByModulePlayer.remove(modulePlayerKey, packetIds);
                            }
                        }
                    }
                }
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

    private record RouteKey(String module, String packetId, String action, UUID playerId) {
    }

    private static final class GuardState {
        private final ArrayDeque<Long> hitTimestamps = new ArrayDeque<>();
        private long lastNotifyAt;
        private long lastPunishAt;
        private long lastTouchedAt;

        private void prune(long now, long windowMs) {
            while (!hitTimestamps.isEmpty() && now - hitTimestamps.peekFirst() >= windowMs) {
                hitTimestamps.removeFirst();
            }
        }
    }
}
