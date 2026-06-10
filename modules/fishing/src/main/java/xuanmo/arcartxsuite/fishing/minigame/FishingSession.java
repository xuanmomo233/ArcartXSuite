package xuanmo.arcartxsuite.fishing.minigame;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.fishing.model.FishDefinition;
import xuanmo.arcartxsuite.fishing.model.WaterArea;

public final class FishingSession {

    private final UUID playerUuid;
    private final FishDefinition fish;
    private final int caughtSize;
    private final int playerLevel;
    private final WaterArea water;
    private final int adjustedDifficulty;
    private final String baitId;
    private final AtomicBoolean pressing = new AtomicBoolean(false);
    private volatile boolean active = true;
    private volatile long remainingTicks;

    public FishingSession(@NotNull Player player, @NotNull FishDefinition fish, int caughtSize,
                          int playerLevel, long durationTicks, @Nullable WaterArea water, int adjustedDifficulty,
                          @Nullable String baitId) {
        this.playerUuid = player.getUniqueId();
        this.fish = fish;
        this.caughtSize = caughtSize;
        this.playerLevel = playerLevel;
        this.water = water;
        this.adjustedDifficulty = adjustedDifficulty;
        this.baitId = baitId;
        this.remainingTicks = durationTicks;
    }

    public @NotNull UUID playerUuid() {
        return playerUuid;
    }

    public @NotNull FishDefinition fish() {
        return fish;
    }

    public int caughtSize() {
        return caughtSize;
    }

    public int playerLevel() {
        return playerLevel;
    }

    public @Nullable WaterArea water() {
        return water;
    }

    public int adjustedDifficulty() {
        return adjustedDifficulty;
    }

    public @Nullable String baitId() {
        return baitId;
    }

    public boolean isPressing() {
        return pressing.get();
    }

    public void setPressing(boolean pressing) {
        this.pressing.set(pressing);
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public long remainingTicks() {
        return remainingTicks;
    }

    public void decrementRemainingTicks() {
        this.remainingTicks--;
    }
}
