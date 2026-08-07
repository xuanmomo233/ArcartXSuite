package xuanmo.arcartxsuite.chat.config;

/**
 * 聊天卡片渲染参数配置。
 * <p>
 * 定义提及、私聊、系统消息、物品预览等聊天卡片 ID，以及卡片文本排版参数
 * （字符宽度、行高、最大行宽、偏移量等），供 {@code ChatService} 渲染聊天卡片时使用。
 *
 * @param mentionCardId      提及消息卡片 ID
 * @param privateCardId      私聊消息卡片 ID
 * @param systemCardId       系统消息卡片 ID
 * @param itemPreviewCardId  物品预览卡片 ID
 * @param charWidthFull      全角字符宽度（像素）
 * @param charWidthHalf      半角字符宽度（像素）
 * @param lineHeight         行高（像素）
 * @param maxLineWidth       最大行宽（像素），超出则自动换行
 * @param textOffsetX        文本 X 轴偏移量（像素）
 * @param padRight           右侧内边距（像素）
 * @param baseHeight         卡片基础高度（像素）
 * @param minWidth           卡片最小宽度（像素）
 */
public record ChatCardConfiguration(
    String mentionCardId,
    String privateCardId,
    String systemCardId,
    String itemPreviewCardId,
    int charWidthFull,
    int charWidthHalf,
    int lineHeight,
    int maxLineWidth,
    int textOffsetX,
    int padRight,
    int baseHeight,
    int minWidth
) {
}
