package xuanmo.arcartxsuite.fishing.service;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI.CurrencyBridge;
import xuanmo.arcartxsuite.api.inventory.PlayerDataReceipt;
import xuanmo.arcartxsuite.api.item.PendingRewardService.PendingReward;
import xuanmo.arcartxsuite.api.util.ItemSerializer;
import xuanmo.arcartxsuite.fishing.storage.FishingRepository;
import xuanmo.arcartxsuite.fishing.storage.FishingRepository.FishSale;

/** Holds the exact fish before crediting a sale. Only a definite rejection authorizes a return. */
final class FishingSales {
    private static final NamespacedKey RECEIPT = new NamespacedKey("arcartxsuite", "fishing_sale");
    private static final long BOOT = ManagementFactory.getRuntimeMXBean().getStartTime();
    private static final int RECOVERY_PAGE = 32;
    private static final int MAX_CHECKED_FILES = 128;

    enum Outcome { SOLD, RETURN_QUEUED, UNAVAILABLE, BLOCKED, REVIEW }

    record Fish(int slot, ItemStack expected, int count) {}
    record Result(Outcome outcome, UUID request) {}
    private record JoinProof(UUID session, String loadedReceipt) {}
    private record CheckedFile(BasicFileAttributes version, JoinProof join) {}

    private final FishingRepository repository;
    private final FishingCatchWork deliveries;
    private final Logger logger;
    private final Path playerData;
    private final Set<UUID> active = ConcurrentHashMap.newKeySet();
    private final Map<UUID, JoinProof> joins = new ConcurrentHashMap<>();
    // The existing delivery worker owns this bounded negative-result cache.
    private final Map<UUID, CheckedFile> unchanged = new LinkedHashMap<>();
    private String node;
    private String after = "";

    FishingSales(FishingRepository repository, FishingCatchWork deliveries, Logger logger, Path playerData) {
        this.repository = repository;
        this.deliveries = deliveries;
        this.logger = logger;
        this.playerData = playerData;
    }

    void joined(Player player) {
        String loaded = player.getPersistentDataContainer().get(RECEIPT, PersistentDataType.STRING);
        joins.put(player.getUniqueId(), new JoinProof(UUID.randomUUID(), loaded));
    }

    void quit(UUID player) {
        joins.remove(player);
    }

    Result sell(Player player, String currency, CurrencyBridge bridge, BigDecimal amount, List<Fish> fish) {
        if (!Bukkit.isPrimaryThread() || !isCurrent(player) || amount.signum() <= 0 || fish.isEmpty()
                || bridge == null || !bridge.available() || bridge.creditRequiresReconciliation()) {
            return new Result(Outcome.UNAVAILABLE, null);
        }
        UUID request = UUID.randomUUID();
        FishSale sale;
        try {
            var snapshots = new ArrayList<PendingReward>(fish.size());
            for (Fish part : fish) {
                ItemStack snapshot = part.expected().clone();
                snapshot.setAmount(part.count());
                byte[] bytes = ItemSerializer.serialize(snapshot);
                if (bytes.length == 0 || !snapshot.equals(ItemSerializer.deserializeStrict(bytes))) {
                    throw new IllegalStateException("出售物品无法完整保存以供返还");
                }
                snapshots.add(PendingReward.itemSerialized(player.getUniqueId(), "fishing",
                    Base64.getEncoder().encodeToString(bytes), "售鱼未入账返还 " + request));
            }
            var join = joins.get(player.getUniqueId());
            UUID sourceSession = join == null ? UUID.randomUUID() : join.session();
            sale = new FishSale(player.getUniqueId(), request, deliveries.owner(), sourceNode(), BOOT, sourceSession,
                currency, amount.toPlainString(), List.copyOf(snapshots));
        } catch (IOException | RuntimeException | LinkageError failure) {
            logger.log(Level.WARNING, "无法准备售鱼预约: " + request, failure);
            return new Result(Outcome.UNAVAILABLE, null);
        }

        if (!active.add(sale.player())) return new Result(Outcome.BLOCKED, null);
        try {
            if (!repository.reserveFishSale(sale)) return new Result(Outcome.BLOCKED, null);
            PlayerInventory inventory = player.getInventory();
            if (!isCurrent(player) || !matches(inventory, fish)) {
                repository.cancelUnstartedFishSale(sale);
                return new Result(Outcome.UNAVAILABLE, null);
            }

            removeAndSave(player, inventory, fish, request, playerFile(sale.player()));
            if (!isCurrent(player) || !bridge.available() || bridge.creditRequiresReconciliation()) {
                if (!repository.recoverUnpaidFishSale(sale, true)) {
                    throw new IllegalStateException("出售返还预约已变化");
                }
                deliveries.requestSweep();
                return new Result(Outcome.RETURN_QUEUED, request);
            }
            if (!repository.beginFishSalePayment(sale)) {
                throw new IllegalStateException("出售付款预约已变化");
            }

            var receipt = bridge.deposit(player, amount);
            if (receipt == null || receipt.uncertain() || (receipt.success() && bridge.creditRequiresReconciliation())) {
                throw new IllegalStateException("售鱼入账结果待核对");
            }
            boolean paid = receipt.success();
            repository.finishFishSale(sale, paid);
            if (!paid) deliveries.requestSweep();
            return new Result(paid ? Outcome.SOLD : Outcome.RETURN_QUEUED, request);
        } catch (IOException | SQLException | RuntimeException | LinkageError failure) {
            logger.log(Level.WARNING, "售鱼待核对: request=" + request + " player=" + sale.player(), failure);
            return new Result(Outcome.REVIEW, request);
        } finally {
            active.remove(sale.player());
        }
    }

    void recoverReservedSales() throws IOException, SQLException {
        var sales = repository.reservedFishSales(sourceNode(), after, RECOVERY_PAGE);
        after = sales.size() == RECOVERY_PAGE ? sales.get(sales.size() - 1).player().toString() : "";
        for (FishSale sale : sales) {
            if (active.contains(sale.player())) continue;
            var join = joins.get(sale.player());
            try {
                Path file = playerFile(sale.player());
                var before = Files.readAttributes(file, BasicFileAttributes.class);
                if (!before.isRegularFile()) continue;
                var checked = unchanged.get(sale.request());
                if (checked != null && checked.join() == join && sameFileVersion(checked.version(), before)) continue;
                String receipt = PlayerDataReceipt.readExisting(file, RECEIPT.toString());
                var current = Files.readAttributes(file, BasicFileAttributes.class);
                if (!sameFileVersion(before, current)) continue;
                boolean deducted = sale.request().toString().equals(receipt);
                // A new login must have loaded the same receipt that is still on the source disk.
                boolean reloaded = join != null && !sale.sourceSession().equals(join.session())
                    && Objects.equals(receipt, join.loadedReceipt()) && joins.get(sale.player()) == join;
                if (deducted || sale.boot() != BOOT || reloaded) {
                    unchanged.remove(sale.request());
                    if (repository.recoverUnpaidFishSale(sale, deducted)) {
                        logger.info("已恢复未付款售鱼预约: request=" + sale.request()
                            + (deducted ? "，原鱼已加入返还队列" : "，原鱼未从存档扣除"));
                    }
                } else {
                    if (checked == null && unchanged.size() >= MAX_CHECKED_FILES) unchanged.remove(unchanged.keySet().iterator().next());
                    unchanged.put(sale.request(), new CheckedFile(before, join));
                }
            } catch (IOException | SQLException | RuntimeException failure) {
                logger.fine("未付款售鱼等待原服存档核对: request=" + sale.request() + "，" + failure.getMessage());
            }
        }
    }

    private static boolean sameFileVersion(BasicFileAttributes before, BasicFileAttributes after) {
        return after.isRegularFile() && before.size() == after.size()
            && before.lastModifiedTime().equals(after.lastModifiedTime()) && Objects.equals(before.fileKey(), after.fileKey());
    }

    private Path playerFile(UUID player) {
        return playerData.resolve(player + ".dat");
    }

    private synchronized String sourceNode() throws IOException {
        if (node == null) {
            Files.createDirectories(playerData);
            Path identity = playerData.resolve(".arcartxsuite-fishing-sale-node");
            try {
                Files.writeString(identity, UUID.randomUUID().toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, StandardOpenOption.SYNC);
            } catch (FileAlreadyExistsException ignored) {
                // The identity belongs to this player-data directory, even with a shared SQL store.
            }
            node = UUID.fromString(Files.readString(identity, StandardCharsets.UTF_8).trim()).toString();
        }
        return node;
    }

    private static boolean isCurrent(Player player) {
        UUID id = player.getUniqueId();
        return player.isOnline() && Bukkit.getPlayer(id) == player && AuthAccess.admitted(id);
    }

    private static boolean matches(PlayerInventory inventory, List<Fish> fish) {
        for (Fish part : fish) {
            if (part.count() <= 0 || part.count() > part.expected().getAmount()
                    || !part.expected().equals(inventory.getItem(part.slot()))) return false;
        }
        return true;
    }

    private static void removeAndSave(Player player, PlayerInventory inventory, List<Fish> fish, UUID request, Path file)
            throws IOException {
        var before = inventory.getContents();
        var after = before.clone();
        for (Fish part : fish) {
            ItemStack left = part.expected().clone();
            left.setAmount(left.getAmount() - part.count());
            after[part.slot()] = left.getAmount() == 0 ? null : left;
        }
        String token = request.toString();
        var data = player.getPersistentDataContainer();
        String previous = data.get(RECEIPT, PersistentDataType.STRING);
        try {
            inventory.setContents(after);
            data.set(RECEIPT, PersistentDataType.STRING, token);
        } catch (RuntimeException | LinkageError failure) {
            inventory.setContents(before);
            if (previous == null) data.remove(RECEIPT);
            else data.set(RECEIPT, PersistentDataType.STRING, previous);
            throw failure;
        }
        player.saveData();
        // Never pay against an inventory deduction that can disappear on restart.
        if (!token.equals(PlayerDataReceipt.read(file, RECEIPT.toString()))) {
            throw new IOException("玩家存档未确认本次售鱼的物品扣除");
        }
    }
}
