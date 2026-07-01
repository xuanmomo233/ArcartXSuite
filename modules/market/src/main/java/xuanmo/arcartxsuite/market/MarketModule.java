package xuanmo.arcartxsuite.market;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.AbstractAXSModule;
import xuanmo.arcartxsuite.api.ClientPacketHandler;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.ModuleDescriptor;
import xuanmo.arcartxsuite.api.capability.MailDispatchable;
import xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable;
import xuanmo.arcartxsuite.api.config.SyncPolicy;
import xuanmo.arcartxsuite.api.config.ValidationRule;
import xuanmo.arcartxsuite.api.config.ValueType;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.market.auction.AuctionItemSerializer;
import xuanmo.arcartxsuite.market.command.MarketAdminCommand;
import xuanmo.arcartxsuite.market.command.MarketPlayerCommand;
import xuanmo.arcartxsuite.market.config.MarketModuleConfiguration;
import xuanmo.arcartxsuite.market.listener.MarketEventListener;
import xuanmo.arcartxsuite.market.placeholder.MarketPlaceholderExpansion;
import xuanmo.arcartxsuite.market.storage.JdbcMarketRepository;

public final class MarketModule extends AbstractAXSModule implements ModuleCommandHandler {

    private static final String SHOP_UI_RESOURCE_PATH = "arcartx/ui/market_shop.yml";
    private static final String SHOP_UI_FILE_PATH = "ui/market_shop.yml";
    private static final String AUCTION_UI_RESOURCE_PATH = "arcartx/ui/market_auction.yml";
    private static final String AUCTION_UI_FILE_PATH = "ui/market_auction.yml";
    private static final String RECYCLE_UI_RESOURCE_PATH = "arcartx/ui/market_recycle.yml";
    private static final String RECYCLE_UI_FILE_PATH = "ui/market_recycle.yml";
    private static final String HISTORY_UI_RESOURCE_PATH = "arcartx/ui/market_history.yml";
    private static final String HISTORY_UI_FILE_PATH = "ui/market_history.yml";

    private MarketModuleConfiguration configuration;
    private MarketService service;
    private MarketAdminCommand adminCommand;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("market")
            .name("Market")
            .version("1.0.0")
            .mainClass(getClass().getName())
            .build();
    }

    @Override
    protected String configFileName() {
        return "ArcartXMarket.yml";
    }

    @Override
    protected String messagesFileName() {
        return "messages.yml";
    }

    @Override
    protected @NotNull SyncPolicy defaultSyncPolicy() {
        return SyncPolicy.builder()
            .dynamicSection("auction.categories")
            .dynamicSection("auction.blacklist")
            .dynamicSection("auction.tax-discount")
            .dynamicSection("recycle.price-multiplier")
            .dynamicSection("messages")
            .build();
    }

    @Override
    protected @NotNull List<ValidationRule> mainConfigValidations() {
        return List.of(
            ValidationRule.required("storage.mode", ValueType.STRING)
                .withEnum(Set.of("sqlite", "mysql")),
            ValidationRule.required("storage.pool-size", ValueType.INT)
                .withRange(1, 100),
            ValidationRule.of("auction.max-listings-per-player", ValueType.INT)
                .withRange(1, 1000),
            ValidationRule.of("auction.min-bid-increment-ratio", ValueType.DOUBLE)
                .withRange(0.01, 1.0),
            ValidationRule.of("auction.transaction-tax-rate", ValueType.DOUBLE)
                .withRange(0.0, 0.99),
            ValidationRule.of("auction.min-duration-seconds", ValueType.INT)
                .withRange(60, null),
            ValidationRule.of("auction.max-duration-seconds", ValueType.INT)
                .withRange(3600, null),
            ValidationRule.of("redis.cache-ttl-seconds", ValueType.INT)
                .withRange(10, 86400)
        );
    }

    @Override
    protected @NotNull Map<String, String> uiResourceMappings() {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put(SHOP_UI_RESOURCE_PATH, SHOP_UI_FILE_PATH);
        mappings.put(AUCTION_UI_RESOURCE_PATH, AUCTION_UI_FILE_PATH);
        mappings.put(RECYCLE_UI_RESOURCE_PATH, RECYCLE_UI_FILE_PATH);
        mappings.put(HISTORY_UI_RESOURCE_PATH, HISTORY_UI_FILE_PATH);
        return mappings;
    }

    @Override
    protected boolean overwriteUiFiles() {
        return configuration != null && configuration.ui().overwriteUiFiles();
    }

    @Override
    protected void loadConfiguration(@Nullable File configFile) throws Exception {
        if (configFile == null) {
            throw new IllegalStateException("ArcartXMarket.yml 配置文件缺失");
        }
        configuration = MarketModuleConfiguration.load(
            YamlConfiguration.loadConfiguration(configFile), logger);
    }

    @Override
    protected void startService() throws Exception {
        PacketBridgeAPI packetBridge = packetBridge;
        CurrencyBridgeAPI currencyManager = currencyManager;
        ItemSourceRegistry itemSourceRegistry = itemSourceRegistry;

        // 构造物品序列化器（使用 ItemSerializer byte[] + Base64）
        AuctionItemSerializer itemSerializer = new AuctionItemSerializer() {
            @Override
            public String serialize(ItemStack item) {
                byte[] bytes = xuanmo.arcartxsuite.api.util.ItemSerializer.serialize(item);
                return java.util.Base64.getEncoder().encodeToString(bytes);
            }

            @Override
            public @Nullable ItemStack deserialize(String data) {
                if (data == null || data.isEmpty()) return null;
                byte[] bytes = java.util.Base64.getDecoder().decode(data);
                return xuanmo.arcartxsuite.api.util.ItemSerializer.deserialize(bytes);
            }
        };

        // MailDispatchable supplier（延迟查找）
        java.util.function.Supplier<MailDispatchable> mailSupplier = () -> getCapability(MailDispatchable.class);

        // 首次启动时自动导出示例商店和回收表
        ensureExampleShopExported();
        ensureDefaultRecycleExported();

        service = new MarketService(plugin, configuration, packetBridge,
            currencyManager, itemSourceRegistry, itemSerializer,
            itemStackBridge, mailSupplier, logger, crossServer);
        service.setSignalProvider(() -> getCapability(
            xuanmo.arcartxsuite.api.capability.SignalDispatchable.class));
        service.setQQBotProvider(
            () -> getCapability(xuanmo.arcartxsuite.api.capability.QQBotBroadcastable.class),
            configuration.auction().qqBroadcastThreshold());
        service.setEventBusProvider(() -> getCapability(
            xuanmo.arcartxsuite.api.capability.EventBusCapability.class));
        service.start(dataFolder);

        // 注册待发放队列消费者（玩家上线补发离线期间累积的物品/货币）
        registerListener(new PendingDeliveryService(
            plugin, service.getRepository(), currencyManager, itemSerializer, logger));

        // 注册 UI 到 ArcartX 桥接层
        bindMarketUi(configuration.ui().shopId(), SHOP_UI_FILE_PATH);
        bindMarketUi(configuration.ui().auctionId(), AUCTION_UI_FILE_PATH);
        bindMarketUi(configuration.ui().recycleId(), RECYCLE_UI_FILE_PATH);
        bindMarketUi(configuration.ui().historyId(), HISTORY_UI_FILE_PATH);

        JdbcMarketRepository marketRepo = (JdbcMarketRepository) service.getRepository();
        registerCapability(xuanmo.arcartxsuite.api.capability.DatabaseMigratable.class,
            new xuanmo.arcartxsuite.api.capability.DatabaseMigratable() {
                @Override public @NotNull String moduleId() { return "market"; }
                @Override public @NotNull xuanmo.arcartxsuite.api.storage.MigrationResult migrateDatabase(
                        @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor target, boolean overwrite) {
                    return marketRepo.migrateData(target, overwrite);
                }
                @Override public @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor currentDescriptor() {
                    return marketRepo.getDescriptor();
                }
            });

        registerCapability(PlayerDataPurgeable.class, new PlayerDataPurgeable() {
            @Override public @NotNull String moduleId() { return "market"; }
            @Override public int purgePlayerData(@NotNull java.util.UUID playerUuid) {
                try { return marketRepo.deletePlayerData(playerUuid); }
                catch (Exception e) { logger.warning("Market purge 失败: " + e.getMessage()); return -1; }
            }
            @Override public int purgeAllPlayerData() {
                try { return marketRepo.deleteAllPlayerData(); }
                catch (Exception e) { logger.warning("Market purgeAll 失败: " + e.getMessage()); return -1; }
            }
        });

        adminCommand = new MarketAdminCommand(() -> service, messages());

        // 注册自动回收监听器
        if (configuration.recycle().enabled() && configuration.recycle().allowAutoRecycle()) {
            registerListener(new MarketEventListener(
                () -> service != null ? service.getRecycleService() : null,
                true
            ));
        }

        logger.fine("Market 模块已载入 | 存储=" + configuration.storage().mode().toUpperCase()
            + " | 列表缓存=" + (configuration.redis().enabled() ? "启用" : "禁用")
            + " | 跨服=" + (service != null && service.crossServerActive() ? "ON" : "OFF"));
    }

    private void bindMarketUi(String configuredId, String relativeUiPath) {
        if (!configuration.ui().registerOnEnable()) return;
        registerModuleUi(relativeUiPath, configuredId, true);
    }

    private void ensureExampleShopExported() {
        File shopDir = new File(dataFolder, configuration.shop().shopsDirectory());
        if (!shopDir.exists()) {
            shopDir.mkdirs();
        }
        File[] files = shopDir.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files == null || files.length == 0) {
            File target = new File(shopDir, "example_shop.yml");
            exportResource("shops/example_shop.yml", target, false);
            logger.info("[Market] 已导出示例商店到 " + target.getPath());
        }
    }

    private void ensureDefaultRecycleExported() {
        File recycleDir = new File(dataFolder, configuration.recycle().recycleDirectory());
        if (!recycleDir.exists()) {
            recycleDir.mkdirs();
        }
        File[] files = recycleDir.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files == null || files.length == 0) {
            File target = new File(recycleDir, "default_recycle.yml");
            exportResource("recycle/default_recycle.yml", target, false);
            logger.info("[Market] 已导出默认回收表到 " + target.getPath());
        }
    }

    @Override
    protected void stopService() {
        if (service != null) {
            service.shutdown();
            service = null;
        }
        configuration = null;
    }

    @Override
    protected @NotNull Map<String, TabExecutor> commandBindings() {
        MarketPlayerCommand cmd = new MarketPlayerCommand(() -> service, messages());
        return Map.of("market", (TabExecutor) cmd);
    }

    @Override
    protected @Nullable Object createPlaceholderExpansion() {
        return new MarketPlaceholderExpansion(plugin, () -> service);
    }

    @Override
    protected @Nullable ClientPacketHandler createPacketHandler() {
        return (player, packetId, data) -> {
            MarketService current = service;
            if (current == null || !current.ownsPacket(packetId)) {
                return false;
            }
            // 市场交易涉及背包与经济操作，统一切到主线程串行执行，
            // 消除并发竞态（重复购买/限购绕过）与异步线程操作 Bukkit API 的风险。
            if (org.bukkit.Bukkit.isPrimaryThread()) {
                current.handleClientPacket(player, packetId, data);
            } else {
                org.bukkit.Bukkit.getScheduler().runTask(plugin,
                    () -> current.handleClientPacket(player, packetId, data));
            }
            return true;
        };
    }

    // ClientPacketHandler.data 是 List<String>，MarketService 内部解析为 Map

    // ─── ModuleCommandHandler（/axs market）───────────────────

    @Override public String commandId() { return "market"; }

    @Override public List<String> actions() {
        return adminCommand != null ? adminCommand.actions() : List.of("help", "status", "reload");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        return adminCommand != null && adminCommand.onCommand(sender, label, args);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return adminCommand != null ? adminCommand.onTabComplete(sender, args) : null;
    }
}



