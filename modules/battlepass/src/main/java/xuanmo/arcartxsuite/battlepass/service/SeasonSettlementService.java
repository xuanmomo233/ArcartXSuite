package xuanmo.arcartxsuite.battlepass.service;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import xuanmo.arcartxsuite.battlepass.config.BattlePassModuleConfiguration;
import xuanmo.arcartxsuite.battlepass.model.BattlePassPlayerProgress.PassTier;
import xuanmo.arcartxsuite.battlepass.storage.BattlePassRepository;

/** Enqueues outstanding rewards and closes the season only after every batch is durably accepted. */
public final class SeasonSettlementService {
    public enum Result { COMPLETED, ALREADY_SETTLED, RUNNING, RETRY, NOT_ENDED }

    private final BattlePassModuleConfiguration configuration;
    private final BattlePassRepository repository;
    private final BattlePassService service;
    private final Logger logger;
    private final AtomicBoolean settling = new AtomicBoolean();

    public SeasonSettlementService(BattlePassModuleConfiguration configuration, BattlePassRepository repository,
                                   BattlePassService service, Logger logger) {
        this.configuration = configuration;
        this.repository = repository;
        this.service = service;
        this.logger = logger;
    }

    public void checkAndAutoSettle() {
        if (LocalDate.now().isAfter(configuration.season().endDate())) settle(false);
    }

    public Result settle(boolean forced) {
        return service.callApi(() -> settleNow(forced),Result.RETRY);
    }

    private Result settleNow(boolean forced) {
        var season = configuration.season();
        if (!forced && !LocalDate.now().isAfter(season.endDate())) return Result.NOT_ENDED;
        if (!settling.compareAndSet(false, true)) return Result.RUNNING;
        String seasonId = season.seasonId();
        try {
            if (repository.isSeasonSettled(seasonId)) return Result.ALREADY_SETTLED;
            if (!service.catchUpAbyssTasks()) return Result.RETRY;
            service.flushProgressForSettlement();
            boolean retry = false;
            var recipients = new HashSet<UUID>();
            for (var summary : repository.loadSeasonSummaries(seasonId)) {
                var batch = service.acceptRewards(summary.playerUuid(), rewardsFor(summary));
                if (batch.accepted() > 0) recipients.add(summary.playerUuid());
                retry |= batch.retry();
            }
            service.flushAcceptedRewards(recipients);
            if (retry) {
                logger.warning("赛季 " + seasonId + " 有奖励尚未确认，保留待结算");
                return Result.RETRY;
            }
            repository.markSeasonSettled(seasonId);
            logger.info("赛季 " + seasonId + " 奖励已确认入队，结算完成");
            return Result.COMPLETED;
        } catch (SQLException | RuntimeException error) {
            logger.log(Level.WARNING, "赛季结算待重试: " + seasonId, error);
            return Result.RETRY;
        } finally {
            settling.set(false);
        }
    }

    private List<BattlePassService.RewardEntry> rewardsFor(BattlePassRepository.PlayerSeasonSummary summary) {
        PassTier tier;
        try {
            tier = PassTier.valueOf(summary.tier().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            tier = PassTier.FREE;
        }

        var entries = new ArrayList<BattlePassService.RewardEntry>();
        for (var reward : configuration.rewards()) {
            if (reward.level() <= summary.level() && BattlePassService.canClaim(reward, tier)) {
                entries.add(new BattlePassService.RewardEntry(reward.rewardId(), reward));
            }
        }
        String tierName = tier.name().toLowerCase(Locale.ROOT);
        for (var settlement : configuration.seasonRewards()) {
            if (summary.level() < settlement.minLevel()) continue;
            var reward = switch (tier) {
                case DELUXE -> settlement.deluxe() != null ? settlement.deluxe()
                    : settlement.premium() != null ? settlement.premium() : settlement.free();
                case PREMIUM -> settlement.premium() != null ? settlement.premium() : settlement.free();
                case FREE -> settlement.free();
            };
            if (reward != null) {
                String key = "settlement_" + settlement.settlementId() + "_" + tierName;
                entries.add(new BattlePassService.RewardEntry(key, reward));
            }
        }
        return entries;
    }

    public String getSettlementStatus(String seasonId) {
        return service.callApi(() -> settlementStatusNow(seasonId),"模块已停用");
    }

    private String settlementStatusNow(String seasonId) {
        try {
            if (repository.isSeasonSettled(seasonId)) return "已结算";
            if (settling.get()) return "结算中";
            return LocalDate.now().isAfter(configuration.season().endDate()) ? "待结算（赛季已结束）" : "未结算（赛季进行中）";
        } catch (SQLException error) {
            return "查询失败: " + error.getMessage();
        }
    }
}
