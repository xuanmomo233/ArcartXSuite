package xuanmo.arcartxsuite.qqbot.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.api.AxsCapabilities;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.capability.AuthRuntimeCapability;
import xuanmo.arcartxsuite.api.security.ActionTokenGuard;
import xuanmo.arcartxsuite.qqbot.config.QQBotConfiguration;
import xuanmo.arcartxsuite.qqbot.onebot.OneBotResult;
import xuanmo.arcartxsuite.qqbot.storage.QQBotRepository;
import xuanmo.arcartxsuite.qqbot.storage.QQBotRepository.QQBotBinding;

/** Session-bound UI actions. Repository work runs off the server thread. */
public final class QQBotUiService {
    public static final String BIND_UI_RESOURCE = "arcartx/ui/qqbot_bind.yml";
    public static final String BIND_UI_FILE = "ui/qqbot_v2/qqbot_bind.yml";
    public static final String NOTIFY_UI_RESOURCE = "arcartx/ui/qqbot_notify.yml";
    public static final String NOTIFY_UI_FILE = "ui/qqbot_v2/qqbot_notify.yml";
    public static final String ADMIN_UI_RESOURCE = "arcartx/ui/qqbot_admin.yml";
    public static final String ADMIN_UI_FILE = "ui/qqbot_v2/qqbot_admin.yml";
    private static final String BIND_PACKET_ID = "AXS_qqbot";
    private static final String ADMIN_PACKET_ID = "AXS_qqbot_admin";
    private static final int PAGE_SIZE = 10;
    private static final int MAX_MSG_CACHE = 50;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm")
        .withZone(ZoneId.systemDefault());

    private final JavaPlugin plugin;
    private final QQBotConfiguration config;
    private final QQBotRepository repository;
    private final QQBotBindService bindService;
    private final PacketBridgeAPI packetBridge;
    private final Logger logger;
    private final ActionTokenGuard tokens = new ActionTokenGuard();
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Set<BukkitTask> tasks = ConcurrentHashMap.newKeySet();
    private final Set<Player> readyClients = ConcurrentHashMap.newKeySet();
    private final Deque<Map<String, Object>> recentMessages = new ArrayDeque<>();
    private volatile boolean closed;
    private String bindUiId, notifyUiId, adminUiId;
    private BiFunction<Player, String, CompletableFuture<OneBotResult>> sendPlayerMessageCallback;
    private Supplier<Boolean> serviceConnectedSupplier;

    private static final class Session {
        final Player player;
        final String uiId;
        final String token;
        boolean busy;
        String query = "";
        int page;
        Session(Player player, String uiId, String token) {
            this.player = player; this.uiId = uiId; this.token = token;
        }
    }

    public QQBotUiService(JavaPlugin plugin, QQBotConfiguration config, QQBotRepository repository,
                         QQBotBindService bindService, PacketBridgeAPI packetBridge, Logger logger) {
        this.plugin = plugin; this.config = config; this.repository = repository;
        this.bindService = bindService; this.packetBridge = packetBridge; this.logger = logger;
    }
    public void setBindUiId(String uiId) { bindUiId = uiId; }
    public void setNotifyUiId(String uiId) { notifyUiId = uiId; }
    public void setAdminUiId(String uiId) { adminUiId = uiId; }
    public void setSendPlayerMessageCallback(BiFunction<Player, String, CompletableFuture<OneBotResult>> callback) {
        sendPlayerMessageCallback = callback;
    }
    public void setServiceConnectedSupplier(Supplier<Boolean> supplier) { serviceConnectedSupplier = supplier; }

    public void openBindCenter(Player player) {
        if (!player.hasPermission("arcartxsuite.qqbot.use")) return;
        Session session = open(player, bindUiId);
        if (session != null) later(() -> loadBinding(session, "init", "", "info"), 2);
    }
    public void openAdminPanel(Player player) {
        if (!player.hasPermission("arcartxsuite.qqbot.admin")) return;
        Session session = open(player, adminUiId);
        if (session != null) later(() -> loadAdmin(session, "init", "", "info"), 2);
    }
    private Session open(Player player, String uiId) {
        if (closed || uiId == null || packetBridge == null) {
            player.sendMessage("§cQQBot 界面未就绪，请使用 /qqbot help 查看命令入口。");
            return null;
        }
        tokens.clear(player.getUniqueId());
        Session session = new Session(player, uiId, tokens.issue(player.getUniqueId(), uiId));
        session.busy = true;
        sessions.put(player.getUniqueId(), session);
        boolean opened = packetBridge.openUiWithCallback(player, uiId, () -> onMain(() -> {
            if (sessions.get(player.getUniqueId()) == session) clearSession(player.getUniqueId());
        }));
        if (!opened) {
            clearSession(player.getUniqueId());
            player.sendMessage("§c无法打开 QQBot 界面。请确认已安装 ArcartX 客户端，或使用 /qqbot help 查看命令。");
            return null;
        }
        return session;
    }

    public boolean handleBindPacket(Player player, String action, List<String> data) {
        if (!List.of("refresh", "fetch_messages", "confirm_bind", "unbind", "send_message").contains(action)) return false;
        Session session = begin(player, bindUiId, data, "arcartxsuite.qqbot.use");
        if (session == null) return true;
        switch (action) {
            case "refresh" -> loadBinding(session, "update", "", "info");
            case "fetch_messages" -> finish(session, "messages", messagePayload());
            case "confirm_bind" -> {
                String code = argument(data, 1).trim();
                if (!code.matches("[A-Za-z0-9-]{4,64}")) {
                    finishResult(session, "请输入在 QQ 群申请的有效验证码。", "error");
                    return true;
                }
                pending(session, "正在确认绑定…");
                try { bindingAction(session, bindService.confirmBind(player, code)); }
                catch (RuntimeException e) { fail(session, e); }
            }
            case "unbind" -> {
                pending(session, "正在解除绑定…");
                try { bindingAction(session, bindService.unbindByPlayer(player.getUniqueId())); }
                catch (RuntimeException e) { fail(session, e); }
            }
            case "send_message" -> sendPlayerMessage(session, argument(data, 1));
            default -> throw new IllegalStateException(action);
        }
        return true;
    }

    public boolean handleAdminPacket(Player player, String action, List<String> data) {
        if (!List.of("refresh", "fetch_bindings", "search_binding", "admin_unbind", "exec_command").contains(action)) return false;
        Session session = begin(player, adminUiId, data, "arcartxsuite.qqbot.admin");
        if (session == null) return true;
        switch (action) {
            case "refresh" -> loadAdmin(session, "update", "", "info");
            case "fetch_bindings" -> {
                session.page = parsePage(argument(data, 1));
                loadAdmin(session, "bindings", "", "info");
            }
            case "search_binding" -> {
                session.query = truncate(argument(data, 1).trim(), 32);
                session.page = 0;
                loadAdmin(session, "bindings", "", "info");
            }
            case "admin_unbind" -> adminUnbind(session, argument(data, 1).trim());
            case "exec_command" -> executeConsole(session, argument(data, 1));
            default -> throw new IllegalStateException(action);
        }
        return true;
    }

    private Session begin(Player player, String uiId, List<String> data, String permission) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null || !session.uiId.equals(uiId) || !valid(session)
            || !player.hasPermission(permission) || !tokens.verify(player.getUniqueId(), uiId, argument(data, 0))
            || session.busy) return null;
        session.busy = true;
        return session;
    }
    private boolean valid(Session session) {
        return !closed && sessions.get(session.player.getUniqueId()) == session && session.player.isOnline()
            && Bukkit.getPlayer(session.player.getUniqueId()) == session.player
            && session.player.hasPermission(session.uiId.equals(adminUiId) ? "arcartxsuite.qqbot.admin" : "arcartxsuite.qqbot.use")
            && packetBridge.isUiOpen(session.player, session.uiId);
    }
    private void bindingAction(Session session, CompletionStage<QQBotBindService.BindResult> action) {
        action.whenComplete((result, error) -> onMain(() -> {
            if (!valid(session)) return;
            if (error != null) { fail(session, error); return; }
            loadBinding(session, "update", bindingMessage(result.message()), result.success() ? "success" : "error");
        }));
    }
    private void adminUnbind(Session session, String value) {
        UUID profileId;
        try { profileId = UUID.fromString(value); }
        catch (IllegalArgumentException error) {
            finishResult(session, "请选择有效的玩家绑定。", "error"); return;
        }
        pending(session, "正在解除所选玩家的绑定…");
        submit(() -> repository.findByPlayer(profileId)).whenComplete((binding, error) -> onMain(() -> {
            if (!valid(session)) return;
            if (error != null) { fail(session, error); return; }
            if (binding == null) { finishResult(session, "该绑定已不存在，请刷新列表。", "error"); return; }
            try {
                bindService.unbindByPlayer(binding.playerUuid()).whenComplete((result, failure) -> onMain(() -> {
                    if (!valid(session)) return;
                    if (failure != null) { fail(session, failure); return; }
                    session.page = 0;
                    loadAdmin(session, "bindings", bindingMessage(result.message()), result.success() ? "success" : "error");
                }));
            } catch (RuntimeException e) { fail(session, e); }
        }));
    }
    private void sendPlayerMessage(Session session, String message) {
        if (message.isBlank()) { finishResult(session, "请输入消息内容。", "error"); return; }
        if (sendPlayerMessageCallback == null) { finishResult(session, "消息发送服务未就绪。", "error"); return; }
        pending(session, "发送中，正在等待 QQ 实现端确认…");
        try {
            sendPlayerMessageCallback.apply(session.player, message).whenComplete((result, error) -> onMain(() -> {
                if (!valid(session)) return;
                if (error != null) { fail(session, error); return; }
                finish(session, "result", Map.of("message", result.message().isBlank()
                    ? (result.success() ? "消息发送成功。" : "消息发送失败。") : result.message(),
                    "type", result.success() ? "success" : "error", "sent", result.success()));
            }));
        } catch (RuntimeException e) { fail(session, e); }
    }
    private void executeConsole(Session session, String input) {
        Player player = session.player;
        if (!canExecuteConsole(player)) {
            finishResult(session, "执行服务器命令需要独立的控制台执行权限。", "error"); return;
        }
        String command = input.trim();
        if (command.startsWith("/")) command = command.substring(1);
        if (command.isBlank() || command.length() > 256 || command.indexOf('\n') >= 0 || command.indexOf('\r') >= 0) {
            finishResult(session, "请输入不超过 256 个字符的单行命令。", "error"); return;
        }
        logger.info("[QQBot Console] actor=" + player.getUniqueId() + " command=" + command);
        try {
            boolean accepted = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            finishResult(session, accepted ? "命令已由服务器处理；业务结果请查看服务器日志。" : "服务器未接受该命令，请检查命令名和参数。",
                accepted ? "success" : "error");
        } catch (RuntimeException e) {
            logger.warning("QQBot 控制台命令执行异常: " + e.getMessage());
            finishResult(session, "执行失败，请查看服务器日志。", "error");
        }
    }
    private static boolean canExecuteConsole(Player player) {
        return player.hasPermission("arcartxsuite.admin") || player.hasPermission("arcartxsuite.qqbot.console");
    }

    private void loadBinding(Session session, String event, String message, String type) {
        UUID profileId = session.player.getUniqueId();
        submit(() -> repository.findByPlayer(profileId)).whenComplete((binding, error) -> onMain(() -> {
            if (!valid(session)) return;
            if (error != null) { fail(session, error); return; }
            Map<String, Object> payload = base(session);
            payload.put("bound", binding != null);
            payload.put("qqId", binding == null ? "" : Long.toString(binding.qqId()));
            payload.put("playerName", session.player.getName());
            payload.put("bindTime", binding == null ? "" : time(binding.boundAt()));
            payload.put("bindEnabled", config.binding().enabled());
            payload.put("bindGuide", "在 QQ 群发送 " + config.binding().bindPrefix() + " " + session.player.getName()
                + "，收到验证码后在下方确认。");
            payload.put("botConnected", isServiceConnected());
            payload.put("canSend", session.player.hasPermission("arcartxsuite.qqbot.send"));
            payload.putAll(messagePayload());
            payload.put("message", message); payload.put("type", type);
            finish(session, event, payload);
        }));
    }
    private void loadAdmin(Session session, String event, String message, String type) {
        String query = session.query;
        int requestedPage = session.page;
        submit(() -> {
            int total, page;
            List<QQBotBinding> rows;
            if (query.isEmpty()) {
                total = repository.countBindings();
                page = Math.min(requestedPage, Math.max(0, (total - 1) / PAGE_SIZE));
                rows = repository.getBindingsPage(page, PAGE_SIZE);
            } else {
                List<QQBotBinding> matches = repository.searchBindings(query, 1000);
                total = matches.size();
                page = Math.min(requestedPage, Math.max(0, (total - 1) / PAGE_SIZE));
                int from = Math.min(matches.size(), page * PAGE_SIZE);
                rows = matches.subList(from, Math.min(matches.size(), from + PAGE_SIZE));
            }
            Map<String, Object> result = new HashMap<>();
            List<Map<String, Object>> bindings = new ArrayList<>();
            for (QQBotBinding binding : rows) bindings.add(Map.of("name", binding.playerName(),
                "profile", binding.playerUuid().toString(), "qq", Long.toString(binding.qqId()), "time", time(binding.boundAt())));
            result.put("bindings", indexedRows(bindings));
            result.put("page", page); result.put("total", total);
            result.put("pages", Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE));
            result.put("totalBindings", repository.countBindings());
            return result;
        }).whenComplete((result, error) -> onMain(() -> {
            if (!valid(session)) return;
            if (error != null) { fail(session, error); return; }
            session.page = (Integer) result.get("page");
            Map<String, Object> payload = base(session);
            payload.putAll(result); payload.put("query", query);
            payload.put("botConnected", isServiceConnected());
            payload.put("onlinePlayers", Bukkit.getOnlinePlayers().size());
            payload.put("maxPlayers", Bukkit.getMaxPlayers()); payload.put("groupCount", config.groups().size());
            payload.put("canExecute", canExecuteConsole(session.player));
            payload.put("message", message); payload.put("type", type);
            finish(session, event, payload);
        }));
    }
    private Map<String, Object> base(Session session) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("packetId", session.uiId.equals(adminUiId) ? ADMIN_PACKET_ID : BIND_PACKET_ID);
        payload.put("action_token", session.token);
        return payload;
    }
    private Map<String, Object> messagePayload() {
        List<Map<String, Object>> rows = new ArrayList<>();
        int count;
        synchronized (recentMessages) {
            count = recentMessages.size();
            var it = recentMessages.descendingIterator();
            while (it.hasNext() && rows.size() < PAGE_SIZE) rows.add(it.next());
        }
        return Map.of("messages", indexedRows(rows), "msgCount", count);
    }
    static Map<String, Object> indexedRows(List<? extends Map<String, Object>> rows) {
        Map<String, Object> indexed = new LinkedHashMap<>();
        for (int i = 0; i < rows.size(); i++) indexed.put("row" + i, rows.get(i));
        return indexed;
    }
    static int parsePage(String value) {
        try {
            double number = Double.parseDouble(value);
            return Double.isFinite(number) && number >= 0 && number <= 1_000_000 && number == Math.rint(number) ? (int) number : 0;
        } catch (NumberFormatException e) { return 0; }
    }
    public static String bindingMessage(String message) {
        if (message == null || message.isBlank()) return "认证服务暂时不可用，请稍后重试。";
        return switch (message) {
            case "INVALID_BINDING_CODE", "INVALID_CODE", "CODE_EXPIRED" -> "验证码无效、已使用或已过期，请在 QQ 群重新申请。";
            case "INVALID_STATE", "IDENTITY_REQUIRED", "NOT_AUTHENTICATED" -> "请先完成游戏账号身份验证，再确认 QQ 绑定。";
            case "DISABLED", "AUTH_DISABLED", "UNAVAILABLE", "BINDING_DISABLED" -> "当前认证服务不支持此操作，请联系管理员。";
            default -> message.matches("[A-Z][A-Z0-9_]{2,}") ? "认证服务未能完成操作，请重新申请验证码或联系管理员。" : message;
        };
    }
    private void pending(Session session, String message) {
        if (valid(session)) packetBridge.sendPacket(session.player, session.uiId, "result",
            Map.of("busy", true, "message", message, "type", "info"));
    }
    private void finishResult(Session session, String message, String type) {
        finish(session, "result", Map.of("message", message, "type", type));
    }
    private void finish(Session session, String event, Map<String, Object> payload) {
        if (!valid(session)) return;
        session.busy = false;
        Map<String, Object> response = new HashMap<>(payload);
        response.put("busy", false);
        packetBridge.sendPacket(session.player, session.uiId, event, response);
    }
    private void fail(Session session, Throwable error) {
        logger.warning("QQBot UI 操作失败: " + error.getMessage());
        finishResult(session, "操作失败，请稍后重试；详情请联系管理员查看日志。", "error");
    }
    private <T> CompletableFuture<T> submit(Supplier<T> work) {
        CompletableFuture<T> future = new CompletableFuture<>();
        if (closed) { future.cancel(false); return future; }
        BukkitTask task = Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (closed) { future.cancel(false); return; }
            try { future.complete(work.get()); } catch (Throwable error) { future.completeExceptionally(error); }
        });
        tasks.add(task);
        future.whenComplete((value, error) -> tasks.remove(task));
        return future;
    }
    private void onMain(Runnable work) {
        if (closed) return;
        if (Bukkit.isPrimaryThread()) work.run(); else later(work, 0);
    }
    private void later(Runnable work, long ticks) {
        if (closed) return;
        final BukkitTask[] holder = new BukkitTask[1];
        holder[0] = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            tasks.remove(holder[0]);
            if (!closed) work.run();
        }, ticks);
        tasks.add(holder[0]);
    }

    public void sendNotification(Player player, String nick, String message) {
        if (!Bukkit.isPrimaryThread()) { onMain(() -> sendNotification(player, nick, message)); return; }
        if (closed || !config.ui().notifyEnabled() || notifyUiId == null || !player.isOnline()
            || Bukkit.getPlayer(player.getUniqueId()) != player || !readyClients.contains(player)) return;
        AuthRuntimeCapability auth = AxsCapabilities.get(AuthRuntimeCapability.class);
        if (auth == null || !auth.snapshot(player.getUniqueId()).admitted()) return;
        packetBridge.sendPacket(player, notifyUiId, "notify", Map.of("nick", truncate(nick, 24),
            "msg", truncate(message, 80), "time", TIME_FMT.format(Instant.now())));
    }
    public void broadcastNotification(String nick, String message) {
        onMain(() -> {
            if (config.ui().notifyEnabled()) for (Player player : Bukkit.getOnlinePlayers()) sendNotification(player, nick, message);
        });
    }
    public void cacheGroupMessage(String nick, String message, long groupId) {
        if (closed) return;
        Map<String, Object> entry = Map.of("nick", truncate(nick, 24), "msg", truncate(message, 160),
            "time", TIME_FMT.format(Instant.now()), "group", Long.toString(groupId));
        synchronized (recentMessages) {
            recentMessages.addLast(entry);
            while (recentMessages.size() > MAX_MSG_CACHE) recentMessages.removeFirst();
        }
    }
    public void markClientReady(Player player) { if (!closed) readyClients.add(player); }
    private void clearSession(UUID playerId) { sessions.remove(playerId); tokens.clear(playerId); }
    public void clearPlayerState(UUID playerId) {
        clearSession(playerId);
        readyClients.removeIf(player -> player.getUniqueId().equals(playerId));
    }
    public void clearAllTokens() { sessions.clear(); tokens.clearAll(); }
    public void shutdown() {
        closed = true;
        for (BukkitTask task : tasks) task.cancel();
        tasks.clear(); clearAllTokens(); readyClients.clear();
        synchronized (recentMessages) { recentMessages.clear(); }
        sendPlayerMessageCallback = null; serviceConnectedSupplier = null;
    }
    private boolean isServiceConnected() { return serviceConnectedSupplier != null && Boolean.TRUE.equals(serviceConnectedSupplier.get()); }
    private static String argument(List<String> data, int index) { return data != null && data.size() > index && data.get(index) != null ? data.get(index) : ""; }
    private static String time(long millis) { return millis > 0 ? TIME_FMT.format(Instant.ofEpochMilli(millis)) : ""; }
    private static String truncate(String text, int max) { return text == null ? "" : text.length() > max ? text.substring(0, max) + "…" : text; }
}
