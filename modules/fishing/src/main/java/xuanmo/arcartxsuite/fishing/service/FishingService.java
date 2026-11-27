package xuanmo.arcartxsuite.fishing.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.api.BuildEdition;
import xuanmo.arcartxsuite.api.scheduler.ModuleCallback;
import xuanmo.arcartxsuite.api.scheduler.ModuleExecutor;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.api.capability.MapNavigable;
import xuanmo.arcartxsuite.api.capability.MailDispatchable;
import xuanmo.arcartxsuite.api.capability.mail.MailPresetRequest;
import xuanmo.arcartxsuite.api.capability.TitleGrantable;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.currency.CurrencyDefinition;
import xuanmo.arcartxsuite.api.crossserver.CrossServerAPI;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannel;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannelConfig;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.api.item.PendingRewardService;
import xuanmo.arcartxsuite.api.item.PendingRewardService.PendingReward;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.api.util.ItemSerializer;
import xuanmo.arcartxsuite.api.script.XpCurve;
import xuanmo.arcartxsuite.fishing.config.FishingModuleConfiguration;
import xuanmo.arcartxsuite.fishing.minigame.FishingMinigame;
import xuanmo.arcartxsuite.fishing.minigame.FishingFight;
import xuanmo.arcartxsuite.fishing.minigame.FishingSession;
import xuanmo.arcartxsuite.fishing.model.BaitDefinition;
import xuanmo.arcartxsuite.fishing.model.BaitQueueEntry;
import xuanmo.arcartxsuite.fishing.model.FishCollectionEntry;
import xuanmo.arcartxsuite.fishing.model.FishDefinition;
import xuanmo.arcartxsuite.fishing.model.FishRarity;
import xuanmo.arcartxsuite.fishing.model.FishingCatchRecord;
import xuanmo.arcartxsuite.fishing.model.FishingPermanentData;
import xuanmo.arcartxsuite.fishing.model.FishingSeasonData;
import xuanmo.arcartxsuite.fishing.model.RodDefinition;
import xuanmo.arcartxsuite.fishing.model.TreasureDefinition;
import xuanmo.arcartxsuite.fishing.model.WaterArea;
import xuanmo.arcartxsuite.api.icon.IconDefinition;
import xuanmo.arcartxsuite.api.icon.IconResolver;
import xuanmo.arcartxsuite.fishing.storage.FishingRepository;
import xuanmo.arcartxsuite.fishing.storage.FishingRepository.BaitEdit;
import xuanmo.arcartxsuite.fishing.storage.FishingRepository.BaitDeposit;
import xuanmo.arcartxsuite.fishing.storage.FishingRepository.BiteProfile;
import xuanmo.arcartxsuite.fishing.storage.FishingRepository.ConditionalReward;
import xuanmo.arcartxsuite.fishing.talent.TalentBonuses;
import xuanmo.arcartxsuite.fishing.talent.TalentEngine;
import xuanmo.arcartxsuite.fishing.talent.TalentNode;
import xuanmo.arcartxsuite.fishing.fatigue.FatigueEngine;
import xuanmo.arcartxsuite.fishing.reward.RewardDispatcher;
import xuanmo.arcartxsuite.fishing.reward.SeasonRewardDefinition;
import xuanmo.arcartxsuite.fishing.settlement.SettlementEngine;
import xuanmo.arcartxsuite.fishing.task.TaskEngine;
import xuanmo.arcartxsuite.fishing.task.PlayerTaskInstance;
import xuanmo.arcartxsuite.api.security.ActionTokenGuard;

public final class FishingService {

    /** 最近捕获记录条目，仅用于主界面展示。 */
    public record RecentCatch(
        @NotNull String fishId,
        @NotNull String fishName,
        @NotNull String rarity,
        int size,
        boolean perfect,
        long timestamp
    ) {}

    private static final int MAX_RECENT_CATCHES = 10;
    private static final long CAST_PROFILE_TTL = TimeUnit.MINUTES.toNanos(2);
    private static final int MAX_RECENT_CATCH_PLAYERS = 512;

    private final JavaPlugin plugin;
    private final FishingModuleConfiguration configuration;
    private FishingModuleConfiguration.WaterSettings waters;
    private final FishingRepository repository;
    private final FishingPlaceholderStats placeholderStats;
    private final FishingUiQueries uiQueries;
    private final Set<UUID> pendingTalents = new HashSet<>();
    private final Set<UUID> pendingBaitEdits = new HashSet<>();
    private FishingBaitDeposits baitDeposits;
    private final ModuleExecutor playerWrites = new ModuleExecutor(0, 1, 30, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(64), work -> {
            var thread = new Thread(work, "AXS-Fishing-PlayerWrites");
            thread.setDaemon(true);
            return thread;
        });
    private final PacketBridgeAPI packetBridge;
    private final IconResolver iconResolver;
    private final Logger logger;
    private final String minigameUiId;
    private final Map<UUID, FishingMinigame> activeMinigames = new HashMap<>();
    private final Map<UUID, CastProfile> castProfiles = new LinkedHashMap<>();
    private final ModuleExecutor biteWork = new ModuleExecutor(2, 2, 30, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(64), work -> {
            var thread = new Thread(work, "AXS-Fishing-Bite");
            thread.setDaemon(true);
            return thread;
        });
    private final Set<UUID> settlingCaptures = new HashSet<>();
    private FishingCatchWork catchWork;
    private FishingSales fishSales;
    private BukkitTask catchDeliverySweep;
    private Supplier<MapNavigable> mapProvider = () -> null;
    private final FishSchoolTracker schools = new FishSchoolTracker(() -> mapProvider.get());
    private EventBusCapability schoolEventBus;
    private String mapReadySubscription;
    private final Map<UUID, Long> fishingCooldowns = new HashMap<>(); // 玩家UUID → 冷却到期时间戳(System.currentTimeMillis)
    private final Map<UUID, CatchHistory> recentCatches = Collections.synchronizedMap(
        new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<UUID, CatchHistory> eldest) {
                return size() > MAX_RECENT_CATCH_PLAYERS;
            }
        });
    private volatile boolean stopping;
    private BukkitTask cooldownTask;
    private boolean minigameCloseRegistered;
    private final ModuleCallback callbacks = new ModuleCallback(() -> {});
    private final CompletableFuture<Void> stopped = new CompletableFuture<>();
    private final ModuleExecutor catchInbox = new ModuleExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(128),work->{
        var thread=new Thread(work,"AXS-Fishing-CatchInbox");thread.setDaemon(true);return thread;
    });
    private final TalentEngine talentEngine;
    private final @Nullable FatigueEngine fatigueEngine;
    private final @Nullable CrossServerAPI crossServer;
    private @Nullable CrossServerChannel crossServerChannel;
    private final @Nullable xuanmo.arcartxsuite.api.script.AriaBridge ariaBridge;

    /** 调试日志：仅当 configuration.debug() 为 true 时输出。 */
    public void debug(@NotNull String message) {
        if (configuration.debug()) {
            logger.info(message);
        }
    }
    private final List<SeasonRewardDefinition> seasonRewards;
    private final RewardDispatcher rewardDispatcher;
    private final SettlementEngine settlementEngine;
    private final TaskEngine taskEngine;
    private final ActionTokenGuard tokenGuard = new ActionTokenGuard();
    private MessageProvider messageProvider;

    // 跨模块能力提供者（懒加载，避免循环依赖）
    private Supplier<EventBusCapability> eventBusProvider;
    private Supplier<CurrencyBridgeAPI> currencyProvider;
    private Supplier<TitleGrantable> titleProvider;
    private Supplier<MailDispatchable> mailProvider;
    private xuanmo.arcartxsuite.api.item.ItemMatcherAPI itemMatcher;

    private static final String PDC_FISH_ID = "axs_fishing_fish_id";
    private static final String PDC_FISH_SIZE = "axs_fishing_fish_size";
    private static final String PDC_IS_PERFECT = "axs_fishing_is_perfect";
    private static final String PDC_BAIT_ID = "axs_fishing_bait_id";
    private static final String PDC_ROD_ID = "axs_fishing_rod_id";

    public FishingService(@NotNull JavaPlugin plugin, @NotNull FishingModuleConfiguration configuration,
                          @NotNull FishingRepository repository, @Nullable PacketBridgeAPI packetBridge,
                          @NotNull IconResolver iconResolver,
                          @NotNull Logger logger, @NotNull String minigameUiId,
                          @NotNull TalentEngine talentEngine,
                          @Nullable FatigueEngine fatigueEngine,
                          @NotNull List<SeasonRewardDefinition> seasonRewards,
                          @NotNull RewardDispatcher rewardDispatcher,
                          @NotNull SettlementEngine settlementEngine,
                          @NotNull TaskEngine taskEngine,
                          @Nullable Supplier<MailDispatchable> mailProvider,
                          @Nullable CrossServerAPI crossServer,
                          @Nullable xuanmo.arcartxsuite.api.script.AriaBridge ariaBridge) {
        this.plugin = plugin;
        biteWork.allowCoreThreadTimeOut(true);
        catchInbox.allowCoreThreadTimeOut(true);
        this.configuration = configuration;
        this.waters = new FishingModuleConfiguration.WaterSettings(configuration.specifiedWaters(), configuration.defaultWater());
        this.repository = repository;
        this.placeholderStats = new FishingPlaceholderStats(repository, configuration, fatigueEngine, logger);
        this.uiQueries = new FishingUiQueries(plugin, packetBridge, logger);
        this.packetBridge = packetBridge;
        this.iconResolver = iconResolver;
        this.logger = logger;
        this.minigameUiId = minigameUiId;
        this.talentEngine = talentEngine;
        this.fatigueEngine = fatigueEngine;
        this.seasonRewards = seasonRewards;
        this.rewardDispatcher = rewardDispatcher;
        this.settlementEngine = settlementEngine;
        this.taskEngine = taskEngine;
        this.mailProvider = mailProvider;
        this.crossServer = crossServer;
        this.ariaBridge = ariaBridge;
        // 将天赋系统的 rest_boost 查询关联到疲惫引擎
        if (fatigueEngine != null) {
            fatigueEngine.setRestBoostProvider(data -> talentEngine.computeBonuses(data).getOrDefault("rest_boost", 0.0));
        }
    }

    public void setMessageProvider(@Nullable MessageProvider messageProvider) {
        this.messageProvider = messageProvider;
    }

    /** 获取宿主插件实例（供调度器使用）。 */
    public @NotNull JavaPlugin getPlugin() {
        return plugin;
    }

    /** 获取模块专用 logger（PluginConsoleLogger 实例，自动附带 [fishing] 标签）。 */
    public @NotNull Logger getLogger() {
        return logger;
    }

    public void setEventBusProvider(@Nullable Supplier<EventBusCapability> provider) {
        this.eventBusProvider = provider;
    }


    public void setCurrencyProvider(@Nullable Supplier<CurrencyBridgeAPI> provider) {
        this.currencyProvider = provider;
    }

    public void setTitleProvider(@Nullable Supplier<TitleGrantable> provider) {
        this.titleProvider = provider;
    }

    public void setItemMatcher(@Nullable xuanmo.arcartxsuite.api.item.ItemMatcherAPI itemMatcher) {
        this.itemMatcher = itemMatcher;
    }

    public void configureCatchDelivery(PendingRewardService host, @Nullable StorageDescriptor sharedHostStorage,
                                       java.nio.file.Path dataFolder) {
        catchWork = new FishingCatchWork(repository, host, sharedHostStorage, dataFolder, logger, players -> onMain(() -> {
            for (UUID uuid : players) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) catchWork.flush(player);
            }
        }));
        rewardDispatcher.configureDeliveryOwner(catchWork::owner);
    }

    public void configureFishSales(java.nio.file.Path playerData) {
        if (catchWork == null) return;
        fishSales = new FishingSales(repository, catchWork, logger, playerData);
        catchWork.setSaleRecovery(fishSales);
    }

    public void start() throws Exception {
        if (baitDeposits != null) baitDeposits.start(plugin);
        if (packetBridge != null && !minigameUiId.isBlank()) {
            minigameCloseRegistered = packetBridge.registerUiCloseCallback(minigameUiId, player -> {
                if (stopping || packetBridge.isUiOpen(player, minigameUiId)) return;
                var game = activeMinigames.get(player.getUniqueId());
                if (game != null && !game.isReady()) game.cleanup();
            });
        }
        if (catchWork != null) {
            catchDeliverySweep = plugin.getServer().getScheduler().runTaskTimer(plugin, catchWork::requestSweep, 1L, 100L);
        }
        startCrossServer();
        schools.start(plugin, () -> waters.specifiedWaters());
        schoolEventBus = eventBusProvider == null ? null : eventBusProvider.get();
        if (schoolEventBus != null) {
            mapReadySubscription = schoolEventBus.subscribe("axs.map.ready", event -> {
                if (event.player() != null) onMain(() -> schools.refreshMap(event.player()));
            });
        }
        // M6: 定期清理过期的钓鱼冷却条目（每5分钟清理一次）
        cooldownTask = plugin.getServer().getScheduler().runTaskTimer(plugin, guard(() -> {
            long now = System.currentTimeMillis();
            fishingCooldowns.entrySet().removeIf(e -> e.getValue() < now);
        }), 6000L, 6000L); // 主线程维护的 HashMap 不交给异步定时器遍历。
    }

    public void shutdown() {
        if(stopping)return;
        stopping=true;callbacks.close();
        var failures=new ArrayList<Throwable>();
        var stages=new ArrayList<CompletableFuture<?>>();
        shutdownStep(()->{if(schoolEventBus!=null && mapReadySubscription!=null)schoolEventBus.unsubscribe(mapReadySubscription);},failures);
        shutdownStep(schools::shutdown,failures);
        shutdownStep(()->{if(cooldownTask!=null){cooldownTask.cancel();cooldownTask=null;}},failures);
        if (minigameCloseRegistered) {
            shutdownStep(() -> packetBridge.unregisterUiCloseCallback(minigameUiId), failures);
            minigameCloseRegistered = false;
        }
        for (FishingMinigame minigame : List.copyOf(activeMinigames.values())) {
            shutdownStep(minigame::cleanup,failures);
            stages.add(minigame.stopped().toCompletableFuture());
        }
        activeMinigames.clear();
        castProfiles.clear();
        callbacks.stopped().whenComplete((ignored, error) -> biteWork.shutdown());
        stages.add(biteWork.stopped().toCompletableFuture());
        settlingCaptures.clear();
        if (catchDeliverySweep != null) shutdownStep(catchDeliverySweep::cancel, failures);
        if (catchWork != null) {
            callbacks.stopped().whenComplete((ignored, error) -> catchWork.shutdown());
            stages.add(catchWork.stopped());
        }
        fishingCooldowns.clear();
        recentCatches.clear();
        tokenGuard.clearAll();
        pendingTalents.clear();
        pendingBaitEdits.clear();
        if (baitDeposits != null) shutdownStep(baitDeposits::close, failures);
        playerWrites.shutdown();
        stages.add(playerWrites.stopped().toCompletableFuture());
        shutdownStep(uiQueries::shutdown, failures);
        stages.add(uiQueries.stopped().toCompletableFuture());
        shutdownStep(placeholderStats::shutdown, failures);
        stages.add(placeholderStats.stopped().toCompletableFuture());
        if(fatigueEngine!=null){shutdownStep(fatigueEngine::shutdown,failures);stages.add(fatigueEngine.databaseStopped().toCompletableFuture());}
        if(taskEngine!=null){shutdownStep(taskEngine::shutdown,failures);stages.add(taskEngine.databaseStopped().toCompletableFuture());}
        shutdownStep(this::shutdownCrossServer,failures);
        callbacks.stopped().whenComplete((ignored,failure)->catchInbox.shutdown());
        stages.add(catchInbox.stopped().toCompletableFuture());
        stages.add(callbacks.stopped().toCompletableFuture());
        stages.add(schools.stopped().toCompletableFuture());
        CompletableFuture.allOf(stages.toArray(CompletableFuture[]::new)).whenComplete((ignored,failure)->{
            Throwable result=failure;
            for(Throwable cleanup:failures){if(result==null)result=cleanup;else result.addSuppressed(cleanup);}
            if(result==null)stopped.complete(null);else stopped.completeExceptionally(result);
        });
    }

    private static void shutdownStep(Runnable action,List<Throwable> failures) {
        try{action.run();}catch(RuntimeException | LinkageError failure){failures.add(failure);}
    }
    public boolean isStopping(){return stopping;}

    public void configureBaitDeposits(java.nio.file.Path playerData, java.nio.file.Path dataFolder, String uiId) {
        baitDeposits = new FishingBaitDeposits(repository, playerWrites, playerData, dataFolder, logger, deposit ->
            onMain(() -> {
                Player player = Bukkit.getPlayer(deposit.player());
                if (player == null || !player.isOnline() || !AuthAccess.admitted(deposit.player())) return;
                player.sendMessage("§a已放入 §e" + deposit.amount() + " §a个饵料到槽位 §e" + (deposit.slot() + 1));
                pushBaitsData(player, uiId);
            }));
    }

    public CompletionStage<Void> databaseStopped(){return stopped.copy();}
    public <T> T callApi(Supplier<T> operation,T unavailable){return callbacks.call(operation,unavailable);}
    public Runnable guard(Runnable action){return ()->callbacks.call(()->{if(!stopping)action.run();return null;},null);}
    private void readAsync(Runnable action){if(!stopping)Bukkit.getScheduler().runTaskAsynchronously(plugin,guard(action));}
    public void onMain(Runnable action){if(!stopping)plugin.getServer().getScheduler().runTask(plugin,guard(action));}

    // ─── 跨服同步 ────────────────────────────────────────────────

    private void startCrossServer() {
        if (!BuildEdition.CROSS_SERVER_INCLUDED) return;
        if (crossServer == null) return;
        CrossServerChannelConfig channelConfig = configuration.crossServer();
        if (!channelConfig.enabled()) return;
        crossServerChannel = crossServer.openChannelCompleting(
            "fishing",
            channelConfig,
            delivery -> admitCrossServerMessage(delivery.payload())
        );
        if (crossServerChannel.isActive()) {
            logger.info("钓鱼跨服同步已启用");
        }
    }

    private void shutdownCrossServer() {
        if (BuildEdition.CROSS_SERVER_INCLUDED && crossServerChannel != null) {
            crossServerChannel.close();
            crossServerChannel = null;
        }
    }

    /** 跨服通道是否处于活跃状态。 */
    public boolean crossServerActive() {
        return BuildEdition.CROSS_SERVER_INCLUDED && crossServerChannel != null && crossServerChannel.isActive();
    }

    private void publishCrossServer(String message) {
        if (!BuildEdition.CROSS_SERVER_INCLUDED) return;
        if (crossServerChannel != null && crossServerChannel.isActive() && message != null && !message.isBlank()) {
            crossServerChannel.publish(message);
        }
    }

    private CompletionStage<Boolean> admitCrossServerMessage(String message) {
        if (!BuildEdition.CROSS_SERVER_INCLUDED) return CompletableFuture.completedFuture(false);
        if(stopping)return null;
        if (message == null || !message.startsWith("CATCH2:")) return CompletableFuture.completedFuture(true);
        FishingCatchRecord record;
        try {
            record = FishingCatchRecord.decodeCrossServer(message, System.currentTimeMillis());
        } catch (IllegalArgumentException e) {
            logger.warning("解析跨服捕获记录失败: " + e.getMessage());
            return CompletableFuture.completedFuture(true);
        }
        var receipt=new CompletableFuture<Boolean>();
        try{catchInbox.execute(()->{
            try{
                boolean saved=repository.saveCatchRecordConfirmed(record);
                if (saved) recentCatches.remove(record.playerUuid());
                receipt.complete(saved);
            }catch(RuntimeException | LinkageError failure){receipt.completeExceptionally(failure);}
        });}catch(java.util.concurrent.RejectedExecutionException full){return null;}
        return receipt.copy();
    }

    public boolean isReplaceVanilla() {
        return configuration.fishing().replaceVanilla();
    }

    public boolean isPlayerInMinigame(@NotNull UUID uuid) {
        return activeMinigames.containsKey(uuid);
    }

    public void removeMinigame(@NotNull UUID uuid, @NotNull FishingMinigame minigame) {
        activeMinigames.remove(uuid, minigame);
    }

    public xuanmo.arcartxsuite.fishing.minigame.FishingMinigame getActiveMinigame(@NotNull UUID uuid) {
        return activeMinigames.get(uuid);
    }

    public boolean startMinigame(@NotNull Player player) {
        return startMinigame(player, player.getLocation(), false, null);
    }

    public void setMapProvider(Supplier<MapNavigable> provider) {
        mapProvider = provider;
    }

    public void forgetFishSchools(Player player) {
        schools.forget(player);
    }

    private static final class CastProfile {
        private UUID hook;
        private final long expiresAt = System.nanoTime() + CAST_PROFILE_TTL;
        private BiteProfile loaded;

        private CastProfile(UUID hook) { this.hook = hook; }
    }

    public void prepareCast(Player player, UUID hook) {
        if (stopping) return;
        UUID uuid = player.getUniqueId();
        var existing = castProfiles.get(uuid);
        if (existing != null && existing.loaded == null && System.nanoTime() - existing.expiresAt < 0) {
            existing.hook = hook;
            return;
        }
        if (existing == null && castProfiles.size() >= FishingCatchWork.MAX_CAPTURES) {
            castProfiles.remove(castProfiles.keySet().iterator().next());
        }
        var cast = new CastProfile(hook);
        castProfiles.put(uuid, cast);
        try {
            biteWork.execute(() -> {
                if (stopping) return;
                try {
                    BiteProfile loaded = repository.loadBiteProfile(uuid, configuration.season().seasonId());
                    onMain(() -> {
                        if (castProfiles.get(uuid) == cast) cast.loaded = loaded;
                    });
                } catch (java.sql.SQLException | RuntimeException failure) {
                    logger.fine("钓鱼抛竿预读失败: " + failure.getMessage());
                    onMain(() -> castProfiles.remove(uuid, cast));
                }
            });
        } catch (RejectedExecutionException busy) {
            castProfiles.remove(uuid, cast);
        }
    }

    public void forgetCast(UUID player) {
        castProfiles.remove(player);
    }

    public void forgetCast(UUID player, UUID hook) {
        var cast = castProfiles.get(player);
        if (cast != null && hook.equals(cast.hook)) castProfiles.remove(player);
    }

    public boolean startVanillaBite(Player player, UUID hook, Location location) {
        var cast = castProfiles.get(player.getUniqueId());
        if (cast == null || !hook.equals(cast.hook) || cast.loaded == null || System.nanoTime() - cast.expiresAt >= 0) {
            prepareCast(player, hook);
            return false;
        }
        if (!startMinigame(player, location, true, cast.loaded)) return false;
        castProfiles.remove(player.getUniqueId(), cast);
        return true;
    }

    private boolean startMinigame(Player player, Location hook, boolean hasHook, @Nullable BiteProfile prefetched) {
        if (stopping || catchWork == null || settlingCaptures.contains(player.getUniqueId())
            || activeMinigames.size() + settlingCaptures.size() >= FishingCatchWork.MAX_CAPTURES) return false;
        if (packetBridge == null || !packetBridge.isAvailable()) {
            logger.warning("startMinigame 失败: packetBridge 不可用");
            return false;
        }
        if (isPlayerInMinigame(player.getUniqueId())) {
            debug("startMinigame 跳过: 玩家已在小游戏中 " + player.getName());
            return false;
        }
        // 钓鱼冷却检查
        Long cooldownUntil = fishingCooldowns.get(player.getUniqueId());
        if (cooldownUntil != null && cooldownUntil > System.currentTimeMillis()) {
            debug("startMinigame 跳过: 玩家冷却中 " + player.getName());
            return false;
        }

        // 解析水域
        WaterArea water = resolveWater(hook);
        if (water == null) water = waters.defaultWater();
        // L4: 默认水域仍为 null 时无法继续
        if (water == null) {
            logger.warning("startMinigame 失败: 无法解析水域且默认水域为空");
            return false;
        }

        // 权限检查
        if (water.requirePermission() != null && !water.requirePermission().isBlank() && !player.hasPermission(water.requirePermission())) {
            debug("startMinigame 跳过: 权限不足 " + water.requirePermission());
            return false;
        }

        BiteProfile profile = prefetched;
        if (profile == null) {
            try {
                profile = repository.loadBiteProfile(player.getUniqueId(), configuration.season().seasonId());
            } catch (java.sql.SQLException failure) {
                logger.fine("钓鱼开局读取失败: " + failure.getMessage());
                return false;
            }
        }
        FishingSeasonData seasonData = profile.season();
        int playerLevel = seasonData.level();

        // 水域等级限制
        if (water.minPlayerLevel() > 0 && playerLevel < water.minPlayerLevel()) {
            String msg = getMessage("fishing_water_level_low", String.valueOf(water.minPlayerLevel()));
            if (msg != null && !msg.isEmpty()) player.sendMessage(msg);
            debug("startMinigame 跳过: 水域等级不足 " + player.getName()
                + " level=" + playerLevel + " required=" + water.minPlayerLevel() + " water=" + water.id());
            return false;
        }
        if (water.maxPlayerLevel() > 0 && playerLevel > water.maxPlayerLevel()) {
            String msg = getMessage("fishing_water_level_high", String.valueOf(water.maxPlayerLevel()));
            if (msg != null && !msg.isEmpty()) player.sendMessage(msg);
            debug("startMinigame 跳过: 水域等级超出 " + player.getName()
                + " level=" + playerLevel + " max=" + water.maxPlayerLevel() + " water=" + water.id());
            return false;
        }

        // 检测饵料
        BaitQueueEntry baitHead = profile.baitHead();
        BaitDefinition bait = baitHead == null ? null : findBait(baitHead.baitId());
        if (bait == null) {
            baitHead = null;
            bait = defaultBait();
        }
        String baitId = bait != null ? bait.id() : null;

        // 检测钓竿
        RodDefinition rod = detectRod(player);

        // 鱼竿等级限制校验（maxPlayerLevel = 0 表示无上限）
        if (!rod.id().equals(RodDefinition.DEFAULT.id())) {
            if (playerLevel < rod.minPlayerLevel()) {
                String msg = getMessage("fishing_rod_level_low", String.valueOf(rod.minPlayerLevel()));
                if (msg != null && !msg.isEmpty()) player.sendMessage(msg);
                debug("startMinigame 跳过: 鱼竿等级不足 " + player.getName()
                    + " level=" + playerLevel + " required=" + rod.minPlayerLevel() + " rod=" + rod.id());
                return false;
            }
            if (rod.maxPlayerLevel() > 0 && playerLevel > rod.maxPlayerLevel()) {
                String msg = getMessage("fishing_rod_level_high", String.valueOf(rod.maxPlayerLevel()));
                if (msg != null && !msg.isEmpty()) player.sendMessage(msg);
                debug("startMinigame 跳过: 鱼竿等级超出 " + player.getName()
                    + " level=" + playerLevel + " max=" + rod.maxPlayerLevel() + " rod=" + rod.id());
                return false;
            }
        }

        // 获取该水域的鱼池（直接从水域配置的 fishes 列表获取）
        List<FishDefinition> pool = getFishPool(water);
        if (pool.isEmpty()) {
            // 没有符合条件的鱼，走原版
            logger.warning("startMinigame 失败: 鱼池为空, water=" + water.id() + " fishes=" + water.fishes());
            return false;
        }

        WaterArea.School school = schools.atHook(water, hook);
        FishDefinition fish = selectFishFromPool(player, water, pool, baitId, playerLevel, hook, school);
        if (fish == null) {
            debug("startMinigame 跳过: 无符合条件的鱼, water=" + water.id());
            return false;
        }

        // 疲惫值特殊鱼替换：疲惫值 > 90 时有概率出现特殊鱼
        int fatigue = seasonData.fatigue();
        if (fatigueEngine != null) {
            fish = fatigueEngine.applySpecialFish(fatigue, fish, this::findFish,
                special -> canCatch(special, playerLevel, player::hasPermission));
        }

        debug("startMinigame: player=" + player.getName() + " water=" + water.id()
            + " fish=" + fish.id() + " difficulty=" + fish.difficulty()
            + " bait=" + (baitId != null ? baitId : "none")
            + " fatigue=" + fatigue);

        boolean waitForConfirmation = prefetched != null;
        AuthSnapshot auth = waitForConfirmation ? AuthAccess.snapshot(player.getUniqueId()) : null;
        if (auth != null && (!auth.admitted() || biteWork.getQueue().remainingCapacity() == 0)) return false;
        FishingMinigame minigame = openMinigame(player, fish, fish.randomSize(), water, seasonData, rod, bait,
            school == null ? null : school.id(), hasHook ? water.obstacleAt(hook) : null,
            hasHook ? water.currentAt(hook) : null, false, waitForConfirmation, new FishingSession.Opening(
                fish.personalities().roll(ThreadLocalRandom.current()), hasHook ? fish.hookSet() : null));
        if (minigame == null) return false;
        if (auth != null) return confirmVanillaBite(player, auth, minigame, profile, baitHead != null);
        if (baitHead != null && !repository.replaceBaitSlot(baitHead, new BaitQueueEntry(
                baitHead.playerUuid(), baitHead.slotIndex(), baitHead.baitId(), baitHead.amount() - 1))) {
            minigame.cleanup();
            player.sendMessage("§c饵料扣除失败，请稍后重试。");
            return false;
        }

        sendFishingStart(player, water);
        return true;
    }

    private boolean confirmVanillaBite(Player player, AuthSnapshot auth, FishingMinigame minigame,
                                       BiteProfile profile, boolean consumeBait) {
        try {
            biteWork.execute(() -> {
                if (stopping || !minigame.getSession().isActive()) return;
                boolean confirmed;
                try {
                    confirmed = repository.confirmBite(profile, consumeBait, () -> !stopping && minigame.getSession().isActive());
                } catch (java.sql.SQLException | RuntimeException failure) {
                    logger.fine("咬钩开局存储复核失败: " + failure.getMessage());
                    confirmed = false;
                }
                boolean accepted = confirmed;
                onMain(() -> finishVanillaBite(player, auth, minigame, accepted));
            });
            return true;
        } catch (RejectedExecutionException busy) {
            minigame.cleanup();
            return false;
        }
    }

    private void finishVanillaBite(Player player, AuthSnapshot auth, FishingMinigame minigame, boolean accepted) {
        UUID uuid = auth.playerId();
        if (activeMinigames.get(uuid) != minigame) return;
        var current = AuthAccess.snapshot(uuid);
        if (Bukkit.getPlayer(uuid) != player || !player.isOnline() || !current.admitted()
            || packetBridge == null || !packetBridge.isUiOpen(player, minigameUiId)
            || current.generation() != auth.generation() || !java.util.Objects.equals(auth.connectionId(), current.connectionId())) {
            minigame.cleanup();
        } else if (accepted && minigame.beginFight()) {
            sendFishingStart(player, minigame.getSession().water());
        } else {
            minigame.cleanup();
            player.sendMessage("§c钓鱼状态已变化或未能确认，请重新抛竿。");
        }
    }

    private void sendFishingStart(Player player, WaterArea water) {
        String msg = getMessage("fishing_start");
        if (msg != null && !msg.isEmpty()) player.sendMessage(msg.replace("{water}", water.displayName()));
    }

    public List<String> trialFishIds() {
        return configuration.fishes().stream().map(FishDefinition::id).distinct().sorted().toList();
    }

    public List<String> trialSizes(String fishId) {
        FishDefinition fish = findFish(fishId);
        if (fish == null) return List.of();
        String min = Integer.toString(fish.minSize());
        return fish.minSize() >= fish.maxSize() ? List.of(min) : List.of(min, Integer.toString(fish.maxSize()));
    }

    public List<String> trialCurrentIds() {
        WaterArea water = trialWater();
        return water == null ? List.of() : water.currentZones().stream().map(WaterArea.CurrentZone::id).toList();
    }

    public List<String> trialPersonalityIds(String fishId) {
        FishDefinition fish = findFish(fishId);
        if (fish == null) return List.of();
        var ids = new ArrayList<String>();
        ids.add("normal");
        fish.personalities().variants().forEach(personality -> ids.add(personality.id()));
        return ids;
    }

    private @Nullable WaterArea trialWater() {
        return waters.specifiedWaters().stream().filter(area -> area.id().equals("dragon_lake")).findFirst().orElse(null);
    }

    public enum TrialHook { NONE, SINGLE, LOOP }

    public void startTrial(Player player, String fishId, int size, boolean reeds, @Nullable String currentId,
                           String personalityId, TrialHook trialHook) {
        if(stopping)return;
        if (isPlayerInMinigame(player.getUniqueId())) {
            player.sendMessage("§c请先结束当前钓鱼小游戏。");
            return;
        }
        if (packetBridge == null || !packetBridge.isAvailable()) {
            player.sendMessage("§c钓鱼小游戏界面暂不可用。");
            return;
        }
        FishDefinition fish = findFish(fishId);
        if (fish == null) {
            player.sendMessage("§c找不到鱼种 " + fishId + "。");
            return;
        }
        int maxSize = Math.max(fish.minSize(), fish.maxSize());
        if (size < fish.minSize() || size > maxSize) {
            player.sendMessage("§c" + fish.displayName() + "的体型范围是 " + fish.minSize() + "～" + maxSize + " cm。");
            return;
        }
        FishDefinition.Personality personality = fish.personalities().find(personalityId);
        if (personality == null) {
            player.sendMessage("§c" + fish.displayName() + "没有性格 " + personalityId + "。");
            return;
        }
        WaterArea water = trialWater();
        if (water == null) {
            player.sendMessage("§c请先加载龙泉湖配置 waters/water-dragon_lake.yml。");
            return;
        }
        WaterArea.ObstacleZone obstacle = null;
        if (reeds) {
            obstacle = water.obstacleZones().stream().filter(zone -> zone.id().equals("reeds")).findFirst().orElse(null);
            if (obstacle == null) {
                player.sendMessage("§c龙泉湖缺少 obstacle-zones.reeds 配置。");
                return;
            }
        }
        WaterArea.CurrentZone current = null;
        if (currentId != null) {
            current = water.currentZones().stream().filter(zone -> zone.id().equals(currentId)).findFirst().orElse(null);
            if (current == null) {
                player.sendMessage("§c龙泉湖没有水流区 " + currentId + "。");
                return;
            }
        }

        var seasonData = getSeasonData(player.getUniqueId());
        List<BaitQueueEntry> queue = loadBaitQueue(player);
        BaitDefinition bait = queue.isEmpty() ? null : findBait(queue.get(0).baitId());
        if (bait == null) bait = defaultBait();
        boolean practiceHook = trialHook != TrialHook.NONE;
        FishDefinition.HookSet hookSet = fish.hookSet();
        if (practiceHook && hookSet == null) hookSet = FishDefinition.HookSet.DEFAULT;
        var opening = new FishingSession.Opening(personality, practiceHook ? hookSet : null, trialHook == TrialHook.LOOP);
        if (openMinigame(player, fish, size, water, seasonData, detectRod(player), bait,
                null, obstacle, current, true, false, opening) == null) {
            player.sendMessage("§c无法打开钓鱼小游戏界面。");
            return;
        }
        player.sendMessage("§e试钓 §f" + fish.displayName() + " " + size + " cm · "
            + personality.displayName() + " · "
            + (obstacle == null ? "无障碍" : obstacle.displayName())
            + (current == null ? "" : " · " + current.displayName())
            + (trialHook == TrialHook.LOOP ? " · [F] 连续提竿 [Q] 退出" : (practiceHook ? " · [F] 练提竿" : ""))
            + " §7（本局无奖励）");
    }

    private @Nullable FishingMinigame openMinigame(Player player, FishDefinition fish, int size, WaterArea water,
                                                 FishingSeasonData seasonData, RodDefinition rod, @Nullable BaitDefinition bait,
                                                 @Nullable String schoolId, @Nullable WaterArea.ObstacleZone obstacle,
                                                 @Nullable WaterArea.CurrentZone current, boolean trial, boolean waitForConfirmation,
                                                 FishingSession.Opening opening) {
        TalentBonuses bonuses = new TalentBonuses(talentEngine.computeBonuses(seasonData));
        double fatigueMultiplier = fatigueEngine != null ? fatigueEngine.efficiencyMultiplier(seasonData.fatigue()) : 1.0;
        var settings = configuration.fishing();
        FishingFight.Setup setup = FishingFight.setup(settings, fish, water.difficultyFormula(), seasonData.level(),
            rod, bonuses, fatigueMultiplier, ariaBridge, logger);
        // 对抗试钓复用种子以便比较，连续练习的沉漂时机每轮随机。
        var random = trial && !opening.repeatHook() ? new Random(0x46495348L) : ThreadLocalRandom.current();
        TreasureDefinition offer = fish.activeTreasure()
            ? rollTreasure(configuration, water, bait, rod.treasureChanceBonus(), random) : null;
        FishingSession session = new FishingSession(player.getUniqueId(), fish, size, seasonData.level(),
            setup.durationTicks(), water, setup.difficulty(), bait == null ? null : bait.id(),
            rod.greenBarHeightBonus(), rod.expMultiplier(), rod.treasureChanceBonus(),
            FishingSession.Gear.resolve(rod, bait, fish), offer, schoolId, obstacle, current, trial, opening);
        FishingMinigame minigame = new FishingMinigame(plugin, packetBridge, settings, this, iconResolver, session,
            minigameUiId, bonuses, setup.fatigueMultiplier(), ariaBridge, random);
        if (activeMinigames.putIfAbsent(player.getUniqueId(), minigame) != null) return null;
        boolean opened = waitForConfirmation ? minigame.open() : minigame.start();
        if (opened) return minigame;
        activeMinigames.remove(player.getUniqueId(), minigame);
        return null;
    }

    public void onMinigameComplete(@NotNull FishingSession session, boolean success, boolean perfect) {
        onMinigameComplete(session, success, perfect, false);
    }

    public void onMinigameComplete(@NotNull FishingSession session, boolean success, boolean perfect, boolean treasureClaimed) {
        callApi(()->{completeMinigame(session,success,perfect,treasureClaimed);return null;},null);
    }

    private void completeMinigame(FishingSession session,boolean success,boolean perfect,boolean treasureClaimed) {
        var current = activeMinigames.get(session.playerUuid());
        if (current == null || current.getSession() != session || !session.trySettle()) return;
        if (!activeMinigames.remove(session.playerUuid(), current) || session.trial()) return;
        // 设置钓鱼冷却
        int cooldownTicks = configuration.fishing().fishingCooldownTicks();
        if (cooldownTicks > 0) {
            long cooldownMs = cooldownTicks * 50L; // 1 tick = 50ms
            fishingCooldowns.put(session.playerUuid(), System.currentTimeMillis() + cooldownMs);
        }
        Player player = Bukkit.getPlayer(session.playerUuid());
        if (player == null) return;

        debug("onMinigameComplete: player=" + player.getName() + " success=" + success + " perfect=" + perfect
            + " fish=" + session.fish().id());

        if (success) {
            handleSuccess(player, session, perfect, treasureClaimed);
        } else {
            String msg = getMessage("fishing_failed");
            if (msg != null && !msg.isEmpty()) {
                player.sendMessage(msg);
            }
        }
    }

    private void handleSuccess(@NotNull Player player, @NotNull FishingSession session, boolean perfect, boolean treasureClaimed) {
        UUID uuid = player.getUniqueId();
        var admission = AuthAccess.snapshot(uuid);
        if (catchWork == null || !admission.admitted() || !settlingCaptures.add(uuid)) return;
        FishDefinition fish = session.fish();
        int size = session.caughtSize();
        var record = new FishingCatchRecord(UUID.fromString(session.id()), uuid, fish.id(), fish.displayName(),
            fish.rarity().name().toLowerCase(java.util.Locale.ROOT), size, perfect, System.currentTimeMillis());
        try {
            TreasureDefinition treasure = fish.activeTreasure()
                ? (treasureClaimed ? session.treasureOffer() : null)
                : rollTreasure(session.water(), session.baitId() == null ? null : findBait(session.baitId()),
                    session.rodTreasureChanceBonus());
            var plan = buildCaptureRewards(player, record, fish, treasure);
            var season = configuration.season();
            catchWork.settle(record, season.seasonId(), data -> {
                var bonuses = new TalentBonuses(talentEngine.computeBonuses(data));
                var updated = data.withXpAdded(catchXp(session, perfect, bonuses), season.xpCurve(), season.maxLevel());
                return fatigueEngine == null ? updated : fatigueEngine.addCatchFatigue(updated, bonuses.fatigueReduction());
            }, plan.treasureId() != null, plan.rewards()).whenComplete((result, error) -> {
                if (result != null) recentCatches.remove(uuid);
                onMain(() -> finishCapture(player, admission, session, record, plan, result, error));
            });
        } catch (RuntimeException error) {
            settlingCaptures.remove(uuid);
            reportCaptureFailure(record, error, player);
        }
    }

    private void finishCapture(Player player, AuthSnapshot admission, FishingSession session, FishingCatchRecord record,
                               CapturePlan plan, @Nullable FishingRepository.CatchSettlement result, @Nullable Throwable error) {
        UUID uuid = record.playerUuid();
        settlingCaptures.remove(uuid);
        var current = AuthAccess.snapshot(uuid);
        boolean online = Bukkit.getPlayer(uuid) == player && player.isOnline() && current.admitted()
            && current.generation() == admission.generation()
            && java.util.Objects.equals(current.connectionId(), admission.connectionId());
        if (error != null) {
            reportCaptureFailure(record, error, online ? player : null);
            return;
        }
        if (BuildEdition.CROSS_SERVER_INCLUDED) publishCrossServer(record.encodeCrossServer());
        if (!online) return;

        var fish = session.fish();
        var before = result.season().before();
        var after = result.season().after();
        var bonuses = new TalentBonuses(talentEngine.computeBonuses(before));
        if (fatigueEngine != null && fatigueEngine.rewardMaxFatigue(player, after, bonuses.fatigueReduction())) {
            updateSeason(uuid, FishingSeasonData::withMaxFatigueRewardClaimed);
        }
        emitFishingEvent(player, fish, record.size(), session.water(), session.baitId(), record.perfect(), result.firstCatch(),
            plan.treasureId(), session.schoolId(), after, result.permanent());
        checkMilestones(player, result.permanent(), fish, result.firstCatch());

        String quality = record.perfect() ? "完美" : "普通";
        String displayName = ChatColor.translateAlternateColorCodes('&', fish.displayName());
        String success = getMessage("fishing_success", displayName, String.valueOf(record.size()), quality,
            String.valueOf(catchXp(session, record.perfect(), bonuses)));
        if (success != null) player.sendMessage(success);
        if (result.firstCatch()) {
            String first = getMessage("fishing_first_catch", displayName);
            if (first != null) player.sendMessage(first);
        }
        if (plan.treasureId() != null) {
            String treasure = getMessage("fishing_treasure_found", "??");
            if (treasure != null) player.sendMessage(treasure);
        }
        if (after.level() > before.level()) onLevelUp(player, before.level(), after.level());
    }

    private void reportCaptureFailure(FishingCatchRecord record, Throwable error, @Nullable Player player) {
        logger.log(java.util.logging.Level.WARNING, "钓鱼捕获提交未确认: player=" + record.playerUuid()
            + " event=" + record.eventId(), error);
        if (player != null) player.sendMessage("§c钓鱼捕获提交未确认，请联系管理员核对。");
    }

    private record CapturePlan(List<ConditionalReward> rewards, @Nullable String treasureId) {}

    private CapturePlan buildCaptureRewards(Player player, FishingCatchRecord record, FishDefinition fish,
                                            @Nullable TreasureDefinition treasure) {
        var rewards = new ArrayList<ConditionalReward>();
        UUID uuid = record.playerUuid();
        var item = buildFishItem(player, fish, record.size(), record.perfect());
        rewards.add(new ConditionalReward(pendingCaptureItem(uuid, item, "钓鱼捕获"), false));
        String treasureId = null;
        if (treasure != null) {
            var prize = iconResolver.resolveItemStack(player, treasure.itemRef());
            if (prize != null && !prize.getType().isAir() && prize.getAmount() > 0) {
                rewards.add(new ConditionalReward(pendingCaptureItem(uuid, prize, "钓鱼宝藏"), false));
                treasureId = treasure.id();
            }
        }
        double bonus = record.perfect() ? configuration.fishing().perfectBonusMultiplier() : 1.0;
        if (fish.currencyReward() != null) appendCaptureCurrency(uuid, rewards, fish.currencyReward(), bonus);
        for (var currency : fish.currencyRewards()) appendCaptureCurrency(uuid, rewards, currency, bonus);

        for (var drop : fish.drops()) {
            if (!checkDropCondition(drop.condition(), record.perfect(), record.size(), true)) continue;
            ItemStack stack = iconResolver.resolveItemStack(player, drop.itemRef());
            if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) continue;
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                tagCatchItem(meta, fish.id(), record.size(), record.perfect());
                stack.setItemMeta(meta);
            }
            rewards.add(new ConditionalReward(pendingCaptureItem(uuid, stack, "钓鱼掉落"), firstCatchOnly(drop.condition())));
        }
        for (var command : fish.commandRewards()) {
            if (!checkDropCondition(command.condition(), record.perfect(), record.size(), true)) continue;
            String executed = command.command()
                .replace("{player}", xuanmo.arcartxsuite.api.command.CommandSanitizer.validatePlayerName(player.getName()))
                .replace("{fish}", fish.id())
                .replace("{size}", String.valueOf(record.size()));
            if (executed.isBlank()) continue;
            rewards.add(new ConditionalReward(PendingReward.command(uuid, "fishing", List.of(executed), "钓鱼命令奖励"),
                firstCatchOnly(command.condition())));
        }
        return new CapturePlan(List.copyOf(rewards), treasureId);
    }

    private static boolean firstCatchOnly(String condition) {
        for (String part : condition.split(",")) {
            if (part.trim().equalsIgnoreCase("first_catch")) return true;
        }
        return false;
    }

    private static PendingReward pendingCaptureItem(UUID uuid, ItemStack item, String description) {
        byte[] raw = ItemSerializer.serialize(item);
        if (raw.length == 0 || item.getAmount() <= 0) throw new IllegalArgumentException("无可交付的钓鱼物品");
        return PendingReward.itemSerialized(uuid, "fishing", Base64.getEncoder().encodeToString(raw), description);
    }

    private static void appendCaptureCurrency(UUID uuid, List<ConditionalReward> rewards,
                                               FishDefinition.CurrencyReward currency, double multiplier) {
        double amount = currency.amount() * multiplier;
        if (currency.currencyId() != null && !currency.currencyId().isBlank() && Double.isFinite(amount) && amount > 0) {
            rewards.add(new ConditionalReward(PendingReward.currency(uuid, "fishing", currency.currencyId(), amount, "钓鱼货币奖励"), false));
        }
    }

    private int catchXp(FishingSession session, boolean perfect, TalentBonuses bonuses) {
        double multiplier = session.expMultiplier() * (1.0 + bonuses.xpMultiplierBonus());
        if (perfect) multiplier *= 1.0 + bonuses.perfectXpBonus();
        return Math.max(1, (int) (session.fish().calculateXp(session.caughtSize(), perfect) * multiplier));
    }

    /**
     * 玩家升级回调（服务层副作用）。
     * <p>
     * 发送升级消息、发射 EventBus 事件、发放升级瞬间奖励等。
     *
     * @param player    玩家
     * @param oldLevel  升级前等级
     * @param newLevel  升级后等级
     */
    private void onLevelUp(@NotNull Player player, int oldLevel, int newLevel) {
        // 发送升级消息
        String levelMsg = getMessage("fishing_level_up", String.valueOf(newLevel));
        if (levelMsg != null) {
            player.sendMessage(levelMsg);
        }
        // 发射 EventBus 升级事件
        try {
            xuanmo.arcartxsuite.api.capability.EventBusCapability eventBus =
                eventBusProvider != null ? eventBusProvider.get() : null;
            if (eventBus != null) {
                java.util.Map<String, String> payload = new java.util.HashMap<>();
                payload.put("old_level", String.valueOf(oldLevel));
                payload.put("new_level", String.valueOf(newLevel));
                payload.put("season_id", configuration.season().seasonId());
                eventBus.publish("axs.fishing.level_up", player, payload);
            }
        } catch (Exception e) {
            logger.warning("发射升级事件失败: " + e.getMessage());
        }
    }

    // ─── 水域与池方法 ────────────────────────────────────────────────

    /** 按 ID 查找饵料定义（O(1) 索引）。 */
    private @Nullable BaitDefinition findBait(@NotNull String id) {
        return configuration.findBait(id);
    }

    private @Nullable BaitDefinition defaultBait() {
        return configuration.baits().stream().filter(BaitDefinition::isDefault).findFirst().orElse(null);
    }

    /** 按 ID 查找鱼竿定义（O(1) 索引）。 */
    private @Nullable RodDefinition findRod(@NotNull String id) {
        return configuration.findRod(id);
    }

    /** 按 ID 查找鱼种定义（O(1) 索引）。 */
    private @Nullable FishDefinition findFish(@NotNull String id) {
        return configuration.findFish(id);
    }

    private @Nullable WaterArea resolveWater(@NotNull Location hook) {
        for (WaterArea area : waters.specifiedWaters()) {
            if (area.contains(hook)) {
                return area;
            }
        }
        return null;
    }

    public void reloadWaters(FishingModuleConfiguration.WaterSettings waters) {
        this.waters = waters;
    }

    private @NotNull List<FishDefinition> getFishPool(@NotNull WaterArea water) {
        List<String> ids = water.fishes();
        if (ids == null || ids.isEmpty()) return List.of();
        List<FishDefinition> result = new ArrayList<>();
        for (String id : ids) {
            FishDefinition fish = findFish(id);
            if (fish != null) result.add(fish);
        }
        return result;
    }

    private @Nullable FishDefinition selectFishFromPool(@NotNull Player player,
                                                           @NotNull WaterArea water,
                                                           @NotNull List<FishDefinition> pool,
                                                           @Nullable String baitId,
                                                            int playerLevel, @NotNull Location hook,
                                                            @Nullable WaterArea.School school) {
        List<FishDefinition> candidates = selectCandidates(pool, playerLevel, player::hasPermission,
            getCurrentSeason(), getCurrentWeather(player.getWorld()), getCurrentTimeString(player.getWorld()),
            getBiome(hook).toLowerCase(java.util.Locale.ROOT));
        if (candidates.isEmpty()) return null;

        double totalWeight = 0;
        Map<FishDefinition, Double> weights = new java.util.LinkedHashMap<>();
        BaitDefinition bait = baitId != null ? findBait(baitId) : null;
        for (FishDefinition fish : candidates) {
            double weight = fishWeight(fish, water, bait);
            if (school != null) weight *= school.weights().getOrDefault(fish.id(), 1.0);
            weights.put(fish, weight);
            totalWeight += weight;
        }

        // C4: totalWeight=0 时回退到均匀随机选择，防止除零导致恒返回第一个
        if (totalWeight <= 0) {
            return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        }

        double roll = ThreadLocalRandom.current().nextDouble() * totalWeight;
        double cumulative = 0;
        for (Map.Entry<FishDefinition, Double> entry : weights.entrySet()) {
            cumulative += entry.getValue();
            if (roll <= cumulative) {
                return entry.getKey();
            }
        }
        return candidates.get(candidates.size() - 1);
    }

    static double fishWeight(FishDefinition fish, WaterArea water, @Nullable BaitDefinition bait) {
        double weight = fish.rarity().weightMultiplier();
        if (bait == null) return weight;

        weight *= bait.attractModifier(fish.id());
        for (FishDefinition.BaitPreference preference : fish.baitPreferences()) {
            if (preference.baitId().equals(bait.id())) {
                weight *= preference.multiplier();
                break;
            }
        }
        Double waterMultiplier = water.baitMultipliers().get(bait.id());
        return waterMultiplier == null ? weight : weight * waterMultiplier;
    }

    static List<FishDefinition> selectCandidates(List<FishDefinition> pool, int level, Predicate<String> hasPermission,
                                                 String season, String weather, String time, String biome) {
        List<FishDefinition> eligible = pool.stream().filter(fish -> canCatch(fish, level, hasPermission)).toList();
        List<FishDefinition> seasonal = eligible.stream().filter(fish -> fish.seasons().contains(season)).toList();
        List<FishDefinition> matching = seasonal.stream()
            .filter(fish -> fish.weathers().contains(weather))
            .filter(fish -> isInTimeRange(time, fish.timeRanges()))
            .filter(fish -> fish.biomes().isEmpty() || fish.biomes().contains(biome))
            .toList();
        if (!matching.isEmpty()) return matching;
        return seasonal.isEmpty() ? eligible : seasonal;
    }

    static boolean canCatch(FishDefinition fish, int level, Predicate<String> hasPermission) {
        return level >= fish.minPlayerLevel()
            && (fish.maxPlayerLevel() <= 0 || level <= fish.maxPlayerLevel())
            && (fish.requirePermission() == null || hasPermission.test(fish.requirePermission()));
    }

    private static @Nullable TreasureDefinition selectTreasureFromPool(FishingModuleConfiguration configuration,
                                                                        @NotNull WaterArea water,
                                                                        java.util.random.RandomGenerator random) {
        List<String> ids = water.treasures();
        if (ids == null || ids.isEmpty()) return null;
        List<TreasureDefinition> pool = new ArrayList<>();
        for (String id : ids) {
            TreasureDefinition t = configuration.findTreasure(id);
            if (t != null) pool.add(t);
        }
        if (pool.isEmpty()) return null;
        // 加权随机选择：chance 作为权重，归一化后按概率抽取
        double totalWeight = pool.stream().mapToDouble(TreasureDefinition::chance).sum();
        if (totalWeight <= 0) return null;
        double roll = random.nextDouble() * totalWeight;
        double cumulative = 0;
        for (TreasureDefinition t : pool) {
            cumulative += t.chance();
            if (roll < cumulative) return t;
        }
        return pool.get(pool.size() - 1);
    }

    @Nullable
    private String getBaitIdFromRod(@NotNull ItemStack rod) {
        if (!rod.hasItemMeta()) return null;
        ItemMeta meta = rod.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(plugin, PDC_BAIT_ID);
        return pdc.has(key, PersistentDataType.STRING) ? pdc.get(key, PersistentDataType.STRING) : null;
    }

    @Nullable
    private String getRodIdFromRod(@NotNull ItemStack rod) {
        if (!rod.hasItemMeta()) return null;
        ItemMeta meta = rod.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(plugin, PDC_ROD_ID);
        return pdc.has(key, PersistentDataType.STRING) ? pdc.get(key, PersistentDataType.STRING) : null;
    }

    /**
     * 检测玩家手持的鱼竿对应的 RodDefinition。
     * 优先用 ItemMatcher 匹配，未配置 matcher 时回退到 PDC 标签。
     */
    public @NotNull RodDefinition detectRod(@NotNull Player player) {
        ItemStack rod = player.getInventory().getItemInMainHand();
        if (rod.getType() != Material.FISHING_ROD) {
            rod = player.getInventory().getItemInOffHand();
            if (rod.getType() != Material.FISHING_ROD) return RodDefinition.DEFAULT;
        }

        // 优先用 ItemMatcher 匹配
        if (itemMatcher != null) {
            for (RodDefinition def : configuration.rods()) {
                if (def.matcher() != null && !def.matcher().emptyMatcher()
                    && itemMatcher.matches(def.matcher(), rod)) {
                    return def;
                }
            }
        }

        // 回退：PDC 标签识别
        String rodId = getRodIdFromRod(rod);
        if (rodId == null) return RodDefinition.DEFAULT;
        RodDefinition def = findRod(rodId);
        return def != null ? def : RodDefinition.DEFAULT;
    }

    private ItemStack buildFishItem(Player player, FishDefinition fish, int size, boolean perfect) {
        ItemStack stack = iconResolver.resolveItemStack(player, fish.itemRef());
        if (stack == null) stack = new ItemStack(Material.COD);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&',
            "&7[" + fish.rarity().name() + "] &f" + fish.displayName()));
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.translateAlternateColorCodes('&', "&7尺寸: &f" + size + "cm"));
        lore.add(ChatColor.translateAlternateColorCodes('&',
            "&7售价: &6" + fish.calculatePrice(size) + " &7" + getDefaultCurrency()));
        if (perfect) lore.add(ChatColor.translateAlternateColorCodes('&', "&a✦ 完美捕获"));
        meta.setLore(lore);
        tagCatchItem(meta, fish.id(), size, perfect);
        stack.setItemMeta(meta);
        return stack;
    }

    /** PDC 字段供鱼类识别、出售和市场展示使用。 */
    private void tagCatchItem(ItemMeta meta, String fishId, int size, boolean perfect) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(new NamespacedKey(plugin, PDC_FISH_ID), PersistentDataType.STRING, fishId);
        pdc.set(new NamespacedKey(plugin, PDC_FISH_SIZE), PersistentDataType.INTEGER, size);
        pdc.set(new NamespacedKey(plugin, PDC_IS_PERFECT), PersistentDataType.BYTE, (byte) (perfect ? 1 : 0));
    }

    /** 获取货币桥接 API，未配置时返回 null。 */
    private @Nullable CurrencyBridgeAPI currencyApi() {
        return currencyProvider != null ? currencyProvider.get() : null;
    }

    /** 发送 UI init 数据包，packetBridge 不可用时静默跳过。 */
    private void sendUiInitPacket(@NotNull Player player, @NotNull String uiId,
                                  @NotNull java.util.Map<String, Object> payload) {
        if (packetBridge == null || !packetBridge.isAvailable()) return;
        // 每次推送轮换动作令牌：写操作回包必须携带最新 token（防重放/防外部注入）。
        payload.put("action_token", tokenGuard.rotate(player.getUniqueId(), uiId));
        packetBridge.sendPacket(player, uiId, "init", payload);
    }

    /**
     * 校验写操作令牌：要求 UI 已打开、客户端回传 token 与当前签发值一致、且玩家无在途写操作。
     *
     * @param player   玩家
     * @param uiId     动作来源 UI（token 命名空间）
     * @param supplied 客户端回传的 action_token
     * @return {@code true} 表示校验通过且已占用在途槽位（须配对 {@link #finishWriteAction}）
     */
    public boolean beginWriteAction(@NotNull Player player, @NotNull String uiId, @Nullable String supplied) {
        UUID uuid = player.getUniqueId();
        if (pendingBaitEdits.contains(uuid) || !uiQueries.canView(player, uiId) || !AuthAccess.admitted(uuid)
            || !tokenGuard.begin(uuid, uiId, supplied)) return false;
        // Consume the old token now. The replacement is sent when the refreshed view is ready.
        tokenGuard.rotate(uuid, uiId);
        uiQueries.invalidate(uuid, uiId);
        return true;
    }

    /** 释放玩家的在途写操作槽位。 */
    public void finishWriteAction(@NotNull Player player) {
        tokenGuard.finish(player.getUniqueId());
    }

    private @NotNull String getDefaultCurrency() {
        CurrencyBridgeAPI api = currencyApi();
        if (api != null) {
            // 优先找 provider 为 vault 的货币（Vault 注册的默认经济）
            for (CurrencyDefinition def : api.definitions()) {
                if (def.provider() != null && "vault".equalsIgnoreCase(def.provider().trim())) {
                    return def.id();
                }
            }
            // 回退：第一个可用货币
            if (!api.currencyIds().isEmpty()) {
                return api.currencyIds().iterator().next();
            }
        }
        return "money";
    }

    private @Nullable TreasureDefinition rollTreasure(@Nullable WaterArea water, @Nullable BaitDefinition bait, double rodBonus) {
        return rollTreasure(configuration, water, bait, rodBonus, ThreadLocalRandom.current());
    }

    static @Nullable TreasureDefinition rollTreasure(FishingModuleConfiguration configuration, @Nullable WaterArea water,
                                                   @Nullable BaitDefinition bait, double rodBonus,
                                                   java.util.random.RandomGenerator random) {
        if (water == null) return null;
        double chance = configuration.fishing().treasureChance() + rodBonus;
        if (bait != null) chance += bait.treasureChanceBoost();
        return random.nextDouble() < Math.max(0, Math.min(1, chance))
            ? selectTreasureFromPool(configuration, water, random) : null;
    }

    // ─── 饵料队列管理 ────────────────────────────────────────────

    private int baitSlotBonus(FishingSeasonData seasonData) {
        return (int) TalentEngine.getBonus(talentEngine.computeBonuses(seasonData), "bait_slot_bonus");
    }

    /** 加载玩家饵料队列（按 slot_index 升序）。 */
    public @NotNull List<BaitQueueEntry> loadBaitQueue(@NotNull Player player) {
        return repository.loadBaitQueue(player.getUniqueId());
    }

    public void submitBaitDeposit(Player player, String uiId, String token, int invSlot, int queueSlot, int amount) {
        var action = beginBaitEdit(player, uiId, token);
        if (action == null) return;
        try {
            snapshotBaitDeposit(action, invSlot, queueSlot, amount);
        } catch (RuntimeException failure) {
            logger.fine("读取待放入饵料失败: " + failure.getMessage());
            finishBaitEdit(action, "&c饵料暂未受理，请稍后重试。");
        }
    }

    private void snapshotBaitDeposit(BaitUiAction action, int invSlot, int queueSlot, int amount) {
        if (baitDeposits == null || itemMatcher == null) {
            finishBaitEdit(action, "&c饵料放入暂不可用。");
            return;
        }
        if (invSlot < 9 || invSlot > 44 || queueSlot < 0) {
            finishBaitEdit(action, "&c槽位号无效。");
            return;
        }
        int slot = toBukkitBackpackSlot(invSlot);
        ItemStack item = action.player().getInventory().getItem(slot);
        if (item == null || item.getType().isAir()) {
            finishBaitEdit(action, "&c背包槽位为空。");
            return;
        }
        BaitDefinition bait = matchingBait(item);
        if (bait == null) {
            finishBaitEdit(action, "&c该物品不是饵料。");
            return;
        }
        int count = amount <= 0 ? item.getAmount() : Math.min(amount, item.getAmount());
        if (count <= 0) {
            finishBaitEdit(action, "&c数量无效。");
            return;
        }
        ItemStack source = item.clone();
        try {
            playerWrites.execute(() -> prepareBaitDeposit(action, source, slot, queueSlot, bait.id(), count));
        } catch (RejectedExecutionException busy) {
            finishBaitEdit(action, "&e钓鱼数据繁忙，请稍后重试。");
        }
    }

    private @Nullable BaitDefinition matchingBait(ItemStack item) {
        for (var bait : configuration.baits()) {
            if (bait.matcher() != null && !bait.matcher().emptyMatcher() && itemMatcher.matches(bait.matcher(), item)) {
                return bait;
            }
        }
        return null;
    }

    private void prepareBaitDeposit(BaitUiAction action, ItemStack source, int inventorySlot, int slot, String bait, int count) {
        if (stopping) return;
        try {
            BaitEdit edit = repository.loadBaitEdit(action.auth().playerId(), configuration.season().seasonId(), slot, slot);
            if (slot >= 1 + baitSlotBonus(edit.season())) {
                onMain(() -> finishBaitEdit(action, "&c槽位号无效。"));
                return;
            }
            if (edit.first().amount() > 0 && !edit.first().baitId().equals(bait)) {
                onMain(() -> finishBaitEdit(action, "&c目标槽位已有其他饵料，请先取出。"));
                return;
            }
            if ((long) edit.first().amount() + count > Integer.MAX_VALUE) {
                onMain(() -> finishBaitEdit(action, "&c槽位饵料数量已达上限。"));
                return;
            }
            BaitDeposit deposit = baitDeposits.reserve(edit, action.request(), bait, count);
            if (deposit == null) {
                onMain(() -> finishBaitEdit(action, "&e饵料队列已变化，或上次放入正在等待存档。"));
            } else {
                onMain(() -> removeDepositedBait(action, source, inventorySlot, deposit));
            }
        } catch (java.io.IOException | java.sql.SQLException | RuntimeException failure) {
            logger.fine("暂存钓鱼饵料未确认: " + failure.getMessage());
            onMain(() -> finishBaitEdit(action, "&c饵料暂存未确认，请刷新后重试。"));
        }
    }

    private void removeDepositedBait(BaitUiAction action, ItemStack source, int slot, BaitDeposit deposit) {
        PlayerInventory inventory;
        PersistentDataContainer pdc;
        String previous;
        ItemStack remainder;
        var key = FishingBaitDeposits.RECEIPT;
        try {
            if (!currentBaitView(action)) {
                baitDeposits.cancel(deposit);
                finishBaitEdit(action, null);
                return;
            }
            inventory = action.player().getInventory();
            if (!source.equals(inventory.getItem(slot))) {
                baitDeposits.cancel(deposit);
                finishBaitEdit(action, "&c背包槽位已变化，放入未执行。");
                return;
            }
            pdc = action.player().getPersistentDataContainer();
            previous = pdc.get(key, PersistentDataType.STRING);
            remainder = source.clone();
            remainder.setAmount(source.getAmount() - deposit.amount());
        } catch (RuntimeException failure) {
            baitDeposits.cancel(deposit);
            logger.fine("核对待扣饵料失败: " + failure.getMessage());
            finishBaitEdit(action, "&c未能核对背包，放入未执行。");
            return;
        }
        if (!baitDeposits.beginRemoval(deposit)) {
            finishBaitEdit(action, "&c饵料暂存已关闭，放入未执行。");
            return;
        }
        try {
            inventory.setItem(slot, remainder.getAmount() == 0 ? null : remainder);
            pdc.set(key, PersistentDataType.STRING, deposit.request().toString());
        } catch (RuntimeException failure) {
            try {
                inventory.setItem(slot, source);
                if (previous == null) pdc.remove(key);
                else pdc.set(key, PersistentDataType.STRING, previous);
                baitDeposits.cancel(deposit);
            } catch (RuntimeException rollback) {
                failure.addSuppressed(rollback);
            }
            logger.log(java.util.logging.Level.WARNING, "钓鱼饵料扣除未确认: " + deposit.request(), failure);
            finishBaitEdit(action, "&c饵料扣除未确认，请刷新队列。");
            return;
        }
        // The scheduled scan also covers this removal if the immediate request cannot run.
        baitDeposits.requestScan();
        finishBaitEdit(action, "&e已从背包扣除，饵料将在玩家存档确认后进入队列。");
    }

    /**
     * 将 ARIA UI ~Backpack 槽位编号转换为 Bukkit inventory slot index。
     * ARIA rawSlot 9-35 → Bukkit 9-35（主背包 27 格），
     * ARIA rawSlot 36-44 → Bukkit 0-8（快捷栏 9 格）。
     */
    private static int toBukkitBackpackSlot(int rawSlot) {
        if (rawSlot >= 36 && rawSlot <= 44) {
            return rawSlot - 36;
        }
        return rawSlot;
    }

    public void submitBaitSwap(Player player, String uiId, String token, int first, int second) {
        submitBaitEdit(player, uiId, token, first, second, 0, true);
    }

    public void submitBaitWithdrawal(Player player, String uiId, String token, int slot, int amount) {
        submitBaitEdit(player, uiId, token, slot, slot, amount, false);
    }

    private record BaitUiAction(Player player, AuthSnapshot auth, String uiId, UUID request) {}

    private @Nullable BaitUiAction beginBaitEdit(Player player, String uiId, String token) {
        if (!beginWriteAction(player, uiId, token)) return null;
        UUID uuid = player.getUniqueId();
        var action = new BaitUiAction(player, AuthAccess.snapshot(uuid), uiId, UUID.randomUUID());
        pendingBaitEdits.add(uuid);
        return action;
    }

    private void submitBaitEdit(Player player, String uiId, String token, int first, int second,
                                int amount, boolean swap) {
        var action = beginBaitEdit(player, uiId, token);
        if (action == null) return;
        if (first < 0 || second < 0 || swap && first == second) {
            finishBaitEdit(action, swap && first == second ? "&c不能与自身交换。" : "&c槽位号无效。");
            return;
        }
        try {
            playerWrites.execute(() -> prepareBaitEdit(action, first, second, amount, swap));
        } catch (RejectedExecutionException busy) {
            finishBaitEdit(action, "&e钓鱼数据繁忙，请稍后重试。");
        }
    }

    private void prepareBaitEdit(BaitUiAction action, int first, int second, int amount, boolean swap) {
        if (stopping) return;
        try {
            BaitEdit view = repository.loadBaitEdit(action.auth().playerId(), configuration.season().seasonId(), first, second);
            int limit = 1 + baitSlotBonus(view.season());
            if (first >= limit || second >= limit) {
                onMain(() -> finishBaitEdit(action, "&c槽位号无效。"));
                return;
            }
            if (swap) {
                boolean changed = repository.swapBaitEdit(view);
                onMain(() -> finishBaitEdit(action, changed
                    ? "&a已交换槽位 &e" + (first + 1) + " &a和 &e" + (second + 1) + " &a的饵料"
                    : "&c交换失败或队列已变化，请刷新后重试。"));
            } else {
                onMain(() -> prepareBaitWithdrawal(action, view, amount));
            }
        } catch (java.sql.SQLException | RuntimeException error) {
            logger.fine("饵料操作未确认: " + error.getMessage());
            onMain(() -> finishBaitEdit(action, "&c饵料队列未能确认，请刷新状态。"));
        }
    }

    private void prepareBaitWithdrawal(BaitUiAction action, BaitEdit view, int requested) {
        if (!currentBaitView(action)) {
            finishBaitEdit(action, null);
            return;
        }
        var target = view.first();
        if (target.amount() <= 0) {
            finishBaitEdit(action, "&c该槽位为空。");
            return;
        }
        BaitDefinition bait = findBait(target.baitId());
        if (bait == null) {
            finishBaitEdit(action, "&c未知饵料类型。");
            return;
        }
        try {
            Player player = action.player();
            ItemStack item = iconResolver.resolveItemStack(player, bait.itemRef());
            if (item == null || item.getType().isAir() || catchWork == null) {
                finishBaitEdit(action, "&c饵料物品暂不可用。");
                return;
            }
            // An attachment must fit in an empty backpack even when Warehouse and Mail handle overflow.
            int portable = (int) Math.min(Integer.MAX_VALUE,
                (long) item.getMaxStackSize() * player.getInventory().getStorageContents().length);
            int requestedAmount = requested <= 0 ? target.amount() : Math.min(requested, target.amount());
            int count = Math.min(portable, requestedAmount);
            if (count <= 0) {
                finishBaitEdit(action, "&c饵料无法装入玩家背包。");
                return;
            }
            item = item.clone();
            item.setAmount(count);
            var reward = pendingCaptureItem(action.auth().playerId(), item, "钓鱼饵料取回");
            catchWork.withdrawBait(view, count, action.request(), reward).whenComplete((changed, error) -> onMain(() -> {
                if (error != null) {
                    logger.log(java.util.logging.Level.WARNING, "取回饵料未确认: " + action.request(), error);
                    finishBaitEdit(action, "&c取回结果未确认，请刷新队列。");
                } else if (Boolean.TRUE.equals(changed)) {
                    String suffix = count < requestedAmount ? "。剩余饵料仍在队列，可分次取回。" : "。";
                    finishBaitEdit(action, "&a已登记取回 &e" + count + " &a个 &f" + bait.displayName() + suffix);
                } else {
                    finishBaitEdit(action, "&c饵料队列已变化，请刷新后重试。");
                }
            }));
        } catch (RuntimeException failure) {
            logger.fine("准备取回饵料失败: " + failure.getMessage());
            finishBaitEdit(action, "&c饵料暂未受理，请稍后重试。");
        }
    }

    private boolean currentBaitView(BaitUiAction action) {
        if (!uiQueries.canView(action.player(), action.uiId())) return false;
        var current = AuthAccess.snapshot(action.auth().playerId());
        return current.admitted() && current.generation() == action.auth().generation()
            && java.util.Objects.equals(current.connectionId(), action.auth().connectionId());
    }

    private void finishBaitEdit(BaitUiAction action, @Nullable String message) {
        UUID uuid = action.auth().playerId();
        if (!pendingBaitEdits.remove(uuid) || Bukkit.getPlayer(uuid) != action.player()) return;
        finishWriteAction(action.player());
        if (!currentBaitView(action)) return;
        if (message != null) action.player().sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        pushBaitsData(action.player(), action.uiId());
    }

    /**
     * 获取鱼竿上挂载的饵料的耐久加成值。
     */
    public int getRodDurabilityBonus(@NotNull ItemStack rod) {
        String baitId = getBaitIdFromRod(rod);
        if (baitId == null) return 0;
        BaitDefinition bait = findBait(baitId);
        return bait != null ? bait.maxDurabilityBonus() : 0;
    }

    private String getCurrentSeason() {
        int month = java.time.LocalDate.now().getMonthValue();
        return switch (month) {
            case 3, 4, 5 -> "spring";
            case 6, 7, 8 -> "summer";
            case 9, 10, 11 -> "fall";
            default -> "winter";
        };
    }

    private String getBiome(@NotNull Location hook) {
        try {
            return hook.getBlock().getBiome().name();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 检查掉落/命令条件是否满足。
     * 支持条件："" (无条件)、"perfect" (完美捕获)、"size:N" (尺寸≥N)、"first_catch" (首次捕获)。
     * 多条件用逗号分隔（全部满足才通过）。
     */
    private boolean checkDropCondition(@NotNull String condition, boolean perfect, int size, boolean firstCatch) {
        if (condition.isEmpty()) return true;
        for (String cond : condition.split(",")) {
            cond = cond.trim().toLowerCase();
            if (cond.equals("perfect") && !perfect) return false;
            if (cond.equals("first_catch") && !firstCatch) return false;
            // L2: size 条件解析异常保护
            if (cond.startsWith("size:")) {
                try {
                    if (size < Integer.parseInt(cond.substring(5))) return false;
                } catch (NumberFormatException e) {
                    logger.warning("无效的 size 条件: " + cond);
                }
            }
        }
        return true;
    }

    private String getCurrentWeather(@NotNull World world) {
        return world.hasStorm() || world.isThundering() ? "rain" : "clear";
    }

    private String getCurrentTimeString(@NotNull World world) {
        long ticks = world.getTime();
        int hour = (int) ((ticks / 1000 + 6) % 24);
        int minute = (int) ((ticks % 1000) * 60 / 1000);
        return String.format("%02d:%02d", hour, minute);
    }

    private static boolean isInTimeRange(@NotNull String time, @NotNull List<FishDefinition.TimeRange> ranges) {
        for (FishDefinition.TimeRange range : ranges) {
            if (isBetweenTimes(time, range.start(), range.end())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBetweenTimes(@NotNull String current, @NotNull String start, @NotNull String end) {
        int currentMins = parseTimeToMinutes(current);
        int startMins = parseTimeToMinutes(start);
        int endMins = parseTimeToMinutes(end);
        if (endMins < startMins) {
            // 跨越午夜
            return currentMins >= startMins || currentMins <= endMins;
        }
        return currentMins >= startMins && currentMins <= endMins;
    }

    private static int parseTimeToMinutes(@NotNull String time) {
        String[] parts = time.split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
        return hour * 60 + minute;
    }

    private @Nullable String getMessage(@NotNull String key, Object... args) {
        if (messageProvider == null) return null;
        return messageProvider.get(key, args);
    }

    // ─── 统计查询 ────────────────────────────────────────────

    public @NotNull FishingSeasonData getSeasonData(@NotNull UUID uuid) {
        return repository.loadSeasonData(uuid, configuration.season().seasonId());
    }

    public @NotNull FishingPermanentData getPermanentData(@NotNull UUID uuid) {
        return repository.loadPermanentData(uuid);
    }

    public @NotNull XpCurve getXpCurve() {
        return configuration.season().xpCurve();
    }

    public @NotNull List<FishCollectionEntry> getCollection(@NotNull UUID uuid) {
        return repository.loadCollection(uuid);
    }

    public @Nullable FishCollectionEntry getCollectionEntry(@NotNull UUID uuid, @NotNull String fishId) {
        return repository.loadCollectionEntry(uuid, fishId);
    }

    public void resetPlayerData(@NotNull UUID uuid) {
        FishingSeasonData empty = FishingSeasonData.empty(uuid, configuration.season().seasonId());
        repository.saveSeasonData(empty);
        repository.savePermanentData(FishingPermanentData.empty(uuid));
        try {
            for (FishCollectionEntry entry : repository.loadCollection(uuid)) {
                FishCollectionEntry reset = new FishCollectionEntry(uuid, entry.fishId(), 0, 0, entry.firstCatchAt());
                repository.saveCollectionEntry(reset);
            }
        } catch (Exception ignored) {}
    }

    public void giveXp(@NotNull UUID uuid, int amount) {
        var season = configuration.season();
        var change = updateSeason(uuid, state -> state.withXpAdded(amount, season.xpCurve(), season.maxLevel()));
        if (change != null && change.after().level() > change.before().level()) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null && player.isOnline()) {
                onLevelUp(player, change.before().level(), change.after().level());
            }
        }
    }

    private @Nullable FishingRepository.SeasonChange updateSeason(UUID uuid, UnaryOperator<FishingSeasonData> change) {
        try {
            return repository.updateSeason(uuid, configuration.season().seasonId(), change);
        } catch (java.sql.SQLException | RuntimeException failure) {
            logger.log(java.util.logging.Level.WARNING, "钓鱼赛季写入未确认: " + uuid, failure);
            return null;
        }
    }

    public @Nullable FishingRepository.TaskRewardResult completeTask(PlayerTaskInstance instance, int xp) {
        var season = configuration.season();
        var result = repository.completeTask(instance, season.seasonId(), xp, season.xpCurve(), season.maxLevel());
        if (result != null && result.awarded() && result.level() > result.previousLevel()) {
            Player player = plugin.getServer().getPlayer(instance.playerUuid());
            if (player != null && player.isOnline()) {
                onLevelUp(player, result.previousLevel(), result.level());
            }
        }
        return result;
    }

    // ─── 天赋树操作 ────────────────────────────────────────────

    public @NotNull TalentEngine getTalentEngine() {
        return talentEngine;
    }

    public void submitTalentWrite(Player player, String uiId, String token, boolean reset, @Nullable String nodeId) {
        UUID uuid = player.getUniqueId();
        if ((!reset && nodeId == null) || pendingTalents.contains(uuid) || !beginWriteAction(player, uiId, token)) return;
        AuthSnapshot auth = AuthAccess.snapshot(uuid);
        pendingTalents.add(uuid);
        try {
            playerWrites.execute(() -> {
                var change = updateSeason(uuid, state -> reset ? talentEngine.reset(state) : talentEngine.levelUp(state, nodeId));
                if (!stopping) plugin.getServer().getScheduler().runTask(plugin,
                    guard(() -> completeTalentWrite(player, uiId, auth, reset, nodeId, change)));
            });
        } catch (RejectedExecutionException busy) {
            pendingTalents.remove(uuid);
            finishWriteAction(player);
            player.sendMessage("§e天赋写入繁忙，请稍后刷新重试。");
            pushTalentsData(player, uiId);
        }
    }

    private void completeTalentWrite(Player player, String uiId, AuthSnapshot before, boolean reset,
                                     @Nullable String nodeId, @Nullable FishingRepository.SeasonChange change) {
        UUID uuid = player.getUniqueId();
        if (!pendingTalents.remove(uuid) || Bukkit.getPlayer(uuid) != player) return;
        finishWriteAction(player);
        if (!uiQueries.canView(player, uiId)) return;
        AuthSnapshot current = AuthAccess.snapshot(uuid);
        if (!current.admitted() || current.generation() != before.generation()
            || !java.util.Objects.equals(current.connectionId(), before.connectionId())) return;
        if (change == null) {
            player.sendMessage("§c未确认天赋写入，请刷新状态。");
            pushTalentsData(player, uiId);
            return;
        }
        if (reset) {
            player.sendMessage("§a天赋树已重置，所有天赋点已退还。");
        } else {
            TalentNode node = talentEngine.getNode(nodeId);
            String label = node == null ? nodeId : node.displayName();
            boolean upgraded = !change.before().equals(change.after());
            player.sendMessage((upgraded ? "§a天赋升级成功: " : "§c无法升级天赋: ") + label);
        }
        renderTalentsData(player, uiId, change.after());
    }

    // ─── 等级奖励领取 ──────────────────────────────────────────

    public @NotNull List<SeasonRewardDefinition> getSeasonRewards() {
        return seasonRewards;
    }

    /**
     * 领取等级奖励。
     *
     * @param player   玩家
     * @param rewardId 奖励 ID
     * @return 持久交接结果
     */
    public CompletableFuture<Boolean> claimReward(@NotNull Player player, @NotNull String rewardId) {
        UUID playerId = player.getUniqueId();
        if (stopping || !Bukkit.isPrimaryThread() || Bukkit.getPlayer(playerId) != player || !player.isOnline()) {
            return CompletableFuture.completedFuture(false);
        }
        AuthSnapshot admission = AuthAccess.snapshot(playerId);
        if (!admission.admitted()) return CompletableFuture.completedFuture(false);
        SeasonRewardDefinition reward = seasonRewards.stream()
            .filter(r -> r.rewardId().equals(rewardId))
            .findFirst().orElse(null);
        if (reward == null) return CompletableFuture.completedFuture(false);
        String key = RewardDispatcher.rewardKey("level", rewardId);
        var result = submitRewardWork(() -> {
            if (!sameRewardSession(admission)) return false;
            FishingSeasonData data = repository.loadSeasonData(playerId, configuration.season().seasonId());
            if (data.level() < reward.level() || repository.completedRewardKeys(playerId, data.seasonId()).contains(key)
                    || !sameRewardSession(admission)) return false;
            return rewardDispatcher.dispatch(playerId, key, reward, "等级奖励 Lv." + reward.level());
        });
        result.thenAccept(accepted -> {
            if (accepted) onMain(() -> {
                if (Bukkit.getPlayer(playerId) == player && player.isOnline() && sameRewardSession(admission)) {
                    rewardDispatcher.flushPlayer(player);
                }
            });
        });
        return result;
    }

    private static boolean sameRewardSession(AuthSnapshot admission) {
        AuthSnapshot current = AuthAccess.snapshot(admission.playerId());
        return current.admitted() && current.generation() == admission.generation()
            && java.util.Objects.equals(current.connectionId(), admission.connectionId());
    }

    private <T> CompletableFuture<T> submitRewardWork(Supplier<T> work) {
        if (stopping) return CompletableFuture.failedFuture(new IllegalStateException("钓鱼模块已停止"));
        try { return CompletableFuture.supplyAsync(work, playerWrites); }
        catch (RejectedExecutionException busy) { return CompletableFuture.failedFuture(busy); }
    }

    /**
     * 获取玩家可领取但尚未领取的奖励列表。
     *
     * @param uuid 玩家 UUID
     * @return 可领取奖励列表
     */
    public @NotNull List<SeasonRewardDefinition> getClaimableRewards(@NotNull UUID uuid) {
        FishingSeasonData data = repository.loadSeasonData(uuid, configuration.season().seasonId());
        Set<String> claimed = claimedRewardIds(data);
        return seasonRewards.stream()
            .filter(r -> data.level() >= r.level() && !claimed.contains(r.rewardId()))
            .toList();
    }

    private Set<String> claimedRewardIds(FishingSeasonData data) {
        Set<String> claimed = new HashSet<>();
        Set<String> completed = repository.completedRewardKeys(data.uuid(), data.seasonId());
        for (SeasonRewardDefinition reward : seasonRewards) {
            if (completed.contains(RewardDispatcher.rewardKey("level", reward.rewardId()))) {
                claimed.add(reward.rewardId());
            }
        }
        return claimed;
    }

    // ─── 赛季结算 ──────────────────────────────────────────────

    /**
     * 触发赛季结算（对所有在线玩家）。
     */
    public CompletableFuture<Integer> triggerSettlement() {
        List<UUID> recipients = Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).toList();
        var result = submitRewardWork(() -> settlementEngine.settlePlayers(recipients));
        result.thenAccept(failed -> onMain(() -> {
            for (UUID id : recipients) {
                Player player = Bukkit.getPlayer(id);
                if (player != null && player.isOnline()) rewardDispatcher.flushPlayer(player);
            }
        }));
        return result;
    }

    /**
     * 玩家上线时处理。
     * <p>
     * 待发放奖励补发已由本体统一 PendingRewardService 在 PlayerJoinEvent 中自动处理，
     * 此方法仅处理任务分配。
     *
     * @param player 上线玩家
     */
    public void onPlayerJoin(@NotNull Player player) {
        if (fishSales != null) fishSales.joined(player);
        try {
            taskEngine.onPlayerJoin(player);
        } catch (Exception e) {
            logger.warning("分配任务失败 [" + player.getName() + "]: " + e.getMessage());
        }
    }

    /**
     * 玩家退出时清理内存缓存，防止内存泄漏。
     * <p>
     * 清理该玩家的界面查询与钓鱼冷却状态。
     *
     * @param uuid 玩家 UUID
     */
    public void onPlayerQuit(@NotNull UUID uuid) {
        if (fishSales != null) fishSales.quit(uuid);
        forgetCast(uuid);
        tokenGuard.clear(uuid);
        uiQueries.forget(uuid);
        recentCatches.remove(uuid);
        fishingCooldowns.remove(uuid);
        placeholderStats.forget(uuid);
        taskEngine.onPlayerQuit(uuid);
    }

    // ─── 疲惫值 ────────────────────────────────────────────────

    /**
     * 获取玩家当前疲惫值。
     *
     * @param uuid 玩家 UUID
     * @return 疲惫值 [0, maxFatigue]，疲惫值系统未启用时返回 0
     */
    public int getFatigue(@NotNull UUID uuid) {
        if (fatigueEngine == null) return 0;
        FishingSeasonData data = repository.loadSeasonData(uuid, configuration.season().seasonId());
        return data.fatigue();
    }

    /**
     * 获取疲惫效率系数（供 UI 显示）。
     *
     * @param uuid 玩家 UUID
     * @return 效率系数（>1 提升，<1 降低，=1 无影响）
     */
    public double getFatigueEfficiency(@NotNull UUID uuid) {
        if (fatigueEngine == null) return 1.0;
        return fatigueEngine.efficiencyMultiplier(getFatigue(uuid));
    }

    /**
     * 获取疲惫值引擎（供模块内部使用）。
     */
    public @Nullable FatigueEngine fatigueEngine() {
        return fatigueEngine;
    }

    /**
     * 设置玩家疲惫值（管理员命令使用）。
     *
     * @param uuid  玩家 UUID
     * @param value 疲惫值（0-100，自动 clamp）
     */
    public void setFatigue(@NotNull UUID uuid, int value) {
        int clamped = Math.max(0, Math.min(100, value));
        updateSeason(uuid, data -> new FishingSeasonData(
            data.uuid(), data.seasonId(), data.level(), data.currentXp(),
            data.talentPoints(), data.unspentTalentPoints(), data.talents(),
            clamped, data.claimedMaxFatigueReward()));
    }

    // ─── 任务查询 ──────────────────────────────────────────────

    public @NotNull TaskEngine getTaskEngine() {
        return taskEngine;
    }

    public int getCollectionCount(@NotNull UUID uuid) {
        return repository.countCollection(uuid);
    }

    public int getTotalFishTypes() {
        return configuration.fishes().size();
    }

    public @Nullable String displayPlaceholder(@NotNull UUID uuid, @NotNull String name) {
        return name.equals("season_id") ? configuration.season().seasonId() : placeholderStats.value(uuid, name);
    }

    private List<RecentCatch> loadRecentCatches(UUID uuid) {
        CatchHistory history = recentCatches.computeIfAbsent(uuid, ignored -> new CatchHistory());
        List<RecentCatch> cached = history.entries;
        if (cached != null) return cached;
        List<RecentCatch> loaded = repository.loadCatchRecords(uuid, MAX_RECENT_CATCHES).stream()
            .map(record -> new RecentCatch(record.fishId(), record.fishName(), record.rarity(),
                record.size(), record.perfect(), record.timestamp()))
            .toList();
        // A catch or logout can detach this holder while SQL is in flight.
        if (!loaded.isEmpty()) history.entries = loaded;
        return loaded;
    }

    private static final class CatchHistory {
        private volatile List<RecentCatch> entries;
    }

    /** 打开指定 UI（供 PacketHandler 导航跳转使用）。 */
    public void openUi(@NotNull Player player, @NotNull String uiId) {
        if (stopping || packetBridge == null || !packetBridge.isAvailable()) return;
        UUID uuid = player.getUniqueId();
        uiQueries.forget(uuid);
        tokenGuard.clear(uuid);
        packetBridge.openUi(player, uiId);
    }

    /**
     * 推送主界面总览数据到 UI。
     * <p>
     * 包含：等级/经验、图鉴完成度、捕获统计、天赋点、可领取奖励数、最近捕获记录流。
     */
    public void pushMainData(@NotNull Player player, @NotNull String uiId) {
        uiQueries.read(player, uiId, uuid -> {
            var season = getSeasonData(uuid);
            return new MainViewData(season, repository.loadPermanentData(uuid), repository.countCollection(uuid),
                loadRecentCatches(uuid), claimedRewardIds(season), season.xpForNextLevel(configuration.season().xpCurve()));
        }, data -> renderMainData(player, uiId, data));
    }

    private record MainViewData(FishingSeasonData season, FishingPermanentData permanent, int collectionCount,
                                List<RecentCatch> recent, Set<String> claimedRewards, int xpForNext) {}

    private void renderMainData(Player player, String uiId, MainViewData data) {
        FishingSeasonData seasonData = data.season();
        FishingPermanentData permData = data.permanent();
        List<RecentCatch> recent = data.recent();

        // 统计可领取奖励数
        Set<String> claimedRewards = data.claimedRewards();
        int claimableRewardCount = 0;
        for (SeasonRewardDefinition reward : seasonRewards) {
            if (seasonData.level() >= reward.level() && !claimedRewards.contains(reward.rewardId())) {
                claimableRewardCount++;
            }
        }

        // 当前级升级所需经验与进度比
        int xpForNext = data.xpForNext();
        int maxLevel = configuration.season().maxLevel();
        double progressRatio = xpForNext > 0 ? (double) seasonData.currentXp() / xpForNext : 0;
        if (seasonData.level() >= maxLevel) progressRatio = 1.0;

        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("packetId", "AXS_FISHING");
        payload.put("seasonName", configuration.season().displayName());
        payload.put("playerLevel", seasonData.level());
        payload.put("maxLevel", maxLevel);
        payload.put("currentXp", seasonData.currentXp());
        payload.put("xpForNext", xpForNext);
        payload.put("progressRatio", progressRatio);
        payload.put("totalCaught", permData.totalCaught());
        payload.put("perfectCatches", permData.perfectCatches());
        payload.put("treasureCaught", permData.treasureCaught());
        payload.put("collectionCount", data.collectionCount());
        payload.put("totalFishTypes", configuration.fishes().size());
        int collectionPercent = configuration.fishes().isEmpty() ? 0 : (data.collectionCount() * 100) / configuration.fishes().size();
        payload.put("collectionPercent", collectionPercent);
        payload.put("unspentTalentPoints", seasonData.unspentTalentPoints());
        payload.put("totalTalentPoints", seasonData.talentPoints());
        payload.put("unlockedTalentCount", seasonData.talents().size());
        payload.put("claimableRewardCount", claimableRewardCount);
        payload.put("claimedRewardCount", claimedRewards.size());
        payload.put("totalRewardCount", seasonRewards.size());

        // 最近捕获记录流
        java.util.Map<String, Object> recentMap = new java.util.LinkedHashMap<>();
        int idx = 0;
        for (RecentCatch catchEntry : recent) {
            java.util.Map<String, Object> entry = new java.util.LinkedHashMap<>();
            entry.put("fishId", catchEntry.fishId());
            entry.put("fishName", catchEntry.fishName());
            entry.put("rarity", catchEntry.rarity());
            entry.put("size", catchEntry.size());
            entry.put("perfect", catchEntry.perfect());
            entry.put("timestamp", catchEntry.timestamp());
            recentMap.put(Integer.toString(idx), entry);
            // 鱼物品图标 JSON（分离模式）
            FishDefinition fishDef = findFish(catchEntry.fishId());
            if (fishDef != null) {
                payload.put("recentIconJson" + idx, iconResolver.resolveItemJson(player, fishDef.displayIcon()));
            }
            idx++;
        }
        payload.put("recentCatches", recentMap);
        payload.put("recentCount", recent.size());

        sendUiInitPacket(player, uiId, payload);
    }

    public void pushCollectionData(@NotNull Player player, @NotNull String uiId) {
        uiQueries.read(player, uiId, uuid -> {
            var season = getSeasonData(uuid);
            var counts = new HashMap<String, FishCollectionEntry>();
            for (FishCollectionEntry caught : repository.loadCollection(uuid)) counts.put(caught.fishId(), caught);
            return new CollectionViewData(season.level(), season.currentXp(), getPermanentData(uuid).totalCaught(), counts);
        }, data -> renderCollectionData(player, uiId, data));
    }

    private record CollectionViewData(int level, int xp, int totalCaught, Map<String, FishCollectionEntry> collection) {}

    private void renderCollectionData(Player player, String uiId, CollectionViewData data) {
        Map<String, FishCollectionEntry> collection = data.collection();
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("packetId", "AXS_FISHING");
        payload.put("playerLevel", data.level());
        payload.put("currentXp", data.xp());
        payload.put("totalCaught", data.totalCaught());
        payload.put("collectionCount", collection.size());
        payload.put("totalFishTypes", configuration.fishes().size());
        int percent = configuration.fishes().isEmpty() ? 0 : (collection.size() * 100) / configuration.fishes().size();
        payload.put("collectionPercent", percent);

        java.util.Map<String, Object> fishDict = new java.util.LinkedHashMap<>();
        int idx = 0;
        for (FishDefinition fish : configuration.fishes()) {
            java.util.Map<String, Object> entry = new java.util.LinkedHashMap<>();
            entry.put("id", fish.id());
            entry.put("name", fish.displayName());
            entry.put("rarity", fish.rarity().name().toLowerCase());

            FishCollectionEntry caught = collection.get(fish.id());

            entry.put("caught", caught != null && caught.caughtCount() > 0);
            entry.put("maxSize", caught != null ? caught.maxSize() : 0);
            entry.put("count", caught != null ? caught.caughtCount() : 0);
            fishDict.put(Integer.toString(idx), entry);
            // 鱼物品图标 itemJson（分离模式，UI 端 Slot Icon 通过 fishIconJson{idx} 渲染）
            payload.put("fishIconJson" + idx, iconResolver.resolveItemJson(player, fish.displayIcon()));
            idx++;
        }
        payload.put("fishList", fishDict);
        payload.put("maxFishCount", configuration.fishes().size());

        sendUiInitPacket(player, uiId, payload);
    }

    // ─── 跨模块事件发射 ──────────────────────────────────────────────

    private void emitFishingEvent(@NotNull Player player, @NotNull FishDefinition fish, int size,
                                   @Nullable WaterArea water, @Nullable String baitId, boolean perfect,
                                    boolean isFirstCatch, @Nullable String treasureId, @Nullable String schoolId,
                                   @NotNull FishingSeasonData seasonData, @NotNull FishingPermanentData permData) {
        // EventBus 发布（供 BattlePass / QuestGPS / OnlineRewards 等订阅）
        EventBusCapability eventBus = eventBusProvider != null ? eventBusProvider.get() : null;
        boolean gotTreasure = treasureId != null;
        if (eventBus != null) {
            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("fish_id", fish.id());
            payload.put("fish_name", fish.displayName());
            payload.put("fish_rarity", fish.rarity().name());
            payload.put("fish_size", String.valueOf(size));
            payload.put("water_area", water != null ? water.id() : "default");
            payload.put("bait_id", baitId != null ? baitId : "");
            if (schoolId != null) payload.put("school_id", schoolId);
            payload.put("is_perfect", String.valueOf(perfect));
            payload.put("first_catch", String.valueOf(isFirstCatch));
            payload.put("player_level", String.valueOf(seasonData.level()));
            Map<String, String> snapshot = Map.copyOf(payload);
            eventBus.publish("axs.fishing.success", player, snapshot);

            if (perfect) {
                eventBus.publish("axs.fishing.perfect", player, snapshot);
            }
            if (isFirstCatch && eventBus.hasSubscribers("axs.fishing.collection_unlock")) {
                // loadCollection 是 DB 查询，挪异步读完后回主线程 publish（保持订阅者在主线程）
                UUID uid = player.getUniqueId();
                Map<String, String> collectionPayload = new LinkedHashMap<>(payload);
                readAsync(() -> {
                    int totalCollection;
                    try {
                        totalCollection = repository.countCollection(uid);
                    } catch (Exception e) {
                        logger.warning("读取图鉴计数失败 [" + uid + "]: " + e.getMessage());
                        return;
                    }
                    collectionPayload.put("total_collection_count", String.valueOf(totalCollection));
                    onMain(() ->
                        eventBus.publish("axs.fishing.collection_unlock", player, collectionPayload));
                });
            }
            if (gotTreasure) {
                Map<String, String> treasurePayload = new LinkedHashMap<>();
                treasurePayload.put("water_area", water != null ? water.id() : "default");
                treasurePayload.put("treasure_id", treasureId);
                if (schoolId != null) treasurePayload.put("school_id", schoolId);
                eventBus.publish("axs.fishing.treasure", player, treasurePayload);
            }
        }

    }

    // ─── 里程碑检测 ─────────────────────────────────────────────────

    private void checkMilestones(@NotNull Player player, @NotNull FishingPermanentData data,
                                   @NotNull FishDefinition fish, boolean isFirstCatch) {
        // 首次捕获传说鱼
        if (isFirstCatch && fish.rarity() == FishRarity.LEGENDARY) {
            grantTitle(player, "legendary_hunter", "7d");
            sendMailPreset(player, "legendary_first_catch");
        }

        // 累计捕获里程碑
        int total = data.totalCaught();
        if (total == 10) {
            grantTitle(player, "fisher_apprentice", "permanent");
        } else if (total == 100) {
            grantTitle(player, "fisher_master", "permanent");
        } else if (total == 500) {
            grantTitle(player, "fisher_legend", "permanent");
        }

        // 完美捕获里程碑
        int perfect = data.perfectCatches();
        if (perfect == 50) {
            grantTitle(player, "perfect_master", "permanent");
        }
    }

    private void grantTitle(@NotNull Player player, @NotNull String titleId, @NotNull String duration) {
        TitleGrantable title = titleProvider != null ? titleProvider.get() : null;
        if (title != null) {
            try {
                title.giveTitle(player.getUniqueId(), titleId, duration, "Fishing");
            } catch (Exception e) {
                logger.warning("授予称号失败: " + titleId + " — " + e.getMessage());
            }
        }
    }

    private void sendMailPreset(@NotNull Player player, @NotNull String presetId) {
        MailDispatchable mail = mailProvider != null ? mailProvider.get() : null;
        if (mail != null) {
            try {
                mail.dispatchPreset(MailPresetRequest.builder(presetId, player.getName())
                    .sourceModule("fishing")
                    .build());
            } catch (Exception e) {
                logger.warning("发送邮件失败: " + presetId + " — " + e.getMessage());
            }
        }
    }

    // ─── 售卖 ────────────────────────────────────────────────────────

    /** 解析鱼的售卖货币：优先用 currencyReward 配置，未配置时回退到默认货币。 */
    private @NotNull String resolveSellCurrency(@NotNull FishDefinition fish) {
        return fish.currencyReward() != null ? fish.currencyReward().currencyId() : getDefaultCurrency();
    }

    /** 获取消息，null 时返回 fallback。 */
    private @NotNull String msgOr(@NotNull String key, @NotNull String fallback, Object... args) {
        String msg = getMessage(key, args);
        return msg != null ? msg : fallback;
    }

    /**
     * 售卖玩家主手中的鱼。
     */
    public @NotNull String sellFishInHand(@NotNull Player player) {
        if (stopping || fishSales == null || !Bukkit.isPrimaryThread()) return "&c售鱼暂不可用。";
        int slot = player.getInventory().getHeldItemSlot();
        var offer = fishOffer(player.getInventory().getItemInMainHand(), slot, 1);
        if (offer == null) return msgOr("sell_not_fish", "&c手中没有可出售的钓鱼产物。");
        CurrencyBridgeAPI currencyApi = currencyApi();
        if (currencyApi == null) return "&c货币系统暂不可用。";
        var result = fishSales.sell(player, offer.currency(), currencyApi.bridge(offer.currency()),
            offer.amount(), List.of(offer.fish()));
        if (result.outcome() != FishingSales.Outcome.SOLD) return saleMessage(result);
        return msgOr("sell_success", "&a成功出售 &e" + offer.name() + " &a获得 &6" + offer.amount() + " " + offer.currency(),
            offer.name(), offer.amount().toPlainString(), offer.currency());
    }

    /**
     * 售卖背包中所有钓鱼产物。
     */
    public @NotNull String sellAllFish(@NotNull Player player) {
        if (stopping || fishSales == null || !Bukkit.isPrimaryThread()) return "&c售鱼暂不可用。";
        CurrencyBridgeAPI currencyApi = currencyApi();
        if (currencyApi == null) return "&c货币系统暂不可用。";
        Map<String, List<FishOffer>> byCurrency = new LinkedHashMap<>();
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null) continue;
            var offer = fishOffer(item, slot, item.getAmount());
            if (offer != null) byCurrency.computeIfAbsent(offer.currency(), ignored -> new ArrayList<>()).add(offer);
        }
        if (byCurrency.isEmpty()) return msgOr("sell_none", "&c背包中没有可出售的鱼。");

        StringBuilder details = new StringBuilder();
        int totalSold = 0;
        for (var entry : byCurrency.entrySet()) {
            String currency = entry.getKey();
            var offers = entry.getValue();
            BigDecimal payout = offers.stream().map(FishOffer::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            var fish = offers.stream().map(FishOffer::fish).toList();
            var result = fishSales.sell(player, currency, currencyApi.bridge(currency), payout, fish);
            if (result.outcome() == FishingSales.Outcome.SOLD) {
                totalSold += fish.stream().mapToInt(FishingSales.Fish::count).sum();
                details.append(" &6+").append(payout.toPlainString()).append(' ').append(currency).append('。');
            } else {
                details.append(' ').append(currency).append(' ').append(saleMessage(result));
                if (result.outcome() == FishingSales.Outcome.REVIEW || result.outcome() == FishingSales.Outcome.BLOCKED) break;
            }
        }
        return (totalSold == 0 ? details.toString() : "&a共出售 &e" + totalSold + " &a条鱼。" + details).trim();
    }

    private record FishOffer(FishingSales.Fish fish, String name, String currency, BigDecimal amount) {}

    private @Nullable FishOffer fishOffer(@Nullable ItemStack item, int slot, int count) {
        String fishId = getFishIdFromItem(item);
        if (item == null || fishId == null || count <= 0 || count > item.getAmount()) return null;
        FishDefinition fish = findFish(fishId);
        if (fish == null) return null;
        int price = fish.calculatePrice(getFishSizeFromItem(item));
        String currency = resolveSellCurrency(fish);
        if (price <= 0 || currency == null || currency.isBlank()) return null;
        return new FishOffer(new FishingSales.Fish(slot, item.clone(), count), fish.displayName(), currency,
            BigDecimal.valueOf(price).multiply(BigDecimal.valueOf(count)));
    }

    private static String saleMessage(FishingSales.Result result) {
        return switch (result.outcome()) {
            case RETURN_QUEUED -> "&e入账未成功，鱼已加入返还队列。";
            case UNAVAILABLE -> "&c售鱼条件未满足，物品未扣除。";
            case BLOCKED -> "&c上一笔售鱼尚未结清，请联系管理员核对。";
            case REVIEW -> "&e售鱼结果待核对，交易编号：" + result.request();
            case SOLD -> "&a售鱼完成。";
        };
    }

    // ─── 物品识别辅助 ────────────────────────────────────────────────

    /** 从物品 PDC 读取字符串值，物品无效或无标签时返回 null。 */
    private @Nullable String getPdcString(@Nullable ItemStack item, @NotNull String key) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey nk = new NamespacedKey(plugin, key);
        return pdc.has(nk, PersistentDataType.STRING) ? pdc.get(nk, PersistentDataType.STRING) : null;
    }

    /** 从物品 PDC 读取整数值，物品无效或无标签时返回 0。 */
    private int getPdcInt(@Nullable ItemStack item, @NotNull String key) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey nk = new NamespacedKey(plugin, key);
        if (pdc.has(nk, PersistentDataType.INTEGER)) {
            Integer val = pdc.get(nk, PersistentDataType.INTEGER);
            return val != null ? val : 0;
        }
        return 0;
    }

    private @Nullable String getFishIdFromItem(@Nullable ItemStack item) {
        return getPdcString(item, PDC_FISH_ID);
    }

    private int getFishSizeFromItem(@Nullable ItemStack item) {
        return getPdcInt(item, PDC_FISH_SIZE);
    }

    // ─── 天赋/奖励/任务 UI 数据推送 ──────────────────────────

    /**
     * 推送天赋树数据到 UI。
     */
    public void pushTalentsData(@NotNull Player player, @NotNull String uiId) {
        if (pendingTalents.contains(player.getUniqueId())) return;
        uiQueries.read(player, uiId, this::getSeasonData, season -> renderTalentsData(player, uiId, season));
    }

    private void renderTalentsData(Player player, String uiId, FishingSeasonData seasonData) {
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("packetId", "AXS_FISHING_TALENTS");
        payload.put("seasonId", seasonData.seasonId());
        payload.put("level", seasonData.level());
        payload.put("talentPoints", seasonData.talentPoints());
        payload.put("unspentTalentPoints", seasonData.unspentTalentPoints());
        // 已点亮天赋：节点 ID → 当前等级
        payload.put("unlockedTalents", new java.util.HashMap<>(seasonData.talents()));

        // 天赋树定义（按树分组）
        java.util.Map<String, Object> treesMap = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, xuanmo.arcartxsuite.fishing.talent.TalentTreeDefinition> treeEntry : talentEngine.getTrees().entrySet()) {
            String treeId = treeEntry.getKey();
            xuanmo.arcartxsuite.fishing.talent.TalentTreeDefinition tree = treeEntry.getValue();
            java.util.Map<String, Object> treeMap = new java.util.LinkedHashMap<>();
            treeMap.put("id", treeId);
            treeMap.put("displayName", tree.displayName());
            treeMap.put("description", tree.description());
            treeMap.put("icon", tree.icon());
            // 树图标 itemJson（UI 端 Slot Icon 渲染用）
            treeMap.put("iconJson", buildTalentIconJson(tree.icon(), player));
            java.util.Map<String, Object> nodesMap = new java.util.LinkedHashMap<>();
            int nodeIdx = 0;
            for (xuanmo.arcartxsuite.fishing.talent.TalentNode node : tree.nodes()) {
                java.util.Map<String, Object> nodeMap = new java.util.LinkedHashMap<>();
                nodeMap.put("id", node.id());
                nodeMap.put("name", node.displayName());
                nodeMap.put("description", node.description());
                nodeMap.put("tier", node.tier());
                nodeMap.put("branch", node.branch());
                nodeMap.put("treeId", node.treeId());
                int currentLevel = seasonData.talents().getOrDefault(node.id(), 0);
                nodeMap.put("unlocked", currentLevel > 0);
                nodeMap.put("level", currentLevel);
                nodeMap.put("maxLevel", node.maxLevel());
                boolean canLevelUp = talentEngine.canLevelUp(seasonData, node.id());
                nodeMap.put("canLevelUp", canLevelUp);
                nodeMap.put("iconJson", buildTalentNodeIconJson(node, currentLevel > 0, canLevelUp, player));
                // 效果描述（中文）
                java.util.List<String> effects = new java.util.ArrayList<>();
                if (node.effectType() != null) {
                    effects.add(formatEffect(node.effectType(), node.valueAtLevel(currentLevel)));
                }
                for (xuanmo.arcartxsuite.fishing.talent.TalentEffect effect : node.effects()) {
                    effects.add(formatEffect(effect.effectType(), effect.effectValue()));
                }
                nodeMap.put("effects", effects);
                // 前置节点
                nodeMap.put("prerequisites", new java.util.ArrayList<>(node.prerequisites()));
                nodesMap.put(Integer.toString(nodeIdx), nodeMap);
                nodeIdx++;
            }
            treeMap.put("nodes", nodesMap);
            treeMap.put("nodeCount", tree.nodes().size());
            treesMap.put(treeId, treeMap);
        }
        payload.put("trees", treesMap);

        // 联动天赋节点（跨树，不属于任何单棵树）
        java.util.Map<String, Object> linksMap = new java.util.LinkedHashMap<>();
        int linkIdx = 0;
        for (xuanmo.arcartxsuite.fishing.talent.TalentNode link : talentEngine.getLinkNodes()) {
            java.util.Map<String, Object> linkMap = new java.util.LinkedHashMap<>();
            linkMap.put("id", link.id());
            linkMap.put("name", link.displayName());
            linkMap.put("description", link.description());
            linkMap.put("tier", link.tier());
            linkMap.put("branch", link.branch());
            linkMap.put("treeId", link.treeId());
            int linkLevel = seasonData.talents().getOrDefault(link.id(), 0);
            linkMap.put("unlocked", linkLevel > 0);
            linkMap.put("level", linkLevel);
            linkMap.put("maxLevel", link.maxLevel());
            linkMap.put("canLevelUp", talentEngine.canLevelUp(seasonData, link.id()));
            linkMap.put("iconJson", buildTalentNodeIconJson(link, linkLevel > 0, talentEngine.canLevelUp(seasonData, link.id()), player));
            java.util.List<String> linkEffects = new java.util.ArrayList<>();
            if (link.effectType() != null) {
                linkEffects.add(formatEffect(link.effectType(), link.valueAtLevel(linkLevel)));
            }
            for (xuanmo.arcartxsuite.fishing.talent.TalentEffect effect : link.effects()) {
                linkEffects.add(formatEffect(effect.effectType(), effect.effectValue()));
            }
            linkMap.put("effects", linkEffects);
            linkMap.put("prerequisites", new java.util.ArrayList<>(link.prerequisites()));
            java.util.List<Boolean> prerequisiteStates = new java.util.ArrayList<>();
            for (String prerequisite : link.prerequisites()) {
                prerequisiteStates.add(seasonData.talents().getOrDefault(prerequisite, 0) > 0);
            }
            linkMap.put("prerequisiteStates", prerequisiteStates);
            linksMap.put(Integer.toString(linkIdx), linkMap);
            linkIdx++;
        }
        payload.put("links", linksMap);
        payload.put("linkCount", linkIdx);

        // 已激活天赋加成汇总（按效果类型累加，格式化为中文描述列表）
        java.util.Map<String, Double> bonusSums = new java.util.LinkedHashMap<>();
        for (xuanmo.arcartxsuite.fishing.talent.TalentTreeDefinition tree : talentEngine.getTrees().values()) {
            for (xuanmo.arcartxsuite.fishing.talent.TalentNode node : tree.nodes()) {
                if (seasonData.talents().getOrDefault(node.id(), 0) > 0) {
                    if (node.effectType() != null && !node.effectType().isBlank()) {
                        bonusSums.merge(node.effectType(), node.valueAtLevel(1), Double::sum);
                    }
                    for (xuanmo.arcartxsuite.fishing.talent.TalentEffect effect : node.effects()) {
                        bonusSums.merge(effect.effectType(), effect.effectValue(), Double::sum);
                    }
                }
            }
        }
        for (xuanmo.arcartxsuite.fishing.talent.TalentNode link : talentEngine.getLinkNodes()) {
            if (seasonData.talents().getOrDefault(link.id(), 0) > 0) {
                if (link.effectType() != null && !link.effectType().isBlank()) {
                    bonusSums.merge(link.effectType(), link.valueAtLevel(1), Double::sum);
                }
                for (xuanmo.arcartxsuite.fishing.talent.TalentEffect effect : link.effects()) {
                    bonusSums.merge(effect.effectType(), effect.effectValue(), Double::sum);
                }
            }
        }
        // 转为格式化中文列表（按效果类型排序：战斗向优先）
        java.util.List<String> activeBonuses = new java.util.ArrayList<>();
        java.util.List<String> effectOrder = java.util.List.of(
            "progress_rate", "catch_duration", "flee_chance", "progress_drain", "start_progress",
            "perfect_window", "flee_cooldown", "flee_duration", "flee_speed", "fish_speed", "fish_edge",
            "bar_height", "bar_damping", "bar_bounce", "bar_force", "bar_click_force", "bar_gravity",
            "fatigue_reduction", "fatigue_efficiency", "rest_boost",
            "xp_multiplier", "price_multiplier", "rare_chance", "legendary_chance", "treasure_chance"
        );
        for (String effectType : effectOrder) {
            Double sum = bonusSums.get(effectType);
            if (sum != null && sum != 0.0) {
                activeBonuses.add("&8" + formatEffect(effectType, sum));
            }
        }
        payload.put("activeBonuses", activeBonuses);

        sendUiInitPacket(player, uiId, payload);
    }

    /**
     * 推送赛季奖励数据到 UI。
     */
    public void pushRewardsData(@NotNull Player player, @NotNull String uiId) {
        uiQueries.read(player, uiId, uuid -> {
            var season = getSeasonData(uuid);
            return new RewardsViewData(season.level(), claimedRewardIds(season));
        }, data -> renderRewardsData(player, uiId, data));
    }

    private record RewardsViewData(int level, Set<String> claimedRewards) {}

    private void renderRewardsData(Player player, String uiId, RewardsViewData data) {
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("packetId", "AXS_FISHING_REWARDS");
        payload.put("level", data.level());
        Set<String> claimedRewards = data.claimedRewards();
        payload.put("claimedRewards", new java.util.ArrayList<>(claimedRewards));

        // 奖励列表
        java.util.Map<String, Object> rewardsMap = new java.util.LinkedHashMap<>();
        int idx = 0;
        for (SeasonRewardDefinition reward : seasonRewards) {
            java.util.Map<String, Object> entry = new java.util.LinkedHashMap<>();
            entry.put("id", reward.rewardId());
            entry.put("level", reward.level());
            entry.put("type", reward.type());
            // iconName 供 UI 端文本显示用（从 IconDefinition 提取）
            xuanmo.arcartxsuite.api.icon.IconDefinition iconDef = reward.icon();
            entry.put("iconName", iconDef != null && iconDef.name() != null
                ? ChatColor.stripColor(MessageProvider.translateColors(iconDef.name())) : "");
            entry.put("iconMaterial", iconDef != null ? iconDef.material() : "");
            entry.put("iconAmount", reward.item() != null ? reward.item().amount() : (iconDef != null ? iconDef.amount() : 1));
            entry.put("claimable", data.level() >= reward.level() && !claimedRewards.contains(reward.rewardId()));
            entry.put("claimed", claimedRewards.contains(reward.rewardId()));
            rewardsMap.put(Integer.toString(idx), entry);
            // 图标 itemJson：Java 端通过 IconResolver 生成完整 ItemStack 后序列化
            payload.put("iconJson" + idx, buildRewardIconJson(reward, player));
            idx++;
        }
        payload.put("rewards", rewardsMap);
        payload.put("rewardCount", seasonRewards.size());

        sendUiInitPacket(player, uiId, payload);
    }

    /**
     * 根据奖励定义的 IconDefinition 构建 itemJson 字符串（UI 端 Slot Icon 渲染用）。
     * <p>
     * 通过 IconResolver 生成完整 ItemStack（支持外部物品库、自定义 NBT、
     * custom-model-data、glow、skull-texture、color 等），再序列化为 itemJson。
     *
     * @param reward 奖励定义
     * @param player 玩家上下文（Overture 等外部物品库需要）
     * @return itemJson 字符串，生成失败时返回空字符串（UI 端回退到 air 图标）
     */
    private @NotNull String buildRewardIconJson(@NotNull SeasonRewardDefinition reward, @NotNull Player player) {
        if (reward.icon() == null) {
            return "";
        }
        return iconResolver.resolveItemJson(player, reward.icon());
    }

    /**
     * 根据天赋树图标物品引用构建 itemJson 字符串（UI 端 Slot Icon 渲染用）。
     *
     * @param icon 图标定义，为 null 时返回空字符串
     * @param player  玩家上下文（Overture 等外部物品库需要）
     * @return itemJson 字符串，生成失败时返回空字符串
     */
    private @NotNull String buildTalentIconJson(@Nullable IconDefinition icon, @NotNull Player player) {
        if (icon == null || !icon.hasIcon()) {
            return "";
        }
        return iconResolver.resolveItemJson(player, icon);
    }

    /** 效果类型 → 中文描述映射 */
    private static final java.util.Map<String, String> EFFECT_NAMES = java.util.Map.ofEntries(
        java.util.Map.entry("bar_height", "绿条高度"),
        java.util.Map.entry("bar_damping", "绿条阻尼"),
        java.util.Map.entry("bar_bounce", "绿条反弹"),
        java.util.Map.entry("bar_force", "绿条力系数"),
        java.util.Map.entry("fish_speed", "鱼速度"),
        java.util.Map.entry("fish_dwell", "鱼停留时间"),
        java.util.Map.entry("fish_dart", "鱼突进"),
        java.util.Map.entry("fish_noise", "鱼噪声"),
        java.util.Map.entry("fish_edge", "鱼边缘反弹"),
        java.util.Map.entry("flee_chance", "逃离概率"),
        java.util.Map.entry("flee_speed", "逃离速度"),
        java.util.Map.entry("flee_cooldown", "逃离冷却"),
        java.util.Map.entry("flee_duration", "逃离持续"),
        java.util.Map.entry("progress_rate", "进度增速"),
        java.util.Map.entry("progress_drain", "进度下降"),
        java.util.Map.entry("perfect_window", "完美窗口"),
        java.util.Map.entry("start_progress", "起始进度"),
        java.util.Map.entry("xp_multiplier", "钓鱼经验"),
        java.util.Map.entry("price_multiplier", "鱼售价"),
        java.util.Map.entry("legendary_chance", "传说鱼概率"),
        java.util.Map.entry("treasure_chance", "宝藏概率"),
        java.util.Map.entry("rare_chance", "稀有鱼概率"),
        java.util.Map.entry("xp_in_bar", "绿条内经验"),
        java.util.Map.entry("perfect_xp", "完美捕获经验"),
        java.util.Map.entry("catch_duration", "钓鱼超时"),
        java.util.Map.entry("bar_gravity", "绿条重力"),
        java.util.Map.entry("bar_click_force", "绿条点击力"),
        java.util.Map.entry("fatigue_reduction", "疲惫增长降低"),
        java.util.Map.entry("fatigue_efficiency", "疲惫效率缓解"),
        java.util.Map.entry("rest_boost", "休息恢复加速"),
        java.util.Map.entry("bait_slot_bonus", "饵料槽位")
    );

    /**
     * 将效果类型+数值格式化为中文描述。
     * bar_height 单位是像素（直接显示），其余是百分比。
     * "降低"类效果显示 -X%，"提升/加成"类显示 +X%。
     */
    private @NotNull String formatEffect(@NotNull String effectType, double value) {
        String name = EFFECT_NAMES.getOrDefault(effectType, effectType);
        // 像素类：直接显示数值
        if ("bar_height".equals(effectType)) {
            return name + " +" + String.format("%.0f", value) + "px";
        }
        // 整数槽位类：直接显示整数
        if ("bait_slot_bonus".equals(effectType)) {
            return name + " +" + String.format("%.0f", value);
        }
        // "降低/减少"类效果
        if (isReductionEffect(effectType)) {
            return name + " -" + String.format("%.1f", value * 100) + "%";
        }
        // "提升/加成"类效果
        return name + " +" + String.format("%.1f", value * 100) + "%";
    }

    /** 判断是否为"降低/减少"类效果（显示 -X%） */
    private boolean isReductionEffect(@NotNull String effectType) {
        return "bar_bounce".equals(effectType)
            || "bar_gravity".equals(effectType)
            || "fish_speed".equals(effectType)
            || "fish_dwell".equals(effectType)
            || "fish_dart".equals(effectType)
            || "fish_noise".equals(effectType)
            || "fish_edge".equals(effectType)
            || "flee_chance".equals(effectType)
            || "flee_speed".equals(effectType)
            || "flee_duration".equals(effectType)
            || "progress_drain".equals(effectType)
            || "fatigue_reduction".equals(effectType);
    }

    private @NotNull String buildTalentNodeIconJson(@NotNull xuanmo.arcartxsuite.fishing.talent.TalentNode node, boolean unlocked, boolean canLevelUp, @NotNull Player player) {
        // 优先使用节点自定义 icon（IconDefinition），为空时回退到 effectType 映射
        IconDefinition iconRef = node.icon();
        ItemStack stack;
        if (iconRef != null && iconRef.hasIcon()) {
            stack = iconResolver.resolveItemStack(player, iconRef);
            if (stack == null) {
                // 外部物品库生成失败时，尝试用 sourceId/material 作为原版材质回退
                String fallbackId = iconRef.sourceId() != null && !iconRef.sourceId().isBlank()
                    ? iconRef.sourceId() : iconRef.material();
                if (fallbackId != null && !fallbackId.isBlank()) {
                    Material fallback = Material.matchMaterial(fallbackId, true);
                    if (fallback != null && !fallback.isAir()) {
                        stack = new ItemStack(fallback, 1);
                    }
                }
            }
            if (stack == null) return "";
        } else {
            // 回退到 effectType 默认映射
            String effectType = node.effects().isEmpty() ? "" : node.effects().get(0).effectType();
            String material = switch (effectType) {
                case "bar_height" -> "minecraft:iron_ingot";
                case "bar_damping" -> "minecraft:slime_ball";
                case "bar_bounce" -> "minecraft:rabbit_foot";
                case "bar_force" -> "minecraft:anvil";
                case "fish_speed" -> "minecraft:feather";
                case "fish_dwell" -> "minecraft:clock";
                case "fish_dart" -> "minecraft:arrow";
                case "fish_noise" -> "minecraft:amethyst_shard";
                case "fish_edge" -> "minecraft:shield";
                case "flee_chance" -> "minecraft:iron_sword";
                case "flee_speed" -> "minecraft:sugar";
                case "flee_cooldown" -> "minecraft:clock";
                case "flee_duration" -> "minecraft:string";
                case "progress_rate" -> "minecraft:emerald";
                case "progress_drain" -> "minecraft:honey_bottle";
                case "perfect_window" -> "minecraft:ender_eye";
                case "start_progress" -> "minecraft:firework_rocket";
                case "xp_multiplier" -> "minecraft:diamond";
                case "xp_in_bar" -> "minecraft:glowstone_dust";
                case "price_multiplier" -> "minecraft:gold_ingot";
                case "legendary_chance" -> "minecraft:nether_star";
                case "treasure_chance" -> "minecraft:chest";
                case "rare_chance" -> "minecraft:lapis_lazuli";
                case "perfect_xp" -> "minecraft:experience_bottle";
                case "catch_duration" -> "minecraft:clock";
                case "bar_gravity" -> "minecraft:feather";
                case "bar_click_force" -> "minecraft:piston";
                case "fatigue_reduction" -> "minecraft:golden_carrot";
                case "fatigue_efficiency" -> "minecraft:golden_apple";
                case "rest_boost" -> "minecraft:red_bed";
                default -> node.isLink() ? "minecraft:heart_of_the_sea" : "minecraft:book";
            };
            String materialName = material.startsWith("minecraft:") ? material.substring("minecraft:".length()) : material;
            Material mat = Material.matchMaterial(materialName, true);
            if (mat == null || mat.isAir()) {
                return "";
            }
            stack = new ItemStack(mat, 1);
        }
        // 覆盖设置节点显示名和 lore（天赋树 UI 显示天赋信息，不是物品原始信息）
        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add(net.md_5.bungee.api.ChatColor.of("#B0B8C0") + ChatColor.stripColor(node.description()));
        // 效果行：普通天赋（单效果）和联动天赋（多效果）统一用中文格式化
        lore.add("");
        if (node.effectType() != null && !node.effectType().isBlank()) {
            // 普通天赋：单效果
            lore.add(net.md_5.bungee.api.ChatColor.of("#5BC0EB") + formatEffect(node.effectType(), node.valueAtLevel(1)));
        }
        for (xuanmo.arcartxsuite.fishing.talent.TalentEffect effect : node.effects()) {
            // 联动天赋：多效果，统一中文格式化
            lore.add(net.md_5.bungee.api.ChatColor.of("#5BC0EB") + formatEffect(effect.effectType(), effect.effectValue()));
        }
        lore.add("");
        if (unlocked) {
            lore.add(net.md_5.bungee.api.ChatColor.of("#2ECC71") + "✔ 已激活");
        } else if (canLevelUp) {
            lore.add(net.md_5.bungee.api.ChatColor.of("#F1C40F") + "点击激活");
        } else {
            lore.add(net.md_5.bungee.api.ChatColor.of("#7F8C8D") + "尚未解锁");
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(net.md_5.bungee.api.ChatColor.of("#F0F4F8") + ChatColor.stripColor(node.displayName()));
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        return iconResolver.itemToPreviewJson(stack);
    }

    /**
     * 推送任务数据到 UI。
     */
    public void pushTasksData(@NotNull Player player, @NotNull String uiId) {
        if (!uiQueries.canView(player, uiId) || !AuthAccess.admitted(player.getUniqueId())) return;
        // 确保玩家任务已分配（模块重载或玩家在线时模块加载会导致缓存为空）
        if (!taskEngine.prepareTasks(player.getUniqueId(), () -> pushTasksData(player, uiId))) return;
        java.util.List<xuanmo.arcartxsuite.fishing.task.PlayerTaskInstance> allTasks = taskEngine.getAllTasks(player.getUniqueId());

        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("packetId", "AXS_FISHING_TASKS");

        // 按分类组织任务
        java.util.Map<String, Object> dailyMap = new java.util.LinkedHashMap<>();
        java.util.Map<String, Object> weeklyMap = new java.util.LinkedHashMap<>();
        java.util.Map<String, Object> seasonMap = new java.util.LinkedHashMap<>();
        int dailyIdx = 0, weeklyIdx = 0, seasonIdx = 0;
        for (xuanmo.arcartxsuite.fishing.task.PlayerTaskInstance instance : allTasks) {
            xuanmo.arcartxsuite.fishing.task.FishingTask template = taskEngine.findTemplate(
                instance.templateId(), instance.category());
            if (template == null) continue;
            java.util.Map<String, Object> entry = new java.util.LinkedHashMap<>();
            entry.put("id", instance.templateId());
            entry.put("name", template.displayName());
            entry.put("description", template.description());
            entry.put("category", instance.category().name());
            entry.put("progress", instance.currentProgress());
            entry.put("target", instance.targetCount());
            entry.put("completed", instance.completed());
            entry.put("percent", (int) instance.progressPercent());
            entry.put("xpReward", template.totalXpReward());

            switch (instance.category()) {
                case DAILY -> dailyMap.put(Integer.toString(dailyIdx++), entry);
                case WEEKLY -> weeklyMap.put(Integer.toString(weeklyIdx++), entry);
                case SEASON -> seasonMap.put(Integer.toString(seasonIdx++), entry);
            }
        }
        payload.put("daily", dailyMap);
        payload.put("weekly", weeklyMap);
        payload.put("season", seasonMap);

        sendUiInitPacket(player, uiId, payload);
    }

    /**
     * 推送饵料管理 UI 数据。
     * 包含：饵料槽数量、当前队列内容（含物品图标 JSON）、可用饵料定义列表。
     */
    public void pushBaitsData(@NotNull Player player, @NotNull String uiId) {
        if (pendingBaitEdits.contains(player.getUniqueId())) return;
        uiQueries.read(player, uiId,
            uuid -> new BaitsViewData(baitSlotBonus(getSeasonData(uuid)), repository.loadBaitQueue(uuid)),
            data -> renderBaitsData(player, uiId, data));
    }

    private record BaitsViewData(int bonusSlots, List<BaitQueueEntry> queue) {}

    private void renderBaitsData(Player player, String uiId, BaitsViewData data) {
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("packetId", "AXS_FISHING_BAITS");
        payload.put("baitSlots", 1 + data.bonusSlots());
        payload.put("baitSlotsBase", 1);
        payload.put("baitSlotsBonus", data.bonusSlots());

        // 队列条目
        java.util.Map<String, Object> queueMap = new java.util.LinkedHashMap<>();
        for (BaitQueueEntry entry : data.queue()) {
            BaitDefinition bait = findBait(entry.baitId());
            java.util.Map<String, Object> slotData = new java.util.LinkedHashMap<>();
            slotData.put("slotIndex", entry.slotIndex());
            slotData.put("baitId", entry.baitId());
            slotData.put("amount", entry.amount());
            slotData.put("displayName", bait != null ? bait.displayName() : entry.baitId());
            queueMap.put(Integer.toString(entry.slotIndex()), slotData);

            // 生成饵料物品图标 JSON（itemJson 分离模式，key = queueIconJson{slotIndex}）
            if (bait != null) {
                String json = iconResolver.resolveItemJson(player, bait.itemRef());
                if (json != null && !json.isEmpty()) {
                    payload.put("queueIconJson" + entry.slotIndex(), json);
                }
            }
        }
        payload.put("queue", queueMap);

        // 可用饵料定义列表（供 UI 显示图标）
        java.util.Map<String, Object> baitsMap = new java.util.LinkedHashMap<>();
        for (BaitDefinition bait : configuration.baits()) {
            java.util.Map<String, Object> baitData = new java.util.LinkedHashMap<>();
            baitData.put("id", bait.id());
            baitData.put("displayName", bait.displayName());
            baitData.put("isDefault", bait.isDefault());
            baitsMap.put(bait.id(), baitData);
        }
        payload.put("baits", baitsMap);

        sendUiInitPacket(player, uiId, payload);
    }

    public void submitRewardClaim(Player player, String uiId, String token, String rewardId) {
        if (!beginWriteAction(player, uiId, token)) return;
        AuthSnapshot admission = AuthAccess.snapshot(player.getUniqueId());
        claimReward(player, rewardId).whenComplete((accepted, failure) -> onMain(() -> {
            if (Bukkit.getPlayer(player.getUniqueId()) != player) return;
            finishWriteAction(player);
            if (!sameRewardSession(admission) || !uiQueries.canView(player, uiId)) return;
            player.sendMessage(failure == null && Boolean.TRUE.equals(accepted)
                ? "§a奖励已进入待发队列。" : "§c无法确认领取奖励: " + rewardId);
            pushRewardsData(player, uiId);
        }));
    }
}
