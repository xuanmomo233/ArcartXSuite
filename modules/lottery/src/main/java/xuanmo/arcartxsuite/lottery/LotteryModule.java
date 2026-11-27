package xuanmo.arcartxsuite.lottery;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import xuanmo.arcartxsuite.api.auth.AuthAdmissionEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.AbstractAXSModule;
import xuanmo.arcartxsuite.api.ClientPacketHandler;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.ModuleDescriptor;
import xuanmo.arcartxsuite.api.UiBinding;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.capability.DatabaseMigratable;
import xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable;
import xuanmo.arcartxsuite.api.config.SyncPolicy;
import xuanmo.arcartxsuite.api.config.ValidationRule;
import xuanmo.arcartxsuite.api.config.ValueType;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.api.config.ModuleConfig;
import xuanmo.arcartxsuite.api.ModuleUiSpec;
import xuanmo.arcartxsuite.api.PacketHandlerSpec;
import xuanmo.arcartxsuite.lottery.command.LotteryAdminCommand;
import xuanmo.arcartxsuite.lottery.command.LotteryPlayerCommand;
import xuanmo.arcartxsuite.lottery.config.LotteryModuleConfiguration;
import xuanmo.arcartxsuite.lottery.packet.LotteryPacketHandler;
import xuanmo.arcartxsuite.lottery.storage.JdbcLotteryRepository;

/**
 * Lottery 模块主类。
 * <p>
 * 继承 {@link AbstractAXSModule}，负责配置加载、服务启动/停止、
 * 命令绑定、PlaceholderAPI 注册、客户端包处理器创建以及
 * 数据库迁移与玩家数据清除能力的注册。
 */
public final class LotteryModule extends AbstractAXSModule implements ModuleCommandHandler {
    private JdbcLotteryRepository repository;

    private static final String GACHA_UI_RESOURCE = "arcartx/ui/lottery_gacha.yml";
    private static final String GACHA_UI_FILE = "ui/lottery/lottery_gacha.yml";
    private static final String WISH_UI_RESOURCE = "arcartx/ui/lottery_wish.yml";
    private static final String WISH_UI_FILE = "ui/lottery/lottery_wish.yml";
    private static final String CASE_UI_RESOURCE = "arcartx/ui/lottery_case.yml";
    private static final String CASE_UI_FILE = "ui/lottery/lottery_case.yml";

    private LotteryModuleConfiguration configuration;
    private volatile LotteryService service;
    private LotteryAdminCommand adminCommand;
    private xuanmo.arcartxsuite.api.storage.ModuleStorage storageScope;
    private xuanmo.arcartxsuite.api.scheduler.ModuleCallback calls;
    private LotteryPacketHandler packetHandler;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("lottery")
            .name("Lottery")
            .version("3.3.7")
            .mainClass(getClass().getName())
            .build();
    }

    @Override
    protected @NotNull ModuleConfig configSpec() {
        return ModuleConfig.builder()
            .configFileName("ArcartXLottery.yml")
            .messagesFileName("messages.yml")
            .syncPolicy(SyncPolicy.builder()
                    .dynamicSection("messages")
                    .dynamicSection("shared-pity-groups")
                .build())
            .currentVersion(3)
            .validations(List.of(
                    ValidationRule.required("storage.shared", ValueType.BOOLEAN),
                    ValidationRule.required("pools-directory", ValueType.STRING)
            ))
            .build();
    }

    @Override
    protected @NotNull ModuleUiSpec uiSpec() {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put(GACHA_UI_RESOURCE, GACHA_UI_FILE);
        mappings.put(WISH_UI_RESOURCE, WISH_UI_FILE);
        mappings.put(CASE_UI_RESOURCE, CASE_UI_FILE);
        return new ModuleUiSpec(mappings, configuration != null && configuration.overwriteUiFiles());
    }

    @Override
    protected @NotNull PacketHandlerSpec createPacketHandlerSpec() {
        if (service == null || packetBridge == null || !packetBridge.isAvailable()) {
            return PacketHandlerSpec.NONE;
        }
        xuanmo.arcartxsuite.api.icon.IconResolver iconResolver =
            new xuanmo.arcartxsuite.api.icon.IconResolver(itemStackBridge, itemSourceRegistry);
        UiBinding gachaBinding = getUiBinding(GACHA_UI_FILE);
        UiBinding wishBinding = getUiBinding(WISH_UI_FILE);
        UiBinding caseBinding = getUiBinding(CASE_UI_FILE);
        // 优先使用配置指定的 UI ID，binding 仅作为 fallback
        String configuredGachaUiId = configuration.ui().gachaUiId();
        String gachaUiId;
        if (configuredGachaUiId != null && !configuredGachaUiId.isBlank()) {
            gachaUiId = configuredGachaUiId;
        } else if (gachaBinding != null) {
            gachaUiId = gachaBinding.runtimeUiId();
        } else {
            gachaUiId = "AXS:lottery_gacha";
        }
        String configuredWishUiId = configuration.ui().wishUiId();
        String wishUiId;
        if (configuredWishUiId != null && !configuredWishUiId.isBlank()) {
            wishUiId = configuredWishUiId;
        } else if (wishBinding != null) {
            wishUiId = wishBinding.runtimeUiId();
        } else {
            wishUiId = "AXS:lottery_wish";
        }
        String caseUiId = caseBinding != null ? caseBinding.runtimeUiId() : configuration.ui().caseUiId();
        var handler = new LotteryPacketHandler(service, packetBridge, iconResolver, plugin, gachaUiId, wishUiId, caseUiId);
        packetHandler = handler;
        var runtime = calls;
        return PacketHandlerSpec.of((player, id, data) -> runtime.call(() -> handler.handleClientPacket(player, id, data), true),
            0, LotteryPacketHandler.GACHA, "lottery",
            LotteryPacketHandler.WISH, LotteryPacketHandler.CASE);
    }

    @Override
    protected void loadConfiguration(@Nullable File configFile) throws Exception {
        if (configFile == null) {
            throw new IllegalStateException("ArcartXLottery.yml 配置文件缺失");
        }
        var yaml = YamlConfiguration.loadConfiguration(configFile);
        String poolsDir = yaml.getString("pools-directory", "lottery/pools");
        File poolsDirectory = new File(dataFolder, poolsDir);
        ensurePoolDefaults(poolsDir);
        configuration = LotteryModuleConfiguration.load(yaml, dataFolder, logger);
    }

    private void ensurePoolDefaults(String poolsRelative) {
        File poolsDir = new File(dataFolder, poolsRelative);
        if (!poolsDir.exists()) {
            poolsDir.mkdirs();
        }
        File[] existing = poolsDir.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (existing != null && existing.length > 0) {
            return;
        }
        for (String poolFile : new String[]{"character_event_1.yml", "weapon_event_1.yml", "default_weapon_case.yml"}) {
            File target = new File(poolsDir, poolFile);
            if (!target.exists()) {
                exportResource("lottery/pools/" + poolFile, target, false);
            }
        }
    }

    @Override
    protected java.util.concurrent.CompletionStage<Void> prepareService() throws Exception {
        // 共享模式：使用本体统一数据源，模块只需提供表前缀
        // 若模块配置了独立 storage 节（mode=mysql 等），则走自建模式（向后兼容）
        xuanmo.arcartxsuite.api.storage.StorageDescriptor lotteryDesc;
        if (configuration.storage().hasOverride()) {
            lotteryDesc = configuration.storage().toDescriptor()
                .withTablePrefix(configuration.storage().tablePrefix());
            storageScope = storageManager.openModuleStorage("lottery", null, dataFolder, lotteryDesc);
        } else {
            lotteryDesc = storageManager.getDescriptor()
                .withTablePrefix(configuration.storage().tablePrefix());
            storageScope = storageManager.openModuleStorage("lottery",
                configuration.storage().sqliteFileName(), dataFolder, null);
        }
        repository = new JdbcLotteryRepository(storageScope.dataSource(), lotteryDesc, dataFolder, logger);
        return storageScope.initialize(repository::initialize);
    }

    @Override
    protected void startService() throws Exception {
        var repo = repository;
        var runtime = new xuanmo.arcartxsuite.api.scheduler.ModuleCallback(() -> {});
        calls = runtime;

        java.util.function.Supplier<xuanmo.arcartxsuite.api.capability.MailDispatchable> mailSupplier
            = () -> getCapability(xuanmo.arcartxsuite.api.capability.MailDispatchable.class);

        var claimTransfer = new LotteryClaimTransfer(repo, pendingRewardService,
            LotteryClaimTransfer.target(storageManager.getDescriptor(), !configuration.storage().hasOverride(), dataFolder.toPath()));
        service = new LotteryService(
            plugin, configuration, repo,
            currencyManager, itemSourceRegistry, itemMatcher, mailSupplier, logger, claimTransfer);
        service.setMessageProvider(messages());
        service.start();
        var active = service;

        // UI 注册
        registerModuleUi(GACHA_UI_FILE, configuration.ui().gachaUiId(), true);
        registerModuleUi(WISH_UI_FILE, configuration.ui().wishUiId(), true);
        registerModuleUi(CASE_UI_FILE, configuration.ui().caseUiId(), true);

        registerCapability(DatabaseMigratable.class, new DatabaseMigratable() {
            @Override public @NotNull String moduleId() { return "lottery"; }
            @Override public @NotNull xuanmo.arcartxsuite.api.storage.MigrationResult migrateDatabase(
                    @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor target, boolean overwrite) {
                var unavailable = new xuanmo.arcartxsuite.api.storage.MigrationResult(false, 0, 0);
                unavailable.addError("Lottery 模块已停用");
                return runtime.call(() -> repo.migrateData(target, overwrite), unavailable);
            }
            @Override public @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor currentDescriptor() {
                return repo.getDescriptor();
            }
        });

        registerCapability(PlayerDataPurgeable.class, new PlayerDataPurgeable() {
            @Override public @NotNull String moduleId() { return "lottery"; }
            @Override public int purgePlayerData(@NotNull java.util.UUID playerUuid) {
                return runtime.call(() -> {
                    try { return repo.deletePlayerData(playerUuid); }
                    catch (Exception e) { logger.warning("Lottery purge 失败: " + e.getMessage()); return -1; }
                }, -1);
            }
            @Override public int purgeAllPlayerData() {
                return runtime.call(() -> {
                    try { return repo.deleteAllPlayerData(); }
                    catch (Exception e) { logger.warning("Lottery purgeAll 失败: " + e.getMessage()); return -1; }
                }, -1);
            }
        });

        registerCapability(xuanmo.arcartxsuite.api.capability.LotteryAccess.class,
            new xuanmo.arcartxsuite.api.capability.LotteryAccess() {
                @Override public @NotNull CompletionStage<Long> getPoint(@NotNull java.util.UUID playerUuid,
                        @NotNull String pointId) {
                    return available(runtime.call(() -> active.getPoint(playerUuid, pointId), null));
                }
                @Override public @NotNull CompletionStage<Map<String, Long>> getPoints(
                        @NotNull java.util.UUID playerUuid) {
                    return available(runtime.call(() -> active.getPoints(playerUuid), null));
                }
                @Override public @NotNull CompletionStage<Boolean> givePoints(@NotNull java.util.UUID playerUuid,
                        @NotNull String pointId, long amount) {
                    return available(runtime.call(() -> active.givePoints(playerUuid, pointId, amount), null));
                }
                @Override public @NotNull CompletionStage<Boolean> exchange(@NotNull org.bukkit.entity.Player player,
                        @NotNull String itemId) {
                    return available(runtime.call(() -> active.exchange(player, itemId), null));
                }
                @Override public @NotNull CompletionStage<List<CaseDrawResult>> openCase(
                        @NotNull org.bukkit.entity.Player player, @NotNull String poolId, int count) {
                    return available(runtime.call(() -> active.openCaseBatch(player, poolId, count), null))
                        .thenApply(results -> results.stream()
                        .map(r -> new CaseDrawResult(r.rarity(), r.poolIndex(),
                            r.item() != null ? r.item().key() : null,
                            r.item() != null ? r.item().name() : null,
                            r.item() != null ? r.item().amount() : 0))
                        .toList());
                }
            });

        adminCommand = new LotteryAdminCommand(() -> active, messages());
        logger.fine("Lottery 模块已载入 | 奖池=" + configuration.pools().size());
    }

    private static <T> CompletionStage<T> available(CompletionStage<T> stage) {
        return stage != null ? stage : CompletableFuture.failedFuture(new IllegalStateException("Lottery 模块已停用"));
    }

    @Override
    protected @NotNull List<Listener> createListeners() {
        var runtime = calls;
        var active = service;
        return List.of(new Listener() {
            @EventHandler
            public void onAdmission(AuthAdmissionEvent event) {
                if (runtime != null && active != null) {
                    var player = Bukkit.getPlayer(event.snapshot().playerId());
                    if (player != null) runtime.call(() -> active.claimPending(player), null);
                }
            }

            @EventHandler
            public void onQuit(PlayerQuitEvent event) {
                if (packetHandler != null) {
                    packetHandler.onPlayerQuit(event.getPlayer().getUniqueId());
                }
            }

            @EventHandler
            public void onInteract(PlayerInteractEvent event) {
                // 右键（空气或方块）手持箱子物品时打开对应 CASE 奖池 UI
                if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
                    return;
                }
                if (service == null || packetHandler == null) {
                    return;
                }
                org.bukkit.inventory.ItemStack item = event.getItem();
                if (item == null || item.getType().isAir()) {
                    return;
                }
                xuanmo.arcartxsuite.lottery.config.PoolDefinition pool = service.findCasePoolByItem(event.getPlayer(), item);
                if (pool == null) {
                    return;
                }
                if (packetHandler.openPoolUi(event.getPlayer(), pool.id())) {
                    event.setCancelled(true);
                }
            }
        });
    }

    @Override
    protected void stopService() {
        var runtime = calls;
        var scope = storageScope;
        var handler = packetHandler;
        var retiring = service;
        calls = null;
        packetHandler = null;
        service = null;
        repository = null;
        adminCommand = null;
        storageScope = null;
        configuration = null;
        if (runtime != null) runtime.close();
        try {
            if (handler != null) handler.shutdown();
        } finally {
            if (retiring != null) retiring.shutdown();
            var ready = java.util.concurrent.CompletableFuture.<Void>completedFuture(null);
            var stopped = java.util.concurrent.CompletableFuture.allOf(
                runtime == null ? ready : runtime.stopped().toCompletableFuture(),
                handler == null ? ready : handler.stopped().toCompletableFuture(),
                retiring == null ? ready : retiring.stopped().toCompletableFuture());
            trackShutdown(stopped);
            if (scope != null) {
                scope.retireAfter(stopped);
                trackShutdown(scope.retired());
            }
        }
    }

    @Override
    protected @NotNull Map<String, TabExecutor> commandBindings() {
        var delegate = new LotteryPlayerCommand(() -> service, messages(), () -> packetHandler, () -> packetBridge);
        return Map.of("lottery", new TabExecutor() {
            @Override
            public boolean onCommand(@NotNull CommandSender sender, @NotNull org.bukkit.command.Command command,
                                     @NotNull String label, @NotNull String[] args) {
                var runtime = calls;
                return runtime == null || runtime.call(() -> delegate.onCommand(sender, command, label, args), true);
            }

            @Override
            public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull org.bukkit.command.Command command,
                    @NotNull String alias, @NotNull String[] args) {
                var runtime = calls;
                return runtime == null ? List.of() : runtime.call(() -> delegate.onTabComplete(sender, command, alias, args), List.of());
            }
        });
    }

    @Override
    protected @Nullable Object createPlaceholderExpansion() {
        var active = service;
        return new LotteryPlaceholderExpansion(plugin, () -> active);
    }

    // ─── ModuleCommandHandler (/axs lottery) ──────────────────

    @Override public String commandId() { return "lottery"; }

    @Override public List<String> actions() {
        return adminCommand != null ? adminCommand.actions() : List.of("help", "status");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        var runtime = calls;
        var handler = adminCommand;
        return runtime != null && handler != null && runtime.call(() -> handler.onCommand(sender, label, args), true);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return adminCommand != null ? adminCommand.onTabComplete(sender, args) : null;
    }

}

