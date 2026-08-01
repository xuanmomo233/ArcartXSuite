package xuanmo.arcartxsuite.entitytracker.service;

import xuanmo.arcartxsuite.api.util.AxsScheduler;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDamageRankRewardDefinition;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDamageRankingRewardsSettings;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDamageRewardAction;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDamageRewardActionType;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDamageRewardMessageTarget;
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
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private final File bossesDirectory;

    public RankingRewardService(
        DataSource dataSource,
        RewardActionExecutor actionExecutor,
        JavaPlugin plugin,
        Logger logger,
        java.util.function.Supplier<PluginConfiguration> configurationSupplier,
        File bossesDirectory
    ) {
        this.recordDao = new RankingRewardRecordDao(dataSource, plugin);
        this.bestDamageDao = new PlayerBossBestDamageDao(dataSource, plugin);
        this.actionExecutor = actionExecutor;
        this.plugin = plugin;
        this.logger = logger;
        this.configurationSupplier = configurationSupplier;
        this.bossesDirectory = bossesDirectory;
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
            return AxsScheduler.callSyncMethod(plugin, () ->
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

    // ─── 配置管理接口（UI 用）──────────────────────────────

    /**
     * 聚合所有 Boss 指定周期类型的排行榜奖励配置列表。
     *
     * @param periodType "weekly" 或 "monthly"
     * @return 配置列表，每项为 Map，包含 id / rankingTypeDisplay / rankStart / rankEnd / rewardName / rewardPreview / statusIcon
     */
    public List<Map<String, Object>> getConfigsForPeriod(String periodType) {
        List<Map<String, Object>> configs = new ArrayList<>();
        PluginConfiguration config = configurationSupplier.get();
        if (config == null || config.bosses().isEmpty()) {
            return configs;
        }

        for (Map.Entry<String, BossDefinition> entry : config.bosses().entrySet()) {
            BossDefinition boss = entry.getValue();
            if (!boss.enabled() || !boss.damageRanking().enabled()) {
                continue;
            }

            BossRankingRewardsSettings rankingRewards = boss.damageRanking().rankingRewards();
            BossDamageRankingRewardsSettings periodSettings = rankingRewards.forPeriod(periodType);
            if (periodSettings.rankRewards().isEmpty()) {
                continue;
            }

            for (Map.Entry<Integer, BossDamageRankRewardDefinition> rankEntry : periodSettings.rankRewards().entrySet()) {
                int rank = rankEntry.getKey();
                BossDamageRankRewardDefinition rewardDef = rankEntry.getValue();

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", boss.mythicMobId() + "-" + rank);
                item.put("bossId", boss.mythicMobId());
                item.put("rank", Integer.toString(rank));
                item.put("rankingTypeDisplay", "Boss伤害");
                item.put("rankStart", Integer.toString(rank));
                item.put("rankEnd", Integer.toString(rank));
                item.put("rewardName", buildRewardName(rewardDef));
                item.put("rewardPreview", buildRewardPreview(rewardDef));
                item.put("statusIcon", periodSettings.enabled() ? "&a启用" : "&c停用");
                item.put("enabled", periodSettings.enabled());
                configs.add(item);
            }
        }

        return configs;
    }

    /**
     * 切换指定 Boss + 周期类型的排行榜奖励启用状态。
     *
     * @param configId   格式: bossId-rank
     * @param periodType "weekly" 或 "monthly"
     * @return 操作结果消息
     */
    public String toggleConfig(String configId, String periodType) {
        if (configId == null || configId.isBlank()) {
            return "§c无效的配置 ID";
        }

        int dashIndex = configId.lastIndexOf('-');
        if (dashIndex <= 0 || dashIndex >= configId.length() - 1) {
            return "§c无法解析配置 ID: " + configId;
        }
        String bossId = configId.substring(0, dashIndex);

        File bossFile = findBossFile(bossId);
        if (bossFile == null || !bossFile.exists()) {
            return "§c找不到 Boss 配置文件: " + bossId;
        }

        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(bossFile);
            String basePath = "damage-ranking.ranking-rewards." + periodType + ".enabled";
            boolean currentEnabled = yaml.getBoolean(basePath, false);
            yaml.set(basePath, !currentEnabled);
            yaml.save(bossFile);

            return (currentEnabled ? "§e已停用 " : "§a已启用 ") + bossId + " 的" + ("weekly".equals(periodType) ? "周" : "月") + "奖励";
        } catch (Exception e) {
            logger.severe("切换奖励配置失败: " + e.getMessage());
            return "§c切换失败: " + e.getMessage();
        }
    }

    /**
     * 保存奖励配置到 Boss yml 文件。
     *
     * @param configId          配置 ID（可为空，新建时为空）
     * @param rewardType        "weekly" 或 "monthly"
     * @param rankingType       排行类型（如 "boss_damage"）
     * @param bossId            Boss ID
     * @param rankStart         起始名次
     * @param rankEnd           结束名次
     * @param rewardName        奖励名称
     * @param rewardDescription 奖励描述
     * @param rewardMoney       金钱奖励
     * @param rewardDkp         DKP 奖励
     * @return 操作结果消息
     */
    public String saveConfig(String configId, String rewardType, String rankingType, String bossId,
                             int rankStart, int rankEnd, String rewardName, String rewardDescription,
                             int rewardMoney, int rewardDkp) {
        if (bossId == null || bossId.isBlank()) {
            return "§c缺少 Boss ID";
        }
        if (rankStart < 1 || rankEnd < rankStart) {
            return "§c名次范围无效";
        }
        if (!"weekly".equals(rewardType) && !"monthly".equals(rewardType)) {
            return "§c奖励周期类型无效: " + rewardType;
        }

        File bossFile = findBossFile(bossId);
        if (bossFile == null || !bossFile.exists()) {
            return "§c找不到 Boss 配置文件: " + bossId;
        }

        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(bossFile);
            String ranksBasePath = "damage-ranking.ranking-rewards." + rewardType + ".ranks";

            // 确保 enabled 字段存在
            String enabledPath = "damage-ranking.ranking-rewards." + rewardType + ".enabled";
            if (!yaml.contains(enabledPath)) {
                yaml.set(enabledPath, true);
            }

            // 为名次范围内的每个名次写入奖励配置
            for (int rank = rankStart; rank <= rankEnd; rank++) {
                String rankPath = ranksBasePath + "." + rank + ".actions";
                List<Map<String, Object>> actions = new ArrayList<>();

                if (rewardMoney > 0) {
                    Map<String, Object> action = new LinkedHashMap<>();
                    action.put("type", "command");
                    action.put("command", "points give {player} " + rewardMoney);
                    actions.add(action);
                }

                if (rewardDkp > 0) {
                    Map<String, Object> action = new LinkedHashMap<>();
                    action.put("type", "command");
                    action.put("command", "dkp give {player} " + rewardDkp);
                    actions.add(action);
                }

                if (rewardName != null && !rewardName.isBlank()) {
                    Map<String, Object> action = new LinkedHashMap<>();
                    action.put("type", "message");
                    action.put("target", "player");
                    action.put("text", "&6" + rewardName);
                    actions.add(action);
                }

                if (rewardDescription != null && !rewardDescription.isBlank()) {
                    Map<String, Object> action = new LinkedHashMap<>();
                    action.put("type", "message");
                    action.put("target", "player");
                    action.put("text", "&7" + rewardDescription);
                    actions.add(action);
                }

                if (actions.isEmpty()) {
                    yaml.set(rankPath, List.of());
                } else {
                    yaml.set(rankPath, actions);
                }
            }

            yaml.save(bossFile);
            return "§a配置保存成功: " + bossId + " " + ("weekly".equals(rewardType) ? "周" : "月") + "奖励 第" + rankStart + "-" + rankEnd + "名";
        } catch (IOException e) {
            logger.severe("保存奖励配置失败: " + e.getMessage());
            return "§c保存失败: " + e.getMessage();
        }
    }

    /**
     * 重试发放失败的记录。
     *
     * @param recordId 记录 ID
     * @return 操作结果消息
     */
    public String retryDistribution(int recordId) {
        try {
            RankingRewardRecord record = recordDao.findById(recordId).orElse(null);
            if (record == null) {
                return "§c找不到记录: " + recordId;
            }
            if (!"failed".equals(record.getStatus())) {
                return "§e该记录状态为 " + record.getStatus() + "，无需重试";
            }

            PluginConfiguration config = configurationSupplier.get();
            if (config == null) {
                return "§c插件配置尚未加载";
            }

            BossDefinition boss = config.findBoss(record.getBossId());
            if (boss == null) {
                return "§c找不到 Boss 配置: " + record.getBossId();
            }

            BossRankingRewardsSettings rankingRewards = boss.damageRanking().rankingRewards();
            BossDamageRankingRewardsSettings periodSettings = rankingRewards.forPeriod(record.getRewardType());
            int rank = record.getRank() != null ? record.getRank() : 0;
            BossDamageRankRewardDefinition rewardDef = periodSettings.rewardForRank(rank);
            if (rewardDef.actions().isEmpty()) {
                return "§c该名次无奖励配置";
            }

            // 解析目标玩家
            OfflinePlayer target = resolvePlayer(record.getPlayerUuid());
            if (target == null) {
                return "§c无法解析玩家: " + record.getPlayerName();
            }

            // 构建变量上下文
            Map<String, String> variables = new LinkedHashMap<>();
            variables.put("player", record.getPlayerName());
            variables.put("player_name", record.getPlayerName());
            variables.put("player_uuid", record.getPlayerUuid());
            variables.put("rank", String.valueOf(rank));
            variables.put("boss_id", boss.mythicMobId());
            variables.put("boss_display_name", boss.mythicMobId());
            variables.put("period_type", record.getRewardType());
            variables.put("period_type_display", "weekly".equals(record.getRewardType()) ? "周" : "月");

            // 在主线程执行奖励动作
            List<RewardActionResult> results = executeOnMainThread(rewardDef, target, variables, periodSettings);

            boolean allSuccess = results.stream().allMatch(RewardActionResult::success);
            String failureReason = results.stream()
                .filter(r -> !r.success())
                .map(r -> r.type() + ": " + r.message())
                .reduce((a, b) -> a + "; " + b)
                .orElse(null);

            record.setStatus(allSuccess ? "success" : "failed");
            record.setIssuedTime(LocalDateTime.now());
            record.setFailureReason(failureReason);
            record.setRetryCount((record.getRetryCount() != null ? record.getRetryCount() : 0) + 1);
            recordDao.update(record);

            return allSuccess
                ? "§a重试成功: " + record.getPlayerName() + " 第" + rank + "名"
                : "§c重试失败: " + failureReason;
        } catch (SQLException e) {
            logger.severe("重试发放失败: " + e.getMessage());
            return "§c重试失败: " + e.getMessage();
        }
    }

    // ─── 辅助方法 ──────────────────────────────────────────

    /**
     * 在 bosses 目录中查找指定 Boss 的 yml 文件。
     */
    private File findBossFile(String bossId) {
        if (bossesDirectory == null || bossId == null || bossId.isBlank()) {
            return null;
        }
        File ymlFile = new File(bossesDirectory, bossId + ".yml");
        if (ymlFile.exists()) {
            return ymlFile;
        }
        File yamlFile = new File(bossesDirectory, bossId + ".yaml");
        if (yamlFile.exists()) {
            return yamlFile;
        }
        // 大小写不敏感查找
        File[] files = bossesDirectory.listFiles((dir, name) ->
            name.equalsIgnoreCase(bossId + ".yml") || name.equalsIgnoreCase(bossId + ".yaml"));
        if (files != null && files.length > 0) {
            return files[0];
        }
        return null;
    }

    /**
     * 根据奖励动作列表构建奖励名称摘要。
     */
    private static String buildRewardName(BossDamageRankRewardDefinition rewardDef) {
        if (rewardDef.actions().isEmpty()) {
            return "无奖励";
        }
        for (BossDamageRewardAction action : rewardDef.actions()) {
            if (action.type() == BossDamageRewardActionType.MESSAGE && !action.text().isBlank()) {
                return action.text();
            }
        }
        return "第" + rewardDef.rank() + "名奖励";
    }

    /**
     * 根据奖励动作列表构建奖励预览摘要。
     */
    private static String buildRewardPreview(BossDamageRankRewardDefinition rewardDef) {
        if (rewardDef.actions().isEmpty()) {
            return "无";
        }
        List<String> parts = new ArrayList<>();
        for (BossDamageRewardAction action : rewardDef.actions()) {
            switch (action.type()) {
                case COMMAND -> {
                    if (!action.command().isBlank()) {
                        parts.add("指令");
                    }
                }
                case NEIGE_ITEMS, MYTHIC_ITEMS, OVERTURE_ITEMS -> {
                    if (!action.itemId().isBlank()) {
                        parts.add("物品×" + action.amount());
                    }
                }
                case MESSAGE -> parts.add("消息");
                case MAIL -> parts.add("邮件");
                case SIGNAL -> parts.add("信号");
            }
        }
        return parts.isEmpty() ? "无" : String.join(", ", parts);
    }
}



