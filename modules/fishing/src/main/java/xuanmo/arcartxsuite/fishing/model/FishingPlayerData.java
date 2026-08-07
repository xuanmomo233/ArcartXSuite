package xuanmo.arcartxsuite.fishing.model;

import java.util.UUID;
import org.jetbrains.annotations.NotNull;

/**
 * 玩家钓鱼数据，记录等级、经验与各类捕获计数。
 * <p>
 * 关联模块：{@code fishing}。由 {@link xuanmo.arcartxsuite.fishing.storage.FishingRepository} 持久化，
 * 提供经验累加、升级、捕获计数等不可变更新方法。
 *
 * @param uuid           玩家 UUID
 * @param level          当前等级
 * @param totalXp        累计经验值
 * @param totalCaught    累计捕获总数
 * @param perfectCatches 完美捕获次数
 * @param treasureCaught 宝藏捕获次数
 */
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
