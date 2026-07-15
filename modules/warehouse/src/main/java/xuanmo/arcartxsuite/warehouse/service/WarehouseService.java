package xuanmo.arcartxsuite.warehouse.service;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.api.capability.WarehouseAutoDepositable;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.api.currency.CurrencyDefinition;
import xuanmo.arcartxsuite.api.item.ItemMatcherAPI;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;
import xuanmo.arcartxsuite.api.util.ItemSerializer;
import xuanmo.arcartxsuite.api.capability.PickupNotifiable;
import xuanmo.arcartxsuite.api.crossserver.CrossServerAPI;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannelConfig;
import xuanmo.arcartxsuite.warehouse.crossserver.SharedEditLock;
import xuanmo.arcartxsuite.warehouse.crossserver.WarehouseCrossServerLockService;
import xuanmo.arcartxsuite.warehouse.crossserver.WarehouseCrossServerPayloadCodec;
import xuanmo.arcartxsuite.warehouse.config.WarehouseModuleConfiguration;
import xuanmo.arcartxsuite.warehouse.config.WarehouseModuleConfiguration.CategoryDefinition;
import xuanmo.arcartxsuite.warehouse.config.WarehouseModuleConfiguration.DepositProductDefinition;
import xuanmo.arcartxsuite.warehouse.config.WarehouseModuleConfiguration.InterestTier;
import xuanmo.arcartxsuite.warehouse.config.WarehouseModuleConfiguration.SharedPermissionTier;
import xuanmo.arcartxsuite.warehouse.config.WarehouseModuleConfiguration.WarehouseDefinition;
import xuanmo.arcartxsuite.warehouse.config.WarehouseModuleConfiguration.WarehouseLevelDefinition;
import xuanmo.arcartxsuite.warehouse.storage.WarehouseRepository;
import xuanmo.arcartxsuite.warehouse.storage.WarehouseRepository.FixedDepositRecord;
import xuanmo.arcartxsuite.warehouse.storage.WarehouseRepository.PendingTransfer;
import xuanmo.arcartxsuite.warehouse.storage.WarehouseRepository.SecurityRecord;
import xuanmo.arcartxsuite.warehouse.storage.WarehouseRepository.SharedMemberRecord;
import xuanmo.arcartxsuite.warehouse.storage.WarehouseRepository.SharedWarehouseRecord;
import xuanmo.arcartxsuite.warehouse.storage.WarehouseRepository.SlotItemRecord;
import xuanmo.arcartxsuite.warehouse.storage.WarehouseRepository.WarehouseRecord;
import java.util.logging.Logger;

/**
 * Warehouse 核�ƒ�š�Š��œ��Š��Œ�Ÿ筹个人�“�“�€��…�享�“�“�€��š货币�“��Œ�€��Œ级�†码�Ž�‡��Š��‹��–�€��‘�€‚
 * <p>
 * �€š�‡ {@link PacketBridgeAPI} �Ž客�ˆ�端 AXUI �€š信�Œ�‰€�œ‰�Š��€��˜�›��Ž�›ž�‘�›��–��Œ…�ˆ��–��•Œ面�€‚
 * �…�享�“�“使�”� {@link #sharedEditLocks} �ž�Ž��–�‘�’�–��”��›启�”� cross-server �—�经 SDK �Œ步�‡��…��–子�œ��€‚
 */
public final class WarehouseService implements Listener {

    private final MessageProvider messages;

    private static final String PREFIX = ChatColor.DARK_AQUA + "�—† " + ChatColor.GOLD + "ArcartXSuite " + ChatColor.GRAY + "| " + ChatColor.RESET;
    private static final String OWNER_PERSONAL = "personal";
    private static final String OWNER_SHARED = "shared";
    private static final String STORAGE_UI_RESOURCE_PATH = "arcartx/ui/warehouse_storage.yml";
    private static final String STORAGE_UI_FILE_PATH = "ui/warehouse_storage.yml";
    private static final String MANAGE_UI_RESOURCE_PATH = "arcartx/ui/warehouse_manage.yml";
    private static final String MANAGE_UI_FILE_PATH = "ui/warehouse_manage.yml";
    private static final String BANK_UI_RESOURCE_PATH = "arcartx/ui/warehouse_bank.yml";
    private static final String BANK_UI_FILE_PATH = "ui/warehouse_bank.yml";
    private static final int SLOT_COUNT = 54;
    private static final long MAX_AGGREGATED_AMOUNT = Integer.MAX_VALUE;
    private static final int PASSWORD_HASH_ITERATIONS = 120000;
    private static final int PASSWORD_HASH_BITS = 256;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private static final HanyuPinyinOutputFormat PINYIN_FORMAT = new HanyuPinyinOutputFormat();

    static {
        PINYIN_FORMAT.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        PINYIN_FORMAT.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
    }

    /**
     * UI �„源导�‡��‡��•��€‚
     */
    @FunctionalInterface
    public interface UiResourceExporter {
        File export(String resourcePath, String relativeUiPath, boolean overwrite) throws IOException;
    }

    private final JavaPlugin plugin;
    private final Logger logger;
    private final PacketBridgeAPI packetBridge;
    private final xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI itemStackBridge;
    private final PacketGuardAPI packetGuard;
    private final UiResourceExporter uiResourceExporter;
    private final WarehouseModuleConfiguration configuration;
    private final WarehouseRepository repository;
    private final ItemSourceRegistry itemSourceRegistry;
    private final ItemMatcherAPI itemMatcherSupport;
    private final CurrencyBridgeAPI currencyBridgeManager;
    private final Supplier<PickupNotifiable> pickupNotifiableSupplier;
    private final SecureRandom secureRandom = new SecureRandom();
    /** �Ž�家�“�‰� UI �†�›��Š��€��ˆownerType / warehouseId / page / search �‰�‰�€‚ */
    private final ConcurrentMap<UUID, ViewState> viewStates = new ConcurrentHashMap<>();
    /** �Ž�家�Œ级�†码解�”��‡�œŸ�—��—��ˆ��ˆ毫�’�‰�€‚ */
    private final ConcurrentMap<UUID, Long> unlockedUntil = new ConcurrentHashMap<>();
    /** �…�享�“�“�–�‘�’�–��”��š�…�享�“�“ ID �†’ �“�‰��–�‘�€…�ˆ含子�œ� nodeId�‰�€‚ */
    private final ConcurrentMap<String, SharedEditLock> sharedEditLocks = new ConcurrentHashMap<>();
    private final CrossServerAPI crossServerApi;
    private final CrossServerChannelConfig crossServerChannelConfig;
    private WarehouseCrossServerLockService crossServerLockService;
    private Supplier<EventBusCapability> eventBusProvider;
    /** �Ž�家�Š次�•示�“�“�š„�—��—��ˆ��ˆ毫�’�‰�Œ�”��Ž�†�却�Ž��ˆ��€‚ */
    private final ConcurrentMap<UUID, Long> showcaseCooldowns = new ConcurrentHashMap<>();
    private String storageRuntimeUiId = "";
    private String manageRuntimeUiId = "";
    private String bankRuntimeUiId = "";
    private String storageRegisteredUiId = "";
    private String manageRegisteredUiId = "";
    private String bankRegisteredUiId = "";

    public WarehouseService(
        JavaPlugin plugin,
        Logger logger,
        PacketBridgeAPI packetBridge,
        xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI itemStackBridge,
        PacketGuardAPI packetGuard,
        UiResourceExporter uiResourceExporter,
        WarehouseModuleConfiguration configuration,
        WarehouseRepository repository,
        ItemSourceRegistry itemSourceRegistry,
        ItemMatcherAPI itemMatcherSupport,
        CurrencyBridgeAPI currencyBridgeManager,
        Supplier<PickupNotifiable> pickupNotifiableSupplier,
        CrossServerAPI crossServerApi,
        CrossServerChannelConfig crossServerChannelConfig
    ) {
        this(plugin, logger, packetBridge, itemStackBridge, packetGuard, uiResourceExporter, configuration,
            repository, itemSourceRegistry, itemMatcherSupport, currencyBridgeManager,
            pickupNotifiableSupplier, crossServerApi, crossServerChannelConfig, null);
    }

    public WarehouseService(
        JavaPlugin plugin,
        Logger logger,
        PacketBridgeAPI packetBridge,
        xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI itemStackBridge,
        PacketGuardAPI packetGuard,
        UiResourceExporter uiResourceExporter,
        WarehouseModuleConfiguration configuration,
        WarehouseRepository repository,
        ItemSourceRegistry itemSourceRegistry,
        ItemMatcherAPI itemMatcherSupport,
        CurrencyBridgeAPI currencyBridgeManager,
        Supplier<PickupNotifiable> pickupNotifiableSupplier,
        CrossServerAPI crossServerApi,
        CrossServerChannelConfig crossServerChannelConfig,
        MessageProvider messages
    ) {
        this.plugin = plugin;
        this.logger = logger;
        this.packetBridge = packetBridge;
        this.itemStackBridge = itemStackBridge;
        this.packetGuard = packetGuard;
        this.uiResourceExporter = uiResourceExporter;
        this.configuration = configuration;
        this.repository = repository;
        this.itemSourceRegistry = itemSourceRegistry;
        this.itemMatcherSupport = itemMatcherSupport;
        this.currencyBridgeManager = currencyBridgeManager;
        this.pickupNotifiableSupplier = pickupNotifiableSupplier;
        this.crossServerApi = crossServerApi;
        this.messages = messages;
        this.crossServerChannelConfig = crossServerChannelConfig == null
            ? CrossServerChannelConfig.disabled() : crossServerChannelConfig;
    }

    public WarehouseService(
        JavaPlugin plugin,
        Logger logger,
        PacketBridgeAPI packetBridge,
        xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI itemStackBridge,
        PacketGuardAPI packetGuard,
        UiResourceExporter uiResourceExporter,
        WarehouseModuleConfiguration configuration,
        WarehouseRepository repository,
        ItemSourceRegistry itemSourceRegistry,
        ItemMatcherAPI itemMatcherSupport,
        CurrencyBridgeAPI currencyBridgeManager,
        Supplier<PickupNotifiable> pickupNotifiableSupplier
    ) {
        this(plugin, logger, packetBridge, itemStackBridge, packetGuard, uiResourceExporter, configuration,
            repository, itemSourceRegistry, itemMatcherSupport, currencyBridgeManager,
            pickupNotifiableSupplier, null, CrossServerChannelConfig.disabled(), null);
    }

    /**
     * 启�Š��œ��Š��š�ˆ��‹�Œ–�•�据�“�€��‘�š�‰�— AXUI�€�注�†Œ Bukkit �‹件�›‘听�€‚
     */
    public void setEventBusProvider(Supplier<EventBusCapability> eventBusProvider) {
        this.eventBusProvider = eventBusProvider;
    }

    public void start() throws Exception {
        repository.initialize();
        bindUis();
        if (configuration.shared().enabled()
            && crossServerChannelConfig.enabled()
            && crossServerApi != null) {
            crossServerLockService = new WarehouseCrossServerLockService(
                plugin,
                logger,
                crossServerApi,
                crossServerChannelConfig,
                this::applyRemoteSharedLock,
                (unlock, ignored) -> applyRemoteSharedUnlock(unlock)
            );
            crossServerLockService.start();
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * �…��—��œ��Š��š注�”€�‹件�›‘听�€��…�† UI �›ž�ƒ�Ž�Ž�家�Š��€��€��…��—��•�据�“�ž�Ž��€‚
     */
    public void shutdown() {
        if (crossServerLockService != null) {
            crossServerLockService.shutdown();
            crossServerLockService = null;
        }
        HandlerList.unregisterAll(this);
        if (packetBridge != null) {
            packetBridge.unregisterUiCloseCallback(storageRuntimeUiId);
            packetBridge.unregisterUiCloseCallback(manageRuntimeUiId);
            packetBridge.unregisterUiCloseCallback(bankRuntimeUiId);
            if (!storageRegisteredUiId.isBlank()) {
                packetBridge.unregisterUi(storageRegisteredUiId);
            }
            if (!manageRegisteredUiId.isBlank()) {
                packetBridge.unregisterUi(manageRegisteredUiId);
            }
            if (!bankRegisteredUiId.isBlank()) {
                packetBridge.unregisterUi(bankRegisteredUiId);
            }
        }
        viewStates.clear();
        unlockedUntil.clear();
        sharedEditLocks.clear();
        repository.close();
    }

    public int cachedPlayerCount() {
        return viewStates.size();
    }

    public int dirtyPlayerCount() {
        return 0;
    }

    public boolean mythicBridgeAvailable() {
        return itemSourceRegistry.mythicBridgeAvailable();
    }

    public boolean neigeBridgeAvailable() {
        return itemSourceRegistry.neigeBridgeAvailable();
    }

    public boolean crossServerActive() {
        return crossServerLockService != null && crossServerLockService.isActive();
    }

    public int activeSharedEditLockCount() {
        return sharedEditLocks.size();
    }

    public Set<String> currencyIds() {
        return currencyBridgeManager.currencyIds();
    }

    public String runtimeUiId() {
        return storageRuntimeUiId;
    }

    /**
     * �›�–�ƒ�模�—�ˆ�‚ Pickup�‰�ƒ�”��š„�‡��Š��…��“�Ž�口�€‚
     * �†�‰��“��˜�…��Ž�家第�€个个人�“�“�Œ�”�›ž�˜�…��“�žœ�€‚
     *
     * @param player    �›��‡�Ž�家
     * @param itemStack �…�˜�…��‰��“��ˆ�š被 clone�Œ不�š修�”��ŽŸ对象�‰
     * @return �˜�…��“�žœ�Œ�Œ…含�ˆ��ŠŸ�Š��€��€�已�˜�•��‡��€��‰��™�•��‡��Ž提示�ˆ息
     */
    public WarehouseAutoDepositable.DepositResult depositToPersonalWarehouse(Player player, ItemStack itemStack) {
        if (player == null || !player.isOnline()) {
            return new WarehouseAutoDepositable.DepositResult(false, 0L, 0, "�Ž�家不�œ�线�€‚");
        }
        ItemStack stack = itemStack == null ? null : itemStack.clone();
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            return new WarehouseAutoDepositable.DepositResult(false, 0L, 0, "没�œ‰可�˜�…��‰��“��€‚");
        }
        try {
            DepositResult result = depositStack(player, OWNER_PERSONAL, player.getUniqueId().toString(), firstPersonalWarehouseId(), stack);
            if (result.success()) {
                publishDepositEvent(player, stack, result.storedAmount());
            }
            return new WarehouseAutoDepositable.DepositResult(
                result.success(),
                result.storedAmount(),
                result.remainingAmount(),
                result.message()
            );
        } catch (Exception exception) {
            if (configuration.debug()) {
                this.logger.warning("�–�ƒ��‡��Š��…��“失败: " + exception.getMessage());
            }
            return new WarehouseAutoDepositable.DepositResult(false, 0L, stack.getAmount(), "�‡��Š��…��“失败�€‚");
        }
    }

    private void publishDepositEvent(Player player, ItemStack stack, long storedAmount) {
        if (eventBusProvider == null) return;
        EventBusCapability eventBus = eventBusProvider.get();
        if (eventBus == null) return;
        Map<String, String> payload = new HashMap<>();
        payload.put("material", stack.getType().name());
        payload.put("amount", String.valueOf(storedAmount));
        eventBus.publish("axs.warehouse.item_deposited", player, payload);
    }

    /**
     * 为�Ž�家�‰“�€�“�“主�•Œ面�ˆ�˜�–�•Œ面�‰�€‚
     * �š�‡��Š��ˆ��‹�Œ–�Ž�家�ƒ�™��“�“�€�确保�“�‰��“�“�œ‰�•ˆ�Œ并�‘�€� storage �›��–��Œ…�€‚
     *
     * @param player �›��‡�Ž�家
     * @return �“��œ�“�žœ
     */
    public ActionResult openMenu(Player player) {
        if (player == null || !player.isOnline()) {
            return ActionResult.failure(message("player.player-offline"));
        }
        if (storageRuntimeUiId == null || storageRuntimeUiId.isBlank()) {
            return ActionResult.failure(message("player.ui-not-registered"));
        }
        try {
            ensureEntitlements(player);
            ViewState state = state(player);
            ensureCurrentWarehouse(player, state);
            openStorage(player, "init");
            return ActionResult.success(message("player.opened"));
        } catch (Exception exception) {
            this.logger.warning("�‰“�€�“�“失败: " + exception.getMessage());
            return ActionResult.failure(message("player.open-failed"));
        }
    }

    /**
     * 以只读�„�ˆ模式�‰“�€�›��‡�Ž�家�š„�“�“�€‚
     * �„�ˆ模式�‹�…�”��Œ�翻页�€��ˆ†类�€��œ索�€��€‰�‹��’Œ�ˆ��–��Œ禁止�˜�–�Ž�ˆ‡换�“�“�€‚
     *
     * @param viewer      �„�ˆ�€…
     * @param targetUuid  被�„�ˆ�Ž�家 UUID
     * @param warehouseId �Œ‡�š�“�“ ID�Œ空�—符串�ˆ™使�”��˜认�“�“
     * @return �“��œ�“�žœ
     */
    public ActionResult openPreview(Player viewer, UUID targetUuid, String warehouseId) {
        if (viewer == null || !viewer.isOnline()) {
            return ActionResult.failure(message("player.player-offline"));
        }
        if (storageRuntimeUiId == null || storageRuntimeUiId.isBlank()) {
            return ActionResult.failure(message("player.ui-not-registered"));
        }
        try {
            String previewOwnerType = OWNER_PERSONAL;
            if (warehouseId != null && !warehouseId.isBlank() && !configuration.warehouses().containsKey(warehouseId)) {
                previewOwnerType = OWNER_SHARED;
            }
            ViewState previewState = ViewState.preview(targetUuid, warehouseId, previewOwnerType);
            viewStates.put(viewer.getUniqueId(), previewState);
            if (packetBridge != null) {
                packetBridge.openUi(viewer, storageRuntimeUiId);
            }
            openStorage(viewer, "init");
            return ActionResult.success(message("player.preview-opened"));
        } catch (Exception exception) {
            this.logger.warning("�‰“�€�“�“�„�ˆ失败: " + exception.getMessage());
            return ActionResult.failure(message("player.preview-open-failed"));
        }
    }

    /**
     * �‘�…��œ��•示�Ž�家�“�“�€‚
     * �‹��…�置�† {@code card-id} �ˆ™�‘�€� ArcartX �Š天卡�‰‡�Œ否�ˆ™�‘�€�可�‚��‡��Š天�ˆ息�€‚
     * �— {@code cooldown-seconds} �†�却�Ž��ˆ��€‚
     *
     * @param player �•示�€…
     * @return �“��œ�“�žœ
     */
    public ActionResult showcase(Player player) {
        if (player == null || !player.isOnline()) {
            return ActionResult.failure(message("player.player-offline"));
        }
        var showcaseConfig = configuration.showcase();
        if (!showcaseConfig.enabled()) {
            return ActionResult.failure(message("player.showcase-disabled"));
        }
        if (!player.hasPermission(showcaseConfig.permission())) {
            return ActionResult.failure(message("player.showcase-no-permission"));
        }
        UUID showcaseCooldownKey = player.getUniqueId();
        Long lastShowcase = showcaseCooldowns.get(showcaseCooldownKey);
        long now = System.currentTimeMillis();
        if (lastShowcase != null && (now - lastShowcase) < showcaseConfig.cooldownSeconds() * 1000L) {
            long remaining = (showcaseConfig.cooldownSeconds() * 1000L - (now - lastShowcase)) / 1000L;
            return ActionResult.failure(message("player.showcase-cooldown", remaining));
        }
        showcaseCooldowns.put(showcaseCooldownKey, now);

        UUID playerUuid = player.getUniqueId();
        String displayName = player.getName();

        // �”��›†可�•示�“�“�š个人�“�“设置�†可�•示 + 主人设置�†可�•示�š„�…�享�“�“
        List<String[]> showcaseEntries = new ArrayList<>();
        try {
            Map<String, WarehouseRecord> personalRecords = personalWarehouseMap(playerUuid);
            for (WarehouseDefinition definition : configuration.warehouses().values()) {
                WarehouseRecord record = personalRecords.get(definition.id());
                if (record != null && record.showcaseEnabled()) {
                    String name = record.customName() == null || record.customName().isBlank()
                        ? ChatColor.translateAlternateColorCodes('&', definition.displayName())
                        : record.customName();
                    showcaseEntries.add(new String[]{name, playerUuid.toString(), definition.id()});
                }
            }
            for (SharedWarehouseRecord shared : repository.loadSharedWarehouses(playerUuid)) {
                if ("owner".equalsIgnoreCase(shared.viewerRole()) && shared.showcaseEnabled()) {
                    showcaseEntries.add(new String[]{shared.name(), playerUuid.toString(), shared.id()});
                }
            }
        } catch (Exception ignored) {
        }
        if (showcaseEntries.isEmpty()) {
            return ActionResult.failure(message("player.showcase-empty"));
        }

        if (showcaseConfig.useCard()) {
            String previewCommand = "/wh spreview " + playerUuid + " " + showcaseEntries.get(0)[2];
            if (packetBridge != null) {
                packetBridge.sendChatCard(player, showcaseConfig.cardId(), Map.of(
                    "player_name", displayName,
                    "preview_command", previewCommand
                ));
            }
        } else {
            net.md_5.bungee.api.chat.TextComponent prefix = new net.md_5.bungee.api.chat.TextComponent(
                ChatColor.GOLD + "[�“�“�•示] " + ChatColor.WHITE + displayName + " 正�œ��•示�“�“�š "
            );
            for (String[] entry : showcaseEntries) {
                net.md_5.bungee.api.chat.TextComponent link = new net.md_5.bungee.api.chat.TextComponent(
                    ChatColor.AQUA + "[" + entry[0] + "]"
                );
                link.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                    net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND,
                    "/wh spreview " + entry[1] + " " + entry[2]
                ));
                link.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                    net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                    new net.md_5.bungee.api.chat.hover.content.Text(ChatColor.GRAY + "�‚��‡��„�ˆ " + displayName + " �š„ " + entry[0])
                ));
                prefix.addExtra(link);
                prefix.addExtra(" ");
            }
            for (Player onlinePlayer : org.bukkit.Bukkit.getOnlinePlayers()) {
                onlinePlayer.spigot().sendMessage(prefix);
            }
        }
        return ActionResult.success(message("player.showcase-success"));
    }

    /**
     * �„�†客�ˆ�端 UI �‘来�š„�“��œ�Œ…�€‚
     * <p>
     * �”��Œ��š„�“��œ�ˆaction�‰详见 wiki �–‡档�€Œ客�ˆ�端�Œ…协议�€�章�Š‚�€‚
     * �‰€�œ‰�“��œ�‡经�‡ {@link PacketGuardAPI} 校�Œ�Œ�‚常�—��‡��Š��ˆ��–� UI�€‚
     *
     * @param player   �‘�€��Œ…�š„�Ž�家
     * @param packetId �Œ… ID�ˆ�”为 {@code AXS_WAREHOUSE}�‰
     * @param data     �Œ…�•�据�ˆ—表�Œ第�€项�€š常为 action
     * @return true 表示已�„�†�ˆ�—�论�ˆ��ŠŸ�ˆ–失败�‰
     */
    public boolean handleClientPacket(Player player, String packetId, List<String> data) {
        if (player == null || !player.isOnline() || packetId == null || !configuration.ui().packetId().equalsIgnoreCase(packetId)) {
            return false;
        }
        String action = data == null || data.isEmpty() ? "refresh" : safe(data.get(0)).toLowerCase(Locale.ROOT);
        debug("IN player=" + player.getName() + " packetId=" + packetId + " action=" + action + " data=" + data);
        // guard injected via field
        if (packetGuard != null && !packetGuard.allow(player, "warehouse", action, configuration.debug())) {
            debug("IN-BLOCKED player=" + player.getName() + " action=" + action + " reason=packet-guard");
            return true;
        }
        try {
            ViewState currentViewState = viewStates.get(player.getUniqueId());
            if (currentViewState != null && currentViewState.previewMode()) {
                switch (action) {
                    case "page" -> setPage(player, parseInt(value(data, 1, "1"), 1));
                    case "category" -> setCategory(player, value(data, 1, "all"));
                    case "search" -> setSearch(player, value(data, 1, ""));
                    case "select" -> selectSlot(player, parsePacketSlot(value(data, 1, "-1")));
                    case "refresh" -> refreshBoth(player);
                    case "warehouse" -> selectPersonalWarehouse(player, value(data, 1, firstPersonalWarehouseId()));
                    case "shared" -> selectPreviewSharedWarehouse(player, value(data, 1, ""));
                    case "close" -> handleUiClosed(player);
                    default -> sendMessage(player, false, message("player.preview-read-only"));
                }
                return true;
            }
            switch (action) {
                case "open", "storage" -> openStorage(player, "init");
                case "manage" -> openManage(player, "init");
                case "bank" -> openBank(player, "init");
                case "refresh" -> refreshBoth(player);
                case "showcase" -> {
                    ActionResult result = showcase(player);
                    if (!result.success()) {
                        sendMessage(player, false, result.message());
                    }
                }
                case "page" -> setPage(player, parseInt(value(data, 1, "1"), 1));
                case "warehouse_upgrade" -> upgradeCurrentWarehouse(player);
                case "warehouse" -> selectPersonalWarehouse(player, value(data, 1, firstPersonalWarehouseId()));
                case "shared" -> selectSharedWarehouse(player, value(data, 1, ""));
                case "shared_mode" -> setSharedMode(player, value(data, 1, "readonly"));
                case "category" -> setCategory(player, value(data, 1, "all"));
                case "search" -> setSearch(player, value(data, 1, ""));
                case "select" -> selectSlot(player, parsePacketSlot(value(data, 1, "-1")));
                case "deposit_slot" -> depositSlot(player, value(data, 1, ""), parseLong(value(data, 2, "1"), 1L));
                case "deposit_all_backpack" -> depositAllBackpack(player);
                case "withdraw" -> withdraw(player, parsePacketSlot(value(data, 1, "-1")), parseLong(value(data, 2, "1"), 1L), false);
                case "withdraw_all" -> withdraw(player, parsePacketSlot(value(data, 1, "-1")), Long.MAX_VALUE, true);
                case "bank_deposit" -> bankDeposit(player, value(data, 1, ""), parseDecimal(value(data, 2, "0")));
                case "bank_withdraw" -> bankWithdraw(player, value(data, 1, ""), parseDecimal(value(data, 2, "0")));
                case "fixed_create" -> createFixedDeposit(player, value(data, 1, ""), parseDecimal(value(data, 2, "0")));
                case "fixed_claim" -> claimFixedDeposit(player, value(data, 1, ""));
                case "shared_create" -> createSharedWarehouse(player, value(data, 1, "�…�享�“�“"));
                case "shared_rename" -> renameSharedWarehouse(player, value(data, 1, ""), value(data, 2, ""));
                case "shared_showcase_toggle" -> toggleSharedWarehouseShowcase(player, value(data, 1, ""));
                case "personal_rename" -> renamePersonalWarehouse(player, value(data, 1, ""), value(data, 2, ""));
                case "personal_showcase_toggle" -> togglePersonalWarehouseShowcase(player, value(data, 1, ""));
                case "shared_delete" -> deleteSharedWarehouse(player, value(data, 1, ""), value(data, 2, ""));
                case "shared_invite" -> inviteSharedMember(player, value(data, 1, ""), value(data, 2, ""), value(data, 3, "member"));
                case "shared_remove" -> removeSharedMember(player, value(data, 1, ""), value(data, 2, ""));
                case "shared_transfer" -> transferSharedWarehouse(player, value(data, 1, ""), value(data, 2, ""));
                case "shared_transfer_confirm" -> confirmPendingTransfer(player, value(data, 1, ""));
                case "shared_transfer_reject" -> rejectPendingTransfer(player, value(data, 1, ""));
                case "password_set" -> setPassword(player, value(data, 1, ""));
                case "password_unlock" -> unlockPassword(player, value(data, 1, ""));
                case "password_lock" -> {
                    unlockedUntil.remove(player.getUniqueId());
                    sendMessage(player, true, message("player.secondary-locked"));
                    refreshBoth(player);
                }
                case "password_clear" -> clearPassword(player, value(data, 1, ""));
                case "toggle_auto_pickup" -> toggleAutoPickup(player, "pickup");
                case "toggle_auto_mythic" -> toggleAutoPickup(player, "mythic");
                case "toggle_auto_notify" -> toggleAutoPickup(player, "notify");
                default -> refreshBoth(player);
            }
        } catch (Exception exception) {
            this.logger.warning("�„�†�“�“客�ˆ�端�Œ…失败: " + exception.getMessage());
            debug("IN-ERROR player=" + player.getName() + " action=" + action + " error=" + exception.getClass().getSimpleName() + ": " + exception.getMessage());
            sendMessage(player, false, message("player.operation-failed"));
            try {
                refreshBoth(player);
            } catch (Exception refreshException) {
                this.logger.warning("�ˆ��–��“�“ UI 失败: " + refreshException.getMessage());
            }
        }
        return true;
    }

    /**
     * �‚步�”Ÿ�ˆ��Ž�家�“�“�‚�ˆ信息�Œ�”��Ž管�†�‘˜�Ÿ�询�€‚
     *
     * @param playerUuid   �Ž�家 UUID
     * @param playerName   �Ž�家名称�ˆ�”��Ž�˜�示�‰
     * @param callback     �ˆ��ŠŸ�›ž�ƒ�Œ�Ž��”�格式�Œ–�Ž�š„信息�Œ�ˆ—表
     * @param errorCallback 失败�›ž�ƒ�Œ�Ž��”��”™误信息
     */
    public void describePlayer(UUID playerUuid, String playerName, Consumer<List<String>> callback, Consumer<String> errorCallback) {
        try {
            long total = totalItems(playerUuid);
            long used = personalUsed(playerUuid);
            long capacity = personalCapacity(playerUuid);
            List<FixedDepositRecord> deposits = repository.loadFixedDeposits(playerUuid);
            List<SharedWarehouseRecord> shared = repository.loadSharedWarehouses(playerUuid);
            List<String> lines = new ArrayList<>();
            lines.add(ChatColor.GRAY + "�Ž�家: " + ChatColor.WHITE + playerName);
            lines.add(ChatColor.GRAY + "个人�“�“: " + ChatColor.WHITE + used + "/" + capacity + " 格�Œ�ˆ计 " + total + " 件");
            lines.add(ChatColor.GRAY + "�…�享�“�“: " + ChatColor.WHITE + shared.size() + " 个");
            lines.add(ChatColor.GRAY + "�š�œŸ�˜款: " + ChatColor.WHITE + deposits.stream().filter(d -> !d.claimed()).count() + " �”�œ��†�–");
            callback.accept(lines);
        } catch (Exception exception) {
            errorCallback.accept("读�–�“�“信息失败: " + exception.getMessage());
        }
    }

    /**
     * 管�†�‘˜�…�™��Ž�家�Œ级�†码�€‚
     *
     * @param playerUuid �›��‡�Ž�家 UUID
     * @param callback   �“��œ�“�žœ�›ž�ƒ
     */
    public void describePersonalWarehouse(UUID playerUuid, String playerName, String warehouseId, Consumer<List<String>> callback, Consumer<String> errorCallback) {
        try {
            Optional<WarehouseRecord> warehouse = repository.loadPersonalWarehouses(playerUuid).stream().filter(record -> record.warehouseId().equals(warehouseId)).findFirst();
            if (warehouse.isEmpty()) { errorCallback.accept(message("player.warehouse-not-found", warehouseId)); return; }
            List<SlotItemRecord> slots = repository.loadSlots(OWNER_PERSONAL, playerUuid.toString(), warehouseId);
            List<String> lines = new ArrayList<>();
            lines.add(ChatColor.GRAY + "�Ž�家: " + ChatColor.WHITE + playerName);
            lines.add(ChatColor.GRAY + "�“�“: " + ChatColor.WHITE + warehouseId + " (" + warehouse.get().customName() + ")");
            lines.add(ChatColor.GRAY + "�‰��“�槽: " + ChatColor.WHITE + slots.size());
            if (slots.isEmpty()) lines.add(ChatColor.DARK_GRAY + "- 空");
            for (SlotItemRecord slot : slots) lines.add(ChatColor.GRAY + "[" + slot.slot() + "] " + ChatColor.WHITE + slot.displayName() + ChatColor.GRAY + " x" + slot.amount() + " (" + slot.materialId() + ")");
            callback.accept(lines);
        } catch (Exception exception) { errorCallback.accept("读�–�“�“�†…容失败: " + exception.getMessage()); }
    }

    public ActionResult adminDeletePersonalWarehouse(UUID playerUuid, String warehouseId) {
        try {
            Optional<WarehouseRecord> warehouse = repository.loadPersonalWarehouses(playerUuid).stream().filter(record -> record.warehouseId().equals(warehouseId)).findFirst();
            if (warehouse.isEmpty()) return ActionResult.failure(message("player.warehouse-not-found", warehouseId));
            repository.deletePersonalWarehouse(playerUuid, warehouseId);
            Player target = Bukkit.getPlayer(playerUuid);
            if (target != null) refreshBoth(target);
            return ActionResult.success(message("admin.warehouse-deleted", playerUuid, warehouseId));
        } catch (Exception exception) { return ActionResult.failure(message("admin.delete-failed", exception.getMessage())); }
    }

    public void adminClearSecondaryPassword(UUID playerUuid, Consumer<ActionResult> callback) {
        try {
            repository.clearSecurity(playerUuid);
            unlockedUntil.remove(playerUuid);
            callback.accept(ActionResult.success(message("admin.secondary-cleared")));
        } catch (Exception exception) {
            callback.accept(ActionResult.failure(message("admin.password-clear-failed", exception.getMessage())));
        }
    }

    /**
     * 管�†�‘˜�ƒ�•��Ž�家�“��Œ�™额�€‚�”��Œ� set / add / take �‰种模式�€‚
     *
     * @param playerUuid �›��‡�Ž�家 UUID
     * @param currencyId 货币 ID
     * @param mode       �“��œ模式�šset / add / take
     * @param amountText �‡‘额�–‡�œ�
     * @param callback   �“��œ�“�žœ�›ž�ƒ
     */
    public void adminAdjustWallet(UUID playerUuid, String currencyId, String mode, String amountText, Consumer<ActionResult> callback) {
        try {
            String normalizedCurrency = normalizeId(currencyId);
            if (!currencyBridgeManager.currencyIds().contains(normalizedCurrency)) {
                callback.accept(ActionResult.failure(message("admin.unknown-currency", normalizedCurrency)));
                return;
            }
            BigDecimal amount = parseDecimal(amountText);
            BigDecimal current = bankBalance(playerUuid, normalizedCurrency);
            BigDecimal updated = switch (normalizeId(mode)) {
                case "set" -> amount;
                case "add" -> current.add(amount);
                case "take" -> current.subtract(amount).max(BigDecimal.ZERO);
                default -> null;
            };
            if (updated == null) {
                callback.accept(ActionResult.failure(message("admin.unknown-bank-mode", mode)));
                return;
            }
            repository.setBankBalance(playerUuid, normalizedCurrency, updated, System.currentTimeMillis());
            callback.accept(ActionResult.success(message("admin.bank-updated", normalizedCurrency, formatCurrency(normalizedCurrency, updated))));
        } catch (Exception exception) {
            callback.accept(ActionResult.failure(message("admin.bank-update-failed", exception.getMessage())));
        }
    }

    public long totalItems(UUID playerUuid) {
        try {
            return personalRecords(playerUuid).stream()
                .flatMap(record -> loadSlots(OWNER_PERSONAL, playerUuid.toString(), record.warehouseId()).stream())
                .mapToLong(SlotItemRecord::amount)
                .sum();
        } catch (Exception exception) {
            return 0L;
        }
    }

    public long personalUsed(UUID playerUuid) {
        try {
            return personalRecords(playerUuid).stream()
                .mapToLong(record -> loadSlots(OWNER_PERSONAL, playerUuid.toString(), record.warehouseId()).size())
                .sum();
        } catch (Exception exception) {
            return 0L;
        }
    }

    public long personalCapacity(UUID playerUuid) {
        try {
            long capacity = 0L;
            for (WarehouseRecord record : personalRecords(playerUuid)) {
                WarehouseDefinition definition = configuration.warehouse(record.warehouseId());
                WarehouseLevelDefinition level = definition == null ? null : definition.level(record.level());
                capacity += level == null ? SLOT_COUNT : Math.max(SLOT_COUNT, level.capacity());
            }
            return capacity;
        } catch (Exception exception) {
            return 0L;
        }
    }

    public long categoryAmount(UUID playerUuid, String categoryId) {
        try {
            String normalized = normalizeId(categoryId);
            return personalRecords(playerUuid).stream()
                .flatMap(record -> loadSlots(OWNER_PERSONAL, playerUuid.toString(), record.warehouseId()).stream())
                .filter(item -> normalized.equals(item.categoryId()))
                .mapToLong(SlotItemRecord::amount)
                .sum();
        } catch (Exception exception) {
            return 0L;
        }
    }

    public BigDecimal bankBalance(UUID playerUuid, String currencyId) {
        try {
            return repository.loadBankBalances(playerUuid).getOrDefault(normalizeId(currencyId), BigDecimal.ZERO);
        } catch (Exception exception) {
            return BigDecimal.ZERO;
        }
    }

    public long fixedActive(UUID playerUuid, String currencyId) {
        try {
            String normalized = normalizeId(currencyId);
            return repository.loadFixedDeposits(playerUuid).stream().filter(d -> !d.claimed() && normalized.equals(d.currencyId())).count();
        } catch (Exception exception) {
            return 0L;
        }
    }

    public long fixedMatured(UUID playerUuid, String currencyId) {
        try {
            long now = System.currentTimeMillis();
            String normalized = normalizeId(currencyId);
            return repository.loadFixedDeposits(playerUuid).stream().filter(d -> !d.claimed() && d.maturesAt() <= now && normalized.equals(d.currencyId())).count();
        } catch (Exception exception) {
            return 0L;
        }
    }

    public int sharedOwnedCount(UUID playerUuid) {
        try {
            return repository.countOwnedSharedWarehouses(playerUuid);
        } catch (Exception exception) {
            return 0;
        }
    }

    public int sharedJoinedCount(UUID playerUuid) {
        try {
            return repository.loadSharedWarehouses(playerUuid).size();
        } catch (Exception exception) {
            return 0;
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerUuid = event.getPlayer().getUniqueId();
        viewStates.remove(playerUuid);
        unlockedUntil.remove(playerUuid);
        releaseSharedLocks(playerUuid);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player) || !player.isOnline()) {
            return;
        }
        ViewState state = viewStates.get(player.getUniqueId());
        boolean pickup = state != null ? state.autoPickup() : configuration.pickup().autoStoreOnPickup();
        if (!pickup) {
            return;
        }
        ItemStack stack = event.getItem().getItemStack();
        try {
            DepositResult result = depositStack(player, OWNER_PERSONAL, player.getUniqueId().toString(), firstPersonalWarehouseId(), stack);
            if (!result.success()) {
                return;
            }
            stack.setAmount(result.remainingAmount());
            if (result.remainingAmount() <= 0) {
                event.setCancelled(true);
                event.getItem().remove();
            }
            boolean notify = state != null ? state.autoPickupNotify() : configuration.pickup().notifyOnAutoStore();
            if (notify) {
                // �‚�žœ Pickup �€š�Ÿ�模式已为该�Ž�家提�› HUD 提示�Œ�ˆ™跳�‡�Š天栏�ˆ息
                PickupNotifiable pickupNotifiable = pickupNotifiableSupplier.get();
                boolean hudActive = pickupNotifiable != null && pickupNotifiable.isNotificationActive(player.getUniqueId());
                if (!hudActive) {
                    player.sendMessage(PREFIX + ChatColor.GREEN + message("player.auto-deposited", result.storedAmount()));
                }
            }
        } catch (Exception exception) {
            if (configuration.debug()) {
                this.logger.warning("�‡��Š��…��“失败: " + exception.getMessage());
            }
        }
    }

    /**
     * 导�‡�并注�†Œ�‰�— AXUI �–‡件�ˆstorage / manage / bank�‰�Œ�Œ�—�为每�— UI 注�†Œ�…��—��›ž�ƒ�€‚
     */
    private void bindUis() throws Exception {
        storageRuntimeUiId = bindUi(
            configuration.ui().uiId(),
            STORAGE_UI_RESOURCE_PATH,
            STORAGE_UI_FILE_PATH,
            "storage"
        );
        manageRuntimeUiId = bindUi(
            configuration.ui().manageUiId(),
            MANAGE_UI_RESOURCE_PATH,
            MANAGE_UI_FILE_PATH,
            "manage"
        );
        bankRuntimeUiId = bindUi(
            configuration.ui().bankUiId(),
            BANK_UI_RESOURCE_PATH,
            BANK_UI_FILE_PATH,
            "bank"
        );
        if (packetBridge != null) {
            packetBridge.registerUiCloseCallback(storageRuntimeUiId, this::handleUiClosed);
            packetBridge.registerUiCloseCallback(manageRuntimeUiId, this::handleUiClosed);
            packetBridge.registerUiCloseCallback(bankRuntimeUiId, this::handleUiClosed);
        }
    }

    /**
     * 导�‡��•个 UI �„源�–‡件并注�†Œ�ˆ� PacketBridge�€‚
     *
     * @param configuredId   �…�置中�š„ UI ID
     * @param resourcePath   jar �†…�„源路�„
     * @param destinationPath 导�‡��ˆ�磁�›˜�š„�›�对路�„
     * @param uiKind         UI 类�ž‹�‡�†�ˆstorage / manage / bank�‰
     * @return 运�Œ�—� UI ID
     */
    private String bindUi(String configuredId, String resourcePath, String destinationPath, String uiKind) throws Exception {
        PacketBridgeAPI bridge = packetBridge;
        File uiFile = uiResourceExporter.export(resourcePath, destinationPath, configuration.ui().overwriteUiFiles());
        if (bridge == null || !configuration.ui().registerUiOnEnable()) {
            String runtime = xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI.normalizeUiId(configuredId, uiFile);
            if (bridge != null) {
                this.logger.fine("Warehouse UI �‡��Š�注�†Œ已�…��—��Œ�†�›��Ž�使�”� UI �‡�†: " + runtime);
            }
            return runtime;
        }
        xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI.UiRegistrationResult registration = bridge.registerOrReloadUi(configuredId, uiFile);
        if (!registration.success()) {
            throw new IllegalStateException("注�†Œ Warehouse UI 失败: " + registration.message());
        }
        String registered = registration.registeredUiId() == null ? "" : registration.registeredUiId();
        switch (uiKind) {
            case "storage" -> storageRegisteredUiId = registered;
            case "bank" -> bankRegisteredUiId = registered;
            default -> manageRegisteredUiId = registered;
        }
        return registration.runtimeUiId();
    }

    /**
     * �‰“�€�“�“�˜�–�•Œ面并�‘�€��ˆ��‹�Œ–/�›��–��•�据�Œ…�€‚
     * 延�Ÿ 2 ticks �‘�€��Œ避�…��Ž�€��ƒ�客�ˆ�端 UI �Š�载�œ��Œ�ˆ��—�丢失 packet�€‚
     */
    private void openStorage(Player player, String handler) throws Exception {
        ensureEntitlements(player);
        ensureCurrentWarehouse(player, state(player));
        if (state(player).sharedEditMode() && !acquireCurrentSharedLock(player, state(player))) {
            state(player).setSharedEditMode(false);
        }
        packetBridge.openUi(player, storageRuntimeUiId);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            try {
                if (player.isOnline()) {
                    sendStorage(player, handler);
                }
            } catch (Exception exception) {
                this.logger.warning("延�Ÿ�‘�€��“�“�•�据�Œ…失败: " + exception.getMessage());
            }
        }, 2L);
    }

    /**
     * �‰“�€�…�享管�†�•Œ面并�‘�€��ˆ��‹�Œ–/�›��–��•�据�Œ…�€‚
     * 延�Ÿ 2 ticks �‘�€��Œ避�…��Ž�€��ƒ�客�ˆ�端 UI �Š�载�œ��Œ�ˆ��—�丢失 packet�€‚
     */
    private void openManage(Player player, String handler) throws Exception {
        ensureEntitlements(player);
        ensureCurrentWarehouse(player, state(player));
        packetBridge.openUi(player, manageRuntimeUiId);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            try {
                if (player.isOnline()) {
                    sendManage(player, handler);
                }
            } catch (Exception exception) {
                this.logger.warning("延�Ÿ�‘�€�管�†�•�据�Œ…失败: " + exception.getMessage());
            }
        }, 2L);
    }

    /**
     * �‰“�€�“��Œ�•Œ面并�‘�€��ˆ��‹�Œ–/�›��–��•�据�Œ…�€‚
     * 延�Ÿ 2 ticks �‘�€��Œ避�…��Ž�€��ƒ�客�ˆ�端 UI �Š�载�œ��Œ�ˆ��—�丢失 packet�€‚
     */
    private void openBank(Player player, String handler) throws Exception {
        ensureEntitlements(player);
        ensureCurrentWarehouse(player, state(player));
        packetBridge.openUi(player, bankRuntimeUiId);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            try {
                if (player.isOnline()) {
                    sendBank(player, handler);
                }
            } catch (Exception exception) {
                this.logger.warning("延�Ÿ�‘�€��“��Œ�•�据�Œ…失败: " + exception.getMessage());
            }
        }, 2L);
    }

    /**
     * �Œ�—��ˆ��–� storage�€�manage�€�bank �‰个�•Œ面�š„�•�据�Œ…�€‚
     */
    private void refreshBoth(Player player) throws Exception {
        ensureEntitlements(player);
        ensureCurrentWarehouse(player, state(player));
        sendStorage(player, "update");
        sendManage(player, "update");
        sendBank(player, "update");
    }

    private void sendStorage(Player player, String handler) throws Exception {
        if (packetBridge != null && !storageRuntimeUiId.isBlank()) {
            Map<String, Object> packet = buildStoragePacket(player, state(player));
            debug("OUT storage player=" + player.getName() + " uiId=" + storageRuntimeUiId + " handler=" + handler + " " + summarizeStoragePacket(packet));
            boolean success = packetBridge.sendPacket(player, storageRuntimeUiId, handler, packet);
            debug("OUT storage-result player=" + player.getName() + " handler=" + handler + " success=" + success);
        }
    }

    private void sendManage(Player player, String handler) throws Exception {
        if (packetBridge != null && !manageRuntimeUiId.isBlank()) {
            Map<String, Object> packet = buildManagePacket(player, state(player));
            debug("OUT manage player=" + player.getName() + " uiId=" + manageRuntimeUiId + " handler=" + handler + " " + summarizeManagePacket(packet));
            boolean success = packetBridge.sendPacket(player, manageRuntimeUiId, handler, packet);
            debug("OUT manage-result player=" + player.getName() + " handler=" + handler + " success=" + success);
        }
    }

    private void sendBank(Player player, String handler) throws Exception {
        if (packetBridge != null && !bankRuntimeUiId.isBlank()) {
            Map<String, Object> packet = buildBankPacket(player, state(player));
            debug("OUT bank player=" + player.getName() + " uiId=" + bankRuntimeUiId + " handler=" + handler + " balances=" + mapValue(packet.get("balances")).size());
            boolean success = packetBridge.sendPacket(player, bankRuntimeUiId, handler, packet);
            debug("OUT bank-result player=" + player.getName() + " handler=" + handler + " success=" + success);
        }
    }

    private void handleUiClosed(Player player) {
        if (player == null) {
            return;
        }
        releaseCurrentSharedLock(player);
        // �„�ˆ模式�‹�…��—� UI 即�€€�‡��„�ˆ�Š��€��Œ避�…��Ž�家"卡"�œ��„�ˆ中
        ViewState state = viewStates.get(player.getUniqueId());
        if (state != null && state.previewMode()) {
            viewStates.remove(player.getUniqueId());
        }
    }

    /**
     * �ž„建�“�“�˜�–�•Œ面�š„�•�据�Œ…�Œ�Œ…含�ˆ†页槽位�€��€‰中�‰��“��€��ƒŒ�Œ…信息�€�容�‡��Ž�ƒ�™��Š��€��€‚
     */
    private Map<String, Object> buildStoragePacket(Player player, ViewState state) throws Exception {
        List<SlotItemRecord> visibleSlots = visibleSlots(state);
        String slotOrder = visibleSlots.stream().map(i -> String.valueOf(i.slot())).collect(Collectors.joining(","));
        debug("buildStoragePacket visibleSlots order: " + slotOrder);
        Map<String, Object> slots = new LinkedHashMap<>();
        Map<String, Object> packet = basePacket(player, state);
        int pageSize = SLOT_COUNT;
        int pageTotal = Math.max(1, (visibleSlots.size() + pageSize - 1) / pageSize);
        state.setPage(Math.max(1, Math.min(state.page(), pageTotal)));
        int start = (state.page() - 1) * pageSize;
        for (int displaySlot = 0; displaySlot < SLOT_COUNT; displaySlot++) {
            int index = start + displaySlot;
            SlotItemRecord item = index >= 0 && index < visibleSlots.size()
                ? visibleSlots.get(index)
                : null;
            Map<String, Object> row = item != null
                ? slotPacket(item)
                : emptySlotPacket(displaySlot);
            String key = String.valueOf(displaySlot);
            row.put("key", key);
            slots.put(key, row);
            if (item != null) {
                String itemJson = displayItemJson(item);
                if (!itemJson.isBlank()) {
                    packet.put("slotItemJson" + key, itemJson);
                }
            }
        }
        String slotKeys = String.join(",", slots.keySet());
        debug("buildStoragePacket slots keys order: " + slotKeys);
        int selectedDisplaySlot = selectedDisplaySlot(state, visibleSlots);
        SlotItemRecord selected = validSlot(state.selectedSlot())
            ? repository.loadSlot(state.ownerType(), state.ownerId(), state.warehouseId(), state.selectedSlot()).orElse(null)
            : null;
        packet.put("ui", "storage");
        packet.put("slots", slots);
        packet.put("slotCount", SLOT_COUNT);
        packet.put("selectedSlot", selectedDisplaySlot);
        packet.put("selectedActualSlot", state.selectedSlot());
        packet.put("selectedItem", selected == null ? emptySelectionPacket() : slotPacket(selected));
        packet.put("backpack", backpackPacket(player));
        packet.put("page", state.page());
        packet.put("pageTotal", pageTotal);
        packet.put("pageText", state.page() + "/" + pageTotal);
        packet.put("usedSlots", loadSlots(state.ownerType(), state.ownerId(), state.warehouseId()).size());
        packet.put("matchedSlots", visibleSlots.size());
        putCapacityFields(player, state, packet);
        packet.put("readOnly", !canModifyCurrent(player, state));
        packet.put("sharedEditMode", OWNER_SHARED.equals(state.ownerType()) && state.sharedEditMode());
        packet.put("sharedCanEdit", sharedCanEdit(player, state));
        packet.put("lockOwner", lockOwnerName(state));
        packet.put("maxPersonalCount", ((java.util.Map<?,?>) packet.getOrDefault("personalWarehouses", java.util.Map.of())).size());
        packet.put("maxSharedCount", ((java.util.Map<?,?>) packet.getOrDefault("sharedWarehouses", java.util.Map.of())).size());
        packet.put("maxCategoryCount", ((java.util.Map<?,?>) packet.getOrDefault("categories", java.util.Map.of())).size());
        return packet;
    }

    private void putFlatSlotFields(Map<String, Object> packet, int slot, Map<String, Object> row) {
        packet.put("slotJson" + slot, row.getOrDefault("itemJson", ""));
        packet.put("slotAmount" + slot, row.getOrDefault("amount", 0L));
        packet.put("slotEmpty" + slot, row.getOrDefault("empty", true));
        packet.put("slotMatched" + slot, row.getOrDefault("matched", true));
        packet.put("slotActual" + slot, row.getOrDefault("slot", -1));
    }

    private List<SlotItemRecord> visibleSlots(ViewState state) {
        String normalizedSearch = normalizeToken(state.search());
        return loadSlots(state.ownerType(), state.ownerId(), state.warehouseId()).stream()
            .filter(item -> slotMatches(item, state.categoryId(), normalizedSearch))
            .sorted(Comparator.comparingInt(SlotItemRecord::slot))
            .toList();
    }

    private int selectedDisplaySlot(ViewState state, List<SlotItemRecord> visibleSlots) {
        if (!validSlot(state.selectedSlot())) {
            return -1;
        }
        int start = (Math.max(1, state.page()) - 1) * SLOT_COUNT;
        for (int index = start; index < Math.min(visibleSlots.size(), start + SLOT_COUNT); index++) {
            if (visibleSlots.get(index).slot() == state.selectedSlot()) {
                return index - start;
            }
        }
        return -1;
    }

    private int actualSlotFromDisplay(ViewState state, int displaySlot) {
        if (displaySlot < 0 || displaySlot >= SLOT_COUNT) {
            return -1;
        }
        List<SlotItemRecord> visibleSlots = visibleSlots(state);
        int index = (Math.max(1, state.page()) - 1) * SLOT_COUNT + displaySlot;
        return index >= 0 && index < visibleSlots.size() ? visibleSlots.get(index).slot() : -1;
    }

    /**
     * �ž„建�…�享管�†�•Œ面�š„�•�据�Œ…�Œ�Œ…含�ˆ��‘˜�ˆ—表�€��œ索�“�žœ�€��‡��Š��‹��–设置�‰�€‚
     */
    private Map<String, Object> buildManagePacket(Player player, ViewState state) throws Exception {
        Map<String, Object> packet = basePacket(player, state);
        Map<String, Object> sharedMembers = sharedMemberPacket(player, state);
        Map<String, Object> searchResults = searchResultPacket(state);
        packet.put("ui", "manage");
        putCapacityFields(player, state, packet);
        packet.put("sharedMembers", sharedMembers);
        packet.put("sharedMemberTexts", fieldMap(sharedMembers, "text"));
        packet.put("sharedMemberNames", fieldMap(sharedMembers, "name"));
        packet.put("sharedMemberRoles", fieldMap(sharedMembers, "role"));
        packet.put("searchResults", searchResults);
        packet.put("searchResultTexts", fieldMap(searchResults, "text"));
        packet.put("searchResultSlots", fieldMap(searchResults, "slot"));
        packet.put("readOnly", !canModifyCurrent(player, state));
        packet.put("sharedEditMode", OWNER_SHARED.equals(state.ownerType()) && state.sharedEditMode());
        packet.put("sharedCanEdit", sharedCanEdit(player, state));
        packet.put("lockOwner", lockOwnerName(state));
        packet.put("autoPickup", state.autoPickup());
        packet.put("autoPickupMythic", state.autoPickupMythic());
        packet.put("autoPickupNotify", state.autoPickupNotify());
        packet.put("showcaseEnabled", currentShowcaseEnabled(player, state));
        packet.put("maxManageCount", ((java.util.Map<?,?>) packet.getOrDefault("manageWarehouses", java.util.Map.of())).size());
        packet.put("maxMemberCount", sharedMembers.size());
        return packet;
    }

    /**
     * �ž„建�“��Œ�•Œ面�š„�•�据�Œ…�Œ�Œ…含活�œŸ�™额�€��š�œŸ产�“��’Œ�“�‰��š�œŸ�ˆ—表�€‚
     */
    private Map<String, Object> buildBankPacket(Player player, ViewState state) throws Exception {
        Map<String, Object> packet = basePacket(player, state);
        Map<String, Object> balances = bankBalancePacket(player);
        Map<String, Object> products = depositProductPacket(player);
        Map<String, Object> fixedDeposits = fixedDepositPacket(player);
        packet.put("ui", "bank");
        packet.put("balances", balances);
        packet.put("balanceTexts", fieldMap(balances, "text"));
        packet.put("products", products);
        packet.put("productTexts", fieldMap(products, "text"));
        packet.put("fixedDeposits", fixedDeposits);
        packet.put("fixedDepositTexts", fieldMap(fixedDeposits, "text"));
        packet.put("maxBalanceCount", balances.size());
        packet.put("maxProductCount", products.size());
        packet.put("maxFixedCount", fixedDeposits.size());
        return packet;
    }

    private Map<String, Object> basePacket(Player player, ViewState state) throws Exception {
        Map<String, Object> packet = new LinkedHashMap<>();
        Map<String, Object> categories = categoryPacket();
        Map<String, Object> personalWarehouses;
        Map<String, Object> sharedWarehouses;
        if (state.previewMode() && state.previewTargetUuid() != null) {
            personalWarehouses = previewPersonalWarehousePacket(state.previewTargetUuid());
            sharedWarehouses = previewSharedWarehousePacket(state.previewTargetUuid());
        } else {
            personalWarehouses = personalWarehousePacket(player);
            sharedWarehouses = sharedWarehousePacket(player);
        }
        Map<String, Object> manageWarehouses = manageWarehousePacket(personalWarehouses, sharedWarehouses, state);
        packet.put("packetId", configuration.ui().packetId());
        packet.put("ownerType", state.ownerType());
        packet.put("ownerId", state.ownerId());
        packet.put("warehouseId", state.warehouseId());
        packet.put("warehouseName", currentWarehouseName(player, state));
        packet.put("categoryId", state.categoryId());
        packet.put("search", state.search());
        packet.put("categories", categories);
        packet.put("categoryTexts", fieldMap(categories, "text"));
        packet.put("unlocked", isSecondaryUnlocked(player.getUniqueId()));
        packet.put("hasPassword", repository.loadSecurity(player.getUniqueId()).isPresent());
        packet.put("personalWarehouses", personalWarehouses);
        packet.put("personalWarehouseTexts", fieldMap(personalWarehouses, "text"));
        packet.put("sharedWarehouses", sharedWarehouses);
        packet.put("sharedWarehouseStorageTexts", fieldMap(sharedWarehouses, "storageText"));
        packet.put("sharedWarehouseManageTexts", fieldMap(sharedWarehouses, "manageText"));
        packet.put("manageWarehouses", manageWarehouses);
        packet.put("manageWarehouseTexts", fieldMap(manageWarehouses, "text"));
        packet.put("manageWarehouseTypes", fieldMap(manageWarehouses, "ownerType"));
        packet.put("manageWarehouseTargets", fieldMap(manageWarehouses, "target"));
        packet.put("manageWarehouseSelected", fieldMap(manageWarehouses, "selected"));
        packet.put("sharedCreateCostText", sharedCreateCostText());
        packet.put("sharedRoleOwnerName", sharedRoleName("owner"));
        packet.put("sharedRoleMemberName", sharedRoleName("member"));
        packet.put("sharedRoleViewerName", sharedRoleName("viewer"));
        packet.put("storageUiId", storageRuntimeUiId);
        packet.put("manageUiId", manageRuntimeUiId);
        packet.put("bankUiId", bankRuntimeUiId);
        return packet;
    }

    private Map<String, Object> fieldMap(Map<String, Object> rows, String field) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : rows.entrySet()) {
            Object row = entry.getValue();
            if (row instanceof Map<?, ?> map) {
                Object value = map.get(field);
                result.put(entry.getKey(), value == null ? "" : value);
            } else {
                result.put(entry.getKey(), "");
            }
        }
        return result;
    }

    private void setCategory(Player player, String categoryId) throws Exception {
        ViewState state = state(player);
        state.setCategoryId(normalizeId(categoryId).isBlank() ? "all" : normalizeId(categoryId));
        state.setPage(1);
        clearFilteredSelection(state);
        refreshBoth(player);
    }

    private void setSearch(Player player, String search) throws Exception {
        ViewState state = state(player);
        state.setSearch(crop(safe(search), 32));
        state.setPage(1);
        clearFilteredSelection(state);
        refreshBoth(player);
    }

    private void setPage(Player player, int page) throws Exception {
        ViewState state = state(player);
        state.setPage(Math.max(1, page));
        clearFilteredSelection(state);
        sendStorage(player, "update");
    }

    private void selectSlot(Player player, int displaySlot) throws Exception {
        ViewState state = state(player);
        state.setSelectedSlot(actualSlotFromDisplay(state, displaySlot));
        sendStorage(player, "update");
    }

    private void selectPersonalWarehouse(Player player, String warehouseId) throws Exception {
        releaseCurrentSharedLock(player);
        ViewState state = state(player);
        state.setOwnerType(OWNER_PERSONAL);
        if (state.previewMode() && state.previewTargetUuid() != null) {
            state.setOwnerId(state.previewTargetUuid().toString());
        } else {
            state.setOwnerId(player.getUniqueId().toString());
        }
        state.setWarehouseId(normalizeId(warehouseId).isBlank() ? firstPersonalWarehouseId() : normalizeId(warehouseId));
        state.setSelectedSlot(-1);
        state.setPage(1);
        state.setSharedEditMode(false);
        ensureCurrentWarehouse(player, state);
        refreshBoth(player);
    }

    private void selectSharedWarehouse(Player player, String sharedId) throws Exception {
        Optional<SharedWarehouseRecord> selected = repository.loadSharedWarehouses(player.getUniqueId()).stream()
            .filter(shared -> shared.id().equalsIgnoreCase(sharedId))
            .findFirst();
        if (selected.isEmpty()) {
            sendMessage(player, false, message("player.not-shared-member"));
            refreshBoth(player);
            return;
        }
        releaseCurrentSharedLock(player);
        ViewState state = state(player);
        SharedWarehouseRecord shared = selected.get();
        state.setOwnerType(OWNER_SHARED);
        state.setOwnerId(shared.id());
        state.setWarehouseId(shared.id());
        state.setSelectedSlot(-1);
        state.setPage(1);
        state.setSharedEditMode(false);
        refreshBoth(player);
    }

    private void selectPreviewSharedWarehouse(Player player, String sharedId) throws Exception {
        Optional<SharedWarehouseRecord> selected = repository.loadSharedWarehousesByOwner(state(player).previewTargetUuid()).stream()
            .filter(shared -> shared.id().equalsIgnoreCase(sharedId) && shared.showcaseEnabled())
            .findFirst();
        if (selected.isEmpty()) {
            sendMessage(player, false, message("player.shared-preview-disabled"));
            refreshBoth(player);
            return;
        }
        ViewState state = state(player);
        SharedWarehouseRecord shared = selected.get();
        state.setOwnerType(OWNER_SHARED);
        state.setOwnerId(shared.id());
        state.setWarehouseId(shared.id());
        state.setSelectedSlot(-1);
        state.setPage(1);
        state.setSharedEditMode(false);
        refreshBoth(player);
    }

    private void setSharedMode(Player player, String mode) throws Exception {
        ViewState state = state(player);
        if (!OWNER_SHARED.equals(state.ownerType())) {
            sendMessage(player, false, message("player.not-shared-warehouse"));
            refreshBoth(player);
            return;
        }
        if ("edit".equalsIgnoreCase(normalizeId(mode))) {
            if (!sharedCanEdit(player, state)) {
                state.setSharedEditMode(false);
                sendMessage(player, false, message("player.shared-no-permission"));
                refreshBoth(player);
                return;
            }
            state.setSharedEditMode(true);
            if (!acquireCurrentSharedLock(player, state)) {
                state.setSharedEditMode(false);
            }
            refreshBoth(player);
            return;
        }
        state.setSharedEditMode(false);
        releaseCurrentSharedLock(player);
        sendMessage(player, true, message("player.shared-read-only-mode"));
        refreshBoth(player);
    }

    private void toggleAutoPickup(Player player, String kind) throws Exception {
        ViewState state = state(player);
        switch (kind) {
            case "pickup" -> {
                state.setAutoPickup(!state.autoPickup());
                sendMessage(player, true, message("player.auto-pickup", state.autoPickup() ? message("player.enabled") : message("player.disabled")));
            }
            case "mythic" -> {
                state.setAutoPickupMythic(!state.autoPickupMythic());
                sendMessage(player, true, message("player.auto-pickup-mythic", state.autoPickupMythic() ? message("player.enabled") : message("player.disabled")));
            }
            case "notify" -> {
                state.setAutoPickupNotify(!state.autoPickupNotify());
                sendMessage(player, true, message("player.deposit-notify", state.autoPickupNotify() ? message("player.enabled") : message("player.disabled")));
            }
        }
        refreshBoth(player);
    }

    /**
     * �†�Ž�家�ƒŒ�Œ…�Œ‡�š槽位�š„�‰��“��˜�…��“�‰��“�“�€‚
     * �š�€�Ÿ�只读�ƒ�™��€��‘名�•�’Œ容�‡��Š�™��Œ�ˆ��ŠŸ�—��‰��™��ƒŒ�Œ…�‰��“�并�ˆ��–� UI�€‚
     * �”��Œ��Œ‡�š�˜�…��•��‡��ˆrequestedAmount�‰�Œ�‹�大�Ž�ž�™…�†叠�•��‡��ˆ™�Œ‰�ž�™…�•��‡��˜�…��€‚
     */
    private void depositSlot(Player player, String rawSlotValue, long requestedAmount) throws Exception {
        ViewState state = state(player);
        debug("DEPOSIT start player=" + player.getName() + " rawArg=" + safe(rawSlotValue)
            + " amount=" + requestedAmount + " ownerType=" + state.ownerType() + " ownerId=" + state.ownerId() + " warehouseId=" + state.warehouseId());
        if (!canModifyCurrent(player, state)) {
            debug("DEPOSIT reject player=" + player.getName() + " reason=read-only-or-locked");
            sendMessage(player, false, message("player.read-only-deposit"));
            refreshBoth(player);
            return;
        }
        int rawSlot = parseRawBackpackSlot(rawSlotValue);
        if (rawSlot < 9 || rawSlot > 44) {
            debug("DEPOSIT reject player=" + player.getName() + " reason=invalid-raw rawArg=" + safe(rawSlotValue) + " parsed=" + rawSlot);
            sendMessage(player, false, message("player.invalid-slot", safe(rawSlotValue)));
            refreshBoth(player);
            return;
        }
        int slot = toBukkitBackpackSlot(rawSlot);
        ItemStack stack = player.getInventory().getItem(slot);
        debug("DEPOSIT slot player=" + player.getName() + " rawSlot=" + rawSlot + " bukkitSlot=" + slot + " stack=" + describeStack(stack));
        if (stack == null || stack.getType().isAir()) {
            debug("DEPOSIT reject player=" + player.getName() + " reason=empty rawSlot=" + rawSlot + " bukkitSlot=" + slot);
            sendMessage(player, false, message("player.slot-empty"));
            refreshBoth(player);
            return;
        }
        if (itemMatcherSupport.matches(configuration.blacklist(), stack)) {
            debug("DEPOSIT reject player=" + player.getName() + " reason=blacklist stack=" + describeStack(stack));
            sendMessage(player, false, message("player.item-blocked"));
            refreshBoth(player);
            return;
        }
        int originalAmount = stack.getAmount();
        int depositAmount = (requestedAmount <= 0) ? originalAmount : (int) Math.min(originalAmount, requestedAmount);
        ItemStack depositStack = stack.clone();
        depositStack.setAmount(depositAmount);
        DepositResult result = depositStack(player, state.ownerType(), state.ownerId(), state.warehouseId(), depositStack);
        debug("DEPOSIT result player=" + player.getName() + " success=" + result.success()
            + " stored=" + result.storedAmount() + " remaining=" + result.remainingAmount() + " message=" + result.message());
        if (!result.success()) {
            sendMessage(player, false, result.message());
            refreshBoth(player);
            return;
        }
        int remainingInSlot = originalAmount - (int) result.storedAmount();
        if (remainingInSlot <= 0) {
            player.getInventory().setItem(slot, null);
        } else {
            ItemStack remaining = stack.clone();
            remaining.setAmount(remainingInSlot);
            player.getInventory().setItem(slot, remaining);
        }
        player.updateInventory();
        debug("DEPOSIT inventory-updated player=" + player.getName() + " bukkitSlot=" + slot + " newStack=" + describeStack(player.getInventory().getItem(slot)));
        sendMessage(player, true, message("player.deposit-success", result.storedAmount()));
        refreshBoth(player);
    }

    /**
     * �€�”��˜�…��ƒŒ�Œ…�…��ƒ��‰��“��ˆ主�“�˜ 9~44 槽�‰�€‚
     * �€�件�€�Ÿ��‘名�•�’Œ容�‡��Œ跳�‡不可�˜�‰��“��Œ�Ÿ计�˜�…��“�žœ�€‚
     */
    private void depositAllBackpack(Player player) throws Exception {
        ViewState state = state(player);
        debug("DEPOSIT-ALL start player=" + player.getName()
            + " ownerType=" + state.ownerType() + " ownerId=" + state.ownerId() + " warehouseId=" + state.warehouseId());
        if (!canModifyCurrent(player, state)) {
            debug("DEPOSIT-ALL reject player=" + player.getName() + " reason=read-only-or-locked");
            sendMessage(player, false, message("player.read-only-deposit"));
            refreshBoth(player);
            return;
        }
        PlayerInventory inventory = player.getInventory();
        long totalStored = 0L;
        int changedSlots = 0;
        int skippedBlacklisted = 0;
        int failedSlots = 0;
        boolean foundItem = false;
        for (int rawSlot = 9; rawSlot <= 44; rawSlot++) {
            int slot = toBukkitBackpackSlot(rawSlot);
            ItemStack stack = slot >= 0 && slot < inventory.getSize() ? inventory.getItem(slot) : null;
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            foundItem = true;
            if (itemMatcherSupport.matches(configuration.blacklist(), stack)) {
                skippedBlacklisted++;
                continue;
            }
            DepositResult result = depositStack(player, state.ownerType(), state.ownerId(), state.warehouseId(), stack);
            if (!result.success()) {
                failedSlots++;
                continue;
            }
            totalStored += result.storedAmount();
            changedSlots++;
            if (result.remainingAmount() <= 0) {
                inventory.setItem(slot, null);
            } else {
                ItemStack remaining = stack.clone();
                remaining.setAmount(result.remainingAmount());
                inventory.setItem(slot, remaining);
            }
        }
        if (totalStored > 0L) {
            player.updateInventory();
            String suffix = skippedBlacklisted > 0 || failedSlots > 0
                ? "�Œ跳�‡ " + skippedBlacklisted + " 格禁�˜�‰��“��Œ" + failedSlots + " 格�œ��ƒ��˜�…��€‚"
                : "�€‚";
            sendMessage(player, true, message("player.deposit-from-inventory", totalStored, changedSlots, suffix));
        } else if (!foundItem) {
            sendMessage(player, false, message("player.no-deposit-items"));
        } else if (skippedBlacklisted > 0 && failedSlots == 0) {
            sendMessage(player, false, message("player.all-items-blocked"));
        } else {
            sendMessage(player, false, message("player.deposit-none"));
        }
        refreshBoth(player);
    }

    /**
     * 核�ƒ�˜�…��€��‘�š�†�‰��“��†�ˆ�˜�…��Œ‡�š�“�“�€‚
     * �˜�…ˆ尝�•�Ž已�œ‰�Œ hash 槽位�ˆ并�ˆ�š�ˆ�Š�™� {@link #MAX_AGGREGATED_AMOUNT}�‰�Œ
     * �—��•�ˆ并�ˆ™占�”��–�空槽�€‚�‹��“�“已满�ˆ™�”�›ž失败�€‚
     *
     * @param player      �“��œ�Ž�家�ˆ�”��Ž�—��—�Ž debug�‰
     * @param ownerType   {@code personal} �ˆ– {@code shared}
     * @param ownerId     �‰€�œ‰�€…�‡�†�ˆUUID �—符串�ˆ–�…�享�“�“ ID�‰
     * @param warehouseId �“�“ ID
     * @param stack       �…�˜�…��‰��“��ˆamount 可�ƒ��ƒ��ˆ†�˜�…��‰
     * @return �˜�…��“�žœ
     */
    private DepositResult depositStack(Player player, String ownerType, String ownerId, String warehouseId, ItemStack stack) throws Exception {
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0 || itemMatcherSupport.matches(configuration.blacklist(), stack)) {
            debug("DEPOSIT-STACK reject player=" + player.getName() + " reason=invalid-stack stack=" + describeStack(stack));
            return DepositResult.failure(message("player.item-not-depositable"));
        }
        if (OWNER_SHARED.equals(ownerType) && !canModifyCurrent(player, state(player))) {
            debug("DEPOSIT-STACK reject player=" + player.getName() + " reason=shared-read-only ownerId=" + ownerId);
            return DepositResult.failure(message("player.shared-read-only"));
        }
        ItemStack prototype = stack.clone();
        prototype.setAmount(1);
        byte[] bytes = ItemSerializer.serialize(prototype);
        String itemData = Base64.getEncoder().encodeToString(bytes);
        String itemHash = sha256Hex(bytes);
        long now = System.currentTimeMillis();
        int remaining = stack.getAmount();
        long stored = 0L;
        List<SlotItemRecord> currentSlots = repository.loadSlots(ownerType, ownerId, warehouseId);
        debug("DEPOSIT-STACK load player=" + player.getName() + " ownerType=" + ownerType + " ownerId=" + ownerId
            + " warehouseId=" + warehouseId + " currentSlots=" + currentSlots.size() + " hash=" + shortHash(itemHash)
            + " itemJsonEmpty=" + itemJson(prototype, 1L).isBlank());

        for (SlotItemRecord current : currentSlots) {
            if (remaining <= 0 || !itemHash.equals(current.itemHash())) {
                continue;
            }
            long room = Math.max(0L, MAX_AGGREGATED_AMOUNT - current.amount());
            int moved = (int) Math.min(remaining, room);
            if (moved > 0) {
                repository.upsertSlot(copySlot(current, current.amount() + moved, now));
                debug("DEPOSIT-STACK merge player=" + player.getName() + " slot=" + current.slot()
                    + " moved=" + moved + " oldAmount=" + current.amount() + " newAmount=" + (current.amount() + moved));
                remaining -= moved;
                stored += moved;
            }
        }

        if (remaining > 0) {
            int emptySlot = firstEmptySlot(currentSlots, currentWarehouseCapacity(player, ownerType, ownerId, warehouseId));
            if (emptySlot >= 0) {
                SearchTokens searchTokens = toSearchTokens(resolveDisplayName(prototype) + " " + prototype.getType().name() + " " + resolveCategory(prototype));
                String displayJson = itemJson(prototype, 1L);
                repository.upsertSlot(new SlotItemRecord(
                    ownerType,
                    ownerId,
                    warehouseId,
                    emptySlot,
                    itemHash,
                    resolveCategory(prototype),
                    resolveDisplayName(prototype),
                    prototype.getType().name(),
                    searchTokens.searchText(),
                    searchTokens.pinyin(),
                    searchTokens.initials(),
                    itemData,
                    displayJson,
                    remaining,
                    now,
                    now
                ));
                debug("DEPOSIT-STACK insert player=" + player.getName() + " slot=" + emptySlot + " amount=" + remaining
                    + " item=" + describeStack(prototype) + " itemJsonEmpty=" + displayJson.isBlank());
                stored += remaining;
                remaining = 0;
            } else {
                debug("DEPOSIT-STACK no-empty-slot player=" + player.getName() + " currentSlots=" + currentSlots.size());
            }
        }

        if (stored <= 0L) {
            debug("DEPOSIT-STACK failure player=" + player.getName() + " reason=no-stored remaining=" + remaining);
            return DepositResult.failure(message("player.warehouse-full"));
        }
        return DepositResult.success(stored, remaining);
    }

    private SlotItemRecord copySlot(SlotItemRecord current, long amount, long updatedAt) {
        return new SlotItemRecord(
            current.ownerType(),
            current.ownerId(),
            current.warehouseId(),
            current.slot(),
            current.itemHash(),
            current.categoryId(),
            current.displayName(),
            current.materialId(),
            current.searchText(),
            current.pinyin(),
            current.initials(),
            current.itemData(),
            current.itemJson(),
            amount,
            current.createdAt(),
            updatedAt
        );
    }

    /**
     * �Ž�“�‰��“�“�–�‡��‰��“��ˆ��Ž�家�ƒŒ�Œ…�€‚
     * �œ€要�Œ级�†码已解�”��Œ�”�“�‰��“�“可�†™�€‚�Œ‰�†叠�Š�™��ˆ†�‰��™�ˆ�Œ�ƒŒ�Œ…满�—��œ止�€‚
     */
    private void withdraw(Player player, int slot, long requestedAmount, boolean all) throws Exception {
        if (!isSecondaryUnlocked(player.getUniqueId())) {
            sendMessage(player, false, message("player.secondary-required-withdraw"));
            refreshBoth(player);
            return;
        }
        ViewState state = state(player);
        if (!canModifyCurrent(player, state)) {
            sendMessage(player, false, message("player.read-only-withdraw"));
            refreshBoth(player);
            return;
        }
        int selectedSlot = slot >= 0 && slot < SLOT_COUNT ? actualSlotFromDisplay(state, slot) : -1;
        if (!validSlot(selectedSlot)) {
            sendMessage(player, false, message("player.no-slot-selected"));
            refreshBoth(player);
            return;
        }
        Optional<SlotItemRecord> optional = repository.loadSlot(state.ownerType(), state.ownerId(), state.warehouseId(), selectedSlot);
        if (optional.isEmpty()) {
            sendMessage(player, false, message("player.slot-no-item"));
            refreshBoth(player);
            return;
        }
        SlotItemRecord item = optional.get();
        ItemStack base = ItemSerializer.deserialize(Base64.getDecoder().decode(item.itemData()));
        if (base == null || base.getType().isAir()) {
            repository.deleteSlot(state.ownerType(), state.ownerId(), state.warehouseId(), selectedSlot);
            sendMessage(player, false, message("player.slot-corrupt"));
            refreshBoth(player);
            return;
        }
        long remaining = all ? item.amount() : Math.max(1L, Math.min(requestedAmount, item.amount()));
        long delivered = 0L;
        while (remaining > 0L) {
            int give = (int) Math.min(remaining, Math.max(1, base.getMaxStackSize()));
            ItemStack clone = base.clone();
            clone.setAmount(give);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(clone);
            long left = leftovers.values().stream().mapToLong(ItemStack::getAmount).sum();
            long moved = give - left;
            delivered += moved;
            remaining -= moved;
            if (left > 0L || moved <= 0L) {
                break;
            }
        }
        if (delivered <= 0L) {
            sendMessage(player, false, message("player.inventory-full"));
            refreshBoth(player);
            return;
        }
        long updatedAmount = item.amount() - delivered;
        if (updatedAmount <= 0L) {
            repository.deleteSlot(state.ownerType(), state.ownerId(), state.warehouseId(), selectedSlot);
            state.setSelectedSlot(-1);
        } else {
            repository.upsertSlot(copySlot(item, updatedAmount, System.currentTimeMillis()));
        }
        sendMessage(player, true, message("player.withdraw-success", delivered));
        refreshBoth(player);
    }

    /**
     * �Ž�家�†�ƒŒ�Œ…货币�˜�…��“��Œ活�œŸ账�ˆ��€‚
     * �…ˆ�€š�‡货币桥�Ž��‰�款�Œ�†��†™�…��•�据�“�›失败�—��‡��Š��›ž�š�€‚
     */
    private void bankDeposit(Player player, String currencyId, BigDecimal amount) throws Exception {
        String normalized = normalizeId(currencyId);
        var bridge = currencyBridgeManager.bridge(normalized);
        if (bridge == null || !bridge.available()) {
            sendMessage(player, false, bridge == null ? message("player.unknown-currency") : bridge.unavailableReason());
            refreshBoth(player);
            return;
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            sendMessage(player, false, message("player.amount-positive"));
            refreshBoth(player);
            return;
        }
        var result = bridge.withdraw(player, amount);
        if (!result.success()) {
            sendMessage(player, false, result.message());
            refreshBoth(player);
            return;
        }
        try {
            repository.creditBankBalance(player.getUniqueId(), normalized, amount, System.currentTimeMillis());
        } catch (Exception exception) {
            bridge.deposit(player, amount);
            throw exception;
        }
        sendMessage(player, true, message("player.bank-deposit-success", formatCurrency(normalized, amount)));
        refreshBoth(player);
    }

    /**
     * �Ž�家�Ž�“��Œ活�œŸ账�ˆ�提�Ž��ˆ��ƒŒ�Œ…�€‚
     * �…ˆ�ŽŸ子�‰��‡��•�据�“�™额�Œ�†��€š�‡货币桥�Ž��”�款�›失败�—��‡��Š��›ž�š�€‚
     */
    private void bankWithdraw(Player player, String currencyId, BigDecimal amount) throws Exception {
        if (!isSecondaryUnlocked(player.getUniqueId())) {
            sendMessage(player, false, message("player.secondary-required-withdraw-bank"));
            refreshBoth(player);
            return;
        }
        String normalized = normalizeId(currencyId);
        var bridge = currencyBridgeManager.bridge(normalized);
        if (bridge == null || !bridge.available()) {
            sendMessage(player, false, bridge == null ? message("player.unknown-currency") : bridge.unavailableReason());
            refreshBoth(player);
            return;
        }
        BigDecimal current = bankBalance(player.getUniqueId(), normalized);
        if (amount.compareTo(BigDecimal.ZERO) <= 0 || current.compareTo(amount) < 0) {
            sendMessage(player, false, message("player.bank-invalid-amount"));
            refreshBoth(player);
            return;
        }
        long now = System.currentTimeMillis();
        if (!repository.debitBankBalance(player.getUniqueId(), normalized, amount, now)) {
            sendMessage(player, false, message("player.bank-invalid-amount"));
            refreshBoth(player);
            return;
        }
        var result = bridge.deposit(player, amount);
        if (!result.success()) {
            repository.creditBankBalance(player.getUniqueId(), normalized, amount, now);
            sendMessage(player, false, result.message());
            refreshBoth(player);
            return;
        }
        sendMessage(player, true, message("player.bank-withdraw-success", formatCurrency(normalized, amount)));
        refreshBoth(player);
    }

    /**
     * 购买�š�œŸ�˜款产�“��€‚
     * 校�Œ�‡‘额�Œ��—��€��Œ��…��ˆ��Ž‡�˜�梯�€��ŽŸ子�‰��‡�活�œŸ�™额�Ž�ˆ›建�š�œŸ记�•�€‚
     */
    private void createFixedDeposit(Player player, String productId, BigDecimal amount) throws Exception {
        DepositProductDefinition product = configuration.depositProduct(productId);
        if (product == null || !canUseProduct(player, product)) {
            sendMessage(player, false, message("player.fixed-deposit-unavailable"));
            refreshBoth(player);
            return;
        }
        if (amount.compareTo(product.minAmount()) < 0 || (product.maxAmount().compareTo(BigDecimal.ZERO) > 0 && amount.compareTo(product.maxAmount()) > 0)) {
            sendMessage(player, false, message("player.fixed-deposit-range"));
            refreshBoth(player);
            return;
        }
        InterestTier tier = product.tierFor(amount);
        if (tier == null) {
            sendMessage(player, false, message("player.fixed-deposit-tier"));
            refreshBoth(player);
            return;
        }
        BigDecimal current = bankBalance(player.getUniqueId(), product.currencyId());
        if (current.compareTo(amount) < 0) {
            sendMessage(player, false, message("player.bank-insufficient"));
            refreshBoth(player);
            return;
        }
        long now = System.currentTimeMillis();
        if (!repository.debitBankBalance(player.getUniqueId(), product.currencyId(), amount, now)) {
            sendMessage(player, false, message("player.bank-insufficient"));
            refreshBoth(player);
            return;
        }
        try {
            repository.createFixedDeposit(new FixedDepositRecord(
                UUID.randomUUID().toString(),
                player.getUniqueId(),
                product.id(),
                product.currencyId(),
                amount,
                tier.rate(),
                now,
                now + product.durationSeconds() * 1000L,
                false,
                0L
            ));
        } catch (Exception exception) {
            repository.creditBankBalance(player.getUniqueId(), product.currencyId(), amount, now);
            throw exception;
        }
        sendMessage(player, true, message("player.fixed-deposit-created"));
        refreshBoth(player);
    }

    /**
     * �†�–�ˆ��œŸ�š�œŸ�˜款�œ�息�€‚
     * �€š�‡ {@link WarehouseRepository#claimFixedDepositAtomic} �ŽŸ子�‡记 claimed 并计�—�œ�息�…�账�Œ�˜�止并�‘�‡�复�†�–�€‚
     */
    private void claimFixedDeposit(Player player, String depositId) throws Exception {
        if (!isSecondaryUnlocked(player.getUniqueId())) {
            sendMessage(player, false, message("player.secondary-required-claim"));
            refreshBoth(player);
            return;
        }
        Optional<FixedDepositRecord> optional = repository.loadFixedDeposits(player.getUniqueId()).stream()
            .filter(deposit -> deposit.id().equals(depositId))
            .findFirst();
        if (optional.isEmpty()) {
            sendMessage(player, false, message("player.fixed-deposit-not-claimable"));
            refreshBoth(player);
            return;
        }
        FixedDepositRecord deposit = optional.get();
        if (deposit.claimed() || deposit.maturesAt() > System.currentTimeMillis()) {
            sendMessage(player, false, message("player.fixed-deposit-not-claimable"));
            refreshBoth(player);
            return;
        }
        Optional<BigDecimal> payout = repository.claimFixedDepositAtomic(depositId, player.getUniqueId(), System.currentTimeMillis());
        if (payout.isEmpty()) {
            sendMessage(player, false, message("player.fixed-deposit-not-claimable"));
            refreshBoth(player);
            return;
        }
        sendMessage(player, true, message("player.fixed-deposit-claimed", formatCurrency(deposit.currencyId(), payout.get())));
        refreshBoth(player);
    }

    /**
     * �ˆ›建�…�享�“�“�€‚校�Œ�ƒ�™��‚级�š„ max-owned �™��ˆ��Œ�‰��™��ˆ›建费�”��Ž�†™�…��•�据�“�€‚
     */
    private void createSharedWarehouse(Player player, String rawName) throws Exception {
        if (!configuration.shared().enabled()) {
            sendMessage(player, false, message("player.shared-disabled"));
            refreshBoth(player);
            return;
        }
        SharedPermissionTier tier = resolveSharedTier(player);
        if (repository.countOwnedSharedWarehouses(player.getUniqueId()) >= tier.maxOwned()) {
            sendMessage(player, false, message("player.shared-limit"));
            refreshBoth(player);
            return;
        }
        WarehouseLevelDefinition initialLevel = sharedInitialLevel();
        if (initialLevel == null) {
            sendMessage(player, false, message("player.shared-level-missing"));
            refreshBoth(player);
            return;
        }
        int level = initialLevel.level();
        long capacity = Math.max(SLOT_COUNT, initialLevel.capacity());
        WarehouseModuleConfiguration.UpgradeCost cost = configuration.shared().createCost();
        if (!withdrawCost(player, cost, message("player.unknown-shared-create-currency"))) {
            refreshBoth(player);
            return;
        }
        long now = System.currentTimeMillis();
        String id = UUID.randomUUID().toString();
        try {
            repository.createSharedWarehouse(new SharedWarehouseRecord(id, player.getUniqueId(), crop(rawName.isBlank() ? "�…�享�“�“" : rawName, 64), level, capacity, now, now, "owner", true));
        } catch (Exception exception) {
            refundCost(player, cost);
            throw exception;
        }
        sendMessage(player, true, cost == null ? message("player.shared-created") : message("player.shared-created-cost", formatCurrency(cost.currencyId(), cost.amount())));
        refreshBoth(player);
    }

    /**
     * �ˆ��™��…�享�“�“�ˆ�œ€�Œ级�†码确认�‰�€‚�…�‰€�œ‰�€…可�“��œ�Œ�ˆ��™��Ž�…�†�’�–��”�并�‡�置�“�‰��†�›��€‚
     */
    private void deleteSharedWarehouse(Player player, String sharedId, String password) throws Exception {
        if (!validatePassword(player.getUniqueId(), password)) {
            sendMessage(player, false, message("player.shared-delete-confirm"));
            refreshBoth(player);
            return;
        }
        Optional<SharedWarehouseRecord> shared = repository.loadSharedWarehouses(player.getUniqueId()).stream()
            .filter(record -> record.id().equals(sharedId))
            .findFirst();
        if (shared.isEmpty() || !"owner".equalsIgnoreCase(shared.get().viewerRole())) {
            sendMessage(player, false, message("player.shared-delete-owner"));
            refreshBoth(player);
            return;
        }
        repository.deleteSharedWarehouse(sharedId);
        SharedEditLock lock = sharedEditLocks.remove(sharedId);
        if (lock != null && crossServerLockService != null && crossServerLockService.isActive()) {
            crossServerLockService.publishUnlock(sharedId, lock.playerUuid());
        }
        ViewState state = state(player);
        if (OWNER_SHARED.equals(state.ownerType()) && sharedId.equals(state.ownerId())) {
            state.setOwnerType(OWNER_PERSONAL);
            state.setOwnerId(player.getUniqueId().toString());
            state.setWarehouseId(firstPersonalWarehouseId());
            state.setSelectedSlot(-1);
        }
        sendMessage(player, true, message("player.shared-deleted"));
        refreshBoth(player);
    }

    private void renameSharedWarehouse(Player player, String sharedId, String rawName) throws Exception {
        String name = crop(safe(rawName), 64);
        if (name.isBlank()) {
            sendMessage(player, false, message("player.shared-name-empty"));
            refreshBoth(player);
            return;
        }
        Optional<SharedWarehouseRecord> shared = repository.loadSharedWarehouses(player.getUniqueId()).stream()
            .filter(record -> record.id().equals(sharedId))
            .findFirst();
        if (shared.isEmpty() || !"owner".equalsIgnoreCase(shared.get().viewerRole())) {
            sendMessage(player, false, message("player.shared-name-owner"));
            refreshBoth(player);
            return;
        }
        repository.updateSharedWarehouseName(sharedId, name, System.currentTimeMillis());
        sendMessage(player, true, message("player.shared-name-updated", name));
        refreshBoth(player);
    }

    private void toggleSharedWarehouseShowcase(Player player, String sharedId) throws Exception {
        Optional<SharedWarehouseRecord> shared = repository.loadSharedWarehouses(player.getUniqueId()).stream()
            .filter(record -> record.id().equals(sharedId))
            .findFirst();
        if (shared.isEmpty() || !"owner".equalsIgnoreCase(shared.get().viewerRole())) {
            sendMessage(player, false, message("player.shared-showcase-owner"));
            refreshBoth(player);
            return;
        }
        boolean newValue = !shared.get().showcaseEnabled();
        repository.updateSharedWarehouseShowcase(sharedId, newValue, System.currentTimeMillis());
        sendMessage(player, true, message("player.shared-showcase-updated", newValue ? message("player.enabled") : message("player.disabled")));
        refreshBoth(player);
    }

    private void renamePersonalWarehouse(Player player, String warehouseId, String rawName) throws Exception {
        String name = crop(safe(rawName), 64);
        if (name.isBlank()) {
            sendMessage(player, false, message("player.warehouse-name-empty"));
            refreshBoth(player);
            return;
        }
        WarehouseRecord record = personalWarehouseMap(player.getUniqueId()).get(warehouseId);
        if (record == null) {
            sendMessage(player, false, message("player.warehouse-missing"));
            refreshBoth(player);
            return;
        }
        repository.updatePersonalWarehouseName(player.getUniqueId(), warehouseId, name, System.currentTimeMillis());
        sendMessage(player, true, message("player.warehouse-name-updated", name));
        refreshBoth(player);
    }

    private void togglePersonalWarehouseShowcase(Player player, String warehouseId) throws Exception {
        WarehouseRecord record = personalWarehouseMap(player.getUniqueId()).get(warehouseId);
        if (record == null) {
            sendMessage(player, false, message("player.warehouse-missing"));
            refreshBoth(player);
            return;
        }
        boolean newValue = !record.showcaseEnabled();
        repository.updatePersonalWarehouseShowcase(player.getUniqueId(), warehouseId, newValue, System.currentTimeMillis());
        sendMessage(player, true, message("player.showcase-updated", newValue ? message("player.enabled") : message("player.disabled")));
        refreshBoth(player);
    }

    /**
     * �‚€请�Ž�家�Š��…��…�享�“�“�Œ�ˆ–修�”��Ž��œ‰�ˆ��‘˜�’�‰��€‚
     * �…�‰€�œ‰�€…可�“��œ�Œ校�Œ�ˆ��‘˜�•��‡��Š�™��Œ�›��‡�’�‰�不可为 owner�€‚
     */
    private void inviteSharedMember(Player player, String sharedId, String memberName, String role) throws Exception {
        if (!isSecondaryUnlocked(player.getUniqueId())) {
            sendMessage(player, false, message("player.secondary-required-add-member"));
            refreshBoth(player);
            return;
        }
        Optional<SharedWarehouseRecord> shared = repository.loadSharedWarehouses(player.getUniqueId()).stream()
            .filter(record -> record.id().equals(sharedId))
            .findFirst();
        if (shared.isEmpty() || !"owner".equalsIgnoreCase(shared.get().viewerRole())) {
            sendMessage(player, false, message("player.shared-invite-owner"));
            refreshBoth(player);
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(memberName);
        if (!isRealPlayer(target)) {
            sendMessage(player, false, message("player.player-not-found"));
            refreshBoth(player);
            return;
        }
        SharedPermissionTier tier = resolveSharedTier(player);
        if (repository.countSharedMembers(sharedId) >= tier.maxMembers()) {
            sendMessage(player, false, message("player.shared-member-limit"));
            refreshBoth(player);
            return;
        }
        String normalizedRole = switch (normalizeId(role)) {
            case "viewer" -> "viewer";
            case "owner" -> "member";
            default -> "member";
        };
        repository.upsertSharedMember(sharedId, target.getUniqueId(), normalizedRole, System.currentTimeMillis());
        sendMessage(player, true, message("player.shared-member-updated"));
        refreshBoth(player);
    }

    private void removeSharedMember(Player player, String sharedId, String memberName) throws Exception {
        if (!isSecondaryUnlocked(player.getUniqueId())) {
            sendMessage(player, false, message("player.secondary-required-remove-member"));
            refreshBoth(player);
            return;
        }
        Optional<SharedWarehouseRecord> shared = repository.loadSharedWarehouses(player.getUniqueId()).stream()
            .filter(record -> record.id().equals(sharedId))
            .findFirst();
        if (shared.isEmpty() || !"owner".equalsIgnoreCase(shared.get().viewerRole())) {
            sendMessage(player, false, message("player.shared-remove-owner"));
            refreshBoth(player);
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(memberName);
        if (target.getUniqueId() == null || target.getUniqueId().equals(player.getUniqueId())) {
            sendMessage(player, false, message("player.shared-member-remove-failed"));
            refreshBoth(player);
            return;
        }
        repository.removeSharedMember(sharedId, target.getUniqueId());
        releaseSharedLocks(target.getUniqueId());
        sendMessage(player, true, message("player.shared-member-removed"));
        refreshBoth(player);
    }

    /**
     * �†�…�享�“�“�‰€�œ‰�ƒ转让�™�Ž��œ‰ member �’�‰��ˆ��‘˜�€‚
     * 转让�Ž�ŽŸ�‰€�œ‰�€…�˜为 viewer�Œ�›��‡�ˆ��‘˜提�‡为 owner�€‚
     */
    private boolean isRealPlayer(OfflinePlayer player) {
        return player != null && player.getUniqueId() != null
            && (Bukkit.getPlayer(player.getUniqueId()) != null || player.hasPlayedBefore());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        try {
            List<PendingTransfer> pending = repository.loadPendingTransfers(event.getPlayer().getUniqueId());
            long now = System.currentTimeMillis();
            for (PendingTransfer transfer : pending) {
                if (transfer.expiresAt() > 0 && transfer.expiresAt() <= now) {
                    repository.deletePendingTransfer(transfer.sharedId());
                    sendMessage(event.getPlayer(), false, message("player.transfer-expired-or-invalid"));
                } else {
                    sendMessage(event.getPlayer(), true, message("player.transfer-pending-notice"));
                }
            }
        } catch (Exception exception) {
            logger.warning("�€�Ÿ��“�“�…确认转让失败: " + exception.getMessage());
        }
    }

    public ActionResult confirmPendingTransfer(Player player, String sharedId) {
        try {
            Optional<PendingTransfer> pendingOptional = repository.loadPendingTransfer(sharedId);
            if (pendingOptional.isEmpty() || !pendingOptional.get().targetUuid().equals(player.getUniqueId())) return ActionResult.failure(message("player.transfer-none"));
            PendingTransfer pending = pendingOptional.get();
            if (pending.expiresAt() > 0 && pending.expiresAt() <= System.currentTimeMillis()) { repository.deletePendingTransfer(sharedId); return ActionResult.failure(message("player.transfer-expired")); }
            Optional<SharedWarehouseRecord> shared = repository.loadSharedWarehouses(pending.fromOwnerUuid()).stream().filter(record -> record.id().equals(sharedId) && record.ownerUuid().equals(pending.fromOwnerUuid()) && "owner".equalsIgnoreCase(record.viewerRole())).findFirst();
            Optional<SharedMemberRecord> member = repository.loadSharedMembers(sharedId).stream().filter(record -> record.playerUuid().equals(player.getUniqueId()) && "member".equalsIgnoreCase(record.role())).findFirst();
            if (shared.isEmpty() || member.isEmpty()) { repository.deletePendingTransfer(sharedId); return ActionResult.failure(message("player.transfer-invalid")); }
            repository.transferSharedWarehouse(sharedId, pending.fromOwnerUuid(), player.getUniqueId(), System.currentTimeMillis());
            repository.deletePendingTransfer(sharedId);

            SharedEditLock sharedLock = sharedEditLocks.remove(sharedId);
            if (sharedLock != null && crossServerLockService != null && crossServerLockService.isActive()) {
                crossServerLockService.publishUnlock(sharedId, sharedLock.playerUuid());
            }
            releaseSharedLocks(pending.fromOwnerUuid());
            releaseSharedLocks(player.getUniqueId());

            Player previousOwner = Bukkit.getPlayer(pending.fromOwnerUuid());
            if (previousOwner != null) {
                ViewState previousOwnerState = viewStates.get(previousOwner.getUniqueId());
                if (previousOwnerState != null && OWNER_SHARED.equals(previousOwnerState.ownerType())
                    && sharedId.equals(previousOwnerState.ownerId())) {
                    previousOwnerState.setSharedEditMode(false);
                }
                refreshBoth(previousOwner);
            }
            ViewState targetState = viewStates.get(player.getUniqueId());
            if (targetState != null && OWNER_SHARED.equals(targetState.ownerType())
                && sharedId.equals(targetState.ownerId())) {
                targetState.setSharedEditMode(false);
            }
            refreshBoth(player);
            return ActionResult.success(message("player.transfer-confirmed"));
        } catch (Exception exception) { return ActionResult.failure(message("player.transfer-confirm-failed", exception.getMessage())); }
    }

    public ActionResult rejectPendingTransfer(Player player, String sharedId) {
        try {
            Optional<PendingTransfer> pending = repository.loadPendingTransfer(sharedId);
            if (pending.isEmpty() || !pending.get().targetUuid().equals(player.getUniqueId())) return ActionResult.failure(message("player.transfer-none"));
            repository.deletePendingTransfer(sharedId);
            return ActionResult.success(message("player.transfer-rejected"));
        } catch (Exception exception) { return ActionResult.failure(message("player.transfer-reject-failed", exception.getMessage())); }
    }

    private void transferSharedWarehouse(Player player, String sharedId, String memberName) throws Exception {
        if (!isSecondaryUnlocked(player.getUniqueId())) {
            sendMessage(player, false, message("player.secondary-required-transfer"));
            refreshBoth(player);
            return;
        }
        Optional<SharedWarehouseRecord> shared = repository.loadSharedWarehouses(player.getUniqueId()).stream()
            .filter(record -> record.id().equals(sharedId))
            .findFirst();
        if (shared.isEmpty() || !"owner".equalsIgnoreCase(shared.get().viewerRole())) {
            sendMessage(player, false, message("player.shared-transfer-owner"));
            refreshBoth(player);
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(memberName);
        if (!isRealPlayer(target)) {
            sendMessage(player, false, message("player.player-not-found"));
            refreshBoth(player);
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            sendMessage(player, false, message("player.transfer-member-only"));
            refreshBoth(player);
            return;
        }
        Optional<SharedMemberRecord> member = repository.loadSharedMembers(sharedId).stream()
            .filter(record -> record.playerUuid().equals(target.getUniqueId()))
            .findFirst();
        if (member.isEmpty() || !"member".equalsIgnoreCase(member.get().role())) {
            sendMessage(player, false, message("player.transfer-role-required", sharedRoleName("member")));
            refreshBoth(player);
            return;
        }
        long now = System.currentTimeMillis();
        long expireHours = configuration.transferConfirmExpireHours();
        long expiresAt = expireHours <= 0 ? 0L : now + expireHours * 60L * 60L * 1000L;
        repository.upsertPendingTransfer(new PendingTransfer(sharedId, player.getUniqueId(), target.getUniqueId(), now, expiresAt));
        sendMessage(player, true, message("player.transfer-started"));
        Player onlineTarget = Bukkit.getPlayer(target.getUniqueId());
        if (onlineTarget != null) {
            sendMessage(onlineTarget, true, message("player.transfer-received"));
        }
        refreshBoth(player);
        return;
    }

    /**
     * 设置�Œ级�†码�€‚使�”� PBKDF2WithHmacSHA256 120,000 次迭代 hash�Œ�š��œ� salt�€‚
     * 设置�ˆ��ŠŸ�Ž�‡��Š�解�”��“�‰��š话�€‚
     */
    private void setPassword(Player player, String password) throws Exception {
        String normalized = safe(password);
        if (normalized.length() < configuration.security().minLength() || normalized.length() > configuration.security().maxLength()) {
            sendMessage(player, false, message("player.password-length", configuration.security().minLength(), configuration.security().maxLength()));
            refreshBoth(player);
            return;
        }
        byte[] salt = new byte[16];
        secureRandom.nextBytes(salt);
        byte[] hash = derivePasswordHash(normalized.toCharArray(), salt);
        repository.saveSecurity(new SecurityRecord(
            player.getUniqueId(),
            Base64.getEncoder().encodeToString(salt),
            Base64.getEncoder().encodeToString(hash),
            encryptPasswordForInspection(normalized),
            System.currentTimeMillis()
        ));
        unlockedUntil.put(player.getUniqueId(), System.currentTimeMillis() + configuration.security().unlockSessionMs());
        sendMessage(player, true, message("player.password-set"));
        refreshBoth(player);
    }

    private void unlockPassword(Player player, String password) throws Exception {
        if (validatePassword(player.getUniqueId(), password)) {
            unlockedUntil.put(player.getUniqueId(), System.currentTimeMillis() + configuration.security().unlockSessionMs());
            sendMessage(player, true, message("player.password-unlocked"));
        } else {
            sendMessage(player, false, message("player.password-wrong"));
        }
        refreshBoth(player);
    }

    private void clearPassword(Player player, String password) throws Exception {
        if (!validatePassword(player.getUniqueId(), password)) {
            sendMessage(player, false, message("player.password-wrong"));
            refreshBoth(player);
            return;
        }
        repository.clearSecurity(player.getUniqueId());
        unlockedUntil.remove(player.getUniqueId());
        sendMessage(player, true, message("player.password-cleared"));
        refreshBoth(player);
    }

    private void ensureEntitlements(Player player) throws Exception {
        long now = System.currentTimeMillis();
        Map<String, WarehouseRecord> existing = personalWarehouseMap(player.getUniqueId());
        for (WarehouseDefinition definition : configuration.warehouses().values()) {
            if (!definition.defaultOwned()) {
                continue;
            }
            if (!definition.permission().isBlank() && !player.hasPermission(definition.permission())) {
                continue;
            }
            if (existing.containsKey(definition.id())) {
                continue;
            }
            repository.upsertPersonalWarehouse(player.getUniqueId(), definition.id(), definition.defaultLevel(), "", now);
        }
    }

    private void ensureCurrentWarehouse(Player player, ViewState state) throws Exception {
        if (OWNER_SHARED.equals(state.ownerType())) {
            Optional<SharedWarehouseRecord> shared = repository.loadSharedWarehouses(player.getUniqueId()).stream()
                .filter(record -> record.id().equals(state.ownerId()))
                .findFirst();
            if (shared.isPresent()) {
                return;
            }
        }
        state.setOwnerType(OWNER_PERSONAL);
        state.setOwnerId(player.getUniqueId().toString());
        if (state.warehouseId().isBlank() || !personalWarehouseMap(player.getUniqueId()).containsKey(state.warehouseId())) {
            state.setWarehouseId(firstPersonalWarehouseId());
        }
    }

    private ViewState state(Player player) {
        return viewStates.computeIfAbsent(player.getUniqueId(), ignored -> ViewState.initial(player,
            configuration.pickup().autoStoreOnPickup(),
            configuration.pickup().autoStoreMythicLoot(),
            configuration.pickup().notifyOnAutoStore()));
    }

    private List<WarehouseRecord> personalRecords(UUID playerUuid) throws Exception {
        return repository.loadPersonalWarehouses(playerUuid);
    }

    private Map<String, WarehouseRecord> personalWarehouseMap(UUID playerUuid) throws Exception {
        Map<String, WarehouseRecord> records = new LinkedHashMap<>();
        for (WarehouseRecord record : repository.loadPersonalWarehouses(playerUuid)) {
            records.put(record.warehouseId(), record);
        }
        return records;
    }

    private List<SlotItemRecord> loadSlots(String ownerType, String ownerId, String warehouseId) {
        try {
            return repository.loadSlots(ownerType, ownerId, warehouseId);
        } catch (Exception exception) {
            if (configuration.debug()) {
                this.logger.warning("读�–�“�“槽位失败: " + exception.getMessage());
            }
            return List.of();
        }
    }

    private int firstEmptySlot(List<SlotItemRecord> currentSlots, long capacity) {
        int cappedCapacity = (int) Math.max(1L, Math.min(Integer.MAX_VALUE - 8L, capacity));
        boolean[] occupied = new boolean[cappedCapacity];
        for (SlotItemRecord item : currentSlots) {
            if (item.slot() >= 0 && item.slot() < cappedCapacity) {
                occupied[item.slot()] = true;
            }
        }
        for (int slot = 0; slot < cappedCapacity; slot++) {
            if (!occupied[slot]) {
                return slot;
            }
        }
        return -1;
    }

    private boolean canModifyCurrent(Player player, ViewState state) throws Exception {
        if (OWNER_PERSONAL.equals(state.ownerType())) {
            return player.getUniqueId().toString().equals(state.ownerId());
        }
        if (!state.sharedEditMode()) {
            return false;
        }
        Optional<SharedWarehouseRecord> shared = repository.loadSharedWarehouses(player.getUniqueId()).stream()
            .filter(record -> record.id().equals(state.ownerId()))
            .findFirst();
        if (shared.isEmpty() || !canEditShared(shared.get().viewerRole())) {
            return false;
        }
        SharedEditLock lock = sharedEditLocks.get(state.ownerId());
        if (lock == null) {
            return false;
        }
        return lock.heldBy(player.getUniqueId());
    }

    private boolean sharedCanEdit(Player player, ViewState state) throws Exception {
        if (!OWNER_SHARED.equals(state.ownerType())) {
            return false;
        }
        return repository.loadSharedWarehouses(player.getUniqueId()).stream()
            .filter(record -> record.id().equals(state.ownerId()))
            .findFirst()
            .map(shared -> canEditShared(shared.viewerRole()))
            .orElse(false);
    }

    private boolean currentShowcaseEnabled(Player player, ViewState state) throws Exception {
        if (!OWNER_SHARED.equals(state.ownerType())) {
            return personalWarehouseMap(player.getUniqueId()).getOrDefault(state.warehouseId(), new WarehouseRecord(player.getUniqueId(), state.warehouseId(), 1, "", true, 0L)).showcaseEnabled();
        }
        return repository.loadSharedWarehouses(player.getUniqueId()).stream()
            .filter(record -> record.id().equals(state.ownerId()))
            .findFirst()
            .map(SharedWarehouseRecord::showcaseEnabled)
            .orElse(true);
    }

    private boolean acquireCurrentSharedLock(Player player, ViewState state) throws Exception {
        if (!OWNER_SHARED.equals(state.ownerType())) {
            return true;
        }
        Optional<SharedWarehouseRecord> shared = repository.loadSharedWarehouses(player.getUniqueId()).stream()
            .filter(record -> record.id().equals(state.ownerId()))
            .findFirst();
        if (shared.isEmpty() || !canEditShared(shared.get().viewerRole())) {
            return false;
        }
        String sharedId = state.ownerId();
        SharedEditLock existing = sharedEditLocks.get(sharedId);
        if (existing != null) {
            if (existing.heldBy(player.getUniqueId())) {
                return true;
            }
            sendMessage(player, false, buildLockBusyMessage(existing));
            return false;
        }
        SharedEditLock newLock = new SharedEditLock(
            player.getUniqueId(),
            player.getName(),
            localNodeId()
        );
        SharedEditLock raced = sharedEditLocks.putIfAbsent(sharedId, newLock);
        if (raced != null && !raced.heldBy(player.getUniqueId())) {
            sendMessage(player, false, buildLockBusyMessage(raced));
            return false;
        }
        if (crossServerLockService != null && crossServerLockService.isActive()) {
            crossServerLockService.publishLock(sharedId, newLock);
        }
        return true;
    }

    private boolean canEditShared(String role) {
        String normalized = normalizeId(role);
        return "owner".equals(normalized) || "member".equals(normalized);
    }

    private void releaseCurrentSharedLock(Player player) {
        ViewState state = viewStates.get(player.getUniqueId());
        if (state != null && OWNER_SHARED.equals(state.ownerType())) {
            releaseSharedLock(state.ownerId(), player.getUniqueId());
        }
    }

    private void releaseSharedLocks(UUID playerUuid) {
        List<String> sharedIds = new ArrayList<>();
        sharedEditLocks.forEach((sharedId, lock) -> {
            if (lock.heldBy(playerUuid) && localNodeId().equals(lock.nodeId())) {
                sharedIds.add(sharedId);
            }
        });
        for (String sharedId : sharedIds) {
            releaseSharedLock(sharedId, playerUuid);
        }
    }

    private void releaseSharedLock(String sharedId, UUID playerUuid) {
        SharedEditLock removed = sharedEditLocks.computeIfPresent(sharedId, (id, lock) ->
            lock.heldBy(playerUuid) && localNodeId().equals(lock.nodeId()) ? null : lock
        );
        if (removed != null && crossServerLockService != null && crossServerLockService.isActive()) {
            crossServerLockService.publishUnlock(sharedId, playerUuid);
        }
    }

    private void applyRemoteSharedLock(String sharedId, SharedEditLock lock) {
        if (sharedId == null || sharedId.isBlank() || lock == null) {
            return;
        }
        SharedEditLock previous = sharedEditLocks.put(sharedId, lock);
        if (previous != null
            && previous.playerUuid().equals(lock.playerUuid())
            && previous.nodeId().equals(lock.nodeId())) {
            return;
        }
        for (Map.Entry<UUID, ViewState> entry : viewStates.entrySet()) {
            ViewState viewState = entry.getValue();
            if (!OWNER_SHARED.equals(viewState.ownerType())
                || !sharedId.equals(viewState.ownerId())
                || !viewState.sharedEditMode()
                || lock.heldBy(entry.getKey())) {
                continue;
            }
            viewState.setSharedEditMode(false);
            Player online = Bukkit.getPlayer(entry.getKey());
            if (online == null || !online.isOnline()) {
                continue;
            }
            sendMessage(online, false, buildLockBusyMessage(lock));
            try {
                refreshBoth(online);
            } catch (Exception exception) {
                this.logger.warning("[Warehouse] �ˆ��–��…�享�“�“ UI 失败: " + exception.getMessage());
            }
        }
    }

    private void applyRemoteSharedUnlock(WarehouseCrossServerPayloadCodec.UnlockPayload unlock) {
        if (unlock == null || unlock.sharedId() == null || unlock.sharedId().isBlank()) {
            return;
        }
        sharedEditLocks.computeIfPresent(unlock.sharedId(), (sharedId, lock) ->
            lock.playerUuid().equals(unlock.playerUuid()) && lock.nodeId().equals(unlock.nodeId())
                ? null : lock
        );
    }

    private String buildLockBusyMessage(SharedEditLock lock) {
        if (lock == null) {
            return "该�…�享�“�“正�œ�被�…��–�ˆ��‘˜�–�‘�Œ�“�‰�以只读�–�式�‰“�€�€‚";
        }
        if (localNodeId().equals(lock.nodeId())) {
            return "该�…�享�“�“正�œ�被 " + lock.playerName() + " �–�‘�Œ�“�‰�以只读�–�式�‰“�€�€‚";
        }
        return "该�…�享�“�“正�œ�被 " + lock.playerName() + "�ˆ" + lock.nodeId() + "�‰�–�‘�Œ�“�‰�以只读�–�式�‰“�€�€‚";
    }

    private String localNodeId() {
        return crossServerLockService != null ? crossServerLockService.nodeId() : "local";
    }

    private String lockOwnerName(ViewState state) {
        if (!OWNER_SHARED.equals(state.ownerType())) {
            return "";
        }
        SharedEditLock lock = sharedEditLocks.get(state.ownerId());
        if (lock == null) {
            return "";
        }
        if (localNodeId().equals(lock.nodeId())) {
            Player player = Bukkit.getPlayer(lock.playerUuid());
            return player == null ? lock.playerName() : player.getName();
        }
        return lock.playerName() + "@" + lock.nodeId();
    }

    private void clearFilteredSelection(ViewState state) throws Exception {
        if (!validSlot(state.selectedSlot())) {
            return;
        }
        Optional<SlotItemRecord> selected = repository.loadSlot(state.ownerType(), state.ownerId(), state.warehouseId(), state.selectedSlot());
        if (selected.isEmpty() || !slotMatches(selected.get(), state.categoryId(), normalizeToken(state.search()))) {
            state.setSelectedSlot(-1);
        }
    }

    private long currentWarehouseCapacity(Player player, String ownerType, String ownerId, String warehouseId) throws Exception {
        if (OWNER_SHARED.equals(ownerType)) {
            return repository.loadSharedWarehouses(player.getUniqueId()).stream()
                .filter(shared -> shared.id().equals(ownerId))
                .map(SharedWarehouseRecord::capacity)
                .findFirst()
                .orElse((long) SLOT_COUNT);
        }
        WarehouseDefinition definition = configuration.warehouse(warehouseId);
        WarehouseRecord record = personalWarehouseMap(player.getUniqueId()).get(warehouseId);
        if (definition == null || record == null) {
            return SLOT_COUNT;
        }
        WarehouseLevelDefinition level = definition.level(record.level());
        return level == null ? SLOT_COUNT : Math.max(SLOT_COUNT, level.capacity());
    }

    private void putCapacityFields(Player player, ViewState state, Map<String, Object> packet) throws Exception {
        long capacity = currentWarehouseCapacity(player, state.ownerType(), state.ownerId(), state.warehouseId());
        long used = loadSlots(state.ownerType(), state.ownerId(), state.warehouseId()).size();
        packet.put("capacity", capacity);
        packet.put("used", used);
        packet.put("capacityText", used + "/" + capacity);
        packet.put("level", 1);
        packet.put("canUpgrade", false);
        packet.put("nextUpgradeText", message("ui.upgrade-unavailable"));
        if (OWNER_SHARED.equals(state.ownerType())) {
            Optional<SharedWarehouseRecord> shared = currentSharedRecord(player, state);
            if (shared.isEmpty()) {
                packet.put("nextUpgradeText", message("ui.select-shared"));
                return;
            }
            SharedWarehouseRecord record = shared.get();
            packet.put("level", record.level());
            Map<Integer, WarehouseLevelDefinition> levels = configuration.shared().levels();
            if (levels.isEmpty()) {
                packet.put("nextUpgradeText", message("ui.shared-upgrade-not-configured"));
                return;
            }
            WarehouseLevelDefinition next = levels.get(record.level() + 1);
            if (next == null) {
                packet.put("nextUpgradeText", message("ui.max-level"));
                return;
            }
            if (!"owner".equalsIgnoreCase(record.viewerRole())) {
                packet.put("nextUpgradeText", message("ui.shared-upgrade-owner"));
                return;
            }
            WarehouseModuleConfiguration.UpgradeCost cost = upgradeCost(levels, record.level());
            packet.put("canUpgrade", true);
            packet.put("nextLevel", next.level());
            packet.put("nextCapacity", next.capacity());
            packet.put("nextUpgradeText", cost == null
                ? message("ui.upgrade-to", next.level(), next.capacity())
                : message("ui.upgrade-to-cost", next.level(), next.capacity(), formatCurrencyWithName(cost.currencyId(), cost.amount())));
            return;
        }
        if (!OWNER_PERSONAL.equals(state.ownerType())) {
            return;
        }
        WarehouseDefinition definition = configuration.warehouse(state.warehouseId());
        WarehouseRecord record = personalWarehouseMap(player.getUniqueId()).get(state.warehouseId());
        if (definition == null || record == null) {
            return;
        }
        packet.put("level", record.level());
        WarehouseLevelDefinition next = definition.levels().get(record.level() + 1);
        if (next == null) {
            packet.put("nextUpgradeText", message("ui.max-level"));
            return;
        }
        WarehouseModuleConfiguration.UpgradeCost cost = upgradeCost(definition.levels(), record.level());
        packet.put("canUpgrade", true);
        packet.put("nextLevel", next.level());
        packet.put("nextCapacity", next.capacity());
        packet.put("nextUpgradeText", cost == null
            ? message("ui.upgrade-to", next.level(), next.capacity())
            : message("ui.upgrade-to-cost", next.level(), next.capacity(), formatCurrencyWithName(cost.currencyId(), cost.amount())));
    }

    /**
     * �‰��……�“�‰��“�“�ˆ��‹�€�‰级�€‚
     * 个人�“�“�’Œ�…�享�“�“�‡�”��Œ��Œ�‰��™��‡级费�”��Ž�›��–��•�据�“�€‚
     */
    private void upgradeCurrentWarehouse(Player player) throws Exception {
        ViewState state = state(player);
        if (!isSecondaryUnlocked(player.getUniqueId())) {
            sendMessage(player, false, message("player.secondary-required-upgrade"));
            refreshBoth(player);
            return;
        }
        if (OWNER_SHARED.equals(state.ownerType())) {
            upgradeCurrentSharedWarehouse(player, state);
            return;
        }
        if (!OWNER_PERSONAL.equals(state.ownerType())) {
            sendMessage(player, false, message("player.upgrade-not-available-here"));
            refreshBoth(player);
            return;
        }
        WarehouseDefinition definition = configuration.warehouse(state.warehouseId());
        WarehouseRecord record = personalWarehouseMap(player.getUniqueId()).get(state.warehouseId());
        if (definition == null || record == null) {
            sendMessage(player, false, message("player.upgrade-unavailable"));
            refreshBoth(player);
            return;
        }
        WarehouseLevelDefinition next = definition.levels().get(record.level() + 1);
        if (next == null) {
            sendMessage(player, false, message("player.upgrade-maxed"));
            refreshBoth(player);
            return;
        }
        WarehouseModuleConfiguration.UpgradeCost cost = upgradeCost(definition.levels(), record.level());
        if (!withdrawCost(player, cost, message("player.unknown-upgrade-currency"))) {
            refreshBoth(player);
            return;
        }
        try {
            repository.upsertPersonalWarehouse(player.getUniqueId(), state.warehouseId(), next.level(), record.customName(), System.currentTimeMillis());
        } catch (Exception exception) {
            refundCost(player, cost);
            throw exception;
        }
        sendMessage(player, true, message("player.upgrade-success", next.level(), next.capacity()));
        refreshBoth(player);
    }

    private void upgradeCurrentSharedWarehouse(Player player, ViewState state) throws Exception {
        Optional<SharedWarehouseRecord> shared = currentSharedRecord(player, state);
        if (shared.isEmpty() || !"owner".equalsIgnoreCase(shared.get().viewerRole())) {
            sendMessage(player, false, message("player.shared-upgrade-owner"));
            refreshBoth(player);
            return;
        }
        Map<Integer, WarehouseLevelDefinition> levels = configuration.shared().levels();
        if (levels.isEmpty()) {
            sendMessage(player, false, message("player.shared-upgrade-level-missing"));
            refreshBoth(player);
            return;
        }
        SharedWarehouseRecord record = shared.get();
        WarehouseLevelDefinition next = levels.get(record.level() + 1);
        if (next == null) {
            sendMessage(player, false, message("player.shared-upgrade-maxed"));
            refreshBoth(player);
            return;
        }
        WarehouseModuleConfiguration.UpgradeCost cost = upgradeCost(levels, record.level());
        if (!withdrawCost(player, cost, message("player.unknown-shared-upgrade-currency"))) {
            refreshBoth(player);
            return;
        }
        try {
            repository.updateSharedWarehouseLevel(record.id(), next.level(), Math.max(SLOT_COUNT, next.capacity()), System.currentTimeMillis());
        } catch (Exception exception) {
            refundCost(player, cost);
            throw exception;
        }
        sendMessage(player, true, message("player.shared-upgrade-success", next.level(), next.capacity()));
        refreshBoth(player);
    }

    private Map<String, Object> categoryPacket() {
        Map<String, Object> result = new LinkedHashMap<>();
        int idx = 0;
        Map<String, Object> allRow = new LinkedHashMap<>();
        allRow.put("id", "all");
        allRow.put("name", message("ui.all"));
        allRow.put("text", message("ui.all-text"));
        result.put(Integer.toString(idx), allRow);
        idx++;
        for (CategoryDefinition category : configuration.categories().values().stream()
                .sorted(Comparator.comparingInt(CategoryDefinition::priority))
                .toList()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", category.id());
            row.put("name", category.displayName());
            row.put("text", message("ui.category-name", category.displayName()));
            result.put(Integer.toString(idx), row);
            idx++;
        }
        return result;
    }

    private Map<String, Object> personalWarehousePacket(Player player) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, WarehouseRecord> records = personalWarehouseMap(player.getUniqueId());
        int idx = 0;
        for (WarehouseDefinition definition : configuration.warehouses().values()) {
            if (!records.containsKey(definition.id())) {
                continue;
            }
            WarehouseRecord record = records.get(definition.id());
            WarehouseLevelDefinition level = definition.level(record.level());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", definition.id());
            row.put("name", ChatColor.translateAlternateColorCodes('&', definition.displayName()));
            row.put("text", message("ui.warehouse-name", ChatColor.translateAlternateColorCodes('&', definition.displayName())));
            row.put("level", record.level());
            row.put("capacity", level == null ? SLOT_COUNT : level.capacity());
            result.put(Integer.toString(idx), row);
            idx++;
        }
        return result;
    }

    private Map<String, Object> previewPersonalWarehousePacket(UUID targetUuid) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, WarehouseRecord> records = personalWarehouseMap(targetUuid);
        int idx = 0;
        for (WarehouseDefinition definition : configuration.warehouses().values()) {
            if (!records.containsKey(definition.id())) {
                continue;
            }
            WarehouseRecord record = records.get(definition.id());
            if (!record.showcaseEnabled()) {
                continue;
            }
            WarehouseLevelDefinition level = definition.level(record.level());
            String name = record.customName() == null || record.customName().isBlank()
                ? ChatColor.translateAlternateColorCodes('&', definition.displayName())
                : record.customName();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", definition.id());
            row.put("name", name);
            row.put("text", message("ui.warehouse-name", name));
            row.put("level", record.level());
            row.put("capacity", level == null ? SLOT_COUNT : level.capacity());
            result.put(Integer.toString(idx), row);
            idx++;
        }
        return result;
    }

    private Map<String, Object> previewSharedWarehousePacket(UUID targetUuid) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        int idx = 0;
        for (SharedWarehouseRecord shared : repository.loadSharedWarehousesByOwner(targetUuid)) {
            if (!shared.showcaseEnabled()) {
                continue;
            }
            Map<String, Object> rowMap = new LinkedHashMap<>();
            rowMap.put("id", shared.id());
            rowMap.put("name", shared.name());
            rowMap.put("role", shared.viewerRole());
            rowMap.put("roleName", sharedRoleName(shared.viewerRole()));
            rowMap.put("level", shared.level());
            rowMap.put("storageText", message("ui.shared-name", shared.name()));
            rowMap.put("manageText", message("ui.shared-entry", shared.name(), sharedRoleName(shared.viewerRole()), shared.level(), shared.capacity()));
            rowMap.put("capacity", shared.capacity());
            rowMap.put("lockedBy", "");
            result.put(Integer.toString(idx), rowMap);
            idx++;
        }
        return result;
    }

    private Map<String, Object> manageWarehousePacket(Map<String, Object> personalWarehouses, Map<String, Object> sharedWarehouses, ViewState state) {
        Map<String, Object> result = new LinkedHashMap<>();
        int idx = 0;
        for (Map.Entry<String, Object> entry : personalWarehouses.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> row)) {
                continue;
            }
            String target = safe(String.valueOf(row.get("id")));
            Map<String, Object> rowMap = new LinkedHashMap<>();
            rowMap.put("ownerType", OWNER_PERSONAL);
            rowMap.put("target", target);
            rowMap.put("selected", OWNER_PERSONAL.equals(state.ownerType()) && target.equals(state.warehouseId()));
            rowMap.put("text", message("ui.personal-entry", safe(String.valueOf(row.get("name"))), row.get("level"), row.get("capacity")));
            result.put(Integer.toString(idx), rowMap);
            idx++;
        }
        for (Map.Entry<String, Object> entry : sharedWarehouses.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> row)) {
                continue;
            }
            String target = safe(String.valueOf(row.get("id")));
            Map<String, Object> rowMap = new LinkedHashMap<>();
            rowMap.put("ownerType", OWNER_SHARED);
            rowMap.put("target", target);
            rowMap.put("selected", OWNER_SHARED.equals(state.ownerType()) && target.equals(state.ownerId()));
            rowMap.put("text", safe(String.valueOf(row.get("manageText"))));
            result.put(Integer.toString(idx), rowMap);
            idx++;
        }
        return result;
    }

    private Map<String, Object> sharedWarehousePacket(Player player) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        int idx = 0;
        for (SharedWarehouseRecord shared : repository.loadSharedWarehouses(player.getUniqueId())) {
            Map<String, Object> rowMap = new LinkedHashMap<>();
            rowMap.put("id", shared.id());
            rowMap.put("name", shared.name());
            rowMap.put("role", shared.viewerRole());
            rowMap.put("roleName", sharedRoleName(shared.viewerRole()));
            rowMap.put("level", shared.level());
            rowMap.put("storageText", message("ui.shared-name", shared.name()));
            rowMap.put("manageText", message("ui.shared-entry", shared.name(), sharedRoleName(shared.viewerRole()), shared.level(), shared.capacity()));
            rowMap.put("capacity", shared.capacity());
            rowMap.put("lockedBy", lockOwnerName(ViewState.shared(shared.id(), player, false, false, false)));
            result.put(Integer.toString(idx), rowMap);
            idx++;
        }
        return result;
    }

    private Map<String, Object> sharedMemberPacket(Player player, ViewState state) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!OWNER_SHARED.equals(state.ownerType())) {
            return result;
        }
        Optional<SharedWarehouseRecord> shared = repository.loadSharedWarehouses(player.getUniqueId()).stream()
            .filter(record -> record.id().equals(state.ownerId()))
            .findFirst();
        if (shared.isEmpty() || !"owner".equalsIgnoreCase(shared.get().viewerRole())) {
            return result;
        }
        int idx = 0;
        for (SharedMemberRecord member : repository.loadSharedMembers(state.ownerId())) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(member.playerUuid());
            Map<String, Object> rowMap = new LinkedHashMap<>();
            rowMap.put("uuid", member.playerUuid().toString());
            rowMap.put("name", offline.getName() == null ? member.playerUuid().toString() : offline.getName());
            rowMap.put("role", member.role());
            rowMap.put("roleName", sharedRoleName(member.role()));
            rowMap.put("text", "&0" + (offline.getName() == null ? member.playerUuid().toString() : offline.getName()) + " &7" + sharedRoleName(member.role()));
            result.put(Integer.toString(idx), rowMap);
            idx++;
        }
        return result;
    }

    private Map<String, Object> backpackPacket(Player player) {
        Map<String, Object> result = new LinkedHashMap<>();
        PlayerInventory inventory = player.getInventory();
        for (int raw = 9; raw <= 44; raw++) {
            int slot = toBukkitBackpackSlot(raw);
            ItemStack item = slot >= 0 && slot < inventory.getSize() ? inventory.getItem(slot) : null;
            if (item == null || item.getType().isAir()) {
                result.put(Integer.toString(raw), emptyBackpackPacket(raw, slot));
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", Integer.toString(raw));
            row.put("rawSlot", raw);
            row.put("slot", slot);
            row.put("empty", false);
            row.put("name", resolveDisplayName(item));
            row.put("amount", item.getAmount());
            row.put("category", resolveCategory(item));
            row.put("material", item.getType().name());
            row.put("itemJson", itemJson(item, item.getAmount()));
            row.put("lore", loreLines(item));
            result.put(Integer.toString(raw), row);
        }
        return result;
    }

    private Map<String, Object> emptyBackpackPacket(int rawSlot, int slot) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("key", Integer.toString(rawSlot));
        row.put("rawSlot", rawSlot);
        row.put("slot", slot);
        row.put("empty", true);
        row.put("name", "");
        row.put("amount", 0L);
        row.put("category", "");
        row.put("material", "");
        row.put("itemJson", "");
        row.put("lore", List.of());
        return row;
    }

    private Map<String, Object> slotPacket(SlotItemRecord item) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("key", Integer.toString(item.slot()));
        row.put("slot", item.slot());
        row.put("empty", false);
        row.put("name", item.displayName());
        row.put("amount", item.amount());
        row.put("category", item.categoryId());
        row.put("material", item.materialId());
        row.put("updated", TIME_FORMATTER.format(Instant.ofEpochMilli(item.updatedAt())));
        row.put("lore", storedLoreLines(item));
        return row;
    }

    private String displayItemJson(SlotItemRecord item) {
        try {
            ItemStack stack = ItemSerializer.deserialize(Base64.getDecoder().decode(item.itemData()));
            String json = itemJson(stack, 1L);
            return json.isBlank() ? item.itemJson() : json;
        } catch (RuntimeException exception) {
            return item.itemJson();
        }
    }

    private Map<String, Object> emptySlotPacket(int slot) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("key", Integer.toString(slot));
        row.put("slot", slot);
        row.put("empty", true);
        row.put("name", "");
        row.put("amount", 0L);
        row.put("category", "");
        row.put("material", "");
        row.put("updated", "");
        row.put("lore", List.of());
        return row;
    }

    private Map<String, Object> emptySelectionPacket() {
        Map<String, Object> row = emptySlotPacket(-1);
        row.put("name", message("ui.no-selected-item"));
        row.put("lore", List.of(message("ui.select-slot")));
        return row;
    }

    private Map<String, Object> bankBalancePacket(Player player) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, BigDecimal> balances = repository.loadBankBalances(player.getUniqueId());
        int idx = 0;
        for (String currencyId : configuration.bankCurrencies()) {
            BigDecimal balance = balances.getOrDefault(currencyId, BigDecimal.ZERO);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", currencyId);
            row.put("name", currencyDisplayName(currencyId));
            row.put("balance", formatCurrency(currencyId, balance));
            row.put("text", "&0" + currencyDisplayName(currencyId) + "  &7" + formatCurrency(currencyId, balance));
            result.put(Integer.toString(idx), row);
            idx++;
        }
        return result;
    }

    private Map<String, Object> depositProductPacket(Player player) {
        Map<String, Object> result = new LinkedHashMap<>();
        int idx = 0;
        for (DepositProductDefinition product : configuration.depositProducts().values()) {
            if (!canUseProduct(player, product)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", product.id());
            row.put("name", product.displayName());
            row.put("description", product.description());
            row.put("currency", product.currencyId());
            row.put("duration", product.durationSeconds());
            row.put("min", formatCurrency(product.currencyId(), product.minAmount()));
            row.put("max", product.maxAmount().compareTo(BigDecimal.ZERO) <= 0 ? "不�™�" : formatCurrency(product.currencyId(), product.maxAmount()));
            row.put("text", "&0" + product.displayName() + "\n&7�œ€�Ž " + formatCurrency(product.currencyId(), product.minAmount()) + "  " + product.description());
            result.put(Integer.toString(idx), row);
            idx++;
        }
        return result;
    }

    private Map<String, Object> fixedDepositPacket(Player player) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        long now = System.currentTimeMillis();
        int idx = 0;
        for (FixedDepositRecord deposit : repository.loadFixedDeposits(player.getUniqueId())) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", deposit.id());
            row.put("product", deposit.productId());
            row.put("currency", deposit.currencyId());
            row.put("principal", formatCurrency(deposit.currencyId(), deposit.principal()));
            row.put("rate", deposit.interestRate().toPlainString());
            row.put("maturesAt", TIME_FORMATTER.format(Instant.ofEpochMilli(deposit.maturesAt())));
            row.put("matured", deposit.maturesAt() <= now);
            row.put("text", "&0" + deposit.productId() + " &7�œ��‡‘ " + formatCurrency(deposit.currencyId(), deposit.principal()) + "\n&7�ˆ��œŸ " + TIME_FORMATTER.format(Instant.ofEpochMilli(deposit.maturesAt())));
            result.put(Integer.toString(idx), row);
            idx++;
        }
        return result;
    }

    private Map<String, Object> searchResultPacket(ViewState state) {
        Map<String, Object> result = new LinkedHashMap<>();
        String keyword = normalizeToken(state.search());
        int index = 0;
        for (SlotItemRecord item : loadSlots(state.ownerType(), state.ownerId(), state.warehouseId())) {
            if (!slotMatches(item, state.categoryId(), keyword)) {
                continue;
            }
            result.put(Integer.toString(index++), Map.of(
                "slot", item.slot(),
                "name", item.displayName(),
                "amount", item.amount(),
                "category", item.categoryId(),
                "material", item.materialId(),
                "text", "&0#" + item.slot() + " " + item.displayName() + "\n&7x" + item.amount() + " " + item.categoryId()
            ));
        }
        return result;
    }

    private boolean slotMatches(SlotItemRecord item, String categoryId, String keyword) {
        String normalizedCategory = normalizeId(categoryId);
        if (!normalizedCategory.isBlank() && !"all".equals(normalizedCategory) && !normalizedCategory.equals(item.categoryId())) {
            return false;
        }
        return keyword == null
            || keyword.isBlank()
            || item.searchText().contains(keyword)
            || item.pinyin().contains(keyword)
            || item.initials().contains(keyword);
    }

    private String currentWarehouseName(Player player, ViewState state) throws Exception {
        UUID lookupUuid = state.previewMode() && state.previewTargetUuid() != null ? state.previewTargetUuid() : player.getUniqueId();
        if (OWNER_SHARED.equals(state.ownerType())) {
            return repository.loadSharedWarehousesByOwner(lookupUuid).stream()
                .filter(shared -> shared.id().equals(state.ownerId()))
                .map(SharedWarehouseRecord::name)
                .findFirst()
                .orElse("�…�享�“�“");
        }
        WarehouseRecord record = personalWarehouseMap(lookupUuid).get(state.warehouseId());
        if (record != null && record.customName() != null && !record.customName().isBlank()) {
            return record.customName();
        }
        WarehouseDefinition definition = configuration.warehouse(state.warehouseId());
        return definition == null ? "个人�“�“" : ChatColor.translateAlternateColorCodes('&', definition.displayName());
    }

    private String firstPersonalWarehouseId() {
        return configuration.warehouses().isEmpty() ? "personal" : configuration.warehouses().keySet().iterator().next();
    }

    private int toBukkitBackpackSlot(int rawSlot) {
        if (rawSlot >= 36 && rawSlot <= 44) {
            return rawSlot - 36;
        }
        return rawSlot;
    }

    private int parseRawBackpackSlot(String rawValue) {
        String value = safe(rawValue);
        if (value.isBlank()) {
            return -1;
        }
        try {
            return new BigDecimal(value).intValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            return -1;
        }
    }

    private int parsePacketSlot(String rawValue) {
        return parseRawBackpackSlot(rawValue);
    }

    private boolean validSlot(int slot) {
        return slot >= 0;
    }

    private String resolveDisplayName(ItemStack itemStack) {
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName();
        }
        return itemStack.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private String itemJson(ItemStack itemStack, long displayAmount) {
        if (itemStackBridge == null || itemStack == null || itemStack.getType().isAir()) {
            return "";
        }
        ItemStack display = itemStack.clone();
        display.setAmount((int) Math.max(1L, Math.min(displayAmount, Math.max(1, itemStack.getMaxStackSize()))));
        return itemStackBridge.itemToJson(display).orElse("");
    }

    private List<String> storedLoreLines(SlotItemRecord item) {
        try {
            ItemStack stack = ItemSerializer.deserialize(Base64.getDecoder().decode(item.itemData()));
            return loreLines(stack);
        } catch (RuntimeException exception) {
            return List.of("&f�‰��“�描述读�–失败�€‚");
        }
    }

    private List<String> loreLines(ItemStack itemStack) {
        if (itemStack == null) {
            return List.of("&f�™个�‰��“�没�œ‰额�–描述�€‚");
        }
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null || !meta.hasLore() || meta.getLore() == null || meta.getLore().isEmpty()) {
            return List.of("&f�™个�‰��“�没�œ‰额�–描述�€‚");
        }
        List<String> result = new ArrayList<>();
        for (String line : meta.getLore()) {
            result.add(line == null ? "" : line.replace("k!", "�"));
        }
        return List.copyOf(result);
    }

    private String resolveCategory(ItemStack itemStack) {
        List<CategoryDefinition> definitions = new ArrayList<>(configuration.categories().values());
        definitions.sort(Comparator.comparingInt(CategoryDefinition::priority));
        for (CategoryDefinition definition : definitions) {
            if (definition.fallback() || definition.nbtPath().isBlank() || definition.values().isEmpty()) {
                continue;
            }
            String value = normalizeId(resolvePath(itemStack, definition.nbtPath()));
            if (!value.isBlank() && definition.values().contains(value)) {
                return definition.id();
            }
        }
        return configuration.otherCategory().id();
    }

    private String resolvePath(ItemStack itemStack, String rawPath) {
        String path = safe(rawPath).toLowerCase(Locale.ROOT);
        ItemMeta meta = itemStack.getItemMeta();
        if ("material".equals(path)) {
            return itemStack.getType().name();
        }
        if ("display-name".equals(path) || "name".equals(path)) {
            return resolveDisplayName(itemStack);
        }
        if ("custom-model-data".equals(path) && meta != null && meta.hasCustomModelData()) {
            return Integer.toString(meta.getCustomModelData());
        }
        if (meta == null || !path.startsWith("pdc:")) {
            return "";
        }
        String keyText = rawPath.substring(4);
        NamespacedKey key;
        if (keyText.contains(":")) {
            String[] parts = keyText.split(":", 2);
            key = new NamespacedKey(parts[0], parts[1]);
        } else {
            key = new NamespacedKey(plugin, keyText);
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        String string = container.get(key, PersistentDataType.STRING);
        if (string != null) {
            return string;
        }
        Integer integer = container.get(key, PersistentDataType.INTEGER);
        if (integer != null) {
            return Integer.toString(integer);
        }
        Long longValue = container.get(key, PersistentDataType.LONG);
        return longValue == null ? "" : Long.toString(longValue);
    }

    private boolean isSecondaryUnlocked(UUID playerUuid) throws Exception {
        if (repository.loadSecurity(playerUuid).isEmpty()) {
            return true;
        }
        return unlockedUntil.getOrDefault(playerUuid, 0L) > System.currentTimeMillis();
    }

    private boolean validatePassword(UUID playerUuid, String candidate) throws Exception {
        Optional<SecurityRecord> optional = repository.loadSecurity(playerUuid);
        if (optional.isEmpty()) {
            return true;
        }
        try {
            SecurityRecord security = optional.get();
            byte[] salt = Base64.getDecoder().decode(security.saltBase64());
            byte[] expected = Base64.getDecoder().decode(security.hashBase64());
            byte[] actual = derivePasswordHash(safe(candidate).toCharArray(), salt);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private byte[] derivePasswordHash(char[] password, byte[] salt) {
        PBEKeySpec keySpec = new PBEKeySpec(password, salt, PASSWORD_HASH_ITERATIONS, PASSWORD_HASH_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(keySpec).getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to derive warehouse password hash.", exception);
        } finally {
            keySpec.clearPassword();
        }
    }

    private String encryptPasswordForInspection(String password) {
        if (!configuration.security().allowAdminPasswordReveal() || configuration.security().adminRevealSecret().isBlank()) {
            return "";
        }
        try {
            byte[] key = MessageDigest.getInstance("SHA-256").digest(configuration.security().adminRevealSecret().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] iv = new byte[12];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] merged = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, merged, 0, iv.length);
            System.arraycopy(encrypted, 0, merged, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(merged);
        } catch (GeneralSecurityException exception) {
            return "";
        }
    }

    private SharedPermissionTier resolveSharedTier(Player player) {
        return configuration.shared().permissionTiers().values().stream()
            .filter(tier -> tier.permission().isBlank() || player.hasPermission(tier.permission()))
            .max(Comparator.comparingInt(SharedPermissionTier::priority))
            .orElseGet(() -> configuration.shared().permissionTiers().values().iterator().next());
    }

    private boolean canUseProduct(Player player, DepositProductDefinition product) {
        return product.permission().isBlank() || player.hasPermission(product.permission());
    }

    private WarehouseLevelDefinition sharedInitialLevel() {
        Map<Integer, WarehouseLevelDefinition> levels = configuration.shared().levels();
        if (levels.isEmpty()) {
            return null;
        }
        WarehouseLevelDefinition level = levels.get(configuration.shared().defaultLevel());
        return level == null ? levels.values().iterator().next() : level;
    }

    private WarehouseModuleConfiguration.UpgradeCost upgradeCost(Map<Integer, WarehouseLevelDefinition> levels, int currentLevel) {
        WarehouseLevelDefinition current = levels.get(currentLevel);
        WarehouseLevelDefinition next = levels.get(currentLevel + 1);
        return current == null || current.upgradeCost() == null ? next == null ? null : next.upgradeCost() : current.upgradeCost();
    }

    private Optional<SharedWarehouseRecord> currentSharedRecord(Player player, ViewState state) throws Exception {
        if (!OWNER_SHARED.equals(state.ownerType())) {
            return Optional.empty();
        }
        return repository.loadSharedWarehouses(player.getUniqueId()).stream()
            .filter(shared -> shared.id().equals(state.ownerId()))
            .findFirst();
    }

    private String formatCurrency(String currencyId, BigDecimal amount) {
        return currencyBridgeManager.format(currencyId, amount == null ? BigDecimal.ZERO : amount);
    }

    private String currencyDisplayName(String currencyId) {
        CurrencyDefinition definition = currencyBridgeManager.definition(currencyId);
        return definition == null ? currencyId : definition.displayName();
    }

    private String formatCurrencyWithName(String currencyId, BigDecimal amount) {
        return formatCurrency(currencyId, amount) + " " + currencyDisplayName(currencyId);
    }

    private String sharedRoleName(String role) {
        WarehouseModuleConfiguration.SharedRoleNames names = configuration.shared().roleNames();
        return switch (normalizeId(role)) {
            case "owner" -> names.owner();
            case "viewer" -> names.viewer();
            default -> names.member();
        };
    }

    private String sharedCreateCostText() {
        WarehouseModuleConfiguration.UpgradeCost cost = configuration.shared().createCost();
        return cost == null ? message("ui.shared-create-free") : message("ui.shared-create-cost", formatCurrencyWithName(cost.currencyId(), cost.amount()));
    }

    private boolean withdrawCost(Player player, WarehouseModuleConfiguration.UpgradeCost cost, String unknownCurrencyMessage) {
        if (cost == null) {
            return true;
        }
        var bridge = currencyBridgeManager.bridge(cost.currencyId());
        if (bridge == null || !bridge.available()) {
            sendMessage(player, false, bridge == null ? unknownCurrencyMessage : bridge.unavailableReason());
            return false;
        }
        var result = bridge.withdraw(player, cost.amount());
        if (!result.success()) {
            sendMessage(player, false, result.message());
            return false;
        }
        return true;
    }

    private void refundCost(Player player, WarehouseModuleConfiguration.UpgradeCost cost) {
        if (cost == null) {
            return;
        }
        var bridge = currencyBridgeManager.bridge(cost.currencyId());
        if (bridge != null && bridge.available()) {
            bridge.deposit(player, cost.amount());
        }
    }

    private String message(String key, Object... args) {
        return messages == null ? "" : messages.get(key, args);
    }

    private void sendMessage(Player player, boolean success, String message) {
        player.sendMessage(PREFIX + (success ? ChatColor.GREEN : ChatColor.RED) + message);
    }

    private void debug(String message) {
        if (configuration.debug()) {
            this.logger.info("[WarehouseDebug] " + message);
        }
    }

    private String summarizeStoragePacket(Map<String, Object> packet) {
        Map<?, ?> slots = mapValue(packet.get("slots"));
        Map<?, ?> backpack = mapValue(packet.get("backpack"));
        return "ownerType=" + packet.get("ownerType")
            + " ownerId=" + packet.get("ownerId")
            + " warehouseId=" + packet.get("warehouseId")
            + " page=" + packet.get("page")
            + "/" + packet.get("pageTotal")
            + " pageText=" + packet.get("pageText")
            + " matchedSlots=" + packet.get("matchedSlots")
            + " selectedSlot=" + packet.get("selectedSlot")
            + " slots=" + slots.size()
            + " nonEmptySlots=" + countRows(slots, false)
            + " slotItemJson=" + countNonBlankField(slots, "itemJson")
            + " backpack=" + backpack.size()
            + " nonEmptyBackpack=" + countRows(backpack, false)
            + " readOnly=" + packet.get("readOnly")
            + " lockOwner=" + packet.get("lockOwner");
    }

    private String summarizeManagePacket(Map<String, Object> packet) {
        return "ownerType=" + packet.get("ownerType")
            + " ownerId=" + packet.get("ownerId")
            + " warehouseId=" + packet.get("warehouseId")
            + " categories=" + mapValue(packet.get("categories")).size()
            + " personalWarehouses=" + mapValue(packet.get("personalWarehouses")).size()
            + " sharedWarehouses=" + mapValue(packet.get("sharedWarehouses")).size()
            + " balances=" + mapValue(packet.get("balances")).size()
            + " products=" + mapValue(packet.get("products")).size()
            + " fixedDeposits=" + mapValue(packet.get("fixedDeposits")).size()
            + " sharedMembers=" + mapValue(packet.get("sharedMembers")).size()
            + " searchResults=" + mapValue(packet.get("searchResults")).size()
            + " readOnly=" + packet.get("readOnly")
            + " lockOwner=" + packet.get("lockOwner");
    }

    private Map<?, ?> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    private long countRows(Map<?, ?> rows, boolean emptyValue) {
        return rows.values().stream()
            .filter(row -> row instanceof Map<?, ?> map && Boolean.valueOf(emptyValue).equals(map.get("empty")))
            .count();
    }

    private long countNonBlankField(Map<?, ?> rows, String field) {
        return rows.values().stream()
            .filter(row -> row instanceof Map<?, ?> map && !safe(String.valueOf(map.get(field))).isBlank() && !"null".equals(String.valueOf(map.get(field))))
            .count();
    }

    private String describeStack(ItemStack stack) {
        if (stack == null) {
            return "null";
        }
        if (stack.getType().isAir()) {
            return stack.getType().name() + " x" + stack.getAmount();
        }
        ItemMeta meta = stack.getItemMeta();
        String name = meta != null && meta.hasDisplayName() ? meta.getDisplayName() : "";
        return stack.getType().name()
            + " x" + stack.getAmount()
            + " max=" + stack.getMaxStackSize()
            + " hasMeta=" + (meta != null)
            + (name.isBlank() ? "" : " name=" + name);
    }

    private String shortHash(String hash) {
        String safeHash = safe(hash);
        return safeHash.length() <= 12 ? safeHash : safeHash.substring(0, 12);
    }

    private BigDecimal parseDecimal(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(rawValue.trim());
        } catch (NumberFormatException exception) {
            return BigDecimal.ZERO;
        }
    }

    private int parseInt(String rawValue, int defaultValue) {
        String value = safe(rawValue).trim();
        if (value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            try {
                return new BigDecimal(value).intValueExact();
            } catch (ArithmeticException | NumberFormatException decimalException) {
                return defaultValue;
            }
        }
    }

    private long parseLong(String rawValue, long defaultValue) {
        String value = safe(rawValue).trim();
        if (value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            try {
                return new BigDecimal(value).longValueExact();
            } catch (ArithmeticException | NumberFormatException decimalException) {
                return defaultValue;
            }
        }
    }

    private SearchTokens toSearchTokens(String rawValue) {
        String stripped = ChatColor.stripColor(safe(rawValue));
        String normalized = normalizeToken(stripped);
        StringBuilder pinyin = new StringBuilder();
        StringBuilder initials = new StringBuilder();
        for (char character : stripped.toCharArray()) {
            if (Character.isWhitespace(character)) {
                continue;
            }
            String[] values;
            try {
                values = PinyinHelper.toHanyuPinyinStringArray(character, PINYIN_FORMAT);
            } catch (BadHanyuPinyinOutputFormatCombination ignored) {
                values = null;
            }
            if (values != null && values.length > 0) {
                String candidate = values[0].replaceAll("[^a-z0-9]", "");
                if (!candidate.isBlank()) {
                    pinyin.append(candidate);
                    initials.append(candidate.charAt(0));
                    continue;
                }
            }
            if (Character.isLetterOrDigit(character)) {
                pinyin.append(Character.toLowerCase(character));
                initials.append(Character.toLowerCase(character));
            }
        }
        return new SearchTokens(normalized, pinyin.toString(), initials.toString());
    }

    private String sha256Hex(byte[] payload) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(payload);
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte current : digest) {
                builder.append(Character.forDigit((current >> 4) & 0xF, 16));
                builder.append(Character.forDigit(current & 0xF, 16));
            }
            return builder.toString();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to build warehouse item fingerprint.", exception);
        }
    }

    private static String value(List<String> data, int index, String defaultValue) {
        return data != null && data.size() > index ? safe(data.get(index)) : defaultValue;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeId(String value) {
        return safe(value).toLowerCase(Locale.ROOT);
    }

    private static String normalizeToken(String value) {
        String stripped = ChatColor.stripColor(safe(value));
        return stripped == null ? "" : stripped.toLowerCase(Locale.ROOT).replace(" ", "");
    }

    private static String crop(String value, int maxLength) {
        String safe = safe(value);
        return safe.length() <= maxLength ? safe : safe.substring(0, maxLength);
    }

    public record ActionResult(boolean success, String message) {
        public static ActionResult success(String message) {
            return new ActionResult(true, message);
        }

        public static ActionResult failure(String message) {
            return new ActionResult(false, message);
        }
    }

    private record SearchTokens(String searchText, String pinyin, String initials) {
    }

    private record DepositResult(boolean success, long storedAmount, int remainingAmount, String message) {
        static DepositResult success(long storedAmount, int remainingAmount) {
            return new DepositResult(true, storedAmount, remainingAmount, "");
        }

        static DepositResult failure(String message) {
            return new DepositResult(false, 0L, 0, message);
        }
    }

    private static final class ViewState {
        private String ownerType;
        private String ownerId;
        private String warehouseId;
        private String categoryId;
        private String search;
        private int page;
        private int selectedSlot;
        private boolean sharedEditMode;
        private boolean autoPickup;
        private boolean autoPickupMythic;
        private boolean autoPickupNotify;
        private boolean previewMode;
        private UUID previewTargetUuid;
        private String previewWarehouseId;

        private ViewState(String ownerType, String ownerId, String warehouseId, String categoryId, String search, int selectedSlot,
                          boolean autoPickup, boolean autoPickupMythic, boolean autoPickupNotify) {
            this.ownerType = ownerType;
            this.ownerId = ownerId;
            this.warehouseId = warehouseId;
            this.categoryId = categoryId;
            this.search = search;
            this.page = 1;
            this.selectedSlot = selectedSlot;
            this.sharedEditMode = false;
            this.autoPickup = autoPickup;
            this.autoPickupMythic = autoPickupMythic;
            this.autoPickupNotify = autoPickupNotify;
            this.previewMode = false;
            this.previewTargetUuid = null;
            this.previewWarehouseId = null;
        }

        static ViewState initial(Player player, boolean autoPickup, boolean autoPickupMythic, boolean autoPickupNotify) {
            return new ViewState(OWNER_PERSONAL, player.getUniqueId().toString(), "", "all", "", -1, autoPickup, autoPickupMythic, autoPickupNotify);
        }

        static ViewState shared(String sharedId, Player player, boolean autoPickup, boolean autoPickupMythic, boolean autoPickupNotify) {
            return new ViewState(OWNER_SHARED, sharedId, sharedId, "all", "", -1, autoPickup, autoPickupMythic, autoPickupNotify);
        }

        static ViewState preview(UUID targetUuid, String warehouseId, String ownerType) {
            String effectiveWarehouseId = warehouseId;
            String effectiveOwnerId = targetUuid.toString();
            if (OWNER_SHARED.equals(ownerType)) {
                effectiveOwnerId = warehouseId;
                effectiveWarehouseId = warehouseId;
            }
            ViewState state = new ViewState(ownerType, effectiveOwnerId, effectiveWarehouseId, "all", "", -1, false, false, false);
            state.previewMode = true;
            state.previewTargetUuid = targetUuid;
            state.previewWarehouseId = warehouseId;
            return state;
        }

        String ownerType() { return ownerType; }
        String ownerId() { return ownerId; }
        String warehouseId() { return warehouseId; }
        String categoryId() { return categoryId; }
        String search() { return search; }
        int page() { return page; }
        int selectedSlot() { return selectedSlot; }
        boolean sharedEditMode() { return sharedEditMode; }
        boolean autoPickup() { return autoPickup; }
        boolean autoPickupMythic() { return autoPickupMythic; }
        boolean autoPickupNotify() { return autoPickupNotify; }
        boolean previewMode() { return previewMode; }
        UUID previewTargetUuid() { return previewTargetUuid; }
        String previewWarehouseId() { return previewWarehouseId; }
        void setAutoPickup(boolean autoPickup) { this.autoPickup = autoPickup; }
        void setAutoPickupMythic(boolean autoPickupMythic) { this.autoPickupMythic = autoPickupMythic; }
        void setAutoPickupNotify(boolean autoPickupNotify) { this.autoPickupNotify = autoPickupNotify; }
        void setOwnerType(String ownerType) { this.ownerType = ownerType; }
        void setOwnerId(String ownerId) { this.ownerId = ownerId; }
        void setWarehouseId(String warehouseId) { this.warehouseId = warehouseId; }
        void setCategoryId(String categoryId) { this.categoryId = categoryId; }
        void setSearch(String search) { this.search = search; }
        void setPage(int page) { this.page = page; }
        void setSelectedSlot(int selectedSlot) { this.selectedSlot = selectedSlot; }
        void setSharedEditMode(boolean sharedEditMode) { this.sharedEditMode = sharedEditMode; }
    }
}




