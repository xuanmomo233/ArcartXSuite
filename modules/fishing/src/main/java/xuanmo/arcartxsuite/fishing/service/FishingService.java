package xuanmo.arcartxsuite.fishing.service;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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
import xuanmo.arcartxsuite.api.capability.mail.MailPresetRequest;
import xuanmo.arcartxsuite.api.capability.SignalDispatchable;
import xuanmo.arcartxsuite.api.capability.TitleGrantable;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.api.script.XpCurve;
import xuanmo.arcartxsuite.fishing.config.FishingModuleConfiguration;
import xuanmo.arcartxsuite.fishing.minigame.FishingMinigame;
import xuanmo.arcartxsuite.fishing.minigame.FishingSession;
import xuanmo.arcartxsuite.fishing.model.BaitDefinition;
import xuanmo.arcartxsuite.fishing.model.FishCollectionEntry;
import xuanmo.arcartxsuite.fishing.model.FishDefinition;
import xuanmo.arcartxsuite.fishing.model.FishRarity;
import xuanmo.arcartxsuite.fishing.model.FishingPermanentData;
import xuanmo.arcartxsuite.fishing.model.FishingSeasonData;
import xuanmo.arcartxsuite.fishing.model.RodDefinition;
import xuanmo.arcartxsuite.fishing.model.TreasureDefinition;
import xuanmo.arcartxsuite.fishing.model.WaterArea;
import xuanmo.arcartxsuite.fishing.storage.FishingRepository;
import xuanmo.arcartxsuite.fishing.talent.TalentBonuses;
import xuanmo.arcartxsuite.fishing.talent.TalentEngine;
import xuanmo.arcartxsuite.fishing.reward.RewardDispatcher;
import xuanmo.arcartxsuite.fishing.reward.SeasonRewardDefinition;
import xuanmo.arcartxsuite.fishing.settlement.SettlementEngine;
import xuanmo.arcartxsuite.fishing.task.TaskEngine;

public final class FishingService {

    /** 最近捕获记录条目（内存缓存，重启后清空，仅用于主界面展示）。 */
    public record RecentCatch(
        @NotNull String fishId,
        @NotNull String fishName,
        @NotNull String rarity,
        int size,
        boolean perfect,
        long timestamp
    ) {}

    private static final int MAX_RECENT_CATCHES = 10;

    private final JavaPlugin plugin;
    private final FishingModuleConfiguration configuration;
    private final FishingRepository repository;
    private final PacketBridgeAPI packetBridge;
    private final FishingItemGenerator itemGenerator;
    private final Logger logger;
    private final String minigameUiId;
    private final Map<UUID, FishingMinigame> activeMinigames = new HashMap<>();
    private final Map<UUID, Long> fishingCooldowns = new HashMap<>(); // 玩家UUID → 冷却到期时间戳(System.currentTimeMillis)
    private final Map<UUID, Deque<RecentCatch>> recentCatches = new HashMap<>();
    private final TalentEngine talentEngine;

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
                          @NotNull Logger logger, @NotNull String minigameUiId,
                          @NotNull TalentEngine talentEngine,
                          @NotNull List<SeasonRewardDefinition> seasonRewards,
                          @NotNull RewardDispatcher rewardDispatcher,
                          @NotNull SettlementEngine settlementEngine,
                          @NotNull TaskEngine taskEngine,
                          @Nullable Supplier<MailDispatchable> mailProvider) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.repository = repository;
        this.packetBridge = packetBridge;
        this.itemGenerator = itemGenerator;
        this.logger = logger;
        this.minigameUiId = minigameUiId;
        this.talentEngine = talentEngine;
        this.seasonRewards = seasonRewards;
        this.rewardDispatcher = rewardDispatcher;
        this.settlementEngine = settlementEngine;
        this.taskEngine = taskEngine;
        this.mailProvider = mailProvider;
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

    public void setSignalProvider(@Nullable Supplier<SignalDispatchable> provider) {
        this.signalProvider = provider;
    }

    public void setCurrencyProvider(@Nullable Supplier<CurrencyBridgeAPI> provider) {
        this.currencyProvider = provider;
    }

    public void setTitleProvider(@Nullable Supplier<TitleGrantable> provider) {
        this.titleProvider = provider;
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
        fishingCooldowns.clear();
        repository.shutdown();
    }

    public boolean isReplaceVanilla() {
        return configuration.fishing().replaceVanilla();
    }

    public boolean isPlayerInMinigame(@NotNull UUID uuid) {
        return activeMinigames.containsKey(uuid);
    }

    public void removeMinigame(@NotNull UUID uuid) {
        activeMinigames.remove(uuid);
    }

    public xuanmo.arcartxsuite.fishing.minigame.FishingMinigame getActiveMinigame(@NotNull UUID uuid) {
        return activeMinigames.get(uuid);
    }

    public boolean startMinigame(@NotNull Player player) {
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
        WaterArea water = resolveWater(player);
        if (water == null) water = configuration.defaultWater();

        // 权限检查
        if (water.requirePermission() != null && !water.requirePermission().isBlank() && !player.hasPermission(water.requirePermission())) {
            debug("startMinigame 跳过: 权限不足 " + water.requirePermission());
            return false;
        }

        // 检测饵料
        BaitDefinition bait = detectBait(player);
        String baitId = bait != null ? bait.id() : null;

        // 检测钓竿
        RodDefinition rod = detectRod(player);

        // 鱼竿等级限制校验（maxPlayerLevel = 0 表示无上限）
        if (!rod.id().equals(RodDefinition.DEFAULT.id())) {
            int playerLevel = repository.loadSeasonData(player.getUniqueId(),
                configuration.season().seasonId()).level();
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

        // 获取该水域的鱼池
        List<FishDefinition> pool = getFishPool(water);
        if (pool.isEmpty()) {
            // 没有符合条件的鱼，走原版
            logger.warning("startMinigame 失败: 鱼池为空, water=" + water.name() + " pool=" + water.fishPool());
            return false;
        }

        FishDefinition fish = selectFishFromPool(player, pool, baitId);
        if (fish == null) {
            logger.warning("startMinigame 失败: selectFishFromPool 返回 null");
            return false;
        }

        debug("startMinigame: player=" + player.getName() + " water=" + water.name()
            + " fish=" + fish.id() + " difficulty=" + fish.difficulty()
            + " bait=" + (baitId != null ? baitId : "none"));

        // 应用水域难度修改器
        int adjustedDifficulty = (int) (fish.difficulty() * water.difficultyModifier());
        adjustedDifficulty = Math.max(1, Math.min(100, adjustedDifficulty));

        int caughtSize = fish.randomSize();
        FishingSeasonData seasonData = repository.loadSeasonData(player.getUniqueId(), configuration.season().seasonId());

        FishingSession session = new FishingSession(
            player, fish, caughtSize, seasonData.level(),
            configuration.fishing().catchDurationTicks() + rod.catchDurationBonus(), water, adjustedDifficulty,
            baitId, rod.greenBarHeightBonus(), rod.expMultiplier()
        );

        // 计算天赋加成（开局一次性计算）
        TalentBonuses bonuses = new TalentBonuses(talentEngine.computeBonuses(seasonData));

        FishingMinigame minigame = new FishingMinigame(
            plugin, packetBridge, configuration.fishing(), this, itemGenerator, session, minigameUiId, bonuses
        );
        activeMinigames.put(player.getUniqueId(), minigame);
        minigame.start();

        String msg = getMessage("fishing_start");
        if (msg != null && !msg.isEmpty()) {
            player.sendMessage(msg.replace("{water}", water.displayName()));
        }
        return true;
    }

    public void onMinigameComplete(@NotNull FishingSession session, boolean success, boolean perfect) {
        activeMinigames.remove(session.playerUuid());
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
        String seasonId = configuration.season().seasonId();
        FishingSeasonData seasonData = repository.loadSeasonData(player.getUniqueId(), seasonId);
        FishingPermanentData permData = repository.loadPermanentData(player.getUniqueId());

        // 计算经验（应用钓竿经验倍率 + 天赋经验加成）
        TalentBonuses bonuses = new TalentBonuses(talentEngine.computeBonuses(seasonData));
        double xpMultiplier = session.expMultiplier() * (1.0 + bonuses.xpMultiplierBonus());
        if (perfect) xpMultiplier *= (1.0 + bonuses.perfectXpBonus());
        int xp = (int) (fish.calculateXp(size, perfect) * xpMultiplier);
        xp = Math.max(1, xp);
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

        FishingSeasonData updatedSeason = seasonData.withXpAdded(xp,
            configuration.season().xpCurve(), configuration.season().maxLevel());
        FishingPermanentData updatedPerm = permData.withCaught(perfect, gotTreasure);

        repository.saveSeasonData(updatedSeason);
        repository.savePermanentData(updatedPerm);

        // ─── 货币奖励（单个 + 多货币列表） ───────────────────────
        CurrencyBridgeAPI currencyApi = currencyProvider != null ? currencyProvider.get() : null;
        if (fish.currencyReward() != null && currencyApi != null) {
            var reward = fish.currencyReward();
            double amount = perfect ? reward.amount() * configuration.fishing().perfectBonusMultiplier() : reward.amount();
            var bridge = currencyApi.bridge(reward.currencyId());
            if (bridge != null && bridge.available()) {
                bridge.deposit(player, BigDecimal.valueOf(amount));
            }
        }
        if (!fish.currencyRewards().isEmpty() && currencyApi != null) {
            for (FishDefinition.CurrencyReward cr : fish.currencyRewards()) {
                double amount = perfect ? cr.amount() * configuration.fishing().perfectBonusMultiplier() : cr.amount();
                var bridge = currencyApi.bridge(cr.currencyId());
                if (bridge != null && bridge.available()) {
                    bridge.deposit(player, BigDecimal.valueOf(amount));
                }
            }
        }

        // ─── 多物品掉落（条件掉落） ─────────────────────────────
        for (FishDefinition.DropEntry drop : fish.drops()) {
            if (checkDropCondition(drop.condition(), perfect, size, isFirstCatch)) {
                try {
                    ItemStack dropStack = itemGenerator.generate(drop.itemRef(), player);
                    if (dropStack != null) {
                        dropOrGive(player, dropStack);
                    }
                } catch (Exception e) {
                    logger.warning("给予额外掉落物品失败: " + e.getMessage());
                }
            }
        }

        // ─── 命令奖励 ──────────────────────────────────────────
        for (FishDefinition.CommandReward cmd : fish.commandRewards()) {
            if (checkDropCondition(cmd.condition(), perfect, size, isFirstCatch)) {
                String executed = cmd.command()
                    .replace("{player}", player.getName())
                    .replace("{fish}", fish.id())
                    .replace("{size}", String.valueOf(size));
                try {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), executed);
                } catch (Exception e) {
                    logger.warning("执行鱼命令奖励失败: " + executed + " — " + e.getMessage());
                }
            }
        }

        // ─── 记录最近捕获（内存缓存，用于主界面展示） ──────────
        recordRecentCatch(player.getUniqueId(), new RecentCatch(
            fish.id(), fish.displayName(), fish.rarity().name().toLowerCase(), size, perfect, System.currentTimeMillis()
        ));

        // ─── 跨模块事件发射 ──────────────────────────────────────
        emitFishingEvent(player, fish, size, water, session.baitId(), perfect, isFirstCatch, gotTreasure, updatedSeason, updatedPerm);

        // ─── 里程碑检测 ──────────────────────────────────────────
        checkMilestones(player, updatedPerm, fish, isFirstCatch);

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

        if (updatedSeason.level() > seasonData.level()) {
            onLevelUp(player, seasonData.level(), updatedSeason.level());
        }
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
            .filter(f -> f.minPlayerLevel() <= getPlayerSeasonLevel(player))
            .filter(f -> f.requirePermission() == null || player.hasPermission(f.requirePermission()))
            .filter(f -> f.biomes().isEmpty() || f.biomes().contains(getPlayerBiome(player).toLowerCase()))
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
        BaitDefinition bait = baitId != null ? configuration.baits().stream()
            .filter(b -> b.id().equals(baitId))
            .findFirst()
            .orElse(null) : null;
        for (FishDefinition fish : candidates) {
            double weight = fish.rarity().weightMultiplier();
            if (bait != null) {
                weight *= bait.attractModifier(fish.id());
                // 鱼的饵料偏好覆盖
                for (FishDefinition.BaitPreference pref : fish.baitPreferences()) {
                    if (pref.baitId().equals(bait.id())) {
                        weight *= pref.multiplier();
                        break;
                    }
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

    private int getPlayerSeasonLevel(@NotNull Player player) {
        FishingSeasonData data = repository.loadSeasonData(player.getUniqueId(), configuration.season().seasonId());
        return data != null ? data.level() : 0;
    }

    private String getPlayerBiome(@NotNull Player player) {
        try {
            return player.getLocation().getBlock().getBiome().name();
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
            if (cond.startsWith("size:") && size < Integer.parseInt(cond.substring(5))) return false;
        }
        return true;
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

    public @NotNull FishingSeasonData getSeasonData(@NotNull UUID uuid) {
        return repository.loadSeasonData(uuid, configuration.season().seasonId());
    }

    public @NotNull FishingPermanentData getPermanentData(@NotNull UUID uuid) {
        return repository.loadPermanentData(uuid);
    }

    /** @deprecated 使用 {@link #getSeasonData(UUID)} 代替 */
    @Deprecated
    public @NotNull FishingSeasonData getPlayerData(@NotNull UUID uuid) {
        return getSeasonData(uuid);
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
        FishingSeasonData data = repository.loadSeasonData(uuid, configuration.season().seasonId());
        int oldLevel = data.level();
        FishingSeasonData updated = data.withXpAdded(amount,
            configuration.season().xpCurve(), configuration.season().maxLevel());
        repository.saveSeasonData(updated);
        // 升级回调
        if (updated.level() > oldLevel) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null && player.isOnline()) {
                onLevelUp(player, oldLevel, updated.level());
            }
        }
    }

    // ─── 天赋树操作 ────────────────────────────────────────────

    public @NotNull TalentEngine getTalentEngine() {
        return talentEngine;
    }

    /**
     * 点亮一个天赋节点。
     *
     * @param uuid   玩家 UUID
     * @param nodeId 节点 ID
     * @return {@code true} 表示点亮成功
     */
    public boolean unlockTalent(@NotNull UUID uuid, @NotNull String nodeId) {
        FishingSeasonData data = repository.loadSeasonData(uuid, configuration.season().seasonId());
        if (!talentEngine.canUnlock(data, nodeId)) return false;
        FishingSeasonData updated = talentEngine.unlock(data, nodeId);
        repository.saveSeasonData(updated);
        return true;
    }

    /**
     * 重置玩家天赋树（退还所有天赋点）。
     *
     * @param uuid 玩家 UUID
     */
    public void resetTalents(@NotNull UUID uuid) {
        FishingSeasonData data = repository.loadSeasonData(uuid, configuration.season().seasonId());
        FishingSeasonData updated = talentEngine.reset(data);
        repository.saveSeasonData(updated);
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
     * @return {@code true} 表示领取成功
     */
    public boolean claimReward(@NotNull Player player, @NotNull String rewardId) {
        SeasonRewardDefinition reward = seasonRewards.stream()
            .filter(r -> r.id().equals(rewardId))
            .findFirst().orElse(null);
        if (reward == null) return false;

        FishingSeasonData data = repository.loadSeasonData(player.getUniqueId(), configuration.season().seasonId());
        if (data.level() < reward.level()) return false;
        if (data.claimedRewards().contains(rewardId)) return false;

        FishingSeasonData updated = data.withRewardClaimed(rewardId);
        repository.saveSeasonData(updated);

        rewardDispatcher.dispatch(player, player.getUniqueId(), reward, "等级奖励 Lv." + reward.level());
        return true;
    }

    /**
     * 获取玩家可领取但尚未领取的奖励列表。
     *
     * @param uuid 玩家 UUID
     * @return 可领取奖励列表
     */
    public @NotNull List<SeasonRewardDefinition> getClaimableRewards(@NotNull UUID uuid) {
        FishingSeasonData data = repository.loadSeasonData(uuid, configuration.season().seasonId());
        return seasonRewards.stream()
            .filter(r -> data.level() >= r.level() && !data.claimedRewards().contains(r.id()))
            .toList();
    }

    // ─── 赛季结算 ──────────────────────────────────────────────

    /**
     * 触发赛季结算（对所有在线玩家）。
     */
    public void triggerSettlement() {
        settlementEngine.settleAllOnline();
        logger.info("钓鱼赛季结算已触发: " + configuration.season().seasonId());
    }

    /**
     * 对单个玩家执行赛季结算。
     *
     * @param uuid 玩家 UUID
     */
    public void settlePlayer(@NotNull UUID uuid) {
        settlementEngine.settlePlayer(uuid);
    }

    /**
     * 玩家上线时补发待发放奖励。
     *
     * @param player 上线玩家
     */
    public void onPlayerJoin(@NotNull Player player) {
        try {
            rewardDispatcher.dispatchPending(player);
        } catch (Exception e) {
            logger.warning("补发待发放奖励失败 [" + player.getName() + "]: " + e.getMessage());
        }
        try {
            taskEngine.onPlayerJoin(player);
        } catch (Exception e) {
            logger.warning("分配任务失败 [" + player.getName() + "]: " + e.getMessage());
        }
    }

    // ─── 任务查询 ──────────────────────────────────────────────

    public @NotNull TaskEngine getTaskEngine() {
        return taskEngine;
    }

    public int getCollectionCount(@NotNull UUID uuid) {
        return repository.loadCollection(uuid).size();
    }

    public int getTotalFishTypes() {
        return configuration.fishes().size();
    }

    public @NotNull String getFavoriteFishDisplayName(@NotNull UUID uuid) {
        Map<String, FishCollectionEntry> entries = new HashMap<>();
        for (FishCollectionEntry entry : repository.loadCollection(uuid)) {
            entries.put(entry.fishId(), entry);
        }
        FishDefinition favoriteFish = null;
        int favoriteCount = 0;
        for (FishDefinition fish : configuration.fishes()) {
            FishCollectionEntry entry = entries.get(fish.id());
            if (entry != null && entry.caughtCount() > favoriteCount) {
                favoriteFish = fish;
                favoriteCount = entry.caughtCount();
            }
        }
        return favoriteFish != null ? favoriteFish.displayName() : "";
    }

    /** 记录最近捕获到内存缓存（保留最近 MAX_RECENT_CATCHES 条）。 */
    private void recordRecentCatch(@NotNull UUID uuid, @NotNull RecentCatch catchEntry) {
        Deque<RecentCatch> deque = recentCatches.computeIfAbsent(uuid, k -> new ArrayDeque<>());
        deque.addFirst(catchEntry);
        while (deque.size() > MAX_RECENT_CATCHES) {
            deque.removeLast();
        }
    }

    /** 获取玩家最近捕获记录列表（最新在前）。 */
    @NotNull
    public List<RecentCatch> getRecentCatches(@NotNull UUID uuid) {
        Deque<RecentCatch> deque = recentCatches.get(uuid);
        return deque != null ? new ArrayList<>(deque) : List.of();
    }

    /** 打开指定 UI（供 PacketHandler 导航跳转使用）。 */
    public void openUi(@NotNull Player player, @NotNull String uiId) {
        if (packetBridge == null || !packetBridge.isAvailable()) return;
        packetBridge.openUi(player, uiId);
    }

    /**
     * 推送主界面总览数据到 UI。
     * <p>
     * 包含：等级/经验、图鉴完成度、捕获统计、天赋点、可领取奖励数、最近捕获记录流。
     */
    public void pushMainData(@NotNull Player player, @NotNull String uiId) {
        if (packetBridge == null || !packetBridge.isAvailable()) return;

        FishingSeasonData seasonData = repository.loadSeasonData(player.getUniqueId(), configuration.season().seasonId());
        FishingPermanentData permData = repository.loadPermanentData(player.getUniqueId());
        List<FishCollectionEntry> collection = repository.loadCollection(player.getUniqueId());
        List<RecentCatch> recent = getRecentCatches(player.getUniqueId());

        // 统计可领取奖励数
        int claimableRewardCount = 0;
        for (SeasonRewardDefinition reward : seasonRewards) {
            if (seasonData.level() >= reward.level() && !seasonData.claimedRewards().contains(reward.id())) {
                claimableRewardCount++;
            }
        }

        // 当前级升级所需经验与进度比
        XpCurve xpCurve = configuration.season().xpCurve();
        int xpForNext = seasonData.xpForNextLevel(xpCurve);
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
        payload.put("collectionCount", collection.size());
        payload.put("totalFishTypes", configuration.fishes().size());
        int collectionPercent = configuration.fishes().isEmpty() ? 0 : (collection.size() * 100) / configuration.fishes().size();
        payload.put("collectionPercent", collectionPercent);
        payload.put("unspentTalentPoints", seasonData.unspentTalentPoints());
        payload.put("totalTalentPoints", seasonData.talentPoints());
        payload.put("unlockedTalentCount", seasonData.talents().size());
        payload.put("claimableRewardCount", claimableRewardCount);
        payload.put("claimedRewardCount", seasonData.claimedRewards().size());
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
            FishDefinition fishDef = configuration.fishes().stream()
                .filter(f -> f.id().equals(catchEntry.fishId()))
                .findFirst().orElse(null);
            if (fishDef != null) {
                payload.put("recentIconJson" + idx, itemGenerator.generateItemJson(fishDef.itemRef(), player));
            }
            idx++;
        }
        payload.put("recentCatches", recentMap);
        payload.put("recentCount", recent.size());

        packetBridge.sendPacket(player, uiId, "init", payload);
    }

    public void pushCollectionData(@NotNull Player player, @NotNull String uiId) {
        if (packetBridge == null || !packetBridge.isAvailable()) return;

        FishingSeasonData seasonData = repository.loadSeasonData(player.getUniqueId(), configuration.season().seasonId());
        FishingPermanentData permData = repository.loadPermanentData(player.getUniqueId());
        List<FishCollectionEntry> collection = repository.loadCollection(player.getUniqueId());

        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("packetId", "AXS_FISHING");
        payload.put("playerLevel", seasonData.level());
        payload.put("currentXp", seasonData.currentXp());
        payload.put("totalCaught", permData.totalCaught());
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
            fishDict.put(Integer.toString(idx), entry);
            // 鱼物品图标 itemJson（分离模式，UI 端 Slot Icon 通过 fishIconJson{idx} 渲染）
            payload.put("fishIconJson" + idx, itemGenerator.generateItemJson(fish.itemRef(), player));
            idx++;
        }
        payload.put("fishList", fishDict);
        payload.put("maxFishCount", configuration.fishes().size());

        packetBridge.sendPacket(player, uiId, "init", payload);
    }

    // ─── 跨模块事件发射 ──────────────────────────────────────────────

    private void emitFishingEvent(@NotNull Player player, @NotNull FishDefinition fish, int size,
                                   @Nullable WaterArea water, @Nullable String baitId, boolean perfect,
                                   boolean isFirstCatch, boolean gotTreasure,
                                   @NotNull FishingSeasonData seasonData, @NotNull FishingPermanentData permData) {
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
            payload.put("player_level", String.valueOf(seasonData.level()));
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
                player.getInventory().setItem(i, null);
            }
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

    // ─── 天赋/奖励/任务 UI 数据推送 ──────────────────────────

    /**
     * 推送天赋树数据到 UI。
     */
    public void pushTalentsData(@NotNull Player player, @NotNull String uiId) {
        if (packetBridge == null || !packetBridge.isAvailable()) return;
        FishingSeasonData seasonData = repository.loadSeasonData(player.getUniqueId(), configuration.season().seasonId());

        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("packetId", "AXS_FISHING_TALENTS");
        payload.put("seasonId", seasonData.seasonId());
        payload.put("level", seasonData.level());
        payload.put("talentPoints", seasonData.talentPoints());
        payload.put("unspentTalentPoints", seasonData.unspentTalentPoints());
        payload.put("unlockedTalents", new java.util.ArrayList<>(seasonData.talents()));

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
            treeMap.put("iconJson", buildTalentIconJson(tree.icon()));
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
                nodeMap.put("unlocked", seasonData.talents().contains(node.id()));
                nodeMap.put("canUnlock", talentEngine.canUnlock(seasonData, node.id()));
                nodeMap.put("iconJson", buildTalentNodeIconJson(node, seasonData.talents().contains(node.id()), talentEngine.canUnlock(seasonData, node.id())));
                // 效果描述
                java.util.List<String> effects = new java.util.ArrayList<>();
                for (xuanmo.arcartxsuite.fishing.talent.TalentEffect effect : node.effects()) {
                    effects.add(effect.effectType() + ": " + effect.effectValue());
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
            linkMap.put("unlocked", seasonData.talents().contains(link.id()));
            linkMap.put("canUnlock", talentEngine.canUnlock(seasonData, link.id()));
            linkMap.put("iconJson", buildTalentNodeIconJson(link, seasonData.talents().contains(link.id()), talentEngine.canUnlock(seasonData, link.id())));
            java.util.List<String> linkEffects = new java.util.ArrayList<>();
            for (xuanmo.arcartxsuite.fishing.talent.TalentEffect effect : link.effects()) {
                linkEffects.add(effect.effectType() + ": " + effect.effectValue());
            }
            linkMap.put("effects", linkEffects);
            linkMap.put("prerequisites", new java.util.ArrayList<>(link.prerequisites()));
            java.util.List<Boolean> prerequisiteStates = new java.util.ArrayList<>();
            for (String prerequisite : link.prerequisites()) {
                prerequisiteStates.add(seasonData.talents().contains(prerequisite));
            }
            linkMap.put("prerequisiteStates", prerequisiteStates);
            linksMap.put(Integer.toString(linkIdx), linkMap);
            linkIdx++;
        }
        payload.put("links", linksMap);
        payload.put("linkCount", linkIdx);

        packetBridge.sendPacket(player, uiId, "init", payload);
    }

    /**
     * 推送赛季奖励数据到 UI。
     */
    public void pushRewardsData(@NotNull Player player, @NotNull String uiId) {
        if (packetBridge == null || !packetBridge.isAvailable()) return;
        FishingSeasonData seasonData = repository.loadSeasonData(player.getUniqueId(), configuration.season().seasonId());

        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("packetId", "AXS_FISHING_REWARDS");
        payload.put("level", seasonData.level());
        payload.put("claimedRewards", new java.util.ArrayList<>(seasonData.claimedRewards()));

        // 奖励列表
        java.util.Map<String, Object> rewardsMap = new java.util.LinkedHashMap<>();
        int idx = 0;
        for (SeasonRewardDefinition reward : seasonRewards) {
            java.util.Map<String, Object> entry = new java.util.LinkedHashMap<>();
            entry.put("id", reward.id());
            entry.put("level", reward.level());
            entry.put("type", reward.type());
            entry.put("iconName", reward.iconName());
            entry.put("iconMaterial", reward.iconMaterial());
            entry.put("iconAmount", reward.iconAmount());
            entry.put("claimable", seasonData.level() >= reward.level() && !seasonData.claimedRewards().contains(reward.id()));
            entry.put("claimed", seasonData.claimedRewards().contains(reward.id()));
            rewardsMap.put(Integer.toString(idx), entry);
            // 图标 itemJson：Java 端构建 ItemStack 后序列化，避免 UI 端拼 SNBT 出错
            // （材质名大小写、Count 浮点等客户端拼接问题）
            payload.put("iconJson" + idx, buildRewardIconJson(reward));
            idx++;
        }
        payload.put("rewards", rewardsMap);
        payload.put("rewardCount", seasonRewards.size());

        packetBridge.sendPacket(player, uiId, "init", payload);
    }

    /**
     * 根据奖励定义的图标字段（iconMaterial/iconName/iconAmount）构建 itemJson 字符串。
     * <p>
     * Java 端生成完整 itemJson，避免 UI 端拼接 SNBT 时出现材质名大小写、
     * Count 浮点等客户端解析问题。
     *
     * @param reward 奖励定义
     * @return itemJson 字符串，生成失败时返回空字符串（UI 端回退到 air 图标）
     */
    private @NotNull String buildRewardIconJson(@NotNull SeasonRewardDefinition reward) {
        Material material = Material.matchMaterial(reward.iconMaterial(), true);
        if (material == null || material.isAir()) {
            return "";
        }
        ItemStack stack = new ItemStack(material, Math.max(1, reward.iconAmount()));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && !reward.iconName().isBlank()) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', reward.iconName()));
            stack.setItemMeta(meta);
        }
        return itemGenerator.itemToJson(stack);
    }

    /**
     * 根据天赋树图标材质构建 itemJson 字符串（UI 端 Slot Icon 渲染用）。
     *
     * @param iconMaterial 材质 ID（如 minecraft:iron_bars）
     * @return itemJson 字符串，生成失败时返回空字符串
     */
    private @NotNull String buildTalentIconJson(@NotNull String iconMaterial) {
        String materialName = iconMaterial.startsWith("minecraft:") ? iconMaterial.substring("minecraft:".length()) : iconMaterial;
        Material material = Material.matchMaterial(materialName, true);
        if (material == null || material.isAir()) {
            return "";
        }
        ItemStack stack = new ItemStack(material, 1);
        return itemGenerator.itemToJson(stack);
    }

    private @NotNull String buildTalentNodeIconJson(@NotNull xuanmo.arcartxsuite.fishing.talent.TalentNode node, boolean unlocked, boolean canUnlock) {
        String effectType = node.effects().isEmpty() ? "" : node.effects().get(0).effectType();
        String material = switch (effectType) {
            case "bar_height" -> "minecraft:iron_ingot";
            case "bar_damping" -> "minecraft:slime_ball";
            case "bar_bounce" -> "minecraft:rabbit_foot";
            case "bar_force" -> "minecraft:redstone";
            case "fish_speed" -> "minecraft:feather";
            case "fish_dwell", "flee_cooldown" -> "minecraft:sugar";
            case "fish_dart" -> "minecraft:arrow";
            case "fish_noise" -> "minecraft:amethyst_shard";
            case "fish_edge" -> "minecraft:shield";
            case "flee_chance" -> "minecraft:iron_sword";
            case "flee_speed" -> "minecraft:arrow";
            case "flee_duration" -> "minecraft:string";
            case "progress_rate" -> "minecraft:emerald";
            case "progress_drain" -> "minecraft:redstone";
            case "perfect_window" -> "minecraft:ender_eye";
            case "start_progress" -> "minecraft:firework_rocket";
            case "xp_multiplier", "xp_in_bar" -> "minecraft:diamond";
            case "price_multiplier" -> "minecraft:gold_ingot";
            case "legendary_chance" -> "minecraft:nether_star";
            case "treasure_chance" -> "minecraft:chest";
            case "rare_chance" -> "minecraft:emerald";
            case "perfect_xp" -> "minecraft:diamond";
            default -> node.isLink() ? "minecraft:heart_of_the_sea" : "minecraft:book";
        };
        String materialName = material.startsWith("minecraft:") ? material.substring("minecraft:".length()) : material;
        Material mat = Material.matchMaterial(materialName, true);
        if (mat == null || mat.isAir()) {
            return "";
        }
        ItemStack stack = new ItemStack(mat, 1);
        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add(net.md_5.bungee.api.ChatColor.of("#B0B8C0") + ChatColor.stripColor(node.description()));
        if (!node.effects().isEmpty()) {
            lore.add("");
            for (xuanmo.arcartxsuite.fishing.talent.TalentEffect effect : node.effects()) {
                lore.add(net.md_5.bungee.api.ChatColor.of("#5BC0EB") + effect.effectType() + ": " + effect.effectValue());
            }
        }
        lore.add("");
        if (unlocked) {
            lore.add(net.md_5.bungee.api.ChatColor.of("#2ECC71") + "✔ 已激活");
        } else if (canUnlock) {
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
        return itemGenerator.itemToJson(stack);
    }

    /**
     * 推送任务数据到 UI。
     */
    public void pushTasksData(@NotNull Player player, @NotNull String uiId) {
        if (packetBridge == null || !packetBridge.isAvailable()) return;
        // 确保玩家任务已分配（模块重载或玩家在线时模块加载会导致缓存为空）
        taskEngine.assignTasks(player.getUniqueId());
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

        packetBridge.sendPacket(player, uiId, "init", payload);
    }

    /**
     * 处理客户端 UI 操作请求（点亮天赋/领取奖励等）。
     *
     * @param player 玩家
     * @param action 操作类型
     * @param data   操作数据
     */
    public void handleUiAction(@NotNull Player player, @NotNull String action, @NotNull java.util.List<String> data) {
        switch (action.toLowerCase()) {
            case "unlock_talent" -> {
                if (data.size() > 1) {
                    String nodeId = data.get(1);
                    if (unlockTalent(player.getUniqueId(), nodeId)) {
                        player.sendMessage("§a天赋点亮成功: " + nodeId);
                    } else {
                        player.sendMessage("§c无法点亮天赋: " + nodeId);
                    }
                }
            }
            case "reset_talents" -> {
                resetTalents(player.getUniqueId());
                player.sendMessage("§a天赋树已重置，所有天赋点已退还。");
            }
            case "claim_reward" -> {
                if (data.size() > 1) {
                    String rewardId = data.get(1);
                    if (claimReward(player, rewardId)) {
                        player.sendMessage("§a奖励领取成功！");
                    } else {
                        player.sendMessage("§c无法领取奖励: " + rewardId);
                    }
                }
            }
            case "refresh_talents", "open_talents" -> {
                // UI 请求刷新数据
            }
            case "refresh_rewards", "open_rewards" -> {
                // UI 请求刷新数据
            }
            case "refresh_tasks", "open_tasks" -> {
                // UI 请求刷新数据
            }
        }
    }
}
