package xuanmo.arcartxsuite.qqbot.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.qqbot.config.QQBotConfiguration;
import xuanmo.arcartxsuite.qqbot.storage.QQBotRepository;
import xuanmo.arcartxsuite.qqbot.storage.QQBotRepository.QQBotBinding;

/**
 * QQBot UI 通讯服务。
 * 管理绑定中心、通知 HUD 和管理后台的 server ↔ client 通讯。
 */
public final class QQBotUiService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm")
        .withZone(ZoneId.systemDefault());

    public static final String BIND_UI_RESOURCE = "arcartx/ui/qqbot_bind.yml";
    public static final String BIND_UI_FILE = "ui/qqbot_bind.yml";
    public static final String NOTIFY_UI_RESOURCE = "arcartx/ui/qqbot_notify.yml";
    public static final String NOTIFY_UI_FILE = "ui/qqbot_notify.yml";
    public static final String ADMIN_UI_RESOURCE = "arcartx/ui/qqbot_admin.yml";
    public static final String ADMIN_UI_FILE = "ui/qqbot_admin.yml";

    private static final String BIND_PACKET_ID = "AXS_qqbot";
    private static final String ADMIN_PACKET_ID = "AXS_qqbot_admin";
    private static final int PAGE_SIZE = 10;

    private final JavaPlugin plugin;
    private final QQBotConfiguration config;
    private final QQBotRepository repository;
    private final QQBotBindService bindService;
    private final PacketBridgeAPI packetBridge;
    private final Logger logger;

    private String bindUiId;
    private String notifyUiId;
    private String adminUiId;

    // 群消息缓存（最近 50 条）
    private final Deque<Map<String, Object>> recentMessages = new LinkedList<>();
    private static final int MAX_MSG_CACHE = 50;

    public QQBotUiService(
        JavaPlugin plugin,
        QQBotConfiguration config,
        QQBotRepository repository,
        QQBotBindService bindService,
        PacketBridgeAPI packetBridge,
        Logger logger
    ) {
        this.plugin = plugin;
        this.config = config;
        this.repository = repository;
        this.bindService = bindService;
        this.packetBridge = packetBridge;
        this.logger = logger;
    }

    public void setBindUiId(String uiId) {
        this.bindUiId = uiId;
    }

    public void setNotifyUiId(String uiId) {
        this.notifyUiId = uiId;
    }

    public void setAdminUiId(String uiId) {
        this.adminUiId = uiId;
    }

    // ─── 绑定中心 ──────────────────────────────────────

    /**
     * 为玩家打开绑定中心 UI。
     */
    public void openBindCenter(Player player) {
        if (bindUiId == null) return;
        packetBridge.openUi(player, bindUiId);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            QQBotBinding binding = repository.findByPlayerName(player.getName());
            Map<String, Object> payload = new HashMap<>();
            payload.put("packetId", BIND_PACKET_ID);
            payload.put("bound", binding != null);
            payload.put("qqId", binding != null ? String.valueOf(binding.qqId()) : "");
            payload.put("playerName", player.getName());
            payload.put("bindTime", binding != null && binding.boundAt() > 0
                ? TIME_FMT.format(java.time.Instant.ofEpochMilli(binding.boundAt())) : "");
            payload.put("messages", getRecentMessagesPayload());
            payload.put("msgCount", recentMessages.size());
            packetBridge.sendPacket(player, bindUiId, "init", payload);
        }, 2L);
    }

    // ─── 管理后台 ──────────────────────────────────────

    /**
     * 为管理员打开管理后台 UI。
     */
    public void openAdminPanel(Player player) {
        if (adminUiId == null) return;
        packetBridge.openUi(player, adminUiId);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            Map<String, Object> payload = new HashMap<>();
            payload.put("packetId", ADMIN_PACKET_ID);
            payload.put("botConnected", isServiceConnected());
            payload.put("totalBindings", repository.countBindings());
            payload.put("onlinePlayers", Bukkit.getOnlinePlayers().size());
            payload.put("maxPlayers", Bukkit.getMaxPlayers());
            payload.put("groups", buildGroupList());
            payload.put("bindings", buildBindingsPage(0));
            payload.put("bindTotal", repository.countBindings());
            packetBridge.sendPacket(player, adminUiId, "init", payload);
        }, 2L);
    }

    // ─── 通知 HUD ──────────────────────────────────────

    /**
     * 向指定玩家发送 QQ 消息通知。
     */
    public void sendNotification(Player player, String nick, String message) {
        if (notifyUiId == null) return;
        Map<String, Object> payload = new HashMap<>();
        payload.put("nick", nick);
        payload.put("msg", truncate(message, 60));
        payload.put("time", TIME_FMT.format(Instant.now()));
        packetBridge.sendPacket(player, notifyUiId, "notify", payload);
    }

    /**
     * 广播通知到所有在线玩家（@全体 / 重要消息）。
     */
    public void broadcastNotification(String nick, String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendNotification(player, nick, message);
        }
    }

    // ─── 缓存群消息 ──────────────────────────────────────

    /**
     * 缓存一条群消息（供绑定中心消息页使用）。
     */
    public void cacheGroupMessage(String nick, String message, long groupId) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("nick", nick);
        msg.put("msg", truncate(message, 100));
        msg.put("time", TIME_FMT.format(Instant.now()));
        msg.put("group", groupId);
        synchronized (recentMessages) {
            recentMessages.addLast(msg);
            while (recentMessages.size() > MAX_MSG_CACHE) {
                recentMessages.pollFirst();
            }
        }
    }

    // ─── Client Packet 处理 ──────────────────────────────

    /**
     * 处理来自绑定中心 UI 的客户端包。
     */
    public boolean handleBindPacket(Player player, String action, List<String> data) {
        switch (action) {
            case "request_code" -> handleRequestCode(player);
            case "unbind" -> handleUnbind(player);
            case "fetch_messages" -> sendMessages(player);
            case "send_message" -> handleSendMessage(player, data);
            default -> {
                return false;
            }
        }
        return true;
    }

    /**
     * 处理来自管理后台 UI 的客户端包。
     */
    public boolean handleAdminPacket(Player player, String action, List<String> data) {
        if (!player.hasPermission("arcartxsuite.qqbot.admin")) {
            return true; // 无权限忽略
        }
        switch (action) {
            case "refresh" -> refreshAdmin(player);
            case "fetch_bindings" -> {
                int page = data.isEmpty() ? 0 : parseIntSafe(data.get(0), 0);
                sendBindingsPage(player, page);
            }
            case "search_binding" -> {
                String keyword = data.isEmpty() ? "" : data.get(0);
                handleSearchBinding(player, keyword);
            }
            case "admin_unbind" -> {
                String name = data.isEmpty() ? "" : data.get(0);
                handleAdminUnbind(player, name);
            }
            case "exec_command" -> {
                String cmd = data.isEmpty() ? "" : data.get(0);
                handleExecCommand(player, cmd);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    // ─── 内部处理方法 ──────────────────────────────────

    private void handleRequestCode(Player player) {
        // 生成验证码，向 QQ 群发送或直接缓存
        QQBotBindService.BindResult result = bindService.requestBindFromGame(player.getName());
        Map<String, Object> payload = new HashMap<>();
        if (result.success()) {
            payload.put("code", result.message());
            payload.put("expire", config.binding().codeExpireSeconds());
            packetBridge.sendPacket(player, bindUiId, "code", payload);
        } else {
            sendBindResult(player, result.message(), "error");
        }
    }

    private void handleUnbind(Player player) {
        QQBotBindService.BindResult result = bindService.unbindByPlayer(player.getName());
        if (result.success()) {
            Map<String, Object> update = new HashMap<>();
            update.put("bound", false);
            update.put("qqId", "");
            update.put("bindTime", "");
            update.put("message", "解绑成功");
            update.put("type", "success");
            packetBridge.sendPacket(player, bindUiId, "update", update);
        } else {
            sendBindResult(player, result.message(), "error");
        }
    }

    private void sendMessages(Player player) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("messages", getRecentMessagesPayload());
        payload.put("msgCount", recentMessages.size());
        packetBridge.sendPacket(player, bindUiId, "messages", payload);
    }

    private void handleSendMessage(Player player, List<String> data) {
        if (data.isEmpty()) return;
        String message = data.get(0);
        if (message == null || message.isBlank()) return;

        // 通过 QQBot 发送到所有群
        QQBotBinding binding = repository.findByPlayerName(player.getName());
        String displayName = binding != null ? player.getName() + "(" + binding.qqId() + ")" : player.getName();
        String formatted = "[游戏] " + displayName + ": " + message;

        // 需要通过 QQBotService 发送，这里仅做回调
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (sendToGroupCallback != null) {
                sendToGroupCallback.accept(formatted);
            }
        });
        sendBindResult(player, "消息已发送", "success");
    }

    private void refreshAdmin(Player player) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("botConnected", isServiceConnected());
        payload.put("totalBindings", repository.countBindings());
        payload.put("onlinePlayers", Bukkit.getOnlinePlayers().size());
        payload.put("maxPlayers", Bukkit.getMaxPlayers());
        packetBridge.sendPacket(player, adminUiId, "update", payload);
    }

    private void sendBindingsPage(Player player, int page) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("bindings", buildBindingsPage(page));
        payload.put("page", page);
        payload.put("total", repository.countBindings());
        packetBridge.sendPacket(player, adminUiId, "bindings", payload);
    }

    private void handleSearchBinding(Player player, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            sendBindingsPage(player, 0);
            return;
        }
        List<QQBotBinding> results = repository.searchBindings(keyword, PAGE_SIZE);
        List<Map<String, Object>> list = new ArrayList<>();
        for (QQBotBinding b : results) {
            Map<String, Object> item = new HashMap<>();
            item.put("name", b.playerName());
            item.put("qq", String.valueOf(b.qqId()));
            item.put("time", b.boundAt() > 0 ? TIME_FMT.format(java.time.Instant.ofEpochMilli(b.boundAt())) : "");
            list.add(item);
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("bindings", list);
        payload.put("page", 0);
        payload.put("total", results.size());
        packetBridge.sendPacket(player, adminUiId, "bindings", payload);
    }

    private void handleAdminUnbind(Player player, String playerName) {
        if (playerName == null || playerName.isBlank()) return;
        QQBotBindService.BindResult result = bindService.unbindByPlayer(playerName);
        refreshAdmin(player);
        sendBindingsPage(player, 0);
    }

    private void handleExecCommand(Player player, String command) {
        if (command == null || command.isBlank()) return;
        if (config.debug()) {
            logger.info("[QQBot/Admin UI] " + player.getName() + " 执行命令: " + command);
        }
        String cmd = command;
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                Map<String, Object> payload = new HashMap<>();
                payload.put("cmd", cmd);
                payload.put("result", "执行成功");
                payload.put("time", TIME_FMT.format(Instant.now()));
                packetBridge.sendPacket(player, adminUiId, "cmd_result", payload);
            } catch (Exception e) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("cmd", cmd);
                payload.put("result", "执行失败: " + e.getMessage());
                payload.put("time", TIME_FMT.format(Instant.now()));
                packetBridge.sendPacket(player, adminUiId, "cmd_result", payload);
            }
        });
    }

    // ─── 工具方法 ──────────────────────────────────────

    private void sendBindResult(Player player, String message, String type) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("message", message);
        payload.put("type", type);
        packetBridge.sendPacket(player, bindUiId, "result", payload);
    }

    private List<Map<String, Object>> getRecentMessagesPayload() {
        synchronized (recentMessages) {
            return new ArrayList<>(recentMessages);
        }
    }

    private List<Map<String, Object>> buildBindingsPage(int page) {
        List<QQBotBinding> bindings = repository.getBindingsPage(page, PAGE_SIZE);
        List<Map<String, Object>> list = new ArrayList<>();
        for (QQBotBinding b : bindings) {
            Map<String, Object> item = new HashMap<>();
            item.put("name", b.playerName());
            item.put("qq", String.valueOf(b.qqId()));
            item.put("time", b.boundAt() > 0 ? TIME_FMT.format(java.time.Instant.ofEpochMilli(b.boundAt())) : "");
            list.add(item);
        }
        return list;
    }

    private List<Map<String, Object>> buildGroupList() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (var group : config.groups()) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", String.valueOf(group.groupId()));
            list.add(item);
        }
        return list;
    }

    private boolean isServiceConnected() {
        return serviceConnectedSupplier != null && serviceConnectedSupplier.get();
    }

    private static String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() > max ? text.substring(0, max) + "..." : text;
    }

    private static int parseIntSafe(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return def;
        }
    }

    // ─── 外部回调注入 ──────────────────────────────────

    private java.util.function.Consumer<String> sendToGroupCallback;
    private java.util.function.Supplier<Boolean> serviceConnectedSupplier;

    public void setSendToGroupCallback(java.util.function.Consumer<String> callback) {
        this.sendToGroupCallback = callback;
    }

    public void setServiceConnectedSupplier(java.util.function.Supplier<Boolean> supplier) {
        this.serviceConnectedSupplier = supplier;
    }
}
