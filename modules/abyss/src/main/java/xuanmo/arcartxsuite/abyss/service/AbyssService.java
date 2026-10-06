package xuanmo.arcartxsuite.abyss.service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.abyss.AbyssModule;
import xuanmo.arcartxsuite.abyss.command.AbyssAdminCommand;
import xuanmo.arcartxsuite.abyss.config.AbyssModuleConfiguration;
import xuanmo.arcartxsuite.abyss.data.JdbcAbyssRepository;
import xuanmo.arcartxsuite.abyss.data.PlayerDataManager;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstance;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstanceManager;
import xuanmo.arcartxsuite.abyss.instance.AbyssStatus;
import xuanmo.arcartxsuite.abyss.instance.FailReason;
import xuanmo.arcartxsuite.abyss.instance.LeaveReason;
import xuanmo.arcartxsuite.abyss.interact.InteractManager;
import xuanmo.arcartxsuite.abyss.leaderboard.LeaderboardService;
import xuanmo.arcartxsuite.abyss.listener.AbyssInteractListener;
import xuanmo.arcartxsuite.abyss.listener.AbyssPlayerListener;
import xuanmo.arcartxsuite.abyss.listener.AbyssSpawnListener;
import xuanmo.arcartxsuite.abyss.mob.CompositeMobProvider;
import xuanmo.arcartxsuite.abyss.mob.MobProvider;
import xuanmo.arcartxsuite.abyss.mob.MonsterGroupManager;
import xuanmo.arcartxsuite.abyss.mob.MythicMobDeathRelay;
import xuanmo.arcartxsuite.abyss.mob.MythicMobProvider;
import xuanmo.arcartxsuite.abyss.mob.VanillaMobProvider;
import xuanmo.arcartxsuite.abyss.npc.DungeonNpcManager;
import xuanmo.arcartxsuite.abyss.obstacle.ObstacleManager;
import xuanmo.arcartxsuite.abyss.party.Party;
import xuanmo.arcartxsuite.abyss.party.PartyManager;
import xuanmo.arcartxsuite.abyss.recovery.RecoveryManager;
import xuanmo.arcartxsuite.abyss.quest.QuestManager;
import xuanmo.arcartxsuite.abyss.reward.RewardResolver;
import xuanmo.arcartxsuite.abyss.script.ScriptContext;
import xuanmo.arcartxsuite.abyss.script.ScriptRunner;
import xuanmo.arcartxsuite.abyss.stamina.StaminaManager;
import xuanmo.arcartxsuite.abyss.stats.CombatStatisticsService;
import xuanmo.arcartxsuite.abyss.task.InstanceTaskScheduler;
import xuanmo.arcartxsuite.abyss.template.AbyssTemplate;
import xuanmo.arcartxsuite.abyss.template.AbyssTemplateLoader;
import xuanmo.arcartxsuite.abyss.ui.AbyssUiService;
import xuanmo.arcartxsuite.abyss.world.AbyssWorldPool;
import xuanmo.arcartxsuite.api.attribute.AttributeBridgeRegistry;
import xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.VanillaItemNameBridge;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.api.condition.ScriptConditionEvaluator;
import xuanmo.arcartxsuite.api.crossserver.CrossServerAPI;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.item.ItemMatcherAPI;
import xuanmo.arcartxsuite.api.item.ItemRewardDispatcher;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.api.item.PendingRewardService;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.api.placeholder.PlaceholderResolverAPI;
import xuanmo.arcartxsuite.api.scheduler.ScheduledTask;
import xuanmo.arcartxsuite.api.scheduler.SchedulerAPI;
import xuanmo.arcartxsuite.api.script.AriaBridge;
import xuanmo.arcartxsuite.api.storage.StorageManager;

/**
 * Abyss 核心服务：生命周期编排 + 子系统持有。
 * <p>
 * 职责链：
 * <pre>
 * 进入：模板→条件校验→建实例→复制世界→存档→传送→init→start
 * 运行：怪物组/障碍物/交互点/任务/脚本/NPC/统计 各管理器驱动
 * 结束：complete/fail/cancel → 脚本 → 撤出玩家 → 恢复 → 清理 → 释放世界
 * </pre>
 */
public final class AbyssService {

    private final AbyssModule module;
    private final Plugin plugin;
    private final AbyssModuleConfiguration config;
    private final Logger logger;
    private final MessageProvider messages;

    // ── 宿主 API ──
    private final @Nullable PacketBridgeAPI packetBridge;
    private final @Nullable ItemBridgeAPI itemStackBridge;
    private final ItemSourceRegistry itemSourceRegistry;
    private final ItemRewardDispatcher itemRewardDispatcher;
    private final PendingRewardService pendingRewardService;
    private final ItemMatcherAPI itemMatcher;
    private final VanillaItemNameBridge vanillaItemNameBridge;
    private final CurrencyBridgeAPI currencyManager;
    private final AttributeBridgeRegistry attributeBridge;
    private final AriaBridge ariaBridge;
    private final ScriptConditionEvaluator scriptConditionEvaluator;
    private final PlaceholderResolverAPI placeholderResolver;
    private final StorageManager storageManager;
    private final SchedulerAPI scheduler;
    private final CrossServerAPI crossServer;

    // ── 子系统 ──
    private AbyssInstanceManager instances;
    private AbyssWorldPool worldPool;
    private AbyssTemplateLoader templateLoader;
    private ScriptRunner scriptRunner;
    private MonsterGroupManager mobGroups;
    private MobProvider mobProvider;
    private ObstacleManager obstacles;
    private InteractManager interacts;
    private InstanceTaskScheduler instanceTasks;
    private DungeonNpcManager npcs;
    private RewardResolver rewards;
    private QuestManager quests;
    private StaminaManager stamina;
    private CombatStatisticsService stats;
    private LeaderboardService leaderboard;
    private CooldownManager cooldown;
    private RequirementChecker requirementChecker;
    private PartyManager partyManager;
    private RecoveryManager recoveryManager;
    private xuanmo.arcartxsuite.abyss.room.RoomManager roomManager;
    private xuanmo.arcartxsuite.abyss.links.AbyssLinks links;
    private xuanmo.arcartxsuite.abyss.links.EntranceMarkerService entranceMarkers;
    private xuanmo.arcartxsuite.abyss.interact.EntranceManager entranceManager;
    private xuanmo.arcartxsuite.abyss.puzzle.PuzzleManager puzzleManager;
    private xuanmo.arcartxsuite.abyss.guide.GuideService guideService;
    private xuanmo.arcartxsuite.abyss.buff.BuffManager buffManager;
    private xuanmo.arcartxsuite.abyss.design.DesignManager designManager;
    private xuanmo.arcartxsuite.abyss.design.DesignListener designListener;
    private xuanmo.arcartxsuite.api.bridge.WaypointBridgeAPI waypointBridge;
    private xuanmo.arcartxsuite.api.bridge.AdyeshachNpcBridgeAPI npcBridge;
    private xuanmo.arcartxsuite.abyss.integration.DungeonExternalInstaller externalInstaller;
    /** 模块数据目录（external 部署/模板加载共用）。 */
    private File moduleDataFolder;
    /** EventBus quest_completed 订阅 id（questgps 任务完成 → 副本任务钩子）。 */
    private volatile String questCompletedSubId;
    private GlobalCounterService globalCounters;
    private JdbcAbyssRepository repository;
    private PlayerDataManager playerData;
    private AbyssAdminCommand adminCommand;

    /** worldName → instanceId（事件反向定位实例）。 */
    private final Map<String, UUID> worldIndex = new ConcurrentHashMap<>();
    /** 玩家 → 进入 Essentials AFK 状态的起始时间戳（afk-kick 判定）。 */
    private final Map<UUID, Long> afkSince = new ConcurrentHashMap<>();
    /** EventBus 提供者（模块注入 capability）。 */
    private Supplier<EventBusCapability> eventBusProvider = () -> null;
    /** 宝箱 UI 处理器（AbyssUiService 实现后注入；默认纯发放实现）。 */
    private volatile ChestUiHandler chestUiHandler = this::defaultChestHandler;
    /** 玩家命令/UI 桥（AbyssUiService 注入）。 */
    private volatile UiServiceFacade uiFacade;

    private ScheduledTask watchdogTask;
    private boolean started;

    public AbyssService(AbyssModule module, Plugin plugin, AbyssModuleConfiguration config,
                        @Nullable PacketBridgeAPI packetBridge,
                        @Nullable ItemBridgeAPI itemStackBridge,
                        ItemSourceRegistry itemSourceRegistry,
                        ItemRewardDispatcher itemRewardDispatcher,
                        PendingRewardService pendingRewardService,
                        ItemMatcherAPI itemMatcher,
                        VanillaItemNameBridge vanillaItemNameBridge,
                        CurrencyBridgeAPI currencyManager,
                        AttributeBridgeRegistry attributeBridge,
                        AriaBridge ariaBridge,
                        ScriptConditionEvaluator scriptConditionEvaluator,
                        PlaceholderResolverAPI placeholderResolver,
                        StorageManager storageManager,
                        SchedulerAPI scheduler,
                        CrossServerAPI crossServer,
                        Logger logger, MessageProvider messages) {
        this.module = module;
        this.plugin = plugin;
        this.config = config;
        this.packetBridge = packetBridge;
        this.itemStackBridge = itemStackBridge;
        this.itemSourceRegistry = itemSourceRegistry;
        this.itemRewardDispatcher = itemRewardDispatcher;
        this.pendingRewardService = pendingRewardService;
        this.itemMatcher = itemMatcher;
        this.vanillaItemNameBridge = vanillaItemNameBridge;
        this.currencyManager = currencyManager;
        this.attributeBridge = attributeBridge;
        this.ariaBridge = ariaBridge;
        this.scriptConditionEvaluator = scriptConditionEvaluator;
        this.placeholderResolver = placeholderResolver;
        this.storageManager = storageManager;
        this.scheduler = scheduler;
        this.crossServer = crossServer;
        this.logger = logger;
        this.messages = messages;
    }

    // ═══ 启动/停止 ═══

    public void start(File dataFolder) throws Exception {
        this.moduleDataFolder = dataFolder;
        // 1) 数据层（共享/自建双模式：resolveModuleDataSource 统一解析）
        AbyssModuleConfiguration.StorageSection storage = config.storage();
        xuanmo.arcartxsuite.api.storage.StorageDescriptor desc = storage.hasOverride()
            ? storage.toDescriptor()
            : storageManager.getDescriptor().withTablePrefix(storage.tablePrefix());
        javax.sql.DataSource ds = storageManager.resolveModuleDataSource("abyss",
            storage.sqliteFileName(), dataFolder,
            storage.hasOverride() ? storage.toDescriptor() : null);
        repository = new JdbcAbyssRepository(ds, desc, dataFolder, logger);
        repository.initialize();
        playerData = new PlayerDataManager(repository, logger);

        // 2) 模板加载（首次启动导出示例地牢）
        templateLoader = new AbyssTemplateLoader(dataFolder, logger);
        exportExampleDungeon(dataFolder);
        instances = new AbyssInstanceManager();
        reloadTemplates();

        // 3) 世界池 + 残留世界清理 + 预热池
        worldPool = new AbyssWorldPool(plugin, config.abyss(), logger);
        int orphans = worldPool.cleanupOrphanWorlds();
        if (orphans > 0) {
            logger.info("[abyss] 清理残留副本世界 x" + orphans);
        }
        recoveryManager = new RecoveryManager(dataFolder, config.recovery(), logger, links);

        // 4) 脚本系统
        scriptRunner = new ScriptRunner(plugin, scheduler, () -> this, logger);

        // 5) 怪物组（MythicMobs 可选依赖守卫）
        mobProvider = createMobProvider();
        mobGroups = new MonsterGroupManager(plugin, scheduler, logger);
        mobGroups.setCallback(new GroupCallbackImpl());

        // 6) 子管理器
        obstacles = new ObstacleManager(logger);
        obstacles.setCallbacks((inst, e) -> runScript(inst, e.script(),
            ScriptContext.withVars(inst, e.trigger(),
                Map.of("obstacle", e.obstacleName(), "kind", e.kind()))),
            this::locParser);
        interacts = new InteractManager(scriptConditionEvaluator, logger);
        interacts.setCallbacks((inst, e) -> runScript(inst, e.script(),
            ScriptContext.withVars(inst, e.trigger(), Map.of("interact", e.interactName()))),
            this::locParser);
        interacts.setExternalItemMatcher((req, hand) -> rewards.matches(req, hand));
        puzzleManager = new xuanmo.arcartxsuite.abyss.puzzle.PuzzleManager(logger);
        puzzleManager.setCallback(new PuzzleCallbackImpl());
        interacts.setPuzzleCallback((inst, e) ->
            puzzleManager.onInteractFired(inst, e.interactName(), e.trigger()));
        instanceTasks = new InstanceTaskScheduler(plugin, scheduler, logger);
        instanceTasks.setFireCallback((inst, f) -> runScript(inst, f.script(),
            ScriptContext.withVars(inst, null, f.vars())));
        npcs = new DungeonNpcManager(plugin, scheduler, logger);
        npcs.setNpcBridge(npcBridge);
        npcs.setCallbacks((inst, e) -> runScript(inst, e.config().clickScript(),
            ScriptContext.withVars(inst, e.player(), Map.of("npc", e.npcName()))),
            this::locParser,
            instances::getInstance,
            (player, npcId) -> links != null && links.conversationOpen(player, npcId));
        npcs.initInteractHandler();

        // 6.5) 楼层/房间（floors 模式）
        roomManager = new xuanmo.arcartxsuite.abyss.room.RoomManager(
            plugin, scheduler, mobGroups,
            new xuanmo.arcartxsuite.abyss.world.SchematicWorldService(plugin, logger),
            logger);
        roomManager.setCallback(new RoomCallbackImpl());

        // 7) 奖励/体力/统计/排行/冷却/条件
        rewards = new RewardResolver(itemSourceRegistry, itemRewardDispatcher,
            pendingRewardService, currencyManager, logger);
        quests = new QuestManager(this::links, plugin, scheduler);
        stamina = new StaminaManager(config.stamina(), playerData);
        stats = new CombatStatisticsService(config.statistics());
        leaderboard = new LeaderboardService(config.leaderboard(), playerData);
        cooldown = new CooldownManager(playerData);
        requirementChecker = new RequirementChecker(scriptConditionEvaluator,
            currencyManager, stamina, cooldown, rewards);

        // 8) 队伍
        partyManager = new PartyManager(config.party(), s -> { }, this::publishEvent);

        // 8.5) ArcartX UI 服务（浏览器/队伍/宝箱/HUD/排行统计 + 客户端包路由）
        AbyssUiService ui = new AbyssUiService(this, packetBridge, itemStackBridge, logger);
        uiFacade = ui;
        chestUiHandler = ui;
        if (ui.available()) {
            logger.info("[abyss] ArcartX UI 已接入（浏览/队伍/宝箱/HUD/排行）");
        } else {
            logger.info("[abyss] ArcartX 不可用，UI 功能禁用（宝箱走直接发放兜底）");
        }

        // 8.7) 属性桥伤害/治疗事件 → 战斗统计
        if (links != null && stats != null) {
            links.wireAttributeStats(stats, instances::getInstance);
        }

        // 8.8) 地图入口标记（map 模块缺席时静默）
        if (links != null) {
            entranceMarkers = new xuanmo.arcartxsuite.abyss.links.EntranceMarkerService(
                links, instances::getAllTemplates, eventBusProvider, logger);
            entranceMarkers.start();
            entranceMarkers.pushAll();
        }

        // 8.9) 世界入口点 + 全服共享计数器
        entranceManager = new xuanmo.arcartxsuite.abyss.interact.EntranceManager(logger);
        entranceManager.rebuild(instances.getAllTemplates());
        globalCounters = new GlobalCounterService(playerData,
            config::globalThresholds, (topic, data) -> publishEvent(topic, null, data));
        globalCounters.start();

        // 8.10) 副本内引导（路标/路径渲染）+ 增益选择池
        if (waypointBridge != null) {
            guideService = new xuanmo.arcartxsuite.abyss.guide.GuideService(
                (JavaPlugin) plugin, waypointBridge, logger);
            guideService.setArriveCallback((inst, navId, player) -> {
                publishEvent("axs.abyss.guide_arrive", player, Map.of(
                    "dungeon", inst.getTemplate().id(),
                    "instance", inst.getInstanceId().toString(),
                    "nav", navId));
                // scripts.yml 自定义段 guide_<navId> 存在则跑（可选钩）
                var seg = inst.getTemplate().scripts().customs().get("guide_" + navId);
                if (seg != null && !seg.isEmpty()) {
                    runScript(inst, seg, ScriptContext.withVars(inst, player,
                        Map.of("nav", navId)));
                }
            });
            guideService.start();
        } else {
            logger.info("[abyss] WaypointBridge 不可用，guide_point 引导禁用");
        }
        buffManager = new xuanmo.arcartxsuite.abyss.buff.BuffManager(
            plugin, scheduler, logger);
        buffManager.setCallback(new BuffCallbackImpl());

        // 8.11) 设计模式（在实例内可视化编辑地牢配置）
        designManager = new xuanmo.arcartxsuite.abyss.design.DesignManager(
            logger, (p, m) -> p.sendMessage(m));
        designManager.setMsgResolver((key, kv) -> msg(key, kv));
        designManager.setUi(ui);
        designListener = new xuanmo.arcartxsuite.abyss.design.DesignListener(
            plugin, designManager);
        org.bukkit.Bukkit.getPluginManager().registerEvents(designListener, plugin);

        // 9) 管理命令
        adminCommand = new AbyssAdminCommand(this, logger);

        // 10) 周期看守：超时/无人/消失扫描/障碍触发/区域交互 合一
        watchdogTask = scheduler.scheduleEverySeconds(plugin, this::watchdog,
            config.abyss().instanceCheckInterval(), 10 /* 短周期轮询含触发器 */);
        // 触发器需更细粒度：10s 太粗——另起 1s 轮询做 proximity/region
        proximityTask = scheduler.scheduleEverySeconds(plugin, this::proximityTick, 1, 1);

        // 11) 副本预热池（预热世界就绪后进入秒开）
        Map<String, AbyssTemplate> tplMap = new java.util.LinkedHashMap<>();
        for (AbyssTemplate t : instances.getAllTemplates()) {
            tplMap.put(t.id(), t);
        }
        worldPool.initPreload(tplMap, config.abyss().preload());

        subscribeQuestCompleted(); // EventBus 晚注册时补订（幂等）
        started = true;
        logger.info("[abyss] 已启动，地牢模板: " + instances.getAllTemplates().size());
    }

    private ScheduledTask proximityTask;

    public void shutdown() {
        started = false;
        if (watchdogTask != null) {
            try { watchdogTask.cancel(); } catch (Throwable ignored) { }
        }
        if (proximityTask != null) {
            try { proximityTask.cancel(); } catch (Throwable ignored) { }
        }
        // 回收全部实例（玩家送回）
        if (instances != null) {
            for (AbyssInstance i : new ArrayList<>(instances.getAllInstances())) {
                try { destroyInstance(i, true); } catch (Throwable ignored) { }
            }
        }
        if (designListener != null) {
            org.bukkit.event.HandlerList.unregisterAll(designListener);
            designListener = null;
        }
        if (mobGroups != null) mobGroups.shutdown();
        if (roomManager != null) roomManager.shutdown();
        if (guideService != null) guideService.shutdown();
        if (npcs != null) npcs.shutdown();
        // 退订 questgps 完成事件
        if (questCompletedSubId != null && eventBusProvider != null) {
            try {
                EventBusCapability bus = eventBusProvider.get();
                if (bus != null) bus.unsubscribe(questCompletedSubId);
            } catch (Throwable ignored) { }
            questCompletedSubId = null;
        }
        if (entranceMarkers != null) entranceMarkers.shutdown();
        if (partyManager != null) partyManager.shutdown();
        if (worldPool != null) {
            worldPool.releasePreload();
            worldPool.shutdown();
        }
        if (playerData != null) playerData.shutdown();
        if (repository != null) repository.shutdown();
        try {
            storageManager.closeModuleDataSource("abyss");
        } catch (Throwable ignored) { }
        instances = null;
        started = false;
    }

    /** 启动时把 jar 内示例地牢的缺失文件导出到 data/abyss/abysses/example（逐文件判定，不覆盖已有）。 */
    private void exportExampleDungeon(File dataFolder) {
        File example = new File(dataFolder, "abysses/example");
        String[] files = {
            "config.yml", "monsters.yml", "scripts.yml", "rewards.yml",
            "interacts.yml", "obstacles.yml", "tasks.yml", "npcs.yml", "quests.yml",
            "floors.yml", "rooms.yml", "puzzles.yml", "buffs.yml",
            // 外部系统配套（Chemdah 会话/任务 + questgps 呈现层；由 installer 部署）
            "external/README.md",
            "external/chemdah/conversation/sk_guide.yml",
            "external/chemdah/conversation/sk_merchant.yml",
            "external/chemdah/quest/sk_slayer.yml",
            "external/questgps/sk_slayer.yml"
        };
        for (String f : files) {
            File target = new File(example, f);
            if (target.isFile()) continue;
            try {
                module.exportModuleResource("abysses/example/" + f, target, false);
            } catch (Throwable t) {
                logger.fine("[abyss] 示例资源导出失败 " + f + ": " + t.getMessage());
            }
        }
    }

    /** 重载模板（/axs reload abyss 时模块会重建服务；另供 admin reload 用）。 */
    public void reloadTemplates() {
        instances.clearTemplates();
        var loaded = templateLoader.loadAll();
        loaded.values().forEach(instances::registerTemplate);
        // 模板来源可能变化：重建预热池（运行中实例不受影响，世界已绑定实例id）
        if (worldPool != null) {
            worldPool.releasePreload();
            Map<String, AbyssTemplate> tplMap = new java.util.LinkedHashMap<>();
            for (AbyssTemplate t : instances.getAllTemplates()) {
                tplMap.put(t.id(), t);
            }
            worldPool.initPreload(tplMap, config.abyss().preload());
        }
        // 入口配置可能变化：重推地图标记 + 重建世界入口点索引
        if (entranceMarkers != null) {
            entranceMarkers.pushAll();
        }
        if (entranceManager != null) {
            entranceManager.rebuild(instances.getAllTemplates());
        }
        // 外部配套文件部署（external/ → Chemdah/questgps 目录；缺席即跳过）
        if (externalInstaller == null && moduleDataFolder != null) {
            externalInstaller = new xuanmo.arcartxsuite.abyss.integration.DungeonExternalInstaller(
                moduleDataFolder, this::links, logger);
        }
        if (externalInstaller != null) {
            externalInstaller.installAll();
        }
    }

    private MobProvider createMobProvider() {
        MobProvider vanilla = new VanillaMobProvider(logger);
        MobProvider mythic = null;
        if (Bukkit.getPluginManager().isPluginEnabled("MythicMobs")
            || Bukkit.getPluginManager().isPluginEnabled("MythicBukkit")) {
            try {
                MythicMobProvider p = new MythicMobProvider(logger);
                if (p.available()) {
                    mythic = p;
                    logger.info("[abyss] MythicMobs 已接入");
                }
            } catch (Throwable t) {
                logger.warning("[abyss] MythicMobs 适配失败，降级原版实体: " + t.getMessage());
            }
        } else {
            logger.info("[abyss] 未检测到 MythicMobs，怪物生成走原版实体（vanilla: 前缀可用）");
        }
        return new CompositeMobProvider(mythic, vanilla, logger);
    }

    // ═══ 监听器集合 ═══

    public List<Listener> moduleListeners() {
        List<Listener> list = new ArrayList<>();
        list.add(new AbyssPlayerListener(this, logger));
        list.add(new AbyssInteractListener(this, logger));
        list.add(new AbyssSpawnListener(this, logger));
        // MM 死亡中继：独立类，仅 MM 启用时注册（可选依赖隔离）
        if (mobProvider instanceof CompositeMobProvider cp && cp.hasMythic()) {
            try {
                list.add(new MythicMobDeathRelay(mobGroups,
                    (entity, world) -> findInstanceByWorld(world)));
            } catch (Throwable t) {
                logger.warning("[abyss] MM 死亡中继注册失败: " + t.getMessage());
            }
        }
        return list;
    }

    // ═══ 查询 ═══

    public AbyssInstanceManager instances() { return instances; }
    public PartyManager party() { return partyManager; }
    public CooldownManager cooldowns() { return cooldown; }
    public StaminaManager stamina() { return stamina; }
    public LeaderboardService leaderboard() { return leaderboard; }
    public CombatStatisticsService stats() { return stats; }
    public MonsterGroupManager mobGroups() { return mobGroups; }

    /** floors 模式房间管理器（classic 模式也存在但无运行态）。 */
    public @Nullable xuanmo.arcartxsuite.abyss.room.RoomManager roomManager() {
        return roomManager;
    }
    public ObstacleManager obstacles() { return obstacles; }
    public InteractManager interacts() { return interacts; }
    public DungeonNpcManager npcs() { return npcs; }
    public RewardResolver rewards() { return rewards; }
    public QuestManager quests() { return quests; }
    public InstanceTaskScheduler instanceTasks() { return instanceTasks; }
    public ScriptRunner scripts() { return scriptRunner; }
    public AbyssModuleConfiguration config() { return config; }
    public AbyssModule module() { return module; }
    public AbyssAdminCommand adminCommand() { return adminCommand; }
    public PlayerDataManager playerData() { return playerData; }
    public JdbcAbyssRepository repository() { return repository; }
    public @Nullable GlobalCounterService globalCounters() { return globalCounters; }
    public @Nullable xuanmo.arcartxsuite.abyss.interact.EntranceManager entranceManager() {
        return entranceManager;
    }
    public @Nullable xuanmo.arcartxsuite.abyss.puzzle.PuzzleManager puzzles() {
        return puzzleManager;
    }
    public @Nullable xuanmo.arcartxsuite.abyss.guide.GuideService guide() {
        return guideService;
    }
    public @Nullable xuanmo.arcartxsuite.abyss.buff.BuffManager buffs() {
        return buffManager;
    }
    /** WaypointBridge 由模块创建后注入（start 之前调用）。 */
    public void setWaypointBridge(
        xuanmo.arcartxsuite.api.bridge.WaypointBridgeAPI bridge) {
        this.waypointBridge = bridge;
    }
    public SchedulerAPI scheduler() { return scheduler; }
    public MessageProvider messages() { return messages; }
    public @Nullable PacketBridgeAPI packetBridge() { return packetBridge; }
    public AttributeBridgeRegistry attributeBridge() { return attributeBridge; }
    public PlaceholderResolverAPI placeholderResolver() { return placeholderResolver; }

    public void setEventBusProvider(Supplier<EventBusCapability> provider) {
        this.eventBusProvider = provider;
        subscribeQuestCompleted();
    }

    /** 订阅 questgps 任务完成事件 → 副本任务钩子（幂等；总线缺席下次重试）。 */
    private void subscribeQuestCompleted() {
        if (questCompletedSubId != null) return;
        EventBusCapability bus;
        try {
            bus = eventBusProvider.get();
        } catch (Throwable t) {
            return;
        }
        if (bus == null) return;
        try {
            questCompletedSubId = bus.subscribe("axs.questgps.quest_completed", event -> {
                Player player = event.player();
                if (player == null || quests == null || instances == null) return;
                AbyssInstance inst = instances.getInstance(player);
                if (inst == null) return;
                String questId = event.payload().get("quest_id");
                quests.onQuestCompleted(inst, player, scriptRunner, questId);
            });
        } catch (Throwable t) {
            logger.fine("[abyss] 订阅 quest_completed 失败: " + t.getMessage());
        }
    }

    public void setLinks(xuanmo.arcartxsuite.abyss.links.AbyssLinks links) {
        this.links = links;
    }

    public void setNpcBridge(xuanmo.arcartxsuite.api.bridge.AdyeshachNpcBridgeAPI bridge) {
        this.npcBridge = bridge;
    }

    public @Nullable xuanmo.arcartxsuite.abyss.links.AbyssLinks links() {
        return links;
    }

    public void setChestUiHandler(ChestUiHandler handler) {
        if (handler != null) this.chestUiHandler = handler;
    }

    public void setUiFacade(@Nullable UiServiceFacade facade) {
        this.uiFacade = facade;
    }

    public @Nullable UiServiceFacade uiFacade() { return uiFacade; }

    // ═══ 进入流程 ═══

    public record EnterResult(boolean ok, @Nullable String reason) {
        public static EnterResult fail(String r) { return new EnterResult(false, r); }
        public static EnterResult success() { return new EnterResult(true, null); }
    }

    /** 玩家单人进本入口。 */
    public EnterResult enterDungeon(Player player, String templateId) {
        return enterDungeon(List.of(player), templateId, null);
    }

    /** 队伍进本入口（无方案）。 */
    public EnterResult enterDungeon(List<Player> players, String templateId) {
        return enterDungeon(players, templateId, null);
    }

    /**
     * 进本编排：
     * 校验 → 建实例 → 异步复制世界 → 存档 → 传送 → 全员进图 → init → start。
     *
     * @param plan 作战方案 id（模板定义了 plans 时生效；null=默认首项；
     *             无效 id 拒绝。模板未定义 plans 时忽略）
     */
    public EnterResult enterDungeon(List<Player> players, String templateId,
                                    @Nullable String plan) {
        if (!started || players.isEmpty()) {
            return EnterResult.fail("服务未就绪");
        }
        AbyssTemplate template = instances.getTemplate(templateId);
        if (template == null) {
            return EnterResult.fail(msg("error.dungeon-not-found", "dungeon", templateId));
        }
        if (instances.getInstance(players.get(0)) != null) {
            return EnterResult.fail(msg("error.already-in-dungeon"));
        }
        if (!instances.hasAvailableSlot(template)) {
            return EnterResult.fail("副本实例已达上限");
        }
        // 预热严格模式：配置了预热的地牢池空则拒绝进入
        var preloadCfg = config.abyss().preload();
        if (preloadCfg.enabled() && preloadCfg.strict()
            && preloadCfg.dungeons().containsKey(template.id())
            && worldPool.preparedCount(template.id()) == 0) {
            return EnterResult.fail(msg("error.preload-empty"));
        }
        // 队伍：无论进本人数都取发起者队伍——party-mode 校验需要单人也识别 guild 队
        Party party = partyManager.partyOf(players.get(0));
        RequirementChecker.CheckResult check = requirementChecker.check(template, players, party);
        if (!check.ok()) {
            return EnterResult.fail(msg("error.requirements-failed", "reason", check.reason()));
        }
        // questgps 主线门禁：abyss.<副本id> 被 gate.blocked-module-entries 声明时，
        // 未完成主线的队员拒绝进入（questgps 未装载时恒放行）
        if (links != null) {
            for (Player member : players) {
                if (links.moduleEntryLocked(member, "abyss." + template.id())) {
                    return EnterResult.fail(msg("dungeon.module-locked"));
                }
            }
        }
        // floors+pool 模式需要 WorldEdit/FAWE 粘贴房间；缺席直接拒绝（不传进虚空再失败）
        if (template.mode() == AbyssTemplate.Mode.FLOORS && roomManager != null) {
            boolean hasPool = template.floors().floors().stream()
                .anyMatch(f -> f.source() == xuanmo.arcartxsuite.abyss.template.FloorConfig.Source.POOL);
            if (hasPool && !roomManager.isWorldEditAvailable()) {
                return EnterResult.fail("该地牢需要 WorldEdit/FAWE 支持（服务端未安装）");
            }
        }
        // 作战方案：模板定义 plans 时必须落定一个有效方案（null=默认首项）
        String resolvedPlan = null;
        if (!template.plans().isEmpty()) {
            if (plan == null || plan.isBlank()) {
                resolvedPlan = template.plans().keySet().iterator().next();
            } else if (!template.plans().containsKey(plan)) {
                return EnterResult.fail(msg("error.plan-unknown", "plan", plan));
            } else {
                resolvedPlan = plan;
            }
        }

        AbyssInstance instance = instances.createInstance(template, party);
        // 方案写入实例变量：{plan}=id、{plan_name}=显示名；怪组 plans 过滤也读这里
        if (resolvedPlan != null) {
            instance.getVariables().put("plan", resolvedPlan);
            AbyssTemplate.PlanEntry pe = template.plans().get(resolvedPlan);
            instance.getVariables().put("plan_name", pe != null ? pe.name() : resolvedPlan);
        }
        for (Player p : players) {
            instances.playerEnterInstance(p, instance);
        }
        broadcast(instance, msg("dungeon.preparing"));
        publishEvent("axs.abyss.enter", players.get(0),
            resolvedPlan != null
                ? Map.of("dungeon", template.id(), "instance", instance.getInstanceId().toString(),
                         "players", players.size(), "plan", resolvedPlan)
                : Map.of("dungeon", template.id(), "instance", instance.getInstanceId().toString(),
                         "players", players.size()));
        // 玩家级事件：战令/任务等按玩家推进度的订阅方消费
        for (Player p : players) {
            publishEvent("axs.abyss.player_enter", p,
                resolvedPlan != null
                    ? Map.of("dungeon", template.id(), "instance",
                             instance.getInstanceId().toString(),
                             "party-size", players.size(), "plan", resolvedPlan)
                    : Map.of("dungeon", template.id(), "instance",
                             instance.getInstanceId().toString(),
                             "party-size", players.size()));
        }

        // 异步申请世界
        worldPool.acquireWorld(template, instance.getInstanceId())
            .thenAccept(world -> runOnMain(() -> onWorldReady(instance, world, players)))
            .exceptionally(t -> {
                runOnMain(() -> {
                    broadcast(instance, msg("error.world-load-failed"));
                    logger.warning("[abyss] 世界加载失败 实例 " + instance.getInstanceId()
                        + ": " + t.getMessage());
                    destroyInstance(instance, true);
                });
                return null;
            });
        return EnterResult.success();
    }

    // ═══ 设计模式 ═══

    public @Nullable xuanmo.arcartxsuite.abyss.design.DesignManager designManager() {
        return designManager;
    }

    public @Nullable xuanmo.arcartxsuite.abyss.design.DesignListener designListener() {
        return designListener;
    }

    /** 是否为设计实例（冻结玩法、编辑配置用）。 */
    public boolean isDesignInstance(AbyssInstance instance) {
        return designManager != null && designManager.isDesign(instance);
    }

    /**
     * 进入设计模式：建冻结实例（不跑 prepare/startInstance——怪组/任务/机关/
     * 障碍/interact 全不初始化）→ 世界就绪后传送 + 创造模式 + 发标记杖 + 开主面板。
     * <p>
     * 仅 floors-region 与 classic 支持；floors-pool 因房间随机拼接无法在实例内设计。
     */
    public EnterResult enterDesign(Player player, String templateId) {
        if (!started || designManager == null) {
            return EnterResult.fail("服务未就绪");
        }
        AbyssTemplate template = instances.getTemplate(templateId);
        if (template == null) {
            return EnterResult.fail(msg("error.dungeon-not-found", "dungeon", templateId));
        }
        if (instances.getInstance(player) != null || designManager.session(player) != null) {
            return EnterResult.fail(msg("error.already-in-dungeon"));
        }
        if (template.mode() == AbyssTemplate.Mode.FLOORS) {
            boolean hasPool = template.floors().floors().stream()
                .anyMatch(f -> f.source() == xuanmo.arcartxsuite.abyss.template.FloorConfig.Source.POOL);
            if (hasPool) {
                return EnterResult.fail(msg("design.pool-unsupported"));
            }
        }
        AbyssInstance instance = instances.createInstance(template, null);
        instances.playerEnterInstance(player, instance);
        player.sendMessage(msg("design.preparing"));
        worldPool.acquireWorld(template, instance.getInstanceId())
            .thenAccept(world -> runOnMain(() -> onDesignWorldReady(instance, world, player)))
            .exceptionally(t -> {
                runOnMain(() -> {
                    player.sendMessage(msg("error.world-load-failed"));
                    logger.warning("[abyss] 设计实例世界加载失败: " + t.getMessage());
                    destroyInstance(instance, true);
                });
                return null;
            });
        return EnterResult.success();
    }

    private void onDesignWorldReady(AbyssInstance instance, World world, Player player) {
        if (instance.isFinished() || !player.isOnline()) {
            worldPool.releaseWorld(instance.getInstanceId());
            return;
        }
        instance.setWorld(world);
        worldIndex.put(world.getName(), instance.getInstanceId());
        Location spawn = parseLocation(instance, instance.getTemplate().spawn());
        if (spawn == null) spawn = world.getSpawnLocation();
        instance.saveOriginalLocation(player, player.getLocation());
        instance.savePlayerGameMode(player);
        // 设计模式不跑脚本——虚空模板同样要落点兜底
        ensureFooting(world, spawn);
        if (!teleportWithRetry(player, spawn, 3)) {
            player.sendMessage(msg("error.enter-failed", "reason", "传送失败"));
            destroyInstance(instance, true);
            return;
        }
        instance.markEntered(player.getUniqueId());
        // 直接激活：跳过 prepare/startInstance——玩法系统全冻结
        instance.transitionTo(AbyssStatus.PREPARING, AbyssStatus.ACTIVE);
        player.setGameMode(GameMode.CREATIVE);
        var session = designManager.open(player, instance);
        // 发标记杖（第一空格）
        player.getInventory().setItem(0, designListener.wandItem());
        player.sendMessage(msg("design.entered",
            "dungeon", instance.getTemplate().name()));
        if (uiFacade instanceof AbyssUiService ui) {
            ui.openDesignMain(player, session);
        }
    }

    /**
     * 保存设计：会话快照写回地牢目录 YAML；withWorld 且模板为 FOLDER 源时
     * 同步把实例世界存盘并回拷模板 world/ 目录。完成后重载模板并刷新会话快照。
     */
    public void saveDesign(Player player, boolean withWorld) {
        var session = designManager != null ? designManager.session(player) : null;
        if (session == null) {
            player.sendMessage(msg("design.no-session"));
            return;
        }
        AbyssInstance instance = session.instance();
        AbyssTemplate template = instance.getTemplate();
        if (withWorld) {
            if (template.worldSource().kind() != AbyssTemplate.WorldSource.Kind.FOLDER) {
                player.sendMessage(msg("design.world-unsupported"));
            } else {
                World w = instance.getWorld();
                File srcDir = worldPool != null ? worldPool.worldDirOf(instance.getInstanceId()) : null;
                File dstDir = template.worldSource().file();
                if (w != null && srcDir != null && dstDir != null) {
                    try {
                        w.save(); // 同步落盘区块
                    } catch (Throwable t) {
                        logger.warning("[abyss] 设计世界保存失败: " + t.getMessage());
                    }
                    // 目录回拷走世界池 IO 线程
                    final File src = srcDir, dst = dstDir;
                    worldPool.runIo(() -> {
                        try {
                            xuanmo.arcartxsuite.abyss.world.WorldCopyUtil.copyWorldFolder(src, dst);
                        } catch (Throwable t) {
                            logger.warning("[abyss] 设计世界回写失败: " + t.getMessage());
                        }
                    });
                }
            }
        }
        var result = xuanmo.arcartxsuite.abyss.design.DesignWriter.save(template, session, logger);
        reloadTemplates();
        // 保存后刷新会话快照（对象表重建、dirty 清零）
        designManager.refresh(session);
        player.sendMessage(msg(result.ok() ? "design.saved" : "design.saved-partial",
            "count", String.valueOf(result.objects()),
            "del", String.valueOf(result.deletions()),
            "errors", String.valueOf(result.errors().size())));
    }

    /** 重开设计主面板（/axs abyss design ui）。 */
    public void openDesignUi(Player player) {
        var session = designManager != null ? designManager.session(player) : null;
        if (session == null) {
            player.sendMessage(msg("design.no-session"));
            return;
        }
        designManager.openMain(player);
    }

    /** 退出设计模式：收杖 → 撤出 → 销毁实例（未保存改动丢失）。 */
    public void exitDesign(Player player) {
        var session = designManager != null ? designManager.session(player) : null;
        if (session == null) return;
        AbyssInstance instance = session.instance();
        designManager.close(player);
        designListener.removeWand(player);
        if (uiFacade instanceof AbyssUiService ui) {
            ui.closeAll(player);
        }
        // 传回原位 + 恢复模式 + 销毁实例
        playerExit(instance, player, LeaveReason.END);
        cancelDungeon(instance);
        player.sendMessage(msg("design.exited"));
    }

    // ═══ 世界入口点 ═══

    /**
     * 世界入口点击触发（监听器调用，玩家不在副本内时）。
     * 命中入口 → 队伍带入 → 方案落定（入口固定 plan 或多方案弹选择菜单）→ enterDungeon。
     *
     * @return true=命中入口（调用方应 cancel 交互事件，防门/箱子真的打开）
     */
    public boolean tryEntranceEnter(org.bukkit.entity.Player player, org.bukkit.block.Block block) {
        if (entranceManager == null) return false;
        var hit = entranceManager.match(block);
        if (hit == null) return false;
        AbyssTemplate template = hit.template();
        AbyssTemplate.EntrancePoint ep = hit.point();

        // 队伍带入：发起者是队长则带全队
        Party party = partyManager.partyOf(player);
        List<Player> members =
            (party != null && party.isLeader(player.getUniqueId()) && party.size() > 1)
                ? party.onlineMembers() : List.of(player);

        // 方案落定：入口固定 plan 直接用；模板多方案且未固定 → 弹选择菜单
        String plan = ep.plan();
        if ((plan == null || plan.isBlank()) && template.plans().size() > 1) {
            sendPlanMenu(player, template);
            return true;
        }
        EnterResult r = enterDungeon(members, template.id(), plan);
        if (!r.ok() && r.reason() != null) {
            player.sendMessage(msg("error.enter-failed", "reason", r.reason()));
        }
        return true;
    }

    /** 方案选择菜单：可点击聊天行（点击执行 /abyss enter <dungeon> <plan>）。 */
    private void sendPlanMenu(org.bukkit.entity.Player player, AbyssTemplate template) {
        player.sendMessage(msg("plan.menu-title", "dungeon", template.name()));
        for (AbyssTemplate.PlanEntry pe : template.plans().values()) {
            // msg() 内 & 已转 §；方案名/lore 里的 & 是配置原文，需二次转色
            net.md_5.bungee.api.chat.TextComponent line =
                new net.md_5.bungee.api.chat.TextComponent(
                    org.bukkit.ChatColor.translateAlternateColorCodes('&',
                        msg("plan.menu-row", "name", pe.name())));
            line.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND,
                "/abyss enter " + template.id() + " " + pe.id()));
            for (String lore : pe.lore()) {
                line.addExtra(new net.md_5.bungee.api.chat.TextComponent(
                    org.bukkit.ChatColor.translateAlternateColorCodes('&',
                        "\n  &7" + lore)));
            }
            player.spigot().sendMessage(line);
        }
    }

    /** 世界就绪：传送玩家 → 全员进图 → prepare → start。 */
    private void onWorldReady(AbyssInstance instance, World world, List<Player> players) {
        if (instance.isFinished()) {
            worldPool.releaseWorld(instance.getInstanceId());
            return;
        }
        instance.setWorld(world);
        worldIndex.put(world.getName(), instance.getInstanceId());
        broadcast(instance, msg("dungeon.world-ready"));

        Location spawn = spawnLocation(instance);
        if (spawn == null) {
            broadcast(instance, msg("error.enter-failed", "reason", "出生点无效"));
            destroyInstance(instance, true);
            return;
        }

        // world-init 段：玩家传送前跑（造平台/铺场景），init 保持原位（进图后）
        runLifecycleScript(instance, "world_init", null);
        // 落点兜底：spawn 下方 4 格无支撑时垫一块，防虚空模板直接坠亡
        ensureFooting(world, spawn);

        // 扣费（世界就绪后才扣，失败不花钱）
        requirementChecker.charge(instance.getTemplate(), players);

        // 逐人存档+传送
        for (Player p : players) {
            if (!p.isOnline() || instance.isFinished()) continue;
            instance.saveOriginalLocation(p, p.getLocation());
            instance.savePlayerGameMode(p);
            boolean ok = teleportWithRetry(p, spawn, 3);
            if (ok) {
                instance.markEntered(p.getUniqueId());
            } else {
                // 传送失败：移出实例并送回
                instance.removePlayer(p.getUniqueId());
                p.sendMessage(msg("error.enter-failed", "reason", "传送失败"));
            }
        }
        if (instance.getPlayerUUIDs().isEmpty()) {
            destroyInstance(instance, true);
            return;
        }

        // 全员进图判定：等不到的走 5s 兜底（掉线玩家直接剔除）
        scheduleOnce(instance, () -> {
            for (UUID u : new ArrayList<>(instance.getPlayerUUIDs())) {
                if (!instance.allEntered()) {
                    Player p = Bukkit.getPlayer(u);
                    if (p == null || !p.isOnline()) {
                        instance.removePlayer(u);
                    }
                }
            }
            if (!instance.getPlayerUUIDs().isEmpty()) {
                prepare(instance);
            } else {
                destroyInstance(instance, true);
            }
        }, 100L);
        // 若已全员进图直接 prepare
        if (instance.allEntered()) {
            prepare(instance);
        }
    }

    /**
     * 落点兜底：spawn 下方 4 格内无可站立方块时，在脚下铺一个 5×5 实心平台。
     * void/无地形模板未写 world-init 也不会坠虚空。
     */
    private void ensureFooting(World world, Location spawn) {
        int sx = spawn.getBlockX(), sy = spawn.getBlockY(), sz = spawn.getBlockZ();
        for (int dy = 1; dy <= 4; dy++) {
            if (world.getBlockAt(sx, sy - dy, sz).getType().isSolid()) return;
        }
        int py = sy - 1;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                world.getBlockAt(sx + dx, py, sz + dz).setType(Material.BEDROCK, false);
            }
        }
        logger.info("[abyss] " + world.getName() + " 出生点无支撑，已垫 5x5 兜底平台");
    }

    private void prepare(AbyssInstance instance) {
        if (instance.getStatus() != AbyssStatus.PREPARING) return;
        // init 脚本
        runLifecycleScript(instance, "init", null);
        // 40 tick 后 start（等待玩家传送稳定）
        scheduleOnce(instance, () -> startInstance(instance), 40L);
    }

    private void startInstance(AbyssInstance instance) {
        if (!instance.transitionTo(AbyssStatus.PREPARING, AbyssStatus.ACTIVE)) return;
        AbyssTemplate t = instance.getTemplate();
        // 怪物组（AUTO_START/DELAYED 自动调度）
        mobGroups.initInstance(instance);
        // floors 模式：物化楼层房间链（pool 粘贴/组注册，须在怪组表建好后）
        if (t.mode() == AbyssTemplate.Mode.FLOORS && roomManager != null) {
            try {
                roomManager.initInstance(instance);
                // 物化完成后对齐到首层入口（进图时房间尚未生成，落点是模板 spawn）
                Location entry = roomManager.currentSpawn(instance);
                if (entry != null) {
                    for (Player p : instance.getPlayers()) {
                        teleportWithRetry(p, entry, 3);
                    }
                }
            } catch (Throwable ex) {
                logger.warning("[abyss] floors 物化失败 " + t.id() + ": " + ex.getMessage());
                failDungeon(instance, FailReason.SCRIPTED);
                return;
            }
        }
        // 障碍物 auto_create
        obstacles.initInstance(instance);
        // 交互点注册
        interacts.initInstance(instance);
        // 机关解谜状态初始化
        if (puzzleManager != null) puzzleManager.initInstance(instance);
        // 定时任务
        instanceTasks.initInstance(instance);
        // NPC
        npcs.initInstance(instance);
        // start 脚本
        runLifecycleScript(instance, "start", null);
        // 副本 HUD + 引导目标补推
        if (uiFacade instanceof AbyssUiService ui) {
            for (Player p : instance.getPlayers()) {
                try { ui.openHud(p, instance); } catch (Throwable ignored) { }
            }
        }
        if (guideService != null) {
            for (Player p : instance.getPlayers()) {
                try { guideService.syncPlayer(p, instance); } catch (Throwable ignored) { }
            }
        }
        broadcast(instance, msg("dungeon.started", "dungeon", t.name()));
        publishEvent("axs.abyss.start", null, Map.of(
            "dungeon", t.id(), "instance", instance.getInstanceId().toString(),
            "players", instance.getPlayerUUIDs().size()));
    }

    // ═══ 结束流程 ═══

    /** 通关：状态→COMPLETED，跑 complete 脚本，记成绩，延时撤出+清理。 */
    public void completeDungeon(AbyssInstance instance) {
        if (!instance.transitionTo(AbyssStatus.ACTIVE, AbyssStatus.COMPLETED)
            && !instance.transitionTo(AbyssStatus.PREPARING, AbyssStatus.COMPLETED)) {
            return;
        }
        AbyssTemplate t = instance.getTemplate();
        runLifecycleScript(instance, "complete", null);
        broadcast(instance, msg("dungeon.completed",
            "dungeon", t.name(), "time", formatTime(instance.getRunningTime())));
        // 成绩/排行
        long time = instance.getRunningTime();
        for (UUID u : instance.getPlayerUUIDs()) {
            playerData.saveRecord(u, t.id(), time);
            leaderboard.recordCompletion(u, t.id(), time);
        }
        publishEvent("axs.abyss.complete", null, Map.of(
            "dungeon", t.id(), "instance", instance.getInstanceId().toString(),
            "time_seconds", time));
        // 玩家级通关事件（每名队员一条，供战令/任务/统计订阅）
        for (Player p : instance.getPlayers()) {
            publishEvent("axs.abyss.player_complete", p, Map.of(
                "dungeon", t.id(), "instance", instance.getInstanceId().toString(),
                "time_seconds", time,
                "party-size", instance.getPlayerUUIDs().size()));
        }
        // 声明式联动（links 节）：通关播报 / QQ 群通知
        if (links != null && t.links() != null) {
            AbyssTemplate.DungeonLinks dl = t.links();
            if (dl.announceClear() != null) {
                String text = dl.announceClear()
                    .replace("{dungeon}", t.name())
                    .replace("{time}", formatTime(time))
                    .replace("{party}", partyName(instance));
                links.announce(text, dl.announceForward());
            }
            if (dl.qqNotifyClear()) {
                links.qqNotify(dl.qqGroup(),
                    "[副本] " + t.name() + " 被通关，用时 " + formatTime(time));
            }
        }
        // 统计/宝箱 UI（auto_show）
        if (stats.autoShow() && uiFacade != null) {
            for (Player p : instance.getPlayers()) {
                uiFacade.openStats(p, instance);
            }
        }
        // 10 秒后统一撤出+清理（给结算/开箱时间）
        scheduleOnce(instance, () -> finishAndCleanup(instance), 200L);
    }

    /** 失败：状态→FAILED，跑 fail 脚本，撤出+清理。 */
    public void failDungeon(AbyssInstance instance, FailReason reason) {
        AbyssStatus cur = instance.getStatus();
        if (cur != AbyssStatus.ACTIVE && cur != AbyssStatus.PREPARING) return;
        if (!instance.transitionTo(cur, AbyssStatus.FAILED)) return;
        AbyssTemplate t = instance.getTemplate();
        runLifecycleScript(instance, "fail", null);
        broadcast(instance, msg("dungeon.failed", "reason", failReasonText(reason)));
        publishEvent("axs.abyss.fail", null, Map.of(
            "dungeon", t.id(), "instance", instance.getInstanceId().toString(),
            "reason", reason.name()));
        for (Player p : instance.getPlayers()) {
            publishEvent("axs.abyss.player_fail", p, Map.of(
                "dungeon", t.id(), "instance", instance.getInstanceId().toString(),
                "reason", reason.name()));
        }
        scheduleOnce(instance, () -> finishAndCleanup(instance), 100L);
    }

    /** 取消（管理/无人/卸载）：不跑脚本。 */
    public void cancelDungeon(AbyssInstance instance) {
        AbyssStatus cur = instance.getStatus();
        if (instance.isFinished()) return;
        instance.transitionTo(cur, AbyssStatus.CANCELLED);
        broadcast(instance, msg("dungeon.cancelled"));
        finishAndCleanup(instance);
    }

    /** 结束统一处理：玩家撤出 → 世界释放 → 实例销毁。 */
    private void finishAndCleanup(AbyssInstance instance) {
        // 撤出全部在线玩家
        for (Player p : instance.getPlayers()) {
            playerExit(instance, p, LeaveReason.END);
        }
        destroyInstance(instance, false);
    }

    /** 玩家退出副本（主动/结束/管理）：传送回原位置 + 恢复模式。 */
    public void playerExit(AbyssInstance instance, Player player, LeaveReason reason) {
        if (!instance.hasPlayer(player.getUniqueId())) return;
        // 先取快照再移除：removePlayer 会清掉 originalLocations/savedGameModes
        Location back = instance.getOriginalLocation(player.getUniqueId());
        GameMode saved = instance.getSavedGameMode(player.getUniqueId());
        instance.removePlayer(player.getUniqueId());
        instances.playerLeaveInstance(player);
        // 副本内属性修饰出本即清（apply_attribute 动作施加）
        if (links != null) links.removeAttribute(player, "abyss");

        // 先传送后恢复模式：旁观者若已坠入虚空，先回安全点再切回生存，
        // 避免恢复 SURVIVAL 的瞬间在虚空世界吃到真实致死伤害。
        if (back != null && player.isOnline()) {
            try { player.teleport(back); } catch (Throwable ignored) { }
        }
        if (saved != null && player.getGameMode() != saved) {
            player.setGameMode(saved);
        } else {
            instance.restorePlayerGameMode(player);
        }
        clearBossBars(player);
        if (guideService != null) {
            try { guideService.clearPlayer(player, instance); } catch (Throwable ignored) { }
        }
        // 设计会话清理：收杖 + 关会话
        if (designManager != null && designManager.session(player) != null) {
            designManager.close(player);
            if (designListener != null) designListener.removeWand(player);
        }
        if (uiFacade instanceof AbyssUiService ui) {
            ui.closeHud(player);
            ui.flushPendingChest(player);
        }
        if (reason != LeaveReason.END && reason != LeaveReason.DISCONNECT) {
            player.sendMessage(msg("dungeon.exited"));
        }
        publishEvent("axs.abyss.leave", player, Map.of(
            "dungeon", instance.getTemplate().id(),
            "instance", instance.getInstanceId().toString(),
            "reason", reason.name()));
        // 退出后实例无人 → 倒计时清理（宽限等人重连）
        if (instance.getOnlinePlayerCount() == 0 && !instance.isFinished()) {
            long delay = config.abyss().emptyCleanupDelaySeconds() * 20L;
            scheduleOnce(instance, () -> {
                if (instance.getOnlinePlayerCount() == 0) {
                    cancelDungeon(instance);
                }
            }, delay);
        }
    }

    /** 玩家断线（监听器调用）：存待恢复位置+从实例移除。 */
    public void handleQuit(Player player) {
        // 无论是否在副本内，先清理 UI 会话态（token/HUD 标记）
        if (uiFacade instanceof AbyssUiService ui0) {
            ui0.closeHud(player);
            ui0.onPlayerGone(player);
        }
        if (guideService != null) guideService.handleQuit(player);
        if (designManager != null) designManager.close(player);
        AbyssInstance instance = instances.getInstance(player);
        if (instance == null) return;
        // 非正常退出标记（下次进服执行恢复动作）
        if (recoveryManager != null) {
            recoveryManager.mark(player.getUniqueId(), instance.getTemplate().id());
        }
        Location back = instance.getOriginalLocation(player.getUniqueId());
        if (back != null) {
            playerData.savePendingLocation(player.getUniqueId(), back);
        }
        instance.removePlayer(player.getUniqueId());
        if (links != null) links.removeAttribute(player, "abyss");
        // 不触发退出消息；无人清理由 watchdog/playerExit 路径处理
        if (instance.getOnlinePlayerCount() == 0 && !instance.isFinished()) {
            long delay = config.abyss().emptyCleanupDelaySeconds() * 20L;
            scheduleOnce(instance, () -> {
                if (instance.getOnlinePlayerCount() == 0) cancelDungeon(instance);
            }, delay);
        }
        partyManager.onQuit(player);
    }

    /** 玩家进服（监听器调用）：恢复位置 + 预载数据 + 异常退出恢复动作。 */
    public void handleJoin(Player player) {
        playerData.preloadCooldowns(player.getUniqueId());
        ScheduledTask[] holder = new ScheduledTask[1];
        holder[0] = scheduler.scheduleEverySeconds(plugin, () -> {
            if (holder[0] != null) {
                try { holder[0].cancel(); } catch (Throwable ignored) { }
            }
            Location loc = playerData.takePendingLocation(player.getUniqueId());
            if (loc != null && player.isOnline()) {
                runOnMain(() -> player.teleport(loc));
            }
        }, 0L, Long.MAX_VALUE / 20);
        // 异常退出恢复（在传送恢复后一拍执行）
        if (recoveryManager != null) {
            ScheduledTask[] rh = new ScheduledTask[1];
            rh[0] = scheduler.scheduleEverySeconds(plugin, () -> {
                if (rh[0] != null) {
                    try { rh[0].cancel(); } catch (Throwable ignored) { }
                }
                if (player.isOnline()) {
                    runOnMain(() -> recoveryManager.runIfMarked(player));
                }
            }, 1L, Long.MAX_VALUE / 20);
        }
        // 地图入口标记（延迟 2s 等客户端地图就绪）
        if (entranceMarkers != null) {
            ScheduledTask[] eh = new ScheduledTask[1];
            eh[0] = scheduler.scheduleEverySeconds(plugin, () -> {
                if (eh[0] != null) {
                    try { eh[0].cancel(); } catch (Throwable ignored) { }
                }
                if (player.isOnline()) {
                    runOnMain(() -> entranceMarkers.push(player));
                }
            }, 2L, Long.MAX_VALUE / 20);
        }
    }

    // ═══ 死亡处理 ═══

    /**
     * 致命伤害拦截（监听器 HIGHEST 调用）。
     * @return true = 已接管（取消伤害事件）
     */
    public boolean handleFatalDamage(AbyssInstance instance, Player player,
                                     org.bukkit.event.entity.EntityDamageEvent event) {
        if (!config.death().fatalDamageCheck()) return false;
        if (instance.getStatus() != AbyssStatus.ACTIVE) return false;
        if (instance.isPlayerCurrentlyDead(player)) return false;
        double finalDamage = event.getFinalDamage();
        double health = player.getHealth() + player.getAbsorptionAmount();
        if (finalDamage < health) return false;

        // 图腾救活判定
        if (config.death().allowTotem() && hasTotem(player)) {
            return false; // 放行：原版图腾复活
        }

        int deaths = instance.recordDeath(player);
        player.setGameMode(GameMode.SPECTATOR);
        player.sendMessage(msg("dungeon.death", "deaths", String.valueOf(deaths)));
        runLifecycleScript(instance, "player_death", player);
        publishEvent("axs.abyss.player_death", player, Map.of(
            "dungeon", instance.getTemplate().id(),
            "instance", instance.getInstanceId().toString(),
            "deaths", deaths));
        instanceTasks.fireEvent(instance, "player_death",
            Map.of("player", player.getName(), "deaths", String.valueOf(deaths)));

        // 全队覆灭判定
        if (instance.getAlivePlayerCount() == 0 && instance.markTeamWipeHandled()) {
            if (config.death().teamWipeMode() == AbyssModuleConfiguration.TeamWipeMode.STRICT) {
                runLifecycleScript(instance, "all_death", player);
                failDungeon(instance, FailReason.TEAM_WIPE);
            } else {
                runLifecycleScript(instance, "all_death", player);
                scheduleOnce(instance, () -> {
                    if (instance.getAlivePlayerCount() == 0 && !instance.isFinished()) {
                        failDungeon(instance, FailReason.TEAM_WIPE);
                    }
                }, config.death().teamWipeDelayTicks());
            }
        }
        return true;
    }

    private boolean hasTotem(Player p) {
        ItemStack main = p.getInventory().getItemInMainHand();
        ItemStack off = p.getInventory().getItemInOffHand();
        return (main != null && main.getType() == Material.TOTEM_OF_UNDYING)
            || (off != null && off.getType() == Material.TOTEM_OF_UNDYING);
    }

    /** 复活玩家（revive_player 动作）：回血+回出生点+恢复模式。 */
    public void revivePlayer(AbyssInstance instance, Player player) {
        if (!instance.hasPlayer(player.getUniqueId())) return;
        instance.revive(player);
        instance.restorePlayerGameMode(player);
        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SURVIVAL);
        }
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        Location spawn = spawnLocation(instance);
        if (spawn != null) player.teleport(spawn);
        player.sendMessage(msg("dungeon.revived"));
    }

    // ═══ 脚本 ═══

    /** 执行动作行列表（统一入口）。 */
    public xuanmo.arcartxsuite.abyss.script.ScriptSession runScript(
            AbyssInstance instance, List<String> lines, ScriptContext context) {
        if (lines == null || lines.isEmpty() || instance.isFinished()) {
            return null;
        }
        return scriptRunner.run("script", lines, context, instance);
    }

    /** 生命周期脚本（world_init/init/start/complete/fail/player_death/all_death/exit）。 */
    public void runLifecycleScript(AbyssInstance instance, String kind, @Nullable Player trigger) {
        var scripts = instance.getTemplate().scripts();
        List<String> lines = switch (kind) {
            case "world_init" -> scripts.worldInit();
            case "init" -> scripts.init();
            case "start" -> scripts.start();
            case "complete" -> scripts.complete();
            case "fail" -> scripts.fail();
            case "player_death" -> scripts.playerDeath();
            case "all_death" -> scripts.allDeath();
            case "exit" -> scripts.exit();
            default -> List.of();
        };
        if (!lines.isEmpty()) {
            runScript(instance, lines, ScriptContext.of(instance, trigger));
        }
    }

    /** 自定义脚本段（run_script 动作）。 */
    public void runCustomScript(AbyssInstance instance, String name, @Nullable Player trigger) {
        List<String> lines = instance.getTemplate().scripts().customs().get(name.toLowerCase());
        if (lines != null && !lines.isEmpty()) {
            runScript(instance, lines, ScriptContext.of(instance, trigger));
        }
    }

    // ═══ 动作支持接口（BuiltinActions 调用） ═══

    /** 出生点（floors 模式优先当前房 spawn；否则模板 spawn 解析）。 */
    public @Nullable Location spawnLocation(AbyssInstance instance) {
        if (instance.getTemplate().mode() == AbyssTemplate.Mode.FLOORS
            && roomManager != null) {
            Location cur = roomManager.currentSpawn(instance);
            if (cur != null) return cur;
        }
        return parseLocation(instance, instance.getTemplate().spawn());
    }

    /** "x,y,z[,yaw,pitch]" → 实例世界 Location。 */
    public @Nullable Location parseLocation(AbyssInstance instance, String locStr) {
        if (locStr == null || locStr.isBlank()) return null;
        String[] v = locStr.split(",");
        if (v.length < 3 || instance.getWorld() == null) return null;
        try {
            double x = Double.parseDouble(v[0].trim());
            double y = Double.parseDouble(v[1].trim());
            double z = Double.parseDouble(v[2].trim());
            float yaw = v.length > 3 ? Float.parseFloat(v[3].trim()) : 0f;
            float pitch = v.length > 4 ? Float.parseFloat(v[4].trim()) : 0f;
            return new Location(instance.getWorld(), x, y, z, yaw, pitch);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private java.util.function.Function<String, Location> locParser(AbyssInstance instance) {
        return s -> parseLocation(instance, s);
    }

    public void giveDefinedItem(Player player, String def, AbyssInstance instance) {
        ItemStack item = rewards.buildItem(def, player);
        if (item == null) {
            logger.warning("[abyss] give_item 构建失败: " + def);
            return;
        }
        itemRewardDispatcher.dispatchItem(player, item,
            ItemRewardDispatcher.DispatchOptions.builder("abyss")
                .sourceDetail("give_item:" + def).build());
    }

    public void takeDefinedItem(Player player, String def) {
        rewards.takeItem(player, def);
    }

    public void giveMoney(Player player, double amount) {
        rewards.giveMoney(player, amount, "money");
    }

    public void castMythicSkill(Player player, String skill, double power) {
        if (mobProvider != null && !mobProvider.castSkill(player, skill, power)) {
            logger.fine("[abyss] cast_skill 失败/不可用: " + skill);
        }
    }

    /** 玩家 → 本模块创建的 BossBar（Spigot 无 getBossBars，自行跟踪）。 */
    private final Map<java.util.UUID, java.util.Set<BossBar>> playerBossBars = new ConcurrentHashMap<>();

    /** BossBar（Bukkit API，跨版本通用）。 */
    public void bossBar(Player player, String durationStr, String title, String colorStyle) {
        if ("clear".equalsIgnoreCase(durationStr)) {
            clearBossBars(player);
            return;
        }
        double seconds;
        try { seconds = Double.parseDouble(durationStr); } catch (NumberFormatException e) { seconds = 10; }
        BarColor color = BarColor.WHITE;
        BarStyle style = BarStyle.SOLID;
        if (colorStyle != null) {
            String[] cs = colorStyle.trim().split("\\s+");
            if (cs.length > 0) {
                try { color = BarColor.valueOf(cs[0].toUpperCase()); } catch (Exception ignored) { }
            }
            if (cs.length > 1) {
                try { style = BarStyle.valueOf(cs[1].toUpperCase()); } catch (Exception ignored) { }
            }
        }
        BossBar bar = Bukkit.createBossBar(title, color, style);
        bar.addPlayer(player);
        bar.setVisible(true);
        playerBossBars.computeIfAbsent(player.getUniqueId(),
            k -> java.util.Collections.newSetFromMap(new ConcurrentHashMap<>())).add(bar);
        if (seconds > 0) {
            final BossBar fb = bar;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try { fb.removeAll(); } catch (Throwable ignored) { }
                var set = playerBossBars.get(player.getUniqueId());
                if (set != null) set.remove(fb);
            }, (long) (seconds * 20));
        }
    }

    /** 清除某玩家全部 abyss BossBar（退出副本/清屏用）。 */
    public void clearBossBars(Player player) {
        var set = playerBossBars.remove(player.getUniqueId());
        if (set != null) {
            for (BossBar bar : set) {
                try { bar.removeAll(); } catch (Throwable ignored) { }
            }
        }
    }

    // ═══ 宝箱 ═══

    public interface ChestUiHandler {
        void open(Player player, xuanmo.arcartxsuite.abyss.template.RewardConfig.ChestReward chest,
                  AbyssInstance instance);
    }

    /** 宝箱 UI 门面（AbyssUiService 实现的全部 UI 入口）。 */
    public interface UiServiceFacade {
        void openBrowser(Player player);
        void openEntryConfirm(Player player, AbyssTemplate template);
        void openParty(Player player);
        /** 组队大厅（无队伍时 /party 的入口）。 */
        void openLobby(Player player);
        /** 机关输入面板（puzzles.yml input 型；UI 缺席时静默）。 */
        void openPuzzleInput(Player player, AbyssInstance instance,
                             xuanmo.arcartxsuite.abyss.template.PuzzleConfig.Puzzle puzzle,
                             int attemptsLeft);
        /** 增益选择面板（buffs.yml panel 池）。 */
        void openBuffOffer(Player player, AbyssInstance instance,
                           xuanmo.arcartxsuite.abyss.template.BuffConfig.Pool pool,
                           java.util.List<xuanmo.arcartxsuite.abyss.template.BuffConfig.Option> candidates,
                           int timeoutSeconds);
        /** 队伍数据变更后向全队推送房间 UI 更新（成员/消息流/副本/设置）。 */
        void pushPartyUpdate(Party party);
        void openStats(Player player, AbyssInstance instance);
        void openLeaderboard(Player player, String dungeonId);
        /** UI 包处理；返回 true 表示已消费。 */
        boolean handlePacket(Player player, String packetId, List<String> data);
    }

    public void openChest(Player player, String chestName, AbyssInstance instance) {
        var chest = instance.getTemplate().rewards().chestRewards().get(chestName);
        if (chest == null) {
            logger.warning("[abyss] 宝箱不存在: " + chestName);
            return;
        }
        chestUiHandler.open(player, chest, instance);
    }

    /** 统一物品发放（宝箱 UI/脚本/奖励链共用入口）。 */
    public void dispatchItems(Player player, List<ItemStack> items, String sourceDetail) {
        if (items == null || items.isEmpty()) return;
        itemRewardDispatcher.dispatchItems(player, items,
            ItemRewardDispatcher.DispatchOptions.builder("abyss")
                .sourceDetail(sourceDetail).build());
    }

    /** 默认宝箱：直接 roll 发放（UI 未接入时兜底）。 */
    private void defaultChestHandler(Player player,
                                     xuanmo.arcartxsuite.abyss.template.RewardConfig.ChestReward chest,
                                     AbyssInstance instance) {
        List<ItemStack> items = rewards.rollChestOptions(chest, Math.min(9, chest.rows() * 9 / 2));
        if (!items.isEmpty()) {
            itemRewardDispatcher.dispatchItems(player, items,
                ItemRewardDispatcher.DispatchOptions.builder("abyss")
                    .sourceDetail("chest:" + chest.name()).build());
        }
    }

    // ═══ 工具 ═══

    /** 按世界找实例（怪物死亡事件等反向定位）。 */
    public @Nullable AbyssInstance findInstanceByWorld(World world) {
        UUID id = worldIndex.get(world.getName());
        return id != null ? instances.getInstance(id) : null;
    }

    /** 销毁实例：清理全部子系统 + 释放世界 + 从注册表移除。 */
    public void destroyInstance(AbyssInstance instance, boolean releasePlayers) {
        if (releasePlayers) {
            for (Player p : instance.getPlayers()) {
                try { playerExit(instance, p, LeaveReason.END); } catch (Throwable ignored) { }
            }
        }
        // 取消实例任务+脚本会话
        for (ScheduledTask t : instance.getTasks()) {
            try { t.cancel(); } catch (Throwable ignored) { }
        }
        for (var s : instance.getScriptSessions()) {
            try { s.cancel(); } catch (Throwable ignored) { }
        }
        mobGroups.cleanup(instance);
        if (roomManager != null) roomManager.cleanup(instance);
        obstacles.cleanup(instance);
        interacts.cleanup(instance);
        if (puzzleManager != null) puzzleManager.cleanup(instance);
        if (guideService != null) guideService.cleanup(instance);
        if (buffManager != null) buffManager.cleanup(instance);
        if (designManager != null) designManager.closeInstance(instance);
        instanceTasks.cleanup(instance);
        npcs.cleanup(instance);
        worldIndex.remove(instance.getWorld() != null ? instance.getWorld().getName() : "");
        worldPool.releaseWorld(instance.getInstanceId());
        instance.clearRuntime();
        instances.destroyInstance(instance);
    }

    private boolean teleportWithRetry(Player p, Location loc, int attempts) {
        for (int i = 0; i < attempts; i++) {
            try {
                if (p.teleport(loc)) return true;
            } catch (Throwable ignored) { }
        }
        return false;
    }

    /** 实例级一次性延迟任务（随实例清理）。 */
    public void scheduleOnce(AbyssInstance instance, Runnable r, long delayTicks) {
        ScheduledTask[] holder = new ScheduledTask[1];
        holder[0] = scheduler.scheduleAtFixedRate(plugin, () -> {
            if (holder[0] != null) {
                try { holder[0].cancel(); } catch (Throwable ignored) { }
            }
            r.run();
        }, delayTicks, Long.MAX_VALUE);
        instance.registerTask(holder[0]);
    }

    /** 主线程执行。 */
    public void runOnMain(Runnable r) {
        if (Bukkit.isPrimaryThread()) {
            r.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, r);
        }
    }

    private void broadcast(AbyssInstance instance, String message) {
        for (Player p : instance.getPlayers()) {
            p.sendMessage(message);
        }
    }

    /** 消息格式化（messages.yml 键 + {key} 占位）。 */
    public String msg(String key, String... kv) {
        String s = messages != null ? messages.get(key) : key;
        if (messages != null) {
            s = s.replace("{prefix}", messages.get("prefix"));
        }
        for (int i = 0; i + 1 < kv.length; i += 2) {
            s = s.replace("{" + kv[i] + "}", kv[i + 1]);
        }
        return s;
    }

    /** 失败原因本地化文案（对应 messages.yml 的 fail-reason.* 键）。 */
    public String failReasonText(FailReason reason) {
        String k = "fail-reason." + reason.name().toLowerCase(Locale.ROOT);
        return messages != null && messages.has(k) ? messages.get(k) : reason.name();
    }

    /** afk-kick-minutes 配置（<=0 关）。 */
    private int afkKickMinutes() {
        return config.abyss() != null ? Math.max(0, config.abyss().afkKickMinutes()) : 0;
    }

    /** 队伍显示名（无队伍返回空串）。 */
    private String partyName(AbyssInstance instance) {
        var p = instance.getParty();
        return p != null ? p.name() : "";
    }

    public static String formatTime(long seconds) {
        long m = seconds / 60, s = seconds % 60;
        return m > 0 ? m + "分" + s + "秒" : s + "秒";
    }

    /** EventBus 发布（无总线静默）。 */
    public void publishEvent(String topic, @Nullable Player player, Map<String, Object> data) {
        try {
            EventBusCapability bus = eventBusProvider.get();
            if (bus != null) {
                bus.publish(topic, player, data);
            }
        } catch (Throwable t) {
            logger.fine("[abyss] EventBus 发布失败 " + topic + ": " + t.getMessage());
        }
    }

    // ═══ 周期看守 ═══

    private void watchdog() {
        if (!started) return;
        for (AbyssInstance i : new ArrayList<>(instances.getAllInstances())) {
            // 设计实例冻结：不做消失扫描/超时/兜底回收/AFK 踢
            if (isDesignInstance(i)) continue;
            try {
                // 消失扫描（怪被刷掉兜底）
                mobGroups.sweepVanished(i);
                // 超时
                if (i.isTimeout()) {
                    if (i.getTemplate().timeoutAction() == AbyssTemplate.TimeoutAction.FAIL) {
                        failDungeon(i, FailReason.TIMEOUT);
                    } else {
                        for (Player p : i.getPlayers()) {
                            playerExit(i, p, LeaveReason.TIMEOUT_KICK);
                        }
                        destroyInstance(i, false);
                    }
                    continue;
                }
                // 实例存活超时（兜底清理）
                long age = (System.currentTimeMillis() - i.getCreateTime()) / 1000;
                if (age > config.abyss().instanceCleanupTimeout()) {
                    logger.warning("[abyss] 实例超时强制回收: " + i.getInstanceId());
                    destroyInstance(i, true);
                    continue;
                }
                // 副本内 AFK 踢出（Essentials AFK 状态持续超限）
                if (afkKickMinutes() > 0 && links != null
                    && i.getStatus() == AbyssStatus.ACTIVE) {
                    long now = System.currentTimeMillis();
                    long limit = afkKickMinutes() * 60_000L;
                    for (Player p : i.getPlayers()) {
                        if (links.isAfk(p)) {
                            long since = afkSince.merge(p.getUniqueId(),
                                now, Math::min);
                            if (now - since >= limit) {
                                afkSince.remove(p.getUniqueId());
                                playerExit(i, p, LeaveReason.AFK_KICK);
                                p.sendMessage(msg("dungeon.afk-kick"));
                            }
                        } else {
                            afkSince.remove(p.getUniqueId());
                        }
                    }
                }
            } catch (Throwable t) {
                logger.warning("[abyss] watchdog 异常 " + i.getInstanceId() + ": " + t.getMessage());
            }
        }
    }

    /** 1s 轮询：障碍物 proximity + 区域交互点触发。 */
    private void proximityTick() {
        if (!started) return;
        for (AbyssInstance i : new ArrayList<>(instances.getAllInstances())) {
            if (i.getStatus() != AbyssStatus.ACTIVE) continue;
            if (isDesignInstance(i)) continue; // 设计实例无障碍/房间/interact 初始化
            try {
                obstacles.tickProximity(i);
                if (roomManager != null) roomManager.tick(i);
                for (Player p : i.getPlayers()) {
                    interacts.onRegionTrigger(i, p,
                        xuanmo.arcartxsuite.abyss.template.InteractConfig.InteractType.STEP_ON);
                    interacts.onRegionTrigger(i, p,
                        xuanmo.arcartxsuite.abyss.template.InteractConfig.InteractType.ENTER_REGION);
                    // 副本 HUD 周期刷新（1s）
                    if (uiFacade instanceof AbyssUiService ui) {
                        ui.pushHudUpdate(p, i);
                    }
                }
            } catch (Throwable ignored) { }
        }
    }

    // ═══ 客户端包 ═══

    /** UI 包路由（AbyssUiService 实现后接管）。 */
    public boolean handleClientPacket(Player player, String packetId, List<String> data) {
        return uiFacade != null && uiFacade.handlePacket(player, packetId, data);
    }

    /** 怪物组回调实现（MonsterGroupManager.GroupCallback）。 */
    private final class GroupCallbackImpl implements MonsterGroupManager.GroupCallback {
        @Override
        public void onGroupStart(AbyssInstance instance, String groupName) {
            instanceTasks.fireEvent(instance, "wave_start",
                Map.of("group", groupName));
        }

        @Override
        public void onGroupComplete(AbyssInstance instance, String groupName) {
            instanceTasks.fireEvent(instance, "wave_complete",
                Map.of("group", groupName));
            broadcast(instance, msg("dungeon.mob-group-next", "group", groupName));
            // floors：房间组全清 → 解锁判定
            if (roomManager != null) roomManager.onGroupComplete(instance, groupName);
        }

        @Override
        public void onMobDeath(AbyssInstance instance, UUID entityUuid, @Nullable Player killer) {
            if (killer != null) {
                instance.recordKill(killer.getUniqueId());
                AbyssService.this.publishEvent("axs.abyss.mob_kill", killer, Map.of(
                    "dungeon", instance.getTemplate().id(),
                    "instance", instance.getInstanceId().toString(),
                    "mob", entityUuid.toString()));
            }
            instanceTasks.fireEvent(instance, "monster_kill", Map.of(
                "mob", entityUuid.toString(),
                "killer", killer != null ? killer.getName() : ""));
        }

        @Override
        public void runGroupScript(AbyssInstance instance, List<String> lines,
                                   String groupName, @Nullable Player trigger) {
            if (!lines.isEmpty()) {
                runScript(instance, lines, ScriptContext.group(instance, groupName, trigger));
            }
        }

        @Override
        public @Nullable Location parseLocation(AbyssInstance instance, String locStr) {
            return AbyssService.this.parseLocation(instance, locStr);
        }

        @Override
        public @Nullable MobProvider provider() {
            return mobProvider;
        }

        @Override
        public void publishEvent(String topic, Map<String, Object> data) {
            AbyssService.this.publishEvent(topic, null, data);
        }
    }

    /** RoomManager 回调实现（脚本/事件/广播/通关桥接）。 */
    private final class RoomCallbackImpl
        implements xuanmo.arcartxsuite.abyss.room.RoomManager.Callback {
        @Override
        public void runScript(AbyssInstance instance, List<String> lines,
                              String contextName, @Nullable Player trigger) {
            if (!lines.isEmpty()) {
                AbyssService.this.runScript(instance, lines,
                    ScriptContext.withVars(instance, trigger,
                        Map.of("room", contextName)));
            }
        }

        @Override
        public void publishEvent(String topic, @Nullable Player player,
                                 Map<String, Object> data) {
            AbyssService.this.publishEvent(topic, player, data);
        }

        @Override
        public void broadcast(AbyssInstance instance, String message) {
            AbyssService.this.broadcast(instance, message);
        }

        @Override
        public String msg(String key, String... kv) {
            return AbyssService.this.msg(key, kv);
        }

        @Override
        public void onDungeonComplete(AbyssInstance instance) {
            completeDungeon(instance);
        }

        @Override
        public void setVar(AbyssInstance instance, String key, Object value) {
            instance.getVariables().put(key, value);
        }
    }

    /** PuzzleManager 回调实现：脚本/事件/输入面板桥接。 */
    private final class PuzzleCallbackImpl
        implements xuanmo.arcartxsuite.abyss.puzzle.PuzzleManager.Callback {

        @Override
        public void runScript(AbyssInstance instance, List<String> lines,
                              String puzzleId, @Nullable Player trigger) {
            if (!lines.isEmpty()) {
                AbyssService.this.runScript(instance, lines,
                    ScriptContext.withVars(instance, trigger,
                        Map.of("puzzle", puzzleId)));
            }
        }

        @Override
        public void openInputUi(Player player, AbyssInstance instance,
                                xuanmo.arcartxsuite.abyss.template.PuzzleConfig.Puzzle puzzle,
                                int attemptsLeft) {
            if (uiFacade != null) {
                uiFacade.openPuzzleInput(player, instance, puzzle, attemptsLeft);
            } else {
                player.sendMessage(AbyssService.this.msg("puzzle.ui-unavailable"));
            }
        }

        @Override
        public void publishEvent(String topic, @Nullable Player player,
                                 Map<String, Object> data) {
            AbyssService.this.publishEvent(topic, player, data);
        }

        @Override
        public void broadcast(AbyssInstance instance, String message) {
            AbyssService.this.broadcast(instance, message);
        }

        @Override
        public void msg(Player player, String message) {
            player.sendMessage(message);
        }
    }

    private final class BuffCallbackImpl
        implements xuanmo.arcartxsuite.abyss.buff.BuffManager.Callback {

        @Override
        public void runScript(AbyssInstance instance, List<String> lines,
                              @Nullable Player trigger) {
            if (!lines.isEmpty()) {
                AbyssService.this.runScript(instance, lines,
                    ScriptContext.withVars(instance, trigger, Map.of()));
            }
        }

        @Override
        public void openBuffUi(Player player, AbyssInstance instance,
                               xuanmo.arcartxsuite.abyss.template.BuffConfig.Pool pool,
                               List<xuanmo.arcartxsuite.abyss.template.BuffConfig.Option> candidates,
                               int timeoutSeconds) {
            if (uiFacade != null) {
                uiFacade.openBuffOffer(player, instance, pool, candidates, timeoutSeconds);
            }
        }

        @Override
        public void publishEvent(String topic, @Nullable Player player,
                                 Map<String, Object> data) {
            AbyssService.this.publishEvent(topic, player, data);
        }

        @Override
        public void broadcast(AbyssInstance instance, String message) {
            AbyssService.this.broadcast(instance, message);
        }

        @Override
        public void msg(Player player, String message) {
            player.sendMessage(message);
        }
    }
}
