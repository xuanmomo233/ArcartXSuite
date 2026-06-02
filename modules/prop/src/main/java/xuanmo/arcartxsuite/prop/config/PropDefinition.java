package xuanmo.arcartxsuite.prop.config;

import java.util.List;

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
    List<PropCondition> conditions
) {

    public PropDefinition {
        effects = List.copyOf(effects);
        conditions = List.copyOf(conditions);
    }
}
