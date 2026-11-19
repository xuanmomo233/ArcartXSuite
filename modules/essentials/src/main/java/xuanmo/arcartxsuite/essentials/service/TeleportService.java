package xuanmo.arcartxsuite.essentials.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.essentials.config.EssentialsConfiguration;
import xuanmo.arcartxsuite.essentials.command.EssentialsCommandDelegate;
import xuanmo.arcartxsuite.api.capability.AbyssAdmissionAccess;
import xuanmo.arcartxsuite.api.capability.RegionQueryable;
import xuanmo.arcartxsuite.essentials.storage.EssentialsRepository;

/**
 * 传送服务。
 * <p>
 * 实现 {@link Listener}，负责家（home）、传送点（warp）、出生点（spawn）、
 * TPA 请求、/top、/back 以及带预热的延迟传送等功能。
 * 通过 {@link EssentialsRepository} 持久化位置数据，并监听玩家移动与退出事件
 * 以取消预热中的传送和清理待处理 TPA 请求。
 */
public final class TeleportService implements Listener {

    private final JavaPlugin plugin;
    private final EssentialsConfiguration config;
    private final EssentialsRepository repository;
    private final PlayerManagementService playerService;
    private final Logger logger;
    private final EssentialsWork work;
    private final Map<UUID, HomeNames> homeNames = new java.util.HashMap<>();
    private record TpaLookup(UUID sender, UUID target) {}
    private final Map<TpaLookup, Object> tpaLookups = new java.util.HashMap<>();

    // TPA 请求: target -> sender
    private final TpaRequests pendingTpa;
    private final WarpDirectory warps;
    // 传送等待中的玩家
    private final Map<UUID, Warmup> warmups = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastDamage = new java.util.HashMap<>();
    private final Map<UUID, Long> unstuckCooldowns = new java.util.HashMap<>();
    private final java.util.function.Supplier<AbyssAdmissionAccess> abyss;
    private final java.util.function.Supplier<RegionQueryable> regions;
    private BukkitTask tpaExpiryTask;
    private final xuanmo.arcartxsuite.api.scheduler.ModuleCallback tpaPulse =
        new xuanmo.arcartxsuite.api.scheduler.ModuleCallback(this::expireTpa);
    private final xuanmo.arcartxsuite.api.scheduler.ModuleCallback callbacks =
        new xuanmo.arcartxsuite.api.scheduler.ModuleCallback(()->{});
    private boolean running;
    // 出生点后端（懒加载，按 MV 是否激活动态选择）
    private volatile LocalSpawnBackend localSpawnBackend;
    private volatile SpawnBackend multiverseSpawnBackend;

    /**
     * 构造传送服务。
     *
     * @param plugin        宿主插件实例
     * @param config        模块配置
     * @param repository    数据仓储
     * @param playerService 玩家管理服务（用于 /back 位置栈）
     * @param logger        日志器
     */
    public TeleportService(JavaPlugin plugin, EssentialsConfiguration config,
                           EssentialsRepository repository, PlayerManagementService playerService, Logger logger,
                           WarpDirectory warps,
                           java.util.function.Supplier<AbyssAdmissionAccess> abyss,
                           java.util.function.Supplier<RegionQueryable> regions,
                           EssentialsWork work) {
        this.plugin = plugin;
        this.config = config;
        this.repository = repository;
        this.playerService = playerService;
        this.logger = logger;
        this.work = work;
        this.abyss = abyss;
        this.regions = regions;
        this.pendingTpa = new TpaRequests(config.features().tpa());
        this.warps = warps;
    }

    /**
     * 注册事件监听器。
     */
    public void start() {
        running = true;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        if(config.features().tpa().enabled() || config.spawn().respawnAtSpawn() || config.features().unstuck().enabled())
            tpaExpiryTask = Bukkit.getScheduler().runTaskTimer(plugin, tpaPulse, 20L, 20L);
        if (!multiverseActive() && config.spawn().respawnAtSpawn()) localSpawnBackend().refreshIfDue();
        for (Player player : Bukkit.getOnlinePlayers()) preloadHomeNames(player);
    }

    /**
     * 注销事件监听器，取消全部预热任务并清空待处理 TPA 请求。
     */
    public void shutdown() {
        running = false;
        tpaPulse.close();
        callbacks.close();
        HandlerList.unregisterAll(this);
        if (tpaExpiryTask != null) tpaExpiryTask.cancel();
        warmups.values().forEach(warmup -> warmup.task().cancel());
        warmups.clear();
        pendingTpa.clear();
        homeNames.clear();
        tpaLookups.clear();
        lastDamage.clear();
        unstuckCooldowns.clear();
    }

    public java.util.concurrent.CompletionStage<Void> stopped(){return java.util.concurrent.CompletableFuture.allOf(
        tpaPulse.stopped().toCompletableFuture(),callbacks.stopped().toCompletableFuture());}

    // ─── Home ───

    /**
     * 设置玩家的家，支持权限覆盖上限检查。
     *
     * @param player 目标玩家
     * @param name   家名称
     */
    public void setHome(Player player, String name) {
        if (!validName(player, name)) return;
        int maximum = config.teleport().maxHomes();
        for (int i = 100; i > maximum; i--) {
            if (player.hasPermission("axs.essentials.homes." + i)) { maximum = i; break; }
        }
        int limit = maximum;
        UUID id = player.getUniqueId();
        var position = capture(player.getLocation());
        background(player, "sethome", () -> repository.setHome(id, name, position, limit), saved -> {
            cacheNames(player, saved.names());
            player.sendMessage(prefix() + (saved.saved() ? config.messages().homeSet().replace("{name}", name)
                : config.messages().homeLimit().replace("{max}", String.valueOf(limit))));
        }, "设置家失败。");
    }

    /**
     * 删除玩家的指定家。
     *
     * @param player 目标玩家
     * @param name   家名称
     */
    public void deleteHome(Player player, String name) {
        if (!validName(player, name)) return;
        UUID id = player.getUniqueId();
        background(player, "delhome", () -> repository.deleteHome(id, name), removed -> {
            HomeNames cached = homeNames.get(id);
            if (removed && cached != null) cached.names = cached.names.stream()
                .filter(item -> !item.equals(name.toLowerCase(java.util.Locale.ROOT))).toList();
            player.sendMessage(prefix() + (removed ? config.messages().homeDeleted() : config.messages().homeNotFound())
                .replace("{name}", name));
        }, "删除家失败。");
    }

    /**
     * 将玩家延迟传送到指定的家。
     *
     * @param player 目标玩家
     * @param name   家名称
     */
    public void teleportHome(Player player, String name) {
        if (!validName(player, name)) return;
        UUID id = player.getUniqueId();
        AuthSnapshot admission = AuthAccess.snapshot(id);
        if (!currentAdmission(player, admission)) return;
        query(player, "home", () -> repository.getHome(id, name), position -> {
            if (!currentAdmission(player, admission)) return;
            Location location = resolve(position);
            if (location == null) {
                player.sendMessage(prefix() + config.messages().homeNotFound().replace("{name}", name));
                return;
            }
            int delay = player.hasPermission("axs.essentials.teleport.bypass-delay") ? 0 : config.teleport().teleportDelay();
            scheduleWarmup(player, admission, delay, false, () -> {
                if (current(player, "home")) executeTeleport(player, location, config.messages().homeTeleported().replace("{name}", name), null);
            });
        }, "读取家失败。");
    }

    /**
     * 返回玩家全部家的名称列表。
     *
     * @param player 目标玩家
     * @return 家名称不可变列表，查询失败时返回空列表
     */
    public List<String> getHomeNames(Player player) {
        if (!running || !player.isOnline()) return List.of();
        preloadHomeNames(player);
        HomeNames cached = homeNames.get(player.getUniqueId());
        return cached == null ? List.of() : cached.names;
    }

    private void preloadHomeNames(Player player) {
        if (!running || !(config.features().actionEnabled("home") && player.hasPermission("axs.essentials.home")
            || config.features().actionEnabled("delhome") && player.hasPermission("axs.essentials.delhome"))) return;
        UUID id = player.getUniqueId();
        HomeNames existing = homeNames.get(id);
        if (existing != null && existing.player == player) return;
        HomeNames cached = new HomeNames(player);
        homeNames.put(id, cached);
        work.read(() -> repository.listHomeNames(id)).whenComplete((names, failure) -> work.onMain(() -> {
            if (homeNames.get(id) != cached || !sameSession(player)) return;
            if (failure != null) { homeNames.remove(id, cached); return; }
            cached.names = names;
        }));
    }

    private void cacheNames(Player player, List<String> names) {
        HomeNames cached = homeNames.computeIfAbsent(player.getUniqueId(), id -> new HomeNames(player));
        cached.names = names;
    }

    private static final class HomeNames {
        private final Player player;
        private List<String> names = List.of();
        private HomeNames(Player player) { this.player = player; }
    }

    private <T> void background(Player player, String action, java.util.concurrent.Callable<T> operation,
                                java.util.function.Consumer<T> reply, String failureMessage) {
        if (!current(player, action)) return;
        complete(player, action, work.submit(operation), reply, failureMessage);
    }

    private <T> void query(Player player, String action, java.util.concurrent.Callable<T> operation,
                           java.util.function.Consumer<T> reply, String failureMessage) {
        if (!current(player, action)) return;
        complete(player, action, work.read(operation), reply, failureMessage);
    }

    private <T> void complete(Player player, String action, java.util.concurrent.CompletionStage<T> pending,
                              java.util.function.Consumer<T> reply, String failureMessage) {
        pending.whenComplete((value, failure) -> work.onMain(() -> {
            if (!current(player, action)) return;
            if (failure != null) {
                logger.log(Level.WARNING, "Essentials." + action + " 失败", failure);
                player.sendMessage(prefix() + ChatColor.RED + failureMessage);
            } else reply.accept(value);
        }));
    }

    private boolean sameSession(Player player) {
        return running && player.isOnline() && Bukkit.getPlayer(player.getUniqueId()) == player;
    }

    private boolean currentAdmission(Player player, AuthSnapshot admission) {
        if (!admission.admitted() || !sameSession(player)) return false;
        AuthSnapshot current = AuthAccess.snapshot(player.getUniqueId());
        return current.admitted() && current.generation() == admission.generation()
            && Objects.equals(current.connectionId(), admission.connectionId());
    }

    private boolean current(Player player, String action) {
        return sameSession(player) && config.features().actionEnabled(action)
            && EssentialsCommandDelegate.hasActionPermission(player, action);
    }

    static EssentialsRepository.Position capture(Location location) {
        return new EssentialsRepository.Position(location.getWorld().getName(), location.getX(), location.getY(), location.getZ(),
            location.getYaw(), location.getPitch());
    }

    static Location resolve(EssentialsRepository.Position position) {
        if (position == null) return null;
        World world = Bukkit.getWorld(position.world());
        return world == null ? null : new Location(world, position.x(), position.y(), position.z(), position.yaw(), position.pitch());
    }

    // ─── Warp ───

    /**
     * 以玩家当前位置设置公共传送点。
     *
     * @param player 执行者
     * @param name   传送点名称
     */
    public void setWarp(Player player, String name) {
        var location = capture(player.getLocation());
        background(player, "setwarp", () -> {
            warps.setWarp(name, location.world(), location.x(), location.y(), location.z(), location.yaw(), location.pitch());
            return true;
        }, ignored -> {
            player.sendMessage(prefix() + config.messages().warpSet().replace("{name}", name));
        }, "无法保存传送点，请查看服务端日志。");
    }

    /**
     * 删除指定公共传送点。
     *
     * @param player 执行者
     * @param name   传送点名称
     */
    public void deleteWarp(Player player, String name) {
        background(player, "delwarp", () -> warps.deleteWarp(name), removed -> {
            if (!removed) {
                player.sendMessage(prefix() + config.messages().warpNotFound().replace("{name}", name));
                return;
            }
            player.sendMessage(prefix() + config.messages().warpDeleted().replace("{name}", name));
        }, "无法保存传送点，请查看服务端日志。");
    }

    /**
     * 将玩家延迟传送到指定公共传送点。
     *
     * @param player 目标玩家
     * @param name   传送点名称
     */
    public void teleportWarp(Player player, String name) {
        AuthSnapshot admission = AuthAccess.snapshot(player.getUniqueId());
        if (!currentAdmission(player, admission) || !current(player, "warp")) return;
        if (warps.destination(player, name) == null) {
            player.sendMessage(prefix() + config.messages().warpNotFound().replace("{name}", name));
            return;
        }
        int delay = player.hasPermission("axs.essentials.teleport.bypass-delay") ? 0 : config.teleport().teleportDelay();
        scheduleWarmup(player, admission, delay, false, () -> {
            if (!current(player, "warp")) return;
            Location destination = warps.destination(player, name);
            if (destination != null) executeTeleport(player, destination, config.messages().warpTeleported().replace("{name}", name), null);
            else player.sendMessage(prefix() + config.messages().warpNotFound().replace("{name}", name));
        });
    }

    public WarpDirectory warps() {
        return warps;
    }

    // ─── Spawn ───

    /**
     * 以玩家当前位置设置服务器出生点。
     * <p>
     * 安装 Multiverse-Core 时写入该世界的 MV {@code spawn-location} 配置；
     * 未安装时写入模块数据库 {@code spawn} 表。
     *
     * @param player 执行者
     */
    public void setSpawn(Player player) {
        if (!current(player, "setspawn")) return;
        spawnBackend().setSpawn(player.getLocation()).whenComplete((success, failure) -> work.onMain(() -> {
            if (current(player, "setspawn")) player.sendMessage(prefix() + (failure == null && Boolean.TRUE.equals(success)
                ? config.messages().spawnSet() : config.messages().spawnFailed()));
        }));
    }

    /**
     * 查询指定世界的出生点（经当前 {@link SpawnBackend}：MV 已加载时返回该世界的
     * MV spawn，否则返回本地存储的全局 spawn）。
     * <p>
     * 未设置时返回 {@code null}——不回退世界默认 spawn，兜底策略由调用方决定。
     * 供模块内使用及 {@code EssentialsQueryable} 能力对外暴露。
     *
     * @param world 上下文世界（MV 后端按世界取 spawn；本地后端忽略）
     * @return 出生点位置，未设置或查询失败返回 {@code null}
     */
    public java.util.concurrent.CompletionStage<Location> getSpawn(@NotNull World world) {
        return work.main(this::spawnBackend).thenCompose(backend -> backend.getSpawn(world));
    }

    /**
     * 将玩家延迟传送到出生点，未设置时回退到世界默认出生点。
     *
     * @param player 目标玩家
     */
    public void teleportSpawn(Player player) {
        AuthSnapshot admission = AuthAccess.snapshot(player.getUniqueId());
        if (!current(player, "spawn") || !currentAdmission(player, admission)) return;
        getSpawn(player.getWorld()).whenComplete((location, failure) -> work.onMain(() -> {
            if (!current(player, "spawn") || !currentAdmission(player, admission)) return;
            if (failure != null) { player.sendMessage(prefix() + config.messages().spawnFailed()); return; }
            Location target = location == null ? player.getWorld().getSpawnLocation() : location;
            int delay = player.hasPermission("axs.essentials.teleport.bypass-delay") ? 0 : config.teleport().teleportDelay();
            scheduleWarmup(player, admission, delay, false, () -> {
                if (current(player, "spawn")) executeTeleport(player, target, config.messages().spawnTeleported(), null);
            });
        }));
    }

    /**
     * 解析当前应使用的出生点后端。
     * <p>
     * Multiverse-Core 已加载时使用 {@link MultiverseSpawnBackend}，
     * 否则使用 {@link LocalSpawnBackend}。每次调用动态判断，
     * MV 后加载或卸载时可无缝切换。
     *
     * @return 当前生效的出生点后端
     */
    private SpawnBackend spawnBackend() {
        if (multiverseActive()) {
            SpawnBackend backend = multiverseSpawnBackend;
            if (backend == null) {
                backend = new MultiverseSpawnBackend(logger);
                multiverseSpawnBackend = backend;
            }
            return backend;
        }
        return localSpawnBackend();
    }

    /**
     * 返回本地出生点后端（懒初始化）。
     *
     * @return 本地后端实例
     */
    private LocalSpawnBackend localSpawnBackend() {
        LocalSpawnBackend backend = localSpawnBackend;
        if (backend == null) {
            backend = new LocalSpawnBackend(repository, work);
            localSpawnBackend = backend;
        }
        return backend;
    }

    /**
     * Multiverse-Core 是否已安装且 API 就绪。
     * <p>
     * 必须先查插件启用再触碰 MV 类（否则无 MV 环境下类解析失败）。
     *
     * @return MV 可接管出生点时返回 {@code true}
     */
    private boolean multiverseActive() {
        try {
            return Bukkit.getPluginManager().isPluginEnabled("Multiverse-Core")
                && MultiverseSpawnBackend.isLoaded();
        } catch (Throwable t) {
            return false;
        }
    }

    // ─── TPA ───

    /**
     * 向目标玩家发送 TPA 请求，并在超时后自动清除。
     *
     * @param sender 请求发起者
     * @param target 请求接收者
     * @param here   {@code true} 为 tpahere（请求对方过来），{@code false} 为 tpa（请求过去）
     */
    public void sendTpa(Player sender, Player target, boolean here) {
        if (!config.features().tpa().enabled()) return;
        if (sender.equals(target)) {
            sender.sendMessage(prefix() + ChatColor.RED + "不能向自己发送传送请求。");
            return;
        }
        if (!playerService.canSee(sender, target) || !playerService.canSee(target, sender)
            || !target.hasPermission("axs.essentials.tpa")) {
            sender.sendMessage(prefix() + config.messages().playerNotFound().replace("{player}", target.getName()));
            return;
        }
        AuthSnapshot senderAdmission = AuthAccess.snapshot(sender.getUniqueId());
        AuthSnapshot targetAdmission = AuthAccess.snapshot(target.getUniqueId());
        if (!tpaEligible(sender, target, here, senderAdmission, targetAdmission)) return;
        TpaLookup key = new TpaLookup(sender.getUniqueId(), target.getUniqueId());
        Object token = new Object();
        if (tpaLookups.putIfAbsent(key, token) != null) {
            sender.sendMessage(prefix() + "该请求正在检查接收设置，请稍后。"); return;
        }
        work.read(() -> repository.receivesTpa(key.target())).whenComplete((receiving, failure) -> work.onMain(() -> {
            if (!tpaLookups.remove(key, token) || !tpaEligible(sender, target, here, senderAdmission, targetAdmission)) return;
            if (failure != null) { logger.log(Level.WARNING, "Essentials 读取传送请求设置失败", failure); sender.sendMessage(prefix() + "暂时无法检查对方的接收设置。"); return; }
            if (!receiving) { sender.sendMessage(prefix() + "对方已关闭传送请求。"); return; }
            finishTpa(sender, target, here, senderAdmission, targetAdmission);
        }));
    }

    private boolean tpaEligible(Player sender, Player target, boolean here,
                                AuthSnapshot senderAdmission, AuthSnapshot targetAdmission) {
        return config.features().tpa().enabled() && currentAdmission(sender, senderAdmission)
            && currentAdmission(target, targetAdmission) && current(sender, here ? "tpahere" : "tpa")
            && target.hasPermission("axs.essentials.tpa") && playerService.canSee(sender, target)
            && playerService.canSee(target, sender);
    }

    private void finishTpa(Player sender, Player target, boolean here,
                           AuthSnapshot senderAdmission, AuthSnapshot targetAdmission) {
        var result = pendingTpa.send(senderAdmission, targetAdmission, here,
            System.currentTimeMillis(), config.teleport().tpaTimeout() * 1000L);
        if (result != TpaRequests.SendResult.SENT) {
            sender.sendMessage(prefix() + ChatColor.RED + switch (result) {
                case COOLDOWN -> "传送请求发送过快，请稍后再试。";
                case INCOMING_FULL -> "对方的待处理请求已满。";
                case OUTGOING_FULL -> "你已发出过多待处理请求。";
                case SENT -> throw new IllegalStateException();
            });
            return;
        }
        sender.sendMessage(prefix() + config.messages().tpaSent().replace("{player}", target.getName()));
        target.sendMessage(prefix() + (here
            ? config.messages().tpaHereReceived().replace("{player}", sender.getName())
            : config.messages().tpaReceived().replace("{player}", sender.getName())));
        target.sendMessage(prefix() + "使用 /ess tpaccept " + sender.getName() + " 或 /ess tpdeny " + sender.getName() + "。");
    }

    private void expireTpa() {
        if (config.spawn().respawnAtSpawn() && !Bukkit.getOnlinePlayers().isEmpty() && !multiverseActive())
            localSpawnBackend().refreshIfDue();
        long now = System.currentTimeMillis();
        unstuckCooldowns.values().removeIf(until -> until <= now);
        lastDamage.values().removeIf(at -> at + config.features().unstuck().combatSeconds() * 1000L <= now);
        pendingTpa.expire(now, request -> {
            Player sender = Bukkit.getPlayer(request.sender());
            Player target = Bukkit.getPlayer(request.target());
            if (sender != null && target != null && playerService.canSee(sender, target)) {
                sender.sendMessage(prefix() + config.messages().tpaExpired());
            }
        });
    }

    public void toggleTpa(Player player) {
        UUID id = player.getUniqueId();
        background(player, "tptoggle", () -> {
            boolean receiving = !repository.receivesTpa(id);
            repository.setReceivesTpa(id, receiving);
            return receiving;
        }, receiving -> {
            if (!receiving) pendingTpa.clearIncoming(id);
            player.sendMessage(prefix() + (receiving ? "已开启接收传送请求。" : "已关闭接收传送请求。"));
        }, "无法保存传送请求设置。");
    }

    public void cancelTpa(Player player, String name) {
        Player target = name.isBlank() ? null : Bukkit.getPlayerExact(name);
        if (!name.isBlank() && (target == null || !playerService.canSee(player, target))) {
            player.sendMessage(prefix() + config.messages().playerNotFound().replace("{player}", name));
            return;
        }
        var cancelled = pendingTpa.cancelOutgoing(player.getUniqueId(), target == null ? null : target.getUniqueId(),
            System.currentTimeMillis());
        int waiting = tpaLookups.size();
        UUID targetId = target == null ? null : target.getUniqueId();
        tpaLookups.keySet().removeIf(key -> key.sender().equals(player.getUniqueId()) && (targetId == null || key.target().equals(targetId)));
        int total = cancelled.size() + waiting - tpaLookups.size();
        for (var request : cancelled) {
            Player recipient = Bukkit.getPlayer(request.target());
            if (recipient != null && playerService.canSee(recipient, player)) {
                recipient.sendMessage(prefix() + player.getName() + " 取消了传送请求。");
            }
        }
        player.sendMessage(prefix() + (total == 0 ? "没有可取消的传送请求。" : "已取消 " + total + " 条传送请求。"));
    }

    public void respondTpa(Player player, String name, boolean accept) {
        var requests = new LinkedHashMap<Player, TpaRequests.Request>();
        for (var request : pendingTpa.received(player.getUniqueId(), System.currentTimeMillis())) {
            Player sender = visibleSender(player, request);
            if (sender != null && (name.isBlank() || sender.getName().equalsIgnoreCase(name))) {
                requests.put(sender, request);
            }
        }
        if (requests.isEmpty()) {
            player.sendMessage(prefix() + config.messages().tpaNoPending());
            return;
        }
        if (requests.size() > 1) {
            player.sendMessage(prefix() + "有多条传送请求，请指定玩家：" + String.join("、",
                requests.keySet().stream().map(Player::getName).toList()));
            return;
        }
        var selected = requests.entrySet().iterator().next();
        var request = selected.getValue();
        Player sender = selected.getKey();
        pendingTpa.remove(request);
        String result = accept ? config.messages().tpaAccepted() : config.messages().tpaDenied();
        player.sendMessage(prefix() + result);
        sender.sendMessage(prefix() + result);
        if (!accept) return;
        Player traveler = request.here() ? player : sender;
        Location destination = (request.here() ? sender : player).getLocation().clone();
        AuthSnapshot travelerAdmission = request.here() ? request.targetAdmission() : request.senderAdmission();
        int delay = traveler.hasPermission("axs.essentials.teleport.bypass-delay") ? 0 : config.teleport().teleportDelay();
        scheduleWarmup(traveler, travelerAdmission, delay, false, () -> {
            if (current(player, "tpaccept")
                && tpaEligible(sender, player, request.here(), request.senderAdmission(), request.targetAdmission())) {
                executeTeleport(traveler, destination, null, null);
            }
        });
    }

    public List<TpaView> tpaRequests(Player player) {
        long now = System.currentTimeMillis();
        var result = new java.util.ArrayList<TpaView>();
        for (var request : pendingTpa.received(player.getUniqueId(), now)) {
            Player sender = visibleSender(player, request);
            if (sender != null) result.add(new TpaView(sender.getName(), request.here(), (request.expiresAt() - now + 999) / 1000));
        }
        return result;
    }

    private Player visibleSender(Player player, TpaRequests.Request request) {
        Player sender = Bukkit.getPlayer(request.sender());
        return sender != null && tpaEligible(sender, player, request.here(), request.senderAdmission(),
            request.targetAdmission()) ? sender : null;
    }

    public record TpaView(String sender, boolean here, long seconds) {}

    // ─── /top ───

    /**
     * 将玩家传送到当前坐标的最高非空气方块上方。
     *
     * @param player 目标玩家
     */
    public void teleportTop(Player player) {
        Location loc = player.getLocation();
        int highestY = loc.getWorld().getHighestBlockYAt(loc.getBlockX(), loc.getBlockZ());
        Location top = new Location(loc.getWorld(), loc.getX(), highestY + 1, loc.getZ(), loc.getYaw(), loc.getPitch());
        scheduleTeleport(player, "top", top, ChatColor.GREEN + "已传送到最高方块。", null);
    }

    // ─── /back ───

    /**
     * 将玩家延迟传送回 /back 栈中的上一个位置。
     *
     * @param player 目标玩家
     */
    public void back(Player player) {
        Location loc = playerService.peekBackLocation(player);
        if (loc == null) {
            player.sendMessage(prefix() + config.messages().backNone());
            return;
        }
        scheduleTeleport(player, "back", loc, config.messages().backTeleported(), loc);
    }

    // ─── Teleport with delay ───

    /**
     * 延迟传送玩家到指定位置，预热期间移动将取消传送。
     * <p>
     * 拥有 {@code axs.essentials.teleport.bypass-delay} 权限或延迟为 0 时即时传送。
     *
     * @param player         目标玩家
     * @param target         目标位置
     * @param successMessage 传送成功后的提示消息，为 null 或空则不发送
     */
    public void delayedTeleport(Player player, Location target, String successMessage) {
        scheduleTeleport(player, "tppos", target, successMessage, null);
    }

    private void scheduleTeleport(Player player, String action, Location target, String successMessage, Location backRecord) {
        if (!current(player, action)) return;
        int delay = player.hasPermission("axs.essentials.teleport.bypass-delay") ? 0 : config.teleport().teleportDelay();
        Location destination = target.clone();
        scheduleWarmup(player, AuthAccess.snapshot(player.getUniqueId()), delay, false, () -> {
            if (current(player, action)) executeTeleport(player, destination, successMessage, backRecord);
        });
    }

    private void scheduleWarmup(Player player, AuthSnapshot admission, int delay, boolean forceCancelOnMove, Runnable attempt) {
        if (!currentAdmission(player, admission)) return;
        UUID id = player.getUniqueId();
        cancelWarmup(id);
        if (delay <= 0) {
            attempt.run();
            return;
        }

        player.sendMessage(prefix() + config.messages().teleportWarmup()
            .replace("{seconds}", String.valueOf(delay)));

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> callbacks.call(() -> {
            warmups.remove(id);
            if (currentAdmission(player, admission) && !player.isDead()) attempt.run();
            return null;
        }, null), delay * 20L);
        warmups.put(id, new Warmup(task, forceCancelOnMove));
    }

    public void unstuck(Player player) {
        AuthSnapshot admission = AuthAccess.snapshot(player.getUniqueId());
        if (!currentAdmission(player, admission)) return;
        Location origin = player.getLocation();
        if (!canUnstuck(player, origin)) return;
        if (findUnstuckDestination(origin) == null) {
            player.sendMessage(prefix() + ChatColor.RED + "附近没有符合脱困条件的安全位置。");
            return;
        }
        scheduleWarmup(player, admission, config.features().unstuck().warmupSeconds(), true, () -> {
            Location current = player.getLocation();
            if (!canUnstuck(player, current)) return;
            Location destination = findUnstuckDestination(current);
            if (destination == null) {
                player.sendMessage(prefix() + ChatColor.RED + "附近没有符合脱困条件的安全位置。");
                return;
            }
            if (executeTeleport(player, destination, ChatColor.GREEN + "已传送到附近安全位置。", null)) {
                unstuckCooldowns.put(player.getUniqueId(), System.currentTimeMillis()
                    + config.features().unstuck().cooldownSeconds() * 1000L);
            }
        });
    }

    private boolean canUnstuck(Player player, Location origin) {
        var settings = config.features().unstuck();
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        if (!settings.enabled() || !player.hasPermission("axs.essentials.unstuck") || player.isInsideVehicle()
            || !settings.allowsWorld(origin.getWorld().getName())) {
            player.sendMessage(prefix() + ChatColor.RED + "当前无法使用脱困。");
            return false;
        }
        AbyssAdmissionAccess access = abyss.get();
        if (access != null && !"IDLE".equals(access.playerState(id).state())) {
            player.sendMessage(prefix() + ChatColor.RED + "副本流程中无法使用脱困。");
            return false;
        }
        long until = Math.max(unstuckCooldowns.getOrDefault(id, 0L),
            lastDamage.getOrDefault(id, 0L) + settings.combatSeconds() * 1000L);
        if (until > now) {
            player.sendMessage(prefix() + ChatColor.RED + "脱困还需等待 " + ((until - now + 999) / 1000) + " 秒。");
            return false;
        }
        if (settings.onlyWhenStuck() && safeStandingPosition(origin) && canWalkOut(origin)) {
            player.sendMessage(prefix() + ChatColor.YELLOW + "当前位置有可行走的出口。");
            return false;
        }
        return true;
    }

    private boolean canWalkOut(Location origin) {
        for (var face : List.of(org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH,
            org.bukkit.block.BlockFace.WEST, org.bukkit.block.BlockFace.EAST)) {
            for (int y = -1; y <= 1; y++) {
                if (safeLoadedPosition(origin.clone().add(face.getModX(), y, face.getModZ()))) return true;
            }
        }
        return false;
    }

    private Location findUnstuckDestination(Location origin) {
        var settings = config.features().unstuck();
        World world = origin.getWorld();
        RegionQueryable query = regions.get();
        List<RegionQueryable.RegionInfo> worldRegions = query == null ? List.of() : query.regionsInWorld(world.getName());
        var originRegions = regionsAt(worldRegions, origin);
        Location best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int x = -settings.radius(); x <= settings.radius(); x++) {
            for (int z = -settings.radius(); z <= settings.radius(); z++) {
                if (x * x + z * z > settings.radius() * settings.radius()) continue;
                for (int y = -settings.verticalRange(); y <= settings.verticalRange(); y++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    Location candidate = new Location(world, origin.getBlockX() + x + 0.5, origin.getBlockY() + y,
                        origin.getBlockZ() + z + 0.5, origin.getYaw(), origin.getPitch());
                    double distance = candidate.distanceSquared(origin);
                    if (distance < bestDistance && safeLoadedPosition(candidate) && originRegions.equals(regionsAt(worldRegions, candidate))) {
                        best = candidate;
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
    }

    private static java.util.Set<String> regionsAt(List<RegionQueryable.RegionInfo> regions, Location loc) {
        return regions.stream().filter(region -> region.contains(loc.getWorld().getName(), loc.getBlockX(),
            loc.getBlockY(), loc.getBlockZ())).map(RegionQueryable.RegionInfo::id).collect(java.util.stream.Collectors.toSet());
    }

    private static boolean safeLoadedPosition(Location loc) {
        World world = loc.getWorld();
        return world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)
            && world.getWorldBorder().isInside(loc) && safeStandingPosition(loc);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!running || !config.features().unstuck().enabled()) return;
        long now = System.currentTimeMillis();
        if (event.getEntity() instanceof Player player && event.getFinalDamage() > 0) {
            lastDamage.put(player.getUniqueId(), now);
        }
        if (event instanceof org.bukkit.event.entity.EntityDamageByEntityEvent attack && event.getFinalDamage() > 0) {
            var damager = attack.getDamager();
            if (damager instanceof Player player) lastDamage.put(player.getUniqueId(), now);
            else if (damager instanceof org.bukkit.entity.Projectile projectile && projectile.getShooter() instanceof Player player) {
                lastDamage.put(player.getUniqueId(), now);
            }
        }
    }

    private record Warmup(BukkitTask task, boolean forceCancelOnMove) {}

    public boolean teleportNow(Player player, Location target) {
        cancelWarmup(player.getUniqueId());
        return executeTeleport(player, target, null, null);
    }

    private boolean executeTeleport(Player player, Location target, String message, Location backRecord) {
        Location safe = validTarget(target) ? (config.teleport().safeTeleport() ? findSafeLocation(target) : target) : null;
        if (safe == null) {
            player.sendMessage(prefix() + ChatColor.RED + "目标附近没有安全的传送位置。");
            return false;
        }
        Location origin = player.getLocation();
        if (!player.teleport(safe)) {
            player.sendMessage(prefix() + config.messages().teleportCancelled());
            return false;
        }
        if (backRecord == null) playerService.pushBackLocation(player, origin);
        else playerService.consumeBackLocation(player, backRecord);
        if (message != null && !message.isBlank()) {
            player.sendMessage(prefix() + message);
        }
        return true;
    }

    private Location findSafeLocation(Location loc) {
        World world = loc.getWorld();
        if (!world.getWorldBorder().isInside(loc)) return null;
        if (safeStandingPosition(loc)) return loc;
        for (int offset = 1; offset <= 8; offset++) {
            Location above = loc.clone().add(0, offset, 0);
            if (safeStandingPosition(above)) return above;
            Location below = loc.clone().subtract(0, offset, 0);
            if (safeStandingPosition(below)) return below;
        }
        return null;
    }

    private static boolean validTarget(Location loc) {
        return loc.isWorldLoaded() && loc.getWorld() != null
            && Double.isFinite(loc.getX()) && Double.isFinite(loc.getY()) && Double.isFinite(loc.getZ())
            && Math.abs(loc.getX()) < 30_000_000 && Math.abs(loc.getY()) < 30_000_000
            && Math.abs(loc.getZ()) < 30_000_000;
    }

    private static boolean safeStandingPosition(Location loc) {
        World world = loc.getWorld();
        int y = loc.getBlockY();
        if (y <= world.getMinHeight() || y >= world.getMaxHeight() - 1) return false;
        Block feet = loc.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        Block support = feet.getRelative(0, -1, 0);
        return feet.isPassable() && head.isPassable() && !feet.isLiquid() && !head.isLiquid()
            && support.getType().isSolid() && !hazardous(feet) && !hazardous(head) && !hazardous(support);
    }

    private static boolean hazardous(Block block) {
        return switch (block.getType()) {
            case LAVA, FIRE, SOUL_FIRE, CACTUS, MAGMA_BLOCK, CAMPFIRE, SOUL_CAMPFIRE, POWDER_SNOW, SWEET_BERRY_BUSH,
                WITHER_ROSE, POINTED_DRIPSTONE -> true;
            default -> false;
        };
    }

    // ─── Events ───

    /**
     * 玩家死亡重生时，未安装 Multiverse-Core 且开启 {@code spawn.respawn-at-spawn}
     * 则强制重生于服务器出生点；床/重生锚按 {@code spawn.respect-personal-spawn} 豁免。
     *
     * @param event 重生事件
     */
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if(!running)return;
        if (multiverseActive() || !config.spawn().respawnAtSpawn()) return;
        if (config.spawn().respectPersonalSpawn()
            && (event.isBedSpawn() || event.isAnchorSpawn())) return;
        Location spawn = localSpawnBackend().snapshot();
        if (spawn != null) {
            event.setRespawnLocation(spawn);
        }
    }

    /**
     * 新玩家首次进服时，未安装 Multiverse-Core 且开启
     * {@code spawn.first-join-at-spawn} 则延迟 1 tick 传送到出生点。
     *
     * @param event 进服事件
     */
    @EventHandler
    public void onFirstJoin(PlayerJoinEvent event) {
        if(!running)return;
        Player player = event.getPlayer();
        preloadHomeNames(player);
        if (multiverseActive() || !config.spawn().firstJoinAtSpawn()
            || player.hasPlayedBefore()) return;
        getSpawn(player.getWorld()).whenComplete((spawn, failure) -> work.onMain(() -> {
            if (failure == null && spawn != null && sameSession(player) && !multiverseActive()) player.teleport(spawn);
        }));
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Warmup warmup = warmups.get(uuid);
        if (warmup == null || (!warmup.forceCancelOnMove() && !config.teleport().cancelOnMove())) return;

        if (event.getTo() != null && !event.getFrom().getBlock().equals(event.getTo().getBlock())) {
            if (cancelWarmup(uuid)) player.sendMessage(prefix() + config.messages().teleportCancelled());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        cancelWarmup(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        cancelWarmup(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        homeNames.remove(uuid);
        tpaLookups.keySet().removeIf(key -> key.sender().equals(uuid) || key.target().equals(uuid));
        pendingTpa.clearIncoming(uuid);
        pendingTpa.cancelOutgoing(uuid, null, System.currentTimeMillis());
        cancelWarmup(uuid);
    }

    private boolean cancelWarmup(UUID uuid) {
        Warmup warmup = warmups.remove(uuid);
        if (warmup == null) return false;
        warmup.task().cancel();
        return true;
    }

    private boolean validName(Player player, String name) {
        if (!name.isBlank() && name.length() <= 64 && name.chars().noneMatch(Character::isISOControl)) return true;
        player.sendMessage(prefix() + ChatColor.RED + "名称须为 1 至 64 个字符，且不能包含控制字符。");
        return false;
    }

    private String prefix() {
        return ChatColor.GRAY + "[" + ChatColor.GREEN + "Essentials" + ChatColor.GRAY + "] " + ChatColor.RESET;
    }

    /**
     * TPA 请求记录。
     *
     * @param sender    请求发起者 UUID
     * @param here      是否为 tpahere（请求对方过来）
     * @param timestamp 请求创建时间戳（毫秒）
     */
}
