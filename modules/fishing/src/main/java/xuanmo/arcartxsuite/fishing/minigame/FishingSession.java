package xuanmo.arcartxsuite.fishing.minigame;

import java.util.UUID;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.fishing.model.FishDefinition;
import xuanmo.arcartxsuite.fishing.model.WaterArea;
import xuanmo.arcartxsuite.fishing.model.BaitDefinition;
import xuanmo.arcartxsuite.fishing.model.FishBehaviorType;
import xuanmo.arcartxsuite.fishing.model.RodDefinition;
import xuanmo.arcartxsuite.fishing.model.TreasureDefinition;

/**
 * 单次钓鱼小游戏会话，记录玩家、目标鱼、水域、难度及实时状态。
 * <p>
 * 关联模块：{@code fishing}。由 {@link xuanmo.arcartxsuite.fishing.service.FishingService}
 * 在小游戏启动时创建，持有玩家按住状态、剩余 tick 等运行时数据。
 */
public final class FishingSession {

    private final UUID playerUuid;
    private final FishDefinition fish;
    private final int caughtSize;
    private final int playerLevel;
    private final WaterArea water;
    private final int adjustedDifficulty;
    private final String baitId;
    private final int greenBarHeightBonus;
    private final double expMultiplier;
    private final double rodTreasureChanceBonus;
    private final AtomicReference<BarInput> barInput = new AtomicReference<>(new BarInput(0, false));
    private final String id = UUID.randomUUID().toString();
    private final AtomicBoolean slackenRequested = new AtomicBoolean();
    private final AtomicLong hookSetRequested = new AtomicLong();
    private final AtomicBoolean settled = new AtomicBoolean();
    private final Gear gear;
    private final @Nullable TreasureDefinition treasureOffer;
    private final @Nullable String schoolId;
    private final @Nullable WaterArea.ObstacleZone obstacle;
    private final @Nullable WaterArea.CurrentZone current;
    private final FishDefinition.SizeModifiers sizeModifiers;
    private final boolean trial;
    private final Opening opening;
    private volatile long hookRound;
    private volatile boolean active = true;
    private final AtomicLong remainingTicks;

    public FishingSession(@NotNull Player player, @NotNull FishDefinition fish, int caughtSize,
                          int playerLevel, long durationTicks, @Nullable WaterArea water, int adjustedDifficulty,
                          @Nullable String baitId, int greenBarHeightBonus, double expMultiplier,
                          double rodTreasureChanceBonus) {
        this(player.getUniqueId(), fish, caughtSize, playerLevel, durationTicks, water, adjustedDifficulty,
            baitId, greenBarHeightBonus, expMultiplier, rodTreasureChanceBonus);
    }

    public FishingSession(@NotNull UUID playerUuid, @NotNull FishDefinition fish, int caughtSize,
                          int playerLevel, long durationTicks, @Nullable WaterArea water, int adjustedDifficulty,
                          @Nullable String baitId, int greenBarHeightBonus, double expMultiplier,
                          double rodTreasureChanceBonus) {
        this(playerUuid, fish, caughtSize, playerLevel, durationTicks, water, adjustedDifficulty, baitId,
            greenBarHeightBonus, expMultiplier, rodTreasureChanceBonus, Gear.DEFAULT, null, null);
    }

    public FishingSession(UUID playerUuid, FishDefinition fish, int caughtSize, int playerLevel, long durationTicks,
                          @Nullable WaterArea water, int adjustedDifficulty, @Nullable String baitId,
                          int greenBarHeightBonus, double expMultiplier, double rodTreasureChanceBonus,
                          Gear gear, @Nullable TreasureDefinition treasureOffer, @Nullable String schoolId) {
        this(playerUuid, fish, caughtSize, playerLevel, durationTicks, water, adjustedDifficulty, baitId,
            greenBarHeightBonus, expMultiplier, rodTreasureChanceBonus, gear, treasureOffer, schoolId, null);
    }

    public FishingSession(UUID playerUuid, FishDefinition fish, int caughtSize, int playerLevel, long durationTicks,
                          @Nullable WaterArea water, int adjustedDifficulty, @Nullable String baitId,
                          int greenBarHeightBonus, double expMultiplier, double rodTreasureChanceBonus,
                          Gear gear, @Nullable TreasureDefinition treasureOffer, @Nullable String schoolId,
                          @Nullable WaterArea.ObstacleZone obstacle) {
        this(playerUuid, fish, caughtSize, playerLevel, durationTicks, water, adjustedDifficulty, baitId,
            greenBarHeightBonus, expMultiplier, rodTreasureChanceBonus, gear, treasureOffer, schoolId, obstacle, null, false);
    }

    public FishingSession(UUID playerUuid, FishDefinition fish, int caughtSize, int playerLevel, long durationTicks,
                          @Nullable WaterArea water, int adjustedDifficulty, @Nullable String baitId,
                          int greenBarHeightBonus, double expMultiplier, double rodTreasureChanceBonus,
                          Gear gear, @Nullable TreasureDefinition treasureOffer, @Nullable String schoolId,
                          @Nullable WaterArea.ObstacleZone obstacle, @Nullable WaterArea.CurrentZone current, boolean trial) {
        this(playerUuid, fish, caughtSize, playerLevel, durationTicks, water, adjustedDifficulty, baitId,
            greenBarHeightBonus, expMultiplier, rodTreasureChanceBonus, gear, treasureOffer, schoolId, obstacle, current,
            trial, Opening.NONE);
    }

    public FishingSession(UUID playerUuid, FishDefinition fish, int caughtSize, int playerLevel, long durationTicks,
                          @Nullable WaterArea water, int adjustedDifficulty, @Nullable String baitId,
                          int greenBarHeightBonus, double expMultiplier, double rodTreasureChanceBonus,
                          Gear gear, @Nullable TreasureDefinition treasureOffer, @Nullable String schoolId,
                          @Nullable WaterArea.ObstacleZone obstacle, @Nullable WaterArea.CurrentZone current, boolean trial,
                          Opening opening) {
        this.playerUuid = playerUuid;
        this.fish = fish;
        this.caughtSize = caughtSize;
        this.playerLevel = playerLevel;
        this.water = water;
        this.adjustedDifficulty = adjustedDifficulty;
        this.baitId = baitId;
        this.greenBarHeightBonus = greenBarHeightBonus;
        this.expMultiplier = expMultiplier;
        this.rodTreasureChanceBonus = rodTreasureChanceBonus;
        this.gear = gear;
        this.treasureOffer = treasureOffer;
        this.schoolId = schoolId;
        this.obstacle = obstacle;
        this.current = current;
        this.sizeModifiers = fish.sizeDifficulty().resolve(caughtSize, fish.minSize(), fish.maxSize());
        this.trial = trial;
        this.opening = opening;
        this.hookRound = opening.hookSet() == null ? 0 : 1;
        this.remainingTicks = new AtomicLong(durationTicks);
    }

    public record Opening(FishDefinition.Personality personality, @Nullable FishDefinition.HookSet hookSet,
                          boolean repeatHook) {
        static final Opening NONE = new Opening(FishDefinition.Personality.NORMAL, null);

        public Opening(FishDefinition.Personality personality, @Nullable FishDefinition.HookSet hookSet) {
            this(personality, hookSet, false);
        }

        public Opening {
            if (repeatHook && hookSet == null) throw new IllegalArgumentException("连续练习需要提竿窗口");
        }
    }

    public record Gear(double lineStrength, double turnResponse, Map<FishBehaviorType, Integer> firstRecoveryBonus) {
        static final Gear DEFAULT = new Gear(1, 1, Map.of());

        public Gear {
            firstRecoveryBonus = Map.copyOf(firstRecoveryBonus);
        }

        public static Gear resolve(RodDefinition rod, @Nullable BaitDefinition bait, FishDefinition fish) {
            var bonuses = new EnumMap<FishBehaviorType, Integer>(FishBehaviorType.class);
            if (bait != null) {
                for (var effect : bait.fightEffects()) {
                    if (effect.fishId().equals(fish.id())) {
                        bonuses.merge(effect.action(), effect.extraRecoveryTicks(), Math::max);
                    }
                }
            }
            return new Gear(rod.lineStrength(), rod.turnResponse(), bonuses);
        }
    }

    public String id() { return id; }
    public Gear gear() { return gear; }
    public @Nullable TreasureDefinition treasureOffer() { return treasureOffer; }
    public @Nullable String schoolId() { return schoolId; }
    public @Nullable WaterArea.ObstacleZone obstacle() { return obstacle; }
    public @Nullable WaterArea.CurrentZone current() { return current; }
    public FishDefinition.SizeModifiers sizeModifiers() { return sizeModifiers; }
    public boolean tensionEnabled() { return fish.lineTension() || obstacle != null; }
    public boolean trial() { return trial; }
    public Opening opening() { return opening; }

    public long hookRound() { return hookRound; }

    void advanceHookRound() {
        hookRound++;
    }

    public void requestHookSet(String fightId, String roundId) {
        long round = hookRound;
        if (active && round > 0 && id.equals(fightId) && Long.toString(round).equals(roundId)) {
            hookSetRequested.accumulateAndGet(round, Math::max);
        }
    }

    public boolean consumeHookSetRequest() {
        long requestedRound = hookSetRequested.getAndSet(0);
        return requestedRound != 0 && requestedRound == hookRound;
    }

    public void requestSlacken(String fightId) {
        if (active && tensionEnabled() && id.equals(fightId)) slackenRequested.set(true);
    }

    public boolean consumeSlackenRequest() {
        return slackenRequested.getAndSet(false);
    }

    public boolean trySettle() {
        return settled.compareAndSet(false, true);
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

    public int greenBarHeightBonus() {
        return greenBarHeightBonus;
    }

    public double expMultiplier() {
        return expMultiplier;
    }

    public double rodTreasureChanceBonus() {
        return rodTreasureChanceBonus;
    }

    public record BarInput(int sequence, boolean pressing) {}

    public BarInput barInput() {
        return barInput.get();
    }

    public void updateBarInput(String fightId, int sequence, boolean pressing) {
        if (!active || !id.equals(fightId) || sequence <= 0) return;
        barInput.updateAndGet(previous -> sequence > previous.sequence() ? new BarInput(sequence, pressing) : previous);
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public long remainingTicks() {
        return remainingTicks.get();
    }

    public void decrementRemainingTicks() {
        remainingTicks.decrementAndGet();
    }
}
