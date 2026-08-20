package xuanmo.arcartxsuite.fishing.model;

import java.util.UUID;
import org.jetbrains.annotations.NotNull;

/**
 * 捕获记录，持久化到数据库，用于 UI 最近捕获记录流展示。
 * <p>
 * 关联模块：{@code fishing}。跨赛季保留，跨服同步。
 */
public record FishingCatchRecord(
    long id,
    @NotNull UUID playerUuid,
    @NotNull String fishId,
    @NotNull String fishName,
    @NotNull String rarity,
    int size,
    boolean perfect,
    long timestamp
) {
    public FishingCatchRecord(@NotNull UUID playerUuid, @NotNull String fishId,
                              @NotNull String fishName, @NotNull String rarity,
                              int size, boolean perfect, long timestamp) {
        this(0, playerUuid, fishId, fishName, rarity, size, perfect, timestamp);
    }
}
