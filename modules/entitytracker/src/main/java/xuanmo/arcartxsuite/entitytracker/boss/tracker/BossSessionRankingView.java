package xuanmo.arcartxsuite.entitytracker.boss.tracker;

import java.util.List;
import java.util.UUID;

public record BossSessionRankingView(
    UUID entityUuid,
    String mythicMobId,
    String displayName,
    double health,
    double maxHealth,
    int participantCount,
    int trackedPlayerCount,
    double totalDamage,
    List<BossDamageRankingEntry> trackedEntries
) {
}

