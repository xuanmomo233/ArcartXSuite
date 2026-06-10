package xuanmo.arcartxsuite.fishing.model;

import java.util.UUID;
import org.jetbrains.annotations.NotNull;

public record FishCollectionEntry(
    @NotNull UUID playerUuid,
    @NotNull String fishId,
    int caughtCount,
    int maxSize,
    long firstCatchAt
) {

    public FishCollectionEntry withNewCatch(int size) {
        return new FishCollectionEntry(
            playerUuid, fishId, caughtCount + 1, Math.max(maxSize, size), firstCatchAt
        );
    }

    public static FishCollectionEntry empty(@NotNull UUID playerUuid, @NotNull String fishId) {
        return new FishCollectionEntry(playerUuid, fishId, 0, 0, System.currentTimeMillis());
    }
}
