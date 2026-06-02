package xuanmo.arcartxsuite.prop.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import xuanmo.arcartxsuite.prop.config.PropMythicLibConfiguration;

public final class PropMythicLibModifierPlanner {

    private PropMythicLibModifierPlanner() {
    }

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
