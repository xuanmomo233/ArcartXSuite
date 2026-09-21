package xuanmo.arcartxsuite.prop.config;

import java.util.List;
import xuanmo.arcartxsuite.api.condition.ScriptCondition;

/**
 * 单个道具的自动触发定义。
 * <p>
 * 由 {@link PropDefinitionLoader} 从道具 YAML 的 {@code auto} 节解析而来。
 * 自动触发独立于 {@code hand}/{@code key} 开关，仅从 ArcartX 快捷槽检测绑定物品；
 * 触发条件满足后仍要经过冷却、权限与使用条件校验（静默失败）。
 *
 * @param enabled       是否启用自动触发
 * @param intervalTicks 检查间隔（tick），{@code <=0} 表示使用全局 {@code auto-trigger.interval}
 * @param notifyPlayer  触发成功时是否给玩家发送提示消息（对应 YAML 键 {@code notify}）
 * @param conditions    触发条件列表，全部满足才尝试使用（AND 逻辑）
 */
public record PropAutoTriggerDefinition(
    boolean enabled,
    int intervalTicks,
    boolean notifyPlayer,
    List<ScriptCondition> conditions
) {

    /** 未配置 auto 节时的禁用态单例。 */
    public static final PropAutoTriggerDefinition DISABLED =
        new PropAutoTriggerDefinition(false, 0, false, List.of());

    public PropAutoTriggerDefinition {
        conditions = List.copyOf(conditions);
    }
}
