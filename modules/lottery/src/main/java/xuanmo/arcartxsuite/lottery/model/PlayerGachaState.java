package xuanmo.arcartxsuite.lottery.model;

import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record PlayerGachaState(
    @NotNull UUID playerUuid,
    @NotNull String poolId,
    int pity5star,
    int pity4star,
    boolean guaranteedUp,
    int fatePoints,
    @Nullable String fateTarget
) {
    public PlayerGachaState {
        if (pity5star < 0) pity5star = 0;
        if (pity4star < 0) pity4star = 0;
        if (fatePoints < 0) fatePoints = 0;
    }

    public static PlayerGachaState empty(@NotNull UUID playerUuid, @NotNull String poolId) {
        return new PlayerGachaState(playerUuid, poolId, 0, 0, false, 0, null);
    }
}
