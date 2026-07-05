package xuanmo.arcartxsuite.fishing.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.api.capability.MailDispatchable;
import xuanmo.arcartxsuite.api.capability.SignalDispatchable;
import xuanmo.arcartxsuite.api.capability.TitleGrantable;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.fishing.config.FishingModuleConfiguration;
import xuanmo.arcartxsuite.fishing.minigame.FishingMinigame;
import xuanmo.arcartxsuite.fishing.minigame.FishingSession;
import xuanmo.arcartxsuite.fishing.model.BaitDefinition;
import xuanmo.arcartxsuite.fishing.model.FishCollectionEntry;
import xuanmo.arcartxsuite.fishing.model.FishDefinition;
import xuanmo.arcartxsuite.fishing.model.FishRarity;
import xuanmo.arcartxsuite.fishing.model.FishingPlayerData;
import xuanmo.arcartxsuite.fishing.model.RodDefinition;
import xuanmo.arcartxsuite.fishing.model.TreasureDefinition;
import xuanmo.arcartxsuite.fishing.model.WaterArea;
import xuanmo.arcartxsuite.fishing.storage.FishingRepository;

public final class FishingService {

    private final JavaPlugin plugin;
    private final FishingModuleConfiguration configuration;
    private final FishingRepository repository;
    private final PacketBridgeAPI packetBridge;
    private final FishingItemGenerator itemGenerator;
    private final Logger logger;
    private final String minigameUiId;
    private final Map<UUID, FishingMinigame> activeMinigames = new HashMap<>();
    private MessageProvider messageProvider;

    // 跨模块能力提供者（懒加载，避免循环依赖）
    private Supplier<EventBusCapability> eventBusProvider;
    private Supplier<SignalDispatchable> signalProvider;
    private Supplier<CurrencyBridgeAPI> currencyProvider;
    private Supplier<TitleGrantable> titleProvider;
    private Supplier<MailDispatchable> mailProvider;

    private static final String PDC_FISH_ID = "axs_fishing_fish_id";
    private static final String PDC_FISH_SIZE = "axs_fishing_fish_size";
    private static final String PDC_IS_PERFECT = "axs_fishing_is_perfect";
    private static final String PDC_BAIT_ID = "axs_fishing_bait_id";
    private static final String PDC_ROD_ID = "axs_fishing_rod_id";

    public FishingService(@NotNull JavaPlugin plugin, @NotNull FishingModuleConfiguration configuration,
                          @NotNull FishingRepository repository, @Nullable PacketBridgeAPI packetBridge,
                          @NotNull FishingItemGenerator itemGenerator,
                          @NotNull Logger logger, @NotNull String minigameUiId) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.repository = repository;
        this.packetBridge = packetBridge;
        this.itemGenerator = itemGenerator;
        this.logger = logger;
        this.minigameUiId = minigameUiId;
    }

    public void setMessageProvider(@Nullable MessageProvider messageProvider) {
        this.messageProvider = messageProvider;
    }

    public void setEventBusProvider(@Nullable Supplier<EventBusCapability> provider) {
        this.eventBusProvider = provider;
    }

    public void setSignalProvider(@Nullable Supplier<SignalDispatchable> provider) {
        this.signalProvider = provider;
    }

    public void setCurrencyProvider(@Nullable Supplier<CurrencyBridgeAPI> provider) {
        this.currencyProvider = provider;
    }

    public void setTitleProvider(@Nullable Supplier<TitleGrantable> provider) {
        this.titleProvider = provider;
    }

    public void setMailProvider(@Nullable Supplier<MailDispatchable> provider) {
        this.mailProvider = provider;
    }

    public void start() throws Exception {
        repository.initialize();
    }

    public void shutdown() {
        for (FishingMinigame minigame : activeMinigames.values()) {
            try {
                minigame.cleanup();
            } catch (Exception ignored) {}
        }
        activeMinigames.clear();
        repository.shutdown();
    }

    public boolean isReplaceVanilla() {
        return configuration.fishing().replaceVanilla();
    }

    public boolean isPlayerInMinigame(@NotNull UUID uuid) {
        return activeMinigames.containsKey(uuid);
    }

    public xuanmo.arcartxsuite.fishing.minigame.FishingMinigame getActiveMinigame(@NotNull UUID uuid) {
        return activeMinigames.get(uuid);
    }

    public void startMinigame(@NotNull Player player) {
        if (packetBridge == null || !packetBridge.isAvailable()) {
            return;
        }
        if (isPlayerInMinigame(player.getUniqueId())) {
            return;
        }

        // 解析水域
        WaterArea water = resolveWater(player);
        if (water == null) water = configuration.defaultWater();

        // 权限检查
        if (water.requirePermission() != null && !player.hasPermission(water.requirePermission())) {
            return;
        }

        // 检测饵料
        BaitDefinition bait = detectBait(player);
        String baitId = bait != null ? bait.id() : null;

        // 检测钓竿
        RodDefinition rod = detectRod(player);

        // 获取该水域的鱼池
        List<FishDefinition> pool = getFishPool(water);
        if (pool.isEmpty()) {
            // 没有符合条件的鱼，走原版
            return;
        }

        FishDefinition fish = selectFishFromPool(player, pool, baitId);
        if (fish == null) {
            return;
        }

        // 应用水域难度修改器
        int adjustedDifficulty = (int) (fish.difficulty() * water.difficultyModifier());
        adjustedDifficulty = Math.max(1, Math.min(100, adjustedDifficulty));

        int caughtSize = fish.randomSize();
        FishingPlayerData playerData = repository.loadPlayerData(player.getUniqueId());

        FishingSession session = new FishingSession(
            player, fish, caughtSize, playerData.level(),
            configuration.fishing().catchDurationTicks() + rod.catchDurationBonus(), water, adjustedDifficulty,
            baitId, rod.greenBarHeightBonus(), rod.expMultiplier()
        );

        FishingMinigame minigame = new FishingMinigame(
            plugin, packetBridge, configuration.fishing(), this, session, minigameUiId
        );
        activeMinigames.put(player.getUniqueId(), minigame);
        minigame.start();

        String msg = getMessage("fishing_start");
        if (msg != null && !msg.isEmpty()) {
            player.sendMessage(msg.replace("{water}", water.displayName()));
        }
    }

    public void onMinigameComplete(@NotNull FishingSession session, boolean success, boolean perfect) {
        activeMinigames.remove(session.playerUuid());
        Player player = Bukkit.getPlayer(session.playerUuid());
        if (player == null) return;

        if (success) {
            handleSuccess(player, session, perfect);
        } else {
            String msg = getMessage("fishing_failed");
            if (msg != null && !msg.isEmpty()) {
                player.sendMessage(msg);
            }
        }
    }

    private void handleSuccess(@NotNull Player player, @NotNull FishingSession session, boolean perfect) {
        FishDefinition fish = session.fish();
        int size = session.caughtSize();
        FishingPlayerData playerData = repository.loadPlayerData(player.getUniqueId());

        // 计算经验（应用钓竿经验倍率）
        int xp = (int) (fish.calculateXp(size, perfect) * session.expMultiplier());
        xp = Math.max(1, xp);
        FishingPlayerData updatedData = playerData.withXpAdded(xp).withCaught(perfect, false);

        // 检查升级
        while (updatedData.canLevelUp(configuration.fishing().baseXpPerLevel())) {
            updatedData = updatedData.withLevelUp();
        }

        repository.savePlayerData(updatedData);

        // 更新图鉴
        FishCollectionEntry existing = repository.loadCollectionEntry(player.getUniqueId(), fish.id());
        boolean isFirstCatch = existing == null || existing.caughtCount() == 0;
        FishCollectionEntry entry = existing != null ? existing : FishCollectionEntry.empty(player.getUniqueId(), fish.id());
        entry = entry.withNewCatch(size);
        repository.saveCollectionEntry(entry);

        // 给予物品
        giveFishItem(player, fish, size, perfect);

        // 检查宝藏（从水域对应的宝藏池中选择）
        boolean gotTreasure = false;
        WaterArea water = session.water();
        double treasureChance = configuration.fishing().treasureChance();
        // 应用饵料宝藏加成
        if (session.baitId() != null) {
            BaitDefinition bait = configuration.baits().stream()
                .filter(b -> b.id().equals(session.baitId()))
                .findFirst()
                .orElse(null);
            if (bait != null) {
                treasureChance += bait.treasureChanceBoost();
            }
        }
        // 应用钓竿宝藏加成
        RodDefinition rod = detectRod(player);
        treasureChance += rod.treasureChanceBonus();
        treasureChance = Math.min(1.0, Math.max(0.0, treasureChance));
        if (water != null && ThreadLocalRandom.current().nextDouble() < treasureChance) {
            TreasureDefinition treasure = selectTreasureFromPool(water);
            if (treasure != null) {
                giveTreasureItem(player, treasure);
                gotTreasure = true;
            }
        }

        // ─── 货币奖励 ────────────────────────────────────────────
        if (fish.currencyReward() != null) {
            CurrencyBridgeAPI currencyApi = currencyProvider != null ? currencyProvider.get() : null;
            if (currencyApi != null) {
                var reward = fish.currencyReward();
                double amount = perfect ? reward.amount() * configuration.fishing().perfectBonusMultiplier() : reward.amount();
                var bridge = currencyApi.bridge(reward.currencyId());
                if (bridge != null && bridge.available()) {
                    bridge.deposit(player, BigDecimal.valueOf(amount));
                }
            }
        }

        // ─── 跨模块事件发射 ──────────────────────────────────────
        emitFishingEvent(player, fish, size, water, session.baitId(), perfect, isFirstCatch, gotTreasure, updatedData);

        // ─── 里程碑检测 ──────────────────────────────────────────
        checkMilestones(player, updatedData, fish, isFirstCatch);

        // 发送消息
        String quality = perfect ? "完美" : "普通";
        String successMsg = getMessage("fishing_success", ChatColor.translateAlternateColorCodes('&', fish.displayName()), String.valueOf(size), quality, String.valueOf(xp));
        if (successMsg != null) {
            player.sendMessage(successMsg);
        }

        if (isFirstCatch) {
            String firstMsg = getMessage("fishing_first_catch", ChatColor.translateAlternateColorCodes('&', fish.displayName()));
            if (firstMsg != null) {
                player.sendMessage(firstMsg);
            }
        }

        if (gotTreasure) {
            String treasureMsg = getMessage("fishing_treasure_found", "??");
            if (treasureMsg != null) {
                player.sendMessage(treasureMsg);
            }
        }

        if (updatedData.level() > playerData.level()) {
            String levelMsg = getMessage("fishing_level_up", String.valueOf(updatedData.level()));
            if (levelMsg != null) {
                player.sendMessage(levelMsg);
            }
        }
    }

    // ─── 水域与池方法 ────────────────────────────────────────────────

    private @Nullable WaterArea resolveWater(@NotNull Player player) {
        for (WaterArea area : configuration.specifiedWaters()) {
            if (area.contains(player.getLocation())) {
                return area;
            }
        }
        return null;
    }

    private @NotNull List<FishDefinition> getFishPool(@NotNull WaterArea water) {
        List<String> ids = configuration.fishPools().get(water.fishPool());
        if (ids == null || ids.isEmpty()) return List.of();
        return configuration.fishes().stream()
            .filter(f -> ids.contains(f.id()))
            .toList();
    }

    private @Nullable FishDefinition selectFishFromPool(@NotNull Player player,
                                                           @NotNull List<FishDefinition> pool,
                                                           @Nullable String baitId) {
        if (pool.isEmpty()) return null;

        String currentSeason = getCurrentSeason();
        String currentWeather = getCurrentWeather(player.getWorld());
        String waterType = detectWaterType(player);
        String currentTime = getCurrentTimeString(player.getWorld());

        List<FishDefinition> candidates = pool.stream()
            .filter(f -> f.seasons().contains(currentSeason))
            .filter(f -> f.weathers().contains(currentWeather))
            .filter(f -> f.waterTypes().contains(waterType))
            .filter(f -> isInTimeRange(currentTime, f.timeRanges()))
            .toList();

        if (candidates.isEmpty()) {
            candidates = pool.stream()
                .filter(f -> f.seasons().contains(currentSeason))
                .toList();
        }
        if (candidates.isEmpty()) {
            candidates = pool;
        }

        // 应用饵料吸引权重
        double totalWeight = 0;
        Map<FishDefinition, Double> weights = new java.util.LinkedHashMap<>();
        for (FishDefinition fish : candidates) {
            double weight = fish.rarity().weightMultiplier();
            if (baitId != null) {
                BaitDefinition bait = configuration.baits().stream()
                    .filter(b -> b.id().equals(baitId))
                    .findFirst()
                    .orElse(null);
                if (bait != null) {
                    weight *= bait.attractModifier(fish.id());
                }
            }
            weights.put(fish, weight);
            totalWeight += weight;
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

    private @Nullable TreasureDefinition selectTreasureFromPool(@NotNull WaterArea water) {
        List<String> ids = configuration.treasurePools().get(water.treasurePool());
        if (ids == null || ids.isEmpty()) return null;
        List<TreasureDefinition> pool = configuration.treasures().stream()
            .filter(t -> ids.contains(t.id()))
            .toList();
        if (pool.isEmpty()) return null;
        double roll = ThreadLocalRandom.current().nextDouble();
        for (TreasureDefinition t : pool) {
            if (roll < t.chance()) return t;
        }
        return null;
    }

    private @Nullable BaitDefinition detectBait(@NotNull Player player) {
        ItemStack rod = player.getInventory().getItemInMainHand();
        if (rod.getType() != Material.FISHING_ROD) {
            rod = player.getInventory().getItemInOffHand();
            if (rod.getType() != Material.FISHING_ROD) return null;
        }

        // 通过 PDC 标签检测饵料 ID
        String baitId = getBaitIdFromRod(rod);
        if (baitId != null) {
            return configuration.baits().stream()
                .filter(b -> b.id().equals(baitId))
                .findFirst()
                .orElse(null);
        }

        // 返回默认饵料
        return configuration.baits().stream()
            .filter(BaitDefinition::isDefault)
            .findFirst()
            .orElse(null);
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
     */
    public @NotNull RodDefinition detectRod(@NotNull Player player) {
        ItemStack rod = player.getInventory().getItemInMainHand();
        if (rod.getType() != Material.FISHING_ROD) {
            rod = player.getInventory().getItemInOffHand();
            if (rod.getType() != Material.FISHING_ROD) return RodDefinition.DEFAULT;
        }
        String rodId = getRodIdFromRod(rod);
        if (rodId == null) return RodDefinition.DEFAULT;
        return configuration.rods().stream()
            .filter(r -> r.id().equals(rodId))
            .findFirst()
            .orElse(RodDefinition.DEFAULT);
    }

    private void giveFishItem(@NotNull Player player, @NotNull FishDefinition fish, int size, boolean perfect) {
        try {
            ItemStack stack = itemGenerator.generate(fish.itemRef(), player);
            if (stack == null) {
                stack = new ItemStack(Material.COD);
            }
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                // 设置显示名称和 lore
                meta.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes('&',
                    "&7[" + fish.rarity().name() + "] &f" + fish.displayName()));
                List<String> lore = new ArrayList<>();
                lore.add(org.bukkit.ChatColor.translateAlternateColorCodes('&',
                    "&7尺寸: &f" + size + "cm"));
                lore.add(org.bukkit.ChatColor.translateAlternateColorCodes('&',
                    "&7售价: &6" + fish.calculatePrice(size) + " &7" + getDefaultCurrency()));
                if (perfect) {
                    lore.add(org.bukkit.ChatColor.translateAlternateColorCodes('&',
                        "&a✦ 完美捕获"));
                }
                meta.setLore(lore);

                // PDC 标记，供 Market 等模块识别
                PersistentDataContainer pdc = meta.getPersistentDataContainer();
                NamespacedKey keyId = new NamespacedKey(plugin, PDC_FISH_ID);
                NamespacedKey keySize = new NamespacedKey(plugin, PDC_FISH_SIZE);
                NamespacedKey keyPerfect = new NamespacedKey(plugin, PDC_IS_PERFECT);
                pdc.set(keyId, PersistentDataType.STRING, fish.id());
                pdc.set(keySize, PersistentDataType.INTEGER, size);
                pdc.set(keyPerfect, PersistentDataType.BYTE, (byte) (perfect ? 1 : 0));

                stack.setItemMeta(meta);
            }
            dropOrGive(player, stack);
        } catch (Exception e) {
            logger.warning("给予鱼物品失败: " + e.getMessage());
        }
    }

    private @NotNull String getDefaultCurrency() {
        CurrencyBridgeAPI api = currencyProvider != null ? currencyProvider.get() : null;
        if (api != null && !api.currencyIds().isEmpty()) {
            return api.currencyIds().iterator().next();
        }
        return "coin";
    }

    private void giveTreasureItem(@NotNull Player player, @NotNull TreasureDefinition treasure) {
        try {
            ItemStack stack = itemGenerator.generate(treasure.itemRef(), player);
            if (stack == null) return;
            dropOrGive(player, stack);
        } catch (Exception e) {
            logger.warning("给予宝箱物品失败: " + e.getMessage());
        }
    }

    private void dropOrGive(@NotNull Player player, @NotNull ItemStack item) {
        if (player.getInventory().firstEmpty() != -1) {
            player.getInventory().addItem(item);
        } else {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
            String msg = getMessage("fishing_inventory_full");
            if (msg != null) {
                player.sendMessage(msg);
            }
        }
    }

    // ─── 饵料装载 ────────────────────────────────────────────────

    /**
     * 将玩家主手中的饵料装载到鱼竿上。
     * 饵料物品会被消耗，鱼竿会写入 PDC 标签。
     *
     * @return 装载成功返回对应 BaitDefinition，失败返回 null
     */
    public @Nullable BaitDefinition attachBait(@NotNull Player player) {
        ItemStack rod = player.getInventory().getItemInMainHand();
        if (rod.getType() != Material.FISHING_ROD) {
            rod = player.getInventory().getItemInOffHand();
            if (rod.getType() != Material.FISHING_ROD) return null;
        }

        // 查找玩家背包中的饵料物品
        for (BaitDefinition bait : configuration.baits()) {
            ItemStack baitItem = itemGenerator.generate(bait.itemRef(), player);
            if (baitItem == null) continue;
            int slot = findBaitInInventory(player, baitItem.getType());
            if (slot < 0) continue;

            // 消耗饵料
            ItemStack invItem = player.getInventory().getItem(slot);
            if (invItem != null && invItem.getAmount() > 0) {
                invItem.setAmount(invItem.getAmount() - 1);
                player.getInventory().setItem(slot, invItem.getAmount() == 0 ? null : invItem);
            }

            // 写入 PDC 标签
            attachBaitToRod(rod, bait.id());
            return bait;
        }
        return null;
    }

    private void attachBaitToRod(@NotNull ItemStack rod, @NotNull String baitId) {
        ItemMeta meta = rod.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(plugin, PDC_BAIT_ID);
        pdc.set(key, PersistentDataType.STRING, baitId);
        rod.setItemMeta(meta);
    }

    /**
     * 获取鱼竿上挂载的饵料的耐久加成值。
     */
    public int getRodDurabilityBonus(@NotNull ItemStack rod) {
        String baitId = getBaitIdFromRod(rod);
        if (baitId == null) return 0;
        return configuration.baits().stream()
            .filter(b -> b.id().equals(baitId))
            .findFirst()
            .map(BaitDefinition::maxDurabilityBonus)
            .orElse(0);
    }

    private int findBaitInInventory(@NotNull Player player, @NotNull Material baitMaterial) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.getType() == baitMaterial) {
                return i;
            }
        }
        return -1;
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

    private String getCurrentWeather(@NotNull World world) {
        return world.hasStorm() || world.isThundering() ? "rain" : "clear";
    }

    private String detectWaterType(@NotNull Player player) {
        Biome biome = player.getWorld().getBiome(player.getLocation().getBlockX(), player.getLocation().getBlockY(), player.getLocation().getBlockZ());
        String biomeName = biome.name().toLowerCase();
        if (biomeName.contains("ocean") || biomeName.contains("beach") || biomeName.contains("deep")) {
            return "ocean";
        }
        if (biomeName.contains("river")) {
            return "river";
        }
        // 大面积水
        if (isLargeWaterBody(player)) {
            return "lake";
        }
        return "pond";
    }

    private boolean isLargeWaterBody(@NotNull Player player) {
        return false; // 简化实现，后续可通过扫描附近水域扩展
    }

    private String getCurrentTimeString(@NotNull World world) {
        long ticks = world.getTime();
        int hour = (int) ((ticks / 1000 + 6) % 24);
        int minute = (int) ((ticks % 1000) * 60 / 1000);
        return String.format("%02d:%02d", hour, minute);
    }

    private boolean isInTimeRange(@NotNull String time, @NotNull List<FishDefinition.TimeRange> ranges) {
        for (FishDefinition.TimeRange range : ranges) {
            if (isBetweenTimes(time, range.start(), range.end())) {
                return true;
            }
        }
        return false;
    }

    private boolean isBetweenTimes(@NotNull String current, @NotNull String start, @NotNull String end) {
        int currentMins = parseTimeToMinutes(current);
        int startMins = parseTimeToMinutes(start);
        int endMins = parseTimeToMinutes(end);
        if (endMins < startMins) {
            // 跨越午夜
            return currentMins >= startMins || currentMins <= endMins;
        }
        return currentMins >= startMins && currentMins <= endMins;
    }

    private int parseTimeToMinutes(@NotNull String time) {
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

    public @NotNull FishingPlayerData getPlayerData(@NotNull UUID uuid) {
        return repository.loadPlayerData(uuid);
    }

    public @NotNull List<FishCollectionEntry> getCollection(@NotNull UUID uuid) {
        return repository.loadCollection(uuid);
    }

    public @Nullable FishCollectionEntry getCollectionEntry(@NotNull UUID uuid, @NotNull String fishId) {
        return repository.loadCollectionEntry(uuid, fishId);
    }

    public void resetPlayerData(@NotNull UUID uuid) {
        FishingPlayerData empty = FishingPlayerData.empty(uuid);
        repository.savePlayerData(empty);
        try {
            for (FishCollectionEntry entry : repository.loadCollection(uuid)) {
                FishCollectionEntry reset = new FishCollectionEntry(uuid, entry.fishId(), 0, 0, entry.firstCatchAt());
                repository.saveCollectionEntry(reset);
            }
        } catch (Exception ignored) {}
    }

    public void giveXp(@NotNull UUID uuid, int amount) {
        FishingPlayerData data = repository.loadPlayerData(uuid);
        FishingPlayerData updated = data.withXpAdded(amount);
        while (updated.canLevelUp(configuration.fishing().baseXpPerLevel())) {
            updated = updated.withLevelUp();
        }
        repository.savePlayerData(updated);
    }

    public int getCollectionCount(@NotNull UUID uuid) {
        return repository.loadCollection(uuid).size();
    }

    public int getTotalFishTypes() {
        return configuration.fishes().size();
    }

    public void pushCollectionData(@NotNull Player player, @NotNull String uiId) {
        if (packetBridge == null || !packetBridge.isAvailable()) return;

        FishingPlayerData data = repository.loadPlayerData(player.getUniqueId());
        List<FishCollectionEntry> collection = repository.loadCollection(player.getUniqueId());

        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("packetId", "AXS_FISHING");
        payload.put("playerLevel", data.level());
        payload.put("totalXp", data.totalXp());
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

            FishCollectionEntry caught = collection.stream()
                .filter(c -> c.fishId().equals(fish.id()))
                .findFirst()
                .orElse(null);

            entry.put("caught", caught != null && caught.caughtCount() > 0);
            entry.put("maxSize", caught != null ? caught.maxSize() : 0);
            entry.put("count", caught != null ? caught.caughtCount() : 0);
            fishDict.put(String.valueOf(idx), entry);
            idx++;
        }
        payload.put("fishList", fishDict);
        payload.put("maxFishCount", configuration.fishes().size());

        packetBridge.sendPacket(player, uiId, "init", payload);
    }

    // ─── 跨模块事件发射 ──────────────────────────────────────────────

    private void emitFishingEvent(@NotNull Player player, @NotNull FishDefinition fish, int size,
                                   @Nullable WaterArea water, @Nullable String baitId, boolean perfect,
                                   boolean isFirstCatch, boolean gotTreasure, @NotNull FishingPlayerData data) {
        // EventBus 发布（供 BattlePass / QuestGPS / OnlineRewards 等订阅）
        EventBusCapability eventBus = eventBusProvider != null ? eventBusProvider.get() : null;
        if (eventBus != null) {
            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("fish_id", fish.id());
            payload.put("fish_rarity", fish.rarity().name());
            payload.put("fish_size", String.valueOf(size));
            payload.put("water_area", water != null ? water.name() : "default");
            payload.put("bait_id", baitId != null ? baitId : "");
            payload.put("is_perfect", String.valueOf(perfect));
            payload.put("player_level", String.valueOf(data.level()));
            eventBus.publish("axs.fishing.success", player, payload);

            if (perfect) {
                eventBus.publish("axs.fishing.perfect", player, payload);
            }
            if (isFirstCatch) {
                int totalCollection = repository.loadCollection(player.getUniqueId()).size();
                payload.put("total_collection_count", String.valueOf(totalCollection));
                eventBus.publish("axs.fishing.collection_unlock", player, payload);
            }
            if (gotTreasure) {
                Map<String, String> treasurePayload = new LinkedHashMap<>();
                treasurePayload.put("water_area", water != null ? water.name() : "default");
                eventBus.publish("axs.fishing.treasure", player, treasurePayload);
            }
        }

        // EventPacket Signal（供 EventPacket 规则引擎匹配）
        SignalDispatchable signal = signalProvider != null ? signalProvider.get() : null;
        if (signal != null) {
            Map<String, String> vars = new LinkedHashMap<>();
            vars.put("fish_id", fish.id());
            vars.put("fish_name", fish.displayName());
            vars.put("fish_rarity", fish.rarity().name());
            vars.put("fish_size", String.valueOf(size));
            vars.put("water_area", water != null ? water.name() : "default");
            vars.put("is_perfect", String.valueOf(perfect));
            signal.dispatchSignal("fishing_success", player, vars);

            if (fish.rarity() == FishRarity.LEGENDARY) {
                signal.dispatchSignal("fishing_legendary_catch", player, vars);
            }
            if (isFirstCatch) {
                signal.dispatchSignal("fishing_first_catch", player, vars);
            }
            if (gotTreasure) {
                signal.dispatchSignal("fishing_treasure", player, vars);
            }
        }
    }

    // ─── 里程碑检测 ─────────────────────────────────────────────────

    private void checkMilestones(@NotNull Player player, @NotNull FishingPlayerData data,
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
                mail.dispatchPreset(presetId, player.getName(), "Fishing");
            } catch (Exception e) {
                logger.warning("发送邮件失败: " + presetId + " — " + e.getMessage());
            }
        }
    }

    // ─── 售卖 ────────────────────────────────────────────────────────

    /**
     * 售卖玩家主手中的鱼。
     */
    public @NotNull String sellFishInHand(@NotNull Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        String fishId = getFishIdFromItem(hand);
        if (fishId == null) {
            return getMessage("sell_not_fish", "") != null ? getMessage("sell_not_fish", "") : "&c手中物品不是钓鱼产物。";
        }

        FishDefinition fish = configuration.fishes().stream()
            .filter(f -> f.id().equals(fishId))
            .findFirst()
            .orElse(null);
        if (fish == null) {
            return "&c未知鱼种，无法出售。";
        }

        int size = getFishSizeFromItem(hand);
        int price = fish.calculatePrice(size);
        String currency = getDefaultCurrency();

        CurrencyBridgeAPI currencyApi = currencyProvider != null ? currencyProvider.get() : null;
        if (currencyApi != null) {
            var bridge = currencyApi.bridge(currency);
            if (bridge != null && bridge.available()) {
                bridge.deposit(player, BigDecimal.valueOf(price));
            }
        }

        hand.setAmount(hand.getAmount() - 1);
        String msg = getMessage("sell_success", fish.displayName(), String.valueOf(price), currency);
        return msg != null ? msg : "&a成功出售 &e" + fish.displayName() + " &a获得 &6" + price + " " + currency;
    }

    /**
     * 售卖背包中所有钓鱼产物。
     */
    public @NotNull String sellAllFish(@NotNull Player player) {
        CurrencyBridgeAPI currencyApi = currencyProvider != null ? currencyProvider.get() : null;
        if (currencyApi == null) {
            return "&c货币系统暂不可用。";
        }

        Map<String, Double> earnings = new LinkedHashMap<>();
        int totalSold = 0;

        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            String fishId = getFishIdFromItem(item);
            if (fishId == null) continue;

            FishDefinition fish = configuration.fishes().stream()
                .filter(f -> f.id().equals(fishId))
                .findFirst()
                .orElse(null);
            if (fish == null) continue;

            int size = getFishSizeFromItem(item);
            int price = fish.calculatePrice(size);
            String currency = fish.currencyReward() != null ? fish.currencyReward().currencyId() : getDefaultCurrency();

            var bridge = currencyApi.bridge(currency);
            if (bridge != null && bridge.available()) {
                bridge.deposit(player, BigDecimal.valueOf(price * item.getAmount()));
                earnings.merge(currency, (double) price * item.getAmount(), Double::sum);
                totalSold += item.getAmount();
            }
            player.getInventory().setItem(i, null);
        }

        if (totalSold == 0) {
            String msg = getMessage("sell_none", "");
            return msg != null ? msg : "&c背包中没有可出售的鱼。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("&a共出售 &e").append(totalSold).append(" &a条鱼，获得 ");
        for (var e : earnings.entrySet()) {
            sb.append("&6").append(String.format("%.0f", e.getValue())).append(" ").append(e.getKey()).append(" ");
        }
        return sb.toString().trim();
    }

    // ─── 物品识别辅助 ────────────────────────────────────────────────

    private @Nullable String getFishIdFromItem(@Nullable ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(plugin, PDC_FISH_ID);
        if (pdc.has(key, PersistentDataType.STRING)) {
            return pdc.get(key, PersistentDataType.STRING);
        }
        return null;
    }

    private int getFishSizeFromItem(@Nullable ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(plugin, PDC_FISH_SIZE);
        if (pdc.has(key, PersistentDataType.INTEGER)) {
            Integer val = pdc.get(key, PersistentDataType.INTEGER);
            return val != null ? val : 0;
        }
        return 0;
    }
}
