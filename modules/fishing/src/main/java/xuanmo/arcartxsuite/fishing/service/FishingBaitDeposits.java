package xuanmo.arcartxsuite.fishing.service;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.api.inventory.PlayerDataReceipt;
import xuanmo.arcartxsuite.api.scheduler.ModuleExecutor;
import xuanmo.arcartxsuite.fishing.storage.FishingRepository;
import xuanmo.arcartxsuite.fishing.storage.FishingRepository.BaitDeposit;
import xuanmo.arcartxsuite.fishing.storage.FishingRepository.BaitEdit;

/** Publishes a deposit when the source player's own save proves the inventory removal. */
final class FishingBaitDeposits {
    static final NamespacedKey RECEIPT = new NamespacedKey("arcartxsuite", "fishing_bait_deposit");
    static final long BOOT = ManagementFactory.getRuntimeMXBean().getStartTime();
    private static final int PAGE = 32;
    private final FishingRepository repository;
    private final ModuleExecutor worker;
    private final Path playerData;
    private final Path installation;
    private final Logger logger;
    private final Consumer<BaitDeposit> published;
    private final Semaphore stageSlots = new Semaphore(64);
    private final Set<BaitDeposit> unopened = ConcurrentHashMap.newKeySet();
    private final ArrayBlockingQueue<BaitDeposit> cancellations = new ArrayBlockingQueue<>(128);
    private final AtomicBoolean scanQueued = new AtomicBoolean();
    private final LinkedHashMap<UUID, FileVersion> unchanged = new LinkedHashMap<>();
    private volatile boolean closed;
    private volatile long idleUntil;
    private BukkitTask timer;
    private String node;
    private String after = "";

    FishingBaitDeposits(FishingRepository repository, ModuleExecutor worker, Path playerData, Path dataDirectory,
                        Logger logger, Consumer<BaitDeposit> published) {
        this.repository = repository;
        this.worker = worker;
        this.playerData = playerData;
        this.installation = dataDirectory.resolve("bait-deposit-installation.id");
        this.logger = logger;
        this.published = published;
    }

    void start(JavaPlugin plugin) {
        timer = plugin.getServer().getScheduler().runTaskTimer(plugin, this::requestScan, 20L, 100L);
    }

    BaitDeposit reserve(BaitEdit expected, UUID request, String bait, int amount) throws IOException, SQLException {
        if (closed || !stageSlots.tryAcquire()) return null;
        boolean retained = false;
        try {
            var deposit = new BaitDeposit(expected.season().uuid(), request, sourceNode(), BOOT,
                expected.first().slotIndex(), bait, amount);
            if (!repository.reserveBaitDeposit(expected, deposit)) return null;
            unopened.add(deposit);
            retained = true;
            idleUntil = 0;
            if (closed) {
                cancel(deposit);
                return null;
            }
            return deposit;
        } finally {
            if (!retained) stageSlots.release();
        }
    }

    boolean beginRemoval(BaitDeposit deposit) {
        if (closed || !unopened.remove(deposit)) return false;
        stageSlots.release();
        return true;
    }

    void cancel(BaitDeposit deposit) {
        if (unopened.remove(deposit)) stageSlots.release();
        if (!cancellations.offer(deposit)) {
            logger.warning("钓鱼饵料暂存等待原服重启核对: request=" + deposit.request());
        }
        requestScan();
    }

    void requestScan() {
        if (closed || !scanQueued.compareAndSet(false, true)) return;
        try {
            worker.execute(() -> {
                try {
                    drainCancellations();
                    scan();
                } catch (IOException | SQLException | RuntimeException error) {
                    logger.fine("钓鱼饵料存档核对暂缓: " + error.getMessage());
                } finally {
                    scanQueued.set(false);
                }
            });
        } catch (RejectedExecutionException busy) {
            scanQueued.set(false);
        }
    }

    private void drainCancellations() throws SQLException {
        for (BaitDeposit deposit; (deposit = cancellations.peek()) != null; ) {
            repository.cancelBaitDeposit(deposit);
            cancellations.remove();
            unchanged.remove(deposit.request());
        }
    }

    void scan() throws IOException, SQLException {
        if (closed || idleUntil != 0 && System.nanoTime() - idleUntil < 0) return;
        idleUntil = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        var rows = repository.pendingBaitDeposits(sourceNode(), after, PAGE);
        if (!rows.isEmpty() || !after.isEmpty()) idleUntil = 0;
        after = rows.size() == PAGE ? rows.get(rows.size() - 1).player().toString() : "";
        for (BaitDeposit row : rows) {
            if (closed) break;
            try {
                inspect(row);
            } catch (IOException | SQLException | RuntimeException failure) {
                logger.fine("钓鱼饵料保存核对暂缓 " + row.request() + ": " + failure.getMessage());
            }
        }
    }

    void inspect(BaitDeposit row) throws IOException, SQLException {
        if (unopened.contains(row)) return;
        Path file = playerData.resolve(row.player() + ".dat");
        FileVersion before = version(file);
        if (before.equals(unchanged.get(row.request()))) return;
        String marker = PlayerDataReceipt.readExisting(file, RECEIPT.toString());
        if (!before.equals(version(file))) return;
        if (row.request().toString().equals(marker)) {
            if (repository.publishBaitDeposit(row)) {
                unchanged.remove(row.request());
                published.accept(row);
            }
        } else if (row.boot() != BOOT) {
            repository.cancelBaitDeposit(row);
        } else {
            if (unchanged.size() >= 128) unchanged.remove(unchanged.keySet().iterator().next());
            unchanged.put(row.request(), before);
        }
    }

    private static FileVersion version(Path path) throws IOException {
        var attributes = Files.readAttributes(path, BasicFileAttributes.class);
        if (!attributes.isRegularFile()) throw new IOException("玩家存档不是普通文件");
        return new FileVersion(attributes.lastModifiedTime(), attributes.size(), attributes.fileKey());
    }

    private String sourceNode() throws IOException {
        if (node == null) {
            try {
                Files.writeString(installation, UUID.randomUUID().toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, StandardOpenOption.SYNC);
            } catch (FileAlreadyExistsException ignored) {
                // The installation identity binds this record to the original player save.
            }
            node = UUID.fromString(Files.readString(installation, StandardCharsets.UTF_8).trim()).toString();
        }
        return node;
    }

    void close() {
        closed = true;
        if (timer != null) timer.cancel();
        for (var deposit : unopened) cancel(deposit);
        try {
            worker.execute(() -> {
                try {
                    drainCancellations();
                } catch (SQLException failure) {
                    logger.fine("钓鱼饵料未开工暂存将在原服恢复: " + failure.getMessage());
                }
            });
        } catch (RejectedExecutionException busy) {
            // Unapplied rows can be released from the source file after the JVM exits.
        }
    }

    private record FileVersion(FileTime modified, long size, Object key) {}
}
