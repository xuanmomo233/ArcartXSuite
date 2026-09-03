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
import xuanmo.arcartxsuite.api.currency.CurrencyDefinition;
import xuanmo.arcartxsuite.api.crossserver.CrossServerAPI;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannel;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannelConfig;
import xuanmo.arcartxsuite.api.crossserver.CrossServerDelivery;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.api.script.XpCurve;
import xuanmo.arcartxsuite.fishing.config.FishingModuleConfiguration;
import xuanmo.arcartxsuite.fishing.minigame.FishingMinigame;
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
import xuanmo.arcartxsuite.fishing.talent.TalentBonuses;
import xuanmo.arcartxsuite.fishing.talent.TalentEngine;
import xuanmo.arcartxsuite.fishing.fatigue.FatigueEngine;
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
    private final IconResolver iconResolver;
    private final Logger logger;
    private final String minigameUiId;
    private final Map<UUID, FishingMinigame> activeMinigames = new HashMap<>();
    private final Map<UUID, Long> fishingCooldowns = new HashMap<>(); // 玩家UUID → 冷却到期时间戳(System.currentTimeMillis)
    private final Map<UUID, Deque<RecentCatch>> recentCatches = new HashMap<>();
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
    private MessageProvider messageProvider;

    // 跨模块能力提供者（懒加载，避免循环依赖）
    private Supplier<EventBusCapability> eventBusProvider;
    private Supplier<SignalDispatchable> signalProvider;
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
        this.configuration = configuration;
        this.repository = repository;
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
            fatigueEngine.setRestBoostProvider(uuid -> {
                try {
                    FishingSeasonData data = repository.loadSeasonData(uuid, configuration.season().seasonId());
                    Map<String, Double> bonuses = talentEngine.computeBonuses(data);
                    return bonuses.getOrDefault("rest_boost", 0.0);
                } catch (Exception e) {
                    return 0.0;
                }
            });
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

    public void setSignalProvider(@Nullable Supplier<SignalDispatchable> provider) {
        this.signalProvider = provider;
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

    public void start() throws Exception {
        repository.initialize();
        startCrossServer();
        // M6: 定期清理过期的钓鱼冷却条目（每5分钟清理一次）
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long now = System.currentTimeMillis();
            fishingCooldowns.entrySet().removeIf(e -> e.getValue() < now);
        }, 6000L, 6000L); // 6000 ticks = 5分钟
    }

    public void shutdown() {
        for (FishingMinigame minigame : activeMinigames.values()) {
            try {
                minigame.cleanup();
            } catch (Exception ignored) {}
        }
        activeMinigames.clear();
        fishingCooldowns.clear();
        recentCatches.clear();
        shutdownCrossServer();
        repository.shutdown();
    }

    // ─── 跨服同步 ────────────────────────────────────────────────

    private void startCrossServer() {
        if (crossServer == null) return;
        CrossServerChannelConfig channelConfig = configuration.crossServer();
        if (!channelConfig.enabled()) return;
        crossServerChannel = crossServer.openChannel(
            "fishing",
            channelConfig,
            delivery -> handleCrossServerMessage(delivery.payload())
        );
        if (crossServerChannel.isActive()) {
            logger.info("钓鱼跨服同步已启用");
        }
    }

    private void shutdownCrossServer() {
        if (crossServerChannel != null) {
            crossServerChannel.close();
            crossServerChannel = null;
        }
    }

    /** 跨服通道是否处于活跃状态。 */
    public boolean crossServerActive() {
        return crossServerChannel != null && crossServerChannel.isActive();
    }

    private void publishCrossServer(String message) {
        if (crossServerChannel != null && crossServerChannel.isActive() && message != null && !message.isBlank()) {
            crossServerChannel.publish(message);
        }
    }

    private void handleCrossServerMessage(String message) {
        if (message == null || message.isBlank()) return;
        // CATCH:<uuid>:<fishId>:<fishName>:<rarity>:<size>:<perfect>:<timestamp>
        if (message.startsWith("CATCH:")) {
            String[] parts = message.split(":", -1);
            if (parts.length == 8) {
                try {
                    UUID uuid = UUID.fromString(parts[1]);
                    String fishId = parts[2];
                    String fishName = parts[3];
                    String rarity = parts[4];
                    int size = Integer.parseInt(parts[5]);
                    // M11: 输入验证 — size 非负、timestamp 合理
                    if (size < 0) {
                        logger.warning("跨服捕获记录 size 异常: " + size + " (玩家=" + uuid + ")");
                        return;
                    }
                    boolean perfect = "1".equals(parts[6]);
                    long timestamp = Long.parseLong(parts[7]);
                    if (timestamp < 0 || timestamp > System.currentTimeMillis() + 86_400_000L) {
                        logger.warning("跨服捕获记录 timestamp 异常: " + timestamp + " (玩家=" + uuid + ")");
                        return;
                    }
                    // 持久化到本地数据库
                    repository.saveCatchRecord(new FishingCatchRecord(
                        uuid, fishId, fishName, rarity, size, perfect, timestamp
                    ));
                    // 清除该玩家的内存缓存，下次打开 UI 时从数据库重新加载
                    recentCatches.remove(uuid);
                    debug("跨服同步捕获记录: " + fishName + " (玩家=" + uuid + ")");
                } catch (Exception e) {
                    logger.warning("解析跨服捕获记录失败: " + e.getMessage());
                }
            }
        }
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

        // 一次性加载赛季数据，后续复用（避免重复 DB 查询）
        FishingSeasonData seasonData = repository.loadSeasonData(player.getUniqueId(),
            configuration.season().seasonId());
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
        BaitDefinition bait = detectBait(player);
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

        FishDefinition fish = selectFishFromPool(player, water, pool, baitId);
        if (fish == null) {
            logger.warning("startMinigame 失败: selectFishFromPool 返回 null");
            return false;
        }

        // 疲惫值特殊鱼替换：疲惫值 > 90 时有概率出现特殊鱼
        int fatigue = seasonData.fatigue();
        if (fatigueEngine != null) {
            fish = fatigueEngine.applySpecialFish(fatigue, fish, configuration::findFish);
        }

        debug("startMinigame: player=" + player.getName() + " water=" + water.id()
            + " fish=" + fish.id() + " difficulty=" + fish.difficulty()
            + " bait=" + (baitId != null ? baitId : "none")
            + " fatigue=" + fatigue);

        // 应用水域难度公式（ARIA 求值，变量 global.level = 玩家钓鱼等级）
        double waterDifficultyModifier = evaluateDifficultyFormula(water.difficultyFormula(), playerLevel);
        int adjustedDifficulty = (int) (fish.difficulty() * waterDifficultyModifier);
        adjustedDifficulty = Math.max(1, Math.min(100, adjustedDifficulty));

        int caughtSize = fish.randomSize();

        // 计算天赋加成（开局一次性计算，catch_duration 需要在创建 session 前应用）
        TalentBonuses bonuses = new TalentBonuses(talentEngine.computeBonuses(seasonData));

        // 动态超时：基础超时 × (1 + 难度/100 × 难度系数) + 鱼竿加成 + 天赋超时加成
        // 低难度鱼超时短（要求快速完成），高难度鱼超时长（给更多尝试机会）
        xuanmo.arcartxsuite.fishing.config.FishingModuleConfiguration.FishingSettings cfg = configuration.fishing();
        double difficultyFactor = 1.0 + (adjustedDifficulty / 100.0) * cfg.catchDurationDifficultyScale();
        long durationTicks = (long) (cfg.catchDurationTicks() * difficultyFactor * (1.0 + bonuses.catchDurationBonus())) + rod.catchDurationBonus();

        FishingSession session = new FishingSession(
            player, fish, caughtSize, playerLevel,
            durationTicks, water, adjustedDifficulty,
            baitId, rod.greenBarHeightBonus(), rod.expMultiplier(), rod.treasureChanceBonus()
        );

        // 计算疲惫效率系数（开局一次性计算）
        double fatigueMultiplier = fatigueEngine != null ? fatigueEngine.efficiencyMultiplier(fatigue) : 1.0;
        // 天赋：疲惫效率惩罚缓解 — 将效率系数向 1.0 拉近
        double fatigueEffBonus = bonuses.fatigueEfficiencyBonus();
        if (fatigueEffBonus > 0 && fatigueMultiplier < 1.0) {
            fatigueMultiplier = fatigueMultiplier + (1.0 - fatigueMultiplier) * fatigueEffBonus;
        }

        FishingMinigame minigame = new FishingMinigame(
            plugin, packetBridge, configuration.fishing(), this, iconResolver, session, minigameUiId, bonuses,
            fatigueMultiplier, ariaBridge
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
            BaitDefinition bait = findBait(session.baitId());
            if (bait != null) {
                treasureChance += bait.treasureChanceBoost();
            }
        }
        // 应用钓竿宝藏加成（使用 session 创建时记录的值，避免玩家中途换竿导致不一致）
        treasureChance += session.rodTreasureChanceBonus();
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

        // 疲惫值增加（钓鱼成功后 +fatiguePerCatch），并检查首次达到100奖励
        // C5: FatigueEngine.onFishCaught 仅计算不保存，统一由此处保存，避免重复写入
        // 天赋 fatigue_reduction：降低每次钓鱼增加的疲惫值
        if (fatigueEngine != null) {
            updatedSeason = fatigueEngine.onFishCaught(player, updatedSeason, bonuses.fatigueReduction());
        }
        repository.saveSeasonData(updatedSeason);
        repository.savePermanentData(updatedPerm);

        // ─── 货币奖励（单个 + 多货币列表） ───────────────────────
        CurrencyBridgeAPI currencyApi = currencyApi();
        if (fish.currencyReward() != null && currencyApi != null) {
            try {
                var reward = fish.currencyReward();
                double amount = perfect ? reward.amount() * configuration.fishing().perfectBonusMultiplier() : reward.amount();
                var bridge = currencyApi.bridge(reward.currencyId());
                if (bridge != null && bridge.available()) {
                    bridge.deposit(player, BigDecimal.valueOf(amount));
                }
            } catch (Exception e) {
                logger.warning("给予货币奖励失败: " + e.getMessage());
            }
        }
        if (!fish.currencyRewards().isEmpty() && currencyApi != null) {
            for (FishDefinition.CurrencyReward cr : fish.currencyRewards()) {
                try {
                    double amount = perfect ? cr.amount() * configuration.fishing().perfectBonusMultiplier() : cr.amount();
                    var bridge = currencyApi.bridge(cr.currencyId());
                    if (bridge != null && bridge.available()) {
                        bridge.deposit(player, BigDecimal.valueOf(amount));
                    }
                } catch (Exception e) {
                    logger.warning("给予多货币奖励失败 [currency=" + cr.currencyId() + "]: " + e.getMessage());
                }
            }
        }

        // ─── 多物品掉落（条件掉落） ─────────────────────────────
        for (FishDefinition.DropEntry drop : fish.drops()) {
            if (checkDropCondition(drop.condition(), perfect, size, isFirstCatch)) {
                try {
                    ItemStack dropStack = iconResolver.resolveItemStack(player, drop.itemRef());
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
                    .replace("{player}", xuanmo.arcartxsuite.api.command.CommandSanitizer.validatePlayerName(player.getName()))
                    .replace("{fish}", fish.id())
                    .replace("{size}", String.valueOf(size));
                try {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), executed);
                } catch (Exception e) {
                    logger.warning("执行鱼命令奖励失败: " + executed + " — " + e.getMessage());
                }
            }
        }

        // ─── 记录最近捕获（持久化到数据库，用于主界面展示） ──────
        recordRecentCatch(player.getUniqueId(), new RecentCatch(
            fish.id(), fish.displayName(), fish.rarity().name().toLowerCase(), size, perfect, System.currentTimeMillis()
        ));

        // ─── 跨服同步捕获事件 ────────────────────────────────────
        publishCrossServer("CATCH:" + player.getUniqueId() + ":" + fish.id() + ":"
            + fish.displayName() + ":" + fish.rarity().name().toLowerCase() + ":"
            + size + ":" + (perfect ? 1 : 0) + ":" + System.currentTimeMillis());

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

    /** 按 ID 查找饵料定义（O(1) 索引）。 */
    private @Nullable BaitDefinition findBait(@NotNull String id) {
        return configuration.findBait(id);
    }

    /** 按 ID 查找鱼竿定义（O(1) 索引）。 */
    private @Nullable RodDefinition findRod(@NotNull String id) {
        return configuration.findRod(id);
    }

    /** 按 ID 查找鱼种定义（O(1) 索引）。 */
    private @Nullable FishDefinition findFish(@NotNull String id) {
        return configuration.findFish(id);
    }

    private @Nullable WaterArea resolveWater(@NotNull Player player) {
        for (WaterArea area : configuration.specifiedWaters()) {
            if (area.contains(player.getLocation())) {
                return area;
            }
        }
        return null;
    }

    /**
     * 求值水域难度公式。
     * <p>
     * 使用 ARIA 表达式求值，变量 {@code global.level} 绑定为玩家钓鱼等级。
     * Aria 不可用时回退为固定 1.0 系数。
     *
     * @param formula 难度公式（ARIA 表达式）
     * @param level   玩家钓鱼等级
     * @return 难度修正系数
     */
    private double evaluateDifficultyFormula(@NotNull String formula, int level) {
        if (formula == null || formula.isBlank() || "1.0".equals(formula.trim())) return 1.0;
        if (ariaBridge == null || !ariaBridge.available()) {
            logger.warning("Aria 桥不可用，水域难度公式无法求值，回退为 1.0: " + formula);
            return 1.0;
        }
        try {
            Object result = ariaBridge.eval(formula, Map.of("level", level));
            if (result instanceof Number n) return n.doubleValue();
            return 1.0;
        } catch (Exception e) {
            logger.warning("水域难度公式求值失败，回退为 1.0: " + formula + " - " + e.getMessage());
            return 1.0;
        }
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
                                                           @Nullable String baitId) {
        if (pool.isEmpty()) return null;

        String currentSeason = getCurrentSeason();
        String currentWeather = getCurrentWeather(player.getWorld());
        String currentTime = getCurrentTimeString(player.getWorld());
        // 提前提取，避免在 stream filter 中对每条鱼重复查 DB / 调用 Bukkit API
        int playerLevel = getPlayerSeasonLevel(player);
        String playerBiome = getPlayerBiome(player).toLowerCase();

        List<FishDefinition> candidates = pool.stream()
            .filter(f -> f.seasons().contains(currentSeason))
            .filter(f -> f.weathers().contains(currentWeather))
            .filter(f -> isInTimeRange(currentTime, f.timeRanges()))
            .filter(f -> f.minPlayerLevel() <= playerLevel)
            .filter(f -> f.maxPlayerLevel() <= 0 || playerLevel <= f.maxPlayerLevel())
            .filter(f -> f.requirePermission() == null || player.hasPermission(f.requirePermission()))
            .filter(f -> f.biomes().isEmpty() || f.biomes().contains(playerBiome))
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
        BaitDefinition bait = baitId != null ? findBait(baitId) : null;
        // 水域级饵料倍率
        Map<String, Double> waterBaitMultipliers = water.baitMultipliers();
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
                // 水域级饵料倍率（管理员可针对特定水域配置特定饵料的额外倍率）
                Double waterMul = waterBaitMultipliers.get(bait.id());
                if (waterMul != null) {
                    weight *= waterMul;
                }
            }
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

    private @Nullable TreasureDefinition selectTreasureFromPool(@NotNull WaterArea water) {
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
        double roll = ThreadLocalRandom.current().nextDouble() * totalWeight;
        double cumulative = 0;
        for (TreasureDefinition t : pool) {
            cumulative += t.chance();
            if (roll < cumulative) return t;
        }
        return pool.get(pool.size() - 1);
    }

    /**
     * 检测玩家当前生效的饵料。
     * 从数据库饵料队列头部（slot_index 最小）读取，未配置饵料时返回默认饵料。
     */
    private @Nullable BaitDefinition detectBait(@NotNull Player player) {
        List<BaitQueueEntry> queue = repository.loadBaitQueue(player.getUniqueId());
        if (!queue.isEmpty()) {
            BaitQueueEntry head = queue.get(0);
            BaitDefinition bait = findBait(head.baitId());
            if (bait != null) return bait;
        }
        // 回退：默认饵料
        for (BaitDefinition bait : configuration.baits()) {
            if (bait.isDefault()) return bait;
        }
        return null;
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

    private void giveFishItem(@NotNull Player player, @NotNull FishDefinition fish, int size, boolean perfect) {
        try {
            ItemStack stack = iconResolver.resolveItemStack(player, fish.itemRef());
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

    /** 获取货币桥接 API，未配置时返回 null。 */
    private @Nullable CurrencyBridgeAPI currencyApi() {
        return currencyProvider != null ? currencyProvider.get() : null;
    }

    /** 发送 UI init 数据包，packetBridge 不可用时静默跳过。 */
    private void sendUiInitPacket(@NotNull Player player, @NotNull String uiId,
                                  @NotNull java.util.Map<String, Object> payload) {
        if (packetBridge == null || !packetBridge.isAvailable()) return;
        packetBridge.sendPacket(player, uiId, "init", payload);
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

    private void giveTreasureItem(@NotNull Player player, @NotNull TreasureDefinition treasure) {
        try {
            ItemStack stack = iconResolver.resolveItemStack(player, treasure.itemRef());
            if (stack == null) return;
            dropOrGive(player, stack);
        } catch (Exception e) {
            logger.warning("给予宝箱物品失败: " + e.getMessage());
        }
    }

    private void dropOrGive(@NotNull Player player, @NotNull ItemStack item) {
        rewardDispatcher.dispatchItem(player, item, "fishing-drop");
    }

    // ─── 饵料队列管理 ────────────────────────────────────────────

    /** 获取玩家当前饵料槽数量（固定基础1 + 天赋加成）。 */
    public int getBaitSlots(@NotNull Player player) {
        int base = 1;
        // 天赋加成：bait_slot_bonus effect-type
        FishingSeasonData seasonData = repository.loadSeasonData(player.getUniqueId(), configuration.season().seasonId());
        Map<String, Double> bonuses = talentEngine.computeBonuses(seasonData);
        int bonus = (int) TalentEngine.getBonus(bonuses, "bait_slot_bonus");
        return base + bonus;
    }

    /** 获取玩家饵料槽位的基础数量和天赋加成数量（供 UI 显示来源）。 */
    public int[] getBaitSlotsBreakdown(@NotNull Player player) {
        int base = 1;
        FishingSeasonData seasonData = repository.loadSeasonData(player.getUniqueId(), configuration.season().seasonId());
        Map<String, Double> bonuses = talentEngine.computeBonuses(seasonData);
        int bonus = (int) TalentEngine.getBonus(bonuses, "bait_slot_bonus");
        return new int[]{base, bonus};
    }

    /** 加载玩家饵料队列（按 slot_index 升序）。 */
    public @NotNull List<BaitQueueEntry> loadBaitQueue(@NotNull Player player) {
        return repository.loadBaitQueue(player.getUniqueId());
    }

    /**
     * 将玩家背包指定槽位的饵料物品放入饵料队列的指定槽位。
     * 从背包扣除物品，写入数据库队列。
     *
     * @param player      玩家
     * @param invSlot     背包槽位号
     * @param queueSlot   饵料队列目标槽位号
     * @param amount      放入数量
     * @return 操作结果消息（成功/失败原因）
     */
    public @NotNull String putBait(@NotNull Player player, int invSlot, int queueSlot, int amount) {
        if (itemMatcher == null) return "&c物品匹配系统不可用。";
        if (queueSlot < 0 || queueSlot >= getBaitSlots(player)) return "&c槽位号无效。";

        // ARIA UI ~Backpack 槽位 id → Bukkit inventory slot index 转换
        // ARIA 9-35 → Bukkit 9-35（主背包27格，相同）
        // ARIA 36-44 → Bukkit 0-8（快捷栏9格，差36）
        int bukkitSlot = toBukkitBackpackSlot(invSlot);
        ItemStack item = player.getInventory().getItem(bukkitSlot);
        if (item == null || item.getType().isAir()) return "&c背包槽位为空。";

        // 用 ItemMatcher 识别物品对应的饵料定义
        BaitDefinition matchedBait = null;
        for (BaitDefinition bait : configuration.baits()) {
            if (bait.matcher() == null || bait.matcher().emptyMatcher()) continue;
            if (itemMatcher.matches(bait.matcher(), item)) {
                matchedBait = bait;
                break;
            }
        }
        if (matchedBait == null) return "&c该物品不是饵料。";

        int available = item.getAmount();
        // amount <= 0 表示放入全部数量
        int toTake = amount <= 0 ? available : Math.min(amount, available);
        if (toTake <= 0) return "&c数量无效。";

        // 检查目标槽位是否已有不同饵料
        List<BaitQueueEntry> queue = repository.loadBaitQueue(player.getUniqueId());
        for (BaitQueueEntry entry : queue) {
            if (entry.slotIndex() == queueSlot && !entry.baitId().equals(matchedBait.id())) {
                return "&c槽位 " + (queueSlot + 1) + " 已有其他饵料，请先取出。";
            }
        }

        // 计算新数量（同饵料叠加）
        int newAmount = toTake;
        for (BaitQueueEntry entry : queue) {
            if (entry.slotIndex() == queueSlot && entry.baitId().equals(matchedBait.id())) {
                newAmount = entry.amount() + toTake;
                break;
            }
        }

        // 从背包扣除物品
        item.setAmount(available - toTake);
        player.getInventory().setItem(bukkitSlot, item.getAmount() == 0 ? null : item);

        // 写入数据库
        repository.saveBaitSlot(new BaitQueueEntry(player.getUniqueId(), queueSlot, matchedBait.id(), newAmount));
        return "&a成功放入 &e" + toTake + " &a个 &f" + matchedBait.displayName() + " &a到槽位 &e" + (queueSlot + 1);
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

    /**
     * 交换饵料队列中两个槽位的饵料（用于调整优先级）。
     *
     * @param player  玩家
     * @param slotA   槽位A
     * @param slotB   槽位B
     * @return 操作结果消息
     */
    public @NotNull String swapBaitQueue(@NotNull Player player, int slotA, int slotB) {
        int maxSlots = getBaitSlots(player);
        if (slotA < 0 || slotA >= maxSlots || slotB < 0 || slotB >= maxSlots) return "&c槽位号无效。";
        if (slotA == slotB) return "&c不能与自身交换。";

        List<BaitQueueEntry> queue = repository.loadBaitQueue(player.getUniqueId());
        BaitQueueEntry entryA = null, entryB = null;
        for (BaitQueueEntry entry : queue) {
            if (entry.slotIndex() == slotA) entryA = entry;
            if (entry.slotIndex() == slotB) entryB = entry;
        }

        // 交换：A的位置放B的内容，B的位置放A的内容（amount=0 自动删除）
        if (entryB != null) {
            repository.saveBaitSlot(new BaitQueueEntry(player.getUniqueId(), slotA, entryB.baitId(), entryB.amount()));
        } else {
            repository.saveBaitSlot(new BaitQueueEntry(player.getUniqueId(), slotA, "", 0));
        }
        if (entryA != null) {
            repository.saveBaitSlot(new BaitQueueEntry(player.getUniqueId(), slotB, entryA.baitId(), entryA.amount()));
        } else {
            repository.saveBaitSlot(new BaitQueueEntry(player.getUniqueId(), slotB, "", 0));
        }
        return "&a已交换槽位 &e" + (slotA + 1) + " &a和 &e" + (slotB + 1) + " &a的饵料";
    }

    /**
     * 从饵料队列指定槽位取出饵料返还到玩家背包。
     *
     * @param player    玩家
     * @param queueSlot 饵料队列槽位号
     * @param amount    取出数量（<=0 表示全部取出）
     * @return 操作结果消息
     */
    public @NotNull String takeBait(@NotNull Player player, int queueSlot, int amount) {
        if (queueSlot < 0 || queueSlot >= getBaitSlots(player)) return "&c槽位号无效。";

        List<BaitQueueEntry> queue = repository.loadBaitQueue(player.getUniqueId());
        BaitQueueEntry target = null;
        for (BaitQueueEntry entry : queue) {
            if (entry.slotIndex() == queueSlot) { target = entry; break; }
        }
        if (target == null) return "&c该槽位为空。";

        BaitDefinition bait = findBait(target.baitId());
        if (bait == null) return "&c未知饵料类型。";

        int toReturn = amount <= 0 ? target.amount() : Math.min(amount, target.amount());

        // 生成饵料物品返还玩家
        ItemStack baitItem = iconResolver.resolveItemStack(player, bait.itemRef());
        if (baitItem != null) {
            baitItem.setAmount(toReturn);
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(baitItem);
            // 背包满则掉落
            for (ItemStack leftover : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }

        // 更新数据库
        int remaining = target.amount() - toReturn;
        repository.saveBaitSlot(new BaitQueueEntry(player.getUniqueId(), queueSlot, target.baitId(), remaining));
        return "&a成功取出 &e" + toReturn + " &a个 &f" + bait.displayName();
    }

    /**
     * 鱼咬钩时消耗队列头部1个饵料。
     * 队列头部 = slot_index 最小的条目，amount-1，为0则删除。
     */
    public void consumeBaitOnBite(@NotNull Player player) {
        List<BaitQueueEntry> queue = repository.loadBaitQueue(player.getUniqueId());
        if (queue.isEmpty()) return;
        BaitQueueEntry head = queue.get(0);
        int newAmount = head.amount() - 1;
        repository.saveBaitSlot(new BaitQueueEntry(player.getUniqueId(), head.slotIndex(), head.baitId(), newAmount));
        debug("consumeBaitOnBite: player=" + player.getName() + " bait=" + head.baitId()
            + " slot=" + head.slotIndex() + " remaining=" + newAmount);
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
     * 升级一个天赋节点（点亮或提升等级）。
     *
     * @param uuid   玩家 UUID
     * @param nodeId 节点 ID
     * @return {@code true} 表示升级成功
     */
    public boolean levelUpTalent(@NotNull UUID uuid, @NotNull String nodeId) {
        FishingSeasonData data = repository.loadSeasonData(uuid, configuration.season().seasonId());
        if (!talentEngine.canLevelUp(data, nodeId)) return false;
        FishingSeasonData updated = talentEngine.levelUp(data, nodeId);
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
            .filter(r -> r.rewardId().equals(rewardId))
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
            .filter(r -> data.level() >= r.level() && !data.claimedRewards().contains(r.rewardId()))
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
     * 玩家上线时处理。
     * <p>
     * 待发放奖励补发已由本体统一 PendingRewardService 在 PlayerJoinEvent 中自动处理，
     * 此方法仅处理任务分配。
     *
     * @param player 上线玩家
     */
    public void onPlayerJoin(@NotNull Player player) {
        try {
            taskEngine.onPlayerJoin(player);
        } catch (Exception e) {
            logger.warning("分配任务失败 [" + player.getName() + "]: " + e.getMessage());
        }
    }

    /**
     * 玩家退出时清理内存缓存，防止内存泄漏。
     * <p>
     * 清理 recentCatches 和 fishingCooldowns 中该玩家的条目。
     *
     * @param uuid 玩家 UUID
     */
    public void onPlayerQuit(@NotNull UUID uuid) {
        recentCatches.remove(uuid);
        fishingCooldowns.remove(uuid);
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
        FishingSeasonData data = repository.loadSeasonData(uuid, configuration.season().seasonId());
        int clamped = Math.max(0, Math.min(100, value));
        FishingSeasonData updated = new FishingSeasonData(
            data.uuid(), data.seasonId(), data.level(), data.currentXp(),
            data.talentPoints(), data.unspentTalentPoints(), data.talents(),
            data.claimedRewards(), clamped, data.claimedMaxFatigueReward());
        repository.saveSeasonData(updated);
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
        // 持久化到数据库
        repository.saveCatchRecord(new FishingCatchRecord(
            uuid, catchEntry.fishId(), catchEntry.fishName(), catchEntry.rarity(),
            catchEntry.size(), catchEntry.perfect(), catchEntry.timestamp()
        ));
        // 同时更新内存缓存（避免每次打开 UI 都查库）
        Deque<RecentCatch> deque = recentCatches.computeIfAbsent(uuid, k -> new ArrayDeque<>());
        deque.addFirst(catchEntry);
        while (deque.size() > MAX_RECENT_CATCHES) {
            deque.removeLast();
        }
    }

    /** 获取玩家最近捕获记录列表（最新在前）。优先用内存缓存，缓存未命中时从数据库加载。 */
    @NotNull
    public List<RecentCatch> getRecentCatches(@NotNull UUID uuid) {
        Deque<RecentCatch> deque = recentCatches.get(uuid);
        if (deque != null && !deque.isEmpty()) {
            return new ArrayList<>(deque);
        }
        // 缓存未命中，从数据库加载
        List<FishingCatchRecord> records = repository.loadCatchRecords(uuid, MAX_RECENT_CATCHES);
        List<RecentCatch> result = new ArrayList<>();
        for (FishingCatchRecord r : records) {
            result.add(new RecentCatch(r.fishId(), r.fishName(), r.rarity(), r.size(), r.perfect(), r.timestamp()));
        }
        // 回填缓存
        if (!result.isEmpty()) {
            Deque<RecentCatch> newDeque = new ArrayDeque<>(result);
            recentCatches.put(uuid, newDeque);
        }
        return result;
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
            if (seasonData.level() >= reward.level() && !seasonData.claimedRewards().contains(reward.rewardId())) {
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
                                   boolean isFirstCatch, boolean gotTreasure,
                                   @NotNull FishingSeasonData seasonData, @NotNull FishingPermanentData permData) {
        // EventBus 发布（供 BattlePass / QuestGPS / OnlineRewards 等订阅）
        EventBusCapability eventBus = eventBusProvider != null ? eventBusProvider.get() : null;
        if (eventBus != null) {
            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("fish_id", fish.id());
            payload.put("fish_rarity", fish.rarity().name());
            payload.put("fish_size", String.valueOf(size));
            payload.put("water_area", water != null ? water.id() : "default");
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
                treasurePayload.put("water_area", water != null ? water.id() : "default");
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
            vars.put("water_area", water != null ? water.id() : "default");
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
        ItemStack hand = player.getInventory().getItemInMainHand();
        String fishId = getFishIdFromItem(hand);
        if (fishId == null) {
            return msgOr("sell_not_fish", "&c手中物品不是钓鱼产物。");
        }

        FishDefinition fish = findFish(fishId);
        if (fish == null) {
            return "&c未知鱼种，无法出售。";
        }

        CurrencyBridgeAPI currencyApi = currencyApi();
        if (currencyApi == null) {
            return "&c货币系统暂不可用。";
        }

        int size = getFishSizeFromItem(hand);
        int price = fish.calculatePrice(size);
        String currency = resolveSellCurrency(fish);

        var bridge = currencyApi.bridge(currency);
        if (bridge == null || !bridge.available()) {
            return "&c货币 " + currency + " 暂不可用。";
        }

        bridge.deposit(player, BigDecimal.valueOf(price));
        hand.setAmount(hand.getAmount() - 1);
        return msgOr("sell_success", "&a成功出售 &e" + fish.displayName() + " &a获得 &6" + price + " " + currency,
            fish.displayName(), String.valueOf(price), currency);
    }

    /**
     * 售卖背包中所有钓鱼产物。
     */
    public @NotNull String sellAllFish(@NotNull Player player) {
        CurrencyBridgeAPI currencyApi = currencyApi();
        if (currencyApi == null) {
            return "&c货币系统暂不可用。";
        }

        Map<String, Double> earnings = new LinkedHashMap<>();
        int totalSold = 0;

        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            String fishId = getFishIdFromItem(item);
            if (fishId == null) continue;

            FishDefinition fish = findFish(fishId);
            if (fish == null) continue;

            String currency = resolveSellCurrency(fish);
            var bridge = currencyApi.bridge(currency);
            if (bridge == null || !bridge.available()) continue;

            int size = getFishSizeFromItem(item);
            int price = fish.calculatePrice(size);
            int amount = item.getAmount();
            bridge.deposit(player, BigDecimal.valueOf(price * amount));
            earnings.merge(currency, (double) price * amount, Double::sum);
            totalSold += amount;
            player.getInventory().setItem(i, null);
        }

        if (totalSold == 0) {
            return msgOr("sell_none", "&c背包中没有可出售的鱼。");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("&a共出售 &e").append(totalSold).append(" &a条鱼，获得 ");
        for (var e : earnings.entrySet()) {
            sb.append("&6").append(String.format("%.0f", e.getValue())).append(" ").append(e.getKey()).append(" ");
        }
        return sb.toString().trim();
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
        if (packetBridge == null || !packetBridge.isAvailable()) return;
        FishingSeasonData seasonData = repository.loadSeasonData(player.getUniqueId(), configuration.season().seasonId());

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
                nodeMap.put("canLevelUp", talentEngine.canLevelUp(seasonData, node.id()));
                nodeMap.put("iconJson", buildTalentNodeIconJson(node, currentLevel > 0, talentEngine.canLevelUp(seasonData, node.id()), player));
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
                activeBonuses.add(formatEffect(effectType, sum));
            }
        }
        payload.put("activeBonuses", activeBonuses);

        sendUiInitPacket(player, uiId, payload);
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
            entry.put("id", reward.rewardId());
            entry.put("level", reward.level());
            entry.put("type", reward.type());
            // iconName 供 UI 端文本显示用（从 IconDefinition 提取）
            xuanmo.arcartxsuite.api.icon.IconDefinition iconDef = reward.icon();
            entry.put("iconName", iconDef != null && iconDef.name() != null ? iconDef.name() : "");
            entry.put("iconMaterial", iconDef != null ? iconDef.material() : "");
            entry.put("iconAmount", iconDef != null ? iconDef.amount() : 1);
            entry.put("claimable", seasonData.level() >= reward.level() && !seasonData.claimedRewards().contains(reward.rewardId()));
            entry.put("claimed", seasonData.claimedRewards().contains(reward.rewardId()));
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
        return iconResolver.itemToJson(stack);
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

        sendUiInitPacket(player, uiId, payload);
    }

    /**
     * 推送饵料管理 UI 数据。
     * 包含：饵料槽数量、当前队列内容（含物品图标 JSON）、可用饵料定义列表。
     */
    public void pushBaitsData(@NotNull Player player, @NotNull String uiId) {
        if (packetBridge == null || !packetBridge.isAvailable()) return;

        int slots = getBaitSlots(player);
        int[] breakdown = getBaitSlotsBreakdown(player);
        List<BaitQueueEntry> queue = loadBaitQueue(player);

        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("packetId", "AXS_FISHING_BAITS");
        payload.put("baitSlots", slots);
        payload.put("baitSlotsBase", breakdown[0]);
        payload.put("baitSlotsBonus", breakdown[1]);

        // 队列条目
        java.util.Map<String, Object> queueMap = new java.util.LinkedHashMap<>();
        for (BaitQueueEntry entry : queue) {
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

    /**
     * 处理客户端 UI 操作请求（点亮天赋/领取奖励等）。
     *
     * @param player 玩家
     * @param action 操作类型
     * @param data   操作数据
     */
    public void handleUiAction(@NotNull Player player, @NotNull String action, @NotNull java.util.List<String> data) {
        switch (action.toLowerCase()) {
            case "level_up_talent" -> {
                if (data.size() > 1) {
                    String nodeId = data.get(1);
                    if (levelUpTalent(player.getUniqueId(), nodeId)) {
                        player.sendMessage("§a天赋升级成功: " + nodeId);
                    } else {
                        player.sendMessage("§c无法升级天赋: " + nodeId);
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
