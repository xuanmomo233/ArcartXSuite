package xuanmo.arcartxsuite.fishing.model;

import java.util.UUID;
import org.jetbrains.annotations.NotNull;

/**
 * 玩家钓鱼永久数据，跨赛季保留，不随赛季重置。
 * <p>
 * 记录历史捕获统计，用于图鉴、里程碑称号等永久成就。
 *
 * @param uuid           玩家 UUID
 * @param totalCaught    累计捕获总数（跨赛季）
 * @param perfectCatches 累计完美捕获次数（跨赛季）
 * @param treasureCaught 累计宝藏捕获次数（跨赛季）
 */
public record FishingPermanentData(
    @NotNull UUID uuid,
    int totalCaught,
    int perfectCatches,
    int treasureCaught
) {

    public static @NotNull FishingPermanentData empty(@NotNull UUID uuid) {
        return new FishingPermanentData(uuid, 0, 0, 0);
    }

    /**
     * 追加一次捕获记录。
     *
     * @param perfect  是否完美捕获
     * @param treasure 是否获得宝藏
     * @return 更新后的新实例
     */
    public @NotNull FishingPermanentData withCaught(boolean perfect, boolean treasure) {
        return new FishingPermanentData(
            uuid,
            totalCaught + 1,
            perfect ? perfectCatches + 1 : perfectCatches,
            treasure ? treasureCaught + 1 : treasureCaught
        );
    }
}
