package xuanmo.arcartxsuite.battlepass.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.api.capability.AbyssRunResultAccess;
import xuanmo.arcartxsuite.battlepass.model.BattlePassTask;
import xuanmo.arcartxsuite.battlepass.storage.BattlePassRepository;

/** Reads committed native dungeon runs in order, including runs completed while BattlePass was offline. */
final class AbyssTaskFeed {
    private static final int PAGE_SIZE = 50;
    private final BattlePassService service;
    private final BattlePassRepository repository;
    private final Supplier<AbyssRunResultAccess> provider;
    private final List<BattlePassTask> tasks;
    private final Logger logger;
    private final String cursorId;
    private final AtomicBoolean polling = new AtomicBoolean();
    private volatile boolean active;
    private BukkitTask timer;

    AbyssTaskFeed(BattlePassService service, BattlePassRepository repository,
                 Supplier<AbyssRunResultAccess> provider, String nodeId, Logger logger) {
        this.service = service;
        this.repository = repository;
        this.provider = provider;
        this.logger = logger;
        String scope = service.configuration().season().seasonId() + "/" + nodeId;
        cursorId = UUID.nameUUIDFromBytes(scope.getBytes(StandardCharsets.UTF_8)).toString();
        tasks = service.configuration().tasks().all().stream()
            .filter(task -> AbyssRunResultAccess.RESULT_COMMITTED_TOPIC.equals(task.eventTopic())).toList();
    }

    void start(Plugin plugin) {
        if (tasks.isEmpty()) return;
        active = true;
        timer = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, service.guard(this::poll), 20L, 100L);
    }

    void stop() {
        active = false;
        if (timer != null) timer.cancel();
    }

    boolean poll() {
        if (tasks.isEmpty()) return true;
        if (!active || !polling.compareAndSet(false, true)) return false;
        try {
            AbyssRunResultAccess abyss = provider.get();
            String season = service.configuration().season().seasonId();
            if (repository.isSeasonSettled(season)) return true;
            if (abyss == null) return false;
            long cursor = repository.loadAbyssCursor(cursorId);
            var page = abyss.committedResults("abyss", cursor, PAGE_SIZE).get(10, TimeUnit.SECONDS);
            long confirmed = cursor;
            try {
                for (var entry : page) {
                    if (!active) return false;
                    apply(entry.result());
                    if (!active) return false;
                    confirmed = entry.sequence();
                }
            } finally {
                if (active && confirmed > cursor) repository.advanceAbyssCursor(cursorId, confirmed);
            }
            return active && page.size() < PAGE_SIZE;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        } catch (Exception error) {
            if (active) logger.log(Level.WARNING, "副本战令结果等待重试", error);
        } finally {
            polling.set(false);
        }
        return false;
    }

    private void apply(AbyssRunResultAccess.Result result) throws Exception {
        if (result.outcome() != AbyssRunResultAccess.Outcome.COMPLETED) return;
        var season = service.configuration().season();
        LocalDate date = Instant.ofEpochMilli(result.endedAt()).atZone(ZoneId.systemDefault()).toLocalDate();
        if (date.isBefore(season.startDate()) || date.isAfter(season.endDate())) return;
        long partySize = result.roster().stream()
            .filter(member -> member.state() != AbyssRunResultAccess.MemberState.LEFT).count();
        for (var member : result.roster()) {
            if (!active) return;
            if (member.state() == AbyssRunResultAccess.MemberState.LEFT) continue;
            var payload = Map.of("dungeon", result.dungeon(), "instance", result.runId().toString(),
                "difficulty", result.difficulty() == null ? "" : result.difficulty(),
                "time_seconds", Long.toString(Math.max(0, result.endedAt() - result.startedAt()) / 1000),
                "party-size", Long.toString(partySize), "deaths", Integer.toString(member.deaths()));
            for (var task : tasks) {
                if (!task.matchesConditions(null, payload)) continue;
                int increment = task.calculateIncrement(null, payload);
                if (increment > 0) {
                    service.acceptTaskFact(member.player(), task.taskId(), increment, result.resultId().toString(), date)
                        .toCompletableFuture().get(10, TimeUnit.SECONDS);
                }
            }
        }
    }
}
