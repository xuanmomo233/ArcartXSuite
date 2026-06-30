package xuanmo.arcartxsuite.questgps.service;

import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.core.PlayerProfile;
import ink.ptms.chemdah.core.quest.AcceptResult;
import ink.ptms.chemdah.core.quest.Quest;
import ink.ptms.chemdah.core.quest.Template;
import java.lang.reflect.Method;
import java.util.ArrayList;
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
import xuanmo.arcartxsuite.questgps.chemdah.ChemdahCategoryResolver;
import xuanmo.arcartxsuite.questgps.chemdah.ChemdahIntegrationBootstrap;
import xuanmo.arcartxsuite.questgps.chemdah.ChemdahMetadataReader;
import xuanmo.arcartxsuite.questgps.chemdah.ChemdahQuestDiscovery;
import xuanmo.arcartxsuite.questgps.chemdah.ChemdahRewardReader;
import xuanmo.arcartxsuite.questgps.chemdah.ChemdahTrackerBridge;
import xuanmo.arcartxsuite.questgps.chemdah.QuestGpsOverlayValidator;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;
import xuanmo.arcartxsuite.api.capability.TitleConfigQueryable;
import xuanmo.arcartxsuite.module.AxsLog;

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
    private final HookDispatcher hookDispatcher;
    private final QuestGpsModuleConfiguration configuration;
    private final PacketBridgeAPI bridge;
    private final java.util.List<String> menuUiIds;
    private final java.util.List<String> guideUiIds;
    private final QuestGpsRewardPreviewResolver rewardResolver;
    private final ChemdahIntegrationBootstrap chemdahBootstrap;
    private final ChemdahCategoryResolver categoryResolver;
    private final ChemdahQuestDiscovery questDiscovery;
    private final QuestGpsPresentationService presentationService;
    private final QuestGpsOverlayValidator overlayValidator;
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
        HookDispatcher hookDispatcher,
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
        this.hookDispatcher = hookDispatcher;
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
        ChemdahMetadataReader metadataReader = new ChemdahMetadataReader(AxsLog.logger());
        ChemdahRewardReader rewardReader = new ChemdahRewardReader(AxsLog.logger(), rewardResolver);
        ChemdahTrackerBridge trackerBridge = new ChemdahTrackerBridge(AxsLog.logger());
        this.chemdahBootstrap = new ChemdahIntegrationBootstrap(
            AxsLog.logger(),
            configuration,
            metadataReader,
            rewardReader,
            trackerBridge
        );
        this.navigationService = new QuestGpsNavigationService(plugin, configuration, waypointBridge, npcBridge, trackerBridge);
        this.categoryResolver = new ChemdahCategoryResolver(
            AxsLog.logger(),
            configuration.categoryDefaults(),
            configuration.categoryRegistry()
        );
        this.questDiscovery = new ChemdahQuestDiscovery(AxsLog.logger(), categoryResolver);
        this.presentationService = new QuestGpsPresentationService(
            configuration,
            questDiscovery,
            categoryResolver,
            metadataReader,
            rewardReader,
            navigationService
        );
        this.overlayValidator = new QuestGpsOverlayValidator(AxsLog.logger(), configuration, categoryResolver);
        this.uiPacketHandler = new QuestGpsUiPacketHandler(this, configuration.client().packetId());
    }

    public void start() {
        chemdahBootstrap.initialize();
        overlayValidator.validate();
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
        Template template = ChemdahAPI.INSTANCE.getQuestTemplate(definition.id());
        if (template == null) {
            player.sendMessage(PREFIX + ChatColor.RED + "未找到 Chemdah 任务: " + definition.id());
            return;
        }
        QuestGpsCategory effectiveCategory = presentationService.effectiveCategory(template, definition);
        if (effectiveCategory == null) {
            player.sendMessage(PREFIX + ChatColor.RED + "任务分类未在 categories 注册，无法接取: " + definition.id());
            return;
        }
        if (isCategoryLocked(player, effectiveCategory) || !presentationService.hasCompletedRequiredMainline(profile, definition.requiredMainline())) {
            denyGate(player);
            return;
        }

        template.acceptTo(profile).whenComplete((result, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (throwable != null) {
                player.sendMessage(PREFIX + ChatColor.RED + "接取任务失败，请检查控制台。");
                AxsLog.logger().log(Level.WARNING, "QuestGPS: 接取任务失败: " + definition.id(), throwable);
                refreshViewer(player);
                return;
            }
            if (result != null && result.getType() == AcceptResult.Type.SUCCESSFUL) {
                QuestGpsViewState state = viewStates.computeIfAbsent(player.getUniqueId(), ignored -> new QuestGpsViewState());
                state.setPage(QuestGpsPage.ACTIVE);
                state.setSelectedQuestId(definition.id());
                player.sendMessage(PREFIX + ChatColor.GREEN + "已接取任务: " + chemdahBootstrap.metadataReader().questDisplayName(template, definition, configuration.presentation()));
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
                    AxsLog.logger().log(Level.WARNING, "QuestGPS 放弃任务失败: " + definition.id(), throwable);
                }
                refreshViewer(player);
                return;
            }
            clearTrackForQuest(player, definition.id());
            QuestGpsViewState state = viewStates.computeIfAbsent(player.getUniqueId(), ignored -> new QuestGpsViewState());
            state.setPage(QuestGpsPage.AVAILABLE);
            state.setSelectedQuestId(definition.id());
            player.sendMessage(PREFIX + ChatColor.YELLOW + "已放弃任务: " + chemdahBootstrap.metadataReader().questDisplayName(template, definition, configuration.presentation()));
            refreshViewer(player);
        }));
    }

    public void trackQuest(Player player, String questId) {
        PlayerProfile profile = loadProfile(player, true);
        if (profile == null) {
            return;
        }
        String targetQuestId = resolveQuestId(player, questId);
        List<QuestGpsSnapshotBuilder.QuestDescriptor> descriptors = presentationService.collectDescriptors(profile, this::isCategoryLocked);
        QuestGpsSnapshotBuilder.QuestDescriptor descriptor = presentationService.findDescriptor(descriptors, targetQuestId);
        if (descriptor == null || !descriptor.questTrackAvailable()) {
            player.sendMessage(PREFIX + ChatColor.RED + "该任务当前没有可用导航点。");
            return;
        }
        boolean success = navigationService.trackQuest(player, descriptor.questId(), descriptor.displayName(), presentationService.prioritizedTaskIds(descriptor.tasks()));
        if (!success) {
            player.sendMessage(PREFIX + ChatColor.RED + "任务导航创建失败。");
            return;
        }
        afterTrackChanged(player, descriptor.questId());
        player.sendMessage(PREFIX + ChatColor.GREEN + "已设置任务导航: " + ChatColor.translateAlternateColorCodes('&', descriptor.displayName()));
        refreshViewer(player);
    }

    public void trackTask(Player player, String questId, String taskId) {
        PlayerProfile profile = loadProfile(player, true);
        if (profile == null) {
            return;
        }
        String targetQuestId = resolveQuestId(player, questId);
        List<QuestGpsSnapshotBuilder.QuestDescriptor> descriptors = presentationService.collectDescriptors(profile, this::isCategoryLocked);
        QuestGpsSnapshotBuilder.QuestDescriptor descriptor = presentationService.findDescriptor(descriptors, targetQuestId);
        if (descriptor == null) {
            player.sendMessage(PREFIX + ChatColor.RED + "未找到任务。");
            return;
        }
        QuestGpsSnapshotBuilder.TaskDescriptor taskDescriptor = presentationService.findTask(descriptor.tasks(), taskId);
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
        Template template = ChemdahAPI.INSTANCE.getQuestTemplate(definition.id());
        state.setCategory(presentationService.effectiveCategory(template, definition));
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
            AxsLog.logger().warning("QuestGPS: 注册 Chemdah 重载事件失败: " + exception.getMessage());
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
            AxsLog.logger().warning("QuestGPS: 注册 Chemdah 任务事件失败(" + className + "): " + exception.getMessage());
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
                AxsLog.logger().fine(
                    "QuestGPS: 忽略非匹配 Chemdah 任务事件: expected="
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
            if (player != null && clearTracking && navigationService.removeOnFinish()) {
                clearTrackForQuest(player, questId);
            }
            QuestGpsModuleConfiguration.QuestDefinition definition = configuration.quest(questId);
            if (player != null && definition != null) {
                fireQuestHooks(player, definition, lifecycle);
            }
            refreshByProfile(profile);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            AxsLog.logger().warning("QuestGPS: 处理 Chemdah 任务事件失败: " + exception.getMessage());
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
        bridge.sendPacketToAll(player, guideUiIds, initPacket ? "init" : "update", QuestGpsGuidePacketFactory.build(
            configuration,
            player,
            trackState,
            presentationService,
            navigationService
        ));
    }

    private void closeGuide(Player player) {
        if (guideUiIds.isEmpty() || player == null || !player.isOnline()) {
            return;
        }
        bridge.closeUiAll(player, guideUiIds);
    }

    private void syncMenu(Player player, PlayerProfile profile, QuestGpsViewState state, boolean initPacket) {
        List<QuestGpsSnapshotBuilder.QuestDescriptor> descriptors = presentationService.collectDescriptors(profile, this::isCategoryLocked);
        QuestGpsCategory viewCategory = resolveViewCategory(player, state.category());
        state.setCategory(viewCategory);
        QuestGpsSnapshotBuilder.BuildResult snapshot = snapshotBuilder.build(
            descriptors,
            viewCategory,
            state.page(),
            state.selectedQuestId(),
            navigationService.trackingState(player)
        );
        state.setCategory(snapshot.category());
        state.setPage(snapshot.page());
        state.setSelectedQuestId(snapshot.detail().questId());
        bridge.sendPacketToAll(
            player,
            menuUiIds,
            initPacket ? "init" : "update",
            QuestGpsMenuPacketFactory.build(configuration, snapshot, navigationService.runtimeReady())
        );
    }

    public boolean mainlineGateSatisfied(Player player) {
        PlayerProfile profile = loadProfile(player, false);
        return profile != null && presentationService.hasCompletedRequiredMainline(profile, configuration.gate().requiredMainlineQuestIds());
    }

    public boolean eventRuleLocked(Player player, String ruleId) {
        return configuration.gate().blockedEventRuleIds().contains(safe(ruleId)) && !mainlineGateSatisfied(player);
    }

    /** 检查指定模块入口是否被主线门禁锁定（供其他模块 capability 查询）。 */
    public boolean moduleEntryLocked(Player player, String moduleEntryId) {
        return configuration.gate().blockedModuleEntries().contains(safe(moduleEntryId)) && !mainlineGateSatisfied(player);
    }

    private QuestGpsCategory resolveViewCategory(Player player, QuestGpsCategory preferred) {
        Map<String, QuestGpsCategory> registry = configuration.categoryRegistry();
        if (registry.isEmpty()) {
            return null;
        }
        if (preferred != null
            && registry.containsKey(preferred.id())
            && !isCategoryLocked(player, preferred)) {
            return preferred;
        }
        for (QuestGpsCategory category : QuestGpsCategory.sorted(registry)) {
            if (!isCategoryLocked(player, category)) {
                return category;
            }
        }
        return QuestGpsCategory.firstOrNull(registry);
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
            if (hookDispatcher != null) {
                hookDispatcher.dispatch(signal, player, definition.id());
            }
        }
    }

    public interface HookDispatcher {
        void dispatch(String signal, Player player, String questId);
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
