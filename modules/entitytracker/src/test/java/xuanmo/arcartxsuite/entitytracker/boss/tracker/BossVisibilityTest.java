package xuanmo.arcartxsuite.entitytracker.boss.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static xuanmo.arcartxsuite.entitytracker.boss.tracker.BossTrackerService.collectVisibleSessions;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDamageRankingSettings;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDefinition;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossSortMode;
import xuanmo.arcartxsuite.entitytracker.boss.tracker.BossTrackerService.TrackedBossSnapshot;
import xuanmo.arcartxsuite.entitytracker.boss.tracker.BossTrackerService.WorldBossSnapshots;

class BossVisibilityTest {
    private final World world = mock(World.class);

    @Test
    void keepsBoundaryBossesAndWideRangesInTheSameOrderAndAppliesThe3dLimit() {
        var wide = boss(1, 375, 64, 0, 512, 1);
        var left = boss(2, -192, 64, 0, 64, 3);
        var right = boss(3, -64, 64, 0, 64, 2);
        var above = boss(4, -128, 129, 0, 64, 0);
        var outsideCircle = boss(5, -80, 64, 48, 64, 0);
        var local = boss(6, -128, 64, 0, 48, 1);
        var snapshots = new ArrayList<>(List.of(wide, left, right, above, outsideCircle, local));
        for (int i = 7; i <= 16; i++) snapshots.add(boss(i, i * 1024, 64, 0, 48, 0));

        var linear = new WorldBossSnapshots(snapshots);
        var indexed = new WorldBossSnapshots(snapshots);
        indexed.indexCells();
        var viewer = new Location(world, -128, 64, 0);

        for (BossSortMode mode : BossSortMode.values()) {
            var expected = collectVisibleSessions(viewer, linear, mode, 2);
            assertEquals(4, expected.totalVisibleCount(), mode.name());
            assertEquals(expected, collectVisibleSessions(viewer, indexed, mode, 2), mode.name());
        }
        assertEquals(List.of(wide, left, right, local),
            collectVisibleSessions(viewer, indexed, BossSortMode.SPAWN_ORDER, 16).visibleSnapshots());
        assertEquals(0, collectVisibleSessions(new Location(world, -128, 64, -4096), indexed,
            BossSortMode.SPAWN_ORDER, 16).totalVisibleCount());
    }

    @Test
    void isolatedGroupsCheckOneCandidatePerViewerInsteadOfScanningTheWholeWorld() {
        int count = 48;
        var snapshots = new ArrayList<TrackedBossSnapshot>();
        for (int i = 0; i < count; i++) snapshots.add(boss(i + 1, i * 512 + 64, 64, 64, 48, 0));
        var linear = new WorldBossSnapshots(snapshots);
        var indexed = new WorldBossSnapshots(snapshots);
        indexed.indexCells();

        int linearCandidates = 0;
        int indexedCandidates = 0;
        for (var boss : snapshots) {
            var viewer = boss.renderState().location();
            linearCandidates += linear.nearby(viewer).size();
            indexedCandidates += indexed.nearby(viewer).size();
            assertEquals(List.of(boss), collectVisibleSessions(viewer, indexed, BossSortMode.DISTANCE, 5).visibleSnapshots());
        }
        assertEquals(2304, linearCandidates);
        assertEquals(48, indexedCandidates);
    }

    private TrackedBossSnapshot boss(long order, double x, double y, double z, double range, int priority) {
        var id = new UUID(0, order);
        var definition = new BossDefinition("boss", true, priority, range, "", "", "", "", "",
            BossDamageRankingSettings.defaults());
        var session = new BossSession(id, definition, "boss", "Boss", order);
        return new TrackedBossSnapshot(session, new BossSession.BossRenderState(id, "boss", order, 0L, "Boss",
            new Location(world, x, y, z), null, order, 100, order / 100.0D, range * range));
    }
}
