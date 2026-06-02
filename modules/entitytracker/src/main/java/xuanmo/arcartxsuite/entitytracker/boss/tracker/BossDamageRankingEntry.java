package xuanmo.arcartxsuite.entitytracker.boss.tracker;

import java.util.UUID;

public record BossDamageRankingEntry(
    UUID playerUuid,
    String playerName,
    int rank,
    boolean qualified,
    double damage,
    double damagePercent,
    double takenDamage
) {

    private static final BossDamageRankingEntry EMPTY = new BossDamageRankingEntry(
        new UUID(0L, 0L),
        "",
        0,
        false,
        0.0D,
        0.0D,
        0.0D
    );

    public static BossDamageRankingEntry empty() {
        return EMPTY;
    }
}

