package xuanmo.arcartxsuite.entitytracker.crossserver;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.crossserver.CrossServerAPI;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannel;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannelConfig;
import xuanmo.arcartxsuite.entitytracker.boss.tracker.BossDamageSettlementEntry;
import xuanmo.arcartxsuite.entitytracker.boss.tracker.BossDamageSettlementRecord;
import xuanmo.arcartxsuite.entitytracker.dao.PlayerBossBestDamageDao;
import xuanmo.arcartxsuite.entitytracker.entity.BossKillRecord;
import xuanmo.arcartxsuite.entitytracker.entity.PlayerBossBestDamage;
import xuanmo.arcartxsuite.entitytracker.service.BossKillRecordingService;
import xuanmo.arcartxsuite.entitytracker.service.CrossServerRankingCacheService;
import java.util.logging.Logger;

/**
 * Boss 跨服同步：最高伤害、击杀记录广播与入站合并。
 */
public final class EntityTrackerCrossServerService {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final CrossServerAPI crossServer;
    private final CrossServerChannelConfig channelConfig;
    private final PlayerBossBestDamageDao damageDao;
    private final xuanmo.arcartxsuite.api.scheduler.ModuleExecutor worker =
        new xuanmo.arcartxsuite.api.scheduler.ModuleExecutor(0,1,30,java.util.concurrent.TimeUnit.SECONDS,
            new java.util.concurrent.ArrayBlockingQueue<>(256),task->{
                var thread=new Thread(task,"AXS-EntityTracker-Inbound");thread.setDaemon(true);return thread;
            });
    private volatile boolean closed;

    private volatile CrossServerChannel channel;
    private BossKillRecordingService killRecordingService;
    private CrossServerRankingCacheService rankingCacheService;

    public EntityTrackerCrossServerService(
        JavaPlugin plugin,
        Logger logger,
        CrossServerAPI crossServer,
        CrossServerChannelConfig channelConfig,
        DataSource dataSource
    ) {
        this.plugin = plugin;
        this.logger = logger;
        this.crossServer = crossServer;
        this.channelConfig = channelConfig == null ? CrossServerChannelConfig.disabled() : channelConfig;
        this.damageDao = new PlayerBossBestDamageDao(dataSource, plugin);
    }

    public void attachKillRecording(BossKillRecordingService killRecordingService) {
        this.killRecordingService = killRecordingService;
    }

    public void attachRankingCache(CrossServerRankingCacheService rankingCacheService) {
        this.rankingCacheService = rankingCacheService;
    }

    public void start() {
        channel = crossServer.openChannelCompleting(
            "entitytracker",
            channelConfig,
            delivery -> admit(delivery.payload())
        );
        if (channel.isActive()) {
            this.logger.info("跨服 Boss 排行通道已启用");
        }
    }

    public void shutdown() {
        closed=true;
        try { if (channel != null) { channel.close(); channel = null; } }
        finally { worker.shutdown(); }
    }

    public java.util.concurrent.CompletionStage<Void> databaseStopped() { return worker.stopped(); }

    private java.util.concurrent.CompletionStage<Boolean> admit(String payload) {
        if(closed)return null;
        var done=new java.util.concurrent.CompletableFuture<Boolean>();
        try{worker.execute(()->{try{handlePayload(payload);done.complete(true);}catch(Exception|LinkageError failure){
            logger.log(java.util.logging.Level.WARNING,"跨服 Boss 入站未确认",failure);done.complete(false);
        }});}catch(java.util.concurrent.RejectedExecutionException full){return null;}
        return done.copy();
    }

    public boolean isActive() {
        return channel != null && channel.isActive();
    }

    /** Called only by an accepted kill worker, whose lifetime also owns these SQL operations. */
    public void publishSettlement(BossDamageSettlementRecord record, BossKillRecord location) {
        if (record == null) {
            return;
        }
        String nodeId = crossServer.nodeId();
        String worldName = location.getWorldName();
        Double locX = location.getLocationX();
        Double locY = location.getLocationY();
        Double locZ = location.getLocationZ();
        LocalDateTime now = LocalDateTime.now();

        boolean changed = false;
        int batchSize = PlayerBossBestDamageDao.WRITE_BATCH_SIZE;
        var candidates = new ArrayList<PlayerBossBestDamage>(batchSize);
        try {
            for (BossDamageSettlementEntry entry : record.entriesByPlayer().values()) {
                if (entry == null || entry.playerUuid() == null || entry.damage() <= 0.0D) {
                    continue;
                }
                PlayerBossBestDamage candidate = new PlayerBossBestDamage(
                    entry.playerUuid().toString(),
                    entry.playerName(),
                    record.mythicMobId(),
                    record.bossDisplayName(),
                    (int) Math.round(entry.damage()),
                    now,
                    nodeId
                );
                candidate.setWorldName(worldName);
                candidate.setLocationX(locX);
                candidate.setLocationY(locY);
                candidate.setLocationZ(locZ);
                candidates.add(candidate);
                if (candidates.size() == batchSize) {
                    changed |= saveAndPublishDamage(record.mythicMobId(), candidates);
                    candidates.clear();
                }
            }
            if (!candidates.isEmpty()) changed |= saveAndPublishDamage(record.mythicMobId(), candidates);
        } finally {
            if (changed) requestDamageRefresh(record.mythicMobId());
        }
    }

    private boolean saveAndPublishDamage(String bossId, List<PlayerBossBestDamage> candidates) {
        List<PlayerBossBestDamage> improved;
        try {
            improved = damageDao.saveBetterBatch(bossId, candidates);
        } catch (SQLException failure) {
            logger.warning("批量写入 Boss 最高伤害失败: " + failure.getMessage());
            return false;
        }
        for (var record : improved) {
            try {
                publish(EntityTrackerCrossServerPayloadCodec.encodeBestDamage(record));
            } catch (RuntimeException failure) {
                logger.warning("最高伤害已提交，跨服广播失败: " + failure.getMessage());
            }
        }
        return !improved.isEmpty();
    }

    public void publishKillRecord(BossKillRecord record) {
        if (record != null) publish(EntityTrackerCrossServerPayloadCodec.encodeKillRecord(record));
    }

    private void publish(String payload) { var current=channel;if(current!=null&&current.isActive())current.publish(payload); }

    private void handlePayload(String payload) throws SQLException {
        if (payload == null || payload.isBlank()) {
            return;
        }
        try {
            EntityTrackerCrossServerPayloadCodec.DecodedPayload decoded =
                EntityTrackerCrossServerPayloadCodec.decode(payload);
            if (EntityTrackerCrossServerPayloadCodec.TYPE_BEST_DAMAGE.equals(decoded.type())) {
                mergeBestDamage(decoded.bestDamage());
            } else if (EntityTrackerCrossServerPayloadCodec.TYPE_KILL_RECORD.equals(decoded.type())) {
                mergeKillRecord(decoded.killRecord());
            }
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void mergeBestDamage(PlayerBossBestDamage remote) throws SQLException {
        if (remote == null || remote.getPlayerUuid() == null || remote.getPlayerUuid().isBlank()
            || remote.getBossId() == null || remote.getBossId().isBlank()) {
            return;
        }
        if (damageDao.insertOrUpdateIfBetter(remote)) {
            requestDamageRefresh(remote.getBossId());
        }
    }

    private void mergeKillRecord(BossKillRecord remote) throws SQLException {
        if (remote == null) return;
        if (killRecordingService == null) throw new SQLException("Kill recorder not ready");
        killRecordingService.applyRemoteKillRecord(remote);
    }

    private void requestDamageRefresh(String bossId) {
        if (rankingCacheService != null) {
            rankingCacheService.requestDamageRefresh(bossId);
        }
    }
}


