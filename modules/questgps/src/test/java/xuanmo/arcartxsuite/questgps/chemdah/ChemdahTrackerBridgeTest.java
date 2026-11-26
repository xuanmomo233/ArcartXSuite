package xuanmo.arcartxsuite.questgps.chemdah;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import ink.ptms.chemdah.core.quest.addon.data.TrackCenter;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class ChemdahTrackerBridgeTest {
    @Test
    void orderedNativeCentersSkipUnavailableTargetsAndStopAtTheFirstUsablePoint() {
        World world = mock(World.class,
            call -> { throw new AssertionError(call.getMethod().getName()); });
        var target = new Location(world, 12, 64, 25);
        var visited = new ArrayList<String>();
        var resolved = ChemdahTrackerBridge.firstResolved(null, List.of("done", "unloaded", "invalid", "pending", "later"), id -> {
            visited.add(id);
            return switch (id) {
                case "done" -> null;
                case "unloaded" -> center(new Location(null, 1, 64, 1));
                case "invalid" -> center(new Location(world, Double.NaN, 64, 1));
                case "pending" -> center(target);
                default -> throw new AssertionError(id);
            };
        });
        assertSame(target, resolved.orElseThrow());
        assertEquals(List.of("done", "unloaded", "invalid", "pending"), visited);

        assertNull(ChemdahTrackerBridge.resolveCenter(null, center(null)));
    }

    private static TrackCenter center(Location location) {
        return new TrackCenter() {
            @Override public String identifier() { return "test"; }
            @Override public Location getLocation(Player player) { return location; }
        };
    }
}
