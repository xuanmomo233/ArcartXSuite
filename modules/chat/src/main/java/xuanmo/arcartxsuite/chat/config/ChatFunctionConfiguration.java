package xuanmo.arcartxsuite.chat.config;

import java.util.List;

/**
 * 聊天功能开关配置。
 * <p>
 * 控制 @提及、物品展示等聊天增强功能的启用状态及展示格式。
 *
 * @param mentionEnabled      是否启用 @提及功能
 * @param mentionAllEnabled   是否允许 @all 全员提及
 * @param itemEnabled         是否启用 [item] 物品展示功能
 * @param itemToken           物品展示触发标记（如 "[item]"）
 * @param itemFormat          物品展示成功时的显示文本
 * @param itemFailedFormat    物品展示失败时的提示文本
 * @param customComponents    自定义聊天组件列表
 */
public record ChatFunctionConfiguration(
    boolean mentionEnabled,
    boolean mentionAllEnabled,
    boolean itemEnabled,
    String itemToken,
    String itemFormat,
    String itemFailedFormat,
    List<ChatCustomComponent> customComponents
) {
}
