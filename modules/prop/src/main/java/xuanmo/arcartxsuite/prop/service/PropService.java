package xuanmo.arcartxsuite.prop.service;

import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.api.attribute.AttributeBridgeRegistry;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.api.bridge.PropBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PropPlayerHandle;
import xuanmo.arcartxsuite.api.bridge.PropSlotUpdates;
import xuanmo.arcartxsuite.api.condition.ScriptCondition;
import xuanmo.arcartxsuite.api.condition.ScriptConditionServices;
import xuanmo.arcartxsuite.api.modattr.ModAttributeService;
import xuanmo.arcartxsuite.api.placeholder.PlaceholderResolverAPI;
import xuanmo.arcartxsuite.api.script.AriaBridge;
import xuanmo.arcartxsuite.prop.config.PropAmount;
import xuanmo.arcartxsuite.prop.config.PropAutoTriggerConfiguration;
import xuanmo.arcartxsuite.prop.config.PropAutoTriggerDefinition;
import xuanmo.arcartxsuite.prop.config.PropDefinition;
import xuanmo.arcartxsuite.prop.config.PropEffects;
import xuanmo.arcartxsuite.prop.config.PropKeyBindingDefinition;
import xuanmo.arcartxsuite.prop.config.PropKeyMappingConfiguration;
import xuanmo.arcartxsuite.prop.config.PropLanguageConfiguration;
import xuanmo.arcartxsuite.prop.config.PropModuleConfiguration;
import java.util.logging.Logger;

/**
 * Prop 模块核心服务。
 * <p>
 * 实现 Bukkit {@link Listener}，负责道具的按键绑定注册、手持右键与按键使用处理、
 * 冷却管理、槽位同步与道具绑定。效果定义与临时属性分别由
 * {@link PropEffects} 和 {@link PropTimedAttributes} 管理。
 */
public final class PropService implements Listener {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final PropModuleConfiguration configuration;
    private final PropBridgeAPI bridge;
    private final PropKeyMappingConfiguration keyMappingConfiguration;
    private final PropLanguageConfiguration languageConfiguration;
    private final Map<String, PropDefinition> props;
    private final PropTimedAttributes timedAttributes;
    private final PropQuickSlotUse quickSlotUse;
    private final PropKeyPressQueue keyPresses;
    private Supplier<EventBusCapability> eventBusProvider;
    private final MessageProvider messages;
    private final xuanmo.arcartxsuite.api.bridge.VanillaItemNameBridge vanillaItemNameBridge;
    private final Set<String> registeredBindingIds = new LinkedHashSet<>();
    private final Map<UUID, Map<String, Long>> autoCheckTimestamps = new HashMap<>();
    private final ConcurrentHashMap<UUID, AutoSlots> observedSlots = new ConcurrentHashMap<>();
    private final List<String> configuredSlotIds;
    private final Map<String, Integer> slotIndices;
    private BukkitTask autoTriggerTask;
    private Runnable unobserveSlots;
    private long autoTick;
    private volatile boolean active;

    private static final class AutoSlots {
        private final Player owner;
        private final PropDefinition[] candidates;
        private final BitSet activeCandidates = new BitSet();
        private volatile BitSet dirty;
        private long fullCheckAt;
        private long nextCandidateAt = Long.MAX_VALUE;

        private AutoSlots(Player owner, int slots) {
            this.owner = owner;
            this.candidates = new PropDefinition[slots];
        }

        private boolean checkDue(long now) {
            return now >= fullCheckAt || now >= nextCandidateAt || dirty != null;
        }

        private void setCandidate(int index, PropDefinition definition) {
            candidates[index] = definition;
            activeCandidates.set(index, definition != null);
        }

        private synchronized void mark(int index) {
            if (dirty == null) dirty = new BitSet();
            dirty.set(index);
        }

        private synchronized BitSet take() {
            BitSet changed = dirty;
            dirty = null;
            return changed;
        }
    }

    /**
     * 构造 Prop 核心服务。
     *
     * @param plugin                    宿主插件实例
     * @param logger                    日志记录器
     * @param configuration             模块配置
     * @param bridge                    Prop 桥接 API
     * @param keyMappingConfiguration   按键映射配置
     * @param languageConfiguration     语言配置
     * @param definitionsByNormalizedId 以小写归一化 ID 为键的道具定义 Map
     * @param attributeBridge           属性桥接注册表
     * @param modAttributes             本体的模组属性服务
     * @param aria                      Aria 求值桥接
     * @param placeholders              PlaceholderAPI 解析桥接
     * @param messages                  消息提供者
     * @param vanillaItemNameBridge     原版物品名称桥接
     */
    public PropService(
        JavaPlugin plugin,
        Logger logger,
        PropModuleConfiguration configuration,
        PropBridgeAPI bridge,
        PropKeyMappingConfiguration keyMappingConfiguration,
        PropLanguageConfiguration languageConfiguration,
        Map<String, PropDefinition> definitionsByNormalizedId,
        AttributeBridgeRegistry attributeBridge,
        ModAttributeService modAttributes,
        AriaBridge aria,
        PlaceholderResolverAPI placeholders,
        MessageProvider messages,
        xuanmo.arcartxsuite.api.bridge.VanillaItemNameBridge vanillaItemNameBridge
    ) {
        this.plugin = plugin;
        this.logger = logger;
        this.configuration = configuration;
        this.bridge = bridge;
        this.keyMappingConfiguration = keyMappingConfiguration;
        this.languageConfiguration = languageConfiguration;
        this.messages = messages;
        this.vanillaItemNameBridge = vanillaItemNameBridge;
        this.props = definitionsByNormalizedId;
        this.timedAttributes = new PropTimedAttributes(attributeBridge, modAttributes, aria, placeholders, configuration.mythicLib(),
            (task, ticks) -> Bukkit.getScheduler().runTaskLater(plugin, task, ticks), logger);
        this.quickSlotUse = new PropQuickSlotUse(plugin, bridge, messages, this::canUse, logger);
        this.keyPresses = new PropKeyPressQueue(task -> Bukkit.getScheduler().runTask(plugin, task), this::handleQueuedPress);
        this.configuredSlotIds = keyMappingConfiguration.bindings().values().stream()
            .filter(binding -> binding.mode() == PropKeyBindingDefinition.Mode.PROP)
            .map(PropKeyBindingDefinition::slotId).distinct().toList();
        var indices = new HashMap<String, Integer>();
        for (int i = 0; i < configuredSlotIds.size(); i++) indices.put(configuredSlotIds.get(i), i);
        this.slotIndices = Map.copyOf(indices);
    }

    /**
     * 设置事件总线供应商，用于发布道具使用事件。
     *
     * @param eventBusProvider 事件总线能力供应商
     */
    public void setEventBusProvider(Supplier<EventBusCapability> eventBusProvider) {
        this.eventBusProvider = eventBusProvider;
    }

    /**
     * 启动服务：注册事件与按键，启动槽位同步及自动触发。
     */
    public void start() {
        active = true;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        registerBindings();
        syncOnlinePlayers();
        startAutoTrigger();
    }

    /**
     * 关闭服务：注销事件监听、取消按键绑定、停止自动触发任务、关闭各属性子服务。
     */
    public void shutdown() {
        active = false;
        keyPresses.close();
        HandlerList.unregisterAll(this);
        unregisterBindings();
        if (autoTriggerTask != null) {
            autoTriggerTask.cancel();
            autoTriggerTask = null;
        }
        if (unobserveSlots != null) {
            unobserveSlots.run();
            unobserveSlots = null;
        }
        observedSlots.clear();
        autoCheckTimestamps.clear();
        quickSlotUse.shutdown();
        timedAttributes.shutdown();
    }

    /**
     * 获取已加载的道具定义数量。
     *
     * @return 道具数量
     */
    public int propCount() {
        return props.size();
    }

    /**
     * 获取已注册的按键绑定数量。
     *
     * @return 按键绑定数量
     */
    public int registeredKeyCount() {
        return registeredBindingIds.size();
    }

    /**
     * 获取按键分类显示名称。
     *
     * @return 分类名称
     */
    public String keyCategory() {
        return keyMappingConfiguration.category();
    }

    public Map<String, Boolean> attributeProviders() {
        return timedAttributes.providers();
    }

    /**
     * 获取全部道具 ID 列表。
     *
     * @return 不可变的道具 ID 列表
     */
    public List<String> propIds() {
        return props.values().stream().map(PropDefinition::id).toList();
    }

    /**
     * 将指定道具绑定到玩家主手物品上。
     *
     * @param player 目标玩家
     * @param propId 道具 ID
     * @return 操作结果，包含成功状态与提示消息
     */
    public PropOperationResult applyPropToMainHand(Player player, String propId) {
        if (player == null) {
            return PropOperationResult.failure(messages.get("set.only-player"));
        }

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (!isRealItem(mainHand)) {
            return PropOperationResult.failure(messages.get("set.no-item"));
        }

        PropDefinition definition = resolveDefinition(propId);
        if (definition == null) {
            return PropOperationResult.failure(messages.get("set.invalid-id", propId));
        }

        ItemStack boundItem = bindItem(mainHand, definition.id(), definition.coolDownGroup());
        if (boundItem == null) {
            return PropOperationResult.failure(messages.get("set.bind-failed"));
        }
        player.getInventory().setItemInMainHand(boundItem);
        return PropOperationResult.success(messages.get("set.success", definition.id()));
    }

    /**
     * 处理客户端初始化完成事件，延迟同步槽位数据。
     *
     * @param player 上线玩家
     */
    public void handleClientInitialized(Player player) {
        scheduleSlotSync(player, 1L);
    }

    /**
     * 玩家加入时延迟同步槽位数据。
     *
     * @param event 玩家加入事件
     */
    @EventHandler(ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        autoCheckTimestamps.remove(uuid);
        observedSlots.remove(uuid);
        scheduleSlotSync(event.getPlayer(), 1L);
    }

    /**
     * 玩家退出时清除各属性子服务的活跃状态。
     *
     * @param event 玩家退出事件
     */
    @EventHandler(ignoreCancelled = true)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        autoCheckTimestamps.remove(uuid);
        observedSlots.remove(uuid);
        keyPresses.cancel(player);
        quickSlotUse.cancel(player);
        timedAttributes.clear(player);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        keyPresses.cancel(event.getEntity());
        quickSlotUse.cancel(event.getEntity());
        timedAttributes.clear(event.getEntity());
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        keyPresses.cancel(event.getPlayer());
        quickSlotUse.cancel(event.getPlayer());
    }

    /**
     * 玩家右键点击时触发手持道具使用。
     *
     * @param event 玩家交互事件
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.useItemInHand() == Event.Result.DENY
            || (action == Action.RIGHT_CLICK_BLOCK && event.useInteractedBlock() == Event.Result.DENY)) {
            return;
        }

        Player player = event.getPlayer();
        if (!canUse(player)) return;
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        PropDefinition prop = resolveBoundProp(mainHand);
        if (prop == null) {
            return;
        }

        event.setCancelled(true);

        PropPlayerHandle handle = bridge.resolvePlayerHandle(player).orElse(null);
        if (handle == null) {
            return;
        }

        handleUse(player, prop, mainHand, PropUseMode.HAND, null, handle);
    }

    private void registerBindings() {
        unregisterBindings();
        for (PropKeyBindingDefinition definition : keyMappingConfiguration.bindings().values()) {
            boolean success = bridge.registerClientKeyBind(
                definition.bindingId(),
                keyMappingConfiguration.category(),
                definition.defaultKey(),
                player -> queueKeyPress(player, definition)
            );
            if (success) {
                registeredBindingIds.add(definition.bindingId());
                if (configuration.debug()) {
                    this.logger.info(
                        "ArcartXProp 注册按键 -> id="
                            + definition.bindingId()
                            + " | key="
                            + definition.defaultKey()
                            + " | slot="
                            + definition.slotId()
                    );
                }
            }
        }
    }

    private void unregisterBindings() {
        for (String bindingId : registeredBindingIds) {
            bridge.unregisterClientKeyBind(bindingId);
        }
        registeredBindingIds.clear();
    }

    private void queueKeyPress(Player player, PropKeyBindingDefinition binding) {
        if (!active || player == null) return;
        var admission = AuthAccess.snapshot(player.getUniqueId());
        if (admission.admitted()) keyPresses.offer(player, binding, admission);
    }

    private void handleQueuedPress(PropKeyPressQueue.Press press) {
        var accepted = press.admission();
        if (!active || Bukkit.getPlayer(accepted.playerId()) != press.player()) return;
        var current = AuthAccess.snapshot(accepted.playerId());
        if (!current.admitted() || current.generation() != accepted.generation()
            || !java.util.Objects.equals(current.connectionId(), accepted.connectionId())) return;
        try {
            handleKeyPress(press.player(), press.binding());
        } catch (RuntimeException failure) {
            logger.log(java.util.logging.Level.WARNING, "Prop 快捷键处理失败: " + press.binding().bindingId(), failure);
        }
    }

    private void handleKeyPress(Player player, PropKeyBindingDefinition binding) {
        if (!canUse(player)) {
            return;
        }

        PropPlayerHandle handle = bridge.resolvePlayerHandle(player).orElse(null);
        if (handle == null) {
            return;
        }

        ItemStack slotItem = handle.getSlotItemStack(binding.slotId());
        switch (binding.mode()) {
            case VANILLA_POTION -> quickSlotUse.potion(player, handle, binding.slotId(), slotItem);
            case MMOITEMS -> quickSlotUse.mmoItem(player, handle, binding.slotId(), slotItem);
            case PROP -> {
                PropDefinition prop = resolveBoundProp(slotItem);
                if (prop != null) {
                    handleUse(player, prop, slotItem, PropUseMode.KEY, binding.slotId(), handle);
                }
            }
        }
    }

    /**
     * 启动自动触发轮询任务。
     * <p>
     * 仅当全局 {@code auto-trigger.enabled} 开启且至少一个道具启用了 {@code auto.enabled} 时才调度；
     * 任务间隔为全局 {@code auto-trigger.interval}，道具 {@code auto.interval} 按服务端 tick 计时。
     */
    private void startAutoTrigger() {
        PropAutoTriggerConfiguration autoConfig = configuration.autoTrigger();
        if (!autoConfig.enabled()) {
            return;
        }

        if (configuredSlotIds.isEmpty() || props.values().stream().noneMatch(prop -> prop.auto().enabled())) {
            return;
        }

        long interval = Math.max(1L, autoConfig.intervalTicks());
        if (bridge instanceof PropSlotUpdates updates) unobserveSlots = updates.observeSlots(this::slotChanged);
        autoTriggerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAutoTrigger, interval, interval);
        if (configuration.debug()) {
            this.logger.info(
                "ArcartXProp 自动触发已启动 -> interval="
                    + interval
                    + " | mode="
                    + autoConfig.mode().name().toLowerCase(Locale.ROOT)
                    + " | slots="
                    + configuredSlotIds.size()
            );
        }
    }

    /**
     * 自动触发轮询：遍历在线玩家的快捷槽，对满足触发条件的道具执行静默使用流程。
     * <p>
     * 每个道具按 {@code auto.interval}（缺省取全局间隔）独立节流；触发失败（冷却/权限/使用条件）
     * 一律静默跳过；{@code mode=first} 时本轮第一个成功触发后停止继续检查该玩家。
     */
    private void tickAutoTrigger() {
        PropAutoTriggerConfiguration autoConfig = configuration.autoTrigger();
        boolean firstOnly = autoConfig.mode() == PropAutoTriggerConfiguration.Mode.FIRST;
        autoTick += autoConfig.intervalTicks();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (unobserveSlots != null) tickObservedSlots(player, autoConfig.intervalTicks(), firstOnly);
            else if (canUse(player)) tickConfiguredSlots(player, autoConfig.intervalTicks(), firstOnly);
        }
    }

    private void slotChanged(Player player, String slot) {
        if (!active || slot == null) return;
        Integer index = slotIndices.get(slot);
        if (index == null) return;
        var signal = observedSlots.get(player.getUniqueId());
        if (signal != null && signal.owner == player) signal.mark(index);
    }

    private void tickConfiguredSlots(Player player, int interval, boolean firstOnly) {
        var handle = bridge.resolvePlayerHandle(player).orElse(null);
        if (handle == null) return;
        UUID uuid = player.getUniqueId();
        for (String slot : configuredSlotIds) {
            ItemStack item = handle.getSlotItemStack(slot);
            if (tryAutoUse(player, uuid, resolveBoundProp(item), item, slot, handle, interval, firstOnly)) break;
        }
    }

    private void tickObservedSlots(Player player, int interval, boolean firstOnly) {
        UUID uuid = player.getUniqueId();
        var cached = observedSlots.get(uuid);
        boolean currentOwner = cached != null && cached.owner == player;
        if (currentOwner && !cached.checkDue(autoTick)) return;
        if (!canUse(player)) return;
        if (!currentOwner) {
            if (cached != null) autoCheckTimestamps.remove(uuid);
            cached = new AutoSlots(player, configuredSlotIds.size());
            observedSlots.put(uuid, cached);
        }
        BitSet changed = cached.take();
        boolean fullCheck = autoTick >= cached.fullCheckAt;
        BitSet selection = changed;
        if (!fullCheck && autoTick >= cached.nextCandidateAt) {
            if (changed == null) {
                selection = cached.activeCandidates;
            } else {
                selection = (BitSet) cached.activeCandidates.clone();
                selection.or(changed);
            }
        }
        if (!fullCheck && (selection == null || selection.isEmpty())) return;

        PropPlayerHandle handle = bridge.resolvePlayerHandle(player).orElse(null);
        if (handle == null) {
            // A full check on the next eligible poll covers any event lost while the handle was unavailable.
            cached.fullCheckAt = 0;
            return;
        }
        boolean finished = false;
        for (int i = fullCheck ? 0 : selection.nextSetBit(0);
             i >= 0 && i < cached.candidates.length;
             i = fullCheck ? i + 1 : selection.nextSetBit(i + 1)) {
            if (!active) return;
            boolean mustRead = fullCheck || changed != null && changed.get(i);
            if (!mustRead && (finished || !autoCheckDue(uuid, cached.candidates[i]))) continue;
            String slot = configuredSlotIds.get(i);
            ItemStack item = handle.getSlotItemStack(slot);
            PropDefinition current = resolveBoundProp(item);
            cached.setCandidate(i, current != null && current.auto().enabled() ? current : null);
            if (!finished) {
                finished = tryAutoUse(player, uuid, cached.candidates[i], item, slot, handle, interval, firstOnly);
            }
        }
        if (fullCheck) {
            // Covers slot writers that bypass ArcartX's event and spreads the checks across players.
            cached.fullCheckAt = autoTick + 100 + Math.floorMod(uuid.hashCode(), 100);
        }
        var checks = autoCheckTimestamps.get(uuid);
        cached.nextCandidateAt = Long.MAX_VALUE;
        for (int i = cached.activeCandidates.nextSetBit(0); i >= 0; i = cached.activeCandidates.nextSetBit(i + 1)) {
            long due = checks == null ? autoTick : checks.getOrDefault(cached.candidates[i].id(), autoTick);
            cached.nextCandidateAt = Math.min(cached.nextCandidateAt, due);
        }
    }

    private boolean autoCheckDue(UUID uuid, PropDefinition definition) {
        if (definition == null || !definition.auto().enabled()) return false;
        var checks = autoCheckTimestamps.get(uuid);
        Long next = checks == null ? null : checks.get(definition.id());
        return next == null || autoTick >= next;
    }

    private boolean tryAutoUse(Player player, UUID uuid, PropDefinition definition, ItemStack item, String slot,
                               PropPlayerHandle handle, int interval, boolean firstOnly) {
        if (!autoCheckDue(uuid, definition)) return false;
        int configured = definition.auto().intervalTicks();
        autoCheckTimestamps.computeIfAbsent(uuid, ignored -> new HashMap<>())
            .put(definition.id(), autoTick + (configured > 0 ? configured : interval));
        return handleUse(player, definition, item, PropUseMode.AUTO, slot, handle) && (firstOnly || !canUse(player));
    }

    private boolean canUse(Player player) {
        return active && player != null && player.isOnline() && !player.isDead()
            && player.getGameMode() != GameMode.SPECTATOR && AuthAccess.admitted(player.getUniqueId());
    }

    private boolean handleUse(
        Player player,
        PropDefinition definition,
        ItemStack itemStack,
        PropUseMode useMode,
        String slotId,
        PropPlayerHandle handle
    ) {
        boolean auto = useMode == PropUseMode.AUTO;
        String itemName = auto ? null : resolveDisplayName(definition, itemStack);
        long cooldown = handle.getTagCooldown(definition.coolDownGroup());

        if (cooldown > 0L) {
            if (!auto) {
                player.sendMessage(render(languageConfiguration.coolDown(), itemName, cooldown));
                if (useMode == PropUseMode.KEY) {
                    handle.syncSlotToClient(slotId);
                }
            }
            return false;
        }
        if (useMode == PropUseMode.HAND && !definition.hand()) {
            player.sendMessage(render(languageConfiguration.noHand(), itemName, 0L));
            return false;
        }
        if (useMode == PropUseMode.KEY && !definition.key()) {
            player.sendMessage(render(languageConfiguration.noKey(), itemName, 0L));
            handle.syncSlotToClient(slotId);
            return false;
        }
        if (!definition.permission().isBlank() && !player.hasPermission(definition.permission())) {
            if (!auto) {
                player.sendMessage(render(languageConfiguration.noPermission(), itemName, 0L));
                if (useMode == PropUseMode.KEY) {
                    handle.syncSlotToClient(slotId);
                }
            }
            return false;
        }
        ItemStack expected = itemStack.clone();
        if (auto && !definition.auto().conditions().isEmpty()
            && ScriptConditionServices.evaluator().firstFailed(player, definition.auto().conditions()) != null) {
            return false;
        }
        if (!definition.conditions().isEmpty()) {
            ScriptCondition failedCondition = ScriptConditionServices.evaluator().firstFailed(player, definition.conditions());
            if (failedCondition != null) {
                if (!auto) {
                    player.sendMessage(renderCondition(languageConfiguration.conditionNotMet(), itemName, failedCondition.raw()));
                    if (useMode == PropUseMode.KEY) {
                        handle.syncSlotToClient(slotId);
                    }
                }
                return false;
            }
        }

        PropEffects effects = definition.effects();
        String blocked = effectUseError(player, effects);
        if (blocked != null) {
            if (!auto) player.sendMessage(blocked);
            if (useMode == PropUseMode.KEY) handle.syncSlotToClient(slotId);
            return false;
        }

        List<PropTimedAttributes.PreparedGroup> attributes;
        try {
            attributes = timedAttributes.prepare(player, effects);
        } catch (PropAmount.EvaluationException exception) {
            if (!auto) player.sendMessage(messages.get("use.amount-unavailable"));
            if (useMode == PropUseMode.KEY) handle.syncSlotToClient(slotId);
            if (configuration.debug()) logger.info("Prop " + definition.id() + " 的 " + exception.getMessage());
            return false;
        }

        ItemStack current = useMode == PropUseMode.HAND
            ? player.getInventory().getItemInMainHand() : handle.getSlotItemStack(slotId);
        if (!canUse(player) || !expected.equals(current)) {
            if (!auto) player.sendMessage(messages.get("use.slot-update-failed"));
            if (useMode == PropUseMode.KEY) handle.syncSlotToClient(slotId);
            return false;
        }
        ItemStack replacement = definition.remove() ? consumeOne(expected) : retagCooldown(expected, definition);
        if (replacement != null) {
            if (isRealItem(replacement) && definition.remove()) {
                synchronizeCooldownTag(replacement, definition);
            }
            if (useMode == PropUseMode.HAND) {
                player.getInventory().setItemInMainHand(replacement);
            } else if (definition.remove()) {
                if (!handle.trySetSlotItemStack(slotId, replacement)) {
                    if (!auto) {
                        player.sendMessage(messages.get("use.slot-update-failed"));
                        handle.syncSlotToClient(slotId);
                    }
                    return false;
                }
            } else {
                handle.setSlotItemStack(slotId, replacement);
            }
        }
        handle.setTagCooldown(definition.coolDownGroup(), definition.coolDownTimeSeconds() * 1000L);

        effects.instant().forEach(effect -> effect.accept(player));
        if (canUse(player)) {
            timedAttributes.apply(player, definition.id(), attributes);
        }
        publishPropUsedEvent(player, definition, useMode);

        if (auto && definition.auto().notifyPlayer()) {
            player.sendMessage(render(languageConfiguration.autoUsed(), resolveDisplayName(definition, itemStack), 0L));
        }

        if (configuration.debug()) {
            this.logger.info(
                "ArcartXProp 使用成功 -> player="
                    + player.getName()
                    + " | prop="
                    + definition.id()
                    + " | mode="
                    + useMode.name().toLowerCase(Locale.ROOT)
                    + " | cooldownGroup="
                    + definition.coolDownGroup()
                    + " | slot="
                    + (slotId == null ? "hand" : slotId)
            );
        }
        return true;
    }

    private PropDefinition resolveBoundProp(ItemStack itemStack) {
        return isRealItem(itemStack) ? resolveDefinition(bridge.getPersistentPropId(itemStack)) : null;
    }

    private ItemStack updatedCooldownTag(ItemStack item, PropDefinition definition) {
        if (definition.coolDownGroup().equals(bridge.getCooldownTag(item))) {
            return null;
        }
        ItemStack updated = item.clone();
        bridge.setCooldownTag(updated, definition.coolDownGroup());
        return updated;
    }

    private ItemStack retagCooldown(ItemStack copy, PropDefinition definition) {
        if (definition.coolDownGroup().equals(bridge.getCooldownTag(copy))) return null;
        bridge.setCooldownTag(copy, definition.coolDownGroup());
        return copy;
    }

    private void synchronizeCooldownTag(ItemStack item, PropDefinition definition) {
        if (!definition.coolDownGroup().equals(bridge.getCooldownTag(item))) {
            bridge.setCooldownTag(item, definition.coolDownGroup());
        }
    }

    private ItemStack bindItem(ItemStack itemStack, String propId, String coolDownGroup) {
        ItemStack working = bridge.writePropId(itemStack.clone(), propId);
        if (working == null || !propId.equals(bridge.getPersistentPropId(working))) return null;
        bridge.setCooldownTag(working, coolDownGroup);
        return working;
    }

    private void publishPropUsedEvent(Player player, PropDefinition definition, PropUseMode mode) {
        if (eventBusProvider == null) return;
        EventBusCapability eventBus = eventBusProvider.get();
        if (eventBus == null) return;
        eventBus.publish("axs.prop.prop_used", player, Map.of(
            "prop_id", definition.id(),
            "trigger", mode.name().toLowerCase(Locale.ROOT),
            "consumed", Boolean.toString(definition.remove()),
            "cooldown_group", definition.coolDownGroup(),
            "max_duration_seconds", Integer.toString(definition.effects().maxDurationSeconds())
        ));
    }

    private String effectUseError(Player player, PropEffects effects) {
        String unavailable = timedAttributes.unavailableProvider(effects);
        if (unavailable != null) return messages.get("use.effect-provider-unavailable", unavailable);
        String unsupported = timedAttributes.unsupportedAttribute(player, effects);
        return unsupported == null ? null : messages.get("use.attribute-unavailable", unsupported);
    }

    private void syncOnlinePlayers() {
        if (configuredSlotIds.isEmpty()) return;
        var players = List.copyOf(Bukkit.getOnlinePlayers());
        if (players.isEmpty()) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player player : players) {
                try {
                    synchronizeConfiguredSlots(player);
                } catch (RuntimeException failure) {
                    logger.log(java.util.logging.Level.WARNING, "Prop 快捷槽初始同步失败: " + player.getName(), failure);
                }
            }
        }, 1L);
    }

    private void scheduleSlotSync(Player player, long delayTicks) {
        if (player == null || configuredSlotIds.isEmpty()) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> synchronizeConfiguredSlots(player), Math.max(0L, delayTicks));
    }

    private void synchronizeConfiguredSlots(Player player) {
        if (!active || player == null || !player.isOnline() || Bukkit.getPlayer(player.getUniqueId()) != player) {
            return;
        }

        PropPlayerHandle handle = bridge.resolvePlayerHandle(player).orElse(null);
        if (handle == null) {
            return;
        }

        for (String slotId : configuredSlotIds) {
            ItemStack slotItem = handle.getSlotItemStack(slotId);
            PropDefinition prop = resolveBoundProp(slotItem);
            if (prop != null) {
                ItemStack updated = updatedCooldownTag(slotItem, prop);
                if (updated != null) handle.setSlotItemStack(slotId, updated);
            }
        }
    }

    private PropDefinition resolveDefinition(String propId) {
        if (propId == null || propId.isBlank()) {
            return null;
        }
        return props.get(propId.trim().toLowerCase(Locale.ROOT));
    }

    private String render(String template, String itemName, long cooldownMillis) {
        String rendered = template
            .replace("{item}", itemName == null ? "" : itemName)
            .replace("{remaining_seconds}", String.format(Locale.US, "%.2f", Math.max(0L, cooldownMillis) / 1000.0D));
        return ChatColor.translateAlternateColorCodes('&', rendered);
    }

    private static boolean isRealItem(ItemStack itemStack) {
        return itemStack != null && !itemStack.getType().isAir() && itemStack.getAmount() > 0;
    }

    private static ItemStack consumeOne(ItemStack copy) {
        if (copy.getAmount() <= 1) {
            return new ItemStack(Material.AIR);
        }
        copy.setAmount(copy.getAmount() - 1);
        return copy;
    }

    private String renderCondition(String template, String itemName, String conditionRaw) {
        String rendered = template
            .replace("{item}", itemName == null ? "" : itemName)
            .replace("{condition}", conditionRaw == null ? "" : conditionRaw);
        return ChatColor.translateAlternateColorCodes('&', rendered);
    }

    private String resolveDisplayName(PropDefinition definition, ItemStack itemStack) {
        if (!definition.displayName().isBlank()) {
            return definition.displayName();
        }
        if (itemStack == null) {
            return "§c未知道具";
        }
        if (vanillaItemNameBridge != null) {
            return vanillaItemNameBridge.resolveDisplayName(itemStack);
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta != null && itemMeta.hasDisplayName()) {
            String displayName = itemMeta.getDisplayName();
            if (displayName != null && !displayName.isBlank()) {
                return displayName;
            }
        }

        String[] parts = itemStack.getType().name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.length() == 0 ? itemStack.getType().name() : builder.toString();
    }

    /**
     * 道具操作结果。
     *
     * @param success 操作是否成功
     * @param message 提示消息
     */
    public record PropOperationResult(boolean success, String message) {

        /**
         * 构造成功结果。
         *
         * @param message 提示消息
         * @return 成功结果实例
         */
        public static PropOperationResult success(String message) {
            return new PropOperationResult(true, message);
        }

        /**
         * 构造失败结果。
         *
         * @param message 提示消息
         * @return 失败结果实例
         */
        public static PropOperationResult failure(String message) {
            return new PropOperationResult(false, message);
        }
    }

    private enum PropUseMode {
        HAND,
        KEY,
        AUTO
    }
}


