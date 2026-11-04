package xuanmo.arcartxsuite.entitytracker.boss.tracker;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDamageRankRewardDefinition;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDamageRankingRewardsSettings;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDamageRewardAction;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDamageRewardMessageTarget;
import xuanmo.arcartxsuite.api.item.ItemRewardDispatcher;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.api.item.PendingRewardService;
import xuanmo.arcartxsuite.api.placeholder.PlaceholderResolverAPI;
import xuanmo.arcartxsuite.api.message.MessageProvider;
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
    private final java.util.function.Supplier<xuanmo.arcartxsuite.api.capability.MailDispatchable> mailDispatchableProvider;
    private final java.util.function.BiConsumer<String, org.bukkit.entity.Player> signalDispatcher;
    private final ItemSourceRegistry itemSourceRegistry;
    private final ItemRewardDispatcher itemRewardDispatcher;
    private final PendingRewardService pendingRewardService;
    private final MessageProvider messages;
    private final Map<String, BossDamageSettlementRecord> settlementsById = new LinkedHashMap<>();
    private final Map<UUID, BossDamagePlayerSettlementView> lastSettlementByPlayer = new LinkedHashMap<>();

    private final PlaceholderResolverAPI placeholderResolver;
    private long nextSettlementId;

    public BossDamageSettlementService(JavaPlugin plugin,
        Logger logger, ItemSourceRegistry itemSourceRegistry, PlaceholderResolverAPI placeholderResolver) {
        this(plugin, logger, () -> null, null, itemSourceRegistry, null, placeholderResolver, null, null);
    }

    public BossDamageSettlementService(
        JavaPlugin plugin,
        Logger logger,
        java.util.function.Supplier<xuanmo.arcartxsuite.api.capability.MailDispatchable> mailDispatchableProvider,
        java.util.function.BiConsumer<String, org.bukkit.entity.Player> signalDispatcher,
        ItemSourceRegistry itemSourceRegistry,
        ItemRewardDispatcher itemRewardDispatcher,
        PlaceholderResolverAPI placeholderResolver,
        MessageProvider messages,
        PendingRewardService pendingRewardService
    ) {
        this.plugin = plugin;
        this.logger = logger;
        this.mailDispatchableProvider = mailDispatchableProvider == null ? () -> null : mailDispatchableProvider;
        this.signalDispatcher = signalDispatcher;
        this.itemSourceRegistry = itemSourceRegistry;
        this.itemRewardDispatcher = itemRewardDispatcher;
        this.placeholderResolver = placeholderResolver;
        this.messages = messages;
        this.pendingRewardService = pendingRewardService;
    }

    /**
     * 关闭结算服务，清空所有缓存。
     */
    public void shutdown() {
        settlementsById.clear();
        lastSettlementByPlayer.clear();
        nextSettlementId = 0L;
    }

    /**
     * 对指定 Boss 会话执行伤害结算并发放奖励。
     *
     * @param session Boss 会话
     * @param entity  Boss 实体
     * @return 结算记录，会话为空或已结算时返回 {@code null}
     */
    public BossDamageSettlementRecord settle(BossSession session, org.bukkit.entity.LivingEntity entity) {
        if (session == null || entity == null || !session.markSettled()) {
            return null;
        }

        BossSession.BossSettlementSnapshot snapshot = session.createSettlementSnapshot(entity);
        BossDamageRankingSnapshot ranking = snapshot.ranking();
        String settlementId = nextSettlementId();
        long settledAtMillis = System.currentTimeMillis();

        List<BossDamageSettlementEntry> provisionalEntries = new ArrayList<>(ranking.trackedEntries().size());
        Map<UUID, BossDamageSettlementEntry> provisionalEntriesByPlayer = new LinkedHashMap<>();
        for (BossDamageRankingEntry rankingEntry : ranking.trackedEntries()) {
            BossDamageSettlementEntry entry = BossDamageSettlementEntry.fromRankingEntry(rankingEntry, false, "", List.of());
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

        List<BossDamageSettlementEntry> finalEntries = new ArrayList<>(provisionalEntries.size());
        Map<UUID, BossDamageSettlementEntry> finalEntriesByPlayer = new LinkedHashMap<>();
        for (BossDamageSettlementEntry entry : provisionalEntries) {
            BossDamageSettlementEntry settledEntry = executeRewardActions(provisionalRecord, entry, null, true);
            finalEntries.add(settledEntry);
            finalEntriesByPlayer.put(settledEntry.playerUuid(), settledEntry);
        }

        BossDamageSettlementRecord finalRecord = new BossDamageSettlementRecord(
            settlementId,
            settledAtMillis,
            provisionalRecord.mythicMobId(),
            provisionalRecord.bossDisplayName(),
            provisionalRecord.entityUuid(),
            provisionalRecord.participantCount(),
            provisionalRecord.trackedPlayerCount(),
            provisionalRecord.totalDamage(),
            provisionalRecord.rewardsSettings(),
            List.copyOf(finalEntries),
            Map.copyOf(finalEntriesByPlayer)
        );
        cacheSettlement(finalRecord);
        return finalRecord;
    }

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
    public BossDamageRewardDispatchResult reissueReward(String settlementId, int rank, OfflinePlayer overridePlayer) {
        BossDamageSettlementRecord record = settlement(settlementId);
        if (record == null) {
            return BossDamageRewardDispatchResult.failure(messages.get("admin.reissue.record-not-found", settlementId));
        }
        BossDamageSettlementEntry entry = record.entry(rank);
        if (entry.rank() <= 0) {
            return BossDamageRewardDispatchResult.failure(messages.get("admin.reissue.rank-not-found", rank));
        }

        BossDamageSettlementEntry reissuedEntry = executeRewardActions(record, entry, overridePlayer, false);
        if (reissuedEntry.rewarded()) {
            String targetName = resolveTargetName(overridePlayer, entry);
            return BossDamageRewardDispatchResult.success(
                messages.get("admin.reissue.success", settlementId, rank, targetName)
            );
        }
        return BossDamageRewardDispatchResult.failure(
            messages.get("admin.reissue.failure",
                reissuedEntry.rewardFailure().isBlank() ? "未知错误" : reissuedEntry.rewardFailure())
        );
    }

    private BossDamageSettlementEntry executeRewardActions(
        BossDamageSettlementRecord record,
        BossDamageSettlementEntry entry,
        OfflinePlayer overridePlayer,
        boolean logFailures
    ) {
        if (entry == null) {
            return BossDamageSettlementEntry.empty();
        }
        if (entry.rank() <= 0) {
            return BossDamageSettlementEntry.fromRankingEntry(
                toRankingEntry(entry),
                false,
                "未达最低伤害阈值",
                List.of()
            );
        }

        BossDamageRankingRewardsSettings rewardsSettings = record.rewardsSettings();
        if (!rewardsSettings.enabled()) {
            return BossDamageSettlementEntry.fromRankingEntry(
                toRankingEntry(entry),
                false,
                "奖励未启用",
                List.of()
            );
        }

        BossDamageRankRewardDefinition rankReward = rewardsSettings.rewardForRank(entry.rank());
        if (rankReward.actions().isEmpty()) {
            return BossDamageSettlementEntry.fromRankingEntry(
                toRankingEntry(entry),
                false,
                "未配置奖励",
                List.of()
            );
        }

        OfflinePlayer target = overridePlayer != null ? overridePlayer : Bukkit.getOfflinePlayer(entry.playerUuid());
        List<BossDamageSettlementActionResult> actionResults = new ArrayList<>(rankReward.actions().size());
        List<String> failures = new ArrayList<>();
        boolean allSuccessful = true;

        for (BossDamageRewardAction action : rankReward.actions()) {
            BossDamageSettlementActionResult result = executeAction(
                action,
                target,
                record,
                entry
            );
            actionResults.add(result);
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
                            + " | player=" + resolveTargetName(target, entry)
                            + " | action=" + (action.type() == null ? "unknown" : action.type().configKey())
                            + " | reason=" + result.message()
                    );
                }
            }
        }

        return BossDamageSettlementEntry.fromRankingEntry(
            toRankingEntry(entry),
            allSuccessful,
            failures.isEmpty() ? "" : String.join("；", failures),
            actionResults
        );
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
            case ITEM -> executeItemAction(
                action, target, record, entry
            );
            case COMMAND -> executeCommandAction(action, target, record, entry);
            case MESSAGE -> executeMessageAction(action, target, record, entry);
            case MAIL -> executeMailRewardAction(action, target, record, entry);
            case SIGNAL -> executeSignalRewardAction(action, target, record, entry);
        };
    }

    private BossDamageSettlementActionResult executeItemAction(
        BossDamageRewardAction action,
        OfflinePlayer target,
        BossDamageSettlementRecord record,
        BossDamageSettlementEntry entry
    ) {
        if (action.id().isBlank()) {
            return new BossDamageSettlementActionResult(action.type().configKey(), false, "缺少 id");
        }
        if (!(target instanceof Player player) || !player.isOnline()) {
            // 离线玩家：暂存到统一 pending 服务，上线后自动补发
            if (target != null && pendingRewardService != null) {
                pendingRewardService.savePending(PendingRewardService.PendingReward.item(
                    target.getUniqueId(), "entitytracker",
                    action.source(), action.id(), action.amount(), action.nbt(),
                    "Boss 伤害结算奖励: " + record.settlementId() + " rank=" + entry.rank()
                ));
                return new BossDamageSettlementActionResult(action.type().configKey(), true, "离线玩家奖励已暂存，上线后自动补发");
            }
            return new BossDamageSettlementActionResult(action.type().configKey(), false, "目标玩家不在线");
        }

        ItemStack itemStack = itemSourceRegistry.generateItem(action.source(), action.id(), action.amount(), action.nbt(), player);
        if (itemStack == null || itemStack.getType().isAir()) {
            return new BossDamageSettlementActionResult(action.type().configKey(), false, "物品不存在: " + action.id());
        }

        ItemRewardDispatcher.DispatchOptions options = ItemRewardDispatcher.DispatchOptions.builder("entitytracker")
            .sourceDetail("settlement:" + record.settlementId() + ":rank" + entry.rank())
            .overflowMailSubject("Boss 结算奖励溢出物品")
            .overflowMailBody("您的背包已满，Boss 伤害结算奖励物品通过邮件补发。")
            .overflowMailSenderName("Boss 追踪")
            .build();
        ItemRewardDispatcher.DispatchResult result = itemRewardDispatcher.dispatchItem(player, itemStack, options);
        if (result.success()) {
            String suffix = "";
            if (result.mailedCount() > 0) suffix = "（部分通过邮件补发）";
            else if (result.pendingCount() > 0) suffix = "（部分已暂存待发放）";
            String desc = itemStack.getAmount() + "x " + action.id() + suffix;
            return new BossDamageSettlementActionResult(action.type().configKey(), true, desc);
        }
        return new BossDamageSettlementActionResult(action.type().configKey(), false, result.message());
    }

    private BossDamageSettlementActionResult executeCommandAction(
        BossDamageRewardAction action,
        OfflinePlayer target,
        BossDamageSettlementRecord record,
        BossDamageSettlementEntry entry
    ) {
        if (action.command().isBlank()) {
            return new BossDamageSettlementActionResult(action.type().configKey(), false, "缺少 command");
        }

        String rendered = renderTemplate(action.command(), target, record, entry);
        // 占位符/PAPI 展开后统一校验最终命令：context.resolve 与 PAPI 值可能携带分隔符
        try {
            rendered = xuanmo.arcartxsuite.api.command.CommandSanitizer.validateExpandedCommand(rendered);
        } catch (IllegalArgumentException e) {
            return new BossDamageSettlementActionResult(action.type().configKey(), false, "命令含分隔符已拒绝: " + rendered);
        }
        boolean dispatched = xuanmo.arcartxsuite.api.command.ThrottledCommandExecutor.dispatch(stripLeadingSlash(rendered));
        return new BossDamageSettlementActionResult(
            action.type().configKey(),
            dispatched,
            dispatched ? rendered : "控制台命令受理失败(队列已满或执行异常): " + rendered
        );
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

    private BossDamageSettlementActionResult executeMailRewardAction(
        BossDamageRewardAction action,
        OfflinePlayer target,
        BossDamageSettlementRecord record,
        BossDamageSettlementEntry entry
    ) {
        if (action.presetId().isBlank()) {
            return new BossDamageSettlementActionResult("mail", false, "缺少 preset-id");
        }
        String targetName = resolveTargetName(target, entry);
        xuanmo.arcartxsuite.api.capability.MailDispatchable mailService = mailDispatchableProvider.get();
        if (mailService == null) {
            return new BossDamageSettlementActionResult("mail", false, "Mail 模块未启用");
        }
        xuanmo.arcartxsuite.api.capability.mail.MailSendResult mailResult = mailService.dispatchPreset(
            xuanmo.arcartxsuite.api.capability.mail.MailPresetRequest.builder(action.presetId(), targetName)
                .sourceModule("entitytracker")
                .sourceDetail("BossSettlement:" + record.settlementId())
                .build());
        boolean ok = mailResult.success();
        return new BossDamageSettlementActionResult("mail", ok, ok ? "邮件已派发" : "邮件派发失败");
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



