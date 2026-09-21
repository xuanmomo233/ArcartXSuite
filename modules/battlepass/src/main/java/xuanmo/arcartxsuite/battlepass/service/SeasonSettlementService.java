package xuanmo.arcartxsuite.battlepass.service;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.item.PendingRewardService;
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
 *   <li>离线玩家：暂存到统一 PendingRewardService，由宿主在玩家上线时自动补发</li>
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
    private final PendingRewardService pendingRewardService;

    public SeasonSettlementService(
        JavaPlugin plugin,
        BattlePassModuleConfiguration configuration,
        BattlePassRepository repository,
        BattlePassService service,
        BattlePassRewardDispatcher dispatcher,
        Logger logger,
        PendingRewardService pendingRewardService
    ) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.repository = repository;
        this.service = service;
        this.dispatcher = dispatcher;
        this.logger = logger;
        this.pendingRewardService = pendingRewardService;
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
                logger.info("赛季 " + seasonId + " 已结算过，跳过");
                return false;
            }
            logger.info("开始结算赛季 " + seasonId
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
                    logger.warning("结算玩家 " + summary.playerUuid() + " 失败: " + e.getMessage());
                }
            }

            repository.markSeasonSettled(seasonId);
            logger.info("赛季 " + seasonId + " 结算完成"
                + " | 在线发放: " + onlineCount + " | 离线发放: " + offlineCount);
            return true;
        } catch (SQLException e) {
            logger.log(Level.WARNING, "赛季结算数据库操作失败: " + seasonId, e);
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
        settleUnclaimedLevelRewards(uuid, level, tier, seasonId, onlinePlayer);

        // 2. 发放赛季结算奖励（按等级阈值）
        settleSeasonRewards(level, tier, onlinePlayer, uuid);

        return online;
    }

    /**
     * 发放玩家未领取的等级奖励。
     *
     * @param uuid         玩家 UUID
     * @param level        玩家等级
     * @param tier         玩家档位
     * @param seasonId     赛季 ID
     * @param onlinePlayer 在线玩家实例，为 null 表示离线
     */
    private void settleUnclaimedLevelRewards(UUID uuid, int level, BattlePassPlayerProgress.PassTier tier,
                                              String seasonId, @Nullable Player onlinePlayer) {
        Set<String> claimedRewards;
        try {
            claimedRewards = repository.loadClaimedRewards(uuid, seasonId);
        } catch (SQLException e) {
            logger.warning("加载玩家领取记录失败 [" + uuid + "]: " + e.getMessage());
            claimedRewards = new HashSet<>();
        }

        for (BattlePassReward reward : configuration.rewards()) {
            if (reward.level() > level) continue;
            if (reward.tier() == BattlePassReward.RewardTier.PREMIUM
                && tier == BattlePassPlayerProgress.PassTier.FREE) continue;
            if (reward.tier() == BattlePassReward.RewardTier.DELUXE
                && tier != BattlePassPlayerProgress.PassTier.DELUXE) continue;
            if (claimedRewards.contains(reward.rewardId())) continue;

            if (onlinePlayer != null) {
                dispatcher.dispatch(onlinePlayer, reward).whenComplete((ok, err) -> {
                    if (err == null && Boolean.TRUE.equals(ok)) {
                        markClaimed(uuid, seasonId, reward.rewardId());
                    } else {
                        // 发放失败：不标记领取，转入统一 PendingRewardService 补发
                        logger.warning("战令等级奖励发放失败，转待补发 [" + uuid + "/" + reward.rewardId() + "]"
                            + (err != null ? ": " + err.getMessage() : ""));
                        savePendingToUnifiedService(uuid, reward, "战令等级奖励: " + reward.rewardId());
                        markClaimed(uuid, seasonId, reward.rewardId());
                    }
                });
            } else {
                // 离线玩家：暂存到统一 PendingRewardService，由宿主上线时补发
                savePendingToUnifiedService(uuid, reward, "战令等级奖励: " + reward.rewardId());
                markClaimed(uuid, seasonId, reward.rewardId());
            }
        }
    }

    private void markClaimed(UUID uuid, String seasonId, String rewardId) {
        try {
            repository.saveClaimedReward(uuid, seasonId, rewardId);
        } catch (SQLException e) {
            logger.warning("标记结算奖励领取记录失败 [" + uuid + "/" + rewardId + "]: " + e.getMessage());
        }
    }

    /**
     * 发放赛季结算奖励（按等级阈值）。
     * <p>
     * 使用 "settlement_<settlementId>_<tier>" 作为 claimedRewards 的 key 防止补发时重复。
     */
    private void settleSeasonRewards(int level, BattlePassPlayerProgress.PassTier tier,
                                      @Nullable Player onlinePlayer, UUID uuid) {
        String seasonId = configuration.season().seasonId();
        Set<String> claimedRewards;
        try {
            claimedRewards = repository.loadClaimedRewards(uuid, seasonId);
        } catch (SQLException e) {
            logger.warning("加载玩家领取记录失败 [" + uuid + "]: " + e.getMessage());
            claimedRewards = new HashSet<>();
        }

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
                dispatcher.dispatch(onlinePlayer, reward).whenComplete((ok, err) -> {
                    if (err != null || !Boolean.TRUE.equals(ok)) {
                        // 发放失败：转统一 PendingRewardService 补发兜底
                        logger.warning("战令结算奖励发放失败，转待补发 [" + uuid + "/" + settlementKey + "]"
                            + (err != null ? ": " + err.getMessage() : ""));
                        savePendingToUnifiedService(uuid, reward, "战令结算奖励: " + settlementKey);
                    }
                    // 标记已发放/已安排补发（防止重复入队）
                    markClaimed(uuid, seasonId, settlementKey);
                });
            } else {
                // 离线玩家：mail 类型尝试通过邮件发送，其他类型暂存到统一 PendingRewardService
                boolean sent = dispatcher.dispatchOffline(uuid, getPlayerName(uuid), reward);
                if (!sent) {
                    savePendingToUnifiedService(uuid, reward, "战令结算奖励: " + settlementKey);
                }
                // 标记已发放（防止补发时重复）
                markClaimed(uuid, seasonId, settlementKey);
            }
        }
    }

    /**
     * 将离线玩家的奖励暂存到统一 PendingRewardService。
     */
    private void savePendingToUnifiedService(UUID uuid, BattlePassReward reward, String description) {
        String type = reward.type() != null ? reward.type().toLowerCase() : "";
        switch (type) {
            case "item" -> {
                if (reward.item() != null) {
                    xuanmo.arcartxsuite.api.icon.IconDefinition itemDef = reward.item();
                    String source = itemDef.source() != null && !itemDef.source().isBlank()
                        ? itemDef.source() : "minecraft";
                    String itemId = itemDef.sourceId() != null && !itemDef.sourceId().isBlank()
                        ? itemDef.sourceId() : (itemDef.material() != null ? itemDef.material() : "");
                    String nbt = itemDef.nbtString() != null ? itemDef.nbtString() : "";
                    pendingRewardService.savePending(PendingRewardService.PendingReward.item(
                        uuid, "battlepass",
                        source, itemId,
                        itemDef.amount(), nbt,
                        description));
                }
            }
            case "currency" -> {
                if (reward.currency() != null) {
                    pendingRewardService.savePending(PendingRewardService.PendingReward.currency(
                        uuid, "battlepass", reward.currency(), reward.currencyAmount(), description));
                }
            }
            case "command" -> {
                if (reward.commands() != null && !reward.commands().isEmpty()) {
                    pendingRewardService.savePending(PendingRewardService.PendingReward.command(
                        uuid, "battlepass", reward.commands(), description));
                }
            }
            case "mail" -> {
                if (reward.mailPresets() != null && !reward.mailPresets().isEmpty()) {
                    pendingRewardService.savePending(PendingRewardService.PendingReward.mailPreset(
                        uuid, "battlepass", reward.mailPresets(), description));
                }
            }
        }
    }

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
