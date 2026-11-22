package xuanmo.arcartxsuite.entitytracker.boss.tracker;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDamageRankRewardDefinition;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDamageRankingRewardsSettings;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDamageRewardAction;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDamageRewardActionType;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDamageRewardMessageTarget;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.api.command.ThrottledCommandExecutor;
import xuanmo.arcartxsuite.api.item.PendingRewardService;
import xuanmo.arcartxsuite.api.mail.AxsMailService;
import xuanmo.arcartxsuite.api.placeholder.PlaceholderResolverAPI;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.api.scheduler.ModuleExecutor;
import xuanmo.arcartxsuite.entitytracker.reward.BossRewardItems;
import xuanmo.arcartxsuite.entitytracker.reward.RewardActionResult;
import java.util.logging.Logger;

/**
 * Boss 伤害结算服务，属于 entitytracker 模块的 boss 伤害结算子系统。
 * <p>
 * 负责 Boss 死亡后的伤害排名结算、奖励动作执行（物品发放、命令执行、消息发送、
 * 邮件发送、信号派发）、结算记录缓存与查询，以及奖励补发功能。
 */
final class BossDamageSettlementService {

    private static final int MAX_SETTLEMENT_CACHE_SIZE = 100;
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([a-zA-Z0-9_]+)}");
    private static final DecimalFormat NUMBER_FORMAT = new DecimalFormat("0.##");

    private final JavaPlugin plugin;
    private final Logger logger;
    private final java.util.function.Supplier<AxsMailService> mailProvider;
    private final java.util.function.BiConsumer<String, org.bukkit.entity.Player> signalDispatcher;
    private final BossRewardItems itemRewards;
    private final MessageProvider messages;
    private final Map<String, BossDamageSettlementRecord> settlementsById = new LinkedHashMap<>();
    private final Map<UUID, BossDamagePlayerSettlementView> lastSettlementByPlayer = new LinkedHashMap<>();
    private final Set<String> issuingSettlements = new HashSet<>();
    private final Set<String> reissues = new HashSet<>();
    private final Set<CompletableFuture<?>> rewardReceipts = ConcurrentHashMap.newKeySet();
    private final CompletableFuture<Void> retired = new CompletableFuture<>();
    private final ModuleExecutor rewardStorage = new ModuleExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(256), task -> {
            var thread = new Thread(task, "EntityTracker-BossRewardStorage");
            thread.setDaemon(true);
            return thread;
        });

    private final PlaceholderResolverAPI placeholderResolver;
    private volatile boolean closed;
    private long nextSettlementId;

    public BossDamageSettlementService(JavaPlugin plugin,
        Logger logger, ItemSourceRegistry itemSourceRegistry, PlaceholderResolverAPI placeholderResolver) {
        this(plugin, logger, () -> null, null, itemSourceRegistry, placeholderResolver, null, null);
    }

    public BossDamageSettlementService(
        JavaPlugin plugin,
        Logger logger,
        java.util.function.Supplier<AxsMailService> mailProvider,
        java.util.function.BiConsumer<String, org.bukkit.entity.Player> signalDispatcher,
        ItemSourceRegistry itemSourceRegistry,
        PlaceholderResolverAPI placeholderResolver,
        MessageProvider messages,
        PendingRewardService pendingRewardService
    ) {
        this.plugin = plugin;
        this.logger = logger;
        this.mailProvider = mailProvider == null ? () -> null : mailProvider;
        this.signalDispatcher = signalDispatcher;
        this.itemRewards = new BossRewardItems(itemSourceRegistry, pendingRewardService);
        this.placeholderResolver = placeholderResolver;
        this.messages = messages;
    }

    /**
     * 关闭结算服务，清空所有缓存。
     */
    public void shutdown() {
        if (closed) return;
        closed = true;
        rewardStorage.shutdown();
        var draining = new ArrayList<CompletableFuture<?>>(rewardReceipts);
        draining.add(rewardStorage.stopped().toCompletableFuture());
        CompletableFuture.allOf(draining.toArray(CompletableFuture[]::new))
            .whenComplete((ignored, failure) -> retired.complete(null));
        settlementsById.clear();
        lastSettlementByPlayer.clear();
        issuingSettlements.clear();
        reissues.clear();
    }

    CompletionStage<Void> databaseStopped() { return retired.copy(); }

    private <T> CompletableFuture<T> trackReceipts(CompletableFuture<T> receipts) {
        if (!receipts.isDone()) {
            rewardReceipts.add(receipts);
            receipts.whenComplete((ignored, failure) -> rewardReceipts.remove(receipts));
        }
        return receipts;
    }

    private void onMain(Runnable callback) {
        if (closed) throw new RejectedExecutionException("Boss 奖励结算已停用");
        if (Bukkit.isPrimaryThread()) callback.run();
        else Bukkit.getScheduler().runTask(plugin, callback);
    }

    /**
     * 对指定 Boss 会话执行伤害结算并发放奖励。
     *
     * @param session Boss 会话
     * @param entity  Boss 实体
     * @return 结算记录，会话为空或已结算时返回 {@code null}
     */
    public BossDamageSettlementRecord settle(BossSession session, org.bukkit.entity.LivingEntity entity) {
        if (closed || session == null || entity == null || !session.markSettled()) {
            return null;
        }

        BossSession.BossSettlementSnapshot snapshot = session.createSettlementSnapshot(entity);
        BossDamageRankingSnapshot ranking = snapshot.ranking();
        String settlementId = nextSettlementId();
        long settledAtMillis = System.currentTimeMillis();
        boolean rewardsEnabled = snapshot.rewards().enabled();

        List<BossDamageSettlementEntry> provisionalEntries = new ArrayList<>(ranking.trackedEntries().size());
        Map<UUID, BossDamageSettlementEntry> provisionalEntriesByPlayer = new LinkedHashMap<>();
        for (BossDamageRankingEntry rankingEntry : ranking.trackedEntries()) {
            String reason = rankingEntry.rank() <= 0 ? "未达最低伤害阈值" : rewardsEnabled ? "" : "奖励未启用";
            BossDamageSettlementEntry entry = BossDamageSettlementEntry.fromRankingEntry(rankingEntry, false, reason, List.of());
            provisionalEntries.add(entry);
            provisionalEntriesByPlayer.put(entry.playerUuid(), entry);
        }

        BossDamageSettlementRecord provisionalRecord = new BossDamageSettlementRecord(
            settlementId,
            settledAtMillis,
            snapshot.mythicMobId(),
            snapshot.displayName(),
            snapshot.entityUuid().toString(),
            ranking.participantCount(),
            ranking.trackedPlayerCount(),
            ranking.totalDamage(),
            snapshot.rewards(),
            List.copyOf(provisionalEntries),
            Map.copyOf(provisionalEntriesByPlayer)
        );

        cacheSettlement(provisionalRecord);
        if (!rewardsEnabled) return provisionalRecord;
        issuingSettlements.add(settlementId);
        var outcomes = new ArrayList<CompletableFuture<BossDamageSettlementEntry>>(provisionalEntries.size());
        for (BossDamageSettlementEntry entry : provisionalEntries) {
            outcomes.add(executeRewardActions(provisionalRecord, entry, null, true, "kill:" + snapshot.entityUuid()));
        }
        trackReceipts(CompletableFuture.allOf(outcomes.toArray(CompletableFuture[]::new))).whenCompleteAsync((ignored, failure) -> {
            if (closed) return;
            issuingSettlements.remove(settlementId);
            if (failure != null) {
                logger.log(java.util.logging.Level.WARNING, "Boss 奖励结算未完成: " + settlementId, failure);
                return;
            }
            publishRewardResults(provisionalRecord, outcomes);
        }, this::onMain);
        return settlementsById.getOrDefault(settlementId, provisionalRecord);
    }

    private void publishRewardResults(BossDamageSettlementRecord record,
                                      List<CompletableFuture<BossDamageSettlementEntry>> outcomes) {
        if (settlementsById.get(record.settlementId()) != record) return;
        var entries = new ArrayList<BossDamageSettlementEntry>(outcomes.size());
        var entriesByPlayer = new LinkedHashMap<UUID, BossDamageSettlementEntry>();
        for (var outcome : outcomes) {
            var entry = outcome.getNow(null);
            entries.add(entry);
            entriesByPlayer.put(entry.playerUuid(), entry);
        }
        BossDamageSettlementRecord finalRecord = new BossDamageSettlementRecord(
            record.settlementId(), record.settledAtMillis(), record.mythicMobId(), record.bossDisplayName(),
            record.entityUuid(), record.participantCount(), record.trackedPlayerCount(), record.totalDamage(),
            record.rewardsSettings(), List.copyOf(entries), Map.copyOf(entriesByPlayer)
        );
        settlementsById.put(record.settlementId(), finalRecord);
        for (var entry : entries) {
            var previous = lastSettlementByPlayer.get(entry.playerUuid());
            if (previous != null && previous.settlement() == record) {
                lastSettlementByPlayer.put(entry.playerUuid(), new BossDamagePlayerSettlementView(finalRecord, entry));
            }
        }
    }

    boolean issuing(String settlementId) { return issuingSettlements.contains(settlementId); }

    /**
     * 返回所有结算记录（按时间倒序）。
     *
     * @return 结算记录列表
     */
    public List<BossDamageSettlementRecord> settlements() {
        List<BossDamageSettlementRecord> records = new ArrayList<>(settlementsById.values());
        java.util.Collections.reverse(records);
        return List.copyOf(records);
    }

    /**
     * 按结算 ID 获取结算记录。
     *
     * @param settlementId 结算 ID
     * @return 结算记录，未找到时返回 {@code null}
     */
    public BossDamageSettlementRecord settlement(String settlementId) {
        if (settlementId == null || settlementId.isBlank()) {
            return null;
        }
        return settlementsById.get(settlementId);
    }

    /**
     * 获取玩家最近一次结算的视图。
     *
     * @param playerUuid 玩家 UUID
     * @return 结算视图，无记录时返回空视图
     */
    public BossDamagePlayerSettlementView lastSettlement(UUID playerUuid) {
        if (playerUuid == null) {
            return BossDamagePlayerSettlementView.empty();
        }
        return lastSettlementByPlayer.getOrDefault(playerUuid, BossDamagePlayerSettlementView.empty());
    }

    /**
     * 返回所有结算 ID（按时间倒序）。
     *
     * @return 结算 ID 列表
     */
    public List<String> settlementIds() {
        List<String> ids = new ArrayList<>(settlementsById.keySet());
        java.util.Collections.reverse(ids);
        return List.copyOf(ids);
    }

    /**
     * 对指定结算的指定名次补发奖励。
     *
     * @param settlementId    结算 ID
     * @param rank            名次
     * @param overridePlayer  覆盖目标玩家（为 {@code null} 时使用原玩家）
     * @return 奖励发放结果
     */
    public CompletionStage<BossDamageRewardDispatchResult> reissueReward(String settlementId, int rank, OfflinePlayer overridePlayer) {
        BossDamageSettlementRecord record = settlement(settlementId);
        if (record == null) {
            return CompletableFuture.completedFuture(BossDamageRewardDispatchResult.failure(messages.get("admin.reissue.record-not-found", settlementId)));
        }
        BossDamageSettlementEntry entry = record.entry(rank);
        if (entry.rank() <= 0) {
            return CompletableFuture.completedFuture(BossDamageRewardDispatchResult.failure(messages.get("admin.reissue.rank-not-found", rank)));
        }
        UUID recipient = overridePlayer == null ? entry.playerUuid() : overridePlayer.getUniqueId();
        String key = settlementId + ":" + rank + ":" + recipient;
        if (issuing(settlementId) || !reissues.add(key)) {
            return CompletableFuture.completedFuture(BossDamageRewardDispatchResult.failure(messages.get("admin.reissue.pending")));
        }
        String targetName = resolveTargetName(overridePlayer, entry);
        return trackReceipts(executeRewardActions(record, entry, overridePlayer, false, "reissue:" + UUID.randomUUID()))
            .handleAsync((result, failure) -> {
                reissues.remove(key);
                if (failure != null) return BossDamageRewardDispatchResult.failure(messages.get("admin.reissue.failure", failure.getMessage()));
                if (result.rewarded()) return BossDamageRewardDispatchResult.success(messages.get("admin.reissue.success", settlementId, rank, targetName));
                return BossDamageRewardDispatchResult.failure(messages.get("admin.reissue.failure",
                    result.rewardFailure().isBlank() ? "未知错误" : result.rewardFailure()));
            }, this::onMain);
    }

    private CompletableFuture<BossDamageSettlementEntry> executeRewardActions(
        BossDamageSettlementRecord record,
        BossDamageSettlementEntry entry,
        OfflinePlayer overridePlayer,
        boolean logFailures,
        String operationId
    ) {
        if (entry == null) {
            return CompletableFuture.completedFuture(BossDamageSettlementEntry.empty());
        }
        if (entry.rank() <= 0) {
            return CompletableFuture.completedFuture(entryResult(entry, false, "未达最低伤害阈值", List.of()));
        }

        BossDamageRankingRewardsSettings rewardsSettings = record.rewardsSettings();
        if (!rewardsSettings.enabled()) {
            return CompletableFuture.completedFuture(entryResult(entry, false, "奖励未启用", List.of()));
        }

        BossDamageRankRewardDefinition rankReward = rewardsSettings.rewardForRank(entry.rank());
        if (rankReward.actions().isEmpty()) {
            return CompletableFuture.completedFuture(entryResult(entry, false, "未配置奖励", List.of()));
        }

        UUID recipient = overridePlayer != null ? overridePlayer.getUniqueId() : entry.playerUuid();
        String requestPrefix = "boss-rank:" + operationId + ":" + recipient + ":";
        var actions = new ArrayList<CompletableFuture<BossDamageSettlementActionResult>>(rankReward.actions().size());
        for (int step = 0; step < rankReward.actions().size(); step++) {
            var action = rankReward.actions().get(step);
            actions.add(performAction(action, recipient, record, entry, requestPrefix + step).toCompletableFuture());
        }
        return CompletableFuture.allOf(actions.toArray(CompletableFuture[]::new)).thenApply(ignored ->
            summarizeActions(record, entry, actions.stream().map(action -> action.getNow(null)).toList(), logFailures));
    }

    private CompletionStage<BossDamageSettlementActionResult> performAction(BossDamageRewardAction action, UUID recipient,
            BossDamageSettlementRecord record, BossDamageSettlementEntry entry, String requestKey) {
        String type = action == null || action.type() == null ? "unknown" : action.type().configKey();
        try {
            if (closed) return CompletableFuture.completedFuture(new BossDamageSettlementActionResult(type, false, "结算已停用"));
            if (action != null && action.type() == BossDamageRewardActionType.MAIL) {
                return executeMailRewardAction(action, recipient, requestKey)
                    .exceptionally(failure -> storageFailure(type, requestKey, failure));
            }
            Player player = Bukkit.getPlayer(recipient);
            if (action != null && action.type() == BossDamageRewardActionType.ITEM) {
                String detail = "settlement:" + record.settlementId() + ":rank" + entry.rank();
                var prepared = player != null && player.isOnline()
                    ? itemRewards.prepareOnline(player, action, detail)
                    : itemRewards.prepareOffline(recipient, action, detail);
                return deliverItem(prepared, recipient, requestKey)
                    .exceptionally(failure -> storageFailure(type, requestKey, failure));
            }
            OfflinePlayer target = player != null ? player : Bukkit.getOfflinePlayer(recipient);
            if (action != null && action.type() == BossDamageRewardActionType.COMMAND) {
                return executeCommandAction(action, target, record, entry);
            }
            return CompletableFuture.completedFuture(executeAction(action, target, record, entry));
        } catch (RuntimeException | LinkageError failure) {
            return CompletableFuture.completedFuture(storageFailure(type, requestKey, failure));
        }
    }

    private BossDamageSettlementActionResult storageFailure(String type, String requestKey, Throwable failure) {
        logger.log(java.util.logging.Level.WARNING, "Boss 奖励执行失败: " + requestKey, failure);
        return new BossDamageSettlementActionResult(type, false, "奖励未确认，请核对后再补发");
    }

    private BossDamageSettlementEntry summarizeActions(BossDamageSettlementRecord record, BossDamageSettlementEntry entry,
            List<BossDamageSettlementActionResult> actionResults, boolean logFailures) {
        List<String> failures = new ArrayList<>();
        boolean allSuccessful = true;
        for (BossDamageSettlementActionResult result : actionResults) {
            if (!result.success()) {
                allSuccessful = false;
                if (!result.message().isBlank()) {
                    failures.add(result.message());
                }
                if (logFailures) {
                    this.logger.warning(
                        "EntityTracker 伤害排行奖励执行失败"
                            + " | settlement=" + record.settlementId()
                            + " | boss=" + record.mythicMobId()
                            + " | rank=" + entry.rank()
                            + " | player=" + entry.playerName()
                            + " | action=" + result.type()
                            + " | reason=" + result.message()
                    );
                }
            }
        }

        return entryResult(entry, allSuccessful, String.join("；", failures), actionResults);
    }

    private static BossDamageSettlementEntry entryResult(BossDamageSettlementEntry entry, boolean rewarded,
            String failure, List<BossDamageSettlementActionResult> results) {
        return BossDamageSettlementEntry.fromRankingEntry(toRankingEntry(entry), rewarded, failure, List.copyOf(results));
    }

    private BossDamageSettlementActionResult executeAction(
        BossDamageRewardAction action,
        OfflinePlayer target,
        BossDamageSettlementRecord record,
        BossDamageSettlementEntry entry
    ) {
        if (action == null || action.type() == null) {
            return new BossDamageSettlementActionResult("unknown", false, "奖励动作类型无效");
        }

        return switch (action.type()) {
            case ITEM -> throw new IllegalStateException("物品奖励必须走受控投递");
            case COMMAND -> throw new IllegalStateException("命令必须等待执行回执");
            case MESSAGE -> executeMessageAction(action, target, record, entry);
            case MAIL -> throw new IllegalStateException("预设邮件必须异步确认");
            case SIGNAL -> executeSignalRewardAction(action, target, record, entry);
        };
    }

    private CompletionStage<BossDamageSettlementActionResult> executeCommandAction(
        BossDamageRewardAction action,
        OfflinePlayer target,
        BossDamageSettlementRecord record,
        BossDamageSettlementEntry entry
    ) {
        if (action.command().isBlank()) {
            return CompletableFuture.completedFuture(new BossDamageSettlementActionResult(
                action.type().configKey(), false, "缺少 command"));
        }

        String rendered = renderTemplate(action.command(), target, record, entry);
        // 占位符/PAPI 展开后统一校验最终命令：context.resolve 与 PAPI 值可能携带分隔符
        try {
            rendered = xuanmo.arcartxsuite.api.command.CommandSanitizer.validateExpandedCommand(rendered);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(new BossDamageSettlementActionResult(
                action.type().configKey(), false, "命令含分隔符已拒绝: " + rendered));
        }
        String command = stripLeadingSlash(rendered);
        var receipt = new CompletableFuture<BossDamageSettlementActionResult>();
        ThrottledCommandExecutor.dispatchWithResult(command, () -> !closed, result -> receipt.complete(
            new BossDamageSettlementActionResult(action.type().configKey(),
                result == ThrottledCommandExecutor.Result.SUCCESS, switch (result) {
                    case SUCCESS -> command;
                    case NOT_EXECUTED -> "控制台命令未执行";
                    case UNCONFIRMED -> "控制台命令结果未确认，请核对后再补发";
                })));
        return receipt;
    }

    private BossDamageSettlementActionResult executeMessageAction(
        BossDamageRewardAction action,
        OfflinePlayer target,
        BossDamageSettlementRecord record,
        BossDamageSettlementEntry entry
    ) {
        if (action.text().isBlank()) {
            return new BossDamageSettlementActionResult(action.type().configKey(), false, "缺少 text");
        }

        String rendered = ChatColor.translateAlternateColorCodes('&', renderTemplate(action.text(), target, record, entry));
        BossDamageRewardMessageTarget targetType = action.target() == null ? BossDamageRewardMessageTarget.PLAYER : action.target();
        switch (targetType) {
            case PLAYER -> {
                if (!(target instanceof Player player) || !player.isOnline()) {
                    return new BossDamageSettlementActionResult(action.type().configKey(), false, "目标玩家不在线");
                }
                player.sendMessage(rendered);
            }
            case BROADCAST -> Bukkit.broadcastMessage(rendered);
            case CONSOLE -> {
                ConsoleCommandSender console = Bukkit.getConsoleSender();
                console.sendMessage(rendered);
            }
        }
        return new BossDamageSettlementActionResult(action.type().configKey(), true, rendered);
    }

    private String renderTemplate(
        String template,
        OfflinePlayer target,
        BossDamageSettlementRecord record,
        BossDamageSettlementEntry entry
    ) {
        String safeTemplate = template == null ? "" : template;
        BossDamageSettlementPlaceholderContext context = new BossDamageSettlementPlaceholderContext(record, entry);
        String playerName = sanitizeName(resolveTargetName(target, entry));
        String playerDisplayName = sanitizeName(resolveTargetDisplayName(target, entry));
        String playerUuid = resolveTargetUuid(target, entry);
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(safeTemplate);
        StringBuffer rendered = new StringBuffer();
        while (matcher.find()) {
            String placeholderKey = matcher.group(1);
            String resolved = switch (placeholderKey.toLowerCase(Locale.ROOT)) {
                case "player", "player_name" -> playerName;
                case "player_display_name" -> playerDisplayName;
                case "uuid", "player_uuid" -> playerUuid;
                default -> context.resolve(placeholderKey);
            };
            matcher.appendReplacement(
                rendered,
                Matcher.quoteReplacement(resolved == null ? matcher.group(0) : resolved)
            );
        }
        matcher.appendTail(rendered);
        return applyPlaceholderApi(target, rendered.toString());
    }

    /** 玩家名净化：合法则原样返回，非法则清空命令分隔符 */
    private String sanitizeName(String name) {
        if (name == null) return "";
        try {
            return xuanmo.arcartxsuite.api.command.CommandSanitizer.validatePlayerName(name);
        } catch (IllegalArgumentException e) {
            return xuanmo.arcartxsuite.api.command.CommandSanitizer.stripCommandSeparators(name);
        }
    }

    private void cacheSettlement(BossDamageSettlementRecord record) {
        settlementsById.put(record.settlementId(), record);
        while (settlementsById.size() > MAX_SETTLEMENT_CACHE_SIZE) {
            String oldestKey = settlementsById.keySet().iterator().next();
            BossDamageSettlementRecord removed = settlementsById.remove(oldestKey);
            if (removed == null) {
                continue;
            }
            for (BossDamageSettlementEntry entry : removed.trackedEntries()) {
                BossDamagePlayerSettlementView current = lastSettlementByPlayer.get(entry.playerUuid());
                if (current != null && oldestKey.equals(current.settlement().settlementId())) {
                    lastSettlementByPlayer.remove(entry.playerUuid());
                }
            }
        }

        for (BossDamageSettlementEntry entry : record.trackedEntries()) {
            lastSettlementByPlayer.put(
                entry.playerUuid(),
                new BossDamagePlayerSettlementView(record, entry)
            );
        }
    }

    private String applyPlaceholderApi(OfflinePlayer player, String text) {
        if (placeholderResolver == null || text == null || text.isBlank()) {
            return text == null ? "" : text;
        }
        if (player instanceof Player online) {
            return placeholderResolver.applyPlaceholders(online, text);
        }
        return text;
    }

    private String nextSettlementId() {
        nextSettlementId++;
        return String.format(Locale.ROOT, "S%06d", nextSettlementId);
    }

    private static BossDamageRankingEntry toRankingEntry(BossDamageSettlementEntry entry) {
        return new BossDamageRankingEntry(
            entry.playerUuid(),
            entry.playerName(),
            entry.rank(),
            entry.qualified(),
            entry.damage(),
            entry.damagePercent(),
            entry.takenDamage()
        );
    }

    private static String stripLeadingSlash(String command) {
        if (command == null) {
            return "";
        }
        String trimmed = command.trim();
        return trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
    }

    private static String resolveTargetName(OfflinePlayer target, BossDamageSettlementEntry entry) {
        if (target != null && target.getName() != null && !target.getName().isBlank()) {
            return target.getName();
        }
        return entry.playerName();
    }

    private static String resolveTargetDisplayName(OfflinePlayer target, BossDamageSettlementEntry entry) {
        if (target instanceof Player player && player.isOnline()) {
            String displayName = player.getDisplayName();
            if (displayName != null && !displayName.isBlank()) {
                return displayName;
            }
        }
        return resolveTargetName(target, entry);
    }

    private static String resolveTargetUuid(OfflinePlayer target, BossDamageSettlementEntry entry) {
        if (target != null && target.getUniqueId() != null) {
            return target.getUniqueId().toString();
        }
        return entry.playerUuid().toString();
    }

    private static String formatNumber(double value) {
        synchronized (NUMBER_FORMAT) {
            return NUMBER_FORMAT.format(value);
        }
    }

    private CompletionStage<BossDamageSettlementActionResult> executeMailRewardAction(
            BossDamageRewardAction action, UUID recipient, String requestKey) {
        if (action.presetId().isBlank()) {
            return CompletableFuture.completedFuture(new BossDamageSettlementActionResult("mail", false, "缺少 preset-id"));
        }
        var mailService = mailProvider.get();
        if (mailService == null) {
            return CompletableFuture.completedFuture(new BossDamageSettlementActionResult("mail", false, "Mail 模块未启用"));
        }
        return mailService.sendPresetDurable(recipient, action.presetId(), "entitytracker", requestKey)
            .thenApply(receipt -> {
                boolean stored = receipt.mailId() > 0;
                return new BossDamageSettlementActionResult("mail", stored, stored ? "邮件已入库" : "预设不可用或邮件未入库");
            });
    }

    private CompletionStage<BossDamageSettlementActionResult> deliverItem(BossRewardItems.Prepared prepared,
            UUID recipient, String requestKey) {
        if (!prepared.requiresStorage()) return CompletableFuture.completedFuture(settlementItemResult(prepared.result()));
        return CompletableFuture.supplyAsync(() -> {
            var result = itemRewards.persist(requestKey, prepared.pendingReward());
            if (result.success() && !closed) {
                try {
                    onMain(() -> {
                        if (!closed) itemRewards.flushOnline(recipient);
                    });
                } catch (RuntimeException failure) {
                    logger.fine("Boss 奖励已入库，在线补发调度失败: " + failure.getMessage());
                }
            }
            return settlementItemResult(result);
        }, rewardStorage);
    }

    private static BossDamageSettlementActionResult settlementItemResult(RewardActionResult result) {
        return new BossDamageSettlementActionResult("item", result.success(), result.message());
    }

    private BossDamageSettlementActionResult executeSignalRewardAction(
        BossDamageRewardAction action,
        OfflinePlayer target,
        BossDamageSettlementRecord record,
        BossDamageSettlementEntry entry
    ) {
        if (action.signal().isBlank()) {
            return new BossDamageSettlementActionResult("signal", false, "缺少 signal");
        }
        if (!(target instanceof Player player) || !player.isOnline()) {
            return new BossDamageSettlementActionResult("signal", false, "目标玩家不在线");
        }
        java.util.Map<String, String> variables = new java.util.LinkedHashMap<>();
        variables.put("boss_id", record.mythicMobId());
        variables.put("boss_name", record.bossDisplayName());
        variables.put("settlement_id", record.settlementId());
        variables.put("rank", String.valueOf(entry.rank()));
        variables.put("damage", formatNumber(entry.damage()));
        if (signalDispatcher != null) {
            signalDispatcher.accept(action.signal(), player);
        }
        return new BossDamageSettlementActionResult("signal", true, "信号 " + action.signal() + " 已发送");
    }
}



