package xuanmo.arcartxsuite.fishing.model;

import java.util.UUID;
import org.jetbrains.annotations.NotNull;

public record FishingPlayerData(
    @NotNull UUID uuid,
    int level,
    int totalXp,
    int totalCaught,
    int perfectCatches,
    int treasureCaught
) {

    public int xpForNextLevel(int baseXpPerLevel) {
        return baseXpPerLevel * level;
    }

    public boolean canLevelUp(int baseXpPerLevel) {
        return totalXp >= xpForNextLevel(baseXpPerLevel);
    }

    public FishingPlayerData withXpAdded(int xp) {
        return new FishingPlayerData(uuid, level, totalXp + xp, totalCaught, perfectCatches, treasureCaught);
    }

    public FishingPlayerData withCaught(boolean perfect, boolean treasure) {
        return new FishingPlayerData(
            uuid, level, totalXp, totalCaught + 1,
            perfect ? perfectCatches + 1 : perfectCatches,
            treasure ? treasureCaught + 1 : treasureCaught
        );
    }

    public FishingPlayerData withLevelUp() {
        return new FishingPlayerData(uuid, level + 1, totalXp, totalCaught, perfectCatches, treasureCaught);
    }

    public static FishingPlayerData empty(@NotNull UUID uuid) {
        return new FishingPlayerData(uuid, 1, 0, 0, 0, 0);
    }
}
