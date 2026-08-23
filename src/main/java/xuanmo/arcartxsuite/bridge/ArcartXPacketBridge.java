package xuanmo.arcartxsuite.bridge;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import priv.seventeen.artist.arcartx.api.ArcartXAPI;
import priv.seventeen.artist.arcartx.core.entity.ArcartXEntityManager;
import priv.seventeen.artist.arcartx.core.entity.data.ArcartXPlayer;
import priv.seventeen.artist.arcartx.core.ui.ArcartXUIRegistry;
import priv.seventeen.artist.arcartx.core.ui.adapter.ArcartXUI;
import priv.seventeen.artist.arcartx.core.ui.adapter.CallBackType;
import priv.seventeen.artist.arcartx.util.collections.CallBack;
import priv.seventeen.artist.arcartx.util.collections.CallData;
import priv.seventeen.artist.arcartx.util.collections.UICallBack;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;

/**
 * {@link PacketBridgeAPI} 的默认实现，桥接 ArcartX 客户端 MOD 的 UI 注册、
 * 数据包收发与回调管理。
 * <p>
 * 维护 UI 适配器映射、关闭回调列表、玩家已打开 UI 集合，并在玩家退出时清理状态。
 */
public final class ArcartXPacketBridge implements PacketBridgeAPI {

    private final JavaPlugin plugin;

    private boolean available;
    private ArcartXUIRegistry uiRegistry;
    private ArcartXEntityManager entityManager;
    private final ConcurrentMap<String, ArcartXUI> uiAdapters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CopyOnWriteArrayList<Consumer<Player>>> closeCallbacks = new ConcurrentHashMap<>();
    private final ConcurrentMap<java.util.UUID, Set<String>> openUiIds = new ConcurrentHashMap<>();
    private final Set<String> closeCallbackRegisteredUiIds = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<String, List<UICallBack>> callbackKeepAlive = new ConcurrentHashMap<>();
    private int successfulUiRegistrationCount;
    private Listener lifecycleListener;

    public ArcartXPacketBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean initialize() {
        if (lifecycleListener != null) {
            xuanmo.arcartxsuite.util.ListenerUnregister.safeUnregisterAll(lifecycleListener, "ArcartXPacketBridge");
            lifecycleListener = null;
        }
        available = false;
        uiRegistry = null;
        entityManager = null;
        uiAdapters.clear();
        closeCallbacks.clear();
        openUiIds.clear();
        closeCallbackRegisteredUiIds.clear();
        callbackKeepAlive.clear();
        successfulUiRegistrationCount = 0;

        Plugin arcartX = plugin.getServer().getPluginManager().getPlugin("ArcartX");
        if (arcartX == null) {
            xuanmo.arcartxsuite.module.AxsLog.logger().severe("arcartxsuite 需要 ArcartX。");
            return false;
        }
        lifecycleListener = new Listener() {
            @EventHandler
            public void onPlayerQuit(PlayerQuitEvent event) {
                openUiIds.remove(event.getPlayer().getUniqueId());
            }
        };
        plugin.getServer().getPluginManager().registerEvents(lifecycleListener, plugin);

        try {
            uiRegistry = ArcartXAPI.getUIRegistry();
            if (uiRegistry == null) {
                throw new IllegalStateException("ArcartXUIRegistry 不可用。");
            }
            entityManager = ArcartXAPI.getEntityManager();
            if (entityManager == null) {
                throw new IllegalStateException("ArcartXEntityManager 不可用。");
            }
            available = true;
            return true;
        } catch (RuntimeException exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().severe("初始化 ArcartX 桥接失败: " + exception.getMessage());
            return false;
        }
    }

    public void shutdown() {
        available = false;
        uiRegistry = null;
        entityManager = null;
        uiAdapters.clear();
        closeCallbacks.clear();
        openUiIds.clear();
        closeCallbackRegisteredUiIds.clear();
        callbackKeepAlive.clear();
        if (lifecycleListener != null) {
            xuanmo.arcartxsuite.util.ListenerUnregister.safeUnregisterAll(lifecycleListener, "ArcartXPacketBridge");
            lifecycleListener = null;
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public String describePacketMode() {
        if (!available) {
            return "unavailable";
        }
        return "sendPacket(Object)";
    }

    public void resetUiRegistrationCount() {
        successfulUiRegistrationCount = 0;
    }

    public int successfulUiRegistrationCount() {
        return successfulUiRegistrationCount;
    }

    public PacketBridgeAPI.UiRegistrationResult registerOrReloadUi(String configuredUiId, File uiFile) {
        if (!available || uiRegistry == null) {
            return PacketBridgeAPI.UiRegistrationResult.failure(normalizeUiId(configuredUiId, uiFile), "ArcartX 桥接未就绪。");
        }

        Set<String> candidateUiIds = buildCandidateUiIds(configuredUiId, uiFile);
        String lastFailure = "未知错误";

        for (String candidateUiId : candidateUiIds) {
            try {
                RegistryCallResult reloadResult = tryReloadUi(candidateUiId, uiFile);
                if (reloadResult.success()) {
                    logRegisteredUi(configuredUiId, candidateUiId, uiFile, reloadResult.action());
                    return uiSuccess(candidateUiId, candidateUiId, reloadResult.action());
                }
                lastFailure = reloadResult.message();

                RegistryCallResult registerResult = tryRegisterUi(candidateUiId, uiFile);
                if (registerResult.success()) {
                    logRegisteredUi(configuredUiId, candidateUiId, uiFile, registerResult.action());
                    return uiSuccess(candidateUiId, candidateUiId, registerResult.action());
                }
                lastFailure = registerResult.message();
            } catch (RuntimeException exception) {
                lastFailure = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            }
        }

        xuanmo.arcartxsuite.module.AxsLog.logger().severe(
            "ArcartX UI 注册失败，配置 UI 标识为 '" + configuredUiId + "'，"
                + "导出文件为 " + uiFile.getAbsolutePath() + "，最后一次失败原因: " + lastFailure
        );
        return PacketBridgeAPI.UiRegistrationResult.failure(normalizeUiId(configuredUiId, uiFile), lastFailure);
    }

    public boolean unregisterUi(String uiId) {
        if (!available || uiRegistry == null || uiId == null || uiId.isBlank()) {
            return false;
        }
        try {
            uiRegistry.unregister(uiId);
            uiAdapters.remove(uiId);
            closeCallbacks.remove(uiId);
            openUiIds.values().forEach(openIds -> openIds.remove(uiId));
            closeCallbackRegisteredUiIds.remove(uiId);
            callbackKeepAlive.remove(uiId);
            return true;
        } catch (RuntimeException exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("ArcartX UI 注销失败(" + uiId + "): " + exception.getMessage());
            return false;
        }
    }

    public boolean openUi(Player player, String uiId) {
        if (!available || uiRegistry == null) return false;
        try {
            uiRegistry.open(player, uiId);
            markUiOpen(player, uiId);
            ensureCloseCallback(uiId);
            return true;
        } catch (RuntimeException exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("ArcartX 桥接调用失败: " + exception.getMessage());
            return false;
        }
    }

    public boolean openUiWithCallback(Player player, String uiId, Runnable callback) {
        if (!available || uiRegistry == null || callback == null) return false;
        try {
            AtomicBoolean called = new AtomicBoolean(false);
            CallBack cb = () -> {
                if (called.compareAndSet(false, true)) {
                    if (plugin.isEnabled()) {
                        plugin.getServer().getScheduler().runTask(plugin, callback);
                    }
                }
            };
            uiRegistry.open(player, uiId, cb);
            markUiOpen(player, uiId);
            ensureCloseCallback(uiId);
            return true;
        } catch (RuntimeException exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("ArcartX 打开 UI 回调桥接调用失败(open): " + exception.getMessage());
            return false;
        }
    }

    public boolean openUiUnsafe(Player player, String uiId) {
        if (!available || uiRegistry == null) return false;
        try {
            uiRegistry.openUnsafe(player, uiId);
            markUiOpen(player, uiId);
            ensureCloseCallback(uiId);
            return true;
        } catch (RuntimeException exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("ArcartX unsafe 桥接调用失败(openUnsafe): " + exception.getMessage());
            return false;
        }
    }

    public boolean openUiUnsafeWithCallback(Player player, String uiId, Runnable callback) {
        if (!available || uiRegistry == null || callback == null) return false;
        try {
            AtomicBoolean called = new AtomicBoolean(false);
            CallBack cb = () -> {
                if (called.compareAndSet(false, true)) {
                    if (plugin.isEnabled()) {
                        plugin.getServer().getScheduler().runTask(plugin, callback);
                    }
                }
            };
            uiRegistry.openUnsafe(player, uiId, cb);
            markUiOpen(player, uiId);
            ensureCloseCallback(uiId);
            return true;
        } catch (RuntimeException exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("ArcartX 打开 UI 回调桥接调用失败(openUnsafe): " + exception.getMessage());
            return false;
        }
    }

    public boolean closeUi(Player player, String uiId) {
        if (!available || uiRegistry == null) return false;
        try {
            uiRegistry.close(player, uiId);
            markUiClosed(player, uiId);
            return true;
        } catch (RuntimeException exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("ArcartX 桥接调用失败: " + exception.getMessage());
            return false;
        }
    }

    public boolean closeUiUnsafe(Player player, String uiId) {
        if (!available || uiRegistry == null) return false;
        try {
            uiRegistry.closeUnsafe(player, uiId);
            markUiClosed(player, uiId);
            return true;
        } catch (RuntimeException exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("ArcartX unsafe 桥接调用失败(closeUnsafe): " + exception.getMessage());
            return false;
        }
    }

    public boolean isUiOpen(Player player, String uiId) {
        if (player == null || uiId == null || uiId.isBlank()) {
            return false;
        }
        Set<String> openIds = openUiIds.get(player.getUniqueId());
        return openIds != null && openIds.contains(uiId);
    }

    public boolean sendPacket(Player player, String uiId, String handlerName, Object payload) {
        if (!available || uiRegistry == null) {
            return false;
        }
        try {
            uiRegistry.sendPacket(player, uiId, handlerName, payload);
            return true;
        } catch (RuntimeException exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("ArcartX 发包失败: " + exception.getMessage());
            return false;
        }
    }

    public boolean sendPacketToAll(Player player, List<String> uiIds, String handlerName, Object payload) {
        boolean anySuccess = false;
        for (String uiId : uiIds) {
            anySuccess |= sendPacket(player, uiId, handlerName, payload);
        }
        return anySuccess;
    }

    public boolean openUiAll(Player player, List<String> uiIds) {
        boolean anySuccess = false;
        for (String uiId : uiIds) {
            anySuccess |= openUi(player, uiId);
        }
        return anySuccess;
    }

    public boolean closeUiAll(Player player, List<String> uiIds) {
        boolean anySuccess = false;
        for (String uiId : uiIds) {
            anySuccess |= closeUi(player, uiId);
        }
        return anySuccess;
    }

    public boolean sendPacketUnsafe(Player player, String uiId, String handlerName, Object payload) {
        if (!available || uiRegistry == null) return false;
        try {
            uiRegistry.sendPacketUnsafe(player, uiId, handlerName, payload);
            return true;
        } catch (RuntimeException exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("ArcartX unsafe 桥接调用失败(sendPacketUnsafe): " + exception.getMessage());
            return false;
        }
    }

    public boolean registerUiCloseCallback(String uiId, Consumer<Player> callback) {
        if (!available || uiId == null || uiId.isBlank() || callback == null) {
            return false;
        }

        closeCallbacks.computeIfAbsent(uiId, ignored -> new CopyOnWriteArrayList<>()).addIfAbsent(callback);
        if (ensureCloseCallback(uiId)) {
            return true;
        }
        CopyOnWriteArrayList<Consumer<Player>> callbacks = closeCallbacks.get(uiId);
        if (callbacks != null) {
            callbacks.remove(callback);
            if (callbacks.isEmpty()) {
                closeCallbacks.remove(uiId, callbacks);
            }
        }
        return false;
    }

    private boolean ensureCloseCallback(String uiId) {
        if (!available || uiId == null || uiId.isBlank()) {
            return false;
        }
        if (closeCallbackRegisteredUiIds.contains(uiId)) {
            return true;
        }

        ArcartXUI adapter = uiAdapter(uiId);
        if (adapter == null) {
            return false;
        }

        UICallBack proxy = (callData) -> {
            if (callData == null) return;
            try {
                Player player = callData.player();
                if (player == null) return;
                markUiClosed(player, uiId);
                CopyOnWriteArrayList<Consumer<Player>> callbacks = closeCallbacks.get(uiId);
                if (callbacks != null && plugin.isEnabled()) {
                    List<Consumer<Player>> snapshot = List.copyOf(callbacks);
                    plugin.getServer().getScheduler().runTask(
                        plugin,
                        () -> snapshot.forEach(cb -> {
                            try {
                                cb.accept(player);
                            } catch (RuntimeException exception) {
                                xuanmo.arcartxsuite.module.AxsLog.logger().warning(
                                    "ArcartX UI 关闭回调执行失败(" + uiId + "): " + exception.getMessage()
                                );
                            }
                        })
                    );
                }
            } catch (RuntimeException exception) {
                xuanmo.arcartxsuite.module.AxsLog.logger().warning(
                    "ArcartX UI 关闭回调处理失败(" + uiId + "): " + exception.getMessage()
                );
            }
        };
        try {
            adapter.registerCallBack(CallBackType.CLOSE, proxy);
            callbackKeepAlive.computeIfAbsent(
                uiId,
                ignored -> Collections.synchronizedList(new java.util.ArrayList<>())
            ).add(proxy);
            closeCallbackRegisteredUiIds.add(uiId);
            return true;
        } catch (RuntimeException exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning(
                "注册 ArcartX UI CLOSE 回调失败(" + uiId + "): " + exception.getMessage()
            );
            return false;
        }
    }

    private void markUiOpen(Player player, String uiId) {
        if (player == null || uiId == null || uiId.isBlank()) {
            return;
        }
        openUiIds.computeIfAbsent(player.getUniqueId(), ignored -> ConcurrentHashMap.newKeySet()).add(uiId);
    }

    private void markUiClosed(Player player, String uiId) {
        if (player == null || uiId == null || uiId.isBlank()) {
            return;
        }
        Set<String> openIds = openUiIds.get(player.getUniqueId());
        if (openIds != null) {
            openIds.remove(uiId);
            if (openIds.isEmpty()) {
                openUiIds.remove(player.getUniqueId(), openIds);
            }
        }
    }

    public void unregisterUiCloseCallback(String uiId) {
        if (uiId == null || uiId.isBlank()) {
            return;
        }
        closeCallbacks.remove(uiId);
    }

    public boolean sendChatCard(Player player, String cardId, Map<String, String> data) {
        if (!available || cardId == null || cardId.isBlank() || entityManager == null || player == null) {
            return false;
        }
        try {
            ArcartXPlayer handler = entityManager.getPlayer(player);
            if (handler == null) {
                return false;
            }
            handler.sendChatCard(cardId, data);
            return true;
        } catch (RuntimeException exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("发送 ArcartX 聊天卡片 '" + cardId + "' 失败: " + exception.getMessage());
            return false;
        }
    }

    public static String normalizeUiId(String configuredUiId, File uiFile) {
        if (configuredUiId != null && !configuredUiId.isBlank()) {
            return configuredUiId.trim();
        }
        String fileName = uiFile.getName();
        int extensionIndex = fileName.lastIndexOf('.');
        return extensionIndex > 0 ? fileName.substring(0, extensionIndex) : fileName;
    }

    private RegistryCallResult tryReloadUi(String uiId, File uiFile) {
        ArcartXUI previousAdapter = uiAdapters.get(uiId);
        try {
            uiRegistry.reload(uiId, uiFile);
            ArcartXUI currentAdapter = refreshUiAdapter(uiId);
            if (currentAdapter != previousAdapter) {
                closeCallbackRegisteredUiIds.remove(uiId);
                callbackKeepAlive.remove(uiId);
            }
            return RegistryCallResult.success("reload", "reload 成功");
        } catch (RuntimeException exception) {
            return RegistryCallResult.failure("reload", exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage(), true);
        }
    }

    private RegistryCallResult tryRegisterUi(String uiId, File uiFile) {
        try {
            uiRegistry.unregister(uiId);
        } catch (RuntimeException ignored) {
        }
        try {
            uiRegistry.register(uiId, uiFile);
            closeCallbackRegisteredUiIds.remove(uiId);
            callbackKeepAlive.remove(uiId);
            refreshUiAdapter(uiId);
            return RegistryCallResult.success("register", "register 成功");
        } catch (RuntimeException exception) {
            return RegistryCallResult.failure("register", exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage(), true);
        }
    }

    private ArcartXUI uiAdapter(String uiId) {
        ArcartXUI adapter = uiAdapters.get(uiId);
        if (adapter != null) {
            return adapter;
        }
        return refreshUiAdapter(uiId);
    }

    private ArcartXUI refreshUiAdapter(String uiId) {
        if (!available || uiRegistry == null || uiId == null || uiId.isBlank()) {
            uiAdapters.remove(uiId);
            return null;
        }
        try {
            ArcartXUI adapter = uiRegistry.get(uiId);
            if (adapter == null) {
                uiAdapters.remove(uiId);
            } else {
                uiAdapters.put(uiId, adapter);
            }
            return adapter;
        } catch (RuntimeException exception) {
            uiAdapters.remove(uiId);
            return null;
        }
    }

    private void logRegisteredUi(String configuredUiId, String actualUiId, File uiFile, String action) {
        successfulUiRegistrationCount++;
        if (configuredUiId != null && !configuredUiId.isBlank() && !actualUiId.equals(configuredUiId)) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning(
                "ArcartX UI 标识 '" + configuredUiId + "' 不可用，已自动回退为 '" + actualUiId + "'。"
            );
        }
        xuanmo.arcartxsuite.module.AxsLog.logger().fine(
            "ArcartX UI 已" + ("reload".equals(action) ? "重载" : "注册")
                + ": " + actualUiId + " <- " + uiFile.getAbsolutePath()
        );
    }

    private static Set<String> buildCandidateUiIds(String configuredUiId, File uiFile) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        String normalized = normalizeUiId(configuredUiId, uiFile);
        candidates.add(normalized);

        int namespaceSeparator = normalized.lastIndexOf(':');
        if (namespaceSeparator >= 0 && namespaceSeparator + 1 < normalized.length()) {
            candidates.add(normalized.substring(namespaceSeparator + 1));
        }

        return candidates;
    }

    private static PacketBridgeAPI.UiRegistrationResult uiSuccess(String runtimeUiId, String registeredUiId, String action) {
        return new PacketBridgeAPI.UiRegistrationResult(true, runtimeUiId, registeredUiId, action, "");
    }

    private record RegistryCallResult(String action, boolean success, String message, boolean exceptionThrown) {
        private static RegistryCallResult success(String action, String message) {
            return new RegistryCallResult(action, true, message, false);
        }

        private static RegistryCallResult failure(String action, String message) {
            return new RegistryCallResult(action, false, message, false);
        }

        private static RegistryCallResult failure(String action, String message, boolean exceptionThrown) {
            return new RegistryCallResult(action, false, message, exceptionThrown);
        }
    }

}
