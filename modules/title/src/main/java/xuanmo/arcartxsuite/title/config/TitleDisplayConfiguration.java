package xuanmo.arcartxsuite.title.config;

import java.util.List;

/**
 * 展示称号配置。
 * <p>
 * 定义展示称号的分组顺序、分隔符和空文本提示。
 *
 * @param groups    展示称号的分组 ID 顺序列表
 * @param separator 多分组展示时的分隔符
 * @param emptyText 无展示称号时的占位文本
 */
public record TitleDisplayConfiguration(
    List<String> groups,
    String separator,
    String emptyText
) {
    public TitleDisplayConfiguration {
        groups = groups == null ? List.of() : List.copyOf(groups);
        separator = separator == null ? " " : separator;
        emptyText = emptyText == null ? "" : emptyText;
    }
}
