package xuanmo.arcartxsuite.fishing.fatigue;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.icon.IconDefinition;
import xuanmo.arcartxsuite.api.icon.IconResolver;
import xuanmo.arcartxsuite.fishing.config.FatigueConfig;
import xuanmo.arcartxsuite.fishing.model.FishDefinition;
import xuanmo.arcartxsuite.fishing.model.FishingSeasonData;
import xuanmo.arcartxsuite.fishing.storage.FishingRepository;

/**
 * 疲惫值引擎，负责：
 * <ul>
 *   <li>计算疲惫值对应的效率系数</li>
 *   <li>定时恢复在线玩家的疲惫值（每分钟 -1）</li>
 *   <li>疲惫值 > 90 时按概率选择特殊鱼替换正常鱼</li>
 *   <li>首次疲惫值达到 100 时发放一次性奖励</li>
 * </ul>
 */
public final class FatigueEngine {

    private final JavaPlugin plugin;
    private final FatigueConfig config;
    private final FishingRepository repository;
    private final Logger logger;
    private final String seasonId;
    private final IconResolver iconResolver;
    private final Supplier<CurrencyBridgeAPI> currencyProvider;
    private BukkitTask restTask;

    public FatigueEngine(@NotNull JavaPlugin plugin, @NotNull FatigueConfig config,
                         @NotNull FishingRepository repository, @NotNull Logger logger,
                         @NotNull String seasonId, @NotNull IconResolver iconResolver,
                         @Nullable Supplier<CurrencyBridgeAPI> currencyProvider) {
        this.plugin = plugin;
        this.config = config;
        this.repository = repository;
        this.logger = logger;
        this.seasonId = seasonId;
        this.iconResolver = iconResolver;
        this.currencyProvider = currencyProvider;
    }

    /**
     * 启动定时恢复任务：每隔 rest-check-interval-ticks 对所有在线玩家减少疲惫值。
     */
    public void start() {
        if (!config.enabled() || config.restDecreasePerMinute() <= 0) return;
        long interval = Math.max(20L, config.restCheckIntervalTicks());
        restTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickRest, interval, interval);
        logger.fine("疲惫值定时恢复任务已启动，间隔=" + interval + " ticks");
    }

    /**
     * 停止定时恢复任务。
     */
    public void shutdown() {
        if (restTask != null && !restTask.isCancelled()) {
            restTask.cancel();
            restTask = null;
        }
    }

    /**
     * 定时恢复：对所有在线玩家减少疲惫值。
     * <p>
     * M7 优化：先检查玩家是否在钓鱼中（钓鱼中的玩家不恢复），再查 DB。
     * fatigue=0 的玩家跳过保存，减少 DB 写入。
     */
    private void tickRest() {
        try {
            int decrease = config.restDecreasePerMinute();
            for (Player player : Bukkit.getOnlinePlayers()) {
                try {
                    UUID uuid = player.getUniqueId();
                    FishingSeasonData data = repository.loadSeasonData(uuid, seasonId);
                    if (data.fatigue() <= 0) continue; // 已为 0，无需减少也无需保存
                    FishingSeasonData updated = data.withFatigueReduced(decrease);
                    // 仅当疲惫值实际变化时才保存
                    if (updated.fatigue() != data.fatigue()) {
                        repository.saveSeasonData(updated);
                    }
                } catch (Exception e) {
                    logger.warning("疲惫值恢复失败 [" + player.getName() + "]: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.warning("疲惫值定时恢复任务异常: " + e.getMessage());
        }
    }

    /**
     * 钓鱼成功后增加疲惫值，并检查是否首次达到 100。
     * <p>
     * 仅计算并返回更新后的赛季数据，不自行保存到数据库（由调用方统一保存，避免重复写入）。
     * 若首次达到 100 且奖励配置启用，会尝试发放奖励；仅当奖励全部发放成功时才标记为已领取。
     *
     * @param player     玩家
     * @param seasonData 钓鱼前的赛季数据
     * @return 更新后的赛季数据（含疲惫值增加和可能的奖励标记）
     */
    public @NotNull FishingSeasonData onFishCaught(@NotNull Player player, @NotNull FishingSeasonData seasonData) {
        if (!config.enabled()) return seasonData;
        int addAmount = config.fatiguePerCatch();
        if (addAmount <= 0) return seasonData;

        int oldFatigue = seasonData.fatigue();
        FishingSeasonData updated = seasonData.withFatigueAdded(addAmount, config.maxFatigue());

        // 首次达到 maxFatigue 且未领取过奖励
        if (updated.fatigue() >= config.maxFatigue() && !updated.claimedMaxFatigueReward()) {
            if (config.firstMaxFatigueReward() != null && config.firstMaxFatigueReward().enabled()) {
                // C6: 仅当奖励全部发放成功时才标记为已领取，防止玩家永远拿不到奖励
                boolean allSuccess = dispatchFirstMaxFatigueReward(player);
                if (allSuccess) {
                    updated = updated.withMaxFatigueRewardClaimed();
                }
            }
        }

        logger.fine("玩家 " + player.getName() + " 疲惫值 " + oldFatigue + " → " + updated.fatigue());
        return updated;
    }

    /**
     * 根据疲惫值获取效率系数。
     *
     * @param fatigue 疲惫值
     * @return 效率系数（>1 提升，<1 降低，=1 无影响）
     */
    public double efficiencyMultiplier(int fatigue) {
        if (!config.enabled()) return 1.0;
        return config.efficiencyMultiplier(fatigue);
    }

    /**
     * 根据疲惫值和正常鱼池选择鱼。
     * <p>
     * 疲惫值 > 90 时，有 special-fish-chance 概率从特殊鱼列表中选一条替换正常鱼。
     * 特殊鱼必须在 configuration.fishes() 中已定义（通过 fish-id 引用）。
     *
     * @param fatigue      疲惫值
     * @param normalFish   正常选出的鱼
     * @param fishLookup   鱼 ID → FishDefinition 查找函数
     * @return 最终使用的鱼（可能是特殊鱼或正常鱼）
     */
    public @NotNull FishDefinition applySpecialFish(int fatigue, @NotNull FishDefinition normalFish,
                                                     @NotNull java.util.function.Function<String, FishDefinition> fishLookup) {
        if (!config.enabled() || !config.isInSpecialFishRange(fatigue)) return normalFish;
        if (config.specialFishes().isEmpty()) return normalFish;
        if (ThreadLocalRandom.current().nextDouble() >= config.specialFishChance()) return normalFish;

        String specialFishId = config.rollSpecialFish();
        if (specialFishId == null) return normalFish;
        FishDefinition special = fishLookup.apply(specialFishId);
        if (special != null) {
            logger.fine("玩家疲惫值=" + fatigue + "，触发特殊鱼: " + specialFishId);
            return special;
        }
        logger.warning("特殊鱼未找到: " + specialFishId + "，使用正常鱼");
        return normalFish;
    }

    /**
     * 发放首次疲惫值达到 100 的奖励。
     *
     * @return true 表示所有奖励发放成功，false 表示至少有一条失败
     */
    private boolean dispatchFirstMaxFatigueReward(@NotNull Player player) {
        FatigueConfig.FirstMaxFatigueReward rewardConfig = config.firstMaxFatigueReward();
        if (rewardConfig == null || !rewardConfig.enabled()) return true;

        boolean allSuccess = true;
        for (FatigueConfig.RewardEntry entry : rewardConfig.rewards()) {
            try {
                dispatchSingleReward(player, entry);
            } catch (Exception e) {
                logger.warning("发放首次疲惫值100奖励失败 [type=" + entry.type() + "]: " + e.getMessage());
                allSuccess = false;
            }
        }

        if (allSuccess) {
            player.sendMessage("§6§l恭喜！你首次达到了疲惫值上限，已发放专属奖励！");
        } else {
            player.sendMessage("§c部分疲惫值奖励发放失败，请联系管理员补发。");
        }
        return allSuccess;
    }

    /**
     * 发放单条奖励。
     */
    private void dispatchSingleReward(@NotNull Player player, @NotNull FatigueConfig.RewardEntry entry) {
        switch (entry.type().toLowerCase()) {
            case "currency" -> {
                if (entry.currencyId() != null && !entry.currencyId().isEmpty() && entry.amount() > 0) {
                    CurrencyBridgeAPI currencyApi = currencyProvider != null ? currencyProvider.get() : null;
                    if (currencyApi != null) {
                        var bridge = currencyApi.bridge(entry.currencyId());
                        if (bridge != null && bridge.available()) {
                            bridge.deposit(player, BigDecimal.valueOf(entry.amount()));
                        }
                    }
                }
            }
            case "command" -> {
                if (entry.command() != null && !entry.command().isEmpty()) {
                    String cmd = entry.command().replace("{player}", player.getName());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                }
            }
            case "item" -> {
                if (entry.item() != null && entry.item().hasIcon()) {
                    var itemStack = iconResolver.resolveItemStack(player, entry.item());
                    if (itemStack != null) {
                        // M9: 优先直接放入玩家背包，背包满时才掉落地面
                        var leftover = player.getInventory().addItem(itemStack);
                        if (!leftover.isEmpty()) {
                            player.getWorld().dropItemNaturally(player.getLocation(), itemStack);
                        }
                    }
                }
            }
            default -> logger.warning("未知奖励类型: " + entry.type());
        }
    }

    /**
     * 获取疲惫值配置（供 UI 或其他模块查询）。
     */
    public @NotNull FatigueConfig config() {
        return config;
    }
}
