package xuanmo.arcartxsuite.conversation.service;

import ink.ptms.adyeshach.core.entity.EntityInstance;
import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.api.event.collect.ConversationEvents;
import ink.ptms.chemdah.api.event.collect.PluginReloadEvent;
import ink.ptms.chemdah.core.conversation.Conversation;
import ink.ptms.chemdah.core.conversation.Session;
import ink.ptms.chemdah.core.conversation.trigger.TriggerAdyeshachKt;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.bridge.AdyeshachNpcBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.AdyeshachNearbyNpc;
import xuanmo.arcartxsuite.api.bridge.AdyeshachNpcScanBridge;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.conversation.config.ConversationModuleConfiguration;
import xuanmo.arcartxsuite.conversation.config.NpcAppearanceEntry;
import xuanmo.arcartxsuite.conversation.theme.ArcartXConversationTheme;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;
import java.util.logging.Logger;

public final class ConversationService implements Listener {

    public static final String UI_RESOURCE_PATH = "arcartx/ui/conversation_dialog.yml";
    public static final String UI_FILE_PATH = "ui/conversation/conversation_dialog.yml";
    public static final String SELECTOR_UI_RESOURCE_PATH = "arcartx/ui/conversation_selector.yml";
    public static final String SELECTOR_UI_FILE_PATH = "ui/conversation/conversation_selector.yml";

    private static final AtomicLong GENERATION_SEQUENCE = new AtomicLong(System.currentTimeMillis());
    private static final DateTimeFormatter DEBUG_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final String NPC_SOURCE = "adyeshach";
    private static final String HANDLER_SYNC = "sync";
    private static final String HANDLER_DISTANCES = "distances";
    private static final String HANDLER_CLOSE = "close";
    private static final String ACTION_SELECT_NPC = "select_npc";
    private static final String ACTION_CONFIRM_NPC = "confirm_npc";
    private static final String ACTION_NAVIGATE_NPC = "navigate_npc";
    private static final String ACTION_SELECT_REPLY = "select_reply";
    private static final String ACTION_CONFIRM_REPLY = "confirm_reply";
    private static final String ACTION_CANCEL = "cancel";
    private static final String TARGET_DIALOG = "dialog";
    private static final String TARGET_SELECTOR = "selector";
    private static final int SELECTOR_VISIBLE_ROWS = 5;
    private static final long CONVERSATION_INDEX_REFRESH = TimeUnit.SECONDS.toNanos(5);
    private static final int NPC_APPEARANCE_MAX_ATTEMPTS = 30;
    private static final long NPC_APPEARANCE_RETRY_INTERVAL_TICKS = 10L;

    private final JavaPlugin plugin;
    private final Logger logger;
    private final PacketGuardAPI packetGuard;
    private final ConversationModuleConfiguration configuration;
    private final PacketBridgeAPI bridge;
    private final java.util.List<String> dialogUiIds;
    private final java.util.List<String> selectorUiIds;
    private final ArcartXConversationTheme theme;
    private final AdyeshachNpcBridgeAPI npcBridge;
    private final ChemdahAxRenderer dialogRenderer;
    private final NpcScanCycle<Player> npcScanCycle;
    private final ConcurrentMap<UUID, SelectorState> selectorStates = new ConcurrentHashMap<>();
    private final Set<UUID> selectorOpenedPlayers = ConcurrentHashMap.newKeySet();
    private final AtomicLong conversationRevision = new AtomicLong();
    private DisplayConversations displayConversations = new DisplayConversations(new NpcConversationIndex(List.of()),
        List.of(), -1, -1, 0);
    private DisplayConversations scanConversations = displayConversations;

    private volatile long generation = GENERATION_SEQUENCE.incrementAndGet();
    private volatile DebugSnapshot latestDebugSnapshot = DebugSnapshot.empty();
    private volatile Map<String, NpcAppearanceEntry> appearanceMapByLowerName = Map.of();
    private BukkitTask scanTask;
    private BukkitTask npcAppearanceRetryTask;
    private Listener chemdahReleasedListener;
    private Class<? extends Event> chemdahReleasedEventClass;
    private boolean selectorUiReady;
    private boolean keybindBridgeReady;
    private boolean keybindsRegistered;
    private boolean keyPressEventReady;
    private boolean npcBridgeReady;
    private boolean conversationOpenerReady;
    private boolean interactionReady;
    private String interactionDisabledReason = "";

    public ConversationService(
        JavaPlugin plugin,
        Logger logger,
        PacketGuardAPI packetGuard,
        ConversationModuleConfiguration configuration,
        PacketBridgeAPI bridge,
        java.util.List<String> dialogUiIds,
        java.util.List<String> selectorUiIds,
        AdyeshachNpcBridgeAPI npcBridge
    ) {
        this.plugin = plugin;
        this.logger = logger;
        this.packetGuard = packetGuard;
        this.configuration = configuration;
        this.npcScanCycle = new NpcScanCycle<>(configuration.interaction().scanPeriodTicks());
        this.bridge = bridge;
        this.dialogUiIds = dialogUiIds;
        this.selectorUiIds = selectorUiIds;
        this.theme = new ArcartXConversationTheme(this);
        this.npcBridge = npcBridge;
        this.dialogRenderer = new ChemdahAxRenderer(plugin, this.logger, configuration, bridge, dialogUiIds, this);
    }

    public void start() {
        registerTheme();
        registerDialogCloseCallback();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        registerChemdahReleasedListener();
        initializeInteractionEnhancement();
    }

    public void shutdown() {
        HandlerList.unregisterAll(this);
        for (String id : dialogUiIds) bridge.unregisterUiCloseCallback(id);
        unregisterChemdahReleasedListener();
        shutdownInteractionEnhancement();
        closeAllOpenViews("shutdown");
        dialogRenderer.clear();
        selectorStates.clear();
        advanceGeneration();
        ChemdahAPI.INSTANCE.getConversationTheme().remove(configuration.themeName(), theme);
    }

    public void display(Session session, List<String> lines, boolean canReply) {
        dialogRenderer.display(session, lines, canReply);
    }

    public void close(Session session) {
        dialogRenderer.close(session);
    }

    public boolean handleClientPacket(Player player, String packetId, List<String> data) {
        if (player == null || !player.isOnline() || packetId == null || !configuration.clientPacketId().equalsIgnoreCase(packetId)) {
            return false;
        }

        String action = data == null || data.isEmpty() ? "" : safeString(data.get(0)).toLowerCase(Locale.ROOT);
        // PacketGuard 已由 ClientPacketRouter 在路由层统一调用，此处不再重复检查。
        String token = data != null && data.size() > 1 ? safeString(data.get(1)) : "";
        String targetId = data != null && data.size() > 2 ? safeString(data.get(2)) : "";
        boolean tokenValid = token.equals(currentToken());
        logInboundAction(player, action, token, targetId, tokenValid);
        if (!tokenValid) {
            return true;
        }
        if ((ACTION_SELECT_NPC.equals(action) || ACTION_CONFIRM_NPC.equals(action)
            || ACTION_NAVIGATE_NPC.equals(action) || (ACTION_CANCEL.equals(action) && TARGET_SELECTOR.equalsIgnoreCase(targetId)))
            && !isSelectorOpen(player)) {
            return true;
        }

        if (ACTION_SELECT_NPC.equals(action)) {
            handleSelectNpc(player, targetId);
            return true;
        }
        if (ACTION_CONFIRM_NPC.equals(action)) {
            handleConfirmNpc(player, targetId);
            return true;
        }
        if (ACTION_NAVIGATE_NPC.equals(action)) {
            handleNavigateNpc(player, targetId);
            return true;
        }
        if (ACTION_SELECT_REPLY.equals(action)) {
            handleSelectReply(player, targetId);
            return true;
        }
        if (ACTION_CONFIRM_REPLY.equals(action)) {
            handleConfirmReply(player, targetId);
            return true;
        }
        if (ACTION_CANCEL.equals(action)) {
            handleCancel(player, targetId);
            return true;
        }
        return true;
    }

    private boolean isSelectorOpen(Player player) {
        if (!selectorOpenedPlayers.contains(player.getUniqueId())) return false;
        for (String id : selectorUiIds) {
            if (bridge.isUiOpen(player, id)) return true;
        }
        return false;
    }

    public String themeName() {
        return configuration.themeName();
    }

    public String uiId() {
        return dialogUiIds.isEmpty() ? "" : dialogUiIds.get(0);
    }

    public java.util.List<String> dialogUiIds() {
        return dialogUiIds;
    }

    public String selectorUiId() {
        return selectorUiIds.isEmpty() ? "" : selectorUiIds.get(0);
    }

    public java.util.List<String> selectorUiIds() {
        return selectorUiIds;
    }

    public String packetId() {
        return configuration.clientPacketId();
    }

    private void registerDialogCloseCallback() {
        if (dialogUiIds.isEmpty()) {
            return;
        }
        boolean registered = false;
        for (String id : dialogUiIds) {
            registered |= bridge.registerUiCloseCallback(id, dialogRenderer::handleUiClosed);
        }
        if (!registered) {
            this.logger.warning(
                "ArcartXConversation 未能注册对话 Menu 关闭回调，关闭按钮/ESC 可能无法同步关闭 Chemdah 会话。"
            );
        }
    }

    public int activeConversationCount() {
        return dialogRenderer.activeConversationCount();
    }

    public int openedPlayerCount() {
        return dialogRenderer.openedPlayerCount();
    }

    public int candidatePlayerCount() {
        int count = 0;
        for (SelectorState state : selectorStates.values()) {
            if (state.hasCandidates()) {
                count++;
            }
        }
        return count;
    }

    public int selectorOpenedPlayerCount() {
        return selectorOpenedPlayers.size();
    }

    public boolean interactionReady() {
        return interactionReady;
    }

    public boolean keybindReady() {
        return keybindBridgeReady && keybindsRegistered;
    }

    public boolean keyPressEventReady() {
        return keyPressEventReady;
    }

    public boolean npcBridgeReady() {
        return npcBridgeReady;
    }

    public AdyeshachNpcBridgeAPI npcBridge() {
        return npcBridge;
    }

    public boolean selectorUiReady() {
        return selectorUiReady;
    }

    /**
     * 能力实现：为玩家强制打开绑定到指定 Adyeshach NPC 的 Chemdah 会话。
     * <p>
     * 供 {@link xuanmo.arcartxsuite.api.capability.ConversationPlayable} 能力实现使用；
     * 与选择器/按键路径一致，最终走 {@code TriggerAdyeshachKt.openConversation}。
     */
    public boolean openConversationFor(Player player, String npcId) {
        if (!conversationOpenerReady || player == null || !player.isOnline() || npcId == null || npcId.isBlank()) {
            return false;
        }
        if (!hasConversation(npcId)) {
            return false;
        }
        java.util.Optional<Object> found;
        try {
            found = npcBridge.findByName(npcId);
        } catch (RuntimeException exception) {
            this.logger.warning("ConversationPlayable: 查找 NPC 失败 npcId=" + npcId + " - " + exception.getMessage());
            return false;
        }
        if (found.isEmpty()) {
            return false;
        }
        Object conversationEntity = found.get();
        if (conversationEntity instanceof ink.ptms.adyeshach.common.entity.EntityInstance common) {
            ink.ptms.adyeshach.core.entity.EntityInstance v2 = common.getV2();
            if (v2 != null) {
                conversationEntity = v2;
            }
        }
        return invokeOpenConversation(conversationEntity, player) != null;
    }

    /**
     * 能力实现：查询指定 NPC 是否绑定了至少一个 Chemdah 会话。
     * 供 {@link xuanmo.arcartxsuite.api.capability.ConversationPlayable} 能力实现使用。
     */
    public boolean hasConversation(String npcId) {
        if (npcId == null || npcId.isBlank()) {
            return false;
        }
        try {
            for (var conversation : ChemdahAPI.INSTANCE.getConversation().values()) {
                if (conversation != null && conversation.isNPC(NPC_SOURCE, npcId)) return true;
            }
            return false;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /**
     * 查询玩家是否正在与 Conversation 模块交互（对话中或 NPC 选择器已打开）。
     * 供 {@link xuanmo.arcartxsuite.api.capability.InteractionState} 能力实现使用。
     */
    public boolean isPlayerInteracting(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        return dialogRenderer.hasDialog(playerId) || selectorOpenedPlayers.contains(playerId);
    }

    /**
     * 宿主按键回调：处理确认键（AXS_INTERACT），返回 true 表示已消费。
     * 仅处理选择器状态（对话 UI 为非 HUD 模式，不接收全局按键事件）。
     */
    public boolean handleConfirmKeyFromHost(Player player) {
        if (player == null || !player.isOnline() || !AuthAccess.admitted(player.getUniqueId())) {
            return false;
        }
        // 选择器有候选 → 打开对话并消费
        SelectorState selectorState = selectorStates.get(player.getUniqueId());
        if (selectorState != null && selectorState.hasCandidates()) {
            openSelectedConversation(player, selectorState, "host-key-confirm");
            return true;
        }
        return false;
    }

    /**
     * 宿主按键回调：处理导航键（上/下），返回 true 表示已消费。
     * 仅处理选择器状态（对话 UI 为非 HUD 模式，不接收全局按键事件）。
     */
    public boolean handleNavigationKeyFromHost(Player player, int delta) {
        if (player == null || !player.isOnline() || !AuthAccess.admitted(player.getUniqueId())) {
            return false;
        }
        // 选择器中导航 → 消费
        SelectorState selectorState = selectorStates.get(player.getUniqueId());
        if (selectorState != null && selectorState.moveSelection(delta)) {
            syncSelectorView(player, selectorState, delta < 0 ? "host-key-prev" : "host-key-next");
            return true;
        }
        return false;
    }

    public String interactionDisabledReason() {
        return interactionDisabledReason;
    }

    public long generationToken() {
        return generation;
    }

    long generation() {
        return generation;
    }

    public String latestDebugSummary() {
        return latestDebugSnapshot.summary();
    }

    @EventHandler
    public void onChemdahConversationReload(PluginReloadEvent.Conversation event) {
        conversationRevision.incrementAndGet();
        long previousGeneration = generation;
        Runnable reload = () -> {
            if (generation != previousGeneration) return;
            resetRuntimeState("chemdah-reload");
            registerTheme();
        };
        if (Bukkit.isPrimaryThread()) reload.run();
        else runSyncIfEnabled("chemdah-reload", reload);
    }

    @EventHandler
    public void onChemdahConversationLoad(ConversationEvents.Load event) {
        conversationRevision.incrementAndGet();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        cleanupPlayerState(event.getPlayer(), "player-quit");
    }

    private void initializeInteractionEnhancement() {
        selectorUiReady = !selectorUiIds.isEmpty();
        interactionReady = false;
        interactionDisabledReason = "";

        if (!configuration.interactionEnabled()) {
            interactionDisabledReason = "配置已关闭";
            return;
        }
        if (!selectorUiReady) {
            interactionDisabledReason = "selector UI 未就绪";
            this.logger.warning("ArcartXConversation selector HUD 未就绪，已禁用按键增强。");
            return;
        }

        npcBridgeReady = npcBridge.initialize();
        conversationOpenerReady = initializeConversationOpener();

        if (!npcBridgeReady || !conversationOpenerReady) {
            interactionDisabledReason = buildInteractionDisabledReason();
            this.logger.warning("ArcartXConversation 按键增强已降级: " + interactionDisabledReason);
            npcBridge.shutdown();
            npcBridgeReady = false;
            conversationOpenerReady = false;
            return;
        }
        // 按键注册已由宿主 KeybindService 统一处理，模块只注册回调
        keybindBridgeReady = true;
        keybindsRegistered = true;
        keyPressEventReady = true;

        scanTask = Bukkit.getScheduler().runTaskTimer(
            plugin,
            this::scanNearbyCandidates,
            1L,
            1L
        );
        interactionReady = true;
        this.logger.info(
            "ArcartXConversation 按键增强已启用 | selectorUI=" + selectorUiIds
        );
        applyNpcAppearances();
    }

    private void shutdownInteractionEnhancement() {
        if (scanTask != null) {
            scanTask.cancel();
            scanTask = null;
        }
        npcScanCycle.reset();
        cancelNpcAppearanceRetry();
        appearanceMapByLowerName = Map.of();
        // 按键注册/监听由宿主管理，此处无需注销
        npcBridge.shutdown();
        keybindBridgeReady = false;
        keybindsRegistered = false;
        keyPressEventReady = false;
        npcBridgeReady = false;
        conversationOpenerReady = false;
        interactionReady = false;
    }

    @SuppressWarnings("unchecked")
    private boolean registerChemdahReleasedListener() {
        unregisterChemdahReleasedListener();

        Plugin chemdah = Bukkit.getPluginManager().getPlugin("Chemdah");
        if (chemdah == null) {
            logger.fine("Conversation: 未检测到 Chemdah 插件，对话释放清理监听已停用。");
            return false;
        }

        try {
            ClassLoader classLoader = chemdah.getClass().getClassLoader();
            Class<?> rawEventClass = Class.forName(
                "ink.ptms.chemdah.api.event.collect.PlayerEvents$Released",
                true, classLoader
            );
            if (!Event.class.isAssignableFrom(rawEventClass)) {
                this.logger.warning("Chemdah PlayerEvents.Released 不是 Bukkit Event，已跳过对话释放清理监听。");
                return false;
            }
            Class<? extends Event> eventClass = (Class<? extends Event>) rawEventClass;
            chemdahReleasedEventClass = eventClass;
            chemdahReleasedListener = new Listener() {};
            Bukkit.getPluginManager().registerEvent(
                eventClass, chemdahReleasedListener, EventPriority.MONITOR,
                (listener, event) -> handleChemdahReleasedEvent(event),
                plugin, true
            );
            return true;
        } catch (Exception | LinkageError exception) {
            this.logger.warning("注册 Chemdah PlayerEvents.Released 监听失败: " + describeThrowable(exception));
            return false;
        }
    }

    private void unregisterChemdahReleasedListener() {
        if (chemdahReleasedListener == null) {
            return;
        }
        HandlerList.unregisterAll(chemdahReleasedListener);
        chemdahReleasedListener = null;
        chemdahReleasedEventClass = null;
    }

    private void handleChemdahReleasedEvent(Event event) {
        // Chemdah 事件均继承自 TabooLib BukkitProxyEvent，共享同一个 HandlerList，
        // 因此注册 PlayerEvents.Released 监听后，TemplateEvents.Load 等其它子类事件也会触发本回调。
        // 这里严格按运行时类型过滤，只处理真正的 Released 事件，避免对无 getPlayer() 的事件误反射。
        Class<? extends Event> expected = chemdahReleasedEventClass;
        if (expected == null || event.getClass() != expected) {
            return;
        }
        try {
            var getPlayer = event.getClass().getMethod("getPlayer");
            Object rawPlayer = getPlayer.invoke(event);
            if (rawPlayer instanceof Player player) {
                cleanupPlayerState(player, "chemdah-released");
            }
        } catch (Exception | LinkageError exception) {
            this.logger.warning("处理 Chemdah PlayerEvents.Released 失败: " + describeThrowable(exception));
        }
    }

    private void registerTheme() {
        Object existing = ChemdahAPI.INSTANCE.getConversationTheme().putIfAbsent(configuration.themeName(), theme);
        if (existing == theme) {
            return;
        }
        if (existing != null) {
            logger.warning("ArcartXConversation 未注册主题 '" + configuration.themeName()
                + "'，该名称已被 " + existing.getClass().getName() + " 使用。请为本模块选择独立的主题名。");
            return;
        }
        if (configuration.debug()) {
            this.logger.info("ArcartXConversation 已注册 Chemdah 主题: " + configuration.themeName());
        }
    }

    private void resetRuntimeState(String reason) {
        closeAllOpenViews(reason);
        dialogRenderer.clear();
        selectorStates.clear();
        npcScanCycle.reset();
        advanceGeneration();
    }

    private void cleanupPlayerState(Player player, String reason) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (player.isOnline()) {
            dialogRenderer.closePlayerView(player, reason);
            closeSelectorView(player, true, reason + "-selector");
        } else {
            dialogRenderer.removePlayer(playerId);
            selectorOpenedPlayers.remove(playerId);
        }
        selectorStates.remove(playerId);
        selectorOpenedPlayers.remove(playerId);
    }

    void runSyncIfEnabled(String reason, Runnable task) {
        if (!plugin.isEnabled()) {
            return;
        }
        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (plugin.isEnabled()) {
                    task.run();
                }
            });
        } catch (RuntimeException exception) {
            if (configuration.debug()) {
                this.logger.warning("ArcartXConversation 主线程调度已跳过(" + reason + "): " + describeThrowable(exception));
            }
        }
    }

    private void handleSelectNpc(Player player, String npcId) {
        if (!interactionReady || dialogRenderer.hasDialog(player.getUniqueId())) {
            return;
        }
        SelectorState state = selectorStates.get(player.getUniqueId());
        if (state == null) return;
        int previousIndex = state.selectedIndex();
        if (state.selectNpc(npcId) && (state.selectedIndex() != previousIndex || !selectorOpenedPlayers.contains(player.getUniqueId()))) {
            syncSelectorView(player, state, "select-npc");
        }
    }

    private void handleConfirmNpc(Player player, String npcId) {
        if (!interactionReady || dialogRenderer.hasDialog(player.getUniqueId())) {
            return;
        }
        SelectorState state = selectorStates.get(player.getUniqueId());
        if (state == null || !state.hasCandidates()) {
            return;
        }
        if (!npcId.isBlank() && !state.selectNpc(npcId)) return;
        openSelectedConversation(player, state, "confirm-npc");
    }

    private void handleNavigateNpc(Player player, String direction) {
        if (!interactionReady || dialogRenderer.hasDialog(player.getUniqueId())) {
            return;
        }
        SelectorState state = selectorStates.get(player.getUniqueId());
        if (state == null || !state.hasCandidates()) {
            return;
        }
        int delta = "prev".equalsIgnoreCase(direction) ? -1 : ("next".equalsIgnoreCase(direction) ? 1 : 0);
        if (delta == 0 || !state.moveSelection(delta)) {
            return;
        }
        syncSelectorView(player, state, "navigate-npc");
    }

    private void handleSelectReply(Player player, String replyId) {
        dialogRenderer.handleSelectReply(player, replyId);
    }

    private void handleConfirmReply(Player player, String replyId) {
        dialogRenderer.handleConfirmReply(player, replyId);
    }

    private void handleCancel(Player player, String target) {
        if (TARGET_DIALOG.equalsIgnoreCase(target)) {
            dialogRenderer.handleCancelDialog(player);
            return;
        }
        if (TARGET_SELECTOR.equalsIgnoreCase(target)) {
            closeSelectorView(player, true, "cancel-selector");
        }
    }

    private void openSelectedConversation(Player player, SelectorState state, String source) {
        if (!interactionReady || state == null || state.generation() != generation || !state.hasCandidates()
            || !AuthAccess.admitted(player.getUniqueId()) || dialogRenderer.hasDialog(player.getUniqueId())) {
            return;
        }

        long now = System.currentTimeMillis();
        if (ConversationInteractionStateSupport.isSuppressed(now, state.suppressOpenUntil())) {
            return;
        }
        if (now - state.lastOpenAt() < configuration.interaction().openCooldownMs()) {
            return;
        }

        NpcCandidate candidate = state.selectedCandidate();
        if (candidate == null) {
            return;
        }

        state.setLastOpenAt(now);
        try {
            double range = configuration.interaction().scanRange();
            // The legacy Adyeshach manager may recreate its wrapper on each lookup.
            boolean targetCurrent = npcBridge instanceof AdyeshachNpcScanBridge scan
                ? scan.canInteract(player, range, candidate.npcId(), candidate.conversationEntity())
                : npcBridge.findNearby(player, range).stream().anyMatch(npc ->
                    candidate.conversationEntity() != null && npc.conversationEntity() == candidate.conversationEntity()
                        && npc.npcId().equalsIgnoreCase(candidate.npcId()));
            if (!targetCurrent || !hasLiveConversation(player, candidate.npcId())) {
                if (targetCurrent) conversationRevision.incrementAndGet();
                List<NpcCandidate> updated = findCandidates(player, displayConversations());
                if (updated.isEmpty()) {
                    state.clearCandidates(generation);
                    closeSelectorView(player, true, source + "-stale");
                } else {
                    state.replaceCandidates(updated, now, generation);
                    syncSelectorView(player, state, source + "-stale");
                }
                return;
            }
        } catch (RuntimeException exception) {
            state.clearCandidates(generation);
            closeSelectorView(player, true, source + "-lookup-failed");
            logger.warning("ArcartXConversation 复核 NPC 交互失败: " + describeThrowable(exception));
            return;
        }
        long openedGeneration = generation;
        closeSelectorView(player, true, source + "-open");
        CompletableFuture<?> future = invokeOpenConversation(candidate.conversationEntity(), player);
        if (future == null) {
            restoreSelectorIfEligible(player, source + "-null-future");
            return;
        }
        UUID playerId = player.getUniqueId();
        future.whenComplete((result, throwable) ->
            runSyncIfEnabled(source + "-open-complete", () -> {
                if (openedGeneration != generation || selectorStates.get(playerId) != state
                    || !player.isOnline() || Bukkit.getPlayer(playerId) != player || state.lastOpenAt() != now) return;
                if (throwable != null) {
                    this.logger.warning("ArcartXConversation 打开 NPC 对话失败: " + describeThrowable(throwable));
                    restoreSelectorIfEligible(player, source + "-failed");
                    return;
                }
                if (!dialogRenderer.hasDialog(playerId) && !dialogRenderer.isDialogOpened(playerId)) {
                    restoreSelectorIfEligible(player, source + "-restore");
                }
            })
        );
    }

    private void scanNearbyCandidates() {
        if (!interactionReady) return;
        var players = npcScanCycle.nextBatch(this::startNpcScanCycle);
        if (players.isEmpty()) return;
        long now = System.currentTimeMillis();
        var conversations = scanConversations;
        for (Player player : players) scanNearbyCandidates(player, conversations, now);
    }

    private Collection<? extends Player> startNpcScanCycle() {
        cleanupOfflineStates();
        scanConversations = displayConversations();
        if (scanConversations.isEmpty() && selectorStates.isEmpty() && selectorOpenedPlayers.isEmpty()) return List.of();
        return Bukkit.getOnlinePlayers();
    }

    private void scanNearbyCandidates(Player player, DisplayConversations conversations, long now) {
        if (!player.isOnline()) return;
        UUID playerId = player.getUniqueId();
        if (Bukkit.getPlayer(playerId) != player) return;
        try {
            SelectorState state = selectorStates.get(playerId);
            if (!AuthAccess.admitted(playerId)) {
                if (state != null) state.clearCandidates(generation);
                closeSelectorView(player, true, "not-admitted");
                return;
            }
            if (dialogRenderer.hasDialog(playerId)) {
                closeSelectorView(player, true, "dialog-active");
                return;
            }
            List<NpcCandidate> candidates = findCandidates(player, conversations);
            if (state == null) {
                if (candidates.isEmpty()) {
                    closeSelectorView(player, true, "no-candidates");
                    return;
                }
                state = selectorStates.computeIfAbsent(playerId, ignored -> new SelectorState());
            }
            boolean changed = refreshSelectorState(state, candidates, now);
            if (!state.hasCandidates()) {
                closeSelectorView(player, true, "no-candidates");
            } else if (changed || !selectorOpenedPlayers.contains(playerId)) {
                syncSelectorView(player, state, changed ? "scan-refresh" : "scan-open");
            }
        } catch (RuntimeException exception) {
            logger.warning("ArcartXConversation 扫描玩家附近 NPC 失败，已跳过本轮: player="
                + player.getName() + " | " + describeThrowable(exception));
        }
    }

    private List<NpcCandidate> findCandidates(Player player, DisplayConversations conversations) {
        if (conversations.isEmpty()) return List.of();
        String world = player.getWorld().getName();
        double range = configuration.interaction().scanRange();
        List<AdyeshachNearbyNpc> nearbyNpcs = npcBridge instanceof AdyeshachNpcScanBridge scan
            ? scan.findVisibleNearby(player, range, id -> conversations.mightContain(id, world))
            : npcBridge.findNearby(player, range);
        if (nearbyNpcs.isEmpty()) return List.of();

        List<NpcCandidate> result = new ArrayList<>();
        Set<String> encounteredIds = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (AdyeshachNearbyNpc npc : nearbyNpcs) {
            try {
                if (!Double.isFinite(npc.distanceSquared()) || !encounteredIds.add(npc.npcId())
                    || !conversations.contains(npc.npcId(), world)) {
                    continue;
                }
                result.add(
                    new NpcCandidate(
                        npc.npcId(),
                        safeString(npc.label()),
                        ConversationInteractionStateSupport.distanceTenths(npc.distanceSquared()),
                        npc.conversationEntity()
                    )
                );
            } catch (RuntimeException exception) {
                this.logger.warning(
                    "ArcartXConversation 处理 NPC 候选失败，已跳过: npc="
                        + npc.npcId()
                        + " | "
                        + describeThrowable(exception)
                );
            }
        }
        return List.copyOf(result);
    }

    private boolean refreshSelectorState(SelectorState state, List<NpcCandidate> candidates, long now) {
        if (!candidates.isEmpty()) {
            return state.replaceCandidates(candidates, now, generation);
        }
        if (state.hasCandidates() && now - state.lastVisibleAt() <= configuration.interaction().selectorStickyMs()) {
            return false;
        }
        return state.clearCandidates(generation);
    }

    private boolean hasLiveConversation(Player player, String npcId) {
        try {
            for (Conversation conversation : ChemdahAPI.INSTANCE.getConversation().values()) {
                if (conversation != null && (conversation.getClass() == Conversation.class
                        ? conversation.isNPC(NPC_SOURCE, npcId, player) : conversation.isNPC(NPC_SOURCE, npcId))) return true;
            }
        } catch (RuntimeException failure) {
            logger.fine("复核 NPC 对话绑定暂缓: " + failure.getMessage());
        }
        return false;
    }

    private DisplayConversations displayConversations() {
        long now = System.nanoTime();
        long revision = conversationRevision.get();
        try {
            var live = ChemdahAPI.INSTANCE.getConversation();
            int count = live.size();
            if (displayConversations.revision() == revision && displayConversations.count() == count
                    && now - displayConversations.refreshAt() < 0) return displayConversations;
            var list = new ArrayList<NpcConversationIndex.Binding>();
            var custom = new ArrayList<Conversation>();
            for (Conversation conversation : live.values()) {
                if (conversation == null) continue;
                if (conversation.getClass() != Conversation.class) {
                    custom.add(conversation);
                    continue;
                }
                for (var binding : conversation.getNpcId().getId()) {
                    if (NPC_SOURCE.equalsIgnoreCase(binding.getNamespace())) {
                        list.add(new NpcConversationIndex.Binding(binding.getValue(), binding.getWorlds()));
                    }
                }
            }
            if (count == live.size() && revision == conversationRevision.get()) {
                displayConversations = new DisplayConversations(new NpcConversationIndex(list), List.copyOf(custom),
                    revision, count, now + CONVERSATION_INDEX_REFRESH);
            }
        } catch (RuntimeException failure) {
            logger.fine("Chemdah 对话索引暂不可刷新: " + failure.getMessage());
        }
        return displayConversations;
    }

    private record DisplayConversations(NpcConversationIndex nativeBindings, List<Conversation> custom,
                                        long revision, int count, long refreshAt) {
        boolean isEmpty() { return nativeBindings.isEmpty() && custom.isEmpty(); }

        boolean mightContain(String npc, String world) {
            return !custom.isEmpty() || nativeBindings.contains(npc, world);
        }

        boolean contains(String npc, String world) {
            if (nativeBindings.contains(npc, world)) return true;
            for (var conversation : custom) {
                if (conversation.isNPC(NPC_SOURCE, npc)) return true;
            }
            return false;
        }
    }

    private void cleanupOfflineStates() {
        for (UUID playerId : selectorStates.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) continue;
            selectorStates.remove(playerId);
            selectorOpenedPlayers.remove(playerId);
            dialogRenderer.removePlayer(playerId);
        }
    }

    private void syncSelectorView(Player player, SelectorState state, String source) {
        if (!interactionReady || !selectorUiReady || !player.isOnline() || !AuthAccess.admitted(player.getUniqueId())
                || state == null || !state.hasCandidates()) {
            return;
        }

        boolean justOpened = selectorOpenedPlayers.add(player.getUniqueId());
        if (justOpened && !bridge.openUiAll(player, selectorUiIds)) {
            selectorOpenedPlayers.remove(player.getUniqueId());
            return;
        }

        boolean distancesOnly = !justOpened && state.canSendDistances();
        String handler = distancesOnly ? HANDLER_DISTANCES : HANDLER_SYNC;
        Map<String, Object> payload = distancesOnly ? state.distancePayload() : buildSelectorPayload(state);
        boolean success = bridge.sendPacketToAll(player, selectorUiIds, handler, payload);
        logOutboundPacket(player, selectorUiIds.isEmpty() ? "" : selectorUiIds.get(0), handler, payload, "npc", state.candidates().size(), state.selectedNpcId(), source, success);
        if (success) {
            state.markSent();
        } else {
            state.clearSent();
            selectorOpenedPlayers.remove(player.getUniqueId());
            bridge.closeUiAll(player, selectorUiIds);
        }
    }

    private void closeSelectorView(Player player, boolean sendClosePacket, String reason) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!selectorOpenedPlayers.remove(playerId)) {
            return;
        }
        SelectorState state = selectorStates.get(playerId);
        if (state != null) state.clearSent();
        if (!player.isOnline()) {
            return;
        }
        Map<String, Object> payload = closePayload();
        if (sendClosePacket) {
            boolean success = bridge.sendPacketToAll(player, selectorUiIds, HANDLER_CLOSE, payload);
            logOutboundPacket(player, selectorUiIds.isEmpty() ? "" : selectorUiIds.get(0), HANDLER_CLOSE, payload, "npc", 0, "", reason, success);
        }
        bridge.closeUiAll(player, selectorUiIds);
    }

    private void restoreSelectorIfEligible(Player player, String reason) {
        if (!interactionReady || player == null || !player.isOnline() || dialogRenderer.hasDialog(player.getUniqueId())) {
            return;
        }
        SelectorState state = selectorStates.get(player.getUniqueId());
        if (state == null || !state.hasCandidates()) {
            return;
        }
        syncSelectorView(player, state, "restore-" + reason);
    }

    void closeSelectorForDialog(Player player, String reason) {
        closeSelectorView(player, true, reason);
    }

    void restoreSelectorAfterDialog(Player player, String reason) {
        restoreSelectorIfEligible(player, reason);
    }

    void suppressSelectorOpen(UUID playerId, long now) {
        SelectorState selectorState = selectorStates.computeIfAbsent(playerId, ignored -> new SelectorState());
        selectorState.setSuppressOpenUntil(
            ConversationInteractionStateSupport.computeSuppressUntil(now, configuration.interaction().suppressReopenMs())
        );
    }

    private void closeAllOpenViews(String reason) {
        dialogRenderer.closeAllOpenViews(reason);
        for (UUID playerId : new ArrayList<>(selectorOpenedPlayers)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                closeSelectorView(player, true, reason + "-selector");
            }
        }
    }

    private Map<String, Object> buildSelectorPayload(SelectorState state) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("token", tokenFor(state.generation()));
        payload.put("packetId", configuration.clientPacketId());
        payload.put("title", "附近对话");
        payload.put("hintText", "滚轮/↑/↓ 切换，G 确认");
        payload.put("selectedNpcId", state.selectedNpcId());
        payload.put("selectedNpcIndex", state.selectedIndex());
        payload.put("npcCount", state.candidates().size());
        payload.put(
            "npcScrollRatio",
            ConversationInteractionStateSupport.computeScrollRatio(state.candidates().size(), state.selectedIndex(), SELECTOR_VISIBLE_ROWS)
        );
        payload.put("npcRows", buildNpcRows(state));
        payload.put("maxNpcCount", state.candidates().size());
        return payload;
    }

    private LinkedHashMap<String, Object> buildNpcRows(SelectorState state) {
        LinkedHashMap<String, Object> rows = new LinkedHashMap<>();
        for (int index = 0; index < state.candidates().size(); index++) {
            NpcCandidate candidate = state.candidates().get(index);
            rows.put(
                ConversationPayloadSupport.rowKey(index),
                ConversationPayloadSupport.flatRow(
                    "id", candidate.npcId(),
                    "text", candidate.label(),
                    "distanceText", ConversationInteractionStateSupport.distanceText(candidate.distanceTenths()),
                    "selected", candidate.npcId().equals(state.selectedNpcId()),
                    "index", index
                )
            );
        }
        return rows;
    }

    private Map<String, Object> closePayload() {
        return Map.of(
            "token", currentToken(),
            "packetId", configuration.clientPacketId()
        );
    }

    private boolean initializeConversationOpener() {
        Plugin chemdah = Bukkit.getPluginManager().getPlugin("Chemdah");
        if (chemdah == null) {
            logger.fine("Conversation: 未检测到 Chemdah 插件，对话触发器已停用。");
            return false;
        }
        if (!npcBridge.isAvailable()) {
            logger.fine("Conversation: Adyeshach 不可用，对话触发器已停用。");
            return false;
        }
        return true;
    }

    private CompletableFuture<?> invokeOpenConversation(Object conversationEntity, Player player) {
        if (!conversationOpenerReady || conversationEntity == null || player == null) {
            return null;
        }
        try {
            return TriggerAdyeshachKt.openConversation(
                (EntityInstance) conversationEntity,
                player,
                false
            );
        } catch (RuntimeException | LinkageError exception) {
            this.logger.warning("调用 Chemdah Adyeshach 对话打开器失败: " + describeThrowable(exception));
            return null;
        }
    }

    private String buildInteractionDisabledReason() {
        List<String> reasons = new ArrayList<>();
        if (!selectorUiReady) {
            reasons.add("selector UI");
        }
        if (!keybindBridgeReady) {
            reasons.add("KeyBindRegistry");
        }
        if (!keyPressEventReady) {
            reasons.add("ClientKeyPressEvent");
        }
        if (!npcBridgeReady) {
            reasons.add("Adyeshach NPC 反射");
        }
        if (!conversationOpenerReady) {
            reasons.add("Chemdah Adyeshach opener");
        }
        return reasons.isEmpty() ? "未知原因" : String.join(" / ", reasons);
    }

    private void logInboundAction(Player player, String action, String token, String targetId, boolean tokenValid) {
        SelectorState selectorState = selectorStates.get(player.getUniqueId());
        boolean dialogAction = action != null && action.contains("reply");
        String selectedId = dialogAction ? targetId : (selectorState == null ? "" : selectorState.selectedNpcId());
        int rowCount = dialogAction ? dialogRenderer.activeConversationCount() : (selectorState == null ? 0 : selectorState.candidates().size());
        String rowLabel = dialogAction ? "reply" : "npc";
        String note = "target=" + targetId + " | tokenValid=" + tokenValid + " | state=" + stateSummary(player.getUniqueId());
        updateDebugSnapshot("IN", player.getName(), action, token, selectedId, rowLabel, rowCount, note);

        if (!configuration.debug()) {
            return;
        }

        List<String> lines = new ArrayList<>();
        lines.add("ArcartXConversation 收到客户端回包");
        lines.add("  player: " + player.getName());
        lines.add("  action: " + action);
        lines.add("  token: " + token);
        lines.add("  tokenValid: " + tokenValid);
        lines.add("  targetId: " + targetId);
        lines.add("  state: " + stateSummary(player.getUniqueId()));
        emitDebugBlock(lines);
    }

    void logOutboundPacket(
        Player player,
        String ui,
        String handler,
        Map<String, Object> payload,
        String rowLabel,
        int rowCount,
        String selectedId,
        String note,
        boolean success
    ) {
        String token = payload == null ? "" : safeString(String.valueOf(payload.getOrDefault("token", "")));
        updateDebugSnapshot("OUT", player.getName(), ui + "/" + handler, token, selectedId, rowLabel, rowCount, note + " | success=" + success);

        if (!configuration.debug()) {
            return;
        }

        List<String> lines = new ArrayList<>();
        lines.add("ArcartXConversation 发包");
        lines.add("  player: " + player.getName());
        lines.add("  ui: " + ui);
        lines.add("  handler: " + handler);
        lines.add("  success: " + success);
        lines.add("  note: " + note);
        if (payload == null || payload.isEmpty()) {
            lines.add("  payload: {}");
        } else {
            appendPayloadLines(lines, payload);
        }
        emitDebugBlock(lines);
    }

    private void appendPayloadLines(List<String> lines, Map<String, Object> payload) {
        lines.add("  payload:");
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> rows) {
                lines.add("    " + entry.getKey() + ":");
                for (Map.Entry<?, ?> rowEntry : rows.entrySet()) {
                    lines.add("      " + rowEntry.getKey() + ": " + describeRowValue(rowEntry.getValue()));
                }
            } else {
                lines.add("    " + entry.getKey() + ": " + describeValue(value));
            }
        }
    }

    private void emitDebugBlock(List<String> lines) {
        this.logger.info(String.join("\n", lines));
    }

    private void updateDebugSnapshot(
        String direction,
        String playerName,
        String eventName,
        String token,
        String selectedId,
        String rowLabel,
        int rowCount,
        String note
    ) {
        latestDebugSnapshot = DebugSnapshot.of(
            System.currentTimeMillis(),
            direction,
            playerName,
            eventName,
            token,
            selectedId,
            rowLabel,
            rowCount,
            note
        );
    }

    void logIgnoredLifecycle(Player player, String reason, Session session, String eventName) {
        String playerName = player == null ? "-" : player.getName();
        UUID playerId = player == null ? null : player.getUniqueId();
        String state = playerId == null ? "state=none" : stateSummary(playerId);
        String note = "reason=" + reason + " | session=" + describeSession(session) + " | " + state;
        updateDebugSnapshot("IGN", playerName, eventName, currentToken(), "", "lifecycle", 0, note);

        if (!configuration.debug()) {
            return;
        }

        List<String> lines = new ArrayList<>();
        lines.add("ArcartXConversation 忽略生命周期事件");
        lines.add("  player: " + playerName);
        lines.add("  event: " + eventName);
        lines.add("  reason: " + reason);
        lines.add("  session: " + describeSession(session));
        lines.add("  state: " + state);
        emitDebugBlock(lines);
    }

    private String stateSummary(UUID playerId) {
        SelectorState selectorState = selectorStates.get(playerId);
        String selectorSummary = selectorState == null
            ? "selector=none"
            : "selector=npcs:" + selectorState.candidates().size() + ",selected:" + selectorState.selectedNpcId();
        return dialogRenderer.stateSummary(playerId) + " | " + selectorSummary;
    }

    private void advanceGeneration() {
        generation = GENERATION_SEQUENCE.incrementAndGet();
    }

    String currentToken() {
        return tokenFor(generation);
    }

    static String tokenFor(long generation) {
        return Long.toString(generation);
    }


    static List<String> safeLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<String> safe = new ArrayList<>(lines.size());
        for (String line : lines) {
            safe.add(safeString(line));
        }
        return List.copyOf(safe);
    }

    static String safeString(String value) {
        return value == null ? "" : value.trim();
    }

    private static String describeValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String string) {
            return '"' + string + '"';
        }
        return String.valueOf(value);
    }

    private static String describeRowValue(Object value) {
        if (!(value instanceof Map<?, ?> row)) {
            return String.valueOf(value);
        }
        List<String> segments = new ArrayList<>();
        for (Map.Entry<?, ?> entry : row.entrySet()) {
            segments.add(entry.getKey() + "=" + describeValue(entry.getValue()));
        }
        return String.join(", ", segments);
    }

    static String describeThrowable(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
    }

    private static String describeSession(Session session) {
        if (session == null) {
            return "null";
        }
        return sessionIdentity(session);
    }

    private static String sessionIdentity(Session session) {
        if (session == null) {
            return "null";
        }
        return session.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(session));
    }

    record NpcCandidate(
        String npcId,
        String label,
        long distanceTenths,
        Object conversationEntity
    ) {
        boolean sameRow(NpcCandidate other) {
            return sameNpc(other) && distanceTenths == other.distanceTenths;
        }

        boolean sameNpc(NpcCandidate other) {
            return npcId.equals(other.npcId) && label.equals(other.label);
        }
    }

    private record DebugSnapshot(
        long timestampMs,
        String direction,
        String playerName,
        String eventName,
        String token,
        String selectedId,
        String rowLabel,
        int rowCount,
        String note
    ) {

        private static DebugSnapshot empty() {
            return new DebugSnapshot(0L, "-", "-", "-", "-", "-", "-", 0, "无");
        }

        private static DebugSnapshot of(
            long timestampMs,
            String direction,
            String playerName,
            String eventName,
            String token,
            String selectedId,
            String rowLabel,
            int rowCount,
            String note
        ) {
            return new DebugSnapshot(timestampMs, direction, playerName, eventName, token, selectedId, rowLabel, rowCount, note);
        }

        private String summary() {
            if (timestampMs <= 0L) {
                return "无";
            }
            String time = DEBUG_TIME_FORMAT.format(Instant.ofEpochMilli(timestampMs).atZone(ZoneId.systemDefault()));
            return time
                + " | "
                + direction
                + " | "
                + playerName
                + " | "
                + eventName
                + " | token="
                + token
                + " | "
                + rowLabel
                + "="
                + rowCount
                + " | selected="
                + selectedId
                + " | "
                + note;
        }
    }

    static final class SelectorState {

        private List<NpcCandidate> candidates = List.of();
        private List<NpcCandidate> sentCandidates = List.of();
        private long sentGeneration;
        private int sentSelectedIndex;
        private String selectedNpcId = "";
        private int selectedIndex = -1;
        private long lastVisibleAt;
        private long suppressOpenUntil;
        private long lastOpenAt;
        private long generation;

        private List<NpcCandidate> candidates() {
            return candidates;
        }

        private String selectedNpcId() {
            return selectedNpcId;
        }

        private int selectedIndex() {
            return selectedIndex;
        }

        private long lastVisibleAt() {
            return lastVisibleAt;
        }

        private long suppressOpenUntil() {
            return suppressOpenUntil;
        }

        private void setSuppressOpenUntil(long suppressOpenUntil) {
            this.suppressOpenUntil = suppressOpenUntil;
        }

        private long lastOpenAt() {
            return lastOpenAt;
        }

        private void setLastOpenAt(long lastOpenAt) {
            this.lastOpenAt = lastOpenAt;
        }

        private long generation() {
            return generation;
        }

        private boolean hasCandidates() {
            return !candidates.isEmpty();
        }

        boolean replaceCandidates(List<NpcCandidate> updatedCandidates, long now, long generation) {
            List<NpcCandidate> normalized = List.copyOf(updatedCandidates);
            boolean changed = this.generation != generation || !sameRows(normalized);
            this.candidates = normalized;
            this.lastVisibleAt = now;
            this.generation = generation;
            if (!changed) return false;
            if (selectedIndex >= 0 && selectedIndex < normalized.size()
                && normalized.get(selectedIndex).npcId().equals(selectedNpcId)) return true;
            for (int index = 0; index < normalized.size(); index++) {
                if (normalized.get(index).npcId().equals(selectedNpcId)) {
                    selectedIndex = index;
                    return true;
                }
            }
            selectedIndex = normalized.isEmpty() ? -1 : 0;
            selectedNpcId = normalized.isEmpty() ? "" : normalized.get(0).npcId();
            return true;
        }

        private boolean sameRows(List<NpcCandidate> updated) {
            if (updated.size() != candidates.size()) return false;
            for (int index = 0; index < updated.size(); index++) {
                if (!updated.get(index).sameRow(candidates.get(index))) return false;
            }
            return true;
        }

        boolean canSendDistances() {
            if (sentCandidates.isEmpty() || sentCandidates.size() != candidates.size()
                    || sentGeneration != generation || sentSelectedIndex != selectedIndex) return false;
            for (int index = 0; index < candidates.size(); index++) {
                if (!candidates.get(index).sameNpc(sentCandidates.get(index))) return false;
            }
            return true;
        }

        Map<String, Object> distancePayload() {
            var values = new LinkedHashMap<String, String>();
            for (int index = 0; index < candidates.size(); index++) {
                values.put(ConversationPayloadSupport.rowKey(index),
                    ConversationInteractionStateSupport.distanceText(candidates.get(index).distanceTenths()));
            }
            return Map.of("token", tokenFor(generation), "distances", values);
        }

        void markSent() {
            sentCandidates = candidates;
            sentGeneration = generation;
            sentSelectedIndex = selectedIndex;
        }

        void clearSent() {
            sentCandidates = List.of();
        }

        private boolean clearCandidates(long generation) {
            boolean changed = !candidates.isEmpty() || selectedIndex != -1 || !selectedNpcId.isBlank();
            candidates = List.of();
            selectedNpcId = "";
            selectedIndex = -1;
            this.generation = generation;
            return changed;
        }

        private boolean moveSelection(int delta) {
            if (candidates.isEmpty()) {
                return false;
            }
            int nextIndex = ConversationInteractionStateSupport.wrapIndex(selectedIndex, candidates.size(), delta);
            if (nextIndex == selectedIndex) {
                return false;
            }
            selectedIndex = nextIndex;
            selectedNpcId = candidates.get(nextIndex).npcId();
            return true;
        }

        boolean selectNpc(String npcId) {
            if (npcId == null || npcId.isBlank()) {
                return false;
            }
            for (int index = 0; index < candidates.size(); index++) {
                if (candidates.get(index).npcId().equalsIgnoreCase(npcId.trim())) {
                    selectedIndex = index;
                    selectedNpcId = candidates.get(index).npcId();
                    return true;
                }
            }
            return false;
        }

        NpcCandidate selectedCandidate() {
            if (selectedIndex < 0 || selectedIndex >= candidates.size()) {
                return candidates.isEmpty() ? null : candidates.get(0);
            }
            return candidates.get(selectedIndex);
        }
    }

    private void applyNpcAppearances() {
        cancelNpcAppearanceRetry();
        npcBridge.unregisterVisibleHandler();
        List<NpcAppearanceEntry> entries = configuration.npcAppearances();
        if (entries == null || entries.isEmpty()) {
            appearanceMapByLowerName = Map.of();
            return;
        }
        if (!npcBridge.isAvailable()) {
            if (configuration.debug()) {
                this.logger.info("ArcartXConversation applyNpcAppearances: NPC 桥接不可用，跳过。");
            }
            return;
        }

        // 构建名称 -> 配置 映射表（visible handler 反查用）
        Map<String, NpcAppearanceEntry> map = new HashMap<>();
        for (NpcAppearanceEntry entry : entries) {
            if (entry.npcName() != null && !entry.npcName().isBlank()) {
                map.put(entry.npcName().strip().toLowerCase(Locale.ROOT), entry);
            }
        }
        appearanceMapByLowerName = Map.copyOf(map);

        // 注册 visible handler：玩家看见 NPC 时立即 per-player 发送模型包
        boolean registered = npcBridge.registerVisibleHandler(this::onAdyeshachEntityVisible);
        if (configuration.debug()) {
            this.logger.info("ArcartXConversation npc-appearances: visible 监听器注册 "
                + (registered ? "成功" : "失败") + "，映射表大小=" + appearanceMapByLowerName.size());
        }

        // 仍然保留启动重试逻辑作为兑底：对已在线且已看见 NPC 的玩家立即广播一次
        applyNpcAppearancesAttempt(new ArrayList<>(entries), 0);
    }

    /**
     * 在 ArcartX 客户端 mod 完成初始化握手后，对该玩家补发所有已配置 NPC 的外观包。
     * <p>必要性：visible 事件可能在客户端 mod 完成 ArcartX 握手之前触发（玩家刚登录瞬间），
     * 此时服务端发出的 sendSetEntityModel 包会被客户端丢弃。等 ClientInitializedEvent$End
     * 触发后再补发一次，可保证客户端 mod 已准备好接收并应用模型。
     */
    public void applyNpcAppearancesForPlayer(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        Map<String, NpcAppearanceEntry> currentMap = appearanceMapByLowerName;
        if (currentMap.isEmpty() || !npcBridge.isAvailable()) {
            return;
        }
        int applied = 0;
        for (NpcAppearanceEntry entry : currentMap.values()) {
            var npcOpt = npcBridge.findByName(entry.npcName());
            if (npcOpt.isEmpty()) {
                continue;
            }
            Object entity = npcOpt.get();
            boolean ok = npcBridge.applyModelForPlayer(player, entity, entry.modelId(), entry.scale());
            if (entry.hasPlayAnimation()) {
                ok = npcBridge.applyAnimationForPlayer(player, entity, entry.animName(),
                    entry.animationSpeed(), entry.transitionTime(), entry.keepTime()) && ok;
            } else if (entry.hasDefaultState()) {
                ok = npcBridge.applyDefaultStateForPlayer(player, entity, entry.state(), entry.animName()) && ok;
            }
            if (ok) {
                applied++;
            }
        }
        if (configuration.debug()) {
            this.logger.info("ArcartXConversation npc-appearances: 玩家 " + player.getName()
                + " 客户端初始化完成，已补发 " + applied + " 个 NPC 模型包。");
        }
    }

    /**
     * AdyeshachEntityVisibleEvent 回调：玩家进入 NPC 视野时，
     * 查找该实体对应的外观配置并点对点发送模型/动画包。
     */
    private void onAdyeshachEntityVisible(Player viewer, Object adyeshachEntity) {
        if (viewer == null || adyeshachEntity == null) {
            return;
        }
        Map<String, NpcAppearanceEntry> currentMap = appearanceMapByLowerName;
        if (currentMap.isEmpty()) {
            return;
        }
        List<String> candidateNames = npcBridge.getEntityNames(adyeshachEntity);
        NpcAppearanceEntry matched = null;
        for (String candidate : candidateNames) {
            NpcAppearanceEntry entry = currentMap.get(candidate.toLowerCase(Locale.ROOT));
            if (entry != null) {
                matched = entry;
                break;
            }
        }
        if (matched == null) {
            return;
        }
        // 对该玩家点对点发送模型包
        npcBridge.applyModelForPlayer(viewer, adyeshachEntity, matched.modelId(), matched.scale());
        if (matched.hasPlayAnimation()) {
            npcBridge.applyAnimationForPlayer(viewer, adyeshachEntity, matched.animName(),
                matched.animationSpeed(), matched.transitionTime(), matched.keepTime());
        } else if (matched.hasDefaultState()) {
            npcBridge.applyDefaultStateForPlayer(viewer, adyeshachEntity, matched.state(), matched.animName());
        }
        if (configuration.debug()) {
            this.logger.info("ArcartXConversation npc-appearances: visible -> " + viewer.getName()
                + " 看见 NPC \"" + matched.npcName() + "\"，已发送模型包 model=" + matched.modelId());
        }
    }

    private void applyNpcAppearancesAttempt(List<NpcAppearanceEntry> pending, int attempt) {
        List<NpcAppearanceEntry> stillPending = new ArrayList<>();
        int applied = 0;
        for (NpcAppearanceEntry entry : pending) {
            var npcOpt = npcBridge.findByName(entry.npcName());
            if (npcOpt.isEmpty()) {
                // Adyeshach 实体可能还未加载，加入待重试队列（不立即警告）
                stillPending.add(entry);
                continue;
            }
            Object npcEntity = npcOpt.get();
            boolean ok = npcBridge.applyModel(npcEntity, entry.modelId(), entry.scale());
            if (entry.hasPlayAnimation()) {
                ok = npcBridge.applyAnimation(npcEntity, entry.animName(), entry.animationSpeed(),
                    entry.transitionTime(), entry.keepTime()) && ok;
            } else if (entry.hasDefaultState()) {
                ok = npcBridge.applyDefaultState(npcEntity, entry.state(), entry.animName()) && ok;
            }
            if (ok) {
                applied++;
            } else {
                this.logger.warning(
                    "ArcartXConversation npc-appearances: NPC \"" + entry.npcName() + "\" 应用失败。"
                );
            }
        }

        if (stillPending.isEmpty()) {
            // 所有实体都已处理完毕（成功或明确失败）
            if (configuration.debug() && applied > 0) {
                this.logger.info("ArcartXConversation npc-appearances: 已应用 " + applied
                    + " 个 NPC（第 " + (attempt + 1) + " 次尝试）。");
            }
            return;
        }

        if (attempt + 1 >= NPC_APPEARANCE_MAX_ATTEMPTS) {
            // 达到最大重试次数，输出最终警告
            for (NpcAppearanceEntry entry : stillPending) {
                this.logger.warning(
                    "ArcartXConversation npc-appearances: 经过 " + NPC_APPEARANCE_MAX_ATTEMPTS
                        + " 次重试仍未找到 NPC \"" + entry.npcName()
                        + "\"，跳过。请确认 Adyeshach 中存在该 NPC。"
                );
            }
            return;
        }

        // 调度延迟重试：Adyeshach 可能尚未加载完全部 NPC json
        if (configuration.debug()) {
            this.logger.info("ArcartXConversation npc-appearances: 第 " + (attempt + 1)
                + " 次尝试后仍有 " + stillPending.size() + " 个 NPC 未找到，将在 "
                + NPC_APPEARANCE_RETRY_INTERVAL_TICKS + " tick 后重试。");
        }
        npcAppearanceRetryTask = Bukkit.getScheduler().runTaskLater(
            plugin,
            () -> applyNpcAppearancesAttempt(stillPending, attempt + 1),
            NPC_APPEARANCE_RETRY_INTERVAL_TICKS
        );
    }

    private void cancelNpcAppearanceRetry() {
        if (npcAppearanceRetryTask != null) {
            npcAppearanceRetryTask.cancel();
            npcAppearanceRetryTask = null;
        }
    }
}



