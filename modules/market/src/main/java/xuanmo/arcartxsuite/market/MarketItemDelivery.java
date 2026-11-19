package xuanmo.arcartxsuite.market;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.logging.Logger;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.inventory.InventoryItems;
import xuanmo.arcartxsuite.api.inventory.PlayerDataReceipt;
import xuanmo.arcartxsuite.market.auction.AuctionItemSerializer;
import xuanmo.arcartxsuite.market.storage.MarketOperation;
import xuanmo.arcartxsuite.market.storage.MarketRepository;

/** The inventory and its bounded in-flight receipts are persisted in the same player file. */
final class MarketItemDelivery {
    static final String SAVING = "ITEM_SAVING";

    private static final NamespacedKey BATCH_RECEIPT = new NamespacedKey("arcartxsuite", "market_item_batch");
    private static final int MAX_BATCH = 8;

    private final JavaPlugin plugin;
    private final MarketWork work;
    private final MarketRepository repository;
    private final AuctionItemSerializer serializer;
    private final Logger logger;
    private final Path playerData;
    private final String node;
    private final Set<UUID> active = ConcurrentHashMap.newKeySet();

    MarketItemDelivery(MarketWork work, JavaPlugin plugin, MarketRepository repository, AuctionItemSerializer serializer, Logger logger,
                       Path playerData) throws IOException {
        this.plugin = plugin;this.work = work;
        this.repository = repository;
        this.serializer = serializer;
        this.logger = logger;
        this.playerData = playerData;
        Files.createDirectories(playerData);
        Path identity = playerData.resolve(".arcartxsuite-market-node");
        if (!Files.exists(identity)) {
            try {
                Files.writeString(identity, UUID.randomUUID().toString(), StandardOpenOption.CREATE_NEW);
            } catch (FileAlreadyExistsException ignored) {
                // Another initializer created the identity.
            }
        }
        this.node = UUID.fromString(Files.readString(identity).trim()).toString();
    }

    String node() {
        return node;
    }

    /** Delivers a bounded group using one player save and one atomic acknowledgement. */
    void deliver(Player player, List<MarketOperation> tasks, Predicate<Runnable> enqueue, BooleanSupplier canApply) {
        UUID playerId = player.getUniqueId();
        if (!active.add(playerId)) return;
        try {
            var pending = tasks.stream().filter(task -> "ITEM".equals(task.kind()) && SAVING.equals(task.state())
                && node.equals(task.owner())).toList();
            if (!pending.isEmpty()) {
                recover(player, pending, enqueue, canApply);
                return;
            }
            var contents = InventoryItems.copy(player.getInventory().getStorageContents());
            var selected = new ArrayList<PreparedItem>();
            for (var task : tasks) {
                if (!"ITEM".equals(task.kind()) || !"READY".equals(task.state())) continue;
                var item = serializer.deserialize(task.itemData());
                if (InventoryItems.empty(item)) {
                    markInvalid(task, "READY");
                    continue;
                }
                var next = InventoryItems.copy(contents);
                if (!InventoryItems.put(next, item)) continue;
                contents = next;
                selected.add(new PreparedItem(task, item));
                if (selected.size() == MAX_BATCH) break;
            }
            if (selected.isEmpty()) {
                active.remove(playerId);
                return;
            }
            work.async(() -> claim(player, selected, enqueue, canApply));
        } catch (RuntimeException exception) {
            active.remove(playerId);
            logger.warning("[Market] 无法准备物品交付 " + playerId + ": " + exception.getMessage());
        }
    }

    private record PreparedItem(MarketOperation task, ItemStack item) { }

    private void claim(Player player, List<PreparedItem> items, Predicate<Runnable> enqueue, BooleanSupplier canApply) {
        UUID playerId = items.get(0).task().player();
        boolean queued = false;
        try {
            var ids = items.stream().map(item -> item.task().id()).toList();
            if (repository.transitionItemBatch(ids, node, true)) {
                queued = enqueue.test(() -> {
                    if (canApply.getAsBoolean()) apply(player, items);
                    else active.remove(playerId);
                });
            }
        } catch (RuntimeException exception) {
            warn(items.get(0).task(), exception);
        } finally {
            if (!queued) active.remove(playerId);
        }
    }

    private void recover(Player player, List<MarketOperation> pending, Predicate<Runnable> enqueue, BooleanSupplier canApply) {
        UUID playerId = pending.get(0).player();
        var pdc = player.getPersistentDataContainer();
        String batch = pdc.get(BATCH_RECEIPT, PersistentDataType.STRING);
        work.async(() -> {
            boolean queued = false;
            try {
                var file = playerFile(playerId);
                String saved = PlayerDataReceipt.readExisting(file, BATCH_RECEIPT.toString());
                var known = new HashSet<String>();
                if (saved != null) known.addAll(List.of(saved.split(",")));
                var live = new HashSet<String>();
                if (batch != null) live.addAll(List.of(batch.split(",")));
                if (pending.stream().anyMatch(task -> known.contains(task.id()) != live.contains(task.id()))) {
                    logger.warning("[Market] 在线交付凭证与存档不一致，等待重新登录核对 " + playerId);
                    return;
                }
                var confirmed = pending.stream().filter(task -> known.contains(task.id())).map(MarketOperation::id).toList();
                if (!confirmed.isEmpty() && !repository.transitionItemBatch(confirmed, node, false)) return;
                var remaining = pending.stream().filter(task -> !known.contains(task.id())).toList();
                if (remaining.isEmpty()) return;
                queued = enqueue.test(() -> {
                    if (!canApply.getAsBoolean()) {
                        active.remove(playerId);
                        return;
                    }
                    var items = new ArrayList<PreparedItem>();
                    try {
                        for (var task : remaining) {
                            var item = serializer.deserialize(task.itemData());
                            if (InventoryItems.empty(item)) markInvalid(task, SAVING);
                            else items.add(new PreparedItem(task, item));
                        }
                        if (!items.isEmpty()) apply(player, items);
                        else active.remove(playerId);
                    } catch (RuntimeException exception) {
                        active.remove(playerId);
                        warn(remaining.get(0), exception);
                    }
                });
            } catch (IOException | RuntimeException exception) {
                warn(pending.get(0), exception);
            } finally {
                if (!queued) active.remove(playerId);
            }
        });
    }

    private void apply(Player player, List<PreparedItem> items) {
        var task = items.get(0).task();
        boolean verifying = false;
        try {
            var inventory = player.getInventory();
            var before = InventoryItems.copy(inventory.getStorageContents());
            var contents = before;
            var applied = new ArrayList<String>();
            for (var item : items) {
                var next = InventoryItems.copy(contents);
                if (!InventoryItems.put(next, item.item())) continue;
                contents = next;
                applied.add(item.task().id());
            }
            if (applied.isEmpty()) return;
            var pdc = player.getPersistentDataContainer();
            String oldBatch = pdc.get(BATCH_RECEIPT, PersistentDataType.STRING);
            String expected = String.join(",", applied);
            boolean saved = false;
            try {
                inventory.setStorageContents(contents);
                pdc.set(BATCH_RECEIPT, PersistentDataType.STRING, expected);
                savePlayerData(player);
                if (!expected.equals(PlayerDataReceipt.read(playerFile(task.player()), BATCH_RECEIPT.toString()))) {
                    throw new IOException("玩家存档未写入本次交付凭证");
                }
                saved = true;
            } finally {
                if (!saved) {
                    inventory.setStorageContents(before);
                    if (oldBatch == null) pdc.remove(BATCH_RECEIPT); else pdc.set(BATCH_RECEIPT, PersistentDataType.STRING, oldBatch);
                }
            }
            acknowledge(task, applied);
            verifying = true;
        } catch (IOException | RuntimeException exception) {
            warn(task, exception);
        } finally {
            if (!verifying) active.remove(task.player());
        }
    }

    private void savePlayerData(Player player) {
        player.saveData();
    }

    private void acknowledge(MarketOperation task, List<String> ids) {
        work.async(() -> {
            try {
                if (!repository.transitionItemBatch(ids, node, false)) throw new IOException("批量交付回执状态已变化");
            } catch (IOException | RuntimeException exception) {
                warn(task, exception);
            } finally {
                active.remove(task.player());
            }
        });
    }

    private Path playerFile(UUID player) {
        return playerData.resolve(player + ".dat");
    }

    private void markInvalid(MarketOperation task, String state) {
        work.async(() -> {
            try { repository.transitionOperation(task.id(), state, "REVIEW", "物品数据无法解析，保留记录", node); }
            catch (RuntimeException exception) { warn(task, exception); }
        });
    }

    private void warn(MarketOperation task, Exception exception) {
        logger.warning("[Market] 物品交付待恢复 id=" + task.id() + ": " + exception.getMessage());
    }
}
