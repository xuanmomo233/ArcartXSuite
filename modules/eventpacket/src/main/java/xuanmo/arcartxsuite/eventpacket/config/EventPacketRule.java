package xuanmo.arcartxsuite.eventpacket.config;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import xuanmo.arcartxsuite.api.condition.ScriptCondition;

/**
 * 事件包规则配置记录。
 * <p>
 * 描述一条完整的事件触发规则：触发器类型、信号标识、过滤条件、
 * 冷却控制、动作列表以及客户端回包相关参数。
 * 由 {@code PluginConfiguration} 从 YAML 配置解析生成。
 *
 * @param id              规则唯一标识
 * @param enabled         是否启用
 * @param trigger         触发器类型
 * @param signal          信号标识，用于跨模块通信
 * @param placeholder     PAPI 占位符表达式（PAPI 触发器使用）
 * @param threshold       数值阈值（PAPI 阈值触发器使用）
 * @param requireNonEmpty 是否要求占位符值非空才触发
 * @param requiredCount   击杀计数触发器所需的目标击杀数
 * @param worlds          限制生效的世界名集合，空表示不限制
 * @param entityTypes     限制生效的实体类型集合
 * @param mythicMobIds    限制生效的 MythicMob ID 集合
 * @param repeatable      是否可重复触发
 * @param cooldownMillis  冷却时间（毫秒），0 表示不冷却
 * @param conditions      脚本条件列表，全部满足才触发
 * @param actions         触发后执行的动作列表
 * @param clientPacketId  客户端回包 ID（客户端回包触发器使用）
 * @param script          脚本内容（脚本触发器使用）
 * @param permission      执行所需权限，空白表示不限制
 * @param allowArgs       是否允许携带额外参数
 * @param argsPattern     额外参数的格式校验正则
 */
public record EventPacketRule(
    String id,
    boolean enabled,
    EventPacketTrigger trigger,
    String signal,
    String placeholder,
    BigDecimal threshold,
    boolean requireNonEmpty,
    int requiredCount,
    Set<String> worlds,
    Set<String> entityTypes,
    Set<String> mythicMobIds,
    boolean repeatable,
    long cooldownMillis,
    List<ScriptCondition> conditions,
    List<EventPacketAction> actions,
    String clientPacketId,
    String script,
    String permission,
    boolean allowArgs,
    String argsPattern
) {
    public EventPacketRule(
        String id, boolean enabled, EventPacketTrigger trigger,
        String signal, String placeholder, BigDecimal threshold,
        boolean requireNonEmpty, int requiredCount,
        Set<String> worlds, Set<String> entityTypes, Set<String> mythicMobIds,
        boolean repeatable, long cooldownMillis, List<ScriptCondition> conditions, List<EventPacketAction> actions
    ) {
        this(id, enabled, trigger, signal, placeholder, threshold,
            requireNonEmpty, requiredCount, worlds, entityTypes, mythicMobIds,
            repeatable, cooldownMillis, conditions, actions, "", "", "", false, "");
    }

    /**
     * 判断此规则是否使用 PAPI（PlaceholderAPI）触发器。
     *
     * @return 使用 PAPI 触发器返回 {@code true}
     */
    public boolean papiTrigger() {
        return trigger != null && trigger.papiTrigger();
    }

    /**
     * 判断此规则是否使用客户端回包触发器。
     *
     * @return 是客户端回包触发器返回 {@code true}
     */
    public boolean isClientPacketTrigger() {
        return trigger == EventPacketTrigger.CLIENT_PACKET;
    }

    /**
     * 判断此规则是否使用脚本触发器（JS / ARIA）。
     *
     * @return 是脚本触发器返回 {@code true}
     */
    public boolean isScriptTrigger() {
        return trigger != null && trigger.scriptTrigger();
    }

    /**
     * 判断此规则是否设置了权限过滤。
     *
     * @return 存在非空白权限字符串返回 {@code true}
     */
    public boolean hasPermissionFilter() {
        return permission != null && !permission.isBlank();
    }

    /**
     * 判断此规则是否设置了额外参数格式校验。
     *
     * @return 存在非空白参数格式正则返回 {@code true}
     */
    public boolean hasArgsPattern() {
        return argsPattern != null && !argsPattern.isBlank();
    }
}
