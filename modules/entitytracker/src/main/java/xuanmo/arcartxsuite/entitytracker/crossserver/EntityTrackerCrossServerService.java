package xuanmo.arcartxsuite.entitytracker.crossserver;

import java.sql.SQLException;
import java.time.LocalDateTime;
import javax.sql.DataSource;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.crossserver.CrossServerAPI;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannel;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannelConfig;
import xuanmo.arcartxsuite.entitytracker.boss.tracker.BossDamageSettlementEntry;
import xuanmo.arcartxsuite.entitytracker.boss.tracker.BossDamageSettlementRecord;
import xuanmo.arcartxsuite.entitytracker.dao.PlayerBossBestDamageDao;
import xuanmo.arcartxsuite.entitytracker.entity.PlayerBossBestDamage;

/**
 * Boss 最高伤害跨服同步：Boss 结算后写入本地库并广播，其他子服入站合并。
 */
public final class EntityTrackerCrossServerService {

    private final JavaPlugin plugin;
    private final CrossServerAPI crossServer;
    private final CrossServerChannelConfig channelConfig;
    private final PlayerBossBestDamageDao damageDao;

    private CrossServerChannel channel;

    public EntityTrackerCrossServerService(
        JavaPlugin plugin,
        CrossServerAPI crossServer,
        CrossServerChannelConfig channelConfig,
        DataSource dataSource
    ) {
        this.plugin = plugin;
        this.crossServer = crossServer;
        this.channelConfig = channelConfig == null ? CrossServerChannelConfig.disabled() : channelConfig;
        this.damageDao = new PlayerBossBestDamageDao(dataSource, plugin);
    }

    public void start() {
        channel = crossServer.openChannel(
            "entitytracker",
            channelConfig,
            delivery -> handlePayload(delivery.payload())
        );
        if (channel.isActive()) {
            plugin.getLogger().info("[EntityTracker] 跨服 Boss 排行通道已启用");
        }
    }

    public void shutdown() {
        if (channel != null) {
            channel.close();
            channel = null;
        }
    }

    public boolean isActive() {
        return channel != null && channel.isActive();
    }

    public void publishSettlement(BossDamageSettlementRecord record, @Nullable Location location) {
        if (channel == null || !channel.isActive() || record == null) {
            return;
        }
        String nodeId = crossServer.nodeId();
        String worldName = location == null || location.getWorld() == null ? "" : location.getWorld().getName();
        Double locX = location == null ? null : location.getX();
        Double locY = location == null ? null : location.getY();
        Double locZ = location == null ? null : location.getZ();
        LocalDateTime now = LocalDateTime.now();

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
            try {
                if (damageDao.insertOrUpdateIfBetter(candidate)) {
                    channel.publish(EntityTrackerCrossServerPayloadCodec.encodeBestDamage(candidate));
                }
            } catch (SQLException exception) {
                plugin.getLogger().warning("[EntityTracker] 写入 Boss 最高伤害失败: " + exception.getMessage());
            }
        }
    }

    private void handlePayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return;
        }
        try {
            PlayerBossBestDamage remote = EntityTrackerCrossServerPayloadCodec.decode(payload);
            if (remote.getPlayerUuid() == null || remote.getPlayerUuid().isBlank()
                || remote.getBossId() == null || remote.getBossId().isBlank()) {
                return;
            }
            damageDao.insertOrUpdateIfBetter(remote);
        } catch (IllegalArgumentException ignored) {
        } catch (SQLException exception) {
            plugin.getLogger().warning("[EntityTracker] 合并跨服 Boss 伤害失败: " + exception.getMessage());
        }
    }
}
