package xuanmo.arcartxsuite.entitytracker.command;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.entitytracker.boss.tracker.ActiveBossSessionView;
import xuanmo.arcartxsuite.entitytracker.boss.tracker.BossDamageRewardDispatchResult;
import xuanmo.arcartxsuite.entitytracker.boss.tracker.BossDamageSettlementEntry;
import xuanmo.arcartxsuite.entitytracker.boss.tracker.BossDamageSettlementRecord;
import xuanmo.arcartxsuite.entitytracker.boss.tracker.BossSessionRankingView;
import xuanmo.arcartxsuite.entitytracker.boss.tracker.BossTrackerService;

public final class EntityTrackerAdminCommand implements ModuleCommandHandler {

    private static final DecimalFormat DF = new DecimalFormat("0.##");
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final List<String> ACTIONS = List.of(
        "help", "status", "reload", "sessions", "rank", "settlements", "settlement", "reissue"
    );
    private static final int PAGE_SIZE = 10;

    private final Supplier<BossTrackerService> serviceProvider;
    private final MessageProvider messages;

    public EntityTrackerAdminCommand(Supplier<BossTrackerService> serviceProvider, MessageProvider messages) {
        this.serviceProvider = serviceProvider;
        this.messages = messages;
    }

    private String fullMsg(String key, Object... args) {
        if (messages == null) return "";
        return messages.get("prefix") + messages.get(key, args);
    }

    @Override
    public String commandId() {
        return "entitytracker";
    }

    @Override
    public List<String> actions() {
        return ACTIONS;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "help";
        switch (action) {
            case "help" -> sendHelp(sender, label);
            case "status" -> sendStatus(sender);
            case "reload" -> sender.sendMessage(fullMsg("common.reload-hint", label));
            case "sessions" -> handleSessions(sender, args);
            case "rank" -> handleRank(sender, args);
            case "settlements" -> handleSettlements(sender, args);
            case "settlement" -> handleSettlement(sender, args);
            case "reissue" -> handleReissue(sender, args);
            default -> sender.sendMessage(fullMsg("common.unknown", label));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 2) {
            return filter(ACTIONS, args[1]);
        }
        BossTrackerService svc = serviceProvider.get();
        if (svc == null) return List.of();
        if (args.length == 3) {
            return switch (args[1].toLowerCase(Locale.ROOT)) {
                case "sessions" -> filter(List.of(), args[2]);
                case "rank" -> filter(svc.sessionEntityIds(), args[2]);
                case "settlement", "reissue" -> filter(svc.settlementIds(), args[2]);
                default -> List.of();
            };
        }
        if (args.length == 4) {
            return switch (args[1].toLowerCase(Locale.ROOT)) {
                case "rank", "settlement" -> filter(List.of("1", "2", "3"), args[3]);
                case "reissue" -> filter(List.of("1", "2", "3"), args[3]);
                default -> List.of();
            };
        }
        if (args.length == 5 && "reissue".equalsIgnoreCase(args[1])) {
            return null; // player names
        }
        return List.of();
    }

    private void sendHelp(CommandSender sender, String label) {
        String cmd = "/" + label + " entitytracker";
        sender.sendMessage(fullMsg("help.title"));
        sender.sendMessage(fullMsg("help.status", cmd));
        sender.sendMessage(fullMsg("help.sessions", cmd));
        sender.sendMessage(fullMsg("help.rank", cmd));
        sender.sendMessage(fullMsg("help.settlements", cmd));
        sender.sendMessage(fullMsg("help.settlement", cmd));
        sender.sendMessage(fullMsg("help.reissue", cmd));
    }

    private void sendStatus(CommandSender sender) {
        BossTrackerService svc = serviceProvider.get();
        if (svc == null) {
            sender.sendMessage(fullMsg("common.service-down"));
            return;
        }
        sender.sendMessage(fullMsg("status.title"));
        sender.sendMessage(fullMsg("status.active-sessions", svc.getActiveSessionCount()));
        sender.sendMessage(fullMsg("status.active-viewers", svc.getActiveViewerCount()));
        sender.sendMessage(fullMsg("status.history-settlements", svc.settlementIds().size()));
    }

    private void handleSessions(CommandSender sender, String[] args) {
        BossTrackerService svc = serviceProvider.get();
        if (svc == null) {
            sender.sendMessage(fullMsg("common.service-down"));
            return;
        }
        String mobIdFilter = args.length >= 3 ? args[2] : null;
        List<ActiveBossSessionView> sessions = svc.activeSessions(mobIdFilter);
        if (sessions.isEmpty()) {
            sender.sendMessage(fullMsg("admin.sessions.empty"));
            return;
        }
        sender.sendMessage(fullMsg("admin.sessions.title", sessions.size()));
        for (ActiveBossSessionView s : sessions) {
            sender.sendMessage(fullMsg("admin.sessions.item-format", s.mythicMobId(), DF.format(s.health()), DF.format(s.maxHealth()), s.participantCount(), s.entityUuid()));
        }
    }

    private void handleRank(CommandSender sender, String[] args) {
        BossTrackerService svc = serviceProvider.get();
        if (svc == null) {
            sender.sendMessage(fullMsg("common.service-down"));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(fullMsg("admin.rank.usage"));
            return;
        }
        UUID entityUuid;
        try {
            entityUuid = UUID.fromString(args[2]);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(fullMsg("admin.rank.invalid-uuid", args[2]));
            return;
        }
        BossSessionRankingView ranking = svc.sessionRanking(entityUuid);
        if (ranking == null) {
            sender.sendMessage(fullMsg("admin.rank.not-found"));
            return;
        }
        sender.sendMessage(fullMsg("admin.rank.title", ranking.mythicMobId()));
        int page = args.length >= 4 ? parsePageNumber(args[3]) : 1;
        List<?> entries = ranking.trackedEntries();
        sendPagedEntries(sender, entries, page, (entry, idx) -> {
            var e = (xuanmo.arcartxsuite.entitytracker.boss.tracker.BossDamageRankingEntry) entry;
            return fullMsg("admin.rank.item-format", idx + 1, e.playerName(), DF.format(e.damage()));
        });
    }

    private void handleSettlements(CommandSender sender, String[] args) {
        BossTrackerService svc = serviceProvider.get();
        if (svc == null) {
            sender.sendMessage(fullMsg("common.service-down"));
            return;
        }
        List<BossDamageSettlementRecord> records = svc.settlements();
        if (records.isEmpty()) {
            sender.sendMessage(fullMsg("admin.settlements.empty"));
            return;
        }
        int page = args.length >= 3 ? parsePageNumber(args[2]) : 1;
        sender.sendMessage(fullMsg("admin.settlements.title", records.size()));
        sendPagedEntries(sender, records, page, (entry, idx) -> {
            var r = (BossDamageSettlementRecord) entry;
            return fullMsg("admin.settlements.item-format", r.settlementId(), r.mythicMobId(), DATE_FMT.format(new Date(r.settledAtMillis())), r.participantCount());
        });
    }

    private void handleSettlement(CommandSender sender, String[] args) {
        BossTrackerService svc = serviceProvider.get();
        if (svc == null) {
            sender.sendMessage(fullMsg("common.service-down"));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(fullMsg("admin.settlement.usage"));
            return;
        }
        BossDamageSettlementRecord record = svc.settlement(args[2]);
        if (record == null || record.settlementId().isEmpty()) {
            sender.sendMessage(fullMsg("admin.settlement.not-found", args[2]));
            return;
        }
        sender.sendMessage(fullMsg("admin.settlement.title", record.mythicMobId(), record.settlementId()));
        sender.sendMessage(fullMsg("admin.settlement.info", DATE_FMT.format(new Date(record.settledAtMillis())), record.participantCount(), DF.format(record.totalDamage())));
        int page = args.length >= 4 ? parsePageNumber(args[3]) : 1;
        List<BossDamageSettlementEntry> entries = record.rankedEntries();
        sendPagedEntries(sender, entries, page, (entry, idx) -> {
            var e = (BossDamageSettlementEntry) entry;
            String rewarded = e.rewarded() ? ChatColor.GREEN + "✓" : ChatColor.RED + "✗";
            return fullMsg("admin.settlement.item-format", e.rank(), e.playerName(), DF.format(e.damage()), rewarded);
        });
    }

    @SuppressWarnings("deprecation")
    private void handleReissue(CommandSender sender, String[] args) {
        BossTrackerService svc = serviceProvider.get();
        if (svc == null) {
            sender.sendMessage(fullMsg("common.service-down"));
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(fullMsg("admin.reissue.usage"));
            return;
        }
        int rank;
        try {
            rank = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(fullMsg("admin.reissue.invalid-rank", args[3]));
            return;
        }
        OfflinePlayer overridePlayer = args.length >= 5 ? Bukkit.getOfflinePlayer(args[4]) : null;
        BossDamageRewardDispatchResult result = svc.reissueSettlementReward(args[2], rank, overridePlayer);
        sender.sendMessage(fullMsg("admin.reissue.result", result.message()));
    }

    // ─── Utilities ──────────────────────────────────────────

    private interface EntryFormatter {
        String format(Object entry, int index);
    }

    private void sendPagedEntries(CommandSender sender, List<?> entries, int page, EntryFormatter formatter) {
        int totalPages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(1, Math.min(page, totalPages));
        int from = (page - 1) * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, entries.size());
        for (int i = from; i < to; i++) {
            sender.sendMessage(formatter.format(entries.get(i), i));
        }
        if (totalPages > 1) {
            sender.sendMessage(fullMsg("admin.common.page-footer", page, totalPages));
        }
    }

    private static int parsePageNumber(String raw) {
        try { return Math.max(1, Integer.parseInt(raw)); } catch (NumberFormatException e) { return 1; }
    }

    private static List<String> filter(List<String> candidates, String input) {
        String normalized = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                result.add(candidate);
            }
        }
        return result;
    }
}
