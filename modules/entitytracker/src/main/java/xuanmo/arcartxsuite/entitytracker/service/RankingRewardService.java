package xuanmo.arcartxsuite.entitytracker.service;

import xuanmo.arcartxsuite.entitytracker.boss.config.BossDamageRankRewardDefinition;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDamageRankingRewardsSettings;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDefinition;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossRankingRewardsSettings;
import xuanmo.arcartxsuite.entitytracker.boss.config.PluginConfiguration;
import xuanmo.arcartxsuite.entitytracker.dao.PlayerBossBestDamageDao;
import xuanmo.arcartxsuite.entitytracker.dao.RankingRewardRecordDao;
import xuanmo.arcartxsuite.entitytracker.entity.PlayerBossBestDamage;
import xuanmo.arcartxsuite.entitytracker.entity.RankingRewardRecord;
import xuanmo.arcartxsuite.entitytracker.reward.RewardActionExecutor;
import xuanmo.arcartxsuite.entitytracker.reward.RewardActionResult;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * 排行榜定时奖励服务。
 * <p>
 * 奖励配置从各 Boss yml 文件的 {@code damage-ranking.ranking-rewards} 节点读取，
 * 排行数据从数据库查询，动作执行委托给 {@link RewardActionExecutor}。
 */
public class RankingRewardService {

    private static final DecimalFormat NUMBER_FORMAT = new DecimalFormat("0.##");

    private final Logger logger;
    private final RankingRewardRecordDao recordDao;
    private final PlayerBossBestDamageDao bestDamageDao;
    private final RewardActionExecutor actionExecutor;
    private final JavaPlugin plugin;
    private final java.util.function.Supplier<PluginConfiguration> configurationSupplier;

    public RankingRewardService(
        DataSource dataSource,
        RewardActionExecutor actionExecutor,
        JavaPlugin plugin,
        java.util.function.Supplier<PluginConfiguration> configurationSupplier
    ) {
        this.logger = plugin.getLogger();
        this.recordDao = new RankingRewardRecordDao(dataSource, plugin);
        this.bestDamageDao = new PlayerBossBestDamageDao(dataSource, plugin);
        this.actionExecutor = actionExecutor;
        this.plugin = plugin;
        this.configurationSupplier = configurationSupplier;
    }

    /**
     * 发放指定周期类型的奖励（遍历所有 Boss 配置）
     *
     * @param periodType  "weekly" 或 "monthly"
     * @param periodStart 周期开始时间
     * @param periodEnd   周期结束时间
     * @return 总发放奖励人数
     */
    public CompletableFuture<Integer> distributeRewards(String periodType, LocalDateTime periodStart,
                                                       LocalDateTime periodEnd) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("开始发放 " + periodType + " 排行榜奖励: " + periodStart + " - " + periodEnd);

                PluginConfiguration config = configurationSupplier.get();
                if (config == null) {
                    logger.warning("PluginConfiguration 尚未加载，跳过奖励发放");
                    return 0;
                }

                int totalRewards = 0;
                for (Map.Entry<String, BossDefinition> entry : config.bosses().entrySet()) {
                    BossDefinition boss = entry.getValue();
                    if (!boss.enabled() || !boss.damageRanking().enabled()) {
                        continue;
                    }

                    BossRankingRewardsSettings rankingRewards = boss.damageRanking().rankingRewards();
                    BossDamageRankingRewardsSettings periodSettings = rankingRewards.forPeriod(periodType);
                    if (!periodSettings.enabled() || periodSettings.rankRewards().isEmpty()) {
                        continue;
                    }

                    int rewarded = distributeRewardsForBoss(
                        boss, periodSettings, periodType, periodStart, periodEnd
                    );
                    totalRewards += rewarded;
                }

                logger.info("完成 " + periodType + " 排行榜奖励发放: 总发放=" + totalRewards);
                return totalRewards;

            } catch (Exception e) {
                logger.severe("发放 " + periodType + " 排行榜奖励失败: " + e.getMessage());
                throw new RuntimeException("发放排行榜奖励失败", e);
            }
        });
    }

    /**
     * 为单个 Boss 发放排行榜奖励
     */
    private int distributeRewardsForBoss(
        BossDefinition boss,
        BossDamageRankingRewardsSettings rewardSettings,
        String periodType,
        LocalDateTime periodStart,
        LocalDateTime periodEnd
    ) {
        try {
            // 查询该 Boss 的伤害排行
            int maxRank = rewardSettings.rankRewards().keySet().stream()
                .mapToInt(Integer::intValue).max().orElse(0);
            if (maxRank <= 0) return 0;

            List<PlayerBossBestDamage> rankings = bestDamageDao.findTopDamageByBoss(
                boss.mythicMobId(), maxRank
            );

            if (rankings.isEmpty()) {
                logger.info("Boss " + boss.mythicMobId() + " 无排行数据，跳过");
                return 0;
            }

            int rewardedCount = 0;
            for (PlayerBossBestDamage ranking : rankings) {
                if (recordDao.hasSuccessfulIssuance(
                    boss.mythicMobId(), periodType, periodStart, ranking.getPlayerUuid(), ranking.getRank()
                )) {
                    logger.fine("Boss " + boss.mythicMobId() + " " + periodType + " 第"
                        + ranking.getRank() + "名已发放，跳过");
                    continue;
                }

                BossDamageRankRewardDefinition rewardDef = rewardSettings.rewardForRank(ranking.getRank());
                if (rewardDef.actions().isEmpty()) {
                    continue;
                }

                // 构建变量上下文
                Map<String, String> variables = buildVariables(boss, ranking, periodType);

                // 解析目标玩家
                OfflinePlayer target = resolvePlayer(ranking.getPlayerUuid());
                if (target == null) {
                    logger.warning("无法解析玩家: UUID=" + ranking.getPlayerUuid() + ", 跳过奖励");
                    continue;
                }

                // 在主线程执行奖励动作（物品发放等需要主线程）
                List<RewardActionResult> results = executeOnMainThread(
                    rewardDef, target, variables, rewardSettings
                );

                // 记录发放结果到数据库
                saveRewardRecord(boss, ranking, periodType, periodStart, periodEnd, results);
                rewardedCount++;

                if (logger.isLoggable(java.util.logging.Level.FINE)) {
                    logger.fine("Boss=" + boss.mythicMobId()
                        + " 第" + ranking.getRank() + "名 " + ranking.getPlayerName()
                        + " 奖励执行: " + summarizeResults(results));
                }
            }

            logger.info("Boss " + boss.mythicMobId() + " " + periodType + " 奖励发放: " + rewardedCount + " 人");
            return rewardedCount;

        } catch (SQLException e) {
            logger.severe("Boss " + boss.mythicMobId() + " 排行榜奖励发放失败: " + e.getMessage());
            return 0;
        }
    }

    /**
     * 在主线程执行奖励动作
     */
    private List<RewardActionResult> executeOnMainThread(
        BossDamageRankRewardDefinition rewardDef,
        OfflinePlayer target,
        Map<String, String> variables,
        BossDamageRankingRewardsSettings rewardSettings
    ) {
        if (Bukkit.isPrimaryThread()) {
            return actionExecutor.executeActions(
                rewardDef.actions(), target, variables, rewardSettings.inventoryFullStrategy()
            );
        }

        try {
            return Bukkit.getScheduler().callSyncMethod(plugin, () ->
                actionExecutor.executeActions(
                    rewardDef.actions(), target, variables, rewardSettings.inventoryFullStrategy()
                )
            ).get();
        } catch (Exception e) {
            logger.severe("主线程执行奖励动作失败: " + e.getMessage());
            return List.of(RewardActionResult.fail("sync", "主线程调度失败: " + e.getMessage()));
        }
    }

    /**
     * 保存奖励发放记录
     */
    private void saveRewardRecord(
        BossDefinition boss,
        PlayerBossBestDamage ranking,
        String periodType,
        LocalDateTime periodStart,
        LocalDateTime periodEnd,
        List<RewardActionResult> results
    ) {
        try {
            boolean allSuccess = results.stream().allMatch(RewardActionResult::success);
            String failureReason = results.stream()
                .filter(r -> !r.success())
                .map(r -> r.type() + ": " + r.message())
                .reduce((a, b) -> a + "; " + b)
                .orElse(null);

            RankingRewardRecord record = new RankingRewardRecord(
                0, // 不再关联旧的 config ID
                periodType,
                "boss_damage",
                periodStart,
                periodEnd,
                ranking.getPlayerUuid(),
                ranking.getPlayerName(),
                ranking.getRank(),
                ranking.getBestDamage()
            );
            record.setBossId(boss.mythicMobId());
            record.setServerName(ranking.getServerName());
            record.setStatus(allSuccess ? "success" : "failed");
            record.setIssuedTime(LocalDateTime.now());
            record.setFailureReason(failureReason);

            recordDao.insert(record);
        } catch (SQLException e) {
            logger.severe("保存奖励发放记录失败: " + e.getMessage());
        }
    }

    /**
     * 构建变量上下文
     */
    private Map<String, String> buildVariables(
        BossDefinition boss,
        PlayerBossBestDamage ranking,
        String periodType
    ) {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("player", ranking.getPlayerName());
        variables.put("player_name", ranking.getPlayerName());
        variables.put("player_uuid", ranking.getPlayerUuid());
        variables.put("rank", String.valueOf(ranking.getRank()));
        variables.put("damage", NUMBER_FORMAT.format(ranking.getBestDamage()));
        variables.put("boss_id", boss.mythicMobId());
        variables.put("boss_display_name", ranking.getBossDisplayName() != null
            ? ranking.getBossDisplayName() : boss.mythicMobId());
        variables.put("period_type", periodType);
        variables.put("period_type_display", "weekly".equals(periodType) ? "周" : "月");
        return variables;
    }

    /**
     * 解析玩家
     */
    private static OfflinePlayer resolvePlayer(String playerUuid) {
        try {
            UUID uuid = UUID.fromString(playerUuid);
            return Bukkit.getOfflinePlayer(uuid);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 汇总执行结果
     */
    private static String summarizeResults(List<RewardActionResult> results) {
        long success = results.stream().filter(RewardActionResult::success).count();
        long failed = results.size() - success;
        return success + " 成功, " + failed + " 失败";
    }

    // ─── 查询接口（保留）─────────────────────────────────

    /**
     * 获取玩家的奖励历史
     */
    public CompletableFuture<List<RankingRewardRecord>> getPlayerRewardHistory(String playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return recordDao.findByPlayerUuid(playerUuid);
            } catch (SQLException e) {
                logger.severe("获取玩家奖励历史失败: " + e.getMessage());
                throw new RuntimeException("获取玩家奖励历史失败", e);
            }
        });
    }

    /**
     * 获取奖励发放记录
     */
    public CompletableFuture<List<RankingRewardRecord>> getRewardRecords(String rewardType,
                                                                        String status,
                                                                        LocalDateTime startTime) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return recordDao.findByFilters(rewardType, status, startTime);
            } catch (SQLException e) {
                logger.severe("获取奖励发放记录失败: " + e.getMessage());
                throw new RuntimeException("获取奖励发放记录失败", e);
            }
        });
    }
}

