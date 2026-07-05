package xuanmo.arcartxsuite.fishing.model;

import org.jetbrains.annotations.NotNull;

/**
 * 通用物品引用结构，供 treasure、fish、bait、rod 四种配置复用。
 * <p>
 * 支持外部物品库（MythicMobs、NeigeItems、Overture、MMOItems）、原版 Material、
 * 自定义 JSON 以及 ArcartX 自定义贴图。
 */
public record FishingItemRef(
    @NotNull String source,
    @NotNull String itemId,
    @NotNull String mmoType,
    @NotNull String mmoId,
    @NotNull String json,
    @NotNull String texture,
    @NotNull String textureUrl,
    int amount
) {

    public static final FishingItemRef EMPTY = new FishingItemRef("", "", "", "", "", "", "", 1);

    public boolean isEmpty() {
        return (source == null || source.isBlank())
            && (itemId == null || itemId.isBlank())
            && (json == null || json.isBlank());
    }

    /**
     * 从旧的简单字符串格式创建（兼容 migration）。
     */
    public static @NotNull FishingItemRef fromMaterial(@NotNull String materialName, int amount) {
        return new FishingItemRef("minecraft", materialName, "", "", "", "", "", Math.max(1, amount));
    }
}