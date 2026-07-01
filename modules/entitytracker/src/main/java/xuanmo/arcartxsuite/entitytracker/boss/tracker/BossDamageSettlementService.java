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
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDamageRankRewardDefinition;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDamageRankingRewardsSettings;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDamageRewardAction;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDamageRewardActionType;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDamageRewardInventoryFullStrategy;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDamageRewardMessageTarget;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.api.placeholder.PlaceholderResolverAPI;
import java.util.logging.Logger;

final class BossDamageSettlementService {

    private static final int MAX_SETTLEMENT_CACHE_SIZE = 100;
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([a-zA-Z0-9_]+)}");
    private static final DecimalFormat NUMBER_FORMAT = new DecimalFormat("0.##");

    private final JavaPlugin plugin;
    private final Logger logger;
    private final java.util.function.Supplier<xuanmo.arcartxsuite.api.capability.MailDispatchable> mailDispatchableProvider;
    private final java.util.function.BiConsumer<String, org.bukkit.entity.Player> signalDispatcher;
    private final ItemSourceRegistry itemSourceRegistry;
    private final Map<String, BossDamageSettlementRecord> settlementsById = new LinkedHashMap<>();
    private final Map<UUID, BossDamagePlayerSettlementView> lastSettlementByPlayer = new LinkedHashMap<>();

    private final PlaceholderResolverAPI placeholderResolver;
    private long nextSettlementId;

    public BossDamageSettlementService(JavaPlugin plugin,
        Logger logger, ItemSourceRegistry itemSourceRegistry, PlaceholderResolverAPI placeholderResolver) {
        this(plugin, () -> null, null, itemSourceRegistry, placeholderResolver);
    }

    public BossDamageSettlementService(
        JavaPlugin plugin,
        java.util.function.Supplier<xuanmo.arcartxsuite.api.capability.MailDispatchable> mailDispatchableProvider,
        java.util.function.BiConsumer<String, org.bukkit.entity.Player> signalDispatcher,
        ItemSourceRegistry itemSourceRegistry,
        PlaceholderResolverAPI placeholderResolver
    ) {
        this.plugin = plugin;
        this.logger = logger;
        this.mailDispatchableProvider = mailDispatchableProvider == null ? () -> null : mailDispatchableProvider;
        this.signalDispatcher = signalDispatcher;
        this.itemSourceRegistry = itemSourceRegistry;
        this.placeholderResolver = placeholderResolver;
    }

    public void shutdown() {
        settlementsById.clear();
        lastSettlementByPlayer.clear();
        nextSettlementId = 0L;
    }

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

    public List<BossDamageSettlementRecord> settlements() {
        List<BossDamageSettlementRecord> records = new ArrayList<>(settlementsById.values());
        java.util.Collections.reverse(records);
        return List.copyOf(records);
    }

    public BossDamageSettlementRecord settlement(String settlementId) {
        if (settlementId == null || settlementId.isBlank()) {
            return null;
        }
        return settlementsById.get(settlementId);
    }

    public BossDamagePlayerSettlementView lastSettlement(UUID playerUuid) {
        if (playerUuid == null) {
            return BossDamagePlayerSettlementView.empty();
        }
        return lastSettlementByPlayer.getOrDefault(playerUuid, BossDamagePlayerSettlementView.empty());
    }

    public List<String> settlementIds() {
        List<String> ids = new ArrayList<>(settlementsById.keySet());
        java.util.Collections.reverse(ids);
        return List.copyOf(ids);
    }

    public BossDamageRewardDispatchResult reissueReward(String settlementId, int rank, OfflinePlayer overridePlayer) {
        BossDamageSettlementRecord record = settlement(settlementId);
        if (record == null) {
            return BossDamageRewardDispatchResult.failure("找不到结算记录: " + settlementId);
        }
        BossDamageSettlementEntry entry = record.entry(rank);
        if (entry.rank() <= 0) {
            return BossDamageRewardDispatchResult.failure("该结算记录中不存在第 " + rank + " 名。");
        }

        BossDamageSettlementEntry reissuedEntry = executeRewardActions(record, entry, overridePlayer, false);
        if (reissuedEntry.rewarded()) {
            String targetName = resolveTargetName(overridePlayer, entry);
            return BossDamageRewardDispatchResult.success(
                "已补发 " + settlementId + " 第 " + rank + " 名奖励 -> " + targetName
            );
        }
        return BossDamageRewardDispatchResult.failure(
            "补发失败: " + (reissuedEntry.rewardFailure().isBlank() ? "未知错误" : reissuedEntry.rewardFailure())
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
                entry,
                rewardsSettings.inventoryFullStrategy()
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
        BossDamageSettlementEntry entry,
        BossDamageRewardInventoryFullStrategy inventoryFullStrategy
    ) {
        if (action == null || action.type() == null) {
            return new BossDamageSettlementActionResult("unknown", false, "奖励动作类型无效");
        }

        return switch (action.type()) {
            case NEIGE_ITEMS -> executeItemAction(
                action,
                target,
                record,
                entry,
                inventoryFullStrategy,
                BossDamageRewardActionType.NEIGE_ITEMS
            );
            case MYTHIC_ITEMS -> executeItemAction(
                action,
                target,
                record,
                entry,
                inventoryFullStrategy,
                BossDamageRewardActionType.MYTHIC_ITEMS
            );
            case OVERTURE_ITEMS -> executeItemAction(
                action,
                target,
                record,
                entry,
                inventoryFullStrategy,
                BossDamageRewardActionType.OVERTURE_ITEMS
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
        BossDamageSettlementEntry entry,
        BossDamageRewardInventoryFullStrategy inventoryFullStrategy,
        BossDamageRewardActionType actionType
    ) {
        if (action.itemId().isBlank()) {
            return new BossDamageSettlementActionResult(actionType.configKey(), false, "缺少 item-id");
        }
        if (!(target instanceof Player player) || !player.isOnline()) {
            return new BossDamageSettlementActionResult(actionType.configKey(), false, "目标玩家不在线");
        }

        ItemStack itemStack = switch (actionType) {
            case NEIGE_ITEMS -> itemSourceRegistry.generateNeigeItem(action.itemId(), action.amount());
            case MYTHIC_ITEMS -> itemSourceRegistry.generateMythicItem(action.itemId(), action.amount());
            case OVERTURE_ITEMS -> itemSourceRegistry.generateOvertureItem(action.itemId(), player, action.amount());
            default -> null;
        };
        if (itemStack == null || itemStack.getType().isAir()) {
            return new BossDamageSettlementActionResult(actionType.configKey(), false, "物品不存在: " + action.itemId());
        }

        if (inventoryFullStrategy == BossDamageRewardInventoryFullStrategy.FAIL && !canFullyFit(player, itemStack)) {
            return new BossDamageSettlementActionResult(actionType.configKey(), false, "背包空间不足");
        }

        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(itemStack.clone());
        if (leftovers.isEmpty()) {
            return new BossDamageSettlementActionResult(actionType.configKey(), true, itemStack.getAmount() + "x " + action.itemId());
        }

        if (inventoryFullStrategy == BossDamageRewardInventoryFullStrategy.FAIL) {
            return new BossDamageSettlementActionResult(actionType.configKey(), false, "背包空间不足");
        }

        Location location = player.getLocation();
        for (ItemStack leftover : leftovers.values()) {
            if (leftover == null || leftover.getType().isAir()) {
                continue;
            }
            player.getWorld().dropItemNaturally(location, leftover);
        }
        if (inventoryFullStrategy == BossDamageRewardInventoryFullStrategy.DROP) {
            player.sendMessage(
                ChatColor.translateAlternateColorCodes(
                    '&',
                    "&e你的背包空间不足，" + action.itemId() + " 已掉落到脚下。"
                )
            );
        }
        return new BossDamageSettlementActionResult(actionType.configKey(), true, "部分掉落到脚下");
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
        boolean dispatched = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), stripLeadingSlash(rendered));
        return new BossDamageSettlementActionResult(
            action.type().configKey(),
            dispatched,
            dispatched ? rendered : "控制台命令执行失败: " + rendered
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
        String playerName = resolveTargetName(target, entry);
        String playerDisplayName = resolveTargetDisplayName(target, entry);
        String playerUuid = resolveTargetUuid(target, entry);
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(safeTemplate);
        StringBuffer rendered = new StringBuffer();
        while (matcher.find()) {
            String placeholderKey = matcher.group(1);
            String resolved = switch (placeholderKey.toLowerCase(Locale.ROOT)) {
                case "player", "player_name" -> playerName;
                case "player_display_name" -> playerDisplayName;
                case "player_uuid" -> playerUuid;
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

    private boolean canFullyFit(Player player, ItemStack itemStack) {
        if (player == null || itemStack == null || itemStack.getType().isAir()) {
            return false;
        }

        int remaining = Math.max(1, itemStack.getAmount());
        for (ItemStack slotItem : player.getInventory().getStorageContents()) {
            if (slotItem == null || slotItem.getType().isAir()) {
                remaining -= itemStack.getMaxStackSize();
            } else if (slotItem.isSimilar(itemStack)) {
                remaining -= Math.max(0, slotItem.getMaxStackSize() - slotItem.getAmount());
            }
            if (remaining <= 0) {
                return true;
            }
        }
        return remaining <= 0;
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
        boolean ok = mailService.dispatchPreset(action.presetId(), targetName, "BossSettlement:" + record.settlementId());
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




