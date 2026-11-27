package xuanmo.arcartxsuite.entitytracker.crossserver;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.crossserver.CrossServerAPI;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannelConfig;
import xuanmo.arcartxsuite.entitytracker.boss.tracker.BossDamageSettlementRecord;
import xuanmo.arcartxsuite.entitytracker.entity.BossKillRecord;
import xuanmo.arcartxsuite.entitytracker.service.BossKillRecordingService;
import xuanmo.arcartxsuite.entitytracker.service.CrossServerRankingCacheService;

public final class EntityTrackerCrossServerService {
    public EntityTrackerCrossServerService(JavaPlugin plugin, Logger logger, CrossServerAPI crossServer,
                                           CrossServerChannelConfig channelConfig, DataSource dataSource,
                                           BooleanSupplier allowed) {
        throw new UnsupportedOperationException("Cross-server boss ranking is not included");
    }

    public void attachKillRecording(BossKillRecordingService recording) {}
    public void attachRankingCache(CrossServerRankingCacheService cache) {}
    public void start() {}
    public void shutdown() {}
    public CompletionStage<Void> databaseStopped() { return CompletableFuture.completedFuture(null); }
    public boolean isActive() { return false; }
    public void publishSettlement(BossDamageSettlementRecord record, BossKillRecord location) {}
    public void publishKillRecord(BossKillRecord record) {}
}
