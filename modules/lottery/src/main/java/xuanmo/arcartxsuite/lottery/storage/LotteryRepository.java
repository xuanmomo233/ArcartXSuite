package xuanmo.arcartxsuite.lottery.storage;

import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.lottery.model.PlayerCaseState;
import xuanmo.arcartxsuite.lottery.model.PlayerGachaState;

public interface LotteryRepository {

    // ─── Gacha State ────────────────────────────────────────

    PlayerGachaState loadGachaState(@NotNull UUID playerUuid, @NotNull String poolId);

    void saveGachaState(@NotNull PlayerGachaState state);

    void deleteGachaState(@NotNull UUID playerUuid, @NotNull String poolId);

    // ─── Case State ─────────────────────────────────────────

    PlayerCaseState loadCaseState(@NotNull UUID playerUuid, @NotNull String poolId);

    void saveCaseState(@NotNull PlayerCaseState state);

    void deleteCaseState(@NotNull UUID playerUuid, @NotNull String poolId);

    long enqueuePendingClaim(@NotNull UUID playerUuid, @NotNull String poolId,
                              @NotNull String itemMetadata, @Nullable String itemData);

    @NotNull List<PendingClaim> getPendingClaims(@NotNull UUID playerUuid);

    void markPendingClaimClaimed(long claimId);

    record PendingClaim(long id, UUID playerUuid, String poolId, String itemMetadata,
                        String itemData, long createdTime, int attempts) {}

    // ─── Logs ───────────────────────────────────────────────

    void logGachaPull(@NotNull UUID playerUuid, @NotNull String poolId, int pullCount,
                      @NotNull String itemsJson, int pityAtPull, boolean guaranteed);

    void logCaseOpen(@NotNull UUID playerUuid, @NotNull String poolId,
                     @NotNull String itemId, @NotNull String rarity,
                     boolean stattrak, double wearValue, @NotNull String wearTier);

    @NotNull List<GachaLogEntry> getGachaHistory(@NotNull UUID playerUuid, @NotNull String poolId, int limit);

    @NotNull List<CaseLogEntry> getCaseHistory(@NotNull UUID playerUuid, @NotNull String poolId, int limit);

    // ─── Log Records ──────────────────────────────────────

    record GachaLogEntry(
        long id,
        long pullTime,
        int pullCount,
        String itemsJson,
        int pityAtPull,
        boolean guaranteed
    ) {}

    record CaseLogEntry(
        long id,
        long openTime,
        String itemId,
        String rarity,
        boolean stattrak,
        double wearValue,
        String wearTier
    ) {}
}
