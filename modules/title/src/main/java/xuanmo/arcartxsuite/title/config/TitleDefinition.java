package xuanmo.arcartxsuite.title.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单个称号的完整定义。
 * <p>
 * 包含称号的基本信息（ID、分组、品质、类型）、显示文本（聊天/Tab 前后缀）、
 * 属性（展示属性与收集属性）、头顶显示模式与纹理参数等。
 * 属于 title 模块配置层。
 *
 * @param id                      称号唯一标识
 * @param enabled                 是否启用
 * @param groupId                 所属分组 ID
 * @param qualityId               品质 ID
 * @param kind                    称号类型（文本/图标）
 * @param displayName             显示名称
 * @param chatPrefix              聊天前缀
 * @param chatSuffix              聊天后缀
 * @param tabPrefix               Tab 列表前缀
 * @param tabSuffix               Tab 列表后缀
 * @param description             称号描述
 * @param source                  称号来源说明
 * @param displayAttributes       装备时生效的展示属性映射
 * @param collectionAttributes    拥有即生效的收集属性映射
 * @param displayAttributeLines   展示属性原始文本行
 * @param collectionAttributeLines 收集属性原始文本行
 * @param sortOrder               排序权重
 * @param overheadMode            头顶显示模式
 * @param overheadTexture         头顶纹理路径
 * @param overheadWidth           头顶纹理宽度
 * @param overheadHeight          头顶纹理高度
 * @param overheadOffsetY         头顶纹理 Y 轴偏移
 * @param overheadPrefix          头顶文本前缀
 * @param overheadSuffix          头顶文本后缀
 */
public record TitleDefinition(
    String id,
    boolean enabled,
    String groupId,
    String qualityId,
    TitleKind kind,
    String displayName,
    String chatPrefix,
    String chatSuffix,
    String tabPrefix,
    String tabSuffix,
    String description,
    String source,
    Map<String, Double> displayAttributes,
    Map<String, Double> collectionAttributes,
    List<String> displayAttributeLines,
    List<String> collectionAttributeLines,
    int sortOrder,
    OverheadMode overheadMode,
    String overheadTexture,
    int overheadWidth,
    int overheadHeight,
    double overheadOffsetY,
    String overheadPrefix,
    String overheadSuffix
) {
    public TitleDefinition {
        displayAttributes = immutableCopy(displayAttributes);
        collectionAttributes = immutableCopy(collectionAttributes);
        displayAttributeLines = immutableListCopy(displayAttributeLines);
        collectionAttributeLines = immutableListCopy(collectionAttributeLines);
    }

    /**
     * 头顶显示模式枚举。
     */
    public enum OverheadMode {
        TEXTURE, TEXT, NONE;
        /**
         * 将字符串解析为头顶显示模式。
         *
         * @param value 配置文本，支持 texture / text / none
         * @return 解析结果，无法识别时返回 {@link #NONE}
         */
        public static OverheadMode parse(String value) {
            if (value == null || value.isBlank()) return NONE;
            return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
                case "texture" -> TEXTURE;
                case "text" -> TEXT;
                default -> NONE;
            };
        }
    }

    private static Map<String, Double> immutableCopy(Map<String, Double> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static List<String> immutableListCopy(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return List.copyOf(values);
    }
}
