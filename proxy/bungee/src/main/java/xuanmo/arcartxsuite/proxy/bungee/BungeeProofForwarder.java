package xuanmo.arcartxsuite.proxy.bungee;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;
import xuanmo.arcartxsuite.auth.minecraft.ProxyProofs;
import xuanmo.arcartxsuite.proxy.common.auth.ProxyProofForwarding;

/** BungeeCord event adapter. Receipt lookup is bounded and never runs on the connection event loop. */
public final class BungeeProofForwarder implements Listener, AutoCloseable {
    private static final class Session {
        final PendingConnection connection;
        final AtomicLong lastRequest = new AtomicLong(System.nanoTime() - 500_000_000L);
        volatile ProxyProofs.Identity identity;

        Session(PendingConnection connection) { this.connection = connection; }
    }

    private final Plugin plugin;
    private final ProxyProofForwarding forwarding;
    private final java.util.function.LongSupplier nanoClock;
    private final Map<PendingConnection, Session> sessions = new ConcurrentHashMap<>();
    private final Set<LoginWork> pending = ConcurrentHashMap.newKeySet();
    private final ExecutorService worker = new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(2), task -> {
            Thread thread = new Thread(task, "AXS-BungeeAuth");
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
    private final ScheduledExecutorService deadlines=Executors.newSingleThreadScheduledExecutor(task->{
        Thread thread=new Thread(task,"AXS-BungeeAuthDeadline"); thread.setDaemon(true); return thread;
    });
    private volatile boolean active = true;

    BungeeProofForwarder(Plugin plugin, ProxyProofForwarding forwarding) {
        this(plugin,forwarding,System::nanoTime);
    }
    BungeeProofForwarder(Plugin plugin, ProxyProofForwarding forwarding,java.util.function.LongSupplier nanoClock) {
        this.plugin = plugin;
        this.forwarding = forwarding;
        this.nanoClock=nanoClock;
        if (forwarding.enabled()) {
            plugin.getProxy().registerChannel(ProxyProofForwarding.CHANNEL);
            try {
                plugin.getProxy().getPluginManager().registerListener(plugin, this);
            } catch (RuntimeException e) {
                plugin.getProxy().unregisterChannel(ProxyProofForwarding.CHANNEL);
                worker.shutdownNow();
                deadlines.shutdownNow();
                throw e;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void login(LoginEvent event) {
        if (!active || !forwarding.enabled() || event.isCancelled() || !event.getConnection().isOnlineMode()) return;
        PendingConnection connection = event.getConnection();
        if (connection.getUniqueId() == null) {
            reject(event, "无法关联当前登录连接，请重新连接。");
            return;
        }
        sessions.keySet().removeIf(existing -> !existing.isConnected());
        var session = new Session(connection);
        sessions.put(connection, session);
        event.registerIntent(plugin);
        var work = new LoginWork(event, session);
        pending.add(work);
        try {
            work.armDeadline();
            worker.execute(work);
        } catch (RejectedExecutionException e) {
            work.finish("登录验证繁忙，请稍后重新连接。");
        }
    }

    private final class LoginWork implements Runnable {
        private final LoginEvent event;
        private final Session session;
        private final AtomicBoolean completed = new AtomicBoolean();
        private final long queuedAt=nanoClock.getAsLong();
        private volatile ScheduledFuture<?> deadline;

        LoginWork(LoginEvent event, Session session) {
            this.event = event;
            this.session = session;
        }
        void armDeadline() {
            var timer=deadlines.schedule(()->finish("登录验证已超时，请重新连接。"),8,TimeUnit.SECONDS);
            deadline=timer;
            if(completed.get()) timer.cancel(false);
        }

        @Override public void run() {
            String failure = null;
            try {
                if (!active || completed.get()) return;
                if(nanoClock.getAsLong()-queuedAt>=3_000_000_000L) throw new TimeoutException("Receipt lookup queue deadline exceeded");
                var connection = session.connection;
                var identity = forwarding.claim(connection.getUniqueId(), connection.getName());
                if (active && !completed.get()) session.identity = identity;
            } catch (Exception e) {
                failure = "无法取得本次登录证明，请重新连接。";
                plugin.getLogger().warning("[AuthForwarding] 获取登录证明失败: " + e.getClass().getSimpleName());
            } finally {
                if (!active) failure = "登录验证已关闭，请重新连接。";
                finish(failure);
            }
        }

        void finish(String failure) {
            if (!completed.compareAndSet(false, true)) return;
            var timer=deadline;
            if(timer!=null) timer.cancel(false);
            try {
                if (failure != null) reject(event, failure);
                if (failure != null || event.isCancelled()) sessions.remove(session.connection, session);
                event.completeIntent(plugin);
            } finally {
                pending.remove(this);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void message(PluginMessageEvent event) {
        if (!ProxyProofForwarding.CHANNEL.equals(event.getTag())) return;
        boolean cancelled = event.isCancelled();
        event.setCancelled(true);
        if (cancelled || !active || !forwarding.enabled() || event.getData().length > ProxyProofForwarding.MAX_FRAME_BYTES
                || !(event.getSender() instanceof Server backend) || !(event.getReceiver() instanceof ProxiedPlayer player)
                || player.getServer() != backend) return;
        PendingConnection connection = player.getPendingConnection();
        Session session = sessions.get(connection);
        if (session == null || session.connection != connection || session.identity == null) return;
        long now = System.nanoTime();
        if (now - session.lastRequest.getAndSet(now) < 500_000_000L) return;
        try {
            byte[] response = forwarding.response(backend.getInfo().getName(), player.getUniqueId(), player.getName(),
                session.identity, event.getData());
            if (response != null && active && player.getServer() == backend) backend.sendData(ProxyProofForwarding.CHANNEL, response);
        } catch (Exception ignored) {
            // Invalid or stale challenges cannot create a proof.
        }
    }

    @EventHandler
    public void disconnect(PlayerDisconnectEvent event) {
        sessions.remove(event.getPlayer().getPendingConnection());
    }

    private static void reject(LoginEvent event, String reason) {
        event.setCancelled(true);
        event.setCancelReason(new TextComponent(reason));
    }

    @Override public void close() {
        if (!active) return;
        active = false;
        if (forwarding.enabled()) {
            plugin.getProxy().getPluginManager().unregisterListener(this);
            plugin.getProxy().unregisterChannel(ProxyProofForwarding.CHANNEL);
        }
        worker.shutdownNow(); deadlines.shutdownNow();
        for (LoginWork work : pending) work.finish("登录验证已关闭，请重新连接。");
        sessions.clear();
    }
}
