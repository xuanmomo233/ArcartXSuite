package xuanmo.arcartxsuite.entitytracker.service;


import xuanmo.arcartxsuite.entitytracker.boss.config.BossDamageRankRewardDefinition;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDamageRankingRewardsSettings;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDamageRewardAction;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDamageRewardActionType;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDamageRewardMessageTarget;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDefinition;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossRankingRewardsSettings;
import xuanmo.arcartxsuite.entitytracker.boss.config.PluginConfiguration;
import xuanmo.arcartxsuite.entitytracker.boss.tracker.BossDamageSettlementRecord;
import xuanmo.arcartxsuite.entitytracker.dao.PeriodRankingDao;
import xuanmo.arcartxsuite.entitytracker.dao.RankingRewardRecordDao;
import xuanmo.arcartxsuite.entitytracker.entity.RankingRewardRecord;
import xuanmo.arcartxsuite.entitytracker.reward.BossRewardItems;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import xuanmo.arcartxsuite.api.mail.AxsMailService;
import xuanmo.arcartxsuite.api.scheduler.ModuleExecutor;
import java.util.logging.Logger;

/**
 * 排行榜定时奖励服务。
 * <p>
 * 奖励配置从各 Boss yml 文件的 {@code damage-ranking.ranking-rewards} 节点读取，
 * 排行数据从数据库查询，动作执行委托给 {@link RewardActionExecutor}。
 */
public class RankingRewardService {

    private static final String MAIL_ISSUING = "mail_issuing";

    private final Logger logger;
    private final RankingRewardRecordDao recordDao;
    private final PeriodRankingDao periodRankings;
    private final ModuleExecutor worker = new ModuleExecutor(1, 1, 0, java.util.concurrent.TimeUnit.MILLISECONDS,
            new java.util.concurrent.LinkedBlockingQueue<>(), task -> {
        Thread thread = new Thread(task, "AXS-EntityTracker-Ranking");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean active = true;
    private final java.util.Set<MainCall<?>> mainCalls = new java.util.HashSet<>();
    private final CompletableFuture<Void> retired = new CompletableFuture<>();
    private final xuanmo.arcartxsuite.api.scheduler.ModuleCallback uiCallbacks =
        new xuanmo.arcartxsuite.api.scheduler.ModuleCallback(() -> {});
    private final RewardActionExecutor actionExecutor;
    private final BossRewardItems itemRewards;
    private final Supplier<AxsMailService> mailProvider;
    private final boolean sharedRankingStorage;
    private final JavaPlugin plugin;
    private final java.util.function.Supplier<PluginConfiguration> configurationSupplier;
    private final File bossesDirectory;
    private final Map<PeriodToggle, Boolean> enabledOverrides = new ConcurrentHashMap<>();

    private record PeriodToggle(String bossId, String periodType) {}

    public RankingRewardService(
        DataSource dataSource,
        RewardActionExecutor actionExecutor,
        BossRewardItems itemRewards,
        Supplier<AxsMailService> mailProvider,
        boolean sharedRankingStorage,
        JavaPlugin plugin,
        Logger logger,
        java.util.function.Supplier<PluginConfiguration> configurationSupplier,
        File bossesDirectory
    ) {
        this.recordDao = new RankingRewardRecordDao(dataSource, plugin);
        this.periodRankings = new PeriodRankingDao(dataSource, recordDao);
        this.actionExecutor = actionExecutor;
        this.itemRewards = itemRewards;
        this.mailProvider = mailProvider;
        this.sharedRankingStorage = sharedRankingStorage;
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
    public CompletableFuture<Integer> distributeRewards(String periodType, ZonedDateTime periodStart,
                                                        ZonedDateTime periodEnd) {
        return submit(() -> {
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
                    if (!active || !boss.enabled() || !boss.damageRanking().enabled()) {
                        continue;
                    }

                    BossRankingRewardsSettings rankingRewards = boss.damageRanking().rankingRewards();
                    BossDamageRankingRewardsSettings periodSettings = rankingRewards.forPeriod(periodType);
                    if (!isPeriodEnabled(boss, periodType, periodSettings) || periodSettings.rankRewards().isEmpty()) {
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

    public void recordPeriodScores(BossDamageSettlementRecord settlement) {
        if (!active || settlement.entriesByPlayer().isEmpty()) return;
        try {
            worker.execute(() -> {
                try {
                    periodRankings.recordScores(settlement);
                } catch (SQLException failure) {
                    logger.log(java.util.logging.Level.WARNING, "保存周期伤害成绩失败: " + settlement.entityUuid(), failure);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException failure) {
            logger.warning("周期伤害记录服务已经关闭: " + settlement.entityUuid());
        }
    }

    public void shutdown() {
        List<MainCall<?>> pending;
        synchronized (mainCalls) {
            if (!active) return;
            active = false;
            pending = List.copyOf(mainCalls);
        }
        uiCallbacks.close();
        pending.forEach(MainCall::cancelBeforeStart);
        worker.shutdown();
        var mainStopped = CompletableFuture.allOf(
            pending.stream().map(call -> call.stopped).toArray(CompletableFuture[]::new));
        CompletableFuture.allOf(worker.stopped().toCompletableFuture(), mainStopped,
                uiCallbacks.stopped().toCompletableFuture()).whenComplete((ignored, failure) -> {
            if (failure == null) retired.complete(null);
            else retired.completeExceptionally(failure);
        });
    }

    public CompletionStage<Void> databaseStopped() { return retired.copy(); }

    public void onMainIfActive(Runnable action) {
        if (!active) return;
        try {
            Bukkit.getScheduler().runTask(plugin, () -> uiCallbacks.call(() -> { action.run(); return null; }, null));
        } catch (RuntimeException | LinkageError closing) {
            if (active) throw closing;
        }
    }

    private <T> CompletableFuture<T> submit(java.util.function.Supplier<T> action) {
        if (!active) return CompletableFuture.failedFuture(new IllegalStateException("EntityTracker ranking service closed"));
        try { return CompletableFuture.supplyAsync(action, worker).copy(); }
        catch (java.util.concurrent.RejectedExecutionException closing) { return CompletableFuture.failedFuture(closing); }
    }

    private int distributeRewardsForBoss(BossDefinition boss, BossDamageRankingRewardsSettings settings,
                                         String type, ZonedDateTime start, ZonedDateTime end) throws SQLException {
        int limit = settings.rankRewards().keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        if (limit <= 0) return 0;

        periodRankings.freeze(boss.mythicMobId(), type, start.toLocalDateTime(),
            start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli(), limit, standing -> {
                var actions = settings.rewardForRank(standing.rank()).actions();
                if (actions.isEmpty()) return null;
                var record = new RankingRewardRecord(-1, type, "boss_damage",
                    start.toLocalDateTime(), end.toLocalDateTime(), standing.playerId(), standing.playerName(),
                    standing.rank(), standing.score());
                record.setBossId(boss.mythicMobId());
                record.setRewardPlan(encodePlan(standing.bossName(), actions));
                return record;
            });
        int sent = 0;
        for (var record : recordDao.pendingPeriod(boss.mythicMobId(), type, start.toInstant().toEpochMilli())) {
            if (!active) break;
            if ("success".equals(issue(record, false))) sent++;
        }
        return sent;
    }

    private String issue(RankingRewardRecord record, boolean retry) throws SQLException {
        boolean resumingMail = MAIL_ISSUING.equals(record.getStatus());
        if (record.getRewardConfigId() != -1 || !("pending".equals(record.getStatus()) || resumingMail)) return record.getStatus();
        YamlConfiguration plan = new YamlConfiguration();
        try {
            plan.loadFromString(record.getRewardPlan());
        } catch (org.bukkit.configuration.InvalidConfigurationException failure) {
            throw new IllegalStateException("无法读取冻结的排行榜奖励计划 " + record.getId(), failure);
        }
        var actions = plan.getMapList("actions").stream().map(BossDamageRewardAction::from).toList();
        UUID playerId = UUID.fromString(record.getPlayerUuid());
        Map<String, String> variables = buildVariables(record);
        variables.put("boss_display_name", plan.getString("boss-name", record.getBossId()));
        for (int step = record.getNextAction(); active && step < actions.size(); step++) {
            var action = actions.get(step);
            if (action == null) throw new IllegalStateException("未知奖励动作 " + (step + 1));
            boolean mailStep = action.type() == BossDamageRewardActionType.MAIL;
            AxsMailService mail = mailStep ? mailProvider.get() : null;
            if (mailStep && mail == null) return "mail_unavailable";
            boolean replayable = mailStep && (!sharedRankingStorage || mail.usesSharedHostStorage());
            if (resumingMail && (!replayable || record.getClaimAttempt() == null)) return MAIL_ISSUING;
            if (needsOnlinePlayer(action) && !onMain(() -> Bukkit.getPlayer(playerId) != null,false)) return "pending";
            String inProgress = replayable ? MAIL_ISSUING : "issuing";
            String previousAttempt = resumingMail ? record.getClaimAttempt() : null;
            String attempt = resumingMail ? previousAttempt : UUID.randomUUID().toString();
            String expected = resumingMail ? MAIL_ISSUING : "pending";
            if (!recordDao.updateAction(record.getId(), step, expected, inProgress, step, null, retry,
                previousAttempt, attempt)) return "busy";
            retry = false;
            resumingMail = false;
            String unconfirmed = replayable ? MAIL_ISSUING : "review";
            RewardActionResult result;
            try {
                result = switch (action.type()) {
                    case MAIL -> sendPreset(mail, playerId, action, attempt);
                    case ITEM -> sendItem(playerId, action, attempt, record.getBossId());
                    default -> onMain(() -> {
                        if (!active) return null;
                        OfflinePlayer target = needsOnlinePlayer(action) ? Bukkit.getPlayer(playerId) : Bukkit.getOfflinePlayer(playerId);
                        return target == null ? null : actionExecutor.executeAction(action, target, variables);
                    }, null);
                };
            } catch (RuntimeException | LinkageError failure) {
                recordDao.updateAction(record.getId(), step, inProgress, unconfirmed, step, failure.toString(), false,
                    attempt, attempt);
                throw failure;
            }
            if (result == null) {
                recordDao.updateAction(record.getId(), step, inProgress, "pending", step, null, false, attempt, null);
                return "pending";
            }
            if (!result.success()) {
                if (!recordDao.updateAction(record.getId(), step, inProgress, unconfirmed, step, result.message(), false,
                    attempt, attempt)) return "busy";
                logger.warning("排行榜奖励未确认: id=" + record.getId() + " action=" + (step + 1)
                    + " attempt=" + attempt + " " + result.message());
                return unconfirmed;
            }
            boolean finished = step + 1 == actions.size();
            if (!recordDao.updateAction(record.getId(), step, inProgress, finished ? "success" : "pending",
                step + 1, null, false, attempt, null)) return replayable ? "busy" : "review";
            if (finished) return "success";
        }
        return "pending";
    }

    private static RewardActionResult sendPreset(AxsMailService mail, UUID recipient, BossDamageRewardAction action,
                                                  String attempt) {
        if (action.presetId().isBlank()) return RewardActionResult.fail("mail", "缺少 preset-id");
        try {
            var receipt = mail.sendPresetDurable(recipient, action.presetId(), "entitytracker", "period-ranking-mail:" + attempt)
                .get(15, TimeUnit.SECONDS);
            return receipt.mailId() > 0 ? RewardActionResult.ok("mail", "邮件已入库")
                : RewardActionResult.fail("mail", "预设不可用或邮件未入库");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            return RewardActionResult.fail("mail", "等待邮件回执时中断");
        } catch (ExecutionException | TimeoutException failure) {
            return RewardActionResult.fail("mail", "邮件入库回执未确认");
        }
    }

    private RewardActionResult sendItem(UUID recipient, BossDamageRewardAction action, String attempt, String bossId) {
        var prepared = onMain(() -> {
            var player = Bukkit.getPlayer(recipient);
            return active && player != null && player.isOnline()
                ? itemRewards.prepareOnline(player, action, "ranking:" + bossId) : null;
        }, null);
        if (prepared == null) return null;
        if (!prepared.requiresStorage()) return prepared.result();
        var result = itemRewards.persist("period-ranking-item:" + attempt, prepared.pendingReward());
        if (result.success()) {
            try { onMainIfActive(() -> itemRewards.flushOnline(recipient)); }
            catch (RuntimeException failure) {
                logger.fine("排行榜奖励已入库，在线补发调度失败: " + failure.getMessage());
            }
        }
        return result;
    }

    private static String encodePlan(String bossName, List<BossDamageRewardAction> actions) {
        YamlConfiguration plan = new YamlConfiguration();
        plan.set("boss-name", bossName);
        plan.set("actions", actions.stream().map(action -> {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("type", action.type().configKey());
            values.put("source", action.source());
            values.put("id", action.id());
            values.put("amount", action.amount());
            if (action.nbt() != null) values.put("nbt", action.nbt());
            values.put("command", action.command());
            if (action.target() != null) values.put("target", action.target().configKey());
            values.put("text", action.text());
            values.put("preset-id", action.presetId());
            values.put("signal", action.signal());
            return values;
        }).toList());
        return plan.saveToString();
    }

    private static boolean needsOnlinePlayer(BossDamageRewardAction action) {
        return action.type() == BossDamageRewardActionType.ITEM || action.type() == BossDamageRewardActionType.SIGNAL
            || (action.type() == BossDamageRewardActionType.MESSAGE
                && (action.target() == null || action.target() == BossDamageRewardMessageTarget.PLAYER));
    }

    private <T> T onMain(java.util.concurrent.Callable<T> operation, T unavailable) {
        MainCall<T> call;
        synchronized (mainCalls) {
            if (!active) return unavailable;
            call = new MainCall<>(operation, unavailable);
            mainCalls.add(call);
        }
        try {
            if (Bukkit.isPrimaryThread()) call.run();
            else Bukkit.getScheduler().runTask(plugin, call);
            return call.result.get(15, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            if (call.cancelBeforeStart()) return unavailable;
            throw new IllegalStateException("排行榜奖励调度被中断", failure);
        } catch (Exception failure) {
            if (call.cancelBeforeStart()) return unavailable;
            throw new IllegalStateException("排行榜奖励主线程调度失败", failure);
        } catch (LinkageError failure) {
            if (call.cancelBeforeStart()) return unavailable;
            throw failure;
        }
    }

    private final class MainCall<T> implements Runnable {
        private final java.util.concurrent.Callable<T> operation;
        private final T unavailable;
        private final CompletableFuture<T> result = new CompletableFuture<>();
        private final CompletableFuture<Void> stopped = new CompletableFuture<>();
        private boolean claimed;
        private boolean cancelled;

        MainCall(java.util.concurrent.Callable<T> operation, T unavailable) {
            this.operation = operation;
            this.unavailable = unavailable;
        }

        @Override public void run() {
            synchronized (this) {
                if (claimed) return;
                claimed = true;
            }
            try { result.complete(operation.call()); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
            finally { finish(); }
        }

        boolean cancelBeforeStart() {
            synchronized (this) {
                if (claimed) return cancelled;
                claimed = true;
                cancelled = true;
            }
            try { result.complete(unavailable); }
            finally { finish(); }
            return true;
        }

        private void finish() {
            synchronized (mainCalls) { mainCalls.remove(this); }
            stopped.complete(null);
        }
    }

    private static Map<String, String> buildVariables(RankingRewardRecord record) {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("player", record.getPlayerName());
        variables.put("player_name", record.getPlayerName());
        variables.put("uuid", record.getPlayerUuid());
        variables.put("player_uuid", record.getPlayerUuid());
        variables.put("rank", String.valueOf(record.getRank()));
        variables.put("damage", String.valueOf(record.getScore()));
        variables.put("boss_id", record.getBossId());
        variables.put("boss_display_name", record.getBossId());
        variables.put("period_type", record.getRewardType());
        variables.put("period_type_display", "weekly".equals(record.getRewardType()) ? "周" : "月");
        return variables;
    }

    public CompletableFuture<List<RankingRewardRecord>> getPlayerRewardHistory(UUID playerUuid) {
        return submit(() -> {
            try {
                return recordDao.findByPlayerUuid(playerUuid.toString());
            } catch (SQLException failure) {
                throw new IllegalStateException("获取玩家奖励历史失败", failure);
            }
        });
    }

    /**
     * 获取奖励发放记录
     */
    public CompletableFuture<List<RankingRewardRecord>> getRewardRecords(String rewardType,
                                                                        String status,
                                                                        LocalDateTime startTime) {
        return submit(() -> {
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
            boolean enabled = isPeriodEnabled(boss, periodType, periodSettings);

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
                item.put("statusIcon", enabled ? "&a启用" : "&c停用");
                item.put("enabled", enabled);
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
    public CompletableFuture<String> toggleConfig(String configId, String periodType) {
        return submit(() -> {
            var target = findToggle(configId, periodType);
            if (target == null) return "§c无效的奖励配置";
            Path bossFile = findBossFile(target.bossId());
            if (bossFile == null) return "§c找不到 Boss 配置文件: " + target.bossId();
            try {
                var yaml = new YamlConfiguration();
                yaml.load(bossFile.toFile());
                String flag = "damage-ranking.ranking-rewards." + periodType + ".enabled";
                boolean next = !yaml.getBoolean(flag, false);
                yaml.set(flag, next);
                writeBossFile(bossFile, yaml.saveToString());
                enabledOverrides.put(target, next);
                return (next ? "§a已启用 " : "§e已停用 ") + target.bossId() + " 的"
                    + ("weekly".equals(periodType) ? "周" : "月") + "奖励";
            } catch (IOException | org.bukkit.configuration.InvalidConfigurationException failure) {
                logger.log(java.util.logging.Level.WARNING, "切换奖励配置失败: " + target.bossId(), failure);
                return "§c保存 Boss 奖励配置失败";
            }
        });
    }

    private boolean isPeriodEnabled(BossDefinition boss, String periodType, BossDamageRankingRewardsSettings settings) {
        return enabledOverrides.getOrDefault(new PeriodToggle(boss.mythicMobId(), periodType), settings.enabled());
    }

    private PeriodToggle findToggle(String configId, String periodType) {
        if (configId == null || !("weekly".equals(periodType) || "monthly".equals(periodType))) return null;
        int separator = configId.lastIndexOf('-');
        if (separator <= 0 || separator == configId.length() - 1) return null;
        PluginConfiguration configuration = configurationSupplier.get();
        BossDefinition boss = configuration == null ? null : configuration.findBoss(configId.substring(0, separator));
        if (boss == null || !boss.enabled() || !boss.damageRanking().enabled()) return null;
        try {
            int rank = Integer.parseInt(configId.substring(separator + 1));
            var rewards = boss.damageRanking().rankingRewards().forPeriod(periodType);
            return rewards.rankRewards().containsKey(rank) ? new PeriodToggle(boss.mythicMobId(), periodType) : null;
        } catch (NumberFormatException invalidRank) {
            return null;
        }
    }

    private static void writeBossFile(Path bossFile, String content) throws IOException {
        Path temporary = Files.createTempFile(bossFile.getParent(), ".ranking-", ".tmp");
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            Files.move(temporary, bossFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public CompletableFuture<String> retryDistribution(int recordId) {
        return submit(() -> {
            try {
                var record = recordDao.findById(recordId).orElse(null);
                if (record == null) return "§c找不到记录: " + recordId;
                String status = issue(record, true);
                return switch (status) {
                    case "success" -> "§a发放完成: " + record.getPlayerName() + " 第" + record.getRank() + "名";
                    case "pending" -> "§e奖励仍待发放，需在目标玩家在线的子服重试。";
                    case MAIL_ISSUING -> "§e邮件仍待确认，请在可访问原邮件库的子服重试。";
                    case "mail_unavailable" -> "§eMail 模块未就绪。";
                    case "busy" -> "§e该奖励正在发放。";
                    default -> "§e该奖励状态为 " + status + "，请核对记录后处理。";
                };
            } catch (SQLException | RuntimeException failure) {
                logger.log(java.util.logging.Level.WARNING, "排行榜奖励处理失败: " + recordId, failure);
                return "§c发放失败，请核对记录 " + recordId;
            }
        });
    }

    public CompletableFuture<String> inspectReward(int id) {
        return submit(() -> {
            try {
                var record = recordDao.findById(id).orElse(null);
                if (record == null) return "§c找不到记录: " + id;
                return "§e记录 " + id + " 玩家=" + record.getPlayerUuid() + " 状态=" + record.getStatus()
                    + " 步骤=" + (record.getNextAction() + 1) + " 尝试编号=" + record.getClaimAttempt()
                    + " 原因=" + record.getFailureReason();
            } catch (SQLException failure) {
                logger.log(java.util.logging.Level.WARNING, "读取排行榜奖励失败: " + id, failure);
                return "§c读取记录失败。";
            }
        });
    }

    public CompletableFuture<String> resolveReviewedAction(int id, int actionNumber, UUID attempt, boolean delivered) {
        return submit(() -> {
            try {
                var record = recordDao.findById(id).orElse(null);
                if (record == null || record.getRewardPlan() == null
                    || !List.of("review", "issuing").contains(record.getStatus())
                    || actionNumber != record.getNextAction() + 1
                    || !attempt.toString().equals(record.getClaimAttempt())) {
                    return "§c记录不处于指定的待核对步骤。";
                }
                if ("issuing".equals(record.getStatus())
                    && (record.getIssuedTime() == null || record.getIssuedTime().plusMinutes(1).isAfter(LocalDateTime.now()))) {
                    return "§e发放尝试仍可能在运行，请待执行者停止后再核对。";
                }
                var plan = new YamlConfiguration();
                plan.loadFromString(record.getRewardPlan());
                boolean complete = delivered && actionNumber == plan.getMapList("actions").size();
                int next = delivered ? actionNumber : actionNumber - 1;
                if (!recordDao.updateAction(id, actionNumber - 1, record.getStatus(), complete ? "success" : "pending",
                    next, null, false, attempt.toString(), null)) return "§c记录已变化，请重新查询。";
                logger.info("控制台核对排行榜奖励: id=" + id + " action=" + actionNumber + " delivered=" + delivered);
                return complete ? "§a奖励已核对完成。" : "§a已登记核对结果，可以重试剩余动作。";
            } catch (SQLException | org.bukkit.configuration.InvalidConfigurationException failure) {
                logger.log(java.util.logging.Level.WARNING, "核对排行榜奖励失败: " + id, failure);
                return "§c保存核对结果失败。";
            }
        });
    }

    // ─── 辅助方法 ──────────────────────────────────────────

    /**
     * 在 bosses 目录中查找指定 Boss 的 yml 文件。
     */
    private Path findBossFile(String bossId) {
        if (bossesDirectory == null) return null;
        Path directory = bossesDirectory.toPath().toAbsolutePath().normalize();
        Path yml = directory.resolve(bossId + ".yml").normalize();
        return directory.equals(yml.getParent()) && Files.isRegularFile(yml, LinkOption.NOFOLLOW_LINKS) ? yml : null;
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
                case ITEM -> {
                    if (!action.id().isBlank()) {
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



