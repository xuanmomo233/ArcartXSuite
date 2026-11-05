package xuanmo.arcartxsuite.abyss.service;

import xuanmo.arcartxsuite.api.capability.AbyssAdmissionAccess;
import xuanmo.arcartxsuite.api.capability.AbyssRunResultAccess;
import xuanmo.arcartxsuite.api.capability.AbyssAdmissionAccess.Denial;
import xuanmo.arcartxsuite.api.capability.AbyssAdmissionAccess.Intent;
import xuanmo.arcartxsuite.api.capability.AbyssAdmissionAccess.Phase;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;
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
import xuanmo.arcartxsuite.abyss.template.SettlementConfig;
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
    private final AdmissionAccess admission = new AdmissionAccess(this);
    private final EntryCosts entryCosts = new EntryCosts(this);
    private final RunResults runResults = new RunResults(this);
    private final RunIntegration runIntegration = new RunIntegration(this);
    private final ExternalParties externalParties = new ExternalParties(this);
    private final RunReconnects reconnects = new RunReconnects(this);
    public AbyssAdmissionAccess admissionAccess() { return admission; }
    public AbyssRunResultAccess runResultAccess() { return runResults; }
    public xuanmo.arcartxsuite.api.capability.AbyssRunAccess runAccess() { return runIntegration; }
    public xuanmo.arcartxsuite.api.capability.AbyssExternalPartyAccess externalPartyAccess() { return externalParties; }
    public xuanmo.arcartxsuite.api.capability.AbyssReconnectAccess reconnectAccess() { return reconnects; }
    AdmissionAccess.Operation admissionOperation(AbyssInstance instance) { return admission.operation(instance); }
    public xuanmo.arcartxsuite.api.capability.AbyssEntryCostAccess entryCostAccess() { return entryCosts; }
    EntryCosts entryCosts() { return entryCosts; }

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
    private ChunkPreloader chunkPreloader;
    private InstanceTicker instanceTicker;
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
    private xuanmo.arcartxsuite.abyss.stage.StageManager stageManager;
    private xuanmo.arcartxsuite.abyss.procgen.RoomSetLibrary roomSetLibrary;
    private xuanmo.arcartxsuite.abyss.procgen.ProcGenManager procgenManager;
    private xuanmo.arcartxsuite.abyss.cross.CrossDungeonManager crossDungeon;
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
    private xuanmo.arcartxsuite.abyss.region.RegionLibrary regionLibrary;
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
    private final Map<UUID,Player> recoveringPlayers=new ConcurrentHashMap<>();
    private AbyssAdminCommand adminCommand;

    /** worldName → instanceId（事件反向定位实例）。 */
    private final Map<String, UUID> worldIndex = new ConcurrentHashMap<>();
    private final Map<String, UUID> designOwners = new ConcurrentHashMap<>();
    /** 玩家 → 进入 Essentials AFK 状态的起始时间戳（afk-kick 判定）。 */
    private final Map<UUID, Long> afkSince = new ConcurrentHashMap<>();
    /** EventBus 提供者（模块注入 capability）。 */
    private Supplier<EventBusCapability> eventBusProvider = () -> null;
    /** 宝箱 UI 处理器（AbyssUiService 实现后注入；默认纯发放实现）。 */
    private volatile ChestUiHandler chestUiHandler = this::defaultChestHandler;
    /** 玩家命令/UI 桥（AbyssUiService 注入）。 */
    private volatile UiServiceFacade uiFacade;

    private ScheduledTask watchdogTask;
    private final java.util.Set<ScheduledTask> serviceTasks=ConcurrentHashMap.newKeySet();
    private final java.util.Set<UUID> partyRefreshQueued=ConcurrentHashMap.newKeySet();
    private volatile boolean stopping;
    private java.util.function.LongSupplier settlementClock=System::currentTimeMillis;
    private final Map<UUID,SettlementSession> settlements=new ConcurrentHashMap<>();
    private final Set<UUID> cleanupInProgress=ConcurrentHashMap.newKeySet();
    private final Set<UUID> exitingPlayers=ConcurrentHashMap.newKeySet();
    private final Set<UUID> transferringInstances=ConcurrentHashMap.newKeySet();
    private static final class SettlementSession {
        final SettlementConfig.Policy policy;final long deadline;final boolean immediate;
        final Map<UUID,Runnable> presentations=new LinkedHashMap<>();
        final Map<UUID,Location> safeLocations=new LinkedHashMap<>();
        ScheduledTask timer;int attempts;long nextAttempt,lastAnnounced=Long.MIN_VALUE,drainingSince=-1;
        SettlementSession(SettlementConfig.Policy policy,long now) {
            this.policy=policy;this.deadline=policy.deadline(now);
            immediate=policy.mode()==SettlementConfig.ExitMode.IMMEDIATE || policy.mode()==SettlementConfig.ExitMode.COUNTDOWN && policy.seconds()==0;
        }
    }
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
        playerData = new PlayerDataManager(repository, logger, config.stamina().max());
        runResults.configure(repository, playerData, dataFolder.toPath().resolve("run-result-outbox"));
        try {
            admission.load(repository, playerData);
        } catch (Exception error) {
            logger.log(java.util.logging.Level.WARNING, "[abyss] 入场日志不可用，暂停公开入场请求", error);
        }

        // 2) 模板加载（首次启动导出示例地牢）
        templateLoader = new AbyssTemplateLoader(dataFolder, logger);
        exportExampleDungeon(dataFolder);
        instances = new AbyssInstanceManager();
        reloadTemplates();

        // 3) 世界池 + 残留世界清理 + 预热池
        worldPool = new AbyssWorldPool(plugin, config.abyss(), logger, scheduler);
        chunkPreloader = new ChunkPreloader(scheduler, plugin, GET_CHUNK_ASYNC == null ? 2 : 4,
            GET_CHUNK_ASYNC == null ? 2 : 8);
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
        interacts.setCallbacks((inst, e) -> {
            // 交互触发是阶段推进事件源之一
            if (stageManager != null) stageManager.onEvent(inst);
        }, this::locParser);
        interacts.setScriptExecutor((inst,event)->runScript(inst,event.script(),
            ScriptContext.withVars(inst,event.trigger(),Map.of("interact",event.interactName()))),this::runOnMain,
            inst->failDungeon(inst,FailReason.SCRIPTED));
        interacts.setExternalItemMatcher((req, hand) -> rewards.matches(req, hand));
        puzzleManager = new xuanmo.arcartxsuite.abyss.puzzle.PuzzleManager(logger);
        puzzleManager.setCallback(new PuzzleCallbackImpl());
        interacts.setPuzzleCallback((inst, e) ->
            puzzleManager.onInteractFired(inst, e.interactName(), e.trigger()));
        // 阶段机：事件驱动推进（怪组清完/交互触发/complete_stage/超时 + watchdog 慢扫）
        stageManager = new xuanmo.arcartxsuite.abyss.stage.StageManager(
            this, plugin, scheduler, logger);
        stageManager.setScriptLauncher((inst, lines, trig, vars) ->
            runScript(inst, lines, ScriptContext.withVars(inst, trig, vars)));
        // procgen：房间集库 + 运行管理器
        roomSetLibrary = new xuanmo.arcartxsuite.abyss.procgen.RoomSetLibrary(
            dataFolder, logger);
        procgenManager = new xuanmo.arcartxsuite.abyss.procgen.ProcGenManager(
            this, roomSetLibrary, logger);
        // 跨服：副本 cross-server 声明后启用（宿主通道缺席时静默降级）
        crossDungeon = new xuanmo.arcartxsuite.abyss.cross.CrossDungeonManager(
            this, crossServer, plugin, logger);
        crossDungeon.start();
        instanceTasks = new InstanceTaskScheduler(plugin, scheduler, logger);
        instanceTasks.setFireCallback((inst, f) -> runScript(inst, f.script(),
            ScriptContext.withVars(inst, null, f.vars())));
        npcs = new DungeonNpcManager(plugin, scheduler, logger);
        // 桥实例由 createAdyeshachNpcBridge() 新建，available 需显式 initialize() 激活
        // （questgps 同款约定）；Adyeshach 未装载时返回 false，NPC 走实体兜底
        if (npcBridge != null && !npcBridge.initialize()) {
            logger.fine("[abyss] Adyeshach 不可用或初始化失败，副本 NPC 使用实体兜底");
        }
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
            pendingRewardService, currencyManager, logger, itemMatcher);
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
        partyManager.setChangeListener(party -> {
            if(partyRefreshQueued.add(party.id()))scheduleDelayedRaw(()->{
                partyRefreshQueued.remove(party.id());ui.refreshPartyState(party);
            },1L);
        });
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

        // 8.12) 区域模板库（服主圈选世界区域存为模板，paste_region 铺进副本世界）
        regionLibrary = new xuanmo.arcartxsuite.abyss.region.RegionLibrary(logger);

        // 8.11) 设计模式（在实例内可视化编辑地牢配置）
        designManager = new xuanmo.arcartxsuite.abyss.design.DesignManager(
            logger, (p, m) -> p.sendMessage(m));
        designManager.setMsgResolver((key, kv) -> msg(key, kv));
        designManager.setDesignAnchor(procgenManager::designAnchor);
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
        instanceTicker = new InstanceTicker(instances::getAllInstances, instances::getInstance, this::tickInstance);
        proximityTask = scheduler.scheduleAtFixedRate(plugin, this::proximityTick, 1, 1);

        // 11) 副本预热池（预热世界就绪后进入秒开）
        Map<String, AbyssTemplate> tplMap = new java.util.LinkedHashMap<>();
        for (AbyssTemplate t : instances.getAllTemplates()) {
            tplMap.put(t.id(), t);
        }
        worldPool.initPreload(tplMap, config.abyss().preload());

        subscribeQuestCompleted(); // EventBus 晚注册时补订（幂等）
        started = true;
        runResults.replay();
        logger.info("[abyss] 已启动，地牢模板: " + instances.getAllTemplates().size());
    }

    private ScheduledTask proximityTask;

    public void shutdown() {
        if (chunkPreloader != null) chunkPreloader.close();
        runIntegration.shutdown();
        admission.shutdown();
        externalParties.clear();
        entryCosts.clear();
        stopping=true;
        if(uiFacade instanceof AbyssUiService ui)ui.stopQueries();
        for(ScheduledTask task:serviceTasks)task.cancel();
        serviceTasks.clear();partyRefreshQueued.clear();
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
        // 释放 Adyeshach 桥（反注册 interact/visible 监听器、清空管理器引用），防重载后指向死服务
        if (npcBridge != null) {
            try { npcBridge.shutdown(); } catch (Throwable ignored) { }
        }
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
        if (links != null) links.close();
        if (worldPool != null) {
            worldPool.releasePreload();
            worldPool.shutdown();
        }
        if (playerData != null) playerData.shutdown();
        if (repository != null) repository.shutdown();
        if (crossDungeon != null) crossDungeon.stop();
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
        // procgen 示例地牢 + 房间集（缺文件才导，不覆盖服主自建）
        File pg = new File(dataFolder, "abysses/procgen");
        for (String f : new String[]{"config.yml", "procgen.yml",
                "npcs.yml", "rewards.yml", "scripts.yml", "shops.yml"}) {
            File target = new File(pg, f);
            if (target.isFile()) continue;
            try {
                module.exportModuleResource("abysses/procgen/" + f, target, false);
            } catch (Throwable t) {
                logger.fine("[abyss] procgen 示例导出失败 " + f + ": " + t.getMessage());
            }
        }
        File rs = new File(dataFolder, "room-sets/example");
        for (String f : new String[]{
                "start_1.yml", "start_1.region.yml",
                "combat_1.yml", "combat_1.region.yml",
                "combat_2.yml", "combat_2.region.yml",
                "elite_1.yml", "elite_1.region.yml",
                "boss_1.yml", "boss_1.region.yml",
                "shop_1.yml", "shop_1.region.yml",
                "treasure_1.yml", "treasure_1.region.yml",
                "rest_1.yml", "rest_1.region.yml"}) {
            File target = new File(rs, f);
            if (target.isFile()) continue;
            try {
                module.exportModuleResource("room-sets/example/" + f, target, false);
            } catch (Throwable t) {
                logger.fine("[abyss] 房间集导出失败 " + f + ": " + t.getMessage());
            }
        }
    }

    /** 重载模板（/axs reload abyss 时模块会重建服务；另供 admin reload 用）。 */
    public void reloadTemplates() {
        admission.catalogChanged();
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
        // 区域模板缓存失效：重载后文件可能已更新
        if (regionLibrary != null) {
            for (AbyssTemplate t : instances.getAllTemplates()) {
                regionLibrary.evict(t.id());
            }
        }
        // 房间集缓存失效（room-sets/ 下文件可能已更新）
        if (roomSetLibrary != null) roomSetLibrary.reload();
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
    /** 阶段机（无 stages 配置的副本内部为空操作）。 */
    public xuanmo.arcartxsuite.abyss.stage.StageManager stages() { return stageManager; }
    /** 随机地牢运行管理器（procgen.yml 配置的副本）。 */
    public xuanmo.arcartxsuite.abyss.procgen.ProcGenManager procgen() { return procgenManager; }
    /** 房间集库（全局共享池 data/abyss/room-sets/）。 */
    public xuanmo.arcartxsuite.abyss.procgen.RoomSetLibrary roomSets() { return roomSetLibrary; }
    public ScriptRunner scripts() { return scriptRunner; }
    /** ARIA/JS 动作执行器（宿主 ScriptConditionEvaluator 同时实现 ScriptActionExecutor）。 */
    public xuanmo.arcartxsuite.api.condition.ScriptActionExecutor scriptActions() {
        return scriptConditionEvaluator instanceof
            xuanmo.arcartxsuite.api.condition.ScriptActionExecutor ex
            ? ex : xuanmo.arcartxsuite.api.condition.ScriptActionExecutor.noop();
    }
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
    /** 区域模板库（paste_region / /axs abyss region 共用）。 */
    public xuanmo.arcartxsuite.abyss.region.RegionLibrary regions() {
        return regionLibrary;
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
    public EnterResult enterFromPlayer(Player player, String templateId, @Nullable String plan) {
        Party party = partyManager.partyOf(player);
        if (party != null && party.size() > 1) {
            if (!party.isLeader(player.getUniqueId()))
                return EnterResult.fail(msg("error.not-party-leader"));
            if (!party.allReady()) return EnterResult.fail(msg("party.not-all-ready"));
            if (party.onlineMembers().size() != party.size())
                return EnterResult.fail("队伍成员未全部在线");
            return enterDungeon(party.onlineMembers(), templateId, plan);
        }
        return enterDungeon(List.of(player), templateId, plan);
    }

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
        if (players.isEmpty()) return EnterResult.fail("队伍为空");
        var intent = new Intent("abyss", UUID.randomUUID(), players.get(0).getUniqueId(),
            players.stream().map(Player::getUniqueId).toList(), templateId, plan, null, null);
        return enterDungeon(players, intent, new AdmissionAccess.Operation(intent));
    }

    EnterResult enterExternal(Intent intent, AdmissionAccess.Operation operation) {
        var failures = preflightExternal(intent);
        if (!failures.isEmpty()) {
            for (Denial failure : failures) {
                if (failure.member() != null) operation.outcomes.put(failure.member(), failure.code());
            }
            return EnterResult.fail(failures.get(0).code());
        }
        return enterDungeon(resolveMembers(intent), intent, operation);
    }

    private List<Player> resolveMembers(Intent intent) {
        var ordered = new java.util.ArrayList<>(intent.members());
        ordered.remove(intent.actor());
        ordered.add(0, intent.actor());
        return ordered.stream().map(org.bukkit.Bukkit::getPlayer).filter(java.util.Objects::nonNull).toList();
    }

    List<Denial> preflightExternal(Intent intent) {
        var failures = new java.util.ArrayList<Denial>();
        var template = instances.getTemplate(intent.dungeon());
        if (template == null) return List.of(AdmissionAccess.deny("UNKNOWN_DUNGEON", "副本不存在"));
        String costOwner = template.requirements().externalCostOwner();
        if (costOwner != null) {
            if (!entryCosts.available(costOwner))
                failures.add(AdmissionAccess.deny("COST_PROVIDER_UNAVAILABLE", "门票提供方不可用"));
            if (!template.requirements().requiredItems().isEmpty())
                failures.add(AdmissionAccess.deny("COST_CONFIGURATION_CONFLICT", "外部门票与原生物品消耗不能同时配置"));
        }
        if (template.crossServer() != null && !template.crossServer().isBlank()
            && (crossDungeon == null || !template.crossServer().equals(crossDungeon.nodeId())))
            return List.of(AdmissionAccess.deny("REMOTE_REQUEST_UNSUPPORTED", "公开事务暂不支持跨服转发"));
        if (intent.plan() != null && !intent.plan().isBlank() && !template.plans().containsKey(intent.plan()))
            failures.add(AdmissionAccess.deny("UNKNOWN_PLAN", "方案不存在"));
        if (!instances.hasAvailableSlot(template))
            failures.add(AdmissionAccess.deny("CAPACITY", "副本实例已达上限"));
        List<Player> members = resolveMembers(intent);
        Player actor = org.bukkit.Bukkit.getPlayer(intent.actor());
        Party party = actor == null ? null : partyManager.partyOf(actor);
        if (intent.externalPartyId() != null) {
            var verified = externalParties.verify(intent);
            if (verified == null) failures.add(AdmissionAccess.deny("EXTERNAL_ROSTER_UNAVAILABLE", "外部队伍或准备状态未通过确认"));
            else party = ExternalParties.context(verified);
        }
        if (party != null && (!party.isLeader(intent.actor()) || !party.allReady()
            || !new java.util.HashSet<>(party.members()).equals(new java.util.HashSet<>(intent.members()))))
            failures.add(AdmissionAccess.deny("ROSTER_CHANGED", "需要真实队长及完整的已准备队伍"));
        if (party == null && intent.members().size() != 1)
            failures.add(AdmissionAccess.deny("PARTY_REQUIRED", "整队入场需要对应的原生队伍"));
        for (UUID id : intent.members()) {
            Player player = org.bukkit.Bukkit.getPlayer(id);
            if (player == null || !player.isOnline() || player.isDead()) {
                failures.add(new Denial(id, "MEMBER_UNAVAILABLE", "成员离线或已死亡"));
                continue;
            }
            if (recoveringPlayers.containsKey(id) || instances.getInstance(player) != null
                || admission.reservedByOther(id, intent) || reconnects.eligible(id) != null)
                failures.add(new Denial(id, "MEMBER_BUSY", "成员正在副本中或等待恢复"));
            var check = requirementChecker.checkSingle(template, player);
            if (!check.ok()) failures.add(new Denial(id, "REQUIREMENT", check.reason()));
            if (links != null && links.moduleEntryLocked(player, "abyss." + template.id()))
                failures.add(new Denial(id, "QUEST_GATE", "成员尚未解锁主线门禁"));
        }
        if (failures.isEmpty()) {
            var group = requirementChecker.check(template, members, party);
            if (!group.ok()) failures.add(AdmissionAccess.deny("REQUIREMENT", group.reason()));
        }
        failures.addAll(admission.policies(intent, template));
        return failures;
    }

    private EnterResult enterDungeon(List<Player> players, Intent intent, AdmissionAccess.Operation operation) {
        String templateId = intent.dungeon();
        String plan = intent.plan();
        if (!started || players.isEmpty()) {
            return EnterResult.fail("服务未就绪");
        }
        AbyssTemplate template = instances.getTemplate(templateId);
        if (template == null) {
            return EnterResult.fail(msg("error.dungeon-not-found", "dungeon", templateId));
        }
        String costOwner = template.requirements().externalCostOwner();
        if (costOwner != null && (!costOwner.equals(operation.costOwner)
            || operation.costState != xuanmo.arcartxsuite.api.capability.AbyssEntryCostAccess.State.COMMITTED))
            return EnterResult.fail("该模板需要由正式入场接口协调外部门票");
        // 逐人查重：任一成员已在其它实例则整队拒绝
        // （只查首人会漏掉队员→其旧实例成员关系残留、被映射进新实例双重记账）
        for (Player member : players) {
            if (reconnects.eligible(member.getUniqueId()) != null) return EnterResult.fail("成员仍有原副本回归资格");
            if (admission.reservedByOther(member.getUniqueId(), intent)) return EnterResult.fail("成员已有待处理入场");
            if (!member.isOnline() || member.isDead()) return EnterResult.fail("成员离线或已死亡");
            if(recoveringPlayers.containsKey(member.getUniqueId()))return EnterResult.fail("玩家断线恢复尚未完成，请稍后重试或重新连接服务器");
            if (instances.getInstance(member) != null) {
                return EnterResult.fail(msg("error.already-in-dungeon-member",
                    "player", member.getName()));
            }
        }
        if (!instances.hasAvailableSlot(template)) {
            return EnterResult.fail("副本实例已达上限");
        }
        List<Denial> policyDenials = admission.policies(intent, template);
        if (!policyDenials.isEmpty()) return EnterResult.fail(policyDenials.get(0).message());
        Party party = partyManager.partyOf(players.get(0));
        if (intent.externalPartyId() != null) {
            operation.externalRoster = externalParties.verify(intent);
            if (operation.externalRoster == null) return EnterResult.fail("外部队伍状态已变化或存在原生队伍冲突");
            party = ExternalParties.context(operation.externalRoster);
        }
        if (players.size() > 1 && (party == null || !party.isLeader(intent.actor()) || !party.allReady()
            || !new java.util.HashSet<>(party.members()).equals(new java.util.HashSet<>(intent.members()))))
            return EnterResult.fail("需要真实队长及完整的已准备队伍");
        RequirementChecker.CheckResult check = requirementChecker.check(template, players, party);
        if (!check.ok()) return EnterResult.fail(msg("error.requirements-failed", "reason", check.reason()));
        if (links != null) {
            for (Player member : players) {
                if (links.moduleEntryLocked(member, "abyss." + template.id()))
                    return EnterResult.fail(msg("dungeon.module-locked"));
            }
        }
        // 远端副本：cross-server 声明宿主节点。
        //   本节点=宿主 → 走本地路径；本节点≠宿主 → 跨服握手前送；
        //   跨服通道未启用且非宿主 → 拒绝（不能静默退化为本地副本）。
        String cs = template.crossServer();
        if (cs != null && !cs.isBlank()) {
            boolean isHost = crossDungeon != null && cs.equals(crossDungeon.nodeId());
            if (!isHost) {
                if (crossDungeon != null && crossDungeon.active()) {
                    crossDungeon.requestRemoteEnter(players, template,plan);
                    return EnterResult.success();
                }
                return EnterResult.fail(msg("cross.forward-failed", "node", cs));
            }
        }
        // 预热严格模式：配置了预热的地牢池空则拒绝进入
        var preloadCfg = config.abyss().preload();
        if (preloadCfg.enabled() && preloadCfg.strict()
            && preloadCfg.dungeons().containsKey(template.id())
            && worldPool.preparedCount(template.id()) == 0) {
            return EnterResult.fail(msg("error.preload-empty"));
        }
        // procgen 前置校验：房间集缺失/缺必需房型 → 加载世界前就拒绝
        if (template.procgen() != null && procgenManager != null) {
            String perr = procgenManager.validate(template);
            if (perr != null) return EnterResult.fail(perr);
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
        admission.attach(instance, operation);
        if(party!=null)instance.getVariables().put("admission_party_revision",party.revision());
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

    private void publishAdmission(AbyssInstance instance,List<Player> players) {
        instance.confirmAdmission();
        runResults.admitted(instance, admission.operation(instance));
        AbyssTemplate template=instance.getTemplate();
        String resolvedPlan=(String)instance.getVariables().get("plan");
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
        return instance.designMode();
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
        instance.designMode(true);
        if (designOwners.putIfAbsent(template.id(), instance.getInstanceId()) != null) {
            instances.destroyInstance(instance);
            return EnterResult.fail("该副本正在被其他设计会话编辑");
        }
        instances.playerEnterInstance(player, instance);
        player.sendMessage(msg("design.preparing"));
        worldPool.acquireDesignWorld(template, instance.getInstanceId())
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
        if (instance.isFinished() || !player.isOnline() || !instance.hasPlayer(player.getUniqueId())
            || instances.getInstance(instance.getInstanceId()) != instance) {
            if (instances.getInstance(instance.getInstanceId()) == instance) destroyInstance(instance, true);
            else worldPool.releaseWorld(instance.getInstanceId());
            return;
        }
        instance.setWorld(world);
        worldIndex.put(world.getName(), instance.getInstanceId());
        Location spawn = parseLocation(instance, instance.getTemplate().spawn());
        if (spawn == null) spawn = world.getSpawnLocation();
        instance.saveOriginalLocation(player, player.getLocation());
        instance.savePlayerGameMode(player);
        // procgen 副本设计模式：物化几何供作者进世界检查房间构件
        //（不注册运行时——design 不跑 startInstance，怪组/交互/NPC 不落）
        if (instance.getTemplate().procgen() != null && procgenManager != null) {
            try {
                Location generatedSpawn = procgenManager.materialize(instance, world);
                if (generatedSpawn == null) throw new IllegalStateException("无法生成设计预览");
                spawn = generatedSpawn;
            } catch (Throwable t) {
                logger.warning("[abyss] procgen 设计物化失败: " + t.getMessage());
                destroyInstance(instance, true);
                player.sendMessage("§c随机房间预览生成失败，已安全退出。");
                return;
            }
        }
        // world_init 是传送前的造地形段——设计模式也要跑，否则脚本建图的副本是空虚空；
        // init/start 等玩法脚本仍跳过（实例不激活玩法系统）
        if (!usesDesignSnapshot(instance.getTemplate())) runLifecycleScript(instance, "world_init", null);
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
        int wandSlot = player.getInventory().firstEmpty();
        if (wandSlot < 0) {
            player.sendMessage("§c请留出一个背包空位用于设计标记杖。");
            destroyInstance(instance, true);
            return;
        }
        player.getInventory().setItem(wandSlot, designListener.wandItem());
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
    private boolean usesDesignSnapshot(AbyssTemplate template) {
        return org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
            new File(template.dir(), "config.yml")).getBoolean("world.design-snapshot", false);
    }

    public void startDesignTrial(Player player) {
        var session = designManager.session(player);
        if (session == null || session.saving()) return;
        try {
            var source = session.instance().getTemplate();
            var files = xuanmo.arcartxsuite.abyss.design.DesignWriter.prepare(source, session);
            var draft = templateLoader.loadPrepared(source.id(), source.dir(), files);
            if (draft == null) throw new IllegalArgumentException("无法加载试跑草稿");
            Map<xuanmo.arcartxsuite.abyss.design.DraftTrial.Hook, xuanmo.arcartxsuite.abyss.design.DraftTrial.Event> hooks = new LinkedHashMap<>();
            for (var object : session.objects().values()) {
                for (var field : xuanmo.arcartxsuite.abyss.design.DesignObject.schema(object.kind())) {
                    if (field.editor() == xuanmo.arcartxsuite.abyss.design.DesignObject.Editor.SCRIPT) {
                        hooks.put(new xuanmo.arcartxsuite.abyss.design.DraftTrial.Hook(object.key(), field.name()),
                            new xuanmo.arcartxsuite.abyss.design.DraftTrial.Event(object.id(), object.kind(), object.strList(field.name())));
                    }
                }
            }
            var refreshQueued = new java.util.concurrent.atomic.AtomicBoolean();
            var trial = new xuanmo.arcartxsuite.abyss.design.DraftTrial(draft, stageManager, scriptRunner, hooks, line -> {
                if (!refreshQueued.compareAndSet(false, true)) return;
                scheduleDelayedRaw(() -> {
                    refreshQueued.set(false);
                    if (player.isOnline() && designManager.session(player) == session
                        && "misc:trial".equals(session.selectedNode()) && uiFacade instanceof AbyssUiService ui) {
                        ui.pushDesignMain(player, session);
                    }
                }, 1L);
            });
            session.trial(trial);
            trial.start();
        } catch (Exception error) {
            session.trial(null);
            player.sendMessage("§c草稿试跑未启动：" + error.getMessage());
        }
    }

    public void saveDesign(Player player, boolean withWorld) {
        var session = designManager != null ? designManager.session(player) : null;
        if (session == null) { player.sendMessage(msg("design.no-session")); return; }
        if (session.saving()) { player.sendMessage("§e正在保存，请稍候。"); return; }
        AbyssInstance instance = session.instance();
        AbyssTemplate template = instance.getTemplate();
        try {
            xuanmo.arcartxsuite.abyss.design.DesignWriter.prepare(template, session);
        } catch (Exception ex) {
            player.sendMessage("§c未保存，编辑内容已保留: " + ex.getMessage());
            return;
        }
        if (!withWorld) { finishDesignSave(player, session, false); return; }
        session.saving(true);
        if (uiFacade instanceof AbyssUiService ui) ui.pushDesignMain(player, session);
        player.sendMessage("§e正在保存建筑和配置，请等待完成提示。");
        if (template.procgen() != null) {
            try {
                var capture = procgenManager.captureDesignRoom(instance, player.getLocation());
                var writes=xuanmo.arcartxsuite.abyss.design.DesignWriter.prepareWrites(template,session);
                var captured=capture.writes();
                var meta=new File(capture.root(),capture.name()+".yml").toPath().toAbsolutePath().normalize();
                if(writes.containsKey(meta)) {
                    var edited=new org.bukkit.configuration.file.YamlConfiguration();
                    edited.loadFromString(new String(writes.get(meta),java.nio.charset.StandardCharsets.UTF_8));
                    edited.set("geometry-source","region");
                    captured.put(meta,edited.saveToString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                writes.putAll(captured);
                var baseline=new java.util.LinkedHashMap<>(session.fileHashes());
                capture.baseline().forEach((name,hash)->baseline.put(template.dir().toPath().toAbsolutePath().normalize()
                    .relativize(new File(capture.root(),name).toPath().toAbsolutePath().normalize()).toString(),hash));
                worldPool.runIo(instance.getInstanceId(), () -> {
                    try { xuanmo.arcartxsuite.abyss.design.DesignWriter.commit(writes,baseline,template.dir().toPath()); }
                    catch (Exception ex) { throw new java.util.concurrent.CompletionException(ex); }
                }).whenComplete((v, error) -> runOnMain(() -> {
                    if (error != null) designSaveFailed(player, session, error);
                    else {
                        session.acknowledgeRegion(capture.bounds());
                        procgenManager.acknowledgeDesignRoom(instance, capture);
                        roomSetLibrary.reload(); finishPublishedDesign(player, session, true);
                    }
                }));
            } catch (Exception ex) { designSaveFailed(player, session, ex); }
            return;
        }
        World world = instance.getWorld();
        File source = worldPool.worldDirOf(instance.getInstanceId());
        if (world == null || source == null) {
            designSaveFailed(player, session, new IllegalStateException("设计世界尚未就绪"));
            return;
        }
        final Map<java.nio.file.Path,byte[]> configWrites;
        final Map<String,String> configBaseline=new java.util.LinkedHashMap<>(session.fileHashes());
        try {
            session.worldSnapshot(true);
            configWrites=xuanmo.arcartxsuite.abyss.design.DesignWriter.prepareWrites(template,session);
        } catch(Exception invalid) { session.worldSnapshot(false); designSaveFailed(player,session,invalid); return; }
        Location designLocation = player.getLocation().clone();
        Location waiting = instance.getOriginalLocation(player.getUniqueId());
        if (waiting == null || !teleportWithRetry(player, waiting, 3)) {
            designSaveFailed(player, session, new IllegalStateException("无法移动到安全等候位置，建筑未保存"));
            return;
        }
        try {
            if (!worldPool.suspendForSnapshot(instance.getInstanceId())) {
                teleportWithRetry(player, designLocation, 3);
                throw new IllegalStateException("世界无法安全卸载，建筑未保存");
            }
        } catch (Throwable error) { designSaveFailed(player, session, error); return; }
        File target = new File(template.dir(), "world");
        worldPool.runIo(instance.getInstanceId(), () -> {
            try { xuanmo.arcartxsuite.abyss.world.WorldCopyUtil.publishWorldFolder(source,target,
                ()->xuanmo.arcartxsuite.abyss.design.DesignWriter.commit(configWrites,configBaseline,template.dir().toPath())); }
            catch (Exception ex) { throw new java.util.concurrent.CompletionException(ex); }
        }).whenComplete((v, error) -> runOnMain(() -> {
            if (instances.getInstance(instance.getInstanceId()) == instance) {
                try {
                    World resumed = worldPool.resumeAfterSnapshot(instance.getInstanceId(), template);
                    instance.setWorld(resumed);
                    if (player.isOnline() && designManager.session(player) == session) {
                        Location back = new Location(resumed, designLocation.getX(), designLocation.getY(),
                            designLocation.getZ(), designLocation.getYaw(), designLocation.getPitch());
                        if (!teleportWithRetry(player, back, 3)) throw new IllegalStateException("无法返回设计世界");
                    }
                } catch (Throwable resumeError) {
                    if(error==null) {
                        finishPublishedDesign(player,session,true);
                        player.sendMessage("§e文件已保存，但设计世界恢复失败，请退出后重新进入。");
                        return;
                    }
                    designSaveFailed(player, session, resumeError);
                    // Keep configuration drafts and the original return location available for recovery.
                    return;
                }
            }
            if (error != null) designSaveFailed(player, session, error);
            else {
                finishPublishedDesign(player, session, true);
            }
        }));
    }

    private void designSaveFailed(Player player,
            xuanmo.arcartxsuite.abyss.design.DesignSession session, Throwable error) {
        session.saving(false);
        session.worldSnapshot(false);
        logger.warning("[abyss] 设计保存失败，保留编辑内容: " + error);
        player.sendMessage("§c建筑未保存，配置编辑仍保留: " +
            (error.getCause() != null ? error.getCause().getMessage() : error.getMessage()));
        if (uiFacade instanceof AbyssUiService ui) ui.pushDesignMain(player, session);
    }

    private void finishDesignSave(Player player,
            xuanmo.arcartxsuite.abyss.design.DesignSession session, boolean savedWorld) {
        var result = xuanmo.arcartxsuite.abyss.design.DesignWriter.save(
            session.instance().getTemplate(), session, logger);
        session.saving(false);
        if (!result.ok()) {
            player.sendMessage("§c配置未保存，编辑内容已保留: " + String.join("; ", result.errors())
                + (savedWorld ? " §e建筑已单独保存，可修正配置后重试。" : ""));
            return;
        }
        finishPublishedDesign(player,session,savedWorld);
    }

    private void finishPublishedDesign(Player player,
            xuanmo.arcartxsuite.abyss.design.DesignSession session, boolean savedWorld) {
        session.saving(false);
        reloadTemplates();
        AbyssTemplate updated = instances.getTemplate(session.instance().getTemplate().id());
        if (updated == null) {
            player.sendMessage("§c文件已写入，但模板重载失败；编辑状态已保留，请检查日志。");
            return;
        }
        session.instance().updateDesignTemplate(updated);
        session.worldSnapshot(false);
        if (savedWorld && updated.procgen() == null) session.worldDirty(false);
        designManager.refresh(session);
        player.sendMessage("§a" + (savedWorld
            ? (updated.procgen() != null ? "当前房间构件和配置已保存；其他房间请分别保存。" : "建筑和配置已完整保存。")
            : "配置已保存。建筑改动需使用建筑保存按钮。"));
        if (uiFacade instanceof AbyssUiService ui && designManager.session(player) == session)
            ui.pushDesignMain(player, session);
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
        if (session.saving()) { player.sendMessage("§e正在保存，请等待完成后再退出。"); return; }
        if ((session.hasDirty() || session.worldDirty()) && !session.confirmDiscard()) {
            player.sendMessage("§e还有未保存的改动。请先保存，或在10秒内再次点击退出以放弃改动。");
            return;
        }
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

        // 方案落定：入口固定 plan 直接用；模板多方案且未固定 → 弹选择菜单
        String plan = ep.plan();
        if ((plan == null || plan.isBlank()) && template.plans().size() > 1) {
            sendPlanMenu(player, template);
            return true;
        }
        EnterResult r = enterFromPlayer(player, template.id(), plan);
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
        if (instance.isFinished() || instances.getInstance(instance.getInstanceId()) != instance) {
            worldPool.releaseWorld(instance.getInstanceId());
            return;
        }
        instance.setWorld(world);
        worldIndex.put(world.getName(), instance.getInstanceId());
        broadcast(instance, msg("dungeon.world-ready"));
        scheduleOnce(instance, () -> abortAdmission(instance, "PREPARATION_TIMEOUT"),
            Math.max(20L, config.abyss().worldLoadTimeout() / 50L));

        Location spawn = spawnLocation(instance);
        if (spawn == null) {
            broadcast(instance, msg("error.enter-failed", "reason", "出生点无效"));
            destroyInstance(instance, true);
            return;
        }

        // 先异步预载副本包围盒内全部区块，再跑 world-init——否则 fillblocks 逐块
        // setType 会在主线程同步触发未生成区块的 initialize_light（看门狗级卡顿）
        preloadInstanceChunks(instance, world).whenComplete((v, err) -> runOnMain(() -> {
            if (err != null) {
                logger.warning("[abyss] 区块预载未完成，取消入场: " + err.getMessage());
                abortAdmission(instance, "CHUNK_PREPARATION_FAILED");
                return;
            }
            if (instance.isFinished()) return;
            continueWorldReady(instance, world, spawn, players);
        }));
    }

    /** 区块就绪后的进图主流程：world-init → 落点兜底 → 传送+扣费 → 全员进图判定。 */
    private void continueWorldReady(AbyssInstance instance, World world,
                                    Location spawn, List<Player> players) {
        if (instances.getInstance(instance.getInstanceId()) != instance || instance.isFinished()) return;
        // procgen 物化先于 world-init：随机地牢几何由引擎建好，
        // world-init 脚本仍可在其上叠加；出生点换到起点房 spawn
        AbyssTemplate tpl = instance.getTemplate();
        if (tpl.procgen() != null && procgenManager != null) {
            try {
                Location s = procgenManager.materialize(instance, world);
                if (s != null) spawn = s;
            } catch (Throwable t) {
                logger.warning("[abyss] procgen 物化失败 " + tpl.id()
                    + ": " + t.getMessage());
                failDungeon(instance, FailReason.SCRIPTED);
                return;
            }
        }
        // world-init 段：玩家传送前跑（造平台/铺场景），init 保持原位（进图后）
        Location initial = spawn;
        afterAdmissionScript(instance, usesDesignSnapshot(tpl) ? List.of() : tpl.scripts().worldInit(),
            "WORLD_INIT_FAILED", () -> transferAdmission(instance, world, initial, players));
    }

    private void transferAdmission(AbyssInstance instance, World world, Location spawn, List<Player> players) {
        ensureFooting(world, spawn);
        AbyssTemplate tpl = instance.getTemplate();

        // Revalidate the complete admission after asynchronous world preparation.
        var op = admission.operation(instance);
        boolean external = op != null && op.externalRoster != null;
        boolean changed = instances.getTemplate(tpl.id()) != tpl || players.stream().anyMatch(p -> !p.isOnline() || p.isDead() || !instance.hasPlayer(p.getUniqueId())
            || (!external && partyManager.partyOf(p) != instance.getParty()));
        if (external) changed |= !op.externalRoster.equals(externalParties.verify(op.intent));
        if(instance.getParty()!=null)changed |= !instance.getParty().allReady()
            || !java.util.Objects.equals(instance.getVariables().get("admission_party_revision"),instance.getParty().revision());
        var fresh = changed ? RequirementChecker.CheckResult.fail("队伍或成员状态已变化")
            : requirementChecker.check(tpl, players, instance.getParty());
        if (!fresh.ok()) {
            broadcast(instance, msg("error.enter-failed", "reason", fresh.reason()));
            destroyInstance(instance, true); return;
        }
        if (op != null) {
            if (!entryCosts.authorized(op)) {
                op.reason = "COST_PROVIDER_CHANGED";
                destroyInstance(instance, true);
                return;
            }
            if (!admission.policyUnchanged(op)) {
                op.reason = "POLICY_CHANGED";
                destroyInstance(instance, true);
                return;
            }
            var denials = admission.policies(op.intent, tpl);
            if (!denials.isEmpty()) {
                op.reason = denials.get(0).code();
                broadcast(instance, denials.get(0).message());
                destroyInstance(instance, true);
                return;
            }
            for (Player player : players) {
                var individual = !op.intent.owner().equals("abyss")
                    ? requirementChecker.checkSingle(tpl, player) : RequirementChecker.CheckResult.pass();
                if (!individual.ok() || (links != null && links.moduleEntryLocked(player, "abyss." + tpl.id()))) {
                    op.outcomes.put(player.getUniqueId(), "FINAL_CHECK_FAILED");
                    broadcast(instance, msg("dungeon.module-locked"));
                    destroyInstance(instance, true);
                    return;
                }
            }
            op.phase = Phase.TELEPORTING;
            admission.changed(op);
        }
        // All players and resources form one admission operation. A failed teleport refunds all.
        for (Player p : players) {
            instance.saveOriginalLocation(p, p.getLocation());
            instance.savePlayerGameMode(p);
            boolean ok;
            transferringInstances.add(instance.getInstanceId());
            try {ok=teleportWithRetry(p,spawn,3);}
            finally {transferringInstances.remove(instance.getInstanceId());}
            if (ok && p.isOnline() && p.getWorld() == world && instance.hasPlayer(p.getUniqueId())
                && !instance.isFinished() && !stopping) {
                instance.markEntered(p.getUniqueId());
                if (op != null) op.outcomes.put(p.getUniqueId(), "ARRIVED");
            } else {
                rollbackAdmission(instance, op, p, null);
                return;
            }
        }
        afterAdmissionScript(instance, tpl.scripts().init(), "INIT_FAILED",
            () -> scheduleOnce(instance, () -> advanceAdmission(instance, true, "RUNTIME_INIT_FAILED",
                () -> startInstance(instance, players)), 40L));
    }

    /**
     * 异步预载副本包围盒内全部区块（Paper {@code getChunkAtAsync}）。
     * 未预载时 world-init 的 fillblocks 逐块 setType 会在主线程同步触发区块生成与
     * initialize_light——几万块填充直接把主线程卡死（看门狗连续 dump）。
     * 包围盒 = 全部房间 region/exit/spawn/门盒（region 模式含 origin 偏移）
     * + 层 spawn + NPC 坐标；pool 模式按 direction/room-spacing 估算锚点带。
     * 外扩 1 区块余量；超出 2048 区块告警并截断（防配置笔误全图预载）。
     */
    private java.util.concurrent.CompletableFuture<Void> preloadInstanceChunks(
            AbyssInstance instance, World world) {
        AbyssTemplate t = instance.getTemplate();
        int[] bbox = {Integer.MAX_VALUE, Integer.MAX_VALUE,
            Integer.MIN_VALUE, Integer.MIN_VALUE}; // minX, minZ, maxX, maxZ
        xuanmo.arcartxsuite.abyss.template.FloorConfig fc = t.floors();
        if (fc != null) {
            for (xuanmo.arcartxsuite.abyss.template.FloorConfig.Floor floor : fc.floors()) {
                growVec(bbox, floor.spawn());
                if (floor.source()
                        == xuanmo.arcartxsuite.abyss.template.FloorConfig.Source.REGION) {
                    for (String ref : floor.roomRefs()) growRoom(bbox, fc, ref);
                    if (floor.bossRoom() != null) growRoom(bbox, fc, floor.bossRoom());
                } else {
                    // pool：锚点从原点起沿 direction 按 room-spacing 排布，横向留一间房余量
                    int n = floor.roomCount() <= 0 ? floor.pool().size()
                        : Math.min(floor.roomCount(), floor.pool().size());
                    if (floor.bossRoom() != null) n++;
                    int spacing = Math.max(64, fc.defaults().roomSpacing());
                    if (n > 0) {
                        if ("z".equals(floor.direction())) {
                            growBBox(bbox, -spacing, -spacing, spacing, (n + 1) * spacing);
                        } else {
                            growBBox(bbox, -spacing, -spacing, (n + 1) * spacing, spacing);
                        }
                    }
                }
            }
        }
        if (t.npcs() != null) {
            for (var npc : t.npcs().npcs().values()) growVec(bbox, npc.location());
        }
        if (bbox[0] == Integer.MAX_VALUE) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
        int cx1 = (bbox[0] >> 4) - 1, cx2 = (bbox[2] >> 4) + 1;
        int cz1 = (bbox[1] >> 4) - 1, cz2 = (bbox[3] >> 4) + 1;
        long total = (long) (cx2 - cx1 + 1) * (cz2 - cz1 + 1);
        if (total > 2048) {
            logger.warning("[abyss] 副本区块预载范围过大(" + total + " 区块)，截断到 2048");
            double shrink = Math.sqrt(2048.0 / total);
            int midX = (cx1 + cx2) / 2, midZ = (cz1 + cz2) / 2;
            int hx = (int) ((cx2 - cx1) * shrink / 2), hz = (int) ((cz2 - cz1) * shrink / 2);
            cx1 = midX - hx; cx2 = midX + hx; cz1 = midZ - hz; cz2 = midZ + hz;
        }
        java.util.List<int[]> coords = new java.util.ArrayList<>();
        for (int cx = cx1; cx <= cx2; cx++) {
            for (int cz = cz1; cz <= cz2; cz++) {
                coords.add(new int[] {cx, cz});
            }
        }
        if (chunkPreloader == null) return java.util.concurrent.CompletableFuture.failedFuture(
            new IllegalStateException("Chunk preloader unavailable"));
        return chunkPreloader.submit(coords, coordinate -> {
            if (GET_CHUNK_ASYNC == null) {
                world.getChunkAt(coordinate[0], coordinate[1]);
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }
            try {
                return (java.util.concurrent.CompletableFuture<?>) GET_CHUNK_ASYNC.invoke(world, coordinate[0], coordinate[1]);
            } catch (ReflectiveOperationException failure) {
                return java.util.concurrent.CompletableFuture.failedFuture(failure);
            }
        }, () -> running() && !instance.isFinished() && instances.getInstance(instance.getInstanceId()) == instance);
    }

    /** Paper {@code World#getChunkAtAsync(int,int)} 反射句柄；Spigot 缺席为 null。 */
    private static final java.lang.reflect.Method GET_CHUNK_ASYNC = findChunkAsync();

    private static java.lang.reflect.Method findChunkAsync() {
        try {
            return World.class.getMethod("getChunkAtAsync", int.class, int.class);
        } catch (Throwable t) {
            return null;
        }
    }

    private void growRoom(int[] bbox,
                          xuanmo.arcartxsuite.abyss.template.FloorConfig fc, String ref) {
        var tpl = fc.rooms().get(ref);
        if (tpl == null) return;
        int[] o = parseVec3(tpl.origin());
        for (String c : tpl.region()) growVecOffset(bbox, c, o);
        if (tpl.exit() != null) {
            for (String c : tpl.exit()) growVecOffset(bbox, c, o);
        }
        growVecOffset(bbox, tpl.spawn(), o);
        for (var d : tpl.doors()) {
            for (String c : d.region()) growVecOffset(bbox, c, o);
        }
    }

    private static void growVec(int[] bbox, @Nullable String coord) {
        growVecOffset(bbox, coord, null);
    }

    private static void growVecOffset(int[] bbox, @Nullable String coord, int[] origin) {
        int[] v = parseVec3(coord);
        if (v == null) return;
        int x = v[0] + (origin != null ? origin[0] : 0);
        int z = v[2] + (origin != null ? origin[2] : 0);
        if (x < bbox[0]) bbox[0] = x;
        if (z < bbox[1]) bbox[1] = z;
        if (x > bbox[2]) bbox[2] = x;
        if (z > bbox[3]) bbox[3] = z;
    }

    private static void growBBox(int[] bbox, int minX, int minZ, int maxX, int maxZ) {
        if (minX < bbox[0]) bbox[0] = minX;
        if (minZ < bbox[1]) bbox[1] = minZ;
        if (maxX > bbox[2]) bbox[2] = maxX;
        if (maxZ > bbox[3]) bbox[3] = maxZ;
    }

    /** 解析 "x,y,z[,yaw,pitch]" 坐标串；非法返回 null。 */
    private static int[] parseVec3(@Nullable String coord) {
        if (coord == null) return null;
        String[] v = coord.split(",");
        if (v.length < 3) return null;
        try {
            return new int[] {(int) Double.parseDouble(v[0].trim()),
                (int) Double.parseDouble(v[1].trim()),
                (int) Double.parseDouble(v[2].trim())};
        } catch (NumberFormatException e) {
            return null;
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

    private void afterAdmissionScript(AbyssInstance instance, List<String> lines, String failure, Runnable next) {
        try {
            var session = runScript(instance, lines, ScriptContext.preparation(instance));
            if (session == null) advanceAdmission(instance, true, failure, next);
            else session.completion().whenComplete((ok, error) ->
                runOnMain(() -> advanceAdmission(instance, error == null && Boolean.TRUE.equals(ok), failure, next)));
        } catch (RuntimeException error) {
            abortAdmission(instance, failure);
        }
    }

    private void advanceAdmission(AbyssInstance instance, boolean success, String failure, Runnable next) {
        if (instance.getStatus() != AbyssStatus.PREPARING || instances.getInstance(instance.getInstanceId()) != instance) return;
        try {
            if (success) next.run();
            else abortAdmission(instance, failure);
        } catch (RuntimeException error) {
            if (!instance.admitted()) abortAdmission(instance, failure);
            else logger.log(java.util.logging.Level.WARNING, "[abyss] 开局后通知或脚本异常", error);
        }
    }

    private void abortAdmission(AbyssInstance instance, String reason) {
        if (instances.getInstance(instance.getInstanceId()) != instance || instance.admitted() || instance.isFinished()) return;
        if (transferringInstances.contains(instance.getInstanceId())) {
            scheduleDelayedRaw(() -> abortAdmission(instance, reason), 1L);
            return;
        }
        var operation = admission.operation(instance);
        rollbackAdmission(instance, operation, null, null, reason);
    }

    private void startInstance(AbyssInstance instance, List<Player> players) {
        if (instance.getStatus() != AbyssStatus.PREPARING) return;
        AbyssTemplate t = instance.getTemplate();
        // 怪物组（AUTO_START/DELAYED 自动调度）
        mobGroups.initializeGroups(instance);
        if (instance.isFinished()) return;
        // floors 模式：物化楼层房间链（pool 粘贴/组注册，须在怪组表建好后）
        if (t.mode() == AbyssTemplate.Mode.FLOORS && roomManager != null) {
            try {
                roomManager.initInstance(instance);
                // 物化完成后对齐到首层入口（进图时房间尚未生成，落点是模板 spawn）
                Location entry = roomManager.currentSpawn(instance);
                if (entry == null) {
                    abortAdmission(instance, "FINAL_SPAWN_UNAVAILABLE");
                    return;
                } else {
                    for (Player p : players) {
                        if (!p.isOnline() || !teleportWithRetry(p, entry, 3) || p.getWorld() != instance.getWorld()) {
                            rollbackAdmission(instance, admission.operation(instance), p, null, "FINAL_TELEPORT_FAILED");
                            return;
                        }
                    }
                }
            } catch (Throwable ex) {
                logger.warning("[abyss] floors 物化失败 " + t.id() + ": " + ex.getMessage());
                abortAdmission(instance, "FLOOR_INIT_FAILED");
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
        // procgen：怪组/交互/NPC 注册必须在各自 initInstance 之后（init 会覆盖注册表）
        if (procgenManager != null) procgenManager.initRuntime(instance);
        var operation = admission.operation(instance);
        if (instance.isFinished() || instances.getInstance(instance.getInstanceId()) != instance) return;
        if (players.stream().anyMatch(player -> !player.isOnline() || player.isDead()
                || !instance.hasPlayer(player.getUniqueId()) || player.getWorld() != instance.getWorld())) {
            abortAdmission(instance, "MEMBER_LOST_BEFORE_START");
            return;
        }
        if (operation != null && (!entryCosts.authorized(operation) || !admission.policyUnchanged(operation)
            || (operation.externalRoster != null && !operation.externalRoster.equals(externalParties.verify(operation.intent)))
            || (operation.externalRoster == null && players.stream().anyMatch(player ->
                partyManager.partyOf(player) != instance.getParty()))
            || !admission.policies(operation.intent, t).isEmpty())) {
            abortAdmission(instance, "FINAL_ADMISSION_CHANGED");
            return;
        }
        if (instances.getTemplate(t.id()) != t || (instance.getParty() != null
            && (!instance.getParty().allReady() || !java.util.Objects.equals(
                instance.getVariables().get("admission_party_revision"), instance.getParty().revision())))) {
            abortAdmission(instance, "FINAL_ROSTER_CHANGED");
            return;
        }
        var finalCheck = requirementChecker.check(t, players, instance.getParty());
        if (!finalCheck.ok() || players.stream().anyMatch(player ->
                (operation != null && !operation.intent.owner().equals("abyss") && !requirementChecker.checkSingle(t, player).ok())
                || (links != null && links.moduleEntryLocked(player, "abyss." + t.id())))) {
            abortAdmission(instance, "FINAL_REQUIREMENTS_FAILED");
            return;
        }
        RequirementChecker.Charge charge;
        transferringInstances.add(instance.getInstanceId());
        try { charge = requirementChecker.charge(t, players); }
        finally { transferringInstances.remove(instance.getInstanceId()); }
        if (operation != null) operation.nativeCosts.putAll(charge.memberCosts());
        if (!charge.ok()) {
            if (operation != null) operation.costUncertain = charge.reviewRequired();
            abortAdmission(instance, "CHARGE_FAILED");
            return;
        }
        if (players.stream().anyMatch(player -> !player.isOnline() || !instance.hasPlayer(player.getUniqueId())
                || player.getWorld() != instance.getWorld()) || !instance.transitionTo(AbyssStatus.PREPARING, AbyssStatus.ACTIVE)) {
            rollbackAdmission(instance, operation, null, charge, "MEMBER_LOST_BEFORE_START");
            return;
        }
        try {
            charge.commit();
        } catch (RuntimeException failure) {
            if (operation != null) {
                operation.costUncertain = true;
                operation.nativeCosts.replaceAll((id, state) ->
                    state.equals("NATIVE_DEBITED") ? "NATIVE_REVIEW_REQUIRED" : state);
            }
            instance.transitionTo(AbyssStatus.ACTIVE, AbyssStatus.PREPARING);
            rollbackAdmission(instance, operation, null, null, "COOLDOWN_COMMIT_UNKNOWN");
            return;
        }
        instance.confirmAdmission();
        if (operation != null) {
            operation.phase = Phase.STARTED;
            admission.changed(operation);
        }
        publishAdmission(instance, players);
        if (instance.getParty() != null) instance.getParty().resetReadiness();
        runResults.started(instance);
        if (instance.isFinished()) return;
        mobGroups.startConfiguredGroups(instance);
        if (instance.isFinished()) return;
        // start 脚本
        runLifecycleScript(instance, "start", null);
        // 阶段机启动（stages.yml 配置的副本从入口阶段开始推进）
        if (stageManager != null) stageManager.start(instance);
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
    public void completeMap(AbyssInstance instance) {
        instance.getVariables().put("map_cleared", true);
        var stages = instance.getTemplate().stages();
        if (stages != null && !stages.isEmpty()) {
            if (stageManager != null) stageManager.onEvent(instance);
            return;
        }
        completeDungeon(instance);
    }

    public void completeDungeon(AbyssInstance instance) {
        if (isDesignInstance(instance)) return;
        if(!instance.admitted()) {
            logger.warning("[abyss] 尚未进图的实例不能结算通关: "+instance.getInstanceId());
            cancelDungeon(instance);return;
        }
        if (!instance.transitionTo(AbyssStatus.ACTIVE, AbyssStatus.COMPLETED)
            && !instance.transitionTo(AbyssStatus.PREPARING, AbyssStatus.COMPLETED)) {
            return;
        }
        AbyssTemplate t = instance.getTemplate();
        List<UUID> completedPlayers = new ArrayList<>(instance.getPlayerUUIDs());
        List<Player> completedOnlinePlayers = instance.getPlayers();
        runResults.finish(instance, AbyssRunResultAccess.Outcome.COMPLETED, "COMPLETED");
        beginSettlement(instance,true);
        runLifecycleScript(instance, "complete", null);
        broadcast(instance, msg("dungeon.completed",
            "dungeon", t.name(), "time", formatTime(instance.getRunningTime())));
        // 成绩/排行
        long time = instance.getRunningTime();
        for (UUID u : completedPlayers) {
            playerData.saveRecord(u, t.id(), time);
        }
        publishEvent("axs.abyss.complete", null, Map.of(
            "dungeon", t.id(), "instance", instance.getInstanceId().toString(),
            "time_seconds", time));
        // 玩家级通关事件（每名队员一条，供战令/任务/统计订阅）
        for (Player p : completedOnlinePlayers) {
            publishEvent("axs.abyss.player_complete", p, Map.of(
                "dungeon", t.id(), "instance", instance.getInstanceId().toString(),
                "time_seconds", time,
                "party-size", completedPlayers.size()));
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
    }

    /** 失败：状态→FAILED，跑 fail 脚本，撤出+清理。 */
    public void failDungeon(AbyssInstance instance, FailReason reason) {
        if (isDesignInstance(instance)) { cancelDungeon(instance); return; }
        if (!instance.admitted() && admission.operation(instance) != null) {
            abortAdmission(instance, "INIT_" + reason.name());
            return;
        }
        AbyssStatus cur = instance.getStatus();
        if (cur != AbyssStatus.ACTIVE && cur != AbyssStatus.PREPARING) return;
        if (!instance.transitionTo(cur, AbyssStatus.FAILED)) return;
        runResults.finish(instance, AbyssRunResultAccess.Outcome.FAILED, reason.name());
        if(cur==AbyssStatus.PREPARING && !instance.hasEnteredPlayers()) {
            broadcast(instance,msg("dungeon.failed","reason",failReasonText(reason)));
            destroyInstance(instance,true);return;
        }
        AbyssTemplate t = instance.getTemplate();
        List<Player> failedPlayers = instance.getPlayers();
        beginSettlement(instance,false);
        runLifecycleScript(instance, "fail", null);
        broadcast(instance, msg("dungeon.failed", "reason", failReasonText(reason)));
        publishEvent("axs.abyss.fail", null, Map.of(
            "dungeon", t.id(), "instance", instance.getInstanceId().toString(),
            "reason", reason.name()));
        for (Player p : failedPlayers) {
            publishEvent("axs.abyss.player_fail", p, Map.of(
                "dungeon", t.id(), "instance", instance.getInstanceId().toString(),
                "reason", reason.name()));
        }
    }

    /** 取消（管理/无人/卸载）：不跑脚本。 */
    public void cancelDungeon(AbyssInstance instance) {
        if (!instance.admitted() && admission.operation(instance) != null) {
            abortAdmission(instance, "ENTRY_CANCELLED");
            return;
        }
        AbyssStatus cur = instance.getStatus();
        if (instance.isFinished()) return;
        if (!instance.transitionTo(cur, AbyssStatus.CANCELLED)) return;
        runResults.finish(instance, AbyssRunResultAccess.Outcome.CANCELLED, "CANCELLED");
        broadcast(instance, msg("dungeon.cancelled"));
        finishAndCleanup(instance);
    }

    /** 结束统一处理：玩家撤出 → 世界释放 → 实例销毁。 */
    private void finishAndCleanup(AbyssInstance instance) {
        if(instances.getInstance(instance.getInstanceId())!=instance)return;
        if(transferringInstances.contains(instance.getInstanceId())) {scheduleDelayedRaw(()->finishAndCleanup(instance),1L);return;}
        if(!cleanupInProgress.add(instance.getInstanceId()))return;
        try {
            for(Player p:instance.getPlayers())playerExit(instance,p,LeaveReason.END);
            // A rejected return teleport keeps the instance owned and safe for retry.
            if(instance.getOnlinePlayerCount()>0)return;
            SettlementSession draining=settlements.get(instance.getInstanceId());
            if(draining!=null) {
                long now=settlementClock.getAsLong();
                if(draining.drainingSince<0)draining.drainingSince=now;
                var pending=instance.getScriptSessions().stream().filter(s->s.context().allowsFinishedInstance()).toList();
                if(!pending.isEmpty() && now-draining.drainingSince<draining.policy.scriptTimeoutSeconds()*1000L)return;
                if(!pending.isEmpty())logger.warning("[abyss] 结算脚本收尾超时，取消未执行部分: "+instance.getInstanceId()+" "+pending.stream().map(s->s.name()+"@"+s.nextLineIndex()).toList());
            }
            SettlementSession session=settlements.remove(instance.getInstanceId());
            if(session!=null && session.timer!=null)session.timer.cancel();
            destroyInstance(instance,false);
        } finally {cleanupInProgress.remove(instance.getInstanceId());}
    }

    private void beginSettlement(AbyssInstance instance,boolean complete) {
        SettlementConfig config=instance.getTemplate().settlement()!=null?instance.getTemplate().settlement():SettlementConfig.DEFAULT;
        SettlementSession session=new SettlementSession(complete?config.complete():config.failure(),settlementClock.getAsLong());
        if(settlements.putIfAbsent(instance.getInstanceId(),session)!=null)return;
        if(roomManager!=null)roomManager.beginSettlement(instance);
        if(procgenManager!=null)procgenManager.beginSettlement(instance);
        if(mobGroups!=null) {
            int[] progress=mobGroups.groupProgress(instance);
            instance.getVariables().put("settlement_group_total",progress[0]);
            instance.getVariables().put("settlement_group_done",progress[1]);
            mobGroups.cleanup(instance);
        }
        instance.getVariables().put("settlement_dead_players",instance.getPlayers().stream().filter(instance::isPlayerCurrentlyDead)
            .map(Player::getUniqueId).collect(java.util.stream.Collectors.toUnmodifiableSet()));
        for(Player player:instance.getPlayers()) {
            if(session.policy.revivePlayers() && instance.isPlayerCurrentlyDead(player))revivePlayer(instance,player);
            Location safe=player.getLocation();
            if(safe!=null)session.safeLocations.put(player.getUniqueId(),safe.clone());
            if(uiFacade instanceof AbyssUiService ui) {
                Runnable show=ui.captureSettlementStats(player,instance);
                if(session.policy.showStats() && stats.autoShow())session.presentations.put(player.getUniqueId(),show);
                ui.closeInstanceViews(player);ui.pushHudUpdate(player,instance);
            } else if(uiFacade!=null && session.policy.showStats() && stats.autoShow())
                session.presentations.put(player.getUniqueId(),()->uiFacade.openStats(player,instance));
            player.sendMessage("§a"+settlementHint(instance));
        }
        if(!session.immediate)scheduleOnce(instance,()->session.presentations.values().forEach(Runnable::run),3L);
        session.timer=scheduler.scheduleEverySeconds(plugin,()->settlementTick(instance),1,1);
        if(session.timer!=null)instance.registerTask(session.timer);
        if(session.immediate)scheduleOnce(instance,()->settlementTick(instance),2L);
    }

    private void settlementTick(AbyssInstance instance) {
        SettlementSession session=settlements.get(instance.getInstanceId());
        if(session==null)return;
        if(instance.getOnlinePlayerCount()==0){finishAndCleanup(instance);return;}
        long now=settlementClock.getAsLong(),remaining=session.policy.remaining(now,session.deadline);
        for(Player player:instance.getPlayers()) {
            if(session.policy.protectPlayers() && instance.getWorld()!=null && player.getLocation().getY()<instance.getWorld().getMinHeight()+2)
                rescueSettlementPlayer(instance,player);
            if(uiFacade instanceof AbyssUiService ui){ui.pushSettlementUpdate(player,instance);ui.pushHudUpdate(player,instance);}
            if(remaining>0 && remaining!=session.lastAnnounced && (remaining==10 || remaining<=5 || remaining%30==0))
                player.sendMessage("§e副本将在 "+remaining+" 秒后离开；可先拾取掉落物，或使用 /abyss leave。");
        }
        session.lastAnnounced=remaining;
        if(now>=session.deadline && now>=session.nextAttempt && session.attempts<3) {
            session.attempts++;session.nextAttempt=now+2000;
            finishAndCleanup(instance);
            if(settlements.containsKey(instance.getInstanceId()) && session.immediate && session.attempts==1)
                session.presentations.values().forEach(Runnable::run);
        }
    }

    public boolean isSettling(AbyssInstance instance) {return settlements.containsKey(instance.getInstanceId());}
    public boolean protectsSettlement(AbyssInstance instance) {
        SettlementSession session=settlements.get(instance.getInstanceId());return session!=null && session.policy.protectPlayers();
    }
    public void rescueSettlementPlayer(AbyssInstance instance,Player player) {
        SettlementSession session=settlements.get(instance.getInstanceId());
        if(session!=null){Location safe=session.safeLocations.get(player.getUniqueId());if(safe!=null)teleportWithRetry(player,safe,3);}
    }
    public String settlementHint(AbyssInstance instance) {
        SettlementSession session=settlements.get(instance.getInstanceId());
        if(session==null)return "结算已保存，可关闭面板；奖励由宿主自动到账。";
        if(session.attempts>=3)return "自动离开未完成，请使用 /abyss leave 重试或联系管理员。";
        long seconds=session.policy.remaining(settlementClock.getAsLong(),session.deadline);
        if(seconds<0)return "可继续拾取掉落物；点击离开副本或使用 /abyss leave 主动退出。";
        if(seconds==0)return "正在离开副本，结算面板将在传送完成后显示。";
        return "请先拾取掉落物；"+seconds+" 秒后离开，可关闭面板继续拾取。";
    }
    public boolean isExiting(Player player) {return exitingPlayers.contains(player.getUniqueId());}
    public long settlementRemaining(AbyssInstance instance) {
        SettlementSession session=settlements.get(instance.getInstanceId());
        return session==null?-2:session.policy.remaining(settlementClock.getAsLong(),session.deadline);
    }
    public double settlementTimeRatio(AbyssInstance instance) {
        SettlementSession session=settlements.get(instance.getInstanceId());
        if(session==null)return 0;
        if(session.deadline==Long.MAX_VALUE)return 1;
        int total=session.policy.mode()==SettlementConfig.ExitMode.MANUAL?session.policy.manualTimeoutSeconds():session.policy.seconds();
        return total<=0?0:Math.min(1d,Math.max(0d,(double)settlementRemaining(instance)/total));
    }
    public void requestScriptExit(AbyssInstance instance,Player player) {
        if(!instance.isFinished())playerExit(instance,player,LeaveReason.QUIT);
    }
    public void handleExternalDeparture(AbyssInstance instance,Player player) {
        playerExit(instance,player,LeaveReason.QUIT,false);
    }

    /** 玩家退出副本（主动/结束/管理）：传送回原位置 + 恢复模式。 */
    public boolean playerExit(AbyssInstance instance, Player player, LeaveReason reason) {
        return playerExit(instance,player,reason,true);
    }
    private boolean playerExit(AbyssInstance instance,Player player,LeaveReason reason,boolean teleport) {
        if (!instance.hasPlayer(player.getUniqueId())) return true;
        if(isExiting(player))return false;
        boolean designing = isDesignInstance(instance);
        // 先取快照再移除：removePlayer 会清掉 originalLocations/savedGameModes
        Location back = instance.getOriginalLocation(player.getUniqueId());
        GameMode saved = instance.getSavedGameMode(player.getUniqueId());
        if(teleport && player.isOnline() && instance.getWorld()!=null && player.getWorld()==instance.getWorld()) {
            if(back==null || back.getWorld()==null || back.getWorld()==instance.getWorld()) {
                back=links!=null?links.essentialsSpawn(player):null;
                if(back==null || back.getWorld()==instance.getWorld())back=org.bukkit.Bukkit.getWorlds().stream()
                    .filter(w->w!=instance.getWorld() && !w.getName().startsWith(config.abyss().worldPrefix()))
                    .findFirst().map(World::getSpawnLocation).orElse(null);
            }
            boolean returned=false;
            exitingPlayers.add(player.getUniqueId());
            try {if(back!=null)returned=teleportWithRetry(player,back,3);}
            finally {exitingPlayers.remove(player.getUniqueId());}
            if(!returned){player.sendMessage("§c暂时无法返回安全位置，副本与物品已保留，请稍后重试退出。");return false;}
        }
        instance.removePlayer(player.getUniqueId());
        instances.playerLeaveInstance(player);
        runResults.member(instance, player.getUniqueId(), AbyssRunResultAccess.MemberState.LEFT, reason.name(), null);
        var admissionOperation = admission.operation(instance);
        if (admissionOperation != null && "RETURN_PENDING".equals(admissionOperation.outcomes.get(player.getUniqueId()))) {
            admissionOperation.outcomes.put(player.getUniqueId(), "RETURNED");
            if (!admissionOperation.costUncertain && admissionOperation.phase == Phase.REVIEW_REQUIRED
                && !admissionOperation.outcomes.containsValue("RETURN_PENDING")) admissionOperation.phase = Phase.COMPENSATED;
            admission.changed(admissionOperation);
        }
        // 副本内属性修饰出本即清（apply_attribute 动作施加）
        if (links != null) links.removeAttribute(player, "abyss");

        // 先传送后恢复模式：旁观者若已坠入虚空，先回安全点再切回生存，
        // 避免恢复 SURVIVAL 的瞬间在虚空世界吃到真实致死伤害。
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
            ui.closeInstanceViews(player);
        }
        if (!designing && instance.admitted()) runLifecycleScript(instance, "exit", player);
        if (reason != LeaveReason.END && reason != LeaveReason.DISCONNECT) {
            player.sendMessage(msg("dungeon.exited"));
        }
        if(!designing && instance.admitted())publishEvent("axs.abyss.leave", player, Map.of(
            "dungeon", instance.getTemplate().id(),
            "instance", instance.getInstanceId().toString(),
            "reason", reason.name()));
        SettlementSession settlement=settlements.get(instance.getInstanceId());
        if(settlement!=null) {
            Runnable presentation=settlement.presentations.get(player.getUniqueId());
            if(presentation!=null && (settlement.immediate||settlement.policy.showAfterExit()))scheduleDelayedRaw(presentation,3L);
            if(instance.getOnlinePlayerCount()==0 && !cleanupInProgress.contains(instance.getInstanceId()))
                scheduleDelayedRaw(()->finishAndCleanup(instance),1L);
        }
        // 退出后实例无人 → 倒计时清理（宽限等人重连）
        if (instance.getOnlinePlayerCount() == 0 && !instance.isFinished()) {
            long delay = config.abyss().emptyCleanupDelaySeconds() * 20L;
            scheduleOnce(instance, () -> {
                if (instance.getOnlinePlayerCount() == 0 && !reconnects.retains(instance.getInstanceId())) {
                    cancelDungeon(instance);
                }
            }, delay);
        }
        return true;
    }

    /** 玩家断线（监听器调用）：存待恢复位置+从实例移除。 */
    public void handleQuit(Player player) {
        recoveringPlayers.remove(player.getUniqueId(),player);
        // 无论是否在副本内，先清理 UI 会话态（token/HUD 标记）
        if (uiFacade instanceof AbyssUiService ui0) {
            ui0.closeHud(player);
            ui0.onPlayerGone(player);
        }
        if (guideService != null) guideService.handleQuit(player);
        if (designManager != null) {
            designManager.close(player);
            if (designListener != null) designListener.removeWand(player);
        }
        if (crossDungeon != null) crossDungeon.onPlayerQuit(player);
        AbyssInstance instance = instances.getInstance(player);
        if (instance == null) return;
        reconnects.retain(instance, player);
        // 非正常退出标记（下次进服执行恢复动作；顺带记录进本前 GameMode——
        // 旁观死亡态断线重连若不还原会永久卡 SPECTATOR）
        if (recoveryManager != null) {
            org.bukkit.GameMode saved = instance.getSavedGameMode(player.getUniqueId());
            recoveryManager.mark(player.getUniqueId(), instance.getTemplate().id(),
                saved != null ? saved.name() : null);
        }
        Location back = instance.getOriginalLocation(player.getUniqueId());
        var pendingAdmission = admission.operation(instance);
        if (back != null) {
            if (!instance.admitted() && pendingAdmission != null && pendingAdmission.persisted)
                playerData.savePendingLocation(player.getUniqueId(), back, pendingAdmission.intent.owner(), pendingAdmission.intent.requestId());
            else playerData.savePendingLocation(player.getUniqueId(), back);
        }
        instance.removePlayer(player.getUniqueId());
        runResults.member(instance, player.getUniqueId(), AbyssRunResultAccess.MemberState.DISCONNECTED, "DISCONNECT", null);
        if (!instance.admitted() && !instance.isFinished() && pendingAdmission != null) {
            pendingAdmission.outcomes.put(player.getUniqueId(), back != null ? "RETURN_PENDING" : "DISCONNECTED");
            admission.changed(pendingAdmission);
            if (!transferringInstances.contains(instance.getInstanceId())) abortAdmission(instance, "MEMBER_DISCONNECTED");
        }
        if (links != null) links.removeAttribute(player, "abyss");
        // 不触发退出消息；无人清理由 watchdog/playerExit 路径处理
        if (instance.getOnlinePlayerCount() == 0 && !instance.isFinished()) {
            long delay = config.abyss().emptyCleanupDelaySeconds() * 20L;
            scheduleOnce(instance, () -> {
                if (instance.getOnlinePlayerCount() == 0 && !reconnects.retains(instance.getInstanceId())) cancelDungeon(instance);
            }, delay);
        }
        partyManager.onQuit(player);
        if(instance.isFinished() && instance.getOnlinePlayerCount()==0)scheduleDelayedRaw(()->finishAndCleanup(instance),1L);
    }

    /** 玩家进服（监听器调用）：恢复位置 + 预载数据 + 异常退出恢复动作。 */
    public void handleJoin(Player player) {
        recoveringPlayers.put(player.getUniqueId(),player);
        playerData.preload(player.getUniqueId()).thenCompose(v->playerData.loadPendingLocation(player.getUniqueId()))
            .whenComplete((location,error)->runOnMain(()->{
            if(!currentRecovery(player))return;
            if(error!=null){player.sendMessage("§c副本恢复数据读取失败，请重新连接；恢复完成前暂不能进入副本。");return;}
            restoreJoinedPlayer(player,location,0);
        }));
        playerData.preloadCooldowns(player.getUniqueId());
        // 地图入口标记（延迟 2s 等客户端地图就绪）
        if (entranceMarkers != null) {
            scheduler.scheduleOnceSeconds(plugin, () -> {
                if (player.isOnline()) {
                    runOnMain(() -> entranceMarkers.push(player));
                }
            }, 2L);
        }
    }

    private boolean currentRecovery(Player player) {
        return started && !stopping && player.isOnline() && recoveringPlayers.get(player.getUniqueId())==player;
    }
    private void restoreJoinedPlayer(Player player,JdbcAbyssRepository.PendingLocation location,int attempt) {
        if(!currentRecovery(player))return;
        if(location==null){finishJoinRecovery(player);return;}
        World world=org.bukkit.Bukkit.getWorld(location.world());
        Location destination=world!=null && !world.getName().startsWith(config.abyss().worldPrefix())
            ?new Location(world,location.x(),location.y(),location.z(),location.yaw(),location.pitch())
            :org.bukkit.Bukkit.getWorlds().stream().filter(w->!w.getName().startsWith(config.abyss().worldPrefix()))
                .findFirst().map(World::getSpawnLocation).orElse(null);
        if(destination==null || !teleportWithRetry(player,destination,3)) {
            if(attempt<2)scheduleDelayedRaw(()->restoreJoinedPlayer(player,location,attempt+1),20L);
            else player.sendMessage("§c返回传送被拒绝，恢复位置已保留，请重新连接或联系管理员。");
            return;
        }
        playerData.confirmPendingLocation(player.getUniqueId(),location).whenComplete((confirmed,error)->runOnMain(()->{
            if(!currentRecovery(player))return;
            if(error!=null || !Boolean.TRUE.equals(confirmed)) {
                player.sendMessage("§c恢复确认未完成，数据已保留，请重新连接后重试。");return;
            }
            finishJoinRecovery(player);
        }));
    }
    private void finishJoinRecovery(Player player) {
        if(!currentRecovery(player))return;
        var seat = reconnects.eligible(player.getUniqueId());
        if (seat == null) {
            if(recoveryManager!=null)recoveryManager.runIfMarked(player);
        } else {
            if (seat.mode() != null && player.getGameMode() != seat.mode()) player.setGameMode(seat.mode());
            player.sendMessage("§e原副本仍可返回，使用 /abyss rejoin。");
        }
        recoveringPlayers.remove(player.getUniqueId(),player);
        scheduleDelayedRaw(()->{
            if(crossDungeon!=null && player.isOnline() && org.bukkit.Bukkit.getPlayer(player.getUniqueId())==player)
                crossDungeon.onPlayerJoin(player);
        },60L);
    }

    String resumeMember(Player player, RunReconnects.Seat seat) {
        UUID id = player.getUniqueId();
        if (!running() || recoveringPlayers.containsKey(id) || player.isDead()
            || instances.getInstance(player) != null) return "MEMBER_UNAVAILABLE";
        AbyssInstance instance = instances.getInstance(seat.run());
        if (instance == null || instance.getStatus() != AbyssStatus.ACTIVE || instance.isTimeout()
            || transferringInstances.contains(seat.run())) return "RUN_UNAVAILABLE";
        Location target = spawnLocation(instance);
        if (target == null || target.getWorld() != instance.getWorld()) return "CHECKPOINT_UNAVAILABLE";
        Location safe = player.getLocation().clone();
        transferringInstances.add(seat.run());
        try {
            if (!teleportWithRetry(player, target, 3) || player.getWorld() != target.getWorld()) return "TELEPORT_FAILED";
            if (instance.getStatus() != AbyssStatus.ACTIVE || !player.isOnline()) {
                if (player.isOnline() && !teleportWithRetry(player, safe, 3)) {
                    instances.playerEnterInstance(player, instance);
                    instance.saveOriginalLocation(player, safe);
                    scheduleDelayedRaw(() -> finishAndCleanup(instance), 1L);
                    return "RETURN_PENDING";
                }
                return "RUN_UNAVAILABLE";
            }
            instances.playerEnterInstance(player, instance);
            instance.markEntered(id);
            instance.saveOriginalLocation(player, seat.returnLocation());
            if (seat.mode() == null) instance.savePlayerGameMode(player);
            else instance.savePlayerGameMode(id, seat.mode());
            if (seat.dead()) {
                instance.restoreDeathState(id);
                player.setGameMode(GameMode.SPECTATOR);
            }
        } finally {
            transferringInstances.remove(seat.run());
        }
        if (recoveryManager != null) recoveryManager.discardMark(id);
        runResults.reconnected(instance, id);
        if (uiFacade instanceof AbyssUiService ui) ui.openHud(player, instance);
        return "REJOINED";
    }

    void expireReconnect(UUID player, UUID runId) {
        finishExpiredReconnect(player);
        var instance = instances.getInstance(runId);
        if (instance != null && !instance.isFinished() && instance.getOnlinePlayerCount() == 0 && !reconnects.retains(runId))
            cancelDungeon(instance);
    }

    void finishExpiredReconnect(UUID id) {
        if (recoveryManager == null || !running() || recoveringPlayers.containsKey(id)) return;
        var player = org.bukkit.Bukkit.getPlayer(id);
        if (player != null && player.isOnline() && instances.getInstance(player) == null)
            recoveryManager.runIfMarked(player);
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
        notifyPlayerDeath(instance, player, deaths);
        return true;
    }

    /**
     * 副本内玩家死亡的统一通报：EventBus + EVENT 任务 + 全队覆灭判定。
     * 致命拦截路径（旁观化）与真死兜底路径（PlayerDeathEvent）共用，
     * 保证两条路径语义一致。
     */
    public void notifyPlayerDeath(AbyssInstance instance, Player player, int deaths) {
        runResults.member(instance, player.getUniqueId(), AbyssRunResultAccess.MemberState.PRESENT, null, deaths);
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
        if (lines == null || lines.isEmpty() || (instance.isFinished() && !context.allowsFinishedInstance())) {
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
            boolean settlement = "complete".equals(kind) || "fail".equals(kind) || "exit".equals(kind);
            runScript(instance, lines, settlement ? ScriptContext.settlement(instance, trigger)
                : ScriptContext.of(instance, trigger));
        }
    }

    /** 自定义脚本段（run_script 动作）。 */
    public void runCustomScript(AbyssInstance instance, String name, @Nullable Player trigger) {
        runCustomScript(instance, name, trigger, false);
    }
    public void runCustomScript(AbyssInstance instance, String name, @Nullable Player trigger, boolean settlement) {
        List<String> lines = instance.getTemplate().scripts().customs().get(name.toLowerCase());
        if (lines != null && !lines.isEmpty()) {
            runScript(instance, lines, settlement ? ScriptContext.settlement(instance, trigger)
                : ScriptContext.of(instance, trigger));
        }
    }
    public void runCustomScript(AbyssInstance instance,String name,ScriptContext parent) {
        List<String> lines=instance.getTemplate().scripts().customs().get(name.toLowerCase(java.util.Locale.ROOT));
        if(lines!=null && !lines.isEmpty())runScript(instance,lines,parent.child());
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
            throw new IllegalArgumentException("give_item 构建失败: " + def);
        }
        if(!dispatchItems(player,List.of(item),"give_item:"+def))throw new IllegalStateException("物品奖励尚未确认保存");
    }

    public void takeDefinedItem(Player player, String def) {
        if(!rewards.takeItem(player, def))throw new IllegalStateException("所需物品不足，后续动作已取消");
    }

    public void giveMoney(Player player, double amount) {
        if(!rewards.giveMoney(player, amount, "money"))throw new IllegalStateException("货币奖励尚未确认保存");
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
        /** 副本商店 UI（shops.yml；实例变量结算）。 */
        void openShop(Player player,
                      xuanmo.arcartxsuite.abyss.template.ShopConfig.Shop shop,
                      AbyssInstance instance);
        /** UI 包处理；返回 true 表示已消费。 */
        boolean handlePacket(Player player, String packetId, List<String> data);
    }

    public void openChest(Player player, String chestName, AbyssInstance instance) {
        var chest = instance.getTemplate().rewards().chestRewards().get(chestName);
        if (chest == null) {
            logger.warning("[abyss] 宝箱不存在: " + chestName);
            return;
        }
        if (instance.isTriggered(chestClaimKey(chestName, player.getUniqueId()))) {
            player.sendMessage(msg("chest.claimed"));
            return;
        }
        chestUiHandler.open(player, chest, instance);
    }

    /**
     * 宝箱"已领取"标记键（实例触发标记，随实例销毁自动失效）。
     * 每玩家每箱一次：UI 领取/兜底发放/离场自动发放在各自发放点打标。
     */
    public static String chestClaimKey(String chestName, UUID playerId) {
        return "chest_claimed:" + chestName + ":" + playerId;
    }

    /** 打开副本商店（shops.yml；open_shop_ui 动作入口）。 */
    public void openShop(Player player, String shopId, AbyssInstance instance) {
        var shop = instance.getTemplate().shops().shop(shopId);
        if (shop == null) {
            logger.warning("[abyss] 商店不存在: " + shopId);
            return;
        }
        UiServiceFacade ui = uiFacade;
        if (ui != null) ui.openShop(player, shop, instance);
    }

    /** 统一物品发放（宝箱 UI/脚本/奖励链共用入口）。 */
    public boolean dispatchItems(Player player, List<ItemStack> items, String sourceDetail) {
        return dispatchItemsConfirmed(player, items, sourceDetail + ":" + UUID.randomUUID());
    }

    public boolean dispatchItemsConfirmed(Player player, List<ItemStack> items, String requestKey) {
        if (items == null || items.isEmpty()) return true;
        return rewards.queueItems(player, items, requestKey);
    }

    /** 默认宝箱：直接 roll 发放（UI 未接入时兜底）。 */
    private void defaultChestHandler(Player player,
                                     xuanmo.arcartxsuite.abyss.template.RewardConfig.ChestReward chest,
                                     AbyssInstance instance) {
        List<ItemStack> items = rewards.rollChestOptions(chest, Math.min(9, chest.rows() * 9));
        if (!items.isEmpty() && dispatchItemsConfirmed(player, items,
                "chest:" + instance.getInstanceId() + ":" + player.getUniqueId() + ":" + chest.name())) {
            instance.markTriggeredOnce(chestClaimKey(chest.name(), player.getUniqueId()));
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
        if(transferringInstances.contains(instance.getInstanceId())) {scheduleDelayedRaw(()->destroyInstance(instance,releasePlayers),1L);return;}
        var pendingEntry = admission.operation(instance);
        if (releasePlayers && !instance.admitted() && !instance.isFinished() && pendingEntry != null && instance.hasEnteredPlayers()) {
            rollbackAdmission(instance, pendingEntry, null, null, stopping ? "SERVICE_STOPPING" : "ENTRY_ABORTED");
            return;
        }
        runResults.finish(instance, AbyssRunResultAccess.Outcome.CANCELLED, stopping ? "SERVICE_STOPPING" : "INSTANCE_DESTROYED");
        SettlementSession settlement=settlements.remove(instance.getInstanceId());
        if(settlement!=null && settlement.timer!=null)settlement.timer.cancel();
        designOwners.remove(instance.getTemplate().id(), instance.getInstanceId());
        if (releasePlayers) {
            boolean returned = true;
            for (Player p : instance.getPlayers()) {
                try { returned &= playerExit(instance, p, LeaveReason.END); }
                catch (RuntimeException failure) { returned = false; }
            }
            if (!returned && instance.getOnlinePlayerCount() > 0) {
                var pending = admission.operation(instance);
                if (pending != null && pending.phase != Phase.STARTED) {
                    pending.phase = Phase.REVIEW_REQUIRED;
                    pending.reason = "RETURN_PENDING";
                }
                instance.transitionTo(AbyssStatus.PREPARING, AbyssStatus.CANCELLED);
                return;
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
        if (stageManager != null) stageManager.clear(instance);
        if (procgenManager != null) procgenManager.cleanup(instance);
        if (guideService != null) guideService.cleanup(instance);
        if (buffManager != null) buffManager.cleanup(instance);
        if (designManager != null) designManager.closeInstance(instance);
        instanceTasks.cleanup(instance);
        npcs.cleanup(instance);
        worldIndex.remove(instance.getWorld() != null ? instance.getWorld().getName() : "");
        worldPool.releaseWorld(instance.getInstanceId());
        instance.clearRuntime();
        instances.destroyInstance(instance);
        var operation = admission.operation(instance);
        if (operation != null && (operation.phase == Phase.PREPARING || operation.phase == Phase.TELEPORTING)) {
            operation.phase = instance.admitted() ? Phase.REVIEW_REQUIRED : Phase.REJECTED;
            if (operation.reason == null) operation.reason = "ENTRY_ABORTED";
        }
        admission.changed(operation);
        admission.detached(instance);
        runIntegration.detached(instance.getInstanceId());
        reconnects.detach(instance.getInstanceId());
    }

    void cancelAdmission(UUID runId) {
        var instance = instances.getInstance(runId);
        if (instance == null || instance.admitted() || transferringInstances.contains(runId)) return;
        var op = admission.operation(instance);
        if (op == null || op.phase != Phase.PREPARING) return;
        op.phase = Phase.CANCELLED;
        admission.changed(op);
        instance.transitionTo(AbyssStatus.PREPARING, AbyssStatus.CANCELLED);
        destroyInstance(instance, true);
    }

    private void rollbackAdmission(AbyssInstance instance, AdmissionAccess.Operation op, Player failed,
                                   RequirementChecker.Charge charge) {
        rollbackAdmission(instance, op, failed, charge, "TELEPORT_FAILED");
    }

    private void rollbackAdmission(AbyssInstance instance, AdmissionAccess.Operation op, @Nullable Player failed,
                                   @Nullable RequirementChecker.Charge charge, String reason) {
        if (instance.admitted() || !instance.transitionTo(AbyssStatus.PREPARING, AbyssStatus.CANCELLED)) {
            if (charge != null) {
                try { charge.rollback(); }
                catch (RuntimeException failure) {
                    if (op != null) { op.costUncertain = true; op.phase = Phase.REVIEW_REQUIRED; admission.changed(op); }
                }
            }
            return;
        }
        boolean review = op != null && op.costUncertain;
        try { if (charge != null) charge.rollback(); }
        catch (RuntimeException error) {
            review = true;
            if (op != null) op.costUncertain = true;
            logger.warning("[abyss] 入场补偿待核对: " + instance.getInstanceId() + " " + error.getMessage());
        }
        if (op != null && failed != null && !"RETURN_PENDING".equals(op.outcomes.get(failed.getUniqueId())))
            op.outcomes.put(failed.getUniqueId(), "TELEPORT_FAILED");
        for (Player player : instance.getPlayers()) {
            UUID id = player.getUniqueId();
            boolean returned = false;
            try { returned = playerExit(instance, player, LeaveReason.END); }
            catch (RuntimeException failure) { logger.warning("[abyss] 入场回退异常: " + id); }
            if (!returned) review = true;
            if (op != null && (!returned || "ARRIVED".equals(op.outcomes.get(id))))
                op.outcomes.put(id, returned ? "RETURNED" : "RETURN_PENDING");
        }
        if (op != null) {
            if (charge != null) op.nativeCosts.putAll(charge.memberCosts());
            op.outcomes.replaceAll((id, state) -> state.equals("ARRIVED") ? "RETURN_PENDING"
                : state.equals("PENDING") ? "NOT_TRANSFERRED" : state);
            review |= op.outcomes.containsValue("RETURN_PENDING");
            op.phase = review ? Phase.REVIEW_REQUIRED : Phase.COMPENSATED;
            op.reason = reason;
            admission.changed(op);
        }
        if (instance.getOnlinePlayerCount() == 0) destroyInstance(instance, false);
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
        instance.registerTask(scheduler.scheduleOnce(plugin, r, delayTicks));
    }

    /** 外部管理器跑脚本行（procgen 房间脚本/阶段机共享入口）。 */
    public void runScriptPublic(AbyssInstance instance, List<String> lines,
                                @Nullable Player trigger) {
        if (!lines.isEmpty()) {
            runScript(instance, lines,
                ScriptContext.withVars(instance, trigger, Map.of()));
        }
    }

    /** 非实例绑定的裸延迟调度（跨服握手超时等用）。 */
    public void scheduleDelayedRaw(Runnable r, long delayTicks) {
        if(stopping)return;
        ScheduledTask[] task=new ScheduledTask[1];
        task[0]=scheduler.scheduleOnce(plugin,()->{
            if(task[0]!=null)serviceTasks.remove(task[0]);
            if(!stopping)r.run();
        },delayTicks);
        if(task[0]!=null)serviceTasks.add(task[0]);
    }

    /** 主线程执行。 */
    public void runOnMain(Runnable r) {
        if (Bukkit.isPrimaryThread()) {
            r.run();
        } else {
            scheduler.scheduleOnce(plugin,r,0L);
        }
    }
    public boolean running() {return started && !stopping;}

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
    void logRunResultFailure(UUID run, Throwable error) {
        logger.log(java.util.logging.Level.SEVERE, "[abyss] 运行结果写入失败 " + run, error);
    }

    void publishRunResult(AbyssRunResultAccess.ResultCommitted result) {
        try {
            var bus = eventBusProvider.get();
            if (bus != null) bus.publish(AbyssRunResultAccess.RESULT_COMMITTED_TOPIC, null, result);
        } catch (RuntimeException failure) {
            logger.warning("[abyss] 运行结果已提交，通知发布失败 " + result.runId());
        }
    }

    void publishRunNotice(AbyssRunResultAccess.RunNotice notice) {
        try {
            var bus = eventBusProvider.get();
            if (bus != null) bus.publish(AbyssRunResultAccess.RUN_NOTICE_TOPIC, null, notice);
        } catch (RuntimeException failure) {
            logger.warning("[abyss] 运行通知发布失败 " + notice.runId());
        }
    }

    void publishEntryNotice(AbyssRunResultAccess.EntryNotice notice) {
        try {
            var bus = eventBusProvider.get();
            if (bus != null) bus.publish(AbyssRunResultAccess.ENTRY_NOTICE_TOPIC, null, notice);
        } catch (RuntimeException failure) {
            logger.warning("[abyss] 入场状态通知发布失败 " + notice.requestId());
        }
    }

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
        runResults.replay();
        if (!started) return;
        for (AbyssInstance i : new ArrayList<>(instances.getAllInstances())) {
            // 设计实例冻结：不做消失扫描/超时/兜底回收/AFK 踢
            if (isDesignInstance(i)) {
                if (i.getStatus() == AbyssStatus.PREPARING && System.currentTimeMillis() - i.getCreateTime()
                    > Math.max(1000L, config.abyss().worldLoadTimeout())) {
                    logger.warning("[abyss] 设计世界准备超时，回收实例: " + i.getInstanceId());
                    cancelDungeon(i);
                }
                continue;
            }
            try {
                if(i.isFinished()) {
                    if(i.getOnlinePlayerCount()==0)finishAndCleanup(i);
                    else if(!settlements.containsKey(i.getInstanceId()))finishAndCleanup(i);
                    continue;
                }
                // 消失扫描（怪被刷掉兜底）
                // 阶段机慢扫：变量条件等无回调事件的路径兜底
                if (stageManager != null) stageManager.onEvent(i);
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
        if (started && instanceTicker != null) instanceTicker.tick();
    }

    private void tickInstance(AbyssInstance i) {
            try {
                mobGroups.sweepVanished(i);
                if (i.isFinished()) return;
                runIntegration.tick(i);
                obstacles.tickProximity(i);
                if (roomManager != null) roomManager.tick(i);
                if (i.isFinished()) return;
                if (procgenManager != null) procgenManager.tick(i);
                if (i.isFinished()) return;
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

    // ═══ 客户端包 ═══

    /** UI 包路由（AbyssUiService 实现后接管）。 */
    public boolean handleClientPacket(Player player, String packetId, List<String> data) {
        return uiFacade != null && uiFacade.handlePacket(player, packetId, data);
    }

    /** 怪物组回调实现（MonsterGroupManager.GroupCallback）。 */
    void publishObjectiveNotice(xuanmo.arcartxsuite.api.capability.AbyssRunAccess.Snapshot snapshot) {
        try {
            var bus = eventBusProvider.get();
            if (bus != null) bus.publish(xuanmo.arcartxsuite.api.capability.AbyssRunAccess.OBJECTIVES_TOPIC, null, snapshot);
        } catch (RuntimeException failure) {
            logger.warning("[abyss] 目标通知发布失败 " + snapshot.runId());
        }
    }

    private final class GroupCallbackImpl implements MonsterGroupManager.GroupCallback {
        @Override
        public @Nullable String currentStage(AbyssInstance instance) {
            return stageManager == null ? null : stageManager.currentStage(instance);
        }
        @Override
        public void onSpawnFailed(AbyssInstance instance, String groupName) {
            broadcast(instance, "§c怪组 " + groupName + " 生成失败，请联系管理员检查配置。");
            failDungeon(instance, FailReason.SCRIPTED);
        }
        @Override
        public void onGroupStart(AbyssInstance instance, String groupName) {
            instanceTasks.fireEvent(instance, "wave_start",
                Map.of("group", groupName));
        }

        @Override
        public void entityChanged(xuanmo.arcartxsuite.api.capability.AbyssRunAccess.Entity context,
                                  xuanmo.arcartxsuite.api.capability.AbyssRunAccess.EntityChange change) {
            var notice = new xuanmo.arcartxsuite.api.capability.AbyssRunAccess.EntityNotice(
                1, UUID.randomUUID(), context, change, System.currentTimeMillis());
            scheduleDelayedRaw(() -> {
                if (!running()) return;
                try {
                    var bus = eventBusProvider.get();
                    if (bus != null) bus.publish(xuanmo.arcartxsuite.api.capability.AbyssRunAccess.ENTITY_TOPIC, null, notice);
                } catch (RuntimeException failure) {
                    logger.warning("[abyss] 实体通知发布失败 " + context.id());
                }
            }, 1L);
        }

        @Override
        public void onGroupComplete(AbyssInstance instance, String groupName) {
            instanceTasks.fireEvent(instance, "wave_complete",
                Map.of("group", groupName));
            broadcast(instance, msg("dungeon.mob-group-next", "group", groupName));
            // floors：房间组全清 → 解锁判定
            if (roomManager != null) roomManager.onGroupComplete(instance, groupName);
            // procgen：房组波次链
            if (procgenManager != null) procgenManager.onGroupComplete(instance, groupName);
            // 阶段机：怪组清完是阶段通关条件的主要事件源
            if (stageManager != null) stageManager.onEvent(instance);
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
            completeMap(instance);
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
