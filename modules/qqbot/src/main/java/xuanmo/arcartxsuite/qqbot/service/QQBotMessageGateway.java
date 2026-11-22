package xuanmo.arcartxsuite.qqbot.service;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.scheduler.ModuleExecutor;
import xuanmo.arcartxsuite.qqbot.config.QQBotConfiguration;
import xuanmo.arcartxsuite.qqbot.config.QQBotGroupConfig;
import xuanmo.arcartxsuite.qqbot.onebot.OneBotClient;
import xuanmo.arcartxsuite.qqbot.onebot.OneBotResult;
import xuanmo.arcartxsuite.qqbot.storage.QQBotRepository;

/** Single application boundary for outbound delivery, including player/session checks and audience policy. */
public final class QQBotMessageGateway implements AutoCloseable {
    private final JavaPlugin plugin;
    private final QQBotConfiguration config;
    private final QQBotRepository repository;
    private final OneBotClient client;
    private final SlidingWindowRateLimiter<UUID> players;
    private final SlidingWindowRateLimiter<UUID> mentions = new SlidingWindowRateLimiter<>(1, 10000L, 4096);
    private final ModuleExecutor worker = new ModuleExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(64), r -> {
            Thread thread = new Thread(r, "QQBot-Delivery"); thread.setDaemon(true); return thread;
        });
    private volatile boolean closed;
    private final CompletableFuture<Void> acknowledgementsStopped = new CompletableFuture<>();
    private final java.util.Set<CompletableFuture<OneBotResult>> outstanding = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public QQBotMessageGateway(JavaPlugin plugin, QQBotConfiguration config, QQBotRepository repository, OneBotClient client) {
        this.plugin = plugin; this.config = config; this.repository = repository; this.client = client;
        players = new SlidingWindowRateLimiter<>(1, config.delivery().playerCooldownMillis(), 4096);
    }

    public CompletableFuture<OneBotResult> sendPlayer(Player player, String text, Long mention) {
        return sendPlayer(player, text, mention, null);
    }

    public CompletableFuture<OneBotResult> sendPlayer(Player player, String text, Long mention, String channelId) {
        if (closed) return failure("QQBot 正在关闭");
        if (!Bukkit.isPrimaryThread()) {
            CompletableFuture<OneBotResult> result = new CompletableFuture<>();
            outstanding.add(result);
            result.whenComplete((value, error) -> outstanding.remove(result));
            if (closed) { result.complete(OneBotResult.failure(-1, "服务已停止，消息未发送")); return result; }
            try {
                Bukkit.getScheduler().runTask(plugin, () -> sendPlayer(player, text, mention, channelId)
                    .whenComplete((value, error) -> complete(result, value, error)));
            } catch (RuntimeException stopped) { result.complete(OneBotResult.failure(-1, "服务已停止，消息未发送")); }
            return result;
        }
        if (!player.isOnline() || !AuthAccess.admitted(player.getUniqueId())) return failure("请先完成账号验证");
        if (!player.hasPermission("arcartxsuite.qqbot.send")) return failure("没有向 QQ 群发送消息的权限");
        if (mention != null && (mention <= 0 || !player.hasPermission("arcartxsuite.qqbot.at"))) return failure("没有 @ 群成员的权限，或 QQ 号无效");
        String message = normalize(text);
        if (message == null) return failure("消息为空、过长或包含不支持的控制字符（上限 " + config.delivery().maxMessageLength() + " 字）");
        if (!client.isConnected()) return failure("机器人未连接，消息未发送");
        List<QQBotGroupConfig> targets = config.groups().stream()
            .filter(QQBotGroupConfig::syncGameToQq)
            .filter(group -> channelId == null || group.chatChannel().equalsIgnoreCase(channelId)).toList();
        if (targets.isEmpty()) return failure("没有允许接收游戏消息的 QQ 群");
        UUID id = player.getUniqueId();
        long now = System.nanoTime() / 1_000_000L;
        if (mention != null && !mentions.allow(id, now)) return failure("@ 操作过于频繁，请稍后再试");
        if (!players.allow(id, now)) return failure("发送过于频繁，请稍后再试");
        var session = AuthAccess.snapshot(id);
        String name = player.getName();
        String displayName = ChatColor.stripColor(player.getDisplayName());
        CompletableFuture<OneBotResult> result = new CompletableFuture<>();
        outstanding.add(result);
        result.whenComplete((value, error) -> outstanding.remove(result));
        try {
            worker.execute(() -> {
                if (closed) { result.complete(OneBotResult.failure(-1, "QQBot 已关闭，消息未发送")); return; }
                try {
                    if (config.delivery().requireBinding() && repository.findByPlayer(id) == null) {
                        result.complete(OneBotResult.failure(-1, "请先绑定 QQ 账号")); return;
                    }
                    var current = AuthAccess.snapshot(id);
                    if (closed || !current.admitted() || !java.util.Objects.equals(current.connectionId(), session.connectionId())
                            || current.generation() != session.generation()) {
                        result.complete(OneBotResult.failure(-1, "账号会话已变更，消息未发送")); return;
                    }
                    List<CompletableFuture<OneBotResult>> requests = new ArrayList<>();
                    for (QQBotGroupConfig group : targets) {
                        String formatted = group.gameToQqFormat().replace("{player}", name)
                            .replace("{display_name}", displayName == null ? name : displayName)
                            .replace("{message}", message).replace("{server}", config.serverId());
                        requests.add(mention == null ? client.sendGroupMessage(group.groupId(), formatted)
                            : client.sendGroupMessageAt(group.groupId(), mention, formatted));
                    }
                    aggregate(requests).whenComplete((value, error) -> complete(result, value, error));
                } catch (Exception error) { result.complete(OneBotResult.failure(-1, "绑定状态查询失败，消息未发送")); }
            });
        } catch (java.util.concurrent.RejectedExecutionException full) { result.complete(OneBotResult.failure(-1, "发送队列已满，请稍后重试")); }
        return result;
    }

    public CompletableFuture<OneBotResult> sendSystem(Long groupId, String message) {
        if (closed) return failure("QQBot 已关闭");
        if (message == null || message.isBlank() || message.length() > 4000) return failure("通知内容为空或过长");
        if (groupId != null && config.groups().stream().noneMatch(group -> group.groupId() == groupId))
            return failure("目标群未在 QQBot 配置中声明");
        if (groupId != null) return client.sendGroupMessage(groupId, message);
        return aggregate(config.groups().stream().map(group -> client.sendGroupMessage(group.groupId(), message)).toList());
    }

    public void clearPlayer(UUID player) { players.remove(player); mentions.remove(player); }
    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        worker.shutdown();
        players.clear();
        mentions.clear();
        CompletableFuture.runAsync(() -> {
            outstanding.forEach(future -> future.complete(OneBotResult.failure(-2, "模块关闭，未完成操作的结果未知，请核对后再操作")));
            outstanding.clear();
        }).whenComplete((ignored, failure) -> {
            if (failure == null) acknowledgementsStopped.complete(null);
            else acknowledgementsStopped.completeExceptionally(failure);
        });
    }

    CompletionStage<Void> stopped() { return CompletableFuture.allOf(worker.stopped().toCompletableFuture(), acknowledgementsStopped); }

    private String normalize(String message) {
        if (message == null) return null;
        String result = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', message)).strip();
        if (result.isBlank() || result.codePointCount(0, result.length()) > config.delivery().maxMessageLength()
                || result.codePoints().anyMatch(code -> Character.isISOControl(code) && code != '\n')) return null;
        return result;
    }
    private static CompletableFuture<OneBotResult> failure(String message) {
        return CompletableFuture.completedFuture(OneBotResult.failure(-1, message));
    }
    private static void complete(CompletableFuture<OneBotResult> future, OneBotResult value, Throwable error) {
        future.complete(error == null ? value : OneBotResult.failure(-2, "操作结果未知，请核对群消息"));
    }
    static CompletableFuture<OneBotResult> aggregate(List<CompletableFuture<OneBotResult>> requests) {
        if (requests.isEmpty()) return failure("未配置目标群");
        return CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new)).thenApply(ignored -> {
            long sent = requests.stream().filter(request -> request.join().success()).count();
            if (sent == requests.size()) {
                JsonObject data = new JsonObject(); data.addProperty("sentGroups", sent); data.addProperty("totalGroups", requests.size());
                return OneBotResult.success(data);
            }
            OneBotResult first = requests.stream().map(CompletableFuture::join).filter(result -> !result.success()).findFirst().orElseThrow();
            boolean unknown = requests.stream().map(CompletableFuture::join).anyMatch(result -> result.retcode() == -2);
            JsonObject data = new JsonObject(); data.addProperty("sentGroups", sent); data.addProperty("totalGroups", requests.size());
            return new OneBotResult(false, unknown ? -2 : first.retcode(), "已发送 " + sent + "/" + requests.size() + " 个群；"
                + (unknown ? "部分结果未知，请核对后再操作" : first.message()), data);
        });
    }
}
