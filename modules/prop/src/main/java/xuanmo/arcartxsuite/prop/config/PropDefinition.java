package xuanmo.arcartxsuite.prop.config;

import java.util.List;
import xuanmo.arcartxsuite.api.condition.ScriptCondition;

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
    List<ScriptCondition> conditions
) {

    public PropDefinition {
        effects = List.copyOf(effects);
        conditions = List.copyOf(conditions);
    }
}
