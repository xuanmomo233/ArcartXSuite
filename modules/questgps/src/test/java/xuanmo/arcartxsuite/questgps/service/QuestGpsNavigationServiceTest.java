package xuanmo.arcartxsuite.questgps.service;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.questgps.config.QuestGpsModuleConfiguration;

class QuestGpsNavigationServiceTest {
    private final Player player = onlinePlayer();
    private final Waypoints waypoints = new Waypoints();
    private final QuestGpsNavigationService navigation = navigation(waypoints);

    @Test
    void reusesAnImmutableSnapshotAndUpdatesOnlyTheAffectedQuestOnRemoval() {
        assertTrue(navigation.trackQuest(player, "alpha", "Alpha", List.of()));
        var initial = navigation.trackingStates(player);
        assertSame(initial, navigation.trackingStates(player));
        assertThrows(UnsupportedOperationException.class, initial::clear);

        assertTrue(navigation.trackTask(player, "alpha", "first", "Alpha", "First"));
        assertTrue(navigation.trackTask(player, "alpha", "second", "Alpha", "Second"));
        assertTrue(navigation.trackQuest(player, "beta", "Beta", List.of()));
        var beforeRemoval = navigation.trackingStates(player);
        assertEquals(List.of("alpha"), List.copyOf(initial.keySet()));
        assertEquals(List.of("alpha", "alpha|first", "alpha|second", "beta"), List.copyOf(beforeRemoval.keySet()));
        assertEquals("second", navigation.primaryStateForQuest(player, " ALPHA ").orElseThrow().taskId());

        assertTrue(navigation.untrack(player, "alpha", "second"));
        assertEquals("first", navigation.primaryStateForQuest(player, "alpha").orElseThrow().taskId());
        assertTrue(navigation.untrackQuest(player, " ALPHA "));
        assertFalse(navigation.isTrackingQuest(player, "Alpha"));
        assertFalse(navigation.untrackQuest(player, "alpha"));
        assertTrue(navigation.trackingPoint(player, "alpha|first").isEmpty());
        assertTrue(navigation.trackingPoint(player, "beta").isPresent());
        assertEquals(List.of("beta"), List.copyOf(navigation.trackingStates(player).keySet()));
        assertEquals(4, beforeRemoval.size());
        assertEquals(List.of(navigation.buildTargetId("alpha", "second"), navigation.buildTargetId("alpha", ""),
            navigation.buildTargetId("alpha", "first")), waypoints.removed);

        navigation.handleQuit(player);
        assertFalse(navigation.hasAnyTracking(player));
    }

    @Test
    void availabilityChecksSkipStyleResolutionAndPreviewUsesPriorityAndInheritedTeleport() {
        for (int i = 0; i < 48; i++) {
            assertTrue(navigation.questTrackable(player, "alpha", List.of("missing", "second", "first")));
            assertTrue(navigation.taskTrackable(player, "alpha", "first"));
            assertTrue(navigation.hasQuestPoint("alpha", null));
            assertTrue(navigation.hasTaskPoint("alpha", "second"));
            assertFalse(navigation.hasTaskPoint("alpha", "missing"));
            assertFalse(navigation.questTrackable(player, "disabled", List.of("first")));
        }
        assertEquals(0, waypoints.styleReads);

        var target = navigation.previewQuestPoint(player, "alpha", List.of("missing", "second", "first"), "Alpha").orElseThrow();
        assertEquals(navigation.buildTargetId("alpha", "second"), target.waypointId());
        assertEquals(22.0, target.x());
        assertEquals("transfer", target.teleport().world());
        assertEquals(1, waypoints.styleReads);
        var fallback = navigation.previewQuestPoint(player, "alpha", List.of("missing"), "Alpha").orElseThrow();
        assertEquals(1.0, fallback.x());
        assertEquals(2, waypoints.styleReads);
    }

    @Test
    void questAndTaskTracksKeepSeparateWaypointsAndRetrackingMakesTheQuestPrimary() {
        assertTrue(navigation.trackQuest(player, "alpha", "Alpha", List.of("first")));
        assertTrue(navigation.trackTask(player, "alpha", "first", "Alpha", "First"));
        var tracks = navigation.trackingStates(player);
        assertNotEquals(tracks.get("alpha").waypointId(), tracks.get("alpha|first").waypointId());
        assertEquals("alpha|first", navigation.primaryStateForQuest(player, "alpha").orElseThrow().trackKey());
        assertTrue(navigation.trackQuest(player, "alpha", "Alpha", List.of("first")));
        assertEquals("alpha", navigation.primaryStateForQuest(player, "alpha").orElseThrow().trackKey());
    }

    private static QuestGpsNavigationService navigation(Waypoints waypoints) {
        var logger = Logger.getAnonymousLogger();
        var global = new YamlConfiguration();
        global.set("navigation.model-marker.enabled", false);
        global.set("navigation.texture-marker.enabled", false);
        var configuration = QuestGpsModuleConfiguration.load(global, logger);

        var alpha = point(1);
        alpha.set("navigation.teleport.world", "transfer");
        alpha.set("navigation.teleport.x", 3);
        alpha.set("navigation.teleport.y", 64);
        alpha.set("navigation.teleport.z", 5);
        var tasks = Map.of("first", QuestGpsModuleConfiguration.readNativeTask("first", point(11)),
            "second", QuestGpsModuleConfiguration.readNativeTask("second", point(22)));
        var disabled = point(3);
        disabled.set("navigation.enabled", false);
        configuration.definitions().publish(Map.of(
            "alpha", QuestGpsModuleConfiguration.readNativeView("alpha", alpha, tasks, Map.of(), logger),
            "beta", QuestGpsModuleConfiguration.readNativeView("beta", point(2), Map.of(), Map.of(), logger),
            "disabled", QuestGpsModuleConfiguration.readNativeView("disabled", disabled, tasks, Map.of(), logger)), Map.of());
        var navigation = new QuestGpsNavigationService(null, configuration, waypoints, null, null, logger, null, null);
        navigation.start();
        return navigation;
    }

    private static MemoryConfiguration point(double x) {
        var config = new MemoryConfiguration();
        config.set("navigation.world", "world");
        config.set("navigation.x", x);
        config.set("navigation.y", 64);
        config.set("navigation.z", 0);
        return config;
    }

    private static Player onlinePlayer() {
        UUID id = UUID.randomUUID();
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getUniqueId" -> id;
                case "isOnline" -> true;
                default -> throw new AssertionError(method.getName());
            });
    }

    private static final class Waypoints implements QuestGpsNavigationService.WaypointRuntime {
        final List<String> removed = new ArrayList<>();
        int styleReads;

        @Override public boolean initialize(String ownerLabel) { return true; }
        @Override public void shutdown() { }

        @Override public String resolveStyleId(String preferredStyleId, String fallbackStyleId, String ownerLabel) {
            styleReads++;
            return fallbackStyleId;
        }

        @Override public boolean addWaypoint(Player player, String id, String title, String style, double x, double y, double z) {
            return true;
        }

        @Override public boolean removeWaypoint(Player player, String id, boolean animated) {
            removed.add(id);
            return true;
        }
    }
}
