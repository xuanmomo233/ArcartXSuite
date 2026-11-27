package xuanmo.arcartxsuite.entitytracker.service;

import xuanmo.arcartxsuite.api.BuildEdition;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.logging.*;
import javax.sql.DataSource;
import org.bukkit.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.api.item.PendingRewardService;
import xuanmo.arcartxsuite.api.scheduler.*;
import xuanmo.arcartxsuite.api.util.ItemSerializer;
import xuanmo.arcartxsuite.entitytracker.boss.tracker.*;
import xuanmo.arcartxsuite.entitytracker.config.*;
import xuanmo.arcartxsuite.entitytracker.crossserver.EntityTrackerCrossServerService;
import xuanmo.arcartxsuite.entitytracker.dao.BossKillRecordDao;
import xuanmo.arcartxsuite.entitytracker.entity.BossKillRecord;
import xuanmo.arcartxsuite.entitytracker.storage.KillSettlementStore;

/** Main captures immutable Bukkit state; one retiring worker commits and hands off rewards. */
public final class BossKillRecordingService implements Listener {
    private static final int RECOVERY_PAGE = 64;
    private static final int FLUSH_PLAYERS_PER_TICK = 32;
    private static final String DROP_JOURNAL_SUFFIX = ".drops.json";
    private static final Gson JSON=new GsonBuilder()
        .registerTypeAdapter(LocalDateTime.class,(JsonSerializer<LocalDateTime>)(value,type,context)->new JsonPrimitive(value.toString()))
        .registerTypeAdapter(LocalDateTime.class,(JsonDeserializer<LocalDateTime>)(value,type,context)->LocalDateTime.parse(value.getAsString())).create();
    private final JavaPlugin plugin;
    private final Logger logger;
    private final EntityTrackerNewFeaturesSettings settings;
    private final BossKillRecordDao killRecords;
    private final KillSettlementStore store;
    private final EntityTrackerCrossServerService cross;
    private final CrossServerRankingCacheService cache;
    private final Supplier<String> nodeId;
    private final PendingRewardService rewards;
    private final Path outbox;
    private final xuanmo.arcartxsuite.api.message.MessageProvider messages;
    private final Map<UUID,Capture> pending=new LinkedHashMap<>();
    private final ModuleCallback mainCallbacks=new ModuleCallback(()->{});
    private final ModuleScheduledExecutor worker=new ModuleScheduledExecutor(1,task->{
        var thread=new Thread(task,"AXS-EntityTracker-Kills");thread.setDaemon(true);return thread;
    });
    private Supplier<EventBusCapability> events;
    private BukkitTask captureTask;
    private ScheduledFuture<?> retry;
    private long recoverAfter;
    private long nextOutboxScan;
    private boolean recoverHadFailure;
    private DirectoryStream<Path> outboxScan;
    private Iterator<Path> outboxCursor;
    private boolean outboxNeedsRescan;
    private final Set<UUID> flushPlayers=ConcurrentHashMap.newKeySet();
    private final java.util.concurrent.atomic.AtomicBoolean flushQueued=new java.util.concurrent.atomic.AtomicBoolean();
    private volatile boolean closed;

    public BossKillRecordingService(JavaPlugin plugin,Logger logger,EntityTrackerNewFeaturesSettings settings,
            DataSource source,PendingRewardService rewards,EntityTrackerCrossServerService cross,
            CrossServerRankingCacheService cache,Supplier<String> nodeId,Path outbox,
            xuanmo.arcartxsuite.api.message.MessageProvider messages) {
        this.plugin=plugin;this.logger=logger;this.settings=settings;this.rewards=rewards;
        this.cross=cross;this.cache=cache;this.nodeId=nodeId;this.outbox=outbox;
        this.messages=messages;
        killRecords=new BossKillRecordDao(source,logger);
        store=new KillSettlementStore(source,new KillAllocation(rewards));
        worker.setKeepAliveTime(30,TimeUnit.SECONDS);worker.allowCoreThreadTimeOut(true);
    }

    public void start() {
        if(settings.dropRecording().enabled()||settings.dropAllocation().enabled())Bukkit.getPluginManager().registerEvents(this,plugin);
        if(!"manual".equals(KillAllocation.effectiveMode(settings.dropAllocation())))worker.execute(()->{
            try{ItemSerializationWarmup.prepare();}
            catch(Exception failure){logger.log(Level.FINE,"物品序列化预初始化未完成，首次掉落按正常路径处理",failure);}
        });
        worker.execute(this::recover);
        if (rewards != null) onMain(() -> {
            for (var player : Bukkit.getOnlinePlayers()) flushPlayers.add(player.getUniqueId());
            scheduleFlush();
        });
    }

    public void setEventBusProvider(Supplier<EventBusCapability> provider){events=provider;}

    public void shutdown() {
        if(closed)return;
        closed=true;
        if(captureTask!=null){captureTask.cancel();captureTask=null;}
        // No JDBC or filesystem work here. Accepted captures still reach the retiring worker.
        drainCaptures();
        mainCallbacks.close();
        flushPlayers.clear();
        HandlerList.unregisterAll(this);
        worker.execute(() -> {
            try { closeOutboxScan(); }
            finally { worker.shutdown(); }
        });
    }

    public CompletionStage<Void> databaseStopped() {
        return CompletableFuture.allOf(worker.stopped().toCompletableFuture(),mainCallbacks.stopped().toCompletableFuture());
    }

    public void recordBossDeath(BossSession session,LivingEntity entity,BossDamageSettlementRecord settlement,Location location) {
        if(closed||session==null||settlement==null)return;
        if(!settings.needsDatabase())return;
        String server=Objects.requireNonNullElse(nodeId.get(),"");
        // The UI settlement sequence resets on reload. The persisted entity UUID identifies this boss life.
        String key=UUID.fromString(settlement.entityUuid()).toString();
        var record=new BossKillRecord();
        record.setSettlementKey(key);
        record.setBossId(settlement.mythicMobId());record.setBossDisplayName(settlement.bossDisplayName());record.setServerName(server);
        record.setKillTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(settlement.settledAtMillis()),ZoneId.systemDefault()));
        record.setTotalDamage((int)Math.min(Integer.MAX_VALUE,Math.max(0,Math.round(settlement.totalDamage()))));
        record.setDurationSeconds((int)Math.min(Integer.MAX_VALUE,Math.max(0,(settlement.settledAtMillis()-session.getSpawnedAt())/1000)));
        if(location!=null&&location.getWorld()!=null){record.setWorldName(location.getWorld().getName());record.setLocationX(location.getX());record.setLocationY(location.getY());record.setLocationZ(location.getZ());}
        var capture=new Capture(key,record,settlement,List.of(),settings.dropRecording().enabled()&&settings.dropRecording().enableStatistics(),settings.dropAllocation(),false);
        if(entity!=null&&(settings.dropRecording().enabled()||settings.dropAllocation().enabled())) {
            pending.putIfAbsent(entity.getUniqueId(),capture);
            if(captureTask==null)captureTask=Bukkit.getScheduler().runTaskLater(plugin,()->{captureTask=null;if(!closed)drainCaptures();},2);
        }else submit(capture);
    }

    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onEntityDeath(EntityDeathEvent event) {
        var capture=pending.get(event.getEntity().getUniqueId());
        if(closed||capture==null)return;
        boolean automatic=KillAllocation.allocatesItems(capture.allocation(),capture.settlement());
        var drops=new ArrayList<DropItemSnapshot>();
        try {
            for(var item:event.getDrops())if(item!=null&&!item.getType().isAir())drops.add(DropItemSnapshot.from(item,automatic));
        }catch(RuntimeException failure) {
            // Keep ownership with the original ground drops when capture cannot preserve the item.
            logger.log(Level.WARNING,"无法保存原始掉落物，保留地面掉落并取消此次自动分配",failure);
            return;
        }
        Capture finalized = capture.withDrops(List.copyOf(drops));
        if (automatic && !drops.isEmpty()) {
            Capture owned = captureDrops(finalized);
            if (owned != null) {
                // Vanilla must not spawn another copy after the journal has taken ownership.
                event.getDrops().clear();
                finalized = owned;
            } else {
                finalized = finalized.withVanillaDrops();
            }
        }
        pending.put(event.getEntity().getUniqueId(), finalized);
    }

    private void drainCaptures() {
        var captures=List.copyOf(pending.values());pending.clear();
        captures.forEach(this::submit);
    }

    private void submit(Capture capture) {
        worker.execute(() -> {
            if (!process(capture)) {
                if (outboxScan != null) outboxNeedsRescan = true;
            } else if (capture.ownsDrops()) {
                try { Files.deleteIfExists(dropJournal(capture)); }
                catch (IOException failure) {
                    if (outboxScan != null) outboxNeedsRescan = true;
                    logger.log(Level.WARNING, "Boss 掉落已提交，恢复凭证暂未清理: " + capture.key(), failure);
                    scheduleRecovery();
                }
            }
        });
    }

    private boolean process(Capture capture) {
        KillSettlementStore.Stored stored;
        try {
            if (capture.record().getParticipantsJson() == null) {
                capture.record().setParticipantsJson(participantsJson(capture.settlement()));
            }
            capture.record().setDropsJson(JSON.toJson(capture.drops().stream().map(drop->Map.of("itemId",drop.itemId(),"displayName",drop.displayName(),"amount",drop.amount())).toList()));
            stored=store.commit(capture.key(),capture.record(),capture.settlement(),capture.drops(),capture.statistics(),capture.allocation());
        }catch(SQLException|RuntimeException failure) {
            logger.log(Level.WARNING,"击杀事务未确认，保留恢复记录: "+capture.key(),failure);
            if (!capture.ownsDrops()) saveFailed(capture);
            scheduleRecovery();return false;
        }
        capture.record().setId(stored.killId());
        if(stored.created())onMain(()->publishKillEvent(capture));
        try {
            if (store.handoff(stored, this::rewardAccepted)) announceAllocation(stored);
            else scheduleRecovery();
        }
        catch(SQLException|RuntimeException failure){logger.log(Level.WARNING,"掉落仍由分配记录持有，等待宿主接收: "+capture.key(),failure);scheduleRecovery();}
        finally { scheduleFlush(); }
        if (BuildEdition.CROSS_SERVER_INCLUDED && cache != null && stored.created()) cache.requestKillRefresh();
        if(BuildEdition.CROSS_SERVER_INCLUDED&&cross!=null) {
            cross.publishSettlement(capture.settlement(),capture.record());
            cross.publishKillRecord(capture.record());
        }
        return true;
    }

    private void announceAllocation(KillSettlementStore.Stored stored) {
        if (!stored.created() || messages == null || stored.deliveries().isEmpty()) return;
        onMain(()->{
            for(var delivery:stored.deliveries())Bukkit.broadcastMessage(messages.get(
                "admin.drop-allocation",delivery.mode().toUpperCase(Locale.ROOT),delivery.itemName(),delivery.name(),delivery.score()));
        });
    }

    private void rewardAccepted(UUID player) {
        if (!closed) flushPlayers.add(player);
    }

    private void publishKillEvent(Capture capture) {
        if(events==null)return;
        var top=capture.settlement().topEntry();var bus=events.get();
        if(top!=null&&bus!=null){var player=Bukkit.getPlayer(top.playerUuid());if(player!=null)bus.publish("axs.entitytracker.boss_kill",player,Map.of(
            "boss_id",capture.settlement().mythicMobId(),"boss_name",capture.settlement().bossDisplayName(),"damage_rank",String.valueOf(top.rank())));}
    }

    private void scheduleFlush() {
        if(closed||rewards==null||flushPlayers.isEmpty()||!flushQueued.compareAndSet(false,true))return;
        if (!onMain(()->{
            try {
                int visited = 0;
                for (var id : flushPlayers) {
                    if (!flushPlayers.remove(id)) continue;
                    var player = Bukkit.getPlayer(id);
                    if (player != null) rewards.flushPlayer(player);
                    if (++visited == FLUSH_PLAYERS_PER_TICK) break;
                }
            }finally{flushQueued.set(false);if(!flushPlayers.isEmpty())scheduleFlush();}
        })) flushQueued.set(false);
    }

    private boolean onMain(Runnable action) {
        if(closed)return false;
        try {
            Bukkit.getScheduler().runTask(plugin,()->mainCallbacks.call(()->{action.run();return null;},null));
            return true;
        } catch (RuntimeException | LinkageError failure) {
            if(!closed)logger.log(Level.WARNING,"击杀通知无法调度",failure);
            return false;
        }
    }

    private void recover() {
        retry=null;
        if (closed) return;
        long now = System.nanoTime();
        if (outboxScan != null || recoverAfter == 0L || now - nextOutboxScan >= 0L) {
            if (outboxScan == null) nextOutboxScan = now + TimeUnit.SECONDS.toNanos(30L);
            recoverOutbox();
        }
        try {
            var plans = store.pendingAfter(recoverAfter, RECOVERY_PAGE);
            for (var plan : plans) {
                recoverAfter = plan.killId();
                try {
                    if (!store.handoff(plan, this::rewardAccepted)) recoverHadFailure = true;
                } catch (SQLException | RuntimeException failure) {
                    recoverHadFailure = true;
                    logger.log(Level.WARNING, "待分配奖励仍待交接: " + plan.key(), failure);
                }
            }
            if (plans.size() == RECOVERY_PAGE) {
                scheduleRecovery(1L);
            } else {
                recoverAfter = 0L;
                if (recoverHadFailure) scheduleRecovery();
                recoverHadFailure = false;
            }
        } catch (SQLException | RuntimeException failure) {
            logger.log(Level.WARNING, "待分配奖励恢复暂不可用", failure);
            scheduleRecovery();
        } finally {
            scheduleFlush();
        }
    }

    private void recoverOutbox() {
        try {
            if (outboxScan == null) {
                if (outbox == null || !Files.isDirectory(outbox)) return;
                outboxScan = Files.newDirectoryStream(outbox);
                outboxCursor = outboxScan.iterator();
            }
            // Resume at the next directory entry before retrying any file that failed on this pass.
            for (int visited = 0; visited < RECOVERY_PAGE; visited++) {
                if (!outboxCursor.hasNext()) {
                    closeOutboxScan();
                    if (outboxNeedsRescan) {
                        outboxNeedsRescan = false;
                        scheduleRecovery();
                    }
                    return;
                }
                Path file = outboxCursor.next();
                if (!file.getFileName().toString().endsWith(".json")) continue;
                try {
                    if (process(JSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Capture.class))) {
                        Files.deleteIfExists(file);
                    } else outboxNeedsRescan = true;
                } catch (NoSuchFileException ignored) {
                    // This journal was removed while another page was being recovered.
                } catch (Exception failure) {
                    outboxNeedsRescan = true;
                    logger.log(Level.WARNING, "击杀恢复记录仍待处理: " + file, failure);
                }
            }
            scheduleRecovery(1L);
        } catch (IOException | RuntimeException failure) {
            closeOutboxScan();
            logger.log(Level.WARNING, "无法读取击杀恢复目录", failure);
            scheduleRecovery();
        }
    }

    private void closeOutboxScan() {
        if (outboxScan == null) return;
        try { outboxScan.close(); }
        catch (IOException failure) {
            logger.log(Level.WARNING, "无法关闭击杀恢复目录", failure);
        } finally {
            outboxScan = null;
            outboxCursor = null;
        }
    }

    private void scheduleRecovery() {
        scheduleRecovery(30L);
    }

    private void scheduleRecovery(long delaySeconds) {
        if (closed) return;
        if (retry != null && !retry.isDone()) {
            if (retry.getDelay(TimeUnit.NANOSECONDS) <= TimeUnit.SECONDS.toNanos(delaySeconds)) return;
            retry.cancel(false);
        }
        retry = worker.schedule(this::recover, delaySeconds, TimeUnit.SECONDS);
    }

    private void saveFailed(Capture capture) {
        if(outbox==null){logger.severe("未配置击杀恢复目录: "+capture.key());return;}
        Path target = outbox.resolve(capture.key() + ".json");
        try {
            if (!Files.exists(target)) writeJournal(target, capture, false);
        }catch(Exception failure){logger.log(Level.SEVERE,"击杀恢复记录未能保存: "+capture.key(),failure);}
    }

    private Capture captureDrops(Capture capture) {
        if (outbox == null) return null;
        Path target = dropJournal(capture);
        try {
            if (Files.exists(target)) {
                Capture existing = readDropJournal(target, capture.key());
                if (existing != null) return existing;
            }
            if (Files.exists(outbox.resolve(capture.key() + ".json"))) return null;
            Capture owned = capture.asDropOwner();
            try {
                writeJournal(target, owned, true);
            } catch (IOException uncertain) {
                Capture stored = readDropJournal(target, capture.key());
                if (stored != null) return stored;
                throw uncertain;
            }
            return owned;
        } catch (IOException | RuntimeException failure) {
            logger.log(Level.WARNING, "Boss 掉落恢复凭证未确认，保留原版掉落: " + capture.key(), failure);
            return null;
        }
    }

    private Path dropJournal(Capture capture) {
        return outbox.resolve(capture.key() + DROP_JOURNAL_SUFFIX);
    }

    private static Capture readDropJournal(Path path, String key) throws IOException {
        try {
            Capture captured = JSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), Capture.class);
            if (captured == null || !captured.ownsDrops() || !key.equals(captured.key()) || captured.drops() == null
                    || captured.drops().isEmpty() || captured.drops().stream().anyMatch(drop ->
                        drop == null || drop.itemData() == null || drop.itemData().isBlank() || drop.amount() <= 0)) {
                throw new IOException("无效的 Boss 掉落恢复凭证");
            }
            return captured;
        } catch (NoSuchFileException completed) {
            // Recovery may finish and remove the existing journal during a replayed death.
            return null;
        }
    }

    private void writeJournal(Path target, Capture capture, boolean atomicRequired) throws IOException {
        Files.createDirectories(outbox);
        Path temp = Files.createTempFile(outbox, ".kill-", ".tmp");
        try {
            Files.writeString(temp, JSON.toJson(capture), StandardCharsets.UTF_8);
            try (var channel = java.nio.channels.FileChannel.open(temp, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException unsupported) {
                if (atomicRequired) throw unsupported;
                Files.move(temp, target);
            }
        } finally {
            try { Files.deleteIfExists(temp); }
            catch (IOException cleanup) { logger.log(Level.FINE, "无法清理击杀临时文件", cleanup); }
        }
    }

    public void applyRemoteKillRecord(BossKillRecord record) throws SQLException {
        if (!BuildEdition.CROSS_SERVER_INCLUDED || record == null) return;
        var stored = store.commit(record.getSettlementKey(), record, null, List.of(), false, DropAllocationSettings.disabled());
        if (cache != null && stored.created()) cache.requestKillRefresh();
    }

    public void purgeOldRecords() {
        if(!closed&&settings.dropRecording().enabled())
            worker.execute(()->purgeBatch(LocalDateTime.now().minusDays(settings.dropRecording().retentionDays())));
    }

    private void purgeBatch(LocalDateTime cutoff) {
        if(closed)return;
        try {
            int deleted = killRecords.deleteExpiredBatch(cutoff, 256);
            if (BuildEdition.CROSS_SERVER_INCLUDED && deleted > 0 && cache != null) cache.requestKillRefresh();
            if(deleted==256&&!closed) {
                try{worker.execute(()->purgeBatch(cutoff));}
                catch(RejectedExecutionException retiring){if(!closed)throw retiring;}
            }
        }catch(SQLException failure){logger.log(Level.WARNING,"击杀历史清理失败",failure);}
    }

    private static String participantsJson(BossDamageSettlementRecord settlement) {
        var values=new JsonArray();for(var entry:settlement.entriesByPlayer().values())if(entry!=null&&entry.playerUuid()!=null) {
            var row=new JsonObject();row.addProperty("uuid",entry.playerUuid().toString());row.addProperty("name",entry.playerName());
            row.addProperty("damage",entry.damage());row.addProperty("rank",entry.rank());values.add(row);
        }return JSON.toJson(values);
    }

    private record Capture(String key,BossKillRecord record,BossDamageSettlementRecord settlement,List<DropItemSnapshot> drops,
                           boolean statistics,DropAllocationSettings allocation,boolean ownsDrops) {
        Capture withDrops(List<DropItemSnapshot> values){return new Capture(key,record,settlement,values,statistics,allocation,ownsDrops);}
        Capture asDropOwner(){return new Capture(key,record,settlement,drops,statistics,allocation,true);}
        Capture withVanillaDrops() {
            var manual = new DropAllocationSettings(allocation.enabled(), "manual", allocation.allocationTimeoutSeconds(),
                allocation.dkp(), allocation.roll(), allocation.priority());
            return new Capture(key, record, settlement, drops, statistics, manual, false);
        }
    }
    public record DropItemSnapshot(String itemId,String displayName,int amount,String itemData) {
        static DropItemSnapshot from(ItemStack item,boolean preserve) {
            var meta=item.hasItemMeta()?item.getItemMeta():null;
            return new DropItemSnapshot(item.getType().name().toLowerCase(Locale.ROOT),meta!=null&&meta.hasDisplayName()?meta.getDisplayName():item.getType().name(),
                item.getAmount(),preserve?Base64.getEncoder().encodeToString(ItemSerializer.serialize(item)):null);
        }
    }
}
