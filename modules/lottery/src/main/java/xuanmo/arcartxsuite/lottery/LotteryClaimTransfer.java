package xuanmo.arcartxsuite.lottery;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.api.item.PendingRewardService;
import xuanmo.arcartxsuite.api.item.PendingRewardService.PendingReward;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.lottery.storage.LotteryRepository;

/** Move each legacy claim to durable host ownership before closing the Lottery row. */
final class LotteryClaimTransfer {
    private static final int BATCH_LIMIT = 32;

    @FunctionalInterface
    interface Target { String load() throws IOException; }

    record Result(int transferred, int remaining, boolean hostAccepted) {}

    private final LotteryRepository repository;
    private final PendingRewardService host;
    private final Target target;

    LotteryClaimTransfer(LotteryRepository repository, PendingRewardService host, Target target) {
        this.repository = repository;
        this.host = host;
        this.target = target;
    }

    Result transfer(UUID player) throws SQLException, IOException {
        if (host == null) return new Result(0, repository.countPendingClaims(player), false);
        String owner = target.load();
        int transferred = 0;
        boolean accepted = false;
        for (var claim : repository.loadTransferableClaims(player, owner, BATCH_LIMIT)) {
            if (claim.itemData().isBlank()) continue;
            UUID request = repository.prepareClaimTransfer(player, claim.id(), owner);
            if (request == null) continue;
            var reward = PendingReward.itemSerialized(player, "lottery", claim.itemData(), "抽奖待发物品 #" + claim.id());
            if (!host.savePendingBatchConfirmed("legacy-claim:v1:" + request, List.of(reward))) break;
            accepted = true;
            if (repository.confirmClaimTransfer(player, claim.id(), owner, request)) transferred++;
        }
        return new Result(transferred, repository.countPendingClaims(player), accepted);
    }

    void flush(Player player) {
        if (host != null) host.flushPlayer(player);
    }

    boolean available() { return host != null; }

    boolean acceptDrawPrize(UUID player, UUID operation, int prizeIndex, String itemData) {
        var reward = PendingReward.itemSerialized(player, "lottery", itemData, "抽奖物品 " + operation + " #" + (prizeIndex + 1));
        return host.savePendingBatchConfirmed("draw:" + operation + ":" + prizeIndex, List.of(reward));
    }

    static Target target(StorageDescriptor host, boolean usesHostDatabase, Path moduleDirectory) {
        if (host.isMysql() && usesHostDatabase) {
            String identity = host.host().toLowerCase(Locale.ROOT) + ":" + host.port() + "/" + host.database();
            String owner = "shared:" + UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
            return () -> owner;
        }
        return new LocalTarget(moduleDirectory.resolve("claim-transfer-installation.id"));
    }

    private static final class LocalTarget implements Target {
        private final Path identity;
        private String owner;

        LocalTarget(Path identity) { this.identity = identity; }

        @Override public synchronized String load() throws IOException {
            if (owner != null) return owner;
            try {
                if (!Files.exists(identity)) Files.writeString(identity, UUID.randomUUID().toString(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, StandardOpenOption.SYNC);
            } catch (FileAlreadyExistsException ignored) {
                // The existing installation identity is authoritative.
            }
            owner = "local:" + UUID.fromString(Files.readString(identity, StandardCharsets.UTF_8).trim());
            return owner;
        }
    }
}
