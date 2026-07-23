package xuanmo.arcartxsuite.extrabackpack.service;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.capability.ExtraBackpackAccess;
import xuanmo.arcartxsuite.api.capability.SecondaryPasswordAccess;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;
import xuanmo.arcartxsuite.api.util.ItemSerializer;
import xuanmo.arcartxsuite.extrabackpack.config.ExtraBackpackConfiguration;
import xuanmo.arcartxsuite.extrabackpack.config.ExtraBackpackConfiguration.ExtraBackpackCategory;
import xuanmo.arcartxsuite.extrabackpack.config.ExtraBackpackConfiguration.UpgradeCost;
import xuanmo.arcartxsuite.extrabackpack.storage.ExtraBackpackRepository;
import xuanmo.arcartxsuite.extrabackpack.storage.ExtraBackpackRepository.ExtraBackpackSlotRecord;

/**
 * 额外槽位背包服务：默认关闭。启用后与玩家原版背包共存，
 * 由多个分类页（装备/材料/杂物…）组成，每页独立配置槽位数量、上限与扩容价格。
 * <p>
 * 物品分类依据 {@link ExtraBackpackCategory} 的 NBT/lore 匹配规则，由本模块自行判断。
 * 数据存于独立表 {@code extra_backpack_slots} / {@code extra_backpack_capacity}。
 */
public final class ExtraBackpackService implements Listener, ExtraBackpackAccess {

    private static final String VANILLA_CATEGORY_ID = "__vanilla__";

    private final JavaPlugin plugin;
    private final Logger logger;
    private final PacketBridgeAPI packetBridge;
    private final ItemBridgeAPI itemBridge;
    private final PacketGuardAPI packetGuard;
    private final UiResourceExporter uiResourceExporter;
    private final ExtraBackpackConfiguration configuration;
    private final ExtraBackpackRepository repository;
    private final CurrencyBridgeAPI currencyBridge;
    private final MessageProvider messages;

    private static final String UI_RESOURCE_PATH = "arcartx/ui/extrabackpack.yml";
    private static final String UI_FILE_PATH = "ui/extrabackpack.yml";

    private String runtimeUiId = "";
    private String registeredUiId = "";
    private final ConcurrentMap<UUID, String> actionTokens = new ConcurrentHashMap<>();
    private final ThreadLocal<Set<UUID>> committedActionTokens =
        ThreadLocal.withInitial(java.util.HashSet::new);

    /** 玩家内存态背包：分类 ID → 该页槽位物品数组（数组长度=当前容量）。 */
    private final ConcurrentMap<UUID, Map<String, ItemStack[]>> backpacks = new ConcurrentHashMap<>();
    /** 玩家当前查看的分类页。 */
    private final ConcurrentMap<UUID, String> activeCategory = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Integer> vanillaInfoSlots = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Integer> vanillaOptionSlots = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, String> passwordPanelModes = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Integer> pendingDestroySlots = new ConcurrentHashMap<>();
    /** Serializes state mutation and persistence for each player independently. */
    private final ConcurrentMap<UUID, Object> playerLocks = new ConcurrentHashMap<>();

    public ExtraBackpackService(
        JavaPlugin plugin,
        Logger logger,
        PacketBridgeAPI packetBridge,
        ItemBridgeAPI itemBridge,
        PacketGuardAPI packetGuard,
        UiResourceExporter uiResourceExporter,
        ExtraBackpackConfiguration configuration,
        ExtraBackpackRepository repository,
        CurrencyBridgeAPI currencyBridge,
        MessageProvider messages
    ) {
        this.plugin = plugin;
        this.logger = logger;
        this.packetBridge = packetBridge;
        this.itemBridge = itemBridge;
        this.packetGuard = packetGuard;
        this.uiResourceExporter = uiResourceExporter;
        this.configuration = configuration;
        this.repository = repository;
        this.currencyBridge = currencyBridge;
        this.messages = messages;
    }

    @FunctionalInterface
    public interface UiResourceExporter {
        File export(String resourcePath, String relativeUiPath, boolean overwrite) throws IOException;
    }

    public boolean enabled() {
        return configuration.extraBackpack().enabled() && !configuration.extraBackpack().categories().isEmpty();
    }

    private Object playerLock(UUID uuid) {
        return playerLocks.computeIfAbsent(uuid, ignored -> new Object());
    }

    @Override
    public boolean isAvailable(@NotNull Player player) {
        return enabled() && player.isOnline();
    }

    @Override
    public @NotNull ExtraBackpackAccess.Snapshot snapshot(@NotNull Player player) {
        if (!isAvailable(player)) {
            return new ExtraBackpackAccess.Snapshot(false, List.of());
        }
        try {
            Map<String, ItemStack[]> pages = pages(player);
            List<ExtraBackpackAccess.CategorySnapshot> categorySnapshots = new ArrayList<>();
            for (ExtraBackpackCategory category : categories()) {
                ItemStack[] page = pages.get(category.id());
                if (page == null) {
                    continue;
                }
                List<ExtraBackpackAccess.SlotSnapshot> slots = new ArrayList<>();
                long used = 0L;
                for (int slot = 0; slot < page.length; slot++) {
                    ItemStack stack = page[slot];
                    if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
                        continue;
                    }
                    used++;
                    slots.add(new ExtraBackpackAccess.SlotSnapshot(slot, stack.clone()));
                }
                categorySnapshots.add(new ExtraBackpackAccess.CategorySnapshot(
                    category.id(),
                    page.length,
                    used,
                    slots
                ));
            }
            return new ExtraBackpackAccess.Snapshot(true, categorySnapshots);
        } catch (Exception exception) {
            logger.warning("读取额外背包快照失败(" + player.getName() + "): " + exception.getMessage());
            return new ExtraBackpackAccess.Snapshot(false, List.of());
        }
    }

    @Override
    public @NotNull ExtraBackpackAccess.DepositResult deposit(
        @NotNull Player player,
        @NotNull ItemStack itemStack
    ) {
        if (!isAvailable(player)) {
            return new ExtraBackpackAccess.DepositResult(
                false,
                0L,
                Math.max(0, itemStack.getAmount()),
                message("extra-backpack.disabled")
            );
        }
        ItemStack input = itemStack.clone();
        int originalAmount = input.getAmount();
        if (originalAmount <= 0 || input.getType().isAir()) {
            return new ExtraBackpackAccess.DepositResult(false, 0L, 0, "");
        }
        synchronized (playerLock(player.getUniqueId())) {
            try {
                ItemStack leftover = storeIntoBackpack(player, input);
                int remaining = leftover == null ? 0 : Math.max(0, leftover.getAmount());
                persist(player.getUniqueId());
                long stored = originalAmount - remaining;
                return new ExtraBackpackAccess.DepositResult(
                    stored > 0L,
                    stored,
                    remaining,
                    remaining > 0 ? message("extra-backpack.page-full") : ""
                );
            } catch (Exception exception) {
                logger.warning("额外背包存入失败(" + player.getName() + "): " + exception.getMessage());
                return new ExtraBackpackAccess.DepositResult(
                    false,
                    0L,
                    originalAmount,
                    message("extra-backpack.operation-failed")
                );
            }
        }
    }

    @Override
    public @NotNull ExtraBackpackAccess.WithdrawResult withdraw(
        @NotNull Player player,
        @NotNull String categoryId,
        int slot,
        int amount
    ) {
        if (!isAvailable(player) || amount <= 0) {
            return new ExtraBackpackAccess.WithdrawResult(
                false,
                0,
                null,
                message("extra-backpack.operation-failed")
            );
        }
        ExtraBackpackCategory category = category(categoryId);
        if (category == null) {
            return new ExtraBackpackAccess.WithdrawResult(false, 0, null, "");
        }
        synchronized (playerLock(player.getUniqueId())) {
            try {
                ItemStack[] page = pages(player).get(category.id());
                if (page == null || slot < 0 || slot >= page.length) {
                    return new ExtraBackpackAccess.WithdrawResult(false, 0, null, "");
                }
                ItemStack stored = page[slot];
                if (stored == null || stored.getType().isAir() || stored.getAmount() <= 0) {
                    return new ExtraBackpackAccess.WithdrawResult(false, 0, null, "");
                }
                int taken = Math.min(amount, stored.getAmount());
                ItemStack result = stored.clone();
                result.setAmount(taken);
                if (taken == stored.getAmount()) {
                    page[slot] = null;
                } else {
                    stored.setAmount(stored.getAmount() - taken);
                }
                persist(player.getUniqueId());
                return new ExtraBackpackAccess.WithdrawResult(true, taken, result, "");
            } catch (Exception exception) {
                logger.warning("额外背包取出失败(" + player.getName() + "): " + exception.getMessage());
                return new ExtraBackpackAccess.WithdrawResult(
                    false,
                    0,
                    null,
                    message("extra-backpack.operation-failed")
                );
            }
        }
    }

    public void start() throws Exception {
        if (!enabled()) {
            return;
        }
        bindUi();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (Player online : Bukkit.getOnlinePlayers()) {
            loadPlayer(online);
        }
    }

    public void shutdown() {
        HandlerList.unregisterAll(this);
        for (UUID uuid : new ArrayList<>(backpacks.keySet())) {
            synchronized (playerLock(uuid)) {
                try {
                    persist(uuid);
                } catch (Exception exception) {
                    logger.warning("保存额外背包失败(" + uuid + "): " + exception.getMessage());
                }
            }
        }
        backpacks.clear();
        activeCategory.clear();
        vanillaInfoSlots.clear();
        vanillaOptionSlots.clear();
        passwordPanelModes.clear();
        pendingDestroySlots.clear();
        playerLocks.clear();
        if (packetBridge != null) {
            packetBridge.unregisterUiCloseCallback(runtimeUiId);
            if (!registeredUiId.isBlank()) {
                packetBridge.unregisterUi(registeredUiId);
            }
        }
    }

    private void bindUi() throws Exception {
        File uiFile = uiResourceExporter.export(UI_RESOURCE_PATH, UI_FILE_PATH, configuration.ui().overwriteUiFiles());
        if (packetBridge == null || !configuration.ui().registerUiOnEnable()) {
            runtimeUiId = PacketBridgeAPI.normalizeUiId("AXS:extrabackpack", uiFile);
            return;
        }
        PacketBridgeAPI.UiRegistrationResult registration = packetBridge.registerOrReloadUi("AXS:extrabackpack", uiFile);
        if (!registration.success()) {
            throw new IllegalStateException("注册额外背包 UI 失败: " + registration.message());
        }
        registeredUiId = registration.registeredUiId() == null ? "" : registration.registeredUiId();
        runtimeUiId = registration.runtimeUiId();
        packetBridge.registerUiCloseCallback(runtimeUiId, this::handleUiClosed);
    }

    // ---------------------------------------------------------------------
    // 分类与容量
    // ---------------------------------------------------------------------

    private List<ExtraBackpackCategory> categories() {
        List<ExtraBackpackCategory> list = new ArrayList<>(configuration.extraBackpack().categories().values());
        list.sort(Comparator.comparingInt(ExtraBackpackCategory::priority));
        return list;
    }

    private ExtraBackpackCategory category(String id) {
        return configuration.extraBackpack().categories().get(normalizeId(id));
    }

    private ExtraBackpackCategory fallbackCategory() {
        for (ExtraBackpackCategory category : categories()) {
            if (category.fallback()) {
                return category;
            }
        }
        List<ExtraBackpackCategory> list = categories();
        return list.isEmpty() ? null : list.get(list.size() - 1);
    }

    private boolean matches(ItemStack stack, ExtraBackpackCategory category) {
        String path = category.nbtPath().toLowerCase(Locale.ROOT);
        if ("lore".equals(path)) {
            for (String line : loreLines(stack)) {
                String normalized = normalizeId(line);
                for (String value : category.values()) {
                    if (normalized.contains(value)) {
                        return true;
                    }
                }
            }
            return false;
        }
        String value = normalizeId(resolvePath(stack, category.nbtPath()));
        return !value.isBlank() && category.values().contains(value);
    }

    private long resolveCapacity(UUID uuid, ExtraBackpackCategory category) throws Exception {
        long stored = repository.loadExtraBackpackCapacity(uuid, category.id());
        long capacity = stored < 0 ? category.initialSlots() : stored;
        return Math.max(category.initialSlots(), Math.min(capacity, category.maxSlots()));
    }

    // ---------------------------------------------------------------------
    // 加载 / 保存
    // ---------------------------------------------------------------------

    private void loadPlayer(Player player) throws Exception {
        UUID uuid = player.getUniqueId();
        synchronized (playerLock(uuid)) {
            Map<String, ItemStack[]> pages = new LinkedHashMap<>();
            for (ExtraBackpackCategory category : categories()) {
                pages.put(category.id(), new ItemStack[(int) resolveCapacity(uuid, category)]);
            }
            for (ExtraBackpackSlotRecord record : repository.loadExtraBackpackSlots(uuid)) {
                try {
                    ItemStack stack = ItemSerializer.deserialize(Base64.getDecoder().decode(record.itemData()));
                    reclassifyIntoPages(player, pages, stack);
                } catch (RuntimeException ignored) {
                    // 损坏的记录跳过，避免整份背包加载失败。
                }
            }
            backpacks.put(uuid, pages);
            activeCategory.putIfAbsent(uuid, categories().isEmpty() ? "" : categories().get(0).id());
            persist(uuid);
        }
    }

    private Map<String, ItemStack[]> pages(Player player) throws Exception {
        Map<String, ItemStack[]> pages = backpacks.get(player.getUniqueId());
        if (pages == null) {
            loadPlayer(player);
            pages = backpacks.get(player.getUniqueId());
        }
        return pages;
    }

    private void persist(UUID uuid) throws Exception {
        synchronized (playerLock(uuid)) {
            Map<String, ItemStack[]> pages = backpacks.get(uuid);
            if (pages == null) {
                return;
            }
            List<ExtraBackpackSlotRecord> records = new ArrayList<>();
            long now = System.currentTimeMillis();
            for (Map.Entry<String, ItemStack[]> entry : pages.entrySet()) {
                ItemStack[] page = entry.getValue();
                for (int slot = 0; slot < page.length; slot++) {
                    ItemStack stack = page[slot];
                    if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
                        continue;
                    }
                    String data = Base64.getEncoder().encodeToString(ItemSerializer.serialize(stack));
                    records.add(new ExtraBackpackSlotRecord(uuid, entry.getKey(), slot, data, now));
                }
            }
            repository.saveExtraBackpackSlots(uuid, records);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        try {
            loadPlayer(player);
        } catch (Exception exception) {
            logger.warning("加载额外背包失败(" + player.getName() + "): " + exception.getMessage());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        synchronized (playerLock(uuid)) {
            try {
                persist(uuid);
            } catch (Exception exception) {
                logger.warning("保存额外背包失败(" + uuid + "): " + exception.getMessage());
            }
            backpacks.remove(uuid);
            activeCategory.remove(uuid);
            vanillaInfoSlots.remove(uuid);
            vanillaOptionSlots.remove(uuid);
            passwordPanelModes.remove(uuid);
            pendingDestroySlots.remove(uuid);
            actionTokens.remove(uuid);
        }
        playerLocks.remove(uuid);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!enabled() || !(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack stack = event.getItem().getItemStack();
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        synchronized (playerLock(player.getUniqueId())) {
            String categoryId = matchCategory(stack);
            if (categoryId.isBlank()) {
                return;
            }
            try {
                ItemStack leftover = storeIntoBackpack(player, stack);
                if (leftover == null || leftover.getAmount() <= 0) {
                    event.getItem().remove();
                    event.setCancelled(true);
                    persist(player.getUniqueId());
                    return;
                }
                if (leftover.getAmount() != stack.getAmount()) {
                    event.getItem().setItemStack(leftover);
                    persist(player.getUniqueId());
                }
            } catch (Exception exception) {
                logger.warning("拾取自动分类额外背包失败(" + player.getName() + "): " + exception.getMessage());
            }
        }
    }

    private String matchCategory(ItemStack stack) {
        for (ExtraBackpackCategory category : categories()) {
            if (category.fallback() || category.nbtPath().isBlank() || category.values().isEmpty()) {
                continue;
            }
            if (matches(stack, category)) {
                return category.id();
            }
        }
        return "";
    }

    private String fallbackCategoryId() {
        ExtraBackpackCategory fallback = fallbackCategory();
        return fallback == null ? "" : fallback.id();
    }

    private void reclassifyIntoPages(
        Player player,
        Map<String, ItemStack[]> pages,
        ItemStack stack
    ) {
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            return;
        }
        ItemStack leftover = storeIntoPage(pages.get(matchCategory(stack)), stack);
        if (leftover != null && leftover.getAmount() > 0) {
            String fallbackId = fallbackCategoryId();
            if (!fallbackId.isBlank() && !fallbackId.equals(matchCategory(stack))) {
                leftover = storeIntoPage(pages.get(fallbackId), leftover);
            }
        }
        if (leftover != null && leftover.getAmount() > 0) {
            returnToVanilla(player, leftover);
        }
    }

    private void returnToVanilla(Player player, ItemStack stack) {
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            return;
        }
        for (ItemStack leftover : player.getInventory().addItem(stack).values()) {
            if (leftover != null && leftover.getAmount() > 0) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
    }

    private ItemStack storeIntoPage(ItemStack[] page, ItemStack stack) {
        if (page == null) {
            return stack;
        }
        ItemStack working = stack.clone();
        for (int slot = 0; slot < page.length && working.getAmount() > 0; slot++) {
            ItemStack existing = page[slot];
            if (existing != null && existing.isSimilar(working)) {
                int space = existing.getMaxStackSize() - existing.getAmount();
                if (space > 0) {
                    int move = Math.min(space, working.getAmount());
                    existing.setAmount(existing.getAmount() + move);
                    working.setAmount(working.getAmount() - move);
                }
            }
        }
        for (int slot = 0; slot < page.length && working.getAmount() > 0; slot++) {
            if (page[slot] == null || page[slot].getType().isAir()) {
                int move = Math.min(working.getMaxStackSize(), working.getAmount());
                ItemStack placed = working.clone();
                placed.setAmount(move);
                page[slot] = placed;
                working.setAmount(working.getAmount() - move);
            }
        }
        return working.getAmount() > 0 ? working : null;
    }

    /** 把物品尽量放入快捷栏 0-8，返回剩余部分（可能为 null）。 */
    private ItemStack addToHotbar(Player player, ItemStack stack) {
        PlayerInventory inventory = player.getInventory();
        ItemStack working = stack.clone();
        for (int slot = 0; slot <= 8 && working.getAmount() > 0; slot++) {
            ItemStack existing = inventory.getItem(slot);
            if (existing != null && existing.isSimilar(working)) {
                int space = existing.getMaxStackSize() - existing.getAmount();
                if (space > 0) {
                    int move = Math.min(space, working.getAmount());
                    existing.setAmount(existing.getAmount() + move);
                    inventory.setItem(slot, existing);
                    working.setAmount(working.getAmount() - move);
                }
            }
        }
        for (int slot = 0; slot <= 8 && working.getAmount() > 0; slot++) {
            ItemStack existing = inventory.getItem(slot);
            if (existing == null || existing.getType().isAir()) {
                int move = Math.min(working.getMaxStackSize(), working.getAmount());
                ItemStack placed = working.clone();
                placed.setAmount(move);
                inventory.setItem(slot, placed);
                working.setAmount(working.getAmount() - move);
            }
        }
        return working.getAmount() > 0 ? working : null;
    }

    /** 将物品按分类存入额外背包，返回放不下的剩余部分（可能为 null）。 */
    private ItemStack storeIntoBackpack(Player player, ItemStack stack) throws Exception {
        Map<String, ItemStack[]> pages = pages(player);
        String categoryId = matchCategory(stack);
        ItemStack[] page = pages.get(categoryId);
        return storeIntoPage(page, stack);
    }

    private ItemStack storeIntoBackpackWithFallback(Player player, ItemStack stack) throws Exception {
        Map<String, ItemStack[]> pages = pages(player);
        String categoryId = matchCategory(stack);
        ItemStack leftover = storeIntoPage(pages.get(categoryId), stack);
        if (leftover != null && leftover.getAmount() > 0) {
            String fallbackId = fallbackCategoryId();
            if (!fallbackId.isBlank() && !fallbackId.equals(categoryId)) {
                leftover = storeIntoPage(pages.get(fallbackId), leftover);
            }
        }
        return leftover;
    }

    // ---------------------------------------------------------------------
    // 客户端包处理
    // ---------------------------------------------------------------------

    public boolean handleClientPacket(Player player, String packetId, List<String> data) {
        return handleClientPacket(player, packetId, data, null);
    }

    public boolean handleClientPacket(
        Player player,
        String packetId,
        List<String> data,
        SecondaryPasswordAccess passwordAccess
    ) {
        if (!enabled() || player == null || !player.isOnline() || packetId == null) {
            return false;
        }
        if (!("AXS_BACKPACK".equalsIgnoreCase(packetId))) {
            return false;
        }
        String action = data == null || data.isEmpty() ? "refresh" : safe(data.get(0)).toLowerCase(Locale.ROOT);
        if (requiresOpenUi(action)
            && (packetBridge == null || !packetBridge.isUiOpen(player, runtimeUiId))) {
            sendMessage(player, false, message("extra-backpack.ui-required"));
            return true;
        }
        synchronized (playerLock(player.getUniqueId())) {
            boolean tokenized = requiresActionToken(action);
            String suppliedToken = data == null || data.isEmpty()
                ? "" : safe(data.get(data.size() - 1));
            if (tokenized && (suppliedToken.isBlank()
                || !suppliedToken.equals(actionTokens.get(player.getUniqueId())))) {
                sendMessage(player, false, message("extra-backpack.action-token-invalid"));
                return true;
            }
            try {
                switch (action) {
                    case "open", "refresh" -> refresh(player);
                    case "category" -> setCategory(player, value(data, 1, ""));
                    case "withdraw" -> withdraw(player, parseInt(value(data, 1, "-1"), -1), false);
                    case "withdraw_all" -> withdraw(player, parseInt(value(data, 1, "-1"), -1), true);
                    case "store" -> storeHotbar(player, parseInt(value(data, 1, "-1"), -1));
                    case "buy_slots" -> buySlots(player, parseInt(value(data, 1, "1"), 1));
                    case "vanilla_info" -> vanillaInfo(player, parseInt(value(data, 1, "-1"), -1));
                    case "vanilla_options" -> vanillaOptions(player, parseInt(value(data, 1, "-1"), -1));
                    case "vanilla_store" -> storeVanillaSlot(player, parseInt(value(data, 1, "-1"), -1));
                    case "vanilla_destroy" ->
                        destroyVanillaSlot(
                            player,
                            parseInt(value(data, 1, "-1"), -1),
                            "true".equalsIgnoreCase(value(data, 2, "false")),
                            value(data, 3, ""),
                            passwordAccess
                        );
                    case "password_set" -> passwordSet(
                        player, value(data, 1, ""), value(data, 2, ""), passwordAccess);
                    case "password_unlock" -> passwordUnlock(
                        player, value(data, 1, ""), passwordAccess);
                    case "password_clear" -> passwordClear(
                        player, value(data, 1, ""), passwordAccess);
                    case "password_panel_close" -> closePasswordPanel(player);
                    case "close" -> handleUiClosed(player);
                    default -> refresh(player);
                }
                if (tokenized) {
                    committedActionTokens.get().remove(player.getUniqueId());
                }
            } catch (Exception exception) {
                logger.warning("处理额外背包客户端包失败: " + exception.getMessage());
                sendMessage(player, false, message("extra-backpack.operation-failed"));
            }
        }
        return true;
    }

    public void open(Player player) throws Exception {
        if (!enabled() || packetBridge == null) {
            return;
        }
        pages(player);
        if (packetBridge.openUi(player, runtimeUiId)) {
            actionTokens.put(player.getUniqueId(), UUID.randomUUID().toString());
        }
    }

    private void setCategory(Player player, String categoryId) throws Exception {
        if (VANILLA_CATEGORY_ID.equals(normalizeId(categoryId))) {
            activeCategory.put(player.getUniqueId(), VANILLA_CATEGORY_ID);
            refresh(player);
            return;
        }
        ExtraBackpackCategory category = category(categoryId);
        if (category != null) {
            activeCategory.put(player.getUniqueId(), category.id());
        }
        refresh(player);
    }

    private String currentCategory(Player player) {
        String id = activeCategory.get(player.getUniqueId());
        if (VANILLA_CATEGORY_ID.equals(id)) {
            return VANILLA_CATEGORY_ID;
        }
        if (id != null && category(id) != null) {
            return id;
        }
        List<ExtraBackpackCategory> list = categories();
        return list.isEmpty() ? "" : list.get(0).id();
    }

    private void vanillaInfo(Player player, int slot) throws Exception {
        if (!validVanillaSlot(slot)) {
            vanillaInfoSlots.remove(player.getUniqueId());
        } else {
            vanillaInfoSlots.put(player.getUniqueId(), slot);
        }
        refresh(player);
    }

    private void vanillaOptions(Player player, int slot) throws Exception {
        if (!validVanillaSlot(slot)) {
            vanillaOptionSlots.remove(player.getUniqueId());
        } else {
            vanillaOptionSlots.put(player.getUniqueId(), slot);
        }
        refresh(player);
    }

    private void storeVanillaSlot(Player player, int slot) throws Exception {
        if (!validVanillaSlot(slot)) {
            refresh(player);
            return;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack current = inventory.getItem(slot);
        if (current == null || current.getType().isAir() || current.getAmount() <= 0) {
            refresh(player);
            return;
        }
        ItemStack original = current.clone();
        ItemStack leftover = storeIntoBackpackWithFallback(player, original);
        int remaining = leftover == null ? 0 : leftover.getAmount();
        if (remaining == original.getAmount()) {
            sendMessage(player, false, message("extra-backpack.no-available-slot"));
            refresh(player);
            return;
        }
        // Re-read the live slot immediately before mutating it. Packet handling runs on the main thread.
        ItemStack latest = inventory.getItem(slot);
        if (latest == null || !latest.isSimilar(original) || latest.getAmount() != original.getAmount()) {
            sendMessage(player, false, message("extra-backpack.item-changed"));
            refresh(player);
            return;
        }
        inventory.setItem(slot, leftover);
        persist(player.getUniqueId());
        vanillaOptionSlots.remove(player.getUniqueId());
        refresh(player);
    }

    private void destroyVanillaSlot(
        Player player,
        int slot,
        boolean confirmed,
        String password,
        SecondaryPasswordAccess passwordAccess
    ) throws Exception {
        if (passwordAccess == null) {
            sendMessage(player, false, message("extra-backpack.destroy-unavailable"));
            refresh(player);
            return;
        }
        if (!confirmed) {
            sendMessage(player, false, message("extra-backpack.destroy-confirm"));
            refresh(player);
            return;
        }
        if (!validVanillaSlot(slot)) {
            refresh(player);
            return;
        }
        if (!passwordAccess.isPasswordSet(player)) {
            pendingDestroySlots.put(player.getUniqueId(), slot);
            passwordPanelModes.put(player.getUniqueId(), "set");
            sendMessage(player, false, message("extra-backpack.password-set-required"));
            refresh(player);
            return;
        }
        if (!passwordAccess.isUnlocked(player)) {
            if (password.isBlank() || !passwordAccess.verify(player, password)) {
                pendingDestroySlots.put(player.getUniqueId(), slot);
                passwordPanelModes.put(player.getUniqueId(), "unlock");
                sendMessage(player, false, message("extra-backpack.destroy-password-required"));
                refresh(player);
                return;
            }
        }
        destroyCurrentVanillaSlot(player, slot);
    }

    private void destroyCurrentVanillaSlot(Player player, int slot) throws Exception {
        if (!validVanillaSlot(slot)) {
            refresh(player);
            return;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack current = inventory.getItem(slot);
        if (current == null || current.getType().isAir() || current.getAmount() <= 0) {
            refresh(player);
            return;
        }
        // The current stack is read again after verification; only that live slot is removed.
        inventory.setItem(slot, null);
        vanillaOptionSlots.remove(player.getUniqueId());
        pendingDestroySlots.remove(player.getUniqueId());
        passwordPanelModes.remove(player.getUniqueId());
        sendMessage(player, true, message("extra-backpack.destroy-success"));
        markActionCommitted(player);
        refresh(player);
    }

    private void passwordSet(
        Player player,
        String oldPassword,
        String newPassword,
        SecondaryPasswordAccess passwordAccess
    ) throws Exception {
        if (passwordAccess == null) {
            sendMessage(player, false, message("extra-backpack.destroy-unavailable"));
            refresh(player);
            return;
        }
        if (!passwordAccess.set(player, oldPassword, newPassword)) {
            sendMessage(player, false, message("extra-backpack.password-set-failed"));
            refresh(player);
            return;
        }
        sendMessage(player, true, message("extra-backpack.password-set-success"));
        markActionCommitted(player);
        passwordPanelModes.remove(player.getUniqueId());
        Integer pending = pendingDestroySlots.get(player.getUniqueId());
        if (pending != null) {
            destroyCurrentVanillaSlot(player, pending);
        } else {
            refresh(player);
        }
    }

    private void passwordUnlock(
        Player player,
        String password,
        SecondaryPasswordAccess passwordAccess
    ) throws Exception {
        if (passwordAccess == null) {
            sendMessage(player, false, message("extra-backpack.destroy-unavailable"));
            refresh(player);
            return;
        }
        if (!passwordAccess.verify(player, password)) {
            sendMessage(player, false, message("extra-backpack.password-wrong"));
            refresh(player);
            return;
        }
        sendMessage(player, true, message("extra-backpack.password-unlocked"));
        passwordPanelModes.remove(player.getUniqueId());
        Integer pending = pendingDestroySlots.get(player.getUniqueId());
        if (pending != null) {
            destroyCurrentVanillaSlot(player, pending);
        } else {
            refresh(player);
        }
    }

    private void passwordClear(
        Player player,
        String password,
        SecondaryPasswordAccess passwordAccess
    ) throws Exception {
        if (passwordAccess == null) {
            sendMessage(player, false, message("extra-backpack.destroy-unavailable"));
            refresh(player);
            return;
        }
        if (!passwordAccess.clear(player, password)) {
            sendMessage(player, false, message("extra-backpack.password-wrong"));
            refresh(player);
            return;
        }
        sendMessage(player, true, message("extra-backpack.password-cleared"));
        markActionCommitted(player);
        passwordPanelModes.remove(player.getUniqueId());
        refresh(player);
    }

    private void closePasswordPanel(Player player) throws Exception {
        passwordPanelModes.remove(player.getUniqueId());
        pendingDestroySlots.remove(player.getUniqueId());
        refresh(player);
    }

    private boolean validVanillaSlot(int slot) {
        return slot >= 0 && slot <= 35;
    }

    private void withdraw(Player player, int slot, boolean all) throws Exception {
        Map<String, ItemStack[]> pages = pages(player);
        String categoryId = currentCategory(player);
        ItemStack[] page = pages.get(categoryId);
        if (page == null || slot < 0 || slot >= page.length) {
            refresh(player);
            return;
        }
        ItemStack stack = page[slot];
        if (stack == null || stack.getType().isAir()) {
            refresh(player);
            return;
        }
        ItemStack remaining = addToHotbar(player, stack);
        if (remaining == null || remaining.getAmount() <= 0) {
            page[slot] = null;
        } else {
            page[slot] = remaining;
            player.getWorld().dropItemNaturally(player.getLocation(), stack.clone());
            page[slot] = null;
        }
        persist(player.getUniqueId());
        refresh(player);
    }

    private void storeHotbar(Player player, int hotbarSlot) throws Exception {
        if (hotbarSlot < 0 || hotbarSlot > 8) {
            refresh(player);
            return;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack stack = inventory.getItem(hotbarSlot);
        if (stack == null || stack.getType().isAir()) {
            refresh(player);
            return;
        }
        ItemStack leftover = storeIntoBackpack(player, stack);
        inventory.setItem(hotbarSlot, leftover);
        if (leftover != null && leftover.getAmount() > 0) {
            sendMessage(player, false, message("extra-backpack.page-full"));
        }
        persist(player.getUniqueId());
        refresh(player);
    }

    private void buySlots(Player player, int amount) throws Exception {
        String categoryId = currentCategory(player);
        ExtraBackpackCategory category = category(categoryId);
        if (category == null) {
            refresh(player);
            return;
        }
        if (amount <= 0) {
            sendMessage(player, false, message("extra-backpack.buy-invalid"));
            refresh(player);
            return;
        }
        UpgradeCost price = category.pricePerSlot();
        if (price == null) {
            sendMessage(player, false, message("extra-backpack.buy-not-configured"));
            refresh(player);
            return;
        }
        long current = resolveCapacity(player.getUniqueId(), category);
        if (current >= category.maxSlots()) {
            sendMessage(player, false, message("extra-backpack.buy-maxed"));
            refresh(player);
            return;
        }
        long actual = Math.min((long) amount, category.maxSlots() - current);
        UpgradeCost cost = new UpgradeCost(price.currencyId(), price.amount().multiply(BigDecimal.valueOf(actual)));
        if (!withdrawCost(player, cost)) {
            refresh(player);
            return;
        }
        long newCapacity = current + actual;
        try {
            repository.setExtraBackpackCapacity(player.getUniqueId(), category.id(), newCapacity, System.currentTimeMillis());
        } catch (Exception exception) {
            refundCost(player, cost);
            throw exception;
        }
        // 扩容后按新容量重建该页数组（保留已有物品）。
        Map<String, ItemStack[]> pages = pages(player);
        ItemStack[] oldPage = pages.get(category.id());
        ItemStack[] newPage = new ItemStack[(int) newCapacity];
        if (oldPage != null) {
            System.arraycopy(oldPage, 0, newPage, 0, Math.min(oldPage.length, newPage.length));
        }
        pages.put(category.id(), newPage);
        sendMessage(player, true, message("extra-backpack.buy-success", actual, newCapacity));
        markActionCommitted(player);
        refresh(player);
    }

    private boolean requiresOpenUi(String action) {
        return "withdraw".equals(action)
            || "withdraw_all".equals(action)
            || "store".equals(action)
            || "buy_slots".equals(action)
            || "vanilla_store".equals(action)
            || "vanilla_destroy".equals(action)
            || "password_set".equals(action)
            || "password_unlock".equals(action)
            || "password_clear".equals(action);
    }

    private boolean requiresActionToken(String action) {
        return "buy_slots".equals(action)
            || "vanilla_destroy".equals(action)
            || "password_set".equals(action)
            || "password_clear".equals(action);
    }

    private void markActionCommitted(Player player) {
        if (player != null) {
            UUID uuid = player.getUniqueId();
            committedActionTokens.get().add(uuid);
            actionTokens.put(uuid, UUID.randomUUID().toString());
        }
    }

    private void handleUiClosed(Player player) {
        actionTokens.remove(player.getUniqueId());
        try {
            persist(player.getUniqueId());
        } catch (Exception exception) {
            logger.warning("关闭额外背包保存失败(" + player.getName() + "): " + exception.getMessage());
        }
    }

    // ---------------------------------------------------------------------
    // 数据包构建
    // ---------------------------------------------------------------------

    private void refresh(Player player) throws Exception {
        if (packetBridge == null || runtimeUiId.isBlank()) {
            return;
        }
        packetBridge.sendPacket(player, runtimeUiId, "update", buildPacket(player));
    }

    private Map<String, Object> buildPacket(Player player) throws Exception {
        UUID uuid = player.getUniqueId();
        Map<String, ItemStack[]> pages = pages(player);
        String categoryId = currentCategory(player);
        ExtraBackpackCategory category = category(categoryId);

        Map<String, Object> packet = new LinkedHashMap<>();
        packet.put("packetId", "AXS_BACKPACK");
        packet.put("action_token", actionTokens.getOrDefault(uuid, ""));
        packet.put("activeCategory", categoryId);
        boolean vanillaTab = VANILLA_CATEGORY_ID.equals(categoryId);
        packet.put("vanillaTab", vanillaTab);

        Map<String, Object> categoryMap = new LinkedHashMap<>();
        int index = 0;
        Map<String, Object> vanillaCategory = new LinkedHashMap<>();
        vanillaCategory.put("id", VANILLA_CATEGORY_ID);
        vanillaCategory.put("name", "背包");
        vanillaCategory.put("active", vanillaTab);
        categoryMap.put(Integer.toString(index++), vanillaCategory);
        for (ExtraBackpackCategory definition : categories()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", definition.id());
            row.put("name", definition.displayName());
            row.put("active", definition.id().equals(categoryId));
            categoryMap.put(Integer.toString(index++), row);
        }
        packet.put("categories", categoryMap);
        packet.put("categoryCount", categoryMap.size());

        Map<String, Object> vanillaSlots = new LinkedHashMap<>();
        ItemStack[] page;
        if (vanillaTab) {
            PlayerInventory inventory = player.getInventory();
            page = new ItemStack[36];
            for (int slot = 0; slot <= 35; slot++) {
                page[slot] = inventory.getItem(slot);
            }
        } else {
            page = category == null ? new ItemStack[0] : pages.getOrDefault(category.id(), new ItemStack[0]);
        }
        Map<String, Object> slots = new LinkedHashMap<>();
        int used = 0;
        for (int slot = 0; slot < page.length; slot++) {
            ItemStack stack = page[slot];
            Map<String, Object> row = new LinkedHashMap<>();
            boolean empty = stack == null || stack.getType().isAir();
            row.put("empty", empty);
            row.put("itemJson", empty ? "" : itemJson(stack));
            row.put("amount", empty ? 0 : stack.getAmount());
            slots.put(Integer.toString(slot), row);
            if (vanillaTab) {
                vanillaSlots.put(Integer.toString(slot), row);
            }
            if (!empty) {
                used++;
            }
        }
        packet.put("slots", slots);
        packet.put("vanillaSlots", vanillaSlots);
        packet.put("slotCount", page.length);
        packet.put("used", used);
        packet.put("capacityText", used + "/" + page.length);

        // 玩家快捷栏 0-8，供 UI 展示并点击存入额外背包。
        Map<String, Object> hotbar = new LinkedHashMap<>();
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot <= 8; slot++) {
            ItemStack stack = inventory.getItem(slot);
            Map<String, Object> row = new LinkedHashMap<>();
            boolean empty = stack == null || stack.getType().isAir();
            row.put("empty", empty);
            row.put("itemJson", empty ? "" : itemJson(stack));
            row.put("amount", empty ? 0 : stack.getAmount());
            hotbar.put(Integer.toString(slot), row);
        }
        packet.put("hotbar", hotbar);

        if (category != null && !vanillaTab) {
            long capacity = resolveCapacity(uuid, category);
            long max = category.maxSlots();
            long remaining = Math.max(0, max - capacity);
            UpgradeCost price = category.pricePerSlot();
            boolean canBuy = price != null && capacity < max;
            packet.put("maxCapacity", max);
            packet.put("initialCapacity", category.initialSlots());
            packet.put("canBuySlots", canBuy);
            packet.put("remainingSlots", remaining);
            if (price != null) {
                packet.put("slotPriceAmount", price.amount().toPlainString());
                packet.put("slotPriceCurrency", price.currencyId());
                packet.put("buySlotsText", message("extra-backpack.buy-hint", price.amount().toPlainString(), remaining));
            } else {
                packet.put("buySlotsText", message("extra-backpack.buy-not-configured"));
            }
        } else {
            packet.put("maxCapacity", 0);
            packet.put("canBuySlots", false);
            packet.put("remainingSlots", 0);
            packet.put("buySlotsText", "");
        }
        Map<String, Object> info = new LinkedHashMap<>();
        int infoSlot = vanillaInfoSlots.getOrDefault(uuid, -1);
        ItemStack infoStack = validVanillaSlot(infoSlot) ? player.getInventory().getItem(infoSlot) : null;
        boolean infoEmpty = infoStack == null || infoStack.getType().isAir();
        info.put("visible", !infoEmpty);
        info.put("slot", infoSlot);
        info.put("itemJson", infoEmpty ? "" : itemJson(infoStack));
        info.put("amount", infoEmpty ? 0 : infoStack.getAmount());
        packet.put("vanillaInfo", info);
        Map<String, Object> options = new LinkedHashMap<>();
        int optionSlot = vanillaOptionSlots.getOrDefault(uuid, -1);
        ItemStack optionStack = validVanillaSlot(optionSlot) ? player.getInventory().getItem(optionSlot) : null;
        boolean optionEmpty = optionStack == null || optionStack.getType().isAir();
        options.put("visible", !optionEmpty);
        options.put("slot", optionSlot);
        options.put("itemJson", optionEmpty ? "" : itemJson(optionStack));
        options.put("amount", optionEmpty ? 0 : optionStack.getAmount());
        packet.put("vanillaOptions", options);
        Map<String, Object> passwordPanel = new LinkedHashMap<>();
        String passwordMode = passwordPanelModes.getOrDefault(uuid, "");
        passwordPanel.put("visible", !passwordMode.isBlank());
        passwordPanel.put("mode", passwordMode);
        passwordPanel.put("pendingSlot", pendingDestroySlots.getOrDefault(uuid, -1));
        packet.put("passwordPanel", passwordPanel);
        return packet;
    }

    // ---------------------------------------------------------------------
    // 工具方法
    // ---------------------------------------------------------------------

    private boolean withdrawCost(Player player, UpgradeCost cost) {
        if (cost == null) {
            return true;
        }
        var bridge = currencyBridge == null ? null : currencyBridge.bridge(cost.currencyId());
        if (bridge == null || !bridge.available()) {
            sendMessage(player, false, bridge == null ? message("extra-backpack.unknown-currency") : bridge.unavailableReason());
            return false;
        }
        var result = bridge.withdraw(player, cost.amount());
        if (!result.success()) {
            sendMessage(player, false, result.message());
            return false;
        }
        return true;
    }

    private void refundCost(Player player, UpgradeCost cost) {
        if (cost == null || currencyBridge == null) {
            return;
        }
        var bridge = currencyBridge.bridge(cost.currencyId());
        if (bridge != null && bridge.available()) {
            bridge.deposit(player, cost.amount());
        }
    }

    private String itemJson(ItemStack stack) {
        if (itemBridge == null || stack == null || stack.getType().isAir()) {
            return "";
        }
        return itemBridge.itemToJson(stack).orElse("");
    }

    private List<String> loreLines(ItemStack stack) {
        if (stack == null) {
            return List.of();
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || meta.getLore() == null) {
            return List.of();
        }
        return meta.getLore();
    }

    private String resolvePath(ItemStack stack, String rawPath) {
        String path = safe(rawPath).toLowerCase(Locale.ROOT);
        ItemMeta meta = stack.getItemMeta();
        if ("material".equals(path)) {
            return stack.getType().name();
        }
        if ("display-name".equals(path) || "name".equals(path)) {
            return meta != null && meta.hasDisplayName() ? meta.getDisplayName() : stack.getType().name();
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
        return string == null ? "" : string;
    }

    private String message(String key, Object... args) {
        return messages == null ? "" : messages.get(key, args);
    }

    private void sendMessage(Player player, boolean success, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        player.sendMessage((success ? ChatColor.GREEN : ChatColor.RED) + text);
    }

    private int parseInt(String rawValue, int defaultValue) {
        try {
            return Integer.parseInt(safe(rawValue));
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    private static String value(List<String> data, int index, String defaultValue) {
        return data != null && data.size() > index ? safe(data.get(index)) : defaultValue;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeId(String rawValue) {
        return rawValue == null ? "" : rawValue.trim().toLowerCase(Locale.ROOT);
    }
}
