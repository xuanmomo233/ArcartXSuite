package xuanmo.arcartxsuite.chat.model;

/**
 * 聊天物品预览信息，用于在消息中展示玩家手持物品。
 * <p>
 * 包含 ArcartX 客户端可识别的物品 JSON、显示文本、材质键与数量，
 * 由 {@link ChatFormatSupport} 渲染为带悬浮提示的聊天组件。
 *
 * @param itemJson    ArcartX 客户端物品 JSON
 * @param displayText 物品展示文本（如 "&b[展示物品]"）
 * @param materialKey 物品材质键（如 "minecraft:diamond"）
 * @param amount      物品数量
 */
public record ChatItemPreview(
    String itemJson,
    String displayText,
    String materialKey,
    int amount
) {
    /**
     * 判断物品预览是否有效（材质键非空且数量大于 0）。
     *
     * @return {@code true} 表示可渲染物品展示
     */
    public boolean available() {
        return materialKey != null && !materialKey.isBlank() && amount > 0;
    }
}
