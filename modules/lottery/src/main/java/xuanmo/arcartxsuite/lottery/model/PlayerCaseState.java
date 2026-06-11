package xuanmo.arcartxsuite.lottery.model;

import java.util.UUID;
import org.jetbrains.annotations.NotNull;

public record PlayerCaseState(
    @NotNull UUID playerUuid,
    @NotNull String poolId,
    int openCount,
    long lastOpenTime
) {
    public PlayerCaseState {
        if (openCount < 0) openCount = 0;
        if (lastOpenTime < 0) lastOpenTime = 0;
    }

    public static PlayerCaseState empty(@NotNull UUID playerUuid, @NotNull String poolId) {
        return new PlayerCaseState(playerUuid, poolId, 0, 0);
    }
}
