package xuanmo.arcartxsuite.chat.config;

/**
 * 聊天卡片渲染参数配置。
 * <p>
 * 定义提及、私聊、系统消息、物品预览等聊天卡片 ID，以及卡片文本换行参数
 * （字符宽度、最大行宽），供 {@code ChatService} 渲染聊天卡片时使用。
 * 卡片的宽度与高度由 UI 侧根据 Text 控件实际渲染尺寸自适应，Java 不再计算。
 *
 * @param mentionCardId      提及消息卡片 ID
 * @param privateCardId      私聊消息卡片 ID
 * @param systemCardId       系统消息卡片 ID
 * @param itemPreviewCardId  物品预览卡片 ID
 * @param charWidthFull      全角字符宽度（像素），用于换行测算
 * @param charWidthHalf      半角字符宽度（像素），用于换行测算
 * @param maxLineWidth       最大行宽（像素），超出则自动换行
 */
public record ChatCardConfiguration(
    String mentionCardId,
    String privateCardId,
    String systemCardId,
    String itemPreviewCardId,
    int charWidthFull,
    int charWidthHalf,
    int maxLineWidth
) {
}
