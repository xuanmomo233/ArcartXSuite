package xuanmo.arcartxsuite.entitytracker.boss.tracker;

import java.util.List;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossSortMode;

public record PlayerBossViewSnapshot(
    int bossCount,
    long totalBossCount,
    int maxVisibleBars,
    BossSortMode sortMode,
    List<BossViewSlot> visibleSlots
) {

    public static PlayerBossViewSnapshot empty(int maxVisibleBars, BossSortMode sortMode) {
        return new PlayerBossViewSnapshot(0, 0L, Math.max(1, maxVisibleBars), sortMode, List.of());
    }

    public BossViewSlot slot(int slotIndex) {
        int zeroBased = slotIndex - 1;
        if (zeroBased < 0 || zeroBased >= visibleSlots.size()) {
            return BossViewSlot.empty();
        }
        return visibleSlots.get(zeroBased);
    }
}

