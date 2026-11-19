package xuanmo.arcartxsuite.fishing.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.item.PendingRewardService;
import xuanmo.arcartxsuite.api.item.PendingRewardService.PendingReward;
import xuanmo.arcartxsuite.api.scheduler.ModuleExecutor;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.fishing.model.FishingCatchRecord;
import xuanmo.arcartxsuite.fishing.model.FishingSeasonData;
import xuanmo.arcartxsuite.fishing.storage.FishingRepository;
import xuanmo.arcartxsuite.fishing.storage.FishingRepository.BaitEdit;
import xuanmo.arcartxsuite.fishing.storage.FishingRepository.CatchDelivery;
import xuanmo.arcartxsuite.fishing.storage.FishingRepository.CatchRewards;
import xuanmo.arcartxsuite.fishing.storage.FishingRepository.ConditionalReward;

/** Commits capture facts independently of host reward availability. */
final class FishingCatchWork {
    static final int MAX_CAPTURES = 128;
    private static final int RECOVERY_PAGE = 16;
    private static final long RETRY_MILLIS = 10_000L;

    private final FishingRepository repository;
    private final PendingRewardService host;
    private final Logger logger;
    private final Consumer<Set<UUID>> flush;
    private final ModuleExecutor commits = executor("AXS-Fishing-Capture", MAX_CAPTURES);
    private final ModuleExecutor deliveries = executor("AXS-Fishing-CaptureDelivery", 1);
    private final AtomicBoolean sweeping = new AtomicBoolean();
    private final CompletableFuture<Void> stopped = new CompletableFuture<>();
    private final Path localIdentity;
    private volatile String owner;
    private volatile boolean closing;
    private FishingSales saleRecovery;

    FishingCatchWork(FishingRepository repository, PendingRewardService host, @Nullable StorageDescriptor sharedHostStorage,
                       Path dataFolder, Logger logger, Consumer<Set<UUID>> flush) {
        this.repository = repository;
        this.host = host;
        this.localIdentity = dataFolder.resolve("capture-delivery-installation.id");
        this.logger = logger;
        this.flush = flush;
        if (sharedHostStorage != null && sharedHostStorage.isMysql()) {
            String identity = sharedHostStorage.host().toLowerCase(Locale.ROOT) + ":" + sharedHostStorage.port()
                + "/" + sharedHostStorage.database();
            owner = "shared:" + UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static ModuleExecutor executor(String name, int capacity) {
        return new ModuleExecutor(0, 1, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(capacity), work -> {
            var thread = new Thread(work, name);
            thread.setDaemon(true);
            return thread;
        });
    }

    synchronized String owner() throws IOException {
        if (owner != null) return owner;
        try {
            Files.writeString(localIdentity, UUID.randomUUID().toString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, StandardOpenOption.SYNC);
        } catch (FileAlreadyExistsException ignored) {
            // A previous startup created the ID for this host's local pending store.
        }
        owner = "local:" + UUID.fromString(Files.readString(localIdentity, StandardCharsets.UTF_8).trim());
        return owner;
    }

    CompletableFuture<FishingRepository.CatchSettlement> settle(FishingCatchRecord record, String seasonId,
            UnaryOperator<FishingSeasonData> update, boolean treasure, List<ConditionalReward> rewards) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var result = repository.settleCatch(record, seasonId, update, new CatchRewards(owner(), treasure, rewards));
                requestSweep();
                return result;
            } catch (SQLException | IOException error) {
                throw new CompletionException(error);
            }
        }, commits);
    }

    CompletableFuture<Boolean> withdrawBait(BaitEdit expected, int amount, UUID request, PendingReward reward) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                boolean committed = repository.withdrawBait(expected, amount, request, owner(), reward);
                if (committed) requestSweep();
                return committed;
            } catch (SQLException | IOException error) {
                throw new CompletionException(error);
            }
        }, commits);
    }

    void requestSweep() {
        if (closing || !sweeping.compareAndSet(false, true)) return;
        try {
            deliveries.execute(() -> {
                try {
                    sweep();
                } catch (IOException | SQLException | RuntimeException error) {
                    logger.log(Level.WARNING, "钓鱼待交付奖励扫描失败", error);
                } finally {
                    sweeping.set(false);
                }
            });
        } catch (RejectedExecutionException stopped) {
            sweeping.set(false);
        }
    }

    void setSaleRecovery(FishingSales sales) {
        saleRecovery = sales;
    }

    private void sweep() throws IOException, SQLException {
        if (saleRecovery != null) {
            try {
                saleRecovery.recoverReservedSales();
            } catch (IOException | SQLException | RuntimeException failure) {
                logger.fine("售鱼预约恢复暂缓: " + failure.getMessage());
            }
        }
        Set<UUID> accepted = new LinkedHashSet<>();
        try {
            var page = repository.pendingCatchDeliveries(owner(), System.currentTimeMillis(), RECOVERY_PAGE);
            for (CatchDelivery delivery : page) {
                if (closing) break;
                try {
                    if (host.savePendingBatchConfirmed("capture:" + delivery.eventId(), delivery.rewards())) {
                        accepted.add(delivery.player());
                        repository.finishCatchDelivery(delivery);
                    } else {
                        repository.deferCatchDelivery(delivery, System.currentTimeMillis() + RETRY_MILLIS);
                    }
                } catch (SQLException | RuntimeException error) {
                    logger.log(Level.WARNING, "钓鱼奖励交接未确认: event=" + delivery.eventId(), error);
                    repository.deferCatchDelivery(delivery, System.currentTimeMillis() + RETRY_MILLIS);
                }
            }
        } finally {
            if (!closing && !accepted.isEmpty()) flush.accept(accepted);
        }
    }

    void flush(org.bukkit.entity.Player player) {
        host.flushPlayer(player);
    }

    void shutdown() {
        closing = true;
        commits.shutdown();
        deliveries.shutdown();
        CompletableFuture.allOf(commits.stopped().toCompletableFuture(), deliveries.stopped().toCompletableFuture())
            .whenComplete((ignored, error) -> {
                if (error == null) stopped.complete(null);
                else stopped.completeExceptionally(error);
            });
    }

    CompletableFuture<Void> stopped() {
        return stopped.copy();
    }
}
