package xuanmo.arcartxsuite.prop.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import xuanmo.arcartxsuite.prop.config.PropMythicLibConfiguration;

/**
 * MythicLib 属性修改器规划器。
 * <p>
 * 工具类，将 {@link PropMythicLibEffect} 列表按属性 ID 聚合后，
 * 结合 {@link PropMythicLibConfiguration} 生成 {@link PropMythicLibModifierSpec} 列表。
 */
public final class PropMythicLibModifierPlanner {

    private PropMythicLibModifierPlanner() {
    }

    /**
     * 规划属性修改器列表。
     * <p>
     * 将同属性的多个效果值累加，生成每个属性对应的修改器规格。
     *
     * @param configuration   MythicLib 配置，用于生成键名
     * @param propId          道具 ID
     * @param effects         效果列表
     * @param durationSeconds 持续时间（秒）
     * @return 不可变的修改器规格列表
     */
    public static List<PropMythicLibModifierSpec> plan(
        PropMythicLibConfiguration configuration,
        String propId,
        List<PropMythicLibEffect> effects,
        int durationSeconds
    ) {
        LinkedHashMap<String, Double> valuesByStat = new LinkedHashMap<>();
        for (PropMythicLibEffect effect : effects) {
            valuesByStat.merge(effect.statId(), effect.value(), Double::sum);
        }

        long durationMillis = Math.max(0L, durationSeconds) * 1000L;
        long durationTicks = Math.max(0L, durationSeconds) * 20L;
        List<PropMythicLibModifierSpec> modifiers = new ArrayList<>();
        for (Map.Entry<String, Double> entry : valuesByStat.entrySet()) {
            String statId = entry.getKey();
            modifiers.add(new PropMythicLibModifierSpec(
                configuration.modifierKey(propId, statId),
                configuration.modifierName(propId, statId),
                statId,
                entry.getValue(),
                durationMillis,
                durationTicks
            ));
        }
        return List.copyOf(modifiers);
    }
}
