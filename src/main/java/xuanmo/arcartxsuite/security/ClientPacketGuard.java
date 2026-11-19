package xuanmo.arcartxsuite.security;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
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

    private static final int MAX_INGRESS_FIELDS = 128;
    private static final int MAX_INGRESS_ID_LENGTH = 128;
    private static final int MAX_INGRESS_PACKET_BYTES = 256 * 1024;
    private static final int MAX_INGRESS_BYTES_PER_SECOND = 1024 * 1024;
    private static final int MAX_INGRESS_PACKETS_PER_SECOND = 128;

    private final JavaPlugin plugin;
    private volatile ClientPacketGuardConfiguration configuration;
    private final LongSupplier timeSupplier;
    private final ConcurrentMap<UUID, PlayerState> players = new ConcurrentHashMap<>();

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
        if (plugin == null) {
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
        players.forEach(this::retire);
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

    /** Transport limits also apply when per-action throttling is disabled. */
    public boolean allowIngress(UUID playerId, String packetId, List<String> data) {
        if (playerId == null || packetId == null || packetId.length() > MAX_INGRESS_ID_LENGTH || packetId.isBlank()
            || data == null || data.size() > MAX_INGRESS_FIELDS) {
            return false;
        }
        // Three bytes per UTF-16 code unit bounds UTF-8 size without allocating an encoded copy.
        long bytes = 3L * packetId.length();
        for (String field : data) {
            if (field == null) return false;
            bytes += 3L * field.length();
            if (bytes > MAX_INGRESS_PACKET_BYTES) return false;
        }

        while (true) {
            PlayerState state = players.computeIfAbsent(playerId, ignored -> new PlayerState());
            synchronized (state) {
                if (state.retired) continue;
                long now = timeSupplier.getAsLong();
                var window = state.ingress;
                if (window == null) {
                    window = new IngressWindow(now);
                    state.ingress = window;
                } else if (now < window.startedAt || now - window.startedAt >= 1000L) {
                    window.startedAt = now;
                    window.packets = 0;
                    window.bytes = 0;
                }
                if (window.packets >= MAX_INGRESS_PACKETS_PER_SECOND || window.bytes + bytes > MAX_INGRESS_BYTES_PER_SECOND) {
                    return false;
                }
                window.packets++;
                window.bytes += (int) bytes;
                return true;
            }
        }
    }

    @Override
    public boolean allow(Player player, String module, String action, boolean debugLogging) {
        return allow(player, module, null, action, debugLogging);
    }

    @Override
    public boolean allow(Player player, String module, String packetId, String action, boolean debugLogging) {
        return player != null && allow(player.getUniqueId(), null, player, module, packetId, action, debugLogging, null);
    }

    boolean allow(UUID playerId, String playerName, String module, String packetId, String action,
                  boolean debugLogging, FeedbackSink feedbackSink) {
        return allow(playerId, playerName, null, module, packetId, action, debugLogging, feedbackSink);
    }

    private boolean allow(UUID playerId, String playerName, Player player, String module, String packetId, String action,
                          boolean debugLogging, FeedbackSink feedbackSink) {
        if (playerId == null) return false;
        ClientPacketGuardConfiguration config = configuration;
        if (!config.enabled()) return true;

        String normalizedModule = normalizeKey(module);
        String normalizedPacket = normalizeKey(packetId);
        String normalizedAction = normalizeKey(action);
        ClientPacketGuardRule rule = config.resolveNormalized(normalizedModule, normalizedAction);
        if (!rule.enabled()) return true;

        Decision decision = admit(playerId, normalizedModule, normalizedPacket, normalizedAction, rule);
        if (decision == Decision.ALLOW) return true;
        if (decision == Decision.NOTIFY) {
            if (feedbackSink != null) feedbackSink.notifyPlayer(rule.notifyMessage());
            else if (player != null && !rule.notifyMessage().isBlank()) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', rule.notifyMessage()));
            }
        } else if (decision == Decision.PUNISH) {
            if (feedbackSink != null) feedbackSink.punish(rule.punishCommand());
            else if (player != null && !rule.punishCommand().isBlank()) {
                String safeName = sanitizePlayerName(player.getName());
                if (safeName != null) Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    rule.punishCommand().replace("{player}", safeName).replace("<player>", safeName));
            }
        }

        if (debugLogging && plugin != null) {
            String name = player == null ? playerName : player.getName();
            String reason = switch (decision) {
                case PACKET_LIMIT -> "packetId 数超上限 " + MAX_PACKET_IDS_PER_MODULE_PLAYER;
                case ACTION_LIMIT -> "action 键数超上限 " + MAX_ACTION_KEYS_PER_MODULE_PLAYER;
                default -> "windowMs=" + rule.windowMs() + " | maxHits=" + rule.maxHits() + " | mode=" + rule.mode().configValue();
            };
            xuanmo.arcartxsuite.module.AxsLog.logger().info(
                "ClientPacketGuard 拒绝回包 -> player=" + name + " | route=" + normalizedModule + "." + normalizedAction
                    + " | packetId=" + normalizedPacket + " | 原因=" + reason);
        }
        return false;
    }

    private Decision admit(UUID playerId, String module, String packetId, String action, ClientPacketGuardRule rule) {
        while (true) {
            var player = players.computeIfAbsent(playerId, ignored -> new PlayerState());
            synchronized (player) {
                if (player.retired) continue;
                var packets = player.modules.computeIfAbsent(module, ignored -> new HashMap<>());
                var actions = packets.get(packetId);
                if (actions == null) {
                    if (packets.size() >= MAX_PACKET_IDS_PER_MODULE_PLAYER) return Decision.PACKET_LIMIT;
                    actions = new HashMap<>();
                    packets.put(packetId, actions);
                }
                GuardState state = actions.get(action);
                if (state == null) {
                    if (actions.size() >= MAX_ACTION_KEYS_PER_MODULE_PLAYER) return Decision.ACTION_LIMIT;
                    state = new GuardState();
                    actions.put(action, state);
                }
                return state.admit(timeSupplier.getAsLong(), rule);
            }
        }
    }

    @Override
    public Map<String, ? extends java.util.Collection<String>> actionKeys(UUID playerId) {
        if (playerId == null) return Map.of();
        PlayerState player = players.get(playerId);
        if (player == null) return Map.of();
        Map<String, List<String>> result = new LinkedHashMap<>();
        synchronized (player) {
            if (!player.retired) player.modules.forEach((module, packets) ->
                packets.forEach((id, actions) -> result.put(module + "|" + id, List.copyOf(actions.keySet()))));
        }
        return result;
    }

    @Override
    public void clearPlayer(UUID playerId) {
        if (playerId == null) return;
        PlayerState player = players.get(playerId);
        if (player != null) retire(playerId, player);
    }

    private void retire(UUID playerId, PlayerState player) {
        synchronized (player) {
            player.retired = true;
            players.remove(playerId, player);
        }
    }

    void cleanup() {
        ClientPacketGuardConfiguration config = configuration;
        long now = timeSupplier.getAsLong();
        players.forEach((id, player) -> {
            synchronized (player) {
                if (player.retired) return;
                if (player.ingress != null && now - player.ingress.startedAt >= 2000L) player.ingress = null;
                player.modules.entrySet().removeIf(module -> {
                    module.getValue().values().removeIf(actions -> {
                        actions.entrySet().removeIf(action -> action.getValue().expired(now,
                            config.resolveNormalized(module.getKey(), action.getKey())));
                        return actions.isEmpty();
                    });
                    return module.getValue().isEmpty();
                });
                if (player.ingress == null && player.modules.isEmpty()) retire(id, player);
            }
        });
    }

    private static String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static final class IngressWindow {
        private long startedAt;
        private int packets;
        private int bytes;

        private IngressWindow(long startedAt) {
            this.startedAt = startedAt;
        }
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

    private enum Decision { ALLOW, LIMIT, PACKET_LIMIT, ACTION_LIMIT, NOTIFY, PUNISH }

    private static final class PlayerState {
        /** Module → packet ID → action. This container is locked as a whole for quota changes. */
        private final Map<String, Map<String, Map<String, GuardState>>> modules = new HashMap<>();
        private IngressWindow ingress;
        private boolean retired;
    }

    private static final class GuardState {
        private long[] hitTimestamps = new long[4];
        private int firstHit;
        private int hits;
        private long lastNotifyAt = Long.MIN_VALUE;
        private long lastPunishAt = Long.MIN_VALUE;
        private long lastTouchedAt;

        private Decision admit(long now, ClientPacketGuardRule rule) {
            prune(now, rule.windowMs());
            lastTouchedAt = now;
            if (hits < rule.maxHits()) {
                add(now);
                return Decision.ALLOW;
            }
            if (rule.mode() == ClientPacketGuardMode.NOTIFY
                && (lastNotifyAt == Long.MIN_VALUE || now - lastNotifyAt >= rule.notifyCooldownMs())) {
                lastNotifyAt = now;
                return Decision.NOTIFY;
            }
            if (rule.mode() == ClientPacketGuardMode.PUNISH
                && (lastPunishAt == Long.MIN_VALUE || now - lastPunishAt >= rule.punishCooldownMs())) {
                lastPunishAt = now;
                return Decision.PUNISH;
            }
            return Decision.LIMIT;
        }

        private boolean expired(long now, ClientPacketGuardRule rule) {
            prune(now, rule.windowMs());
            long retention = Math.max(Math.max(rule.windowMs(), rule.notifyCooldownMs()), rule.punishCooldownMs());
            return hits == 0 && now - Math.max(Math.max(lastTouchedAt, lastNotifyAt), lastPunishAt) > retention;
        }

        private void add(long now) {
            if (hits == hitTimestamps.length) {
                long[] grown = new long[hits * 2];
                int firstSegment = hits - firstHit;
                System.arraycopy(hitTimestamps, firstHit, grown, 0, firstSegment);
                System.arraycopy(hitTimestamps, 0, grown, firstSegment, firstHit);
                hitTimestamps = grown;
                firstHit = 0;
            }
            hitTimestamps[(firstHit + hits) % hitTimestamps.length] = now;
            hits++;
        }

        private void prune(long now, long windowMs) {
            while (hits > 0 && now - hitTimestamps[firstHit] >= windowMs) {
                firstHit = (firstHit + 1) % hitTimestamps.length;
                hits--;
            }
        }
    }
}
