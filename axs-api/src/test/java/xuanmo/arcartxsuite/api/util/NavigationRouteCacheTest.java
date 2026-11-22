package xuanmo.arcartxsuite.api.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Location;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.util.NavigationPathfinderTest.Terrain;

class NavigationRouteCacheTest {
    private final NavigationRouteCache cache = new NavigationRouteCache();
    private final UUID player = UUID.randomUUID();
    private final NavigationPathfinder finder = new NavigationPathfinder(300);
    private static final String ROUTE = "quest";

    @Test
    void sharesSearchBetweenRenderersInSameTickAndRespectsIterationBudget() {
        Terrain terrain = new Terrain();
        terrain.floor(0, 0, 8, 0, 2);
        Location start = terrain.at(0, 1, 0);
        Location goal = terrain.at(8, 1, 0);

        List<Location> model = route(start, goal);
        int afterSearch = terrain.blockReads();
        assertEquals(List.of(start, goal), model);
        assertTrue(afterSearch > 0);

        var textureFinder = new NavigationPathfinder(300);
        assertEquals(model, cache.route(player, ROUTE, textureFinder, start.clone(), goal.clone()));
        assertEquals(afterSearch, terrain.blockReads());

        var boundedFinder = new NavigationPathfinder(1);
        assertNotEquals(model, cache.route(player, ROUTE, boundedFinder, start, goal));
        assertTrue(terrain.blockReads() > afterSearch);
    }

    @Test
    void directBlockChangeInvalidatesDiagonalClearance() {
        Terrain terrain = new Terrain();
        terrain.floor(0, 0, 3, 0, 3);
        Location start = terrain.at(0, 1, 0);
        Location goal = terrain.at(2, 1, 2);
        List<Location> clear = route(start, goal);
        assertEquals(List.of(start, goal), clear);

        // Adjacent to the diagonal. The original path's feet and head cells remain unchanged.
        terrain.put(1, 1, 0, Material.STONE);
        terrain.put(1, 2, 0, Material.STONE);
        terrain.advance(1);

        List<Location> detour = route(start, goal);
        assertNotEquals(clear, detour);
        assertEquals(terrain.at(0, 1, 1), detour.get(1));
        assertEquals(goal, detour.get(detour.size() - 1));
    }

    @Test
    void rechecksDoorClosureAndReopeningWithoutAnEvent() {
        Terrain terrain = new Terrain();
        terrain.floor(0, 0, 4, 0, 0);
        terrain.northPanelDoor(2, 1, 0, true);
        Location start = terrain.at(0, 1, 0);
        Location goal = terrain.at(4, 1, 0);
        assertEquals(List.of(start, goal), route(start, goal));

        terrain.northPanelDoor(2, 1, 0, false);
        terrain.advance(1);
        assertEquals(List.of(start, terrain.at(1, 1, 0)), route(start, goal));

        terrain.northPanelDoor(2, 1, 0, true);
        terrain.advance(1);
        assertEquals(List.of(start, goal), route(start, goal));
    }

    @Test
    void rechecksPartialRouteOnNextTickWhenChunkBecomesAvailable() {
        AtomicBoolean adjacentLoaded = new AtomicBoolean();
        Terrain terrain = new Terrain((x, z) -> z == 0 && (x == 0 || x == 1 && adjacentLoaded.get()));
        terrain.floor(0, 14, 18, 7, 9);
        Location start = terrain.at(14, 1, 8);
        Location goal = terrain.at(17, 1, 8);

        assertEquals(List.of(start, terrain.at(15, 1, 8)), route(start, goal));
        adjacentLoaded.set(true);
        terrain.advance(1);

        assertEquals(List.of(start, goal), route(start, goal));
    }

    @Test
    void rechecksEmptyRouteOnNextTickWhenFloorAppears() {
        Terrain terrain = new Terrain();
        Location start = terrain.at(0, 1, 0);
        Location goal = terrain.at(4, 1, 0);
        assertTrue(route(start, goal).isEmpty());

        terrain.floor(0, 0, 4, 0, 0);
        terrain.advance(1);

        assertEquals(List.of(start, goal), route(start, goal));
    }

    @Test
    void recomputesStillWalkableRouteWhenItExpiresAndFindsShortcut() {
        Terrain terrain = new Terrain();
        terrain.floor(0, 0, 5, 0, 2);
        terrain.put(2, 1, 0, Material.STONE);
        terrain.put(2, 2, 0, Material.STONE);
        Location start = terrain.at(0, 1, 0);
        Location goal = terrain.at(5, 1, 0);
        List<Location> detour = route(start, goal);
        assertTrue(detour.size() > 2);
        assertEquals(goal, detour.get(detour.size() - 1));

        terrain.put(2, 1, 0, Material.AIR);
        terrain.put(2, 2, 0, Material.AIR);
        terrain.advance(39);
        assertEquals(detour, route(start, goal));

        terrain.advance(1);
        assertEquals(List.of(start, goal), route(start, goal));
    }

    @Test
    void removingAStandaloneRouteDiscardsItsSearchResult() {
        Terrain terrain = new Terrain();
        terrain.floor(0, 0, 3, 0, 0);
        Location start = terrain.at(0, 1, 0);
        Location goal = terrain.at(3, 1, 0);
        assertFalse(route(start, goal).isEmpty());
        int afterSearch = terrain.blockReads();

        cache.remove(player, ROUTE, finder);
        assertEquals(List.of(start, goal), route(start, goal));
        assertTrue(terrain.blockReads() > afterSearch);
    }

    @Test
    void keepsTheDisplayedRouteWithinOneStanceAndTrimsItWhenWalkingForward() {
        Terrain terrain = new Terrain();
        terrain.floor(0, 0, 31, 0, 4);
        Location start = terrain.at(0, 1, 2);
        Location goal = terrain.at(31, 1, 2);
        var original = route(start, goal);

        terrain.advance(1);
        assertSame(original, route(start.clone().add(0.3, 0, -0.2), goal));
        Location advanced = terrain.at(6, 1, 2);
        terrain.advance(1);
        int before = terrain.blockReads();
        var remaining = route(advanced, goal);
        int validatedReads = terrain.blockReads() - before;
        assertEquals(List.of(advanced, goal), remaining);
        before = terrain.blockReads();
        var fresh = new NavigationRouteCache();
        assertEquals(remaining, fresh.route(player, ROUTE, finder, advanced, goal));
        assertTrue(validatedReads < terrain.blockReads() - before);

        terrain.advance(1);
        assertEquals(List.of(start, goal), route(start, goal));
        Location offRoute = terrain.at(4, 1, 3);
        terrain.advance(1);
        assertEquals(offRoute, route(offRoute, goal).get(0));
    }

    @Test
    void movingAlongTheRouteDoesNotSkipRecheckingADoorAhead() {
        Terrain terrain = new Terrain();
        terrain.floor(0, 0, 6, 0, 0);
        terrain.northPanelDoor(4, 1, 0, true);
        Location start = terrain.at(0, 1, 0);
        Location goal = terrain.at(6, 1, 0);
        assertEquals(List.of(start, goal), route(start, goal));

        Location advanced = terrain.at(2, 1, 0);
        terrain.northPanelDoor(4, 1, 0, false);
        terrain.advance(1);
        assertEquals(List.of(advanced, terrain.at(3, 1, 0)), route(advanced, goal));
    }

    private List<Location> route(Location start, Location goal) {
        return cache.route(player, ROUTE, finder, start, goal);
    }
}
