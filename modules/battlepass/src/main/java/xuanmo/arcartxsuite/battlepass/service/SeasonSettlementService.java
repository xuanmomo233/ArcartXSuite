package xuanmo.arcartxsuite.battlepass.service;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.util.AxsScheduler;
import xuanmo.arcartxsuite.battlepass.config.BattlePassModuleConfiguration;
import xuanmo.arcartxsuite.battlepass.model.BattlePassPlayerProgress;
import xuanmo.arcartxsuite.battlepass.model.BattlePassReward;
import xuanmo.arcartxsuite.battlepass.model.SeasonSettlementReward;
import xuanmo.arcartxsuite.battlepass.storage.BattlePassRepository;

/**
 * 赛季结算服务，负责赛季结束时的奖励发放。
 * <p>
 * 结算逻辑分两步：
 * <ol>
 *   <li>发放未领取的等级奖励（rewards.yml 中玩家达到等级但未领取的奖励）</li>
 *   <li>发放赛季结算奖励（season-rewards.yml 中按等级阈值配置的额外奖励）</li>
 * </ol>
 * <p>
 * 发放方式：
 * <ul>
 *   <li>在线玩家：直接发放（通过 BattlePassRewardDispatcher）</li>
 *   <li>离线玩家：通过 mail 模块发放（mail 类型奖励），非 mail 类型跳过</li>
 * </ul>
 * <p>
 * 防重复：通过 bp_season_settlement 表记录已结算赛季，重启后不会重复结算。
 */
public final class SeasonSettlementService {

    private final JavaPlugin plugin;
    private final BattlePassModuleConfiguration configuration;
    private final BattlePassRepository repository;
    private final BattlePassService service;
    private final BattlePassRewardDispatcher dispatcher;
    private final Logger logger;

    // 已补发结算的离线玩家集合（防止上线时重复补发）
    private final Set<UUID> settledOfflinePlayers = ConcurrentHashMap.newKeySet();

    public SeasonSettlementService(
        JavaPlugin plugin,
        BattlePassModuleConfiguration configuration,
        BattlePassRepository repository,
        BattlePassService service,
        BattlePassRewardDispatcher dispatcher,
        Logger logger
    ) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.repository = repository;
        this.service = service;
        this.dispatcher = dispatcher;
        this.logger = logger;
    }

    /**
     * 检查是否需要自动结算（赛季结束日期已过且未结算）。
     *
     * @return {@code true} 表示触发了结算
     */
    public boolean checkAndAutoSettle() {
        String seasonId = configuration.season().seasonId();
        LocalDate endDate = configuration.season().endDate();
        LocalDate today = LocalDate.now();
        if (today.isAfter(endDate)) {
            return settle(seasonId, false);
        }
        return false;
    }

    /**
     * 执行赛季结算。
     *
     * @param seasonId  赛季 ID
     * @param forced    是否为管理员强制结算（忽略赛季结束日期检查）
     * @return {@code true} 表示结算成功（或已结算过）
     */
    public boolean settle(String seasonId, boolean forced) {
        try {
            if (repository.isSeasonSettled(seasonId)) {
                logger.info("[battlepass] 赛季 " + seasonId + " 已结算过，跳过");
                return false;
            }
            logger.info("[battlepass] 开始结算赛季 " + seasonId
                + (forced ? "（管理员手动触发）" : "（自动触发）"));

            List<BattlePassRepository.PlayerSeasonSummary> summaries = repository.loadSeasonSummaries(seasonId);
            int onlineCount = 0;
            int offlineCount = 0;
            for (BattlePassRepository.PlayerSeasonSummary summary : summaries) {
                try {
                    boolean online = settlePlayer(summary, seasonId);
                    if (online) onlineCount++;
                    else offlineCount++;
                } catch (Exception e) {
                    logger.warning("[battlepass] 结算玩家 " + summary.playerUuid() + " 失败: " + e.getMessage());
                }
            }

            repository.markSeasonSettled(seasonId);
            logger.info("[battlepass] 赛季 " + seasonId + " 结算完成"
                + " | 在线发放: " + onlineCount + " | 离线发放: " + offlineCount);
            return true;
        } catch (SQLException e) {
            logger.log(Level.WARNING, "[battlepass] 赛季结算数据库操作失败: " + seasonId, e);
            return false;
        }
    }

    /**
     * 结算单个玩家。
     *
     * @param summary  玩家赛季摘要
     * @param seasonId 赛季 ID
     * @return {@code true} 表示玩家在线并已直接发放
     */
    private boolean settlePlayer(BattlePassRepository.PlayerSeasonSummary summary, String seasonId) {
        UUID uuid = summary.playerUuid();
        int level = summary.level();
        BattlePassPlayerProgress.PassTier tier;
        try {
            tier = BattlePassPlayerProgress.PassTier.valueOf(summary.tier().toUpperCase());
        } catch (IllegalArgumentException e) {
            tier = BattlePassPlayerProgress.PassTier.FREE;
        }

        Player onlinePlayer = Bukkit.getPlayer(uuid);
        boolean online = onlinePlayer != null && onlinePlayer.isOnline();

        // 1. 发放未领取的等级奖励
        boolean needsPending = settleUnclaimedLevelRewards(uuid, level, tier, seasonId, onlinePlayer);

        // 2. 发放赛季结算奖励（按等级阈值）
        needsPending |= settleSeasonRewards(level, tier, onlinePlayer, uuid);

        if (!online && needsPending) {
            settledOfflinePlayers.add(uuid);
        }
        return online;
    }

    /**
     * 发放玩家未领取的等级奖励。
     *
     * @return {@code true} 表示有奖励需要上线后补发（离线玩家且有非 mail 类型奖励）
     */
    private boolean settleUnclaimedLevelRewards(UUID uuid, int level, BattlePassPlayerProgress.PassTier tier,
                                              String seasonId, @Nullable Player onlinePlayer) {
        Set<String> claimedRewards;
        try {
            claimedRewards = repository.loadClaimedRewards(uuid, seasonId);
        } catch (SQLException e) {
            logger.warning("[battlepass] 加载玩家领取记录失败 [" + uuid + "]: " + e.getMessage());
            claimedRewards = new HashSet<>();
        }

        boolean needsPending = false;
        for (BattlePassReward reward : configuration.rewards()) {
            if (reward.level() > level) continue;
            if (reward.tier() == BattlePassReward.RewardTier.PREMIUM
                && tier == BattlePassPlayerProgress.PassTier.FREE) continue;
            if (reward.tier() == BattlePassReward.RewardTier.DELUXE
                && tier != BattlePassPlayerProgress.PassTier.DELUXE) continue;
            if (claimedRewards.contains(reward.rewardId())) continue;

            if (onlinePlayer != null) {
                dispatcher.dispatch(onlinePlayer, reward);
            } else {
                boolean sent = dispatcher.dispatchOffline(uuid, getPlayerName(uuid), reward);
                if (!sent) needsPending = true;
            }
            try {
                repository.saveClaimedReward(uuid, seasonId, reward.rewardId());
            } catch (SQLException e) {
                logger.warning("[battlepass] 标记结算奖励领取记录失败 [" + uuid + "/" + reward.rewardId() + "]: " + e.getMessage());
            }
        }
        return needsPending;
    }

    /**
     * 发放赛季结算奖励（按等级阈值）。
     * <p>
     * 使用 "settlement_<settlementId>_<tier>" 作为 claimedRewards 的 key 防止补发时重复。
     *
     * @return {@code true} 表示有奖励需要上线后补发
     */
    private boolean settleSeasonRewards(int level, BattlePassPlayerProgress.PassTier tier,
                                      @Nullable Player onlinePlayer, UUID uuid) {
        String seasonId = configuration.season().seasonId();
        Set<String> claimedRewards;
        try {
            claimedRewards = repository.loadClaimedRewards(uuid, seasonId);
        } catch (SQLException e) {
            logger.warning("[battlepass] 加载玩家领取记录失败 [" + uuid + "]: " + e.getMessage());
            claimedRewards = new HashSet<>();
        }

        boolean needsPending = false;
        String tierName = tier.name().toLowerCase();
        for (SeasonSettlementReward settlement : configuration.seasonRewards()) {
            if (level < settlement.minLevel()) continue;

            BattlePassReward reward = switch (tier) {
                case DELUXE -> settlement.deluxe() != null ? settlement.deluxe() : settlement.premium() != null ? settlement.premium() : settlement.free();
                case PREMIUM -> settlement.premium() != null ? settlement.premium() : settlement.free();
                case FREE -> settlement.free();
            };
            if (reward == null) continue;

            String settlementKey = "settlement_" + settlement.settlementId() + "_" + tierName;
            if (claimedRewards.contains(settlementKey)) continue;

            if (onlinePlayer != null) {
                dispatcher.dispatch(onlinePlayer, reward);
            } else {
                boolean sent = dispatcher.dispatchOffline(uuid, getPlayerName(uuid), reward);
                if (!sent) needsPending = true;
            }
            // 标记已发放（防止补发时重复）
            try {
                repository.saveClaimedReward(uuid, seasonId, settlementKey);
            } catch (SQLException e) {
                logger.warning("[battlepass] 标记结算奖励发放记录失败 [" + uuid + "/" + settlementKey + "]: " + e.getMessage());
            }
        }
        return needsPending;
    }

    /**
     * 玩家上线时检查是否需要补发结算奖励。
     * <p>
     * 如果赛季已结算但该玩家在结算时离线，且尚未补发过，则执行补发。
     *
     * @param player 上线玩家
     */
    public void checkPendingSettlement(Player player) {
        UUID uuid = player.getUniqueId();
        if (settledOfflinePlayers.contains(uuid)) {
            // 该玩家在结算时离线，需要补发
            String seasonId = configuration.season().seasonId();
            try {
                if (!repository.isSeasonSettled(seasonId)) return;
                List<BattlePassRepository.PlayerSeasonSummary> summaries = repository.loadSeasonSummaries(seasonId);
                for (BattlePassRepository.PlayerSeasonSummary summary : summaries) {
                    if (summary.playerUuid().equals(uuid)) {
                        logger.fine("[battlepass] 补发离线玩家 " + player.getName() + " 的赛季结算奖励");
                        settlePlayer(summary, seasonId);
                        break;
                    }
                }
            } catch (SQLException e) {
                logger.warning("[battlepass] 补发结算奖励失败 [" + uuid + "]: " + e.getMessage());
            } finally {
                settledOfflinePlayers.remove(uuid);
            }
        }
    }

    /**
     * 获取玩家名称（在线优先，离线时从 Bukkit OfflinePlayer 获取）。
     */
    private String getPlayerName(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        return offline.getName() != null ? offline.getName() : uuid.toString();
    }

    /**
     * 查询赛季结算状态。
     *
     * @param seasonId 赛季 ID
     * @return 结算状态描述
     */
    public String getSettlementStatus(String seasonId) {
        try {
            boolean settled = repository.isSeasonSettled(seasonId);
            if (settled) return "已结算";
            LocalDate endDate = configuration.season().endDate();
            if (LocalDate.now().isAfter(endDate)) return "待结算（赛季已结束）";
            return "未结算（赛季进行中）";
        } catch (SQLException e) {
            return "查询失败: " + e.getMessage();
        }
    }
}
