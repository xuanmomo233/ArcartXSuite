package xuanmo.arcartxsuite.battlepass.model;

import java.time.LocalDate;

public record BattlePassSeason(
    String seasonId,
    String displayName,
    int maxLevel,
    int xpPerLevel,
    LocalDate startDate,
    LocalDate endDate
) {

    public boolean isActive() {
        LocalDate now = LocalDate.now();
        return !now.isBefore(startDate) && !now.isAfter(endDate);
    }

    public int xpRequiredForLevel(int level) {
        if (level <= 1) return 0;
        return xpPerLevel;
    }
}
