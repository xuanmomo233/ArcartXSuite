package xuanmo.arcartxsuite.entitytracker.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.entitytracker.boss.tracker.BossDamageSettlementEntry;
import xuanmo.arcartxsuite.entitytracker.boss.tracker.BossDamageSettlementRecord;
import xuanmo.arcartxsuite.entitytracker.boss.tracker.BossSession;
import xuanmo.arcartxsuite.entitytracker.config.DropRecordingSettings;
import xuanmo.arcartxsuite.entitytracker.config.EntityTrackerNewFeaturesSettings;
import xuanmo.arcartxsuite.entitytracker.crossserver.EntityTrackerCrossServerService;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.entitytracker.dao.BossDropStatisticsDao;
import xuanmo.arcartxsuite.entitytracker.dao.BossKillRecordDao;
import xuanmo.arcartxsuite.entitytracker.entity.BossKillRecord;

/**
 * Boss 击杀落库、掉落统计与跨服击杀同步。
 */
public final class BossKillRecordingService implements Listener {

    private static final Gson GSON = new Gson();

    private final JavaPlugin plugin;
    private final EntityTrackerNewFeaturesSettings settings;
    private final BossKillRecordDao killRecordDao;
    private final BossDropStatisticsDao dropStatisticsDao;
    private final DropAllocationService dropAllocationService;
    private final EntityTrackerCrossServerService crossServerService;
    private final CrossServerRankingCacheService rankingCacheService;
    private final java.util.function.Supplier<String> nodeIdSupplier;
    private Supplier<EventBusCapability> eventBusProvider;
    private final Map<UUID, PendingDeathCapture> pendingDeaths = new ConcurrentHashMap<>();

    public BossKillRecordingService(
        JavaPlugin plugin,
        EntityTrackerNewFeaturesSettings settings,
        DataSource dataSource,
        DropAllocationService dropAllocationService,
        EntityTrackerCrossServerService crossServerService,
        CrossServerRankingCacheService rankingCacheService,
        java.util.function.Supplier<String> nodeIdSupplier
    ) {
        this.plugin = plugin;
        this.settings = settings;
        this.killRecordDao = new BossKillRecordDao(dataSource, plugin);
        this.dropStatisticsDao = new BossDropStatisticsDao(dataSource, plugin);
        this.dropAllocationService = dropAllocationService;
        this.crossServerService = crossServerService;
        this.rankingCacheService = rankingCacheService;
        this.nodeIdSupplier = nodeIdSupplier;
    }

    public void start() {
        if (settings.dropRecording().enabled() || settings.dropAllocation().enabled()) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
    }

    public void setEventBusProvider(Supplier<EventBusCapability> eventBusProvider) {
        this.eventBusProvider = eventBusProvider;
    }

    public void shutdown() {
        HandlerList.unregisterAll(this);
        pendingDeaths.clear();
    }

    public void recordBossDeath(
        BossSession session,
        LivingEntity entity,
        BossDamageSettlementRecord settlement,
        Location location
    ) {
        if (session == null || settlement == null) {
            return;
        }
        boolean recording = settings.dropRecording().enabled();
        boolean allocating = settings.dropAllocation().enabled();
        boolean crossServer = settings.crossServerRanking().enabled();
        if (!recording && !allocating && !crossServer) {
            return;
        }

        UUID entityUuid = entity == null ? null : entity.getUniqueId();
        if (entityUuid != null && (recording || allocating)) {
            pendingDeaths.put(entityUuid, new PendingDeathCapture(
                session.getDefinition().mythicMobId(),
                settlement.bossDisplayName(),
                settlement,
                location,
                session.getSpawnedAt()
            ));
            Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> finalizeDeath(entityUuid, session, settlement, location),
                2L
            );
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                persistRecord(session, settlement, location, List.of());
            } catch (Exception exception) {
                plugin.getLogger().warning("[EntityTracker] Boss 击杀记录失败: " + exception.getMessage());
            }
        });
    }

    private void finalizeDeath(
        UUID entityUuid,
        BossSession session,
        BossDamageSettlementRecord settlement,
        Location location
    ) {
        PendingDeathCapture pending = pendingDeaths.remove(entityUuid);
        List<DropItemSnapshot> drops = pending == null ? List.of() : pending.capturedDrops();
        try {
            persistRecord(session, settlement, location, drops);
        } catch (Exception exception) {
            plugin.getLogger().warning("[EntityTracker] Boss 击杀记录失败: " + exception.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        PendingDeathCapture pending = pendingDeaths.get(event.getEntity().getUniqueId());
        if (pending == null) {
            return;
        }
        List<DropItemSnapshot> drops = new ArrayList<>();
        for (ItemStack stack : event.getDrops()) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            drops.add(DropItemSnapshot.from(stack));
        }
        pendingDeaths.put(event.getEntity().getUniqueId(), pending.withDrops(drops));
    }

    private void persistRecord(
        BossSession session,
        BossDamageSettlementRecord settlement,
        Location location,
        List<DropItemSnapshot> drops
    ) throws SQLException {
        String nodeId = nodeIdSupplier.get();
        LocalDateTime killTime = LocalDateTime.now();
        long durationSeconds = Math.max(
            0L,
            (System.currentTimeMillis() - session.getSpawnedAt()) / 1000L
        );

        BossKillRecord record = new BossKillRecord();
        record.setBossId(settlement.mythicMobId());
        record.setBossDisplayName(settlement.bossDisplayName());
        record.setKillTime(killTime);
        record.setServerName(nodeId);
        record.setParticipantsJson(buildParticipantsJson(settlement));
        record.setDropsJson(GSON.toJson(drops));
        record.setTotalDamage((int) Math.round(settlement.totalDamage()));
        record.setDurationSeconds((int) durationSeconds);
        if (location != null && location.getWorld() != null) {
            record.setWorldName(location.getWorld().getName());
            record.setLocationX(location.getX());
            record.setLocationY(location.getY());
            record.setLocationZ(location.getZ());
        }

        long killId = killRecordDao.insert(record);
        record.setId(killId);

        if (settings.dropRecording().enabled()) {
            updateDropStatistics(record.getBossId(), nodeId, drops);
        }

        if (settings.dropAllocation().enabled() && dropAllocationService != null) {
            dropAllocationService.allocateAfterKill(record, settlement, drops, nodeId);
        }

        if (crossServerService != null && crossServerService.isActive()) {
            crossServerService.publishKillRecord(record);
        }

        if (rankingCacheService != null) {
            rankingCacheService.requestRefresh();
        }

        publishBossKillEvent(settlement);
    }

    private void publishBossKillEvent(BossDamageSettlementRecord settlement) {
        if (eventBusProvider == null) return;
        EventBusCapability eventBus = eventBusProvider.get();
        if (eventBus == null) return;
        var topEntry = settlement.topEntry();
        if (topEntry == null || topEntry.playerUuid() == null) return;
        org.bukkit.entity.Player player = Bukkit.getPlayer(topEntry.playerUuid());
        if (player == null || !player.isOnline()) return;
        java.util.Map<String, String> payload = new java.util.HashMap<>();
        payload.put("boss_id", settlement.mythicMobId());
        payload.put("boss_name", settlement.bossDisplayName());
        payload.put("damage_rank", String.valueOf(topEntry.rank()));
        eventBus.publish("axs.entitytracker.boss_kill", player, payload);
    }

    private void updateDropStatistics(String bossId, String serverName, List<DropItemSnapshot> drops)
        throws SQLException {
        dropStatisticsDao.incrementKillCount(bossId, serverName);
        for (DropItemSnapshot drop : drops) {
            dropStatisticsDao.recordDrop(bossId, drop.itemId(), drop.displayName(), serverName);
        }
        dropStatisticsDao.refreshDropRates(bossId, serverName);
    }

    private static String buildParticipantsJson(BossDamageSettlementRecord settlement) {
        JsonArray array = new JsonArray();
        for (BossDamageSettlementEntry entry : settlement.entriesByPlayer().values()) {
            if (entry == null || entry.playerUuid() == null) {
                continue;
            }
            JsonObject player = new JsonObject();
            player.addProperty("uuid", entry.playerUuid().toString());
            player.addProperty("name", entry.playerName());
            player.addProperty("damage", entry.damage());
            player.addProperty("rank", entry.rank());
            array.add(player);
        }
        return GSON.toJson(array);
    }

    private static List<DropItemSnapshot> captureDropsFromEntity(LivingEntity entity) {
        return List.of();
    }

    public void applyRemoteKillRecord(BossKillRecord record) throws SQLException {
        if (record == null) {
            return;
        }
        if (killRecordDao.existsByServerBossAndKillTime(
            record.getServerName(), record.getBossId(), record.getKillTime())) {
            return;
        }
        killRecordDao.insert(record);
        if (rankingCacheService != null) {
            rankingCacheService.requestRefresh();
        }
    }

    public void purgeOldRecords() {
        DropRecordingSettings recording = settings.dropRecording();
        if (!recording.enabled()) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                int removed = killRecordDao.deleteOlderThanDays(recording.retentionDays());
                if (removed > 0) {
                    plugin.getLogger().fine("[EntityTracker] 已清理 " + removed + " 条过期击杀记录");
                }
            } catch (SQLException exception) {
                plugin.getLogger().warning("[EntityTracker] 清理击杀记录失败: " + exception.getMessage());
            }
        });
    }

    private record PendingDeathCapture(
        String bossId,
        String bossDisplayName,
        BossDamageSettlementRecord settlement,
        Location location,
        long spawnedAt,
        List<DropItemSnapshot> capturedDrops
    ) {
        PendingDeathCapture(
            String bossId,
            String bossDisplayName,
            BossDamageSettlementRecord settlement,
            Location location,
            long spawnedAt
        ) {
            this(bossId, bossDisplayName, settlement, location, spawnedAt, List.of());
        }

        PendingDeathCapture withDrops(List<DropItemSnapshot> drops) {
            return new PendingDeathCapture(bossId, bossDisplayName, settlement, location, spawnedAt, drops);
        }
    }

    public record DropItemSnapshot(String itemId, String displayName, int amount) {
        static DropItemSnapshot from(ItemStack stack) {
            String itemId = stack.getType().name().toLowerCase(Locale.ROOT);
            String display = stack.hasItemMeta() && stack.getItemMeta().hasDisplayName()
                ? stack.getItemMeta().getDisplayName()
                : stack.getType().name();
            return new DropItemSnapshot(itemId, display, stack.getAmount());
        }
    }
}
