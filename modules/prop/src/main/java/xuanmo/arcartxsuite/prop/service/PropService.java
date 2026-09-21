package xuanmo.arcartxsuite.prop.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import xuanmo.arcartxsuite.api.util.AttributeResolver;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.api.bridge.PropBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PropPlayerHandle;
import xuanmo.arcartxsuite.api.condition.ScriptCondition;
import xuanmo.arcartxsuite.api.condition.ScriptConditionServices;
import xuanmo.arcartxsuite.prop.config.PropAutoTriggerConfiguration;
import xuanmo.arcartxsuite.prop.config.PropAutoTriggerDefinition;
import xuanmo.arcartxsuite.prop.config.PropDefinition;
import xuanmo.arcartxsuite.prop.config.PropKeyBindingDefinition;
import xuanmo.arcartxsuite.prop.config.PropKeyMappingConfiguration;
import xuanmo.arcartxsuite.prop.config.PropLanguageConfiguration;
import xuanmo.arcartxsuite.prop.config.PropModuleConfiguration;
import java.util.logging.Logger;

/**
 * Prop 模块核心服务。
 * <p>
 * 实现 Bukkit {@link Listener}，负责道具的按键绑定注册、手持右键与按键使用处理、
 * 效果执行（命令、消息、饱食度、生命、经验、药水、AttributePlus、MythicLib、Symphony）、
 * 冷却管理、槽位同步与道具绑定。聚合三个属性子系统服务：
 * {@link PropAttributePlusService}、{@link PropMythicLibService}、{@link PropSymphonyService}。
 */
public final class PropService implements Listener {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final PropModuleConfiguration configuration;
    private final PropBridgeAPI bridge;
    private final PropKeyMappingConfiguration keyMappingConfiguration;
    private final PropLanguageConfiguration languageConfiguration;
    private final Map<String, PropDefinition> definitionsByNormalizedId;
    private final PropAttributePlusService attributePlusService;
    private final PropMythicLibService mythicLibService;
    private final PropSymphonyService symphonyService;
    private Supplier<EventBusCapability> eventBusProvider;
    private final MessageProvider messages;
    private final xuanmo.arcartxsuite.api.bridge.VanillaItemNameBridge vanillaItemNameBridge;
    private final Set<String> registeredBindingIds = new LinkedHashSet<>();
    private final Map<UUID, Map<String, Long>> autoCheckTimestamps = new HashMap<>();
    private List<String> autoSlotIds = List.of();
    private BukkitTask autoTriggerTask;

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
        xuanmo.arcartxsuite.api.attribute.AttributeBridgeRegistry attributeBridge,
        MessageProvider messages,
        xuanmo.arcartxsuite.api.bridge.VanillaItemNameBridge vanillaItemNameBridge
    ) {
        this.plugin = plugin;
        this.logger = logger;
        this.configuration = configuration;
        this.bridge = bridge;
        this.keyMappingConfiguration = keyMappingConfiguration;
        this.languageConfiguration = languageConfiguration;
        this.definitionsByNormalizedId = definitionsByNormalizedId;
        this.messages = messages;
        this.vanillaItemNameBridge = vanillaItemNameBridge;
        this.attributePlusService = new PropAttributePlusService(plugin, attributeBridge.attributePlus());
        this.mythicLibService = new PropMythicLibService(plugin, this.logger, configuration.mythicLib(), attributeBridge.mythicLib());
        this.symphonyService = new PropSymphonyService(plugin, attributeBridge.symphony());
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
     * 启动服务：初始化 MythicLib 子服务、注册事件监听、注册按键绑定、同步在线玩家槽位。
     */
    public void start() {
        mythicLibService.start();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        registerBindings();
        syncOnlinePlayers();
        startAutoTrigger();
    }

    /**
     * 关闭服务：注销事件监听、取消按键绑定、停止自动触发任务、关闭各属性子服务。
     */
    public void shutdown() {
        HandlerList.unregisterAll(this);
        unregisterBindings();
        if (autoTriggerTask != null) {
            autoTriggerTask.cancel();
            autoTriggerTask = null;
        }
        autoCheckTimestamps.clear();
        mythicLibService.shutdown();
        symphonyService.shutdown();
    }

    /**
     * 获取已加载的道具定义数量。
     *
     * @return 道具数量
     */
    public int propCount() {
        return definitionsByNormalizedId.size();
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

    /**
     * 检查 AttributePlus 是否已挂钩。
     *
     * @return {@code true} 表示已挂钩
     */
    public boolean attributePlusHooked() {
        return attributePlusService.hooked();
    }

    /**
     * 检查 MythicLib 是否已挂钩。
     *
     * @return {@code true} 表示已挂钩
     */
    public boolean mythicLibHooked() {
        return mythicLibService.hooked();
    }

    /**
     * 检查 Symphony 是否已挂钩。
     *
     * @return {@code true} 表示已挂钩
     */
    public boolean symphonyHooked() {
        return symphonyService.hooked();
    }

    /**
     * 获取全部道具 ID 列表。
     *
     * @return 不可变的道具 ID 列表
     */
    public List<String> propIds() {
        List<String> propIds = new ArrayList<>();
        for (PropDefinition definition : definitionsByNormalizedId.values()) {
            propIds.add(definition.id());
        }
        return List.copyOf(propIds);
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
        autoCheckTimestamps.remove(player.getUniqueId());
        mythicLibService.clear(player);
        symphonyService.clear(player);
        attributePlusService.clear(player);
    }

    /**
     * 玩家右键点击时触发手持道具使用。
     *
     * @param event 玩家交互事件
     */
    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ResolvedBoundProp resolved = resolveBoundProp(mainHand);
        if (resolved == null) {
            return;
        }

        event.setCancelled(true);

        if (resolved.changed()) {
            player.getInventory().setItemInMainHand(resolved.itemStack());
        }

        PropPlayerHandle handle = bridge.resolvePlayerHandle(player).orElse(null);
        if (handle == null) {
            return;
        }

        handleUse(player, resolved, PropUseMode.HAND, null, handle);
    }

    private void registerBindings() {
        unregisterBindings();
        for (PropKeyBindingDefinition definition : keyMappingConfiguration.bindings().values()) {
            boolean success = bridge.registerClientKeyBind(
                definition.bindingId(),
                keyMappingConfiguration.category(),
                definition.defaultKey(),
                player -> Bukkit.getScheduler().runTask(plugin, () -> handleKeyPress(player, definition))
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

    private void handleKeyPress(Player player, PropKeyBindingDefinition binding) {
        if (player == null || !player.isOnline()) {
            return;
        }

        PropPlayerHandle handle = bridge.resolvePlayerHandle(player).orElse(null);
        if (handle == null) {
            return;
        }

        ItemStack slotItem = handle.getSlotItemStack(binding.slotId());
        ResolvedBoundProp resolved = resolveBoundProp(slotItem);
        if (resolved == null) {
            return;
        }

        if (resolved.changed()) {
            handle.setSlotItemStack(binding.slotId(), resolved.itemStack());
        }

        handleUse(player, resolved, PropUseMode.KEY, binding.slotId(), handle);
    }

    /**
     * 启动自动触发轮询任务。
     * <p>
     * 仅当全局 {@code auto-trigger.enabled} 开启且至少一个道具启用了 {@code auto.enabled} 时才调度；
     * 任务间隔为全局 {@code auto-trigger.interval}，道具 {@code auto.interval} 通过时间戳节流实现。
     */
    private void startAutoTrigger() {
        PropAutoTriggerConfiguration autoConfig = configuration.autoTrigger();
        if (!autoConfig.enabled()) {
            return;
        }

        LinkedHashSet<String> slotIds = new LinkedHashSet<>();
        boolean anyAutoProp = false;
        for (PropKeyBindingDefinition binding : keyMappingConfiguration.bindings().values()) {
            slotIds.add(binding.slotId());
        }
        for (PropDefinition definition : definitionsByNormalizedId.values()) {
            if (definition.auto().enabled()) {
                anyAutoProp = true;
                break;
            }
        }
        autoSlotIds = List.copyOf(slotIds);
        if (!anyAutoProp || autoSlotIds.isEmpty()) {
            return;
        }

        long interval = Math.max(1L, autoConfig.intervalTicks());
        autoTriggerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAutoTrigger, interval, interval);
        if (configuration.debug()) {
            this.logger.info(
                "ArcartXProp 自动触发已启动 -> interval="
                    + interval
                    + " | mode="
                    + autoConfig.mode().name().toLowerCase(Locale.ROOT)
                    + " | slots="
                    + autoSlotIds.size()
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
        long now = System.currentTimeMillis();

        for (Player player : Bukkit.getOnlinePlayers()) {
            // 死亡玩家无法通过手动方式使用道具，自动触发同样需要跳过；
            // 否则血量类条件（如 health < 30）会对尸体成立，白消耗物品。
            if (player == null || !player.isOnline() || player.isDead()
                || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            PropPlayerHandle handle = bridge.resolvePlayerHandle(player).orElse(null);
            if (handle == null) {
                continue;
            }
            Map<String, Long> checks = autoCheckTimestamps.computeIfAbsent(
                player.getUniqueId(), key -> new HashMap<>());

            for (String slotId : autoSlotIds) {
                ResolvedBoundProp resolved = resolveBoundProp(handle.getSlotItemStack(slotId));
                if (resolved == null) {
                    continue;
                }
                PropDefinition definition = resolved.definition();
                PropAutoTriggerDefinition auto = definition.auto();
                if (!auto.enabled()) {
                    continue;
                }

                Long nextCheck = checks.get(definition.id());
                if (nextCheck != null && now < nextCheck) {
                    continue;
                }
                int intervalTicks = auto.intervalTicks() > 0
                    ? auto.intervalTicks()
                    : autoConfig.intervalTicks();
                checks.put(definition.id(), now + Math.max(1, intervalTicks) * 50L);

                if (resolved.changed()) {
                    handle.setSlotItemStack(slotId, resolved.itemStack());
                }
                if (!auto.conditions().isEmpty()
                    && ScriptConditionServices.evaluator().firstFailed(player, auto.conditions()) != null) {
                    continue;
                }

                if (handleUse(player, resolved, PropUseMode.AUTO, slotId, handle) && firstOnly) {
                    break;
                }
            }
        }
    }

    private boolean handleUse(
        Player player,
        ResolvedBoundProp resolved,
        PropUseMode useMode,
        String slotId,
        PropPlayerHandle handle
    ) {
        PropDefinition definition = resolved.definition();
        ItemStack itemStack = resolved.itemStack();
        String itemName = resolveDisplayName(itemStack);
        boolean auto = useMode == PropUseMode.AUTO;
        long cooldown = handle.getTagCooldown(definition.coolDownGroup());

        if (cooldown > 0L) {
            if (!auto) {
                player.sendMessage(render(languageConfiguration.coolDown(), itemName, cooldown));
                if (useMode == PropUseMode.KEY) {
                    handle.syncSlotCacheToClient();
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
            handle.syncSlotCacheToClient();
            return false;
        }
        if (!definition.permission().isBlank() && !player.hasPermission(definition.permission())) {
            if (!auto) {
                player.sendMessage(render(languageConfiguration.noPermission(), itemName, 0L));
                if (useMode == PropUseMode.KEY) {
                    handle.syncSlotCacheToClient();
                }
            }
            return false;
        }
        if (!definition.conditions().isEmpty()) {
            ScriptCondition failedCondition = ScriptConditionServices.evaluator().firstFailed(player, definition.conditions());
            if (failedCondition != null) {
                if (!auto) {
                    player.sendMessage(renderCondition(languageConfiguration.conditionNotMet(), itemName, failedCondition.raw()));
                    if (useMode == PropUseMode.KEY) {
                        handle.syncSlotCacheToClient();
                    }
                }
                return false;
            }
        }

        handle.setTagCooldown(definition.coolDownGroup(), definition.coolDownTimeSeconds() * 1000L);

        if (useMode == PropUseMode.HAND) {
            if (definition.remove()) {
                player.getInventory().setItemInMainHand(consumeOne(itemStack));
            } else if (resolved.changed()) {
                player.getInventory().setItemInMainHand(itemStack);
            }
        } else {
            if (definition.remove()) {
                handle.setSlotItemStack(slotId, consumeOne(itemStack));
            } else if (resolved.changed()) {
                handle.setSlotItemStack(slotId, itemStack);
            }
            handle.syncSlotCacheToClient();
        }

        executeEffects(player, definition);
        publishPropUsedEvent(player, definition.id());

        if (auto && definition.auto().notifyPlayer()) {
            player.sendMessage(render(languageConfiguration.autoUsed(), itemName, 0L));
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

    private ResolvedBoundProp resolveBoundProp(ItemStack itemStack) {
        if (!isRealItem(itemStack)) {
            return null;
        }

        String propId = bridge.getPersistentPropId(itemStack);
        if (propId.isBlank()) {
            return null;
        }

        PropDefinition definition = resolveDefinition(propId);
        if (definition == null) {
            return null;
        }

        ItemStack working = itemStack;
        boolean changed = false;
        String currentCooldownTag = bridge.getCooldownTag(working);
        if (!Objects.equals(currentCooldownTag, definition.coolDownGroup())) {
            bridge.setCooldownTag(working, definition.coolDownGroup());
            changed = true;
        }

        return new ResolvedBoundProp(definition, working, changed);
    }

    private ItemStack bindItem(ItemStack itemStack, String propId, String coolDownGroup) {
        ItemStack working = bridge.writePropId(itemStack, propId);
        if (working == null) {
            working = itemStack;
        }
        bridge.setCooldownTag(working, coolDownGroup);
        return working;
    }

    private void publishPropUsedEvent(Player player, String propId) {
        if (eventBusProvider == null) return;
        EventBusCapability eventBus = eventBusProvider.get();
        if (eventBus == null) return;
        Map<String, String> payload = new java.util.HashMap<>();
        payload.put("prop_id", propId);
        eventBus.publish("axs.prop.prop_used", player, payload);
    }

    private void executeEffects(Player player, PropDefinition definition) {
        List<String> attributeLines = new ArrayList<>();
        List<PropMythicLibEffect> mythicLibEffects = new ArrayList<>();
        List<PropSymphonyEffect> symphonyEffects = new ArrayList<>();
        for (String effectLine : definition.effects()) {
            String[] split = effectLine.split("\\|", 2);
            if (split.length != 2) {
                continue;
            }

            String type = split[0].trim().toLowerCase(Locale.ROOT);
            String payload = split[1].trim();
            switch (type) {
                case "cmd" -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), replacePlayerToken(payload, player));
                case "msg" -> player.sendMessage(ChatColor.translateAlternateColorCodes('&', replacePlayerToken(payload, player)));
                case "food" -> player.setFoodLevel(Math.min(20, player.getFoodLevel() + parseInt(payload, 0)));
                case "health" -> applyHealthDelta(player, parseDouble(payload, 0.0D));
                case "exp" -> player.giveExp(parseInt(payload, 0));
                case "healthpercent" -> applyHealthPercent(player, parseDouble(payload, 0.0D));
                case "potion" -> applyPotion(player, definition.durationSeconds(), payload);
                case "ap" -> attributeLines.add(payload);
                case "ml", "mythiclib" -> PropMythicLibEffectParser.parse(type + "|" + payload)
                    .ifPresentOrElse(
                        mythicLibEffects::add,
                        () -> this.logger.warning("Prop MythicLib 效果格式无效，已跳过: " + effectLine)
                    );
                case "sy", "symphony" -> parseSymphonyEffect(payload)
                    .ifPresentOrElse(
                        symphonyEffects::add,
                        () -> this.logger.warning("Prop Symphony 效果格式无效，已跳过: " + effectLine)
                    );
                default -> {
                    if (configuration.debug()) {
                        this.logger.warning("ArcartXProp 检测到未知效果类型: " + effectLine);
                    }
                }
            }
        }

        if (!attributeLines.isEmpty() && !attributePlusService.apply(player, definition.displayName(), attributeLines, definition.durationSeconds()) && configuration.debug()) {
            this.logger.info("ArcartXProp AP 效果已跳过 -> hooked=" + attributePlusService.hooked());
        }
        if (!mythicLibEffects.isEmpty()
            && !mythicLibService.apply(player, definition.id(), mythicLibEffects, definition.durationSeconds())
            && configuration.debug()
        ) {
            this.logger.info("ArcartXProp MythicLib 效果已跳过 -> hooked=" + mythicLibService.hooked());
        }
        if (!symphonyEffects.isEmpty()
            && !symphonyService.apply(player, definition.id(), symphonyEffects, definition.durationSeconds())
            && configuration.debug()
        ) {
            this.logger.info("ArcartXProp Symphony 效果已跳过 -> hooked=" + symphonyService.hooked());
        }
    }

    private static java.util.Optional<PropSymphonyEffect> parseSymphonyEffect(String payload) {
        if (payload == null || payload.isBlank()) {
            return java.util.Optional.empty();
        }
        boolean percent = false;
        String working = payload.trim();
        if (working.endsWith("%")) {
            percent = true;
            working = working.substring(0, working.length() - 1).trim();
        }
        int separatorIndex = working.indexOf(':');
        if (separatorIndex <= 0 || separatorIndex >= working.length() - 1) {
            return java.util.Optional.empty();
        }
        String attributeId = working.substring(0, separatorIndex).trim();
        if (attributeId.isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            double value = Double.parseDouble(working.substring(separatorIndex + 1).trim());
            return java.util.Optional.of(new PropSymphonyEffect(attributeId, value, percent));
        } catch (NumberFormatException exception) {
            return java.util.Optional.empty();
        }
    }

    private void syncOnlinePlayers() {
        for (Player online : Bukkit.getOnlinePlayers()) {
            scheduleSlotSync(online, 1L);
        }
    }

    private void scheduleSlotSync(Player player, long delayTicks) {
        if (player == null) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> synchronizeConfiguredSlots(player), Math.max(0L, delayTicks));
    }

    private void synchronizeConfiguredSlots(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        PropPlayerHandle handle = bridge.resolvePlayerHandle(player).orElse(null);
        if (handle == null) {
            return;
        }

        Set<String> slotIds = new LinkedHashSet<>();
        for (PropKeyBindingDefinition definition : keyMappingConfiguration.bindings().values()) {
            slotIds.add(definition.slotId());
        }
        for (String slotId : slotIds) {
            ItemStack slotItem = handle.getSlotItemStack(slotId);
            ResolvedBoundProp resolved = resolveBoundProp(slotItem);
            if (resolved != null && resolved.changed()) {
                handle.setSlotItemStack(slotId, resolved.itemStack());
            }
        }
        handle.syncSlotCacheToClient();
    }

    private PropDefinition resolveDefinition(String propId) {
        if (propId == null) {
            return null;
        }
        return definitionsByNormalizedId.get(propId.trim().toLowerCase(Locale.ROOT));
    }

    private String render(String template, String itemName, long cooldownMillis) {
        String rendered = template
            .replace("{NAME}", itemName == null ? "" : itemName)
            .replace("{TIME}", String.format(Locale.US, "%.2f", Math.max(0L, cooldownMillis) / 1000.0D));
        return ChatColor.translateAlternateColorCodes('&', rendered);
    }

    private static boolean isRealItem(ItemStack itemStack) {
        return itemStack != null && itemStack.getType() != Material.AIR;
    }

    private static ItemStack consumeOne(ItemStack itemStack) {
        if (!isRealItem(itemStack)) {
            return new ItemStack(Material.AIR);
        }
        if (itemStack.getAmount() <= 1) {
            return new ItemStack(Material.AIR);
        }
        ItemStack next = itemStack.clone();
        next.setAmount(next.getAmount() - 1);
        return next;
    }

    private static String replacePlayerToken(String text, Player player) {
        String safeName = xuanmo.arcartxsuite.api.command.CommandSanitizer.validatePlayerName(player.getName());
        return (text == null ? "" : text).replace("{player}", safeName);
    }

    private static void applyHealthDelta(Player player, double delta) {
        if (player == null) {
            return;
        }
        double maxHealth = AttributeResolver.getMaxHealth(player);
        player.setHealth(Math.min(maxHealth, Math.max(0.0D, player.getHealth() + delta)));
    }

    private static void applyHealthPercent(Player player, double percent) {
        if (player == null) {
            return;
        }
        var maxHealthAttribute = AttributeResolver.getMaxHealthAttribute(player);
        if (maxHealthAttribute == null) {
            return;
        }
        double maxHealth = maxHealthAttribute.getValue();
        double delta = maxHealth * percent / 100.0D;
        player.setHealth(Math.min(maxHealth, Math.max(0.0D, player.getHealth() + delta)));
    }

    // 1.20.2+ 重命名了部分 PotionEffectType 枚举，旧名在 1.21+ 可能无法通过 getByName 查找。
    // 配置中可能仍使用旧名，此处做兼容映射（旧名 → 新名）。
    private static final Map<String, String> LEGACY_POTION_EFFECT_NAMES = Map.ofEntries(
        Map.entry("SLOW", "SLOWNESS"),
        Map.entry("FAST_DIGGING", "HASTE"),
        Map.entry("SLOW_DIGGING", "MINING_FATIGUE"),
        Map.entry("INCREASE_DAMAGE", "STRENGTH"),
        Map.entry("JUMP", "JUMP_BOOST"),
        Map.entry("CONFUSION", "NAUSEA"),
        Map.entry("HEAL", "INSTANT_HEAL"),
        Map.entry("HARM", "INSTANT_DAMAGE"),
        Map.entry("DAMAGE_RESISTANCE", "RESISTANCE")
    );

    private static PotionEffectType resolvePotionEffectType(String rawName) {
        String upper = rawName.trim().toUpperCase(Locale.ROOT);
        // 先直接查找（新版本名称优先）
        PotionEffectType type = PotionEffectType.getByName(upper);
        if (type != null) {
            return type;
        }
        // 回退到旧名映射
        String mapped = LEGACY_POTION_EFFECT_NAMES.get(upper);
        if (mapped != null) {
            return PotionEffectType.getByName(mapped);
        }
        return null;
    }

    private static void applyPotion(Player player, int durationSeconds, String payload) {
        String[] split = payload.split(":", 2);
        if (split.length != 2) {
            return;
        }
        PotionEffectType type = resolvePotionEffectType(split[0]);
        if (type == null) {
            return;
        }
        int amplifier = parseInt(split[1], 0);
        player.addPotionEffect(new PotionEffect(type, Math.max(0, durationSeconds) * 20, amplifier));
    }

    private static int parseInt(String rawValue, int defaultValue) {
        try {
            return Integer.parseInt(rawValue.trim());
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    private static double parseDouble(String rawValue, double defaultValue) {
        try {
            return Double.parseDouble(rawValue.trim());
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    private String renderCondition(String template, String itemName, String conditionRaw) {
        String rendered = template
            .replace("{NAME}", itemName == null ? "" : itemName)
            .replace("{CONDITION}", conditionRaw == null ? "" : conditionRaw);
        return ChatColor.translateAlternateColorCodes('&', rendered);
    }

    private String resolveDisplayName(ItemStack itemStack) {
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

    private record ResolvedBoundProp(PropDefinition definition, ItemStack itemStack, boolean changed) {
    }

    private enum PropUseMode {
        HAND,
        KEY,
        AUTO
    }
}


