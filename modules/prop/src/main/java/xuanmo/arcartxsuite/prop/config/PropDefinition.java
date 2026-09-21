package xuanmo.arcartxsuite.prop.config;

import java.util.List;
import xuanmo.arcartxsuite.api.condition.ScriptCondition;

/**
 * 单个道具的定义模型。
 * <p>
 * 由 {@link PropDefinitionLoader} 从 {@code props/} 目录下的 YAML 文件解析而来，
 * 描述道具的标识、显示名、冷却、效果列表与使用条件等元数据。
 *
 * @param id                  道具唯一标识（相对路径，小写归一化）
 * @param displayName         道具显示名称，用于消息提示
 * @param coolDownGroup       冷却组名称，同组道具共享冷却
 * @param coolDownTimeSeconds 冷却时长（秒）
 * @param durationSeconds     效果持续时间（秒）
 * @param remove              使用后是否消耗一个物品
 * @param hand                是否允许手持右键使用
 * @param key                 是否允许按键使用
 * @param permission          使用所需权限节点，空字符串表示无限制
 * @param effects             效果行列表，格式为 {@code type|payload}
 * @param conditions          使用条件列表，全部满足才可使用
 * @param auto                自动触发定义，未配置时为 {@link PropAutoTriggerDefinition#DISABLED}
 */
public record PropDefinition(
    String id,
    String displayName,
    String coolDownGroup,
    int coolDownTimeSeconds,
    int durationSeconds,
    boolean remove,
    boolean hand,
    boolean key,
    String permission,
    List<String> effects,
    List<ScriptCondition> conditions,
    PropAutoTriggerDefinition auto
) {

    public PropDefinition {
        effects = List.copyOf(effects);
        conditions = List.copyOf(conditions);
    }
}
