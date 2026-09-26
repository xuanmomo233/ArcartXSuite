package xuanmo.arcartxsuite.abyss.service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
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
    private JdbcAbyssRepository repository;
    private PlayerDataManager playerData;
    private AbyssAdminCommand adminCommand;

    /** worldName → instanceId（事件反向定位实例）。 */
    private final Map<String, UUID> worldIndex = new ConcurrentHashMap<>();
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

        // 3) 世界池 + 残留世界清理
        worldPool = new AbyssWorldPool(plugin, config.abyss(), logger);
        int orphans = worldPool.cleanupOrphanWorlds();
        if (orphans > 0) {
            logger.info("[abyss] 清理残留副本世界 x" + orphans);
        }

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
        instanceTasks = new InstanceTaskScheduler(plugin, scheduler, logger);
        instanceTasks.setFireCallback((inst, f) -> runScript(inst, f.script(),
            ScriptContext.withVars(inst, null, f.vars())));
        npcs = new DungeonNpcManager(plugin, scheduler, logger);
        npcs.initAdyeshachIfAvailable();
        npcs.setCallbacks((inst, e) -> runScript(inst, e.config().clickScript(),
            ScriptContext.withVars(inst, e.player(), Map.of("npc", e.npcName()))),
            this::locParser);

        // 7) 奖励/体力/统计/排行/冷却/条件
        rewards = new RewardResolver(itemSourceRegistry, itemRewardDispatcher,
            pendingRewardService, currencyManager, logger);
        quests = new QuestManager(scriptConditionEvaluator);
        stamina = new StaminaManager(config.stamina(), playerData);
        stats = new CombatStatisticsService(config.statistics());
        leaderboard = new LeaderboardService(config.leaderboard(), playerData);
        cooldown = new CooldownManager(playerData);
        requirementChecker = new RequirementChecker(scriptConditionEvaluator,
            currencyManager, stamina, cooldown, rewards);

        // 8) 队伍
        partyManager = new PartyManager(config.party(), s -> { });

        // 8.5) ArcartX UI 服务（浏览器/队伍/宝箱/HUD/排行统计 + 客户端包路由）
        AbyssUiService ui = new AbyssUiService(this, packetBridge, itemStackBridge, logger);
        uiFacade = ui;
        chestUiHandler = ui;
        if (ui.available()) {
            logger.info("[abyss] ArcartX UI 已接入（浏览/队伍/宝箱/HUD/排行）");
        } else {
            logger.info("[abyss] ArcartX 不可用，UI 功能禁用（宝箱走直接发放兜底）");
        }

        // 9) 管理命令
        adminCommand = new AbyssAdminCommand(this, logger);

        // 10) 周期看守：超时/无人/消失扫描/障碍触发/区域交互 合一
        watchdogTask = scheduler.scheduleEverySeconds(plugin, this::watchdog,
            config.abyss().instanceCheckInterval(), 10 /* 短周期轮询含触发器 */);
        // 触发器需更细粒度：10s 太粗——另起 1s 轮询做 proximity/region
        proximityTask = scheduler.scheduleEverySeconds(plugin, this::proximityTick, 1, 1);

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
        if (mobGroups != null) mobGroups.shutdown();
        if (npcs != null) npcs.shutdown();
        if (partyManager != null) partyManager.shutdown();
        if (worldPool != null) worldPool.shutdown();
        if (playerData != null) playerData.shutdown();
        if (repository != null) repository.shutdown();
        try {
            storageManager.closeModuleDataSource("abyss");
        } catch (Throwable ignored) { }
        instances = null;
        started = false;
    }

    /** 首次启动时把 jar 内示例地牢导出到 data/abyss/abysses/example。 */
    private void exportExampleDungeon(File dataFolder) {
        File example = new File(dataFolder, "abysses/example");
        if (new File(example, "config.yml").isFile()) return;
        String[] files = {
            "config.yml", "monsters.yml", "scripts.yml", "rewards.yml",
            "interacts.yml", "obstacles.yml", "tasks.yml", "npcs.yml", "quests.yml"
        };
        for (String f : files) {
            try {
                module.exportModuleResource("abysses/example/" + f,
                    new File(example, f), false);
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
    public SchedulerAPI scheduler() { return scheduler; }
    public MessageProvider messages() { return messages; }
    public @Nullable PacketBridgeAPI packetBridge() { return packetBridge; }
    public AttributeBridgeRegistry attributeBridge() { return attributeBridge; }
    public PlaceholderResolverAPI placeholderResolver() { return placeholderResolver; }

    public void setEventBusProvider(Supplier<EventBusCapability> provider) {
        this.eventBusProvider = provider;
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
        return enterDungeon(List.of(player), templateId);
    }

    /**
     * 进本编排（对齐 QueDP DungeonService.enterDungeon）：
     * 校验 → 建实例 → 异步复制世界 → 存档 → 传送 → 全员进图 → init → start。
     */
    public EnterResult enterDungeon(List<Player> players, String templateId) {
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
        // 队伍：多人进本时取首玩家队伍（队伍检查在 requirements 内）
        Party party = players.size() > 1 ? partyManager.partyOf(players.get(0)) : null;
        RequirementChecker.CheckResult check = requirementChecker.check(template, players, party);
        if (!check.ok()) {
            return EnterResult.fail(msg("error.requirements-failed", "reason", check.reason()));
        }

        AbyssInstance instance = instances.createInstance(template, party);
        for (Player p : players) {
            instances.playerEnterInstance(p, instance);
        }
        broadcast(instance, msg("dungeon.preparing"));
        publishEvent("axs.abyss.enter", players.get(0), Map.of(
            "dungeon", template.id(), "instance", instance.getInstanceId().toString(),
            "players", players.size()));

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

    private void prepare(AbyssInstance instance) {
        if (instance.getStatus() != AbyssStatus.PREPARING) return;
        // init 脚本
        runLifecycleScript(instance, "init", null);
        // 40 tick 后 start（QueDP 同款等待玩家稳定）
        scheduleOnce(instance, () -> startInstance(instance), 40L);
    }

    private void startInstance(AbyssInstance instance) {
        if (!instance.transitionTo(AbyssStatus.PREPARING, AbyssStatus.ACTIVE)) return;
        AbyssTemplate t = instance.getTemplate();
        // 怪物组（AUTO_START/DELAYED 自动调度）
        mobGroups.initInstance(instance);
        // 障碍物 auto_create
        obstacles.initInstance(instance);
        // 交互点注册
        interacts.initInstance(instance);
        // 定时任务
        instanceTasks.initInstance(instance);
        // NPC
        npcs.initInstance(instance);
        // start 脚本
        runLifecycleScript(instance, "start", null);
        // 副本 HUD
        if (uiFacade instanceof AbyssUiService ui) {
            for (Player p : instance.getPlayers()) {
                try { ui.openHud(p, instance); } catch (Throwable ignored) { }
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
        broadcast(instance, msg("dungeon.failed", "reason", reason.name()));
        publishEvent("axs.abyss.fail", null, Map.of(
            "dungeon", t.id(), "instance", instance.getInstanceId().toString(),
            "reason", reason.name()));
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

        if (saved != null && player.getGameMode() != saved) {
            player.setGameMode(saved);
        } else {
            instance.restorePlayerGameMode(player);
        }
        if (back != null && player.isOnline()) {
            try { player.teleport(back); } catch (Throwable ignored) { }
        }
        clearBossBars(player);
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
        // 退出后实例无人 → 倒计时清理（30s 宽限等人重连）
        if (instance.getOnlinePlayerCount() == 0 && !instance.isFinished()) {
            scheduleOnce(instance, () -> {
                if (instance.getOnlinePlayerCount() == 0) {
                    cancelDungeon(instance);
                }
            }, 600L);
        }
    }

    /** 玩家断线（监听器调用）：存待恢复位置+从实例移除。 */
    public void handleQuit(Player player) {
        AbyssInstance instance = instances.getInstance(player);
        if (instance == null) return;
        Location back = instance.getOriginalLocation(player.getUniqueId());
        if (back != null) {
            playerData.savePendingLocation(player.getUniqueId(), back);
        }
        instance.removePlayer(player.getUniqueId());
        if (uiFacade instanceof AbyssUiService ui) {
            ui.closeHud(player);
            ui.flushPendingChest(player);
        }
        // 不触发退出消息；30s 无人清理由 watchdog/playerExit 路径处理
        if (instance.getOnlinePlayerCount() == 0 && !instance.isFinished()) {
            scheduleOnce(instance, () -> {
                if (instance.getOnlinePlayerCount() == 0) cancelDungeon(instance);
            }, 600L);
        }
        partyManager.onQuit(player);
    }

    /** 玩家进服（监听器调用）：恢复位置 + 预载数据。 */
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

    /** 生命周期脚本（init/start/complete/fail/player_death/all_death/exit）。 */
    public void runLifecycleScript(AbyssInstance instance, String kind, @Nullable Player trigger) {
        var scripts = instance.getTemplate().scripts();
        List<String> lines = switch (kind) {
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

    /** 出生点（模板 spawn 解析到实例世界）。 */
    public @Nullable Location spawnLocation(AbyssInstance instance) {
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
        obstacles.cleanup(instance);
        interacts.cleanup(instance);
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
        for (int i = 0; i + 1 < kv.length; i += 2) {
            s = s.replace("{" + kv[i] + "}", kv[i + 1]);
        }
        return s;
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
            try {
                obstacles.tickProximity(i);
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
        }

        @Override
        public void onMobDeath(AbyssInstance instance, UUID entityUuid, @Nullable Player killer) {
            if (killer != null) {
                instance.recordKill(killer.getUniqueId());
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
}
