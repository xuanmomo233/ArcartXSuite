package xuanmo.arcartxsuite.questgps.service;

import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.AcceptResult;
import ink.ptms.chemdah.core.quest.Quest;
import ink.ptms.chemdah.core.quest.Task;
import ink.ptms.chemdah.core.quest.Template;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.capability.ChatCardSendable;
import xuanmo.arcartxsuite.api.capability.MapNavigable;
import xuanmo.arcartxsuite.api.capability.SubtitlePlayable;
import xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.questgps.QuestGpsCategory;
import xuanmo.arcartxsuite.questgps.QuestGpsPage;
import xuanmo.arcartxsuite.questgps.config.QuestGpsModuleConfiguration;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;
import xuanmo.arcartxsuite.api.capability.TitleConfigQueryable;

public final class QuestGpsService implements Listener {

    public static final String MENU_UI_RESOURCE_PATH = "arcartx/ui/questgps_menu.yml";
    public static final String MENU_UI_FILE_PATH = "ui/questgps_menu.yml";
    public static final String GUIDE_UI_RESOURCE_PATH = "arcartx/ui/questgps_guide.yml";
    public static final String GUIDE_UI_FILE_PATH = "ui/questgps_guide.yml";

    private static final String PREFIX = ChatColor.DARK_AQUA + "◆ " + ChatColor.GOLD + "ArcartXSuite " + ChatColor.GRAY + "| " + ChatColor.RESET;
    private static final String MAP_SOURCE = "questgps";

    private final JavaPlugin plugin;
    private final PacketGuardAPI packetGuard;
    private final Supplier<MapNavigable> mapNavigableProvider;
    private final Supplier<SubtitlePlayable> subtitlePlayableProvider;
    private final Supplier<ChatCardSendable> chatCardSendableProvider;
    private final BiConsumer<String, Player> signalDispatcher;
    private final QuestGpsModuleConfiguration configuration;
    private final PacketBridgeAPI bridge;
    private final java.util.List<String> menuUiIds;
    private final java.util.List<String> guideUiIds;
    private final QuestGpsRewardPreviewResolver rewardResolver;
    private final QuestGpsNavigationService navigationService;
    private final QuestGpsSnapshotBuilder snapshotBuilder = new QuestGpsSnapshotBuilder();
    private final QuestGpsUiPacketHandler uiPacketHandler;
    private final ConcurrentMap<UUID, QuestGpsViewState> viewStates = new ConcurrentHashMap<>();
    private final List<Listener> chemdahEventListeners = new ArrayList<>();

    public QuestGpsService(
        JavaPlugin plugin,
        PacketGuardAPI packetGuard,
        QuestGpsModuleConfiguration configuration,
        PacketBridgeAPI bridge,
        ItemBridgeAPI itemStackBridge,
        Supplier<TitleConfigQueryable> titleConfigurationProvider,
        Supplier<MapNavigable> mapNavigableProvider,
        Supplier<SubtitlePlayable> subtitlePlayableProvider,
        Supplier<ChatCardSendable> chatCardSendableProvider,
        BiConsumer<String, Player> signalDispatcher,
        java.util.List<String> menuUiIds,
        java.util.List<String> guideUiIds,
        xuanmo.arcartxsuite.api.item.ItemSourceRegistry itemSourceRegistry,
        xuanmo.arcartxsuite.api.bridge.WaypointBridgeAPI waypointBridge,
        xuanmo.arcartxsuite.api.bridge.AdyeshachNpcBridgeAPI npcBridge
    ) {
        this.plugin = plugin;
        this.packetGuard = packetGuard;
        this.mapNavigableProvider = mapNavigableProvider == null ? () -> null : mapNavigableProvider;
        this.subtitlePlayableProvider = subtitlePlayableProvider == null ? () -> null : subtitlePlayableProvider;
        this.chatCardSendableProvider = chatCardSendableProvider == null ? () -> null : chatCardSendableProvider;
        this.signalDispatcher = signalDispatcher;
        this.configuration = configuration;
        this.bridge = bridge;
        this.menuUiIds = menuUiIds;
        this.guideUiIds = guideUiIds;
        this.rewardResolver = new QuestGpsRewardPreviewResolver(
            plugin,
            titleConfigurationProvider,
            itemStack -> itemStackBridge == null ? java.util.Optional.empty() : itemStackBridge.itemToJson(itemStack),
            itemSourceRegistry
        );
        this.navigationService = new QuestGpsNavigationService(plugin, configuration, waypointBridge, npcBridge);
        this.uiPacketHandler = new QuestGpsUiPacketHandler(this, configuration.client().packetId());
    }

    public void start() {
        navigationService.start();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        registerChemdahEventListeners();
    }

    public void shutdown() {
        HandlerList.unregisterAll(this);
        for (Listener listener : chemdahEventListeners) {
            HandlerList.unregisterAll(listener);
        }
        chemdahEventListeners.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            clearTrackInternal(player, true, false);
        }
        navigationService.shutdown();
        viewStates.clear();
    }

    public boolean handleClientPacket(Player player, String packetId, List<String> data) {
        return uiPacketHandler.handleClientPacket(player, packetId, data);
    }

    JavaPlugin plugin() {
        return plugin;
    }

    Supplier<MapNavigable> mapNavigableProvider() {
        return mapNavigableProvider;
    }

    public java.util.Map<String, xuanmo.arcartxsuite.questgps.QuestGpsCategory> categoryRegistry() {
        return configuration.categoryRegistry();
    }

    boolean allowClientPacket(Player player, String action) {
        return packetGuard == null || packetGuard.allow(player, "questgps", action, configuration.debug());
    }

    public int configuredQuestCount() {
        return configuration.configuredQuestCount();
    }

    public int trackingPlayerCount() {
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (navigationService.trackingState(player).active()) {
                count++;
            }
        }
        return count;
    }

    public boolean navigationRuntimeReady() {
        return navigationService.runtimeReady();
    }

    public void openMenu(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        PlayerProfile profile = loadProfile(player, true);
        if (profile == null) {
            return;
        }
        QuestGpsViewState state = viewStates.computeIfAbsent(player.getUniqueId(), ignored -> new QuestGpsViewState());
        bridge.openUiAll(player, menuUiIds);
        syncMenu(player, profile, state, true);
    }

    public void refreshMenu(Player player) {
        refreshViewer(player);
    }

    public void switchCategory(Player player, QuestGpsCategory category) {
        if (player == null || category == null) {
            return;
        }
        if (isCategoryLocked(player, category)) {
            denyGate(player);
            refreshViewer(player);
            return;
        }
        QuestGpsViewState state = viewStates.computeIfAbsent(player.getUniqueId(), ignored -> new QuestGpsViewState());
        state.setCategory(category);
        state.setSelectedQuestId("");
        refreshViewer(player);
    }

    public void switchPage(Player player, QuestGpsPage page) {
        if (player == null || page == null) {
            return;
        }
        QuestGpsViewState state = viewStates.computeIfAbsent(player.getUniqueId(), ignored -> new QuestGpsViewState());
        state.setPage(page);
        state.setSelectedQuestId("");
        refreshViewer(player);
    }

    public void selectQuest(Player player, String questId) {
        if (player == null || questId == null || questId.isBlank()) {
            return;
        }
        QuestGpsViewState state = viewStates.computeIfAbsent(player.getUniqueId(), ignored -> new QuestGpsViewState());
        state.setSelectedQuestId(questId);
        refreshViewer(player);
    }

    public void acceptQuest(Player player, String questId) {
        PlayerProfile profile = loadProfile(player, true);
        if (profile == null) {
            return;
        }
        String targetQuestId = resolveQuestId(player, questId);
        QuestGpsModuleConfiguration.QuestDefinition definition = configuration.quest(targetQuestId);
        if (definition == null) {
            player.sendMessage(PREFIX + ChatColor.RED + "该任务未配置到 QuestGPS: " + targetQuestId);
            return;
        }
        if (isCategoryLocked(player, definition.category()) || !hasCompletedRequiredMainline(profile, definition.requiredMainline())) {
            denyGate(player);
            return;
        }
        Template template = ChemdahAPI.INSTANCE.getQuestTemplate(definition.id());
        if (template == null) {
            player.sendMessage(PREFIX + ChatColor.RED + "未找到 Chemdah 任务: " + definition.id());
            return;
        }

        template.acceptTo(profile).whenComplete((result, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (throwable != null) {
                player.sendMessage(PREFIX + ChatColor.RED + "接取任务失败，请检查控制台。");
                plugin.getLogger().log(Level.WARNING, "QuestGPS 接取任务失败: " + definition.id(), throwable);
                refreshViewer(player);
                return;
            }
            if (result != null && result.getType() == AcceptResult.Type.SUCCESSFUL) {
                QuestGpsViewState state = viewStates.computeIfAbsent(player.getUniqueId(), ignored -> new QuestGpsViewState());
                state.setPage(QuestGpsPage.ACTIVE);
                state.setSelectedQuestId(definition.id());
                player.sendMessage(PREFIX + ChatColor.GREEN + "已接取任务: " + displayQuestName(template, definition));
            } else {
                String reason = result == null ? "未知原因" : safe(result.getReason());
                player.sendMessage(PREFIX + ChatColor.RED + "接取失败: " + blankTo(reason, result == null ? "未知错误" : result.getType().name()));
            }
            refreshViewer(player);
        }));
    }

    public void abandonQuest(Player player, String questId) {
        PlayerProfile profile = loadProfile(player, true);
        if (profile == null) {
            return;
        }
        String targetQuestId = resolveQuestId(player, questId);
        QuestGpsModuleConfiguration.QuestDefinition definition = configuration.quest(targetQuestId);
        if (definition == null || !definition.allowAbandon()) {
            player.sendMessage(PREFIX + ChatColor.RED + "该任务不允许放弃。");
            return;
        }
        Template template = ChemdahAPI.INSTANCE.getQuestTemplate(definition.id());
        Quest activeQuest = profile.getQuestById(definition.id(), false);
        if (template == null || activeQuest == null || activeQuest.isCompleted()) {
            player.sendMessage(PREFIX + ChatColor.RED + "当前任务不可放弃。");
            return;
        }

        activeQuest.failQuestFuture().whenComplete((success, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (throwable != null || success == null || !success) {
                player.sendMessage(PREFIX + ChatColor.RED + "放弃任务失败。");
                if (throwable != null) {
                    plugin.getLogger().log(Level.WARNING, "QuestGPS 放弃任务失败: " + definition.id(), throwable);
                }
                refreshViewer(player);
                return;
            }
            clearTrackForQuest(player, definition.id());
            QuestGpsViewState state = viewStates.computeIfAbsent(player.getUniqueId(), ignored -> new QuestGpsViewState());
            state.setPage(QuestGpsPage.AVAILABLE);
            state.setSelectedQuestId(definition.id());
            player.sendMessage(PREFIX + ChatColor.YELLOW + "已放弃任务: " + displayQuestName(template, definition));
            refreshViewer(player);
        }));
    }

    public void trackQuest(Player player, String questId) {
        PlayerProfile profile = loadProfile(player, true);
        if (profile == null) {
            return;
        }
        String targetQuestId = resolveQuestId(player, questId);
        List<QuestGpsSnapshotBuilder.QuestDescriptor> descriptors = collectDescriptors(profile);
        QuestGpsSnapshotBuilder.QuestDescriptor descriptor = findDescriptor(descriptors, targetQuestId);
        if (descriptor == null || !descriptor.questTrackAvailable()) {
            player.sendMessage(PREFIX + ChatColor.RED + "该任务当前没有可用导航点。");
            return;
        }
        boolean success = navigationService.trackQuest(player, descriptor.questId(), descriptor.displayName(), prioritizedTaskIds(descriptor.tasks()));
        if (!success) {
            player.sendMessage(PREFIX + ChatColor.RED + "任务导航创建失败。");
            return;
        }
        afterTrackChanged(player, descriptor.questId());
        player.sendMessage(PREFIX + ChatColor.GREEN + "已设置任务导航: " + descriptor.displayName());
        refreshViewer(player);
    }

    public void trackTask(Player player, String questId, String taskId) {
        PlayerProfile profile = loadProfile(player, true);
        if (profile == null) {
            return;
        }
        String targetQuestId = resolveQuestId(player, questId);
        List<QuestGpsSnapshotBuilder.QuestDescriptor> descriptors = collectDescriptors(profile);
        QuestGpsSnapshotBuilder.QuestDescriptor descriptor = findDescriptor(descriptors, targetQuestId);
        if (descriptor == null) {
            player.sendMessage(PREFIX + ChatColor.RED + "未找到任务。");
            return;
        }
        QuestGpsSnapshotBuilder.TaskDescriptor taskDescriptor = findTask(descriptor.tasks(), taskId);
        if (taskDescriptor == null || !taskDescriptor.trackAvailable()) {
            player.sendMessage(PREFIX + ChatColor.RED + "该任务目标当前没有可用导航点。");
            return;
        }
        boolean success = navigationService.trackTask(player, descriptor.questId(), taskDescriptor.taskId(), taskDescriptor.text());
        if (!success) {
            player.sendMessage(PREFIX + ChatColor.RED + "任务目标导航创建失败。");
            return;
        }
        afterTrackChanged(player, descriptor.questId());
        player.sendMessage(PREFIX + ChatColor.GREEN + "已设置任务目标导航: " + taskDescriptor.text());
        refreshViewer(player);
    }

    public void clearTrack(Player player) {
        clearTrackInternal(player, false, true);
        refreshViewer(player);
    }

    public void offerQuest(Player player, String questId, boolean openMenu) {
        QuestGpsModuleConfiguration.QuestDefinition definition = configuration.quest(questId);
        if (player == null || definition == null) {
            return;
        }
        QuestGpsViewState state = viewStates.computeIfAbsent(player.getUniqueId(), ignored -> new QuestGpsViewState());
        state.setCategory(definition.category());
        state.setPage(QuestGpsPage.AVAILABLE);
        state.setSelectedQuestId(definition.id());
        fireQuestHooks(player, definition, Lifecycle.TRIGGERED);
        if (openMenu) {
            openMenu(player);
        } else {
            refreshViewer(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        viewStates.remove(uuid);
        clearTrackInternal(event.getPlayer(), true, false);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String message = safe(event.getMessage()).trim().toLowerCase(java.util.Locale.ROOT);
        if (message.startsWith("/")) {
            message = message.substring(1);
        }
        if (message.isBlank() || mainlineGateSatisfied(event.getPlayer())) {
            return;
        }
        for (String prefix : configuration.gate().blockedCommandPrefixes()) {
            String normalizedPrefix = safe(prefix).trim().toLowerCase(java.util.Locale.ROOT);
            if (normalizedPrefix.startsWith("/")) {
                normalizedPrefix = normalizedPrefix.substring(1);
            }
            if (!normalizedPrefix.isBlank() && message.startsWith(normalizedPrefix)) {
                event.setCancelled(true);
                denyGate(event.getPlayer());
                return;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void registerChemdahEventListeners() {
        org.bukkit.plugin.Plugin chemdah = Bukkit.getPluginManager().getPlugin("Chemdah");
        if (chemdah == null) {
            return;
        }
        registerChemdahQuestLifecycleEvent(chemdah, "ink.ptms.chemdah.api.event.collect.QuestEvents$Accept$Post", Lifecycle.ACCEPTED, false);
        registerChemdahQuestLifecycleEvent(chemdah, "ink.ptms.chemdah.api.event.collect.QuestEvents$Fail$Post", Lifecycle.ABANDONED, true);
        registerChemdahQuestLifecycleEvent(chemdah, "ink.ptms.chemdah.api.event.collect.QuestEvents$Complete$Post", Lifecycle.COMPLETED, true);
        try {
            Class<?> rawEventClass = Class.forName(
                "ink.ptms.chemdah.api.event.collect.PluginReloadEvent$Quest",
                true,
                chemdah.getClass().getClassLoader()
            );
            if (!Event.class.isAssignableFrom(rawEventClass)) {
                return;
            }
            Class<? extends Event> eventClass = (Class<? extends Event>) rawEventClass;
            Listener listener = new Listener() {
            };
            Bukkit.getPluginManager().registerEvent(
                eventClass,
                listener,
                EventPriority.MONITOR,
                (ignored, event) -> {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        refreshViewer(player);
                    }
                },
                plugin,
                true
            );
            chemdahEventListeners.add(listener);
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("QuestGPS 注册 Chemdah 重载事件失败: " + exception.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void registerChemdahQuestLifecycleEvent(
        org.bukkit.plugin.Plugin chemdah,
        String className,
        Lifecycle lifecycle,
        boolean clearTracking
    ) {
        try {
            Class<?> rawEventClass = Class.forName(className, true, chemdah.getClass().getClassLoader());
            if (!Event.class.isAssignableFrom(rawEventClass)) {
                return;
            }
            Class<? extends Event> eventClass = (Class<? extends Event>) rawEventClass;
            Method getPlayerProfile = rawEventClass.getMethod("getPlayerProfile");
            Method getQuest = rawEventClass.getMethod("getQuest");
            Method getQuestId = Class.forName("ink.ptms.chemdah.core.quest.Quest", true, chemdah.getClass().getClassLoader()).getMethod("getId");
            Listener listener = new Listener() {
            };
            Bukkit.getPluginManager().registerEvent(
                eventClass,
                listener,
                EventPriority.MONITOR,
                (ignored, event) -> handleChemdahQuestLifecycleEvent(
                    event,
                    rawEventClass,
                    getPlayerProfile,
                    getQuest,
                    getQuestId,
                    lifecycle,
                    clearTracking
                ),
                plugin,
                true
            );
            chemdahEventListeners.add(listener);
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("QuestGPS 注册 Chemdah 任务事件失败(" + className + "): " + exception.getMessage());
        }
    }

    private void handleChemdahQuestLifecycleEvent(
        Event event,
        Class<?> expectedEventClass,
        Method getPlayerProfile,
        Method getQuest,
        Method getQuestId,
        Lifecycle lifecycle,
        boolean clearTracking
    ) {
        if (!expectedEventClass.isInstance(event)) {
            if (configuration.debug()) {
                plugin.getLogger().fine(
                    "QuestGPS 忽略非匹配 Chemdah 任务事件: expected="
                        + expectedEventClass.getName()
                        + ", actual="
                        + event.getClass().getName()
                );
            }
            return;
        }
        try {
            Object rawProfile = invokeCompatible(getPlayerProfile, event);
            Object rawQuest = invokeCompatible(getQuest, event);
            Object rawQuestId = invokeCompatible(getQuestId, rawQuest);
            if (rawProfile == null || rawQuest == null || rawQuestId == null) {
                return;
            }
            String questId = String.valueOf(rawQuestId);
            if (!(rawProfile instanceof PlayerProfile profile)) {
                return;
            }
            Player player = profile.getPlayer();
            if (player != null && clearTracking) {
                clearTrackForQuest(player, questId);
            }
            QuestGpsModuleConfiguration.QuestDefinition definition = configuration.quest(questId);
            if (player != null && definition != null) {
                fireQuestHooks(player, definition, lifecycle);
            }
            refreshByProfile(profile);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().warning("QuestGPS 处理 Chemdah 任务事件失败: " + exception.getMessage());
        }
    }

    static Object invokeCompatible(Method method, Object target) throws ReflectiveOperationException {
        if (method == null || target == null) {
            return null;
        }
        Method actualMethod = method;
        if (!method.getDeclaringClass().isInstance(target)) {
            try {
                actualMethod = target.getClass().getMethod(method.getName(), method.getParameterTypes());
            } catch (NoSuchMethodException ignored) {
                return null;
            }
            if (!actualMethod.getDeclaringClass().isInstance(target)) {
                return null;
            }
        }
        return actualMethod.invoke(target);
    }

    private void afterTrackChanged(Player player, String questId) {
        syncMapTarget(player);
        syncGuide(player, true);
        QuestGpsModuleConfiguration.QuestDefinition definition = configuration.quest(questId);
        if (definition != null) {
            fireQuestHooks(player, definition, Lifecycle.TRACK_CHANGED);
        }
    }

    private void syncMapTarget(Player player) {
        MapNavigable mapService = mapNavigableProvider.get();
        if (mapService == null) {
            return;
        }
        java.util.Optional<QuestGpsNavigationService.NavigationPoint> point = navigationService.trackingPoint(player);
        QuestGpsNavigationService.TrackingState state = navigationService.trackingState(player);
        if (point.isEmpty() || !state.active()) {
            mapService.clearExternalTargets(player, MAP_SOURCE, true);
            return;
        }
        QuestGpsNavigationService.NavigationPoint p = point.get();
        mapService.upsertExternalTarget(
            player,
            state.waypointId(),
            MAP_SOURCE,
            p.world(),
            p.x(),
            p.y(),
            p.z(),
            state.taskId().isBlank() ? "QuestGPS 任务导航" : "QuestGPS 任务目标导航",
            null,
            true
        );
    }

    private void clearTrackInternal(Player player, boolean silent, boolean notify) {
        navigationService.clearTracking(player, silent);
        MapNavigable mapSvc = mapNavigableProvider.get();
        if (mapSvc != null) {
            mapSvc.clearExternalTargets(player, MAP_SOURCE, true);
        }
        closeGuide(player);
        if (notify) {
            player.sendMessage(PREFIX + ChatColor.YELLOW + "已清除当前任务导航。");
        }
    }

    private void clearTrackForQuest(Player player, String questId) {
        QuestGpsNavigationService.TrackingState state = navigationService.trackingState(player);
        if (state.questId().equalsIgnoreCase(safe(questId))) {
            clearTrackInternal(player, true, false);
        }
    }

    private void refreshByProfile(PlayerProfile profile) {
        if (profile == null) {
            return;
        }
        Player player = profile.getPlayer();
        if (player != null && player.isOnline()) {
            refreshViewer(player);
        }
    }

    private void refreshViewer(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        QuestGpsViewState state = viewStates.get(player.getUniqueId());
        if (state == null) {
            return;
        }
        PlayerProfile profile = loadProfile(player, false);
        if (profile == null) {
            return;
        }
        syncMenu(player, profile, state, false);
        if (navigationService.trackingState(player).active()) {
            syncGuide(player, false);
        }
    }

    private void syncGuide(Player player, boolean initPacket) {
        if (guideUiIds.isEmpty() || player == null || !player.isOnline()) {
            return;
        }
        QuestGpsNavigationService.TrackingState trackState = navigationService.trackingState(player);
        if (!trackState.active()) {
            closeGuide(player);
            return;
        }
        if (initPacket) {
            bridge.openUiAll(player, guideUiIds);
        }
        bridge.sendPacketToAll(player, guideUiIds, initPacket ? "init" : "update", buildGuidePayload(player, trackState));
    }

    private void closeGuide(Player player) {
        if (guideUiIds.isEmpty() || player == null || !player.isOnline()) {
            return;
        }
        bridge.closeUiAll(player, guideUiIds);
    }

    private Map<String, Object> buildGuidePayload(Player player, QuestGpsNavigationService.TrackingState trackState) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("packetId", configuration.client().packetId());
        payload.put("active", trackState.active());
        payload.put("questName", trackState.label());

        // 收集任务目标
        PlayerProfile profile = loadProfile(player, false);
        List<Map<String, Object>> taskRows = new ArrayList<>();
        int completedCount = 0;
        int totalCount = 0;
        if (profile != null) {
            QuestGpsModuleConfiguration.QuestDefinition definition = configuration.quest(trackState.questId());
            if (definition != null) {
                Template template = ChemdahAPI.INSTANCE.getQuestTemplate(definition.id());
                if (template != null) {
                    QuestGpsPage page = QuestGpsPage.ACTIVE;
                    List<QuestGpsSnapshotBuilder.TaskDescriptor> tasks = buildTaskDescriptors(profile, template, definition, page);
                    totalCount = tasks.size();
                    int displayLimit = 3;
                    int shown = 0;
                    for (QuestGpsSnapshotBuilder.TaskDescriptor task : tasks) {
                        if (task.completed()) {
                            completedCount++;
                        }
                        if (shown < displayLimit) {
                            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                            row.put("id", task.taskId());
                            row.put("text", task.text());
                            row.put("completed", task.completed());
                            row.put("status", task.statusText());
                            taskRows.add(row);
                            shown++;
                        }
                    }
                }
            }
        }
        payload.put("completedCount", completedCount);
        payload.put("totalCount", totalCount);
        payload.put("progressText", completedCount + "/" + totalCount);
        payload.put("tasks", taskRows);
        payload.put("taskCount", taskRows.size());

        // 导航坐标
        QuestGpsNavigationService.NavigationPoint point = navigationService.trackingPoint(player).orElse(null);
        payload.put("hasNav", point != null);
        payload.put("navWorld", point != null ? point.world() : "");
        payload.put("navX", point != null ? (int) point.x() : 0);
        payload.put("navY", point != null ? (int) point.y() : 0);
        payload.put("navZ", point != null ? (int) point.z() : 0);
        return payload;
    }

    private void syncMenu(Player player, PlayerProfile profile, QuestGpsViewState state, boolean initPacket) {
        List<QuestGpsSnapshotBuilder.QuestDescriptor> descriptors = collectDescriptors(profile);
        if (isCategoryLocked(player, state.category())) {
            state.setCategory(QuestGpsCategory.MAINLINE);
        }
        QuestGpsSnapshotBuilder.BuildResult snapshot = snapshotBuilder.build(
            descriptors,
            state.category(),
            state.page(),
            state.selectedQuestId(),
            navigationService.trackingState(player)
        );
        state.setCategory(snapshot.category());
        state.setPage(snapshot.page());
        state.setSelectedQuestId(snapshot.detail().questId());
        bridge.sendPacketToAll(player, menuUiIds, initPacket ? "init" : "update", buildMenuPayload(snapshot));
    }

    private Map<String, Object> buildMenuPayload(QuestGpsSnapshotBuilder.BuildResult snapshot) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("packetId", configuration.client().packetId());
        payload.put("categoryId", snapshot.category().id());
        payload.put("pageId", snapshot.page().id());
        payload.put("categoryName", snapshot.category().displayName());
        payload.put("pageName", snapshot.page().displayName());
        payload.put("availableCount", snapshot.counts().getOrDefault(QuestGpsPage.AVAILABLE, 0));
        payload.put("activeCount", snapshot.counts().getOrDefault(QuestGpsPage.ACTIVE, 0));
        payload.put("completedCount", snapshot.counts().getOrDefault(QuestGpsPage.COMPLETED, 0));
        payload.put("questCount", snapshot.questRows().size());
        payload.put("navigationReady", navigationService.runtimeReady());

        List<QuestGpsCategory> sortedCategories = QuestGpsCategory.sorted(configuration.categoryRegistry());
        LinkedHashMap<String, Object> categoryRows = new LinkedHashMap<>();
        for (int index = 0; index < sortedCategories.size(); index++) {
            QuestGpsCategory cat = sortedCategories.get(index);
            categoryRows.put(
                QuestGpsPayloadSupport.rowKey('c', index),
                QuestGpsPayloadSupport.flatRow(
                    "id", cat.id(),
                    "name", cat.displayName(),
                    "selected", cat.equals(snapshot.category())
                )
            );
        }
        payload.put("categories", categoryRows);
        payload.put("categoryCount", sortedCategories.size());

        LinkedHashMap<String, Object> questRows = new LinkedHashMap<>();
        for (int index = 0; index < snapshot.questRows().size(); index++) {
            QuestGpsSnapshotBuilder.ListRow row = snapshot.questRows().get(index);
            questRows.put(
                QuestGpsPayloadSupport.rowKey('q', index),
                QuestGpsPayloadSupport.flatRow(
                    "id", row.questId(),
                    "name", row.displayName(),
                    "summary", row.summaryText(),
                    "state", row.stateText(),
                    "trackable", row.trackAvailable(),
                    "selected", row.selected()
                )
            );
        }
        payload.put("questRows", questRows);

        QuestGpsSnapshotBuilder.DetailSnapshot detail = snapshot.detail();
        payload.put("selectedQuestId", detail.questId());
        payload.put("selectedQuestName", detail.displayName());
        payload.put("selectedQuestState", detail.stateText());
        payload.put("selectedQuestPath", detail.path());
        LinkedHashMap<String, Object> descRows = new LinkedHashMap<>();
        for (int index = 0; index < detail.descriptionLines().size(); index++) {
            descRows.put(
                QuestGpsPayloadSupport.rowKey('d', index),
                QuestGpsPayloadSupport.flatRow("text", detail.descriptionLines().get(index))
            );
        }
        payload.put("selectedQuestDescription", descRows);
        payload.put("selectedQuestDescriptionCount", detail.descriptionLines().size());
        payload.put("selectedQuestDescriptionText", String.join("\n", detail.descriptionLines()));
        payload.put("trackSummary", detail.trackingText());
        payload.put("canAccept", detail.canAccept());
        payload.put("canAbandon", detail.canAbandon());
        payload.put("canTrackQuest", detail.canTrackQuest());
        payload.put("canTrackTask", detail.canTrackTask());
        payload.put("canClearTrack", detail.canClearTrack());
        payload.put("questTracked", detail.questTracked());

        LinkedHashMap<String, Object> taskRows = new LinkedHashMap<>();
        for (int index = 0; index < detail.taskRows().size(); index++) {
            QuestGpsSnapshotBuilder.TaskRow row = detail.taskRows().get(index);
            taskRows.put(
                QuestGpsPayloadSupport.rowKey('t', index),
                QuestGpsPayloadSupport.flatRow(
                    "id", row.taskId(),
                    "text", row.text(),
                    "status", row.statusText(),
                    "completed", row.completed(),
                    "trackable", row.trackAvailable(),
                    "tracked", row.tracked()
                )
            );
        }
        payload.put("taskRows", taskRows);
        payload.put("taskCount", detail.taskRows().size());

        LinkedHashMap<String, Object> rewardRows = new LinkedHashMap<>();
        for (int index = 0; index < detail.rewardRows().size(); index++) {
            QuestGpsSnapshotBuilder.RewardRow row = detail.rewardRows().get(index);
            rewardRows.put(
                QuestGpsPayloadSupport.rowKey('r', index),
                QuestGpsPayloadSupport.flatRow(
                    "id", row.rewardId(),
                    "title", row.title(),
                    "description", row.description(),
                    "type", row.type(),
                    "amount", row.amount(),
                    "itemJson", row.itemJson(),
                    "material", row.materialId()
                )
            );
        }
        payload.put("rewardRows", rewardRows);
        payload.put("rewardCount", detail.rewardRows().size());
        return payload;
    }

    private List<QuestGpsSnapshotBuilder.QuestDescriptor> collectDescriptors(PlayerProfile profile) {
        List<QuestGpsSnapshotBuilder.QuestDescriptor> descriptors = new ArrayList<>();
        for (QuestGpsModuleConfiguration.QuestDefinition definition : configuration.orderedQuests()) {
            Template template = ChemdahAPI.INSTANCE.getQuestTemplate(definition.id());
            if (template == null) {
                continue;
            }
            Quest activeQuest = profile.getQuestById(template.getId(), false);
            boolean active = activeQuest != null && !activeQuest.isCompleted();
            boolean completed = !active && profile.isQuestCompleted(template);
            QuestGpsPage page = active ? QuestGpsPage.ACTIVE : (completed ? QuestGpsPage.COMPLETED : QuestGpsPage.AVAILABLE);
            List<QuestGpsSnapshotBuilder.TaskDescriptor> taskDescriptors = buildTaskDescriptors(profile, template, definition, page);
            boolean canAccept = page == QuestGpsPage.AVAILABLE
                && hasCompletedRequiredMainline(profile, definition.requiredMainline())
                && !isCategoryLocked(profile.getPlayer(), definition.category());
            descriptors.add(
                new QuestGpsSnapshotBuilder.QuestDescriptor(
                    template.getId(),
                    definition.category(),
                    page,
                    displayQuestName(template, definition),
                    buildQuestSummary(page, taskDescriptors),
                    page.displayName(),
                    safe(template.getPath()),
                    descriptionLines(definition),
                    taskDescriptors,
                    rewardResolver.resolve(configuration, definition.category(), template.getId()),
                    canAccept,
                    page == QuestGpsPage.ACTIVE && definition.allowAbandon(),
                    page == QuestGpsPage.ACTIVE && navigationService.hasQuestPoint(template.getId(), prioritizedTaskIds(taskDescriptors)),
                    definition.sortOrder()
                )
            );
        }
        return List.copyOf(descriptors);
    }

    private List<QuestGpsSnapshotBuilder.TaskDescriptor> buildTaskDescriptors(
        PlayerProfile profile,
        Template template,
        QuestGpsModuleConfiguration.QuestDefinition definition,
        QuestGpsPage page
    ) {
        List<Task> tasks = new ArrayList<>(template.getTaskMap().values());
        tasks.sort(
            Comparator
                .comparingInt((Task task) -> taskSortOrder(definition, task.getId()))
                .thenComparing(Task::getId, String.CASE_INSENSITIVE_ORDER)
        );

        List<QuestGpsSnapshotBuilder.TaskDescriptor> descriptors = new ArrayList<>(tasks.size());
        for (Task task : tasks) {
            boolean completed = switch (page) {
                case ACTIVE -> task.isCompleted(profile);
                case COMPLETED -> profile.getQuestTaskCompleteDate(template.getId(), task.getId()) > 0L || task.isCompleted(profile);
                case AVAILABLE -> false;
            };
            descriptors.add(
                new QuestGpsSnapshotBuilder.TaskDescriptor(
                    task.getId(),
                    taskDisplayText(definition, task),
                    completed ? "已完成" : (page == QuestGpsPage.AVAILABLE ? "未开始" : "进行中"),
                    completed,
                    page == QuestGpsPage.ACTIVE && navigationService.hasTaskPoint(template.getId(), task.getId()),
                    taskSortOrder(definition, task.getId())
                )
            );
        }
        return List.copyOf(descriptors);
    }

    private boolean hasCompletedRequiredMainline(PlayerProfile profile, List<String> questIds) {
        if (profile == null || questIds == null || questIds.isEmpty()) {
            return true;
        }
        for (String questId : questIds) {
            Template required = ChemdahAPI.INSTANCE.getQuestTemplate(questId);
            if (required == null || !profile.isQuestCompleted(required)) {
                return false;
            }
        }
        return true;
    }

    public boolean mainlineGateSatisfied(Player player) {
        PlayerProfile profile = loadProfile(player, false);
        return profile != null && hasCompletedRequiredMainline(profile, configuration.gate().requiredMainlineQuestIds());
    }

    public boolean eventRuleLocked(Player player, String ruleId) {
        return configuration.gate().blockedEventRuleIds().contains(safe(ruleId)) && !mainlineGateSatisfied(player);
    }

    public boolean moduleEntryLocked(Player player, String moduleEntryId) {
        return configuration.gate().blockedModuleEntries().contains(safe(moduleEntryId)) && !mainlineGateSatisfied(player);
    }

    private boolean isCategoryLocked(Player player, QuestGpsCategory category) {
        return player != null
            && category != null
            && configuration.gate().blockedCategories().contains(category.id())
            && !mainlineGateSatisfied(player);
    }

    private void denyGate(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        player.sendMessage(PREFIX + ChatColor.translateAlternateColorCodes('&', configuration.gate().denyMessage()));
        ChatCardSendable chatCard = chatCardSendableProvider.get();
        if (chatCard != null && !configuration.gate().denyChatCard().isBlank()) {
            chatCard.sendChatCard(player, configuration.gate().denyChatCard(), Map.of("reason", "mainline_gate"));
        }
        SubtitlePlayable subtitle = subtitlePlayableProvider.get();
        if (subtitle != null && !configuration.gate().denySubtitle().isBlank()) {
            subtitle.playGroup(player, configuration.gate().denySubtitle());
        }
    }

    private String displayQuestName(Template template, QuestGpsModuleConfiguration.QuestDefinition definition) {
        if (!definition.displayNameOverride().isBlank()) {
            return definition.displayNameOverride();
        }
        String configured = readConfigString(template, "name", "");
        return configured.isBlank() ? template.getId() : configured;
    }

    private List<String> descriptionLines(QuestGpsModuleConfiguration.QuestDefinition definition) {
        return definition.description().isEmpty() ? List.of("该任务未配置 QuestGPS 描述。") : definition.description();
    }

    private String taskDisplayText(QuestGpsModuleConfiguration.QuestDefinition definition, Task task) {
        QuestGpsModuleConfiguration.TaskDefinition taskDefinition = definition.task(task.getId());
        if (taskDefinition != null && !taskDefinition.displayText().isBlank()) {
            return taskDefinition.displayText();
        }
        String configured = readConfigString(task, "name", "");
        return configured.isBlank() ? task.getId() : configured;
    }

    private int taskSortOrder(QuestGpsModuleConfiguration.QuestDefinition definition, String taskId) {
        QuestGpsModuleConfiguration.TaskDefinition taskDefinition = definition.task(taskId);
        return taskDefinition == null ? 0 : taskDefinition.sortOrder();
    }

    private String buildQuestSummary(QuestGpsPage page, List<QuestGpsSnapshotBuilder.TaskDescriptor> tasks) {
        if (page == QuestGpsPage.COMPLETED) {
            return "任务已完成";
        }
        if (page == QuestGpsPage.AVAILABLE) {
            return "等待接取";
        }
        int completedCount = 0;
        for (QuestGpsSnapshotBuilder.TaskDescriptor task : tasks) {
            if (task.completed()) {
                completedCount++;
            }
        }
        return completedCount + "/" + tasks.size() + " 目标完成";
    }

    private String resolveQuestId(Player player, String questId) {
        String normalized = safe(questId);
        if (!normalized.isBlank()) {
            return normalized;
        }
        if (player == null) {
            return "";
        }
        QuestGpsViewState state = viewStates.get(player.getUniqueId());
        return state == null ? "" : safe(state.selectedQuestId());
    }

    private List<String> prioritizedTaskIds(List<QuestGpsSnapshotBuilder.TaskDescriptor> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }
        List<String> prioritized = new ArrayList<>(tasks.size());
        for (QuestGpsSnapshotBuilder.TaskDescriptor task : tasks) {
            if (!task.completed()) {
                prioritized.add(task.taskId());
            }
        }
        for (QuestGpsSnapshotBuilder.TaskDescriptor task : tasks) {
            if (task.completed()) {
                prioritized.add(task.taskId());
            }
        }
        return List.copyOf(prioritized);
    }

    private QuestGpsSnapshotBuilder.QuestDescriptor findDescriptor(
        List<QuestGpsSnapshotBuilder.QuestDescriptor> descriptors,
        String questId
    ) {
        if (descriptors == null || questId == null || questId.isBlank()) {
            return null;
        }
        for (QuestGpsSnapshotBuilder.QuestDescriptor descriptor : descriptors) {
            if (descriptor.questId().equalsIgnoreCase(questId.trim())) {
                return descriptor;
            }
        }
        return null;
    }

    private QuestGpsSnapshotBuilder.TaskDescriptor findTask(
        List<QuestGpsSnapshotBuilder.TaskDescriptor> tasks,
        String taskId
    ) {
        if (tasks == null || taskId == null || taskId.isBlank()) {
            return null;
        }
        for (QuestGpsSnapshotBuilder.TaskDescriptor task : tasks) {
            if (task.taskId().equalsIgnoreCase(taskId.trim())) {
                return task;
            }
        }
        return null;
    }

    private PlayerProfile loadProfile(Player player, boolean notify) {
        if (player == null || !player.isOnline()) {
            return null;
        }
        if (!ChemdahAPI.INSTANCE.isChemdahProfileLoaded(player)) {
            if (notify) {
                player.sendMessage(PREFIX + ChatColor.RED + "Chemdah 玩家档案尚未加载。");
            }
            return null;
        }
        PlayerProfile profile = ChemdahAPI.INSTANCE.getChemdahProfile(player);
        if (profile == null && notify) {
            player.sendMessage(PREFIX + ChatColor.RED + "无法读取 Chemdah 玩家档案。");
        }
        return profile;
    }

    private void fireQuestHooks(Player player, QuestGpsModuleConfiguration.QuestDefinition definition, Lifecycle lifecycle) {
        List<String> signals = switch (lifecycle) {
            case TRIGGERED -> definition.hooks().triggered();
            case ACCEPTED -> definition.hooks().accepted();
            case ABANDONED -> definition.hooks().abandoned();
            case COMPLETED -> definition.hooks().completed();
            case TRACK_CHANGED -> definition.hooks().trackChanged();
        };
        for (String signal : signals) {
            if (signalDispatcher != null) {
                signalDispatcher.accept(signal, player);
            }
        }
    }

    private String readConfigString(Object holder, String path, String fallback) {
        Object value = readConfigValue(holder, path);
        if (value == null) {
            return fallback;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? fallback : normalized;
    }

    private Object readConfigValue(Object holder, String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        Object config = getConfigObject(holder);
        if (config == null) {
            return null;
        }

        Object value = invokeConfigMethod(config, "get", path);
        if (value != null) {
            return value;
        }
        value = invokeConfigMethod(config, "getString", path);
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return stringValue;
        }
        return value;
    }

    private Object getConfigObject(Object holder) {
        if (holder == null) {
            return null;
        }
        try {
            Method method = holder.getClass().getMethod("getConfig");
            return method.invoke(holder);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private Object invokeConfigMethod(Object config, String methodName, String path) {
        try {
            Method method = config.getClass().getMethod(methodName, String.class);
            return method.invoke(config, path);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @SuppressWarnings("unused")
    private List<String> normalizeLines(Object value) {
        if (value == null) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object entry : iterable) {
                lines.addAll(normalizeLines(entry));
            }
            return List.copyOf(lines);
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            for (int index = 0; index < length; index++) {
                lines.addAll(normalizeLines(Array.get(value, index)));
            }
            return List.copyOf(lines);
        }
        String raw = String.valueOf(value);
        for (String line : raw.split("\\R")) {
            String normalized = line.trim();
            if (!normalized.isEmpty()) {
                lines.add(normalized);
            }
        }
        return List.copyOf(lines);
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private enum Lifecycle {
        TRIGGERED("triggered"),
        ACCEPTED("accepted"),
        ABANDONED("abandoned"),
        COMPLETED("completed"),
        TRACK_CHANGED("track-changed");

        private final String configValue;

        Lifecycle(String configValue) {
            this.configValue = configValue;
        }
    }
}
