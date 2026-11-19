package xuanmo.arcartxsuite.battlepass.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.event.CombatDamageEvent;
import xuanmo.arcartxsuite.api.event.TaczGunDamageEvent;

class CombatDamageTaskSourceTest {
    @Test
    void oneConfirmedGunTransactionUsesOnlyOneOfTheExistingTaskRoutes() {
        var player = mock(Player.class);
        var world = mock(World.class);
        var target = mock(LivingEntity.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getName()).thenReturn("Archer");
        when(player.getWorld()).thenReturn(world);
        when(world.getName()).thenReturn("world");
        when(target.getType()).thenReturn(EntityType.ZOMBIE);
        when(target.getName()).thenReturn("Zombie");
        var normal = new ArrayList<Map<String, String>>();
        var gun = new ArrayList<Map<String, String>>();
        Map<String, BiConsumer<Player, Map<String, String>>> callbacks = Map.of(
            EntityDamageByEntityEvent.class.getName(), (p, data) -> normal.add(data),
            TaczGunDamageEvent.class.getName(), (p, data) -> gun.add(data));
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
            var listener = BukkitEventSourceListener.register(mock(JavaPlugin.class),
                Logger.getAnonymousLogger(), callbacks);
            var id = UUID.randomUUID();
            listener.onCombatDamage(new CombatDamageEvent(player, target, 8, CombatDamageEvent.Source.SYMPHONY,
                id, new CombatDamageEvent.GunHit(UUID.randomUUID(), "tacz:rifle", true)));
            listener.onCombatDamage(new CombatDamageEvent(player, target, 3, CombatDamageEvent.Source.BUKKIT, null, null));
            assertEquals(List.of("3.0"), normal.stream().map(row -> row.get("damage")).toList());
            assertEquals(List.of("8.0"), gun.stream().map(row -> row.get("damage")).toList());
            assertEquals(id.toString(), gun.get(0).get("transaction_id"));
        }
    }
}
