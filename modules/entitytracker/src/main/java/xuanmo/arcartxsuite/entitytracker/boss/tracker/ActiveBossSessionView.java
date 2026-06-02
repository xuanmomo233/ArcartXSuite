package xuanmo.arcartxsuite.entitytracker.boss.tracker;

import java.util.UUID;

public record ActiveBossSessionView(
    UUID entityUuid,
    String mythicMobId,
    String displayName,
    double health,
    double maxHealth,
    int participantCount,
    int trackedPlayerCount,
    double totalDamage
) {
}

