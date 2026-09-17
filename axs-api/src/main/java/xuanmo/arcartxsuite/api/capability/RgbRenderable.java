package xuanmo.arcartxsuite.api.capability;

import java.util.List;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 渐变文本渲染能力接口（公开 capability）。
 * <p>
 * 由 RGB 模块实现，供外部插件把已配置的渐变条目渲染成文本。
 * <p>
 * 外部插件经 {@code AxsCapabilities.get(RgbRenderable.class)} 获取。
 */
@PublicCapability
public interface RgbRenderable {

    /**
     * 渲染指定渐变条目。
     *
     * @param entryId 渐变条目 id
     * @param player  关联玩家（用于 PlaceholderAPI 变量，可为 null）
     * @return 渲染后的文本；条目不存在时返回空串
     */
    @NotNull String render(@NotNull String entryId, @Nullable OfflinePlayer player);

    /** 已配置的渐变条目 id 列表。 */
    @NotNull List<String> entryIds();
}
