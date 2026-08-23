package xuanmo.arcartxsuite.fishing;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.AbstractAXSModule;
import xuanmo.arcartxsuite.api.ClientPacketHandler;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.ModuleDescriptor;
import xuanmo.arcartxsuite.api.UiBinding;
import xuanmo.arcartxsuite.api.capability.DatabaseMigratable;
import xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable;
import xuanmo.arcartxsuite.api.config.SyncPolicy;
import xuanmo.arcartxsuite.api.config.ValidationRule;
import xuanmo.arcartxsuite.api.config.ValueType;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.fishing.command.FishingAdminCommand;
import xuanmo.arcartxsuite.fishing.command.FishingPlayerCommand;
import xuanmo.arcartxsuite.fishing.config.FishingModuleConfiguration;
import xuanmo.arcartxsuite.fishing.config.FatigueConfig;
import xuanmo.arcartxsuite.fishing.fatigue.FatigueEngine;
import xuanmo.arcartxsuite.fishing.listener.FishingListener;
import xuanmo.arcartxsuite.fishing.model.FishingPendingReward;
import xuanmo.arcartxsuite.fishing.packet.FishingPacketHandler;
import xuanmo.arcartxsuite.fishing.placeholder.FishingPlaceholderExpansion;
import xuanmo.arcartxsuite.api.icon.IconResolver;
import xuanmo.arcartxsuite.api.config.ModuleConfig;
import xuanmo.arcartxsuite.api.ModuleUiSpec;
import xuanmo.arcartxsuite.api.PacketHandlerSpec;
import xuanmo.arcartxsuite.fishing.reward.RewardDispatcher;
import xuanmo.arcartxsuite.fishing.reward.RewardLoader;
import xuanmo.arcartxsuite.fishing.reward.SeasonRewardDefinition;

import xuanmo.arcartxsuite.fishing.service.FishingService;
import xuanmo.arcartxsuite.fishing.settlement.SettlementDefinition;
import xuanmo.arcartxsuite.fishing.settlement.SettlementEngine;
import xuanmo.arcartxsuite.fishing.settlement.SettlementLoader;
import xuanmo.arcartxsuite.fishing.storage.FishingRepository;
import xuanmo.arcartxsuite.fishing.storage.JdbcFishingRepository;
import xuanmo.arcartxsuite.fishing.talent.TalentEngine;
import xuanmo.arcartxsuite.fishing.talent.TalentLoader;
import xuanmo.arcartxsuite.fishing.task.FishingTask;
import xuanmo.arcartxsuite.fishing.task.TaskEngine;
import xuanmo.arcartxsuite.fishing.task.TaskLoader;

/**
 * 钓鱼模块的入口类。
 * <p>
 * 关联模块：{@code fishing}（id），版本 {@code 1.0.0}。
 * 负责加载配置、初始化 {@link xuanmo.arcartxsuite.fishing.service.FishingService}、
 * 注册 UI（小游戏界面与图鉴界面）、注册命令、事件监听器与 PlaceholderAPI 扩展，
 * 并向外暴露 {@link xuanmo.arcartxsuite.api.capability.DatabaseMigratable} 与
 * {@link xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable} 能力。
 */
public final class FishingModule extends AbstractAXSModule implements ModuleCommandHandler {

    private static final String MAIN_UI_RESOURCE = "arcartx/ui/fishing_main.yml";
    private static final String MAIN_UI_FILE = "ui/fishing_main.yml";
    private static final String MINIGAME_UI_RESOURCE = "arcartx/ui/fishing_minigame.yml";
    private static final String MINIGAME_UI_FILE = "ui/fishing_minigame.yml";
    private static final String COLLECTION_UI_RESOURCE = "arcartx/ui/fishing_collection.yml";
    private static final String COLLECTION_UI_FILE = "ui/fishing_collection.yml";
    private static final String TALENTS_UI_RESOURCE = "arcartx/ui/fishing_talents.yml";
    private static final String TALENTS_UI_FILE = "ui/fishing_talents.yml";
    private static final String REWARDS_UI_RESOURCE = "arcartx/ui/fishing_rewards.yml";
    private static final String REWARDS_UI_FILE = "ui/fishing_rewards.yml";
    private static final String TASKS_UI_RESOURCE = "arcartx/ui/fishing_tasks.yml";
    private static final String TASKS_UI_FILE = "ui/fishing_tasks.yml";

    private FishingModuleConfiguration configuration;
    private FishingService service;
    private FishingAdminCommand adminCommand;
    private String mainUiId;
    private String minigameUiId;
    private String collectionUiId;
    private String talentsUiId;
    private String rewardsUiId;
    private String tasksUiId;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("fishing")
            .name("Fishing")
            .version("1.4.4")
            .mainClass(getClass().getName())
            .build();
    }

    @Override
    protected @NotNull ModuleConfig configSpec() {
        return ModuleConfig.builder()
            .configFileName("ArcartXFishing.yml")
            .messagesFileName("messages.yml")
            .syncPolicy(SyncPolicy.builder()
                    .dynamicSection("fishes")
                    .dynamicSection("treasures")
                .build())
            .currentVersion(5)
            .validations(List.of(
                    ValidationRule.required("storage.shared", ValueType.BOOLEAN),
                    ValidationRule.required("fishing.replace-vanilla", ValueType.BOOLEAN),
                    ValidationRule.required("fishing.minigame-tick-interval", ValueType.INT)
                        .withRange(1, 20)
            ))
            .build();
    }

    @Override
    protected @NotNull ModuleUiSpec uiSpec() {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put(MAIN_UI_RESOURCE, MAIN_UI_FILE);
        mappings.put(MINIGAME_UI_RESOURCE, MINIGAME_UI_FILE);
        mappings.put(COLLECTION_UI_RESOURCE, COLLECTION_UI_FILE);
        mappings.put(TALENTS_UI_RESOURCE, TALENTS_UI_FILE);
        mappings.put(REWARDS_UI_RESOURCE, REWARDS_UI_FILE);
        mappings.put(TASKS_UI_RESOURCE, TASKS_UI_FILE);
        return new ModuleUiSpec(mappings, configuration != null && configuration.ui().overwriteUiFiles());
    }

    @Override
    protected @NotNull PacketHandlerSpec createPacketHandlerSpec() {
        if (service == null) return PacketHandlerSpec.NONE;
        return PacketHandlerSpec.of(new FishingPacketHandler(
            player -> {
                if (service == null) return null;
                return service.getActiveMinigame(player.getUniqueId());
            },
            () -> service,
            () -> mainUiId,
            () -> collectionUiId,
            () -> talentsUiId,
            () -> rewardsUiId,
            () -> tasksUiId
        ), 0, null, null);
    }

    @Override
    protected void loadConfiguration(@Nullable File configFile) throws Exception {
        if (configFile == null) {
            throw new IllegalStateException("ArcartXFishing.yml 配置文件缺失");
        }
        // 在加载配置前，确保 fishes/baits/rods/treasures 四个定义目录存在默认文件。
        // 仅当目录为空或不存在时从 jar 导出，不覆盖用户已修改/自定义的文件。
        ensureDefinitionDefaults();
        configuration = FishingModuleConfiguration.load(
            YamlConfiguration.loadConfiguration(configFile), dataFolder, logger, ariaBridge);
    }

    /**
     * 确保 fishes/baits/rods/treasures 四个定义目录存在默认文件。
     * <p>
     * 仅当对应目录不存在或为空时，从模块 jar 中导出内置默认文件；
     * 用户已存在或自定义的文件不会被覆盖。
     */
    private void ensureDefinitionDefaults() {
        ensureDefinitionDir("fishes", new String[]{
            "fishes/catfish.yml", "fishes/legendary_carp.yml", "fishes/pufferfish.yml",
            "fishes/salmon.yml", "fishes/sea_bass.yml", "fishes/tuna.yml",
            "fishes/fatigue_ghost_fish.yml", "fishes/fatigue_shadow_carp.yml", "fishes/fatigue_abyssal_eel.yml"
        });
        ensureDefinitionDir("baits", new String[]{
            "baits/bread.yml", "baits/corn.yml", "baits/worm.yml"
        });
        ensureDefinitionDir("rods", new String[]{
            "rods/wooden_rod.yml"
        });
        ensureDefinitionDir("treasures", new String[]{
            "treasures/ancient_coin.yml", "treasures/custom_treasure_map.yml", "treasures/lost_ring.yml",
            "treasures/mmo_aquamarine.yml", "treasures/mythic_gem.yml", "treasures/neige_rune.yml",
            "treasures/old_boot.yml", "treasures/overture_legendary_rod.yml"
        });
        ensureDefinitionDir("waters", new String[]{
            "waters/water-default.yml", "waters/water-dragon_lake.yml", "waters/water-jade_river.yml"
        });
    }

    /**
     * 确保指定定义目录存在默认文件。
     * <p>
     * 当目录不存在或不含任何 .yml 文件时，从 jar 导出 defaults 列表中的所有文件；
     * 已存在的文件不会被覆盖（exportResource 走 Files.copy，目标存在时会抛异常被捕获并跳过）。
     *
     * @param dirName  dataFolder 下的子目录名（如 "fishes"）
     * @param defaults jar 内资源路径数组（如 {"fishes/catfish.yml", ...}）
     */
    private void ensureDefinitionDir(@NotNull String dirName, @NotNull String[] defaults) {
        File dir = new File(dataFolder, dirName);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File[] existing = dir.listFiles((d, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (existing != null && existing.length > 0) {
            // 用户已有定义文件，不导出默认值
            return;
        }
        for (String res : defaults) {
            String fileName = res.substring(res.lastIndexOf('/') + 1);
            File target = new File(dir, fileName);
            if (!target.exists()) {
                exportResource(res, target);
            }
        }
    }

    @Override
    protected List<String> publishedTopics() {
        return List.of("axs.fishing.success", "axs.fishing.perfect", "axs.fishing.collection_unlock", "axs.fishing.treasure");
    }

    @Override
    protected void startService() throws Exception {
        // 共享模式：使用本体统一数据源，模块只需提供表前缀
        // 若模块配置了独立 storage 节（mode=mysql 等），则走自建模式（向后兼容）
        JdbcFishingRepository repo;
        xuanmo.arcartxsuite.api.storage.StorageDescriptor fishDesc;
        javax.sql.DataSource fishDs;
        if (configuration.storage().hasOverride()) {
            fishDesc = configuration.storage().toDescriptor();
            fishDs = storageManager.resolveModuleDataSource("fishing", null, dataFolder, fishDesc);
        } else {
            fishDesc = storageManager.getDescriptor().withTablePrefix("");
            fishDs = storageManager.resolveModuleDataSource("fishing",
                configuration.storage().sqliteFileName(), dataFolder, null);
        }
        repo = new JdbcFishingRepository(fishDs, fishDesc, dataFolder, logger);

        // 注册 UI 并获取 UI ID
        UiBinding binding0 = registerModuleUi(
            MAIN_UI_FILE,
            configuration.ui().mainId(),
            configuration.ui().registerOnEnable()
        );
        if (binding0.registeredUiId() != null) {
            mainUiId = binding0.registeredUiId();
        } else {
            mainUiId = configuration.ui().mainId();
        }

        UiBinding binding1 = registerModuleUi(
            MINIGAME_UI_FILE,
            configuration.ui().minigameId(),
            configuration.ui().registerOnEnable()
        );
        if (binding1.registeredUiId() != null) {
            minigameUiId = binding1.registeredUiId();
        } else {
            minigameUiId = configuration.ui().minigameId();
        }

        UiBinding binding2 = registerModuleUi(
            COLLECTION_UI_FILE,
            configuration.ui().collectionId(),
            configuration.ui().registerOnEnable()
        );
        if (binding2.registeredUiId() != null) {
            collectionUiId = binding2.registeredUiId();
        } else {
            collectionUiId = configuration.ui().collectionId();
        }

        // 注册天赋树/奖励/任务 UI
        UiBinding binding3 = registerModuleUi(TALENTS_UI_FILE, configuration.ui().talentsId(), configuration.ui().registerOnEnable());
        talentsUiId = binding3.registeredUiId() != null ? binding3.registeredUiId() : configuration.ui().talentsId();
        UiBinding binding4 = registerModuleUi(REWARDS_UI_FILE, configuration.ui().rewardsId(), configuration.ui().registerOnEnable());
        rewardsUiId = binding4.registeredUiId() != null ? binding4.registeredUiId() : configuration.ui().rewardsId();
        UiBinding binding5 = registerModuleUi(TASKS_UI_FILE, configuration.ui().tasksId(), configuration.ui().registerOnEnable());
        tasksUiId = binding5.registeredUiId() != null ? binding5.registeredUiId() : configuration.ui().tasksId();

        // 加载天赋树配置
        TalentEngine talentEngine;
        File talentsFile = new File(dataFolder, "talents.yml");
        if (!talentsFile.exists()) {
            // 导出默认 talents.yml
            try {
                java.io.InputStream is = getClass().getClassLoader().getResourceAsStream("talents.yml");
                if (is != null) {
                    java.nio.file.Files.copy(is, talentsFile.toPath());
                    is.close();
                }
            } catch (Exception e) {
                logger.warning("导出 talents.yml 失败: " + e.getMessage());
            }
        }
        if (talentsFile.exists()) {
            talentEngine = TalentLoader.load(
                YamlConfiguration.loadConfiguration(talentsFile), logger);
        } else {
            talentEngine = TalentLoader.load(
                new YamlConfiguration(), logger);
        }

        // 加载等级奖励配置
        List<SeasonRewardDefinition> rewards;
        File rewardsFile = new File(dataFolder, "rewards.yml");
        if (!rewardsFile.exists()) {
            exportResource("rewards.yml", rewardsFile);
        }
        if (rewardsFile.exists()) {
            rewards = RewardLoader.load(YamlConfiguration.loadConfiguration(rewardsFile), logger);
        } else {
            rewards = java.util.List.of();
        }

        // 加载赛季结算配置
        List<SettlementDefinition> settlements;
        File settlementFile = new File(dataFolder, "settlement.yml");
        if (!settlementFile.exists()) {
            exportResource("settlement.yml", settlementFile);
        }
        if (settlementFile.exists()) {
            settlements = SettlementLoader.load(YamlConfiguration.loadConfiguration(settlementFile), logger);
        } else {
            settlements = java.util.List.of();
        }

        IconResolver iconResolver = new IconResolver(itemStackBridge, itemSourceRegistry);
        RewardDispatcher rewardDispatcher = new RewardDispatcher(
            pendingRewardService, itemSourceRegistry, iconResolver, () -> currencyManager,
            () -> getCapability(xuanmo.arcartxsuite.api.capability.MailDispatchable.class),
            itemRewardDispatcher,
            logger, configuration.season().seasonId());
        SettlementEngine settlementEngine = new SettlementEngine(
            settlements, ariaBridge, repo, rewardDispatcher, logger, configuration.season().seasonId());

        // 加载任务配置
        File tasksDir = new File(dataFolder, "tasks");
        if (!tasksDir.exists()) {
            tasksDir.mkdirs();
            exportResource("tasks/daily.yml", new File(tasksDir, "daily.yml"));
            exportResource("tasks/weekly.yml", new File(tasksDir, "weekly.yml"));
            exportResource("tasks/season.yml", new File(tasksDir, "season.yml"));
        }
        List<FishingTask> dailyTasks = loadTasksFromDir(tasksDir, "daily.yml", FishingTask.TaskCategory.DAILY);
        List<FishingTask> weeklyTasks = loadTasksFromDir(tasksDir, "weekly.yml", FishingTask.TaskCategory.WEEKLY);
        List<FishingTask> seasonTasks = loadTasksFromDir(tasksDir, "season.yml", FishingTask.TaskCategory.SEASON);

        TaskEngine taskEngine = new TaskEngine(dailyTasks, weeklyTasks, seasonTasks,
            3, 3, () -> getCapability(xuanmo.arcartxsuite.api.capability.EventBusCapability.class),
            () -> service, logger);

        // 加载疲惫值配置
        FatigueConfig fatigueConfig;
        File fatigueFile = new File(dataFolder, "fatigue.yml");
        if (!fatigueFile.exists()) {
            exportResource("fatigue.yml", fatigueFile);
        }
        if (fatigueFile.exists()) {
            fatigueConfig = FatigueConfig.load(YamlConfiguration.loadConfiguration(fatigueFile));
        } else {
            fatigueConfig = FatigueConfig.disabled();
        }

        // 创建疲惫值引擎
        FatigueEngine fatigueEngine = new FatigueEngine(
            plugin, fatigueConfig, repo, logger, configuration.season().seasonId(),
            iconResolver, () -> currencyManager);

        service = new FishingService(
            plugin, configuration, repo,
            packetBridge,
            iconResolver,
            logger, minigameUiId, talentEngine, fatigueEngine, rewards, rewardDispatcher, settlementEngine, taskEngine,
            () -> getCapability(xuanmo.arcartxsuite.api.capability.MailDispatchable.class),
            crossServer,
            ariaBridge);
        service.setMessageProvider(messages());
        service.setEventBusProvider(() -> getCapability(
            xuanmo.arcartxsuite.api.capability.EventBusCapability.class));
        service.setSignalProvider(() -> getCapability(
            xuanmo.arcartxsuite.api.capability.SignalDispatchable.class));
        service.setCurrencyProvider(() -> currencyManager);
        service.setTitleProvider(() -> getCapability(
            xuanmo.arcartxsuite.api.capability.TitleGrantable.class));
        service.start();

        // 启动疲惫值定时恢复任务
        fatigueEngine.start();

        // 迁移旧的 fishing pending 奖励到统一 PendingRewardService
        migrateLegacyPendingRewards(repo);

        // 启动任务引擎（需要 eventBusProvider 已设置）
        taskEngine.start();

        registerCapability(DatabaseMigratable.class, new DatabaseMigratable() {
            @Override public @NotNull String moduleId() { return "fishing"; }
            @Override public @NotNull xuanmo.arcartxsuite.api.storage.MigrationResult migrateDatabase(
                    @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor target, boolean overwrite) {
                return repo.migrateData(target, overwrite);
            }
            @Override public @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor currentDescriptor() {
                return repo.getDescriptor();
            }
        });

        registerCapability(PlayerDataPurgeable.class, new PlayerDataPurgeable() {
            @Override public @NotNull String moduleId() { return "fishing"; }
            @Override public int purgePlayerData(@NotNull java.util.UUID playerUuid) {
                try { return repo.deletePlayerData(playerUuid); }
                catch (Exception e) { logger.warning("Fishing purge 失败: " + e.getMessage()); return -1; }
            }
            @Override public int purgeAllPlayerData() {
                try { return repo.deleteAllPlayerData(); }
                catch (Exception e) { logger.warning("Fishing purgeAll 失败: " + e.getMessage()); return -1; }
            }
        });

        adminCommand = new FishingAdminCommand(() -> service, this::msg,
            () -> getCapability(xuanmo.arcartxsuite.api.selection.PolygonSelectionCapability.class),
            dataFolder,
            () -> {
                // 重新加载配置（包括水域文件）
                File configFile = new File(dataFolder, "ArcartXFishing.yml");
                try {
                    loadConfiguration(configFile);
                } catch (Exception e) {
                    throw new RuntimeException("重载钓鱼配置失败: " + e.getMessage(), e);
                }
            });
        logger.fine("Fishing 模块已载入 | 鱼种=" + configuration.fishes().size()
            + " | 替换原版=" + configuration.fishing().replaceVanilla());
    }

    @Override
    protected void stopService() {
        if (service != null) {
            // 关闭疲惫值定时任务
            FatigueEngine fatigueEngine = service.fatigueEngine();
            if (fatigueEngine != null) fatigueEngine.shutdown();
            service.shutdown();
            service = null;
        }
        storageManager.closeModuleDataSource("fishing");
        configuration = null;
    }

    @Override
    protected @NotNull List<org.bukkit.event.Listener> createListeners() {
        return service != null ? List.of(new FishingListener(service)) : List.of();
    }

    @Override
    protected @NotNull Map<String, TabExecutor> commandBindings() {
        return Map.of("fishing", new FishingPlayerCommand(
            () -> packetBridge,
            () -> mainUiId,
            () -> collectionUiId,
            () -> talentsUiId,
            () -> rewardsUiId,
            () -> tasksUiId,
            () -> service,
            this::msg
        ));
    }

    @Override
    protected @Nullable Object createPlaceholderExpansion() {
        return new FishingPlaceholderExpansion(plugin, () -> service);
    }

    // ─── ModuleCommandHandler ─────────────────────────────────

    @Override public String commandId() { return "fishing"; }

    @Override public List<String> actions() {
        return adminCommand != null ? adminCommand.actions() : List.of("help", "stats");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        return adminCommand != null && adminCommand.onCommand(sender, label, args);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return adminCommand != null ? adminCommand.onTabComplete(sender, args) : null;
    }

    private String msg(String key, Object... args) {
        MessageProvider mp = messages();
        if (mp == null) return key;
        String prefix = mp.get("prefix");
        return prefix + mp.get(key, args);
    }

    /** 从 jar 资源导出默认配置文件。 */
    private void exportResource(@NotNull String resourcePath, @NotNull File targetFile) {
        try (java.io.InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is != null) {
                java.nio.file.Files.copy(is, targetFile.toPath());
            }
        } catch (Exception e) {
            logger.warning("导出 " + resourcePath + " 失败: " + e.getMessage());
        }
    }

    /** 从 tasks/ 目录加载指定任务文件。 */
    private java.util.List<FishingTask> loadTasksFromDir(@NotNull File dir, @NotNull String fileName,
                                                          @NotNull FishingTask.TaskCategory category) {
        File file = new File(dir, fileName);
        if (!file.exists()) return java.util.List.of();
        return TaskLoader.load(YamlConfiguration.loadConfiguration(file), category, logger);
    }

    /**
     * 迁移旧的 fishing pending 奖励到统一 PendingRewardService。
     * <p>
     * 旧版本使用 axs_fishing_pending_rewards 表存储离线玩家的待发放奖励，
     * 新版本改用宿主的 PendingRewardService 统一管理。此方法在启动时
     * 扫描旧表，将未发放的奖励迁移到 PendingRewardService，然后清空旧表。
     * <p>
     * 迁移是幂等的：如果旧表为空或不存在，直接返回。
     */
    private void migrateLegacyPendingRewards(JdbcFishingRepository repo) {
        if (pendingRewardService == null) {
            logger.fine("PendingRewardService 不可用，跳过旧 pending 数据迁移");
            return;
        }
        try {
            // 获取所有有待发放奖励的玩家 UUID
            java.util.List<java.util.UUID> playerUuids = repo.loadAllPendingPlayerUuids();
            if (playerUuids.isEmpty()) {
                return;
            }
            int migrated = 0;
            for (java.util.UUID uuid : playerUuids) {
                java.util.List<FishingPendingReward> rewards = repo.loadPendingRewards(uuid);
                for (FishingPendingReward reward : rewards) {
                    // 根据奖励类型转换为 PendingRewardService 格式
                    xuanmo.arcartxsuite.api.item.PendingRewardService.PendingReward pending;
                    String type = reward.rewardType().toUpperCase(java.util.Locale.ROOT);
                    if ("ITEM".equals(type) && reward.itemData() != null && !reward.itemData().isBlank()) {
                        pending = xuanmo.arcartxsuite.api.item.PendingRewardService.PendingReward.itemSerialized(
                            uuid, "fishing", reward.itemData(),
                            reward.description() != null ? reward.description() : "legacy-pending");
                    } else if ("CURRENCY".equals(type) && reward.currencyId() != null && !reward.currencyId().isBlank()) {
                        pending = xuanmo.arcartxsuite.api.item.PendingRewardService.PendingReward.currency(
                            uuid, "fishing", reward.currencyId(), reward.amount(),
                            reward.description() != null ? reward.description() : "legacy-pending");
                    } else if ("COMMAND".equals(type) && reward.command() != null && !reward.command().isBlank()) {
                        pending = xuanmo.arcartxsuite.api.item.PendingRewardService.PendingReward.command(
                            uuid, "fishing", java.util.List.of(reward.command()),
                            reward.description() != null ? reward.description() : "legacy-pending");
                    } else {
                        logger.warning("跳过无法识别的旧 pending 奖励: id=" + reward.id() + " player=" + uuid
                            + " type=" + type);
                        continue;
                    }
                    pendingRewardService.savePending(pending);
                    repo.deletePendingReward(reward.id());
                    migrated++;
                }
            }
            if (migrated > 0) {
                logger.info("已迁移 " + migrated + " 条旧 fishing pending 奖励到统一 PendingRewardService");
            }
        } catch (Exception e) {
            logger.warning("迁移旧 fishing pending 奖励失败: " + e.getMessage());
        }
    }
}

