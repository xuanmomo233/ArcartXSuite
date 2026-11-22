package xuanmo.arcartxsuite.fishing.service;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.scheduler.ModuleExecutor;

/** Limits UI reads and keeps payload callbacks confined to the current player and view. */
final class FishingUiQueries {
    private static final int MAX_PENDING = 64;
    private final JavaPlugin plugin;
    private final PacketBridgeAPI bridge;
    private final Logger logger;
    private final Semaphore slots = new Semaphore(MAX_PENDING);
    private final ConcurrentHashMap<Key, Request> pendingRequests = new ConcurrentHashMap<>();
    private final ModuleExecutor worker = new ModuleExecutor(2, 2, 30, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(MAX_PENDING), work -> {
            var thread = new Thread(work, "AXS-Fishing-UI");
            thread.setDaemon(true);
            return thread;
        });
    private volatile boolean closed;

    FishingUiQueries(JavaPlugin plugin, PacketBridgeAPI bridge, Logger logger) {
        this.plugin = plugin;
        this.bridge = bridge;
        this.logger = logger;
        worker.allowCoreThreadTimeOut(true);
    }

    boolean canView(Player player, String uiId) {
        return !closed && bridge != null && bridge.isAvailable() && player.isOnline()
            && Bukkit.getPlayer(player.getUniqueId()) == player && bridge.isUiOpen(player, uiId);
    }

    <T> void read(Player player, String uiId, Function<UUID, T> load, Consumer<T> render) {
        if (!canView(player, uiId)) return;
        UUID uuid = player.getUniqueId();
        var auth = AuthAccess.snapshot(uuid);
        if (!auth.admitted()) return;
        Key key = new Key(uuid, uiId);
        if (pendingRequests.containsKey(key)) return;
        if (!slots.tryAcquire()) {
            player.sendMessage("§e钓鱼数据读取繁忙，请稍后刷新。");
            return;
        }
        // Each request needs its own identity even when it uses the same admission snapshot.
        Request request = new Request(key, auth);
        pendingRequests.put(key, request);
        try {
            worker.execute(() -> loadAndRender(request, player, load, render));
        } catch (RejectedExecutionException ignored) {
            finish(request);
        }
    }

    private <T> void loadAndRender(Request request, Player player, Function<UUID, T> load, Consumer<T> render) {
        boolean scheduled = false;
        try {
            if (!current(request)) return;
            T data = load.apply(request.key.uuid);
            if (!current(request)) return;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                try {
                    if (current(request) && canView(player, request.key.uiId) && stillAdmitted(request)) {
                        render.accept(data);
                    }
                } finally {
                    finish(request);
                }
            });
            scheduled = true;
        } catch (RuntimeException error) {
            if (!closed) logger.log(Level.WARNING, "Fishing UI 查询失败", error);
        } finally {
            if (!scheduled) finish(request);
        }
    }

    private boolean current(Request request) {
        return !closed && pendingRequests.get(request.key) == request;
    }

    private boolean stillAdmitted(Request request) {
        var auth = AuthAccess.snapshot(request.key.uuid);
        return auth.admitted() && auth.generation() == request.auth.generation()
            && Objects.equals(auth.connectionId(), request.auth.connectionId());
    }

    private void finish(Request request) {
        pendingRequests.remove(request.key, request);
        slots.release();
    }

    void invalidate(UUID uuid, String uiId) {
        pendingRequests.remove(new Key(uuid, uiId));
    }

    void forget(UUID uuid) {
        pendingRequests.keySet().removeIf(key -> key.uuid.equals(uuid));
    }

    void shutdown() {
        closed = true;
        pendingRequests.clear();
        worker.shutdownNow();
    }

    CompletionStage<Void> stopped() {
        return worker.stopped();
    }

    private record Key(UUID uuid, String uiId) {}

    private static final class Request {
        final Key key;
        final AuthSnapshot auth;

        Request(Key key, AuthSnapshot auth) {
            this.key = key;
            this.auth = auth;
        }
    }
}
