package xuanmo.arcartxsuite.api.util;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class NavigationTargetServiceTest {
    private final World home = world("home");
    private final World remote = world("remote");
    private final UUID id = UUID.randomUUID();
    private final Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
        (proxy, method, args) -> switch (method.getName()) {
            case "isOnline" -> true;
            case "getUniqueId" -> id;
            case "getWorld" -> home;
            case "getLocation" -> new Location(home, 0, 64, 0);
            default -> throw new AssertionError(method.getName());
        });

    @Test
    void reportsLossOnceAndRecoversAtTheSameCoordinatesInAnotherWorld() throws Exception {
        var service = navigation(0);
        var position = new Location(remote, 10, 64, 10);
        var current = new AtomicReference<>(position);
        var reported = new ArrayList<String>();
        service.setPositionChangedHandler((viewer, target, point) ->
            reported.add(point == null ? "unavailable" : point.getWorld().getName()));
        assertTrue(service.setTarget(player, NavigationTarget.builder("quest")
            .resolver(viewer -> current.get()).build()));
        refresh(service);
        current.set(null);
        refresh(service);
        refresh(service);
        current.set(position);
        refresh(service);
        current.set(new Location(home, 10, 64, 10));
        refresh(service);
        assertEquals(List.of("remote", "unavailable", "remote", "home"), reported);
    }

    @Test
    void replacingATargetDoesNotReuseItsCachedPosition() {
        var service = navigation(60_000);
        var reported = new ArrayList<Double>();
        service.setPositionChangedHandler((viewer, target, point) ->
            reported.add(point == null ? -1.0 : point.getX()));
        assertTrue(service.setTarget(player, NavigationTarget.builder("quest")
            .resolver(viewer -> new Location(home, 5, 64, 0)).build()));
        assertTrue(service.setTarget(player, NavigationTarget.builder("quest")
            .resolver(viewer -> new Location(home, 15, 64, 0)).build()));
        assertEquals(15.0, service.resolvedLocation(player, "quest").orElseThrow().getX());
        assertTrue(service.setTarget(player, NavigationTarget.builder("quest")
            .resolver(viewer -> null).build()));
        assertEquals(List.of(5.0, 15.0, -1.0), reported);
    }

    private static NavigationTargetService navigation(long ttlMillis) {
        return new NavigationTargetService(null, null, null, Logger.getAnonymousLogger(),
            new NavigationTargetService.Config(20, 30_000, 3, ttlMillis, false));
    }

    private void refresh(NavigationTargetService service) throws ReflectiveOperationException {
        var field = NavigationTargetService.class.getDeclaredField("desiredTargets");
        field.setAccessible(true);
        Object targets = ((Map<?, ?>) field.get(service)).get(id);
        var refresh = NavigationTargetService.class.getDeclaredMethod("refreshPlayer", Player.class, LinkedHashMap.class);
        refresh.setAccessible(true);
        refresh.invoke(service, player, targets);
    }

    private static World world(String name) {
        UUID id = UUID.randomUUID();
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getName" -> name;
                case "getUID" -> id;
                case "equals" -> proxy == args[0];
                case "hashCode" -> id.hashCode();
                default -> throw new AssertionError(method.getName());
            });
    }
}
