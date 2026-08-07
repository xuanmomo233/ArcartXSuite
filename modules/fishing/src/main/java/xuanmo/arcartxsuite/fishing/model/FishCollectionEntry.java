package xuanmo.arcartxsuite.fishing.model;

import java.util.UUID;
import org.jetbrains.annotations.NotNull;

/**
 * 玩家鱼类图鉴条目，记录某玩家对某鱼种的捕获统计。
 * <p>
 * 关联模块：{@code fishing}。由 {@link xuanmo.arcartxsuite.fishing.storage.FishingRepository} 持久化。
 *
 * @param playerUuid  玩家 UUID
 * @param fishId      鱼种 id
 * @param caughtCount 累计捕获次数
 * @param maxSize     历史最大尺寸
 * @param firstCatchAt 首次捕获时间戳（毫秒）
 */
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
