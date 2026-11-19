package xuanmo.arcartxsuite.essentials.service;

import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.essentials.config.EssentialsConfiguration;
import xuanmo.arcartxsuite.essentials.storage.EssentialsRepository;

public final class ModerationService implements Listener {

    private static final String BANS_UNAVAILABLE = ChatColor.RED + "暂时无法验证封禁状态，请稍后重试。";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault());
    private final EssentialsRepository repository;
    private final EssentialsConfiguration.ModerationConfiguration config;
    private final Logger logger;
    private final EssentialsWork work;
    // 拦截已经通过异步查询、但仍未完成主线程登录的连接。
    private final ConcurrentHashMap<UUID, EssentialsRepository.BanRecord> localBans = new ConcurrentHashMap<>();
    private JavaPlugin plugin;
    private BukkitTask expirationTask;
    private volatile boolean running;
    private volatile boolean stopped;
    private volatile boolean loadingBans;

    public ModerationService(EssentialsRepository repository, EssentialsConfiguration.ModerationConfiguration config,
                             Logger logger, EssentialsWork work) {
        this.repository = repository;
        this.config = config;
        this.logger = logger;
        this.work = work;
    }

    public void beginLoadingBans() { loadingBans = true; }

    public void initialize() throws SQLException {
        for (var ban : repository.listActiveBans(Integer.MAX_VALUE, 0)) {
            localBans.put(ban.uuid(), ban);
        }
        loadingBans = false;
    }

    public void start(JavaPlugin host) {
        this.plugin = host;
        running = true;
        for (Player player : Bukkit.getOnlinePlayers()) enforce(localBans.get(player.getUniqueId()), player);
        expirationTask = Bukkit.getScheduler().runTaskTimerAsynchronously(host, () -> work.call(() -> {
            if (!running) return null;
            try {
                repository.expireBans(200);
                localBans.values().removeIf(EssentialsRepository.BanRecord::isExpired);
            } catch (SQLException e) {
                logger.log(Level.WARNING, "记录封禁到期失败", e);
            }
            return null;
        }, null), 20L, 1200L);
    }

    public void stop() {
        running = false;
        stopped = true;
        if (expirationTask != null) expirationTask.cancel();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        remember(event.getPlayer());
    }

    private void remember(Player player) {
        if(!running)return;
        UUID id = player.getUniqueId();
        String name = player.getName();
        work.submit(() -> {
            repository.rememberPlayer(id, name);
            return null;
        }).exceptionally(failure->{logger.log(Level.WARNING,"玩家历史名称未保存: "+id,failure);return null;});
    }

    /** Database-thread operation; callers capture identity/IP on the server thread. */
    public EssentialsRepository.BanRecord ban(Identity identity, String operator, String reason, long expiry, String ip)
        throws SQLException {
        var record = repository.ban(identity.id(), identity.name(), reason, operator, expiry, ip);
        localBans.put(identity.id(), record);
        return record;
    }

    /** Main-thread effect after a committed ban; a newer unban/replacement takes precedence. */
    public void enforce(EssentialsRepository.BanRecord record, Player target) {
        if (!stopped && !work.isClosed() && record != null && !record.isExpired()
            && record.equals(localBans.get(record.uuid())) && target != null && target.getUniqueId().equals(record.uuid()))
            target.kickPlayer(banMessage(record.reason(), record.expiresAt()));
    }

    public boolean unban(UUID id, String operator) throws SQLException {
        boolean removed = repository.unban(id, operator);
        if (removed) localBans.remove(id);
        return removed;
    }

    public EssentialsRepository.WarningResult warn(Identity target, String reason, String operator, int maximum, long since) throws SQLException {
        var result = repository.warn(target.id(), target.name(), reason, operator, maximum, since);
        if (result.ban() != null) localBans.put(target.id(), result.ban());
        return result;
    }

    /** 通过真实玩家身份、历史记录或服务器已有的 UUID 数据定位玩家。 */
    public java.util.concurrent.CompletionStage<Identity> resolve(String target) {
        Player player;
        try { player = Bukkit.getPlayer(UUID.fromString(target)); }
        catch (IllegalArgumentException name) { player = Bukkit.getPlayerExact(target); }
        Identity online = player == null ? null : new Identity(player.getUniqueId(), player.getName());
        return work.read(() -> resolveStored(target, online)).thenCompose(found -> found != null
            ? java.util.concurrent.CompletableFuture.completedFuture(found)
            : work.main(() -> resolveOffline(target)));
    }

    Identity resolveStored(String target, Identity online) throws SQLException {
        UUID id;
        try {
            id = UUID.fromString(target);
        } catch (IllegalArgumentException ignored) {
            var ids = repository.findKnownPlayers(target);
            if (ids.size() > 1 || (online != null && !ids.isEmpty() && !ids.contains(online.id()))) {
                throw new IdentityException("该玩家名对应多个 UUID，请使用 UUID。");
            }
            if (online != null) return online;
            if (ids.isEmpty()) return null;
            id = ids.get(0);
            String known = repository.knownPlayerName(id);
            return new Identity(id, known == null ? target : known);
        }
        if (online != null) return online;
        String known = repository.knownPlayerName(id);
        if (known != null) return new Identity(id, known);
        var ban = repository.getBan(id);
        return ban == null ? null : new Identity(id, ban.playerName());
    }

    /** Rare pre-Suite player fallback; Bukkit profile/file APIs remain on the main thread. */
    private Identity resolveOffline(String target) {
        try {
            var offline = Bukkit.getOfflinePlayer(UUID.fromString(target));
            String name = offline.getName();
            return offline.hasPlayedBefore() && name != null ? new Identity(offline.getUniqueId(), name) : null;
        } catch (IllegalArgumentException nameTarget) {
            Identity match = null;
            for (var offline : Bukkit.getOfflinePlayers()) {
                String name = offline.getName();
                if (name == null || !name.equalsIgnoreCase(target) || !offline.hasPlayedBefore()) continue;
                if (match != null && !match.id().equals(offline.getUniqueId())) throw new IdentityException("该玩家名对应多个 UUID，请使用 UUID。");
                match = new Identity(offline.getUniqueId(), name);
            }
            return match;
        }
    }

    public record Identity(UUID id, String name) {}
    public static final class IdentityException extends IllegalArgumentException {
        public IdentityException(String message) { super(message); }
    }

    public static String formatDate(long timestamp) {
        return DATE.format(Instant.ofEpochMilli(timestamp));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (stopped) return;
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        if (loadingBans) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, BANS_UNAVAILABLE);
            return;
        }
        work.call(()->{checkPreLogin(event);return null;},null);
    }

    private void checkPreLogin(AsyncPlayerPreLoginEvent event) {
        if(stopped)return;
        UUID id = event.getUniqueId();
        EssentialsRepository.BanRecord local = localBans.get(id);
        try {
            EssentialsRepository.BanRecord ban = repository.getBan(id);
            if(stopped)return;
            if (ban != null && !ban.isExpired()) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, banMessage(ban.reason(), ban.expiresAt()));
            } else if (local != null) {
                localBans.remove(id, local);
            }
        } catch (SQLException e) {
            if(stopped)return;
            logger.log(Level.WARNING, "无法检查玩家封禁状态 " + id, e);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, BANS_UNAVAILABLE);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(PlayerLoginEvent event) {
        if(stopped||work.isClosed())return;
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) return;
        if (loadingBans) {
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, BANS_UNAVAILABLE);
            return;
        }
        EssentialsRepository.BanRecord ban = localBans.get(event.getPlayer().getUniqueId());
        if (ban != null && !ban.isExpired()) {
            event.disallow(PlayerLoginEvent.Result.KICK_BANNED, banMessage(ban.reason(), ban.expiresAt()));
        }
    }

    private String banMessage(String reason, long expiry) {
        return config.banMessage().replace("\\n", "\n")
            .replace("{reason}", reason == null ? "无" : reason)
            .replace("{expiry}", expiry > 0 ? DATE.format(Instant.ofEpochMilli(expiry)) : "永久");
    }

    /** 解析正整数时长，默认单位为秒。无效输入和算术溢出都由调用方提示。 */
    public static long expiryAt(String input, long now) {
        String value = input.toLowerCase(Locale.ROOT);
        if (!value.matches("[0-9]+[smhd]?")) throw new IllegalArgumentException("无效时长");
        long unit = switch (value.charAt(value.length() - 1)) {
            case 'm' -> 60_000L;
            case 'h' -> 3_600_000L;
            case 'd' -> 86_400_000L;
            default -> 1_000L;
        };
        int end = Character.isDigit(value.charAt(value.length() - 1)) ? value.length() : value.length() - 1;
        long duration = Long.parseLong(value.substring(0, end));
        if (duration == 0) throw new IllegalArgumentException("时长须大于零");
        return Math.addExact(now, Math.multiplyExact(duration, unit));
    }
}
