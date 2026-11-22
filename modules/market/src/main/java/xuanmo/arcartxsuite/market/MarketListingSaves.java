package xuanmo.arcartxsuite.market;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.logging.Logger;
import org.bukkit.NamespacedKey;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.api.inventory.PlayerDataReceipt;
import xuanmo.arcartxsuite.market.storage.MarketRepository;
import xuanmo.arcartxsuite.market.storage.MarketListingDraft.Kind;
import xuanmo.arcartxsuite.market.storage.MarketListingDraft.Save;

/** Confirms naturally saved inventory removals on the node that applied them. */
final class MarketListingSaves implements AutoCloseable {
    private static final NamespacedKey PLAYER_SHOP_RECEIPT = new NamespacedKey("arcartxsuite", Kind.PLAYER_SHOP.receipt());
    private static final NamespacedKey AUCTION_RECEIPT = new NamespacedKey("arcartxsuite", Kind.AUCTION.receipt());
    static final long SERVER_START = java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime();
    private static final int PAGE_SIZE = 32;
    private static final int MAX_UNCHANGED_FILES = 128;

    private final MarketWork work;
    private final MarketRepository repository;
    private final Logger logger;
    private final Path playerData;
    private final String node;
    private final BiConsumer<Save, Boolean> completed;
    private final AtomicBoolean reading = new AtomicBoolean();
    private final LinkedHashMap<String, FileVersion> unchanged = new LinkedHashMap<>();
    private final LinkedHashMap<String, Save> unconfirmed = new LinkedHashMap<>();
    private volatile boolean closed;
    private volatile long idleUntil;
    private BukkitTask timer;
    private String cursor = "";

    MarketListingSaves(MarketWork work, MarketRepository repository, Logger logger, Path playerData, String node,
                           BiConsumer<Save, Boolean> completed) {
        this.work = work;
        this.repository = repository;
        this.logger = logger;
        this.playerData = playerData;
        this.node = node;
        this.completed = completed;
    }

    String node() { return node; }

    static NamespacedKey receipt(Kind kind) {
        return kind == Kind.AUCTION ? AUCTION_RECEIPT : PLAYER_SHOP_RECEIPT;
    }

    void start() { timer = work.repeatAsync(this::scan, 20L, 100L); }

    void wake() { idleUntil = 0; }

    void scan() {
        if (closed || idleUntil != 0 && System.nanoTime() - idleUntil < 0 || !reading.compareAndSet(false, true)) return;
        try {
            // Set the idle deadline before querying so a concurrent wake survives an empty result.
            idleUntil = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            if (!unconfirmed.isEmpty()) {
                idleUntil = 0;
                for (var row : List.copyOf(unconfirmed.values())) {
                    if (closed) return;
                    publish(row, true);
                }
            }
            int limit = PAGE_SIZE - unconfirmed.size();
            if (limit == 0) return;
            var rows = repository.listingSaves(node, cursor, limit);
            if (!rows.isEmpty() || !cursor.isEmpty()) idleUntil = 0;
            cursor = rows.size() == limit ? rows.get(rows.size() - 1).id() : "";
            for (var row : rows) {
                if (closed) return;
                if (unconfirmed.containsKey(row.id())) continue;
                try { inspect(row); }
                catch (IOException | RuntimeException failure) {
                    logger.fine("[PlayerShop] 上架存档核对暂缓 " + row.id() + ": " + failure.getMessage());
                }
            }
        } catch (RuntimeException failure) {
            logger.fine("[PlayerShop] 等待存档的上架暂不可读: " + failure.getMessage());
        } finally {
            reading.set(false);
        }
    }

    private void inspect(Save row) throws IOException {
        Path file = playerFile(row.seller());
        var version = version(file);
        if (version.equals(unchanged.get(row.id()))) return;
        String saved = PlayerDataReceipt.readExisting(file, receipt(row.kind()).toString());
        if (row.id().equals(saved)) {
            publish(row, false);
        } else if (row.serverStart() != SERVER_START && version.equals(version(file))) {
            // The source JVM exited and the readable player file is missing this removal receipt.
            if (repository.abortListingDraft(row, node, "重启后原存档没有物品扣除凭证，已释放暂存")) {
                completed.accept(row, false);
            }
        } else {
            if (unchanged.size() >= MAX_UNCHANGED_FILES) unchanged.remove(unchanged.keySet().iterator().next());
            unchanged.put(row.id(), version);
        }
    }

    private void publish(Save row, boolean retry) {
        boolean published;
        try {
            published = retry && repository.isListingDraftPublished(row, node)
                || repository.publishListingDraft(row, node);
        } catch (RuntimeException failure) {
            // A lost commit response can remove the draft from listingSaves before completion reaches the caller.
            unconfirmed.put(row.id(), row);
            logger.fine("[Market] 上架提交待确认 " + row.id() + ": " + failure.getMessage());
            return;
        }
        unconfirmed.remove(row.id());
        if (published) completed.accept(row, true);
    }

    private Path playerFile(UUID seller) { return playerData.resolve(seller + ".dat"); }

    private FileVersion version(Path file) throws IOException {
        var attrs = Files.readAttributes(file, BasicFileAttributes.class);
        if (!attrs.isRegularFile()) throw new IOException("玩家存档不是普通文件");
        return new FileVersion(attrs.lastModifiedTime(), attrs.size(), attrs.fileKey());
    }

    @Override
    public void close() {
        closed = true;
        if (timer != null) timer.cancel();
    }

    private record FileVersion(java.nio.file.attribute.FileTime modified, long size, Object key) {}
}
